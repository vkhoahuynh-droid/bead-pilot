package com.beadout.bot;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.Process;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Owns root process lifetime. Only compact commands/status cross the pipes during play. */
public final class PilotService extends Service {
    public static volatile Runnable statusObserver;
    public static volatile Bitmap preview;
    public static volatile Config detectedLayout;
    public static volatile boolean active;
    public static volatile String status="Not running",detectedPackage="";
    private Process process;
    private BufferedWriter control;
    private final ExecutorService writes=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout overlay;
    private TextView overlayStatus;
    private volatile boolean closing;
    private OcrReader ocr;
    private Runnable scheduledAction;
    private final Runnable heartbeat=new Runnable(){public void run(){if(!closing){send("ping");handler.postDelayed(this,1000);}}};
    @Override public void onCreate(){
        super.onCreate();
        active=true;
        try{ocr=new OcrReader();}catch(RuntimeException e){publish("Offline reader failed to start: "+e.getMessage());}
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("pilot","Bead Pilot controls",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PilotService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"pilot").setContentTitle("Bead Pilot")
            .setContentText("Root solver active. Tap to calibrate; Stop ends capture and input.")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
        startForeground(1,n);showOverlay();
        new Thread(this::launch,"root-launch").start();handler.post(heartbeat);
    }
    private void launch(){
        try{
            String apk=getApplicationInfo().sourceDir;
            String quoted="'"+apk.replace("'","'\\''")+"'";
            process=new ProcessBuilder("su","-c","CLASSPATH="+quoted+" /system/bin/app_process / com.beadout.bot.AutomaticRootMain").redirectErrorStream(true).start();
            if(closing){process.destroy();return;}
            control=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
            String lastRootError="";
            try(BufferedReader r=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                String line;while((line=r.readLine())!=null&&!closing){
                    if(line.length()>8_000_000){publish("Oversized root response; stopping");break;}
                    try{
                        JSONObject j=new JSONObject(line);String type=j.optString("type"),text=j.optString("text");
                        if(type.equals("ocr_request")){
                            if(ocr!=null)ocr.read(j,this::sendObject);
                            else sendObject(new JSONObject().put("cmd","ocr").put("id",j.getLong("id")).put("error","Offline text reader is unavailable").put("words",new JSONArray()));
                        }else if(type.equals("layout")){
                            detectedLayout=Config.parse(j.getJSONObject("config"));publish(text);
                        }else if(type.equals("snapshot")){
                            byte[]bytes=android.util.Base64.decode(text,android.util.Base64.NO_WRAP);
                            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                            if(bitmap!=null){preview=bitmap;publish("Detected board preview is ready in RAM.");}
                        }else if(type.equals("focus")){detectedPackage=text;getSharedPreferences("pilot",MODE_PRIVATE).edit().putString("lastGamePackage",text).apply();}
                        else {if(type.equals("error"))lastRootError=text;publish(text);}
                    }catch(JSONException ignored){publish("Root: "+line.substring(0,Math.min(240,line.length())));}
                }
            }
            if(!closing){publish(lastRootError.isEmpty()?"Root process exited. Check root permission and device compatibility.":lastRootError);handler.post(this::stopSelf);}
        }catch(Exception e){publish("Root start failed: "+e.getMessage());handler.post(this::stopSelf);}
    }
    private void publish(String value){status=value;
        handler.post(()->{if(overlayStatus!=null)overlayStatus.setText(value.substring(0,Math.min(150,value.length())));
            Runnable observer=statusObserver;if(observer!=null)observer.run();});}
    private void sendObject(JSONObject value){
        if(closing)return;
        writes.execute(()->{try{BufferedWriter writer=control;if(writer!=null){writer.write(value.toString());writer.newLine();writer.flush();}}
            catch(IOException e){publish("Control pipe closed; root watchdog will stop input");}});
    }
    private void send(String command){try{sendObject(new JSONObject().put("cmd",command));}catch(JSONException ignored){}}
    private void sendConfig(){
        try{
            String raw=getSharedPreferences("pilot",MODE_PRIVATE).getString("config",new Config().json().toString());
            sendObject(new JSONObject().put("cmd","config").put("config",new JSONObject(raw)));
        }catch(JSONException e){publish("Invalid saved calibration: "+e.getMessage());}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if("stop".equals(action)){stopSelf();return START_NOT_STICKY;}
        if(overlay==null)showOverlay();
        if("pause".equals(action)||"config".equals(action)||"delay".equals(action)||"capture".equals(action)){
            if(scheduledAction!=null)handler.removeCallbacks(scheduledAction);
            scheduledAction=null;
        }
        if("auto".equals(action)||"preview".equals(action)){
            if(scheduledAction!=null)handler.removeCallbacks(scheduledAction);
            publish("Switch to Bead Out. Automatic detection starts in five seconds.");
            String cmd="auto".equals(action)?"auto":"inspect";
            scheduledAction=()->{try{sendObject(new JSONObject().put("cmd",cmd).put("package",getSharedPreferences("pilot",MODE_PRIVATE).getString("lastGamePackage","")));}catch(JSONException ignored){}};
            handler.postDelayed(scheduledAction,5000);
        }else if("snapshot".equals(action))send("snapshot");
        else if("capture".equals(action)){
            send("pause");
            publish("Open Bead Out now. Capturing into RAM in 5 seconds.");
            scheduledAction=()->send("snap");handler.postDelayed(scheduledAction,5000);
        }else if("delay".equals(action)){
            String cmd=intent.getStringExtra("command");
            if("inspect".equals(cmd)||"probe".equals(cmd)||"play".equals(cmd)){
                publish("Open Bead Out now. Starting "+cmd+" in 5 seconds.");
                scheduledAction=()->send(cmd);handler.postDelayed(scheduledAction,5000);
            }
        }else if("config".equals(action))sendConfig();
        else if("inspect".equals(action)||"play".equals(action)||"probe".equals(action)||"pause".equals(action))send(action);
        return START_NOT_STICKY;
    }
    private void showOverlay(){
        if(!Settings.canDrawOverlays(this))return;
        wm=getSystemService(WindowManager.class);overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setBackgroundColor(0xdd203329);
        LinearLayout controls=new LinearLayout(this);overlay.addView(controls);
        Button stop=new Button(this);stop.setText("STOP");stop.setOnClickListener(v->stopSelf());controls.addView(stop);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_SECURE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.BOTTOM|Gravity.RIGHT;p.y=12;
        try{wm.addView(overlay,p);}catch(RuntimeException e){publish("Overlay unavailable; use notification Stop");overlay=null;}
    }
    @Override public void onDestroy(){
        closing=true;handler.removeCallbacksAndMessages(null);
        active=false;if(ocr!=null)ocr.close();
        if(overlay!=null)wm.removeView(overlay);
        // Closing stdin also stops the daemon if the final stop command cannot be delivered.
        writes.execute(()->{try{if(control!=null){control.write("{\"cmd\":\"stop\"}\n");control.flush();control.close();}}catch(IOException ignored){}
            if(process!=null){try{if(!process.waitFor(3,TimeUnit.SECONDS))process.destroy();}catch(InterruptedException e){Thread.currentThread().interrupt();process.destroy();}}});
        writes.shutdown();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

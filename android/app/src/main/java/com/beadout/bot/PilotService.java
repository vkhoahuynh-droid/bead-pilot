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
    private Notification.Builder notification;
    private volatile JSONObject deferredMode;
    private static final java.util.ArrayDeque<String> events=new java.util.ArrayDeque<>();
    public static synchronized String recentEvents(){return String.join("\n",events);}
    private static synchronized void remember(String value){
        String item=value.length()>400?value.substring(0,400):value;
        if(!events.isEmpty()&&events.peekLast().endsWith(item))return;
        events.addLast(new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.US).format(new java.util.Date())+"  "+item);
        while(events.size()>20)events.removeFirst();
    }
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
        notification=new Notification.Builder(this,"pilot").setContentTitle("Bead Pilot")
            .setContentText("Floating controls ready. Open Bead Out and tap Start.")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Stop",stop).build());
        startForeground(1,notification.build());showOverlay();
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
                        }else if(type.equals("layout_clear")){detectedLayout=null;
                        }else if(type.equals("layout")){
                            detectedLayout=Config.parse(j.getJSONObject("config"));publish(text);
                        }else if(type.equals("snapshot")){
                            byte[]bytes=android.util.Base64.decode(text,android.util.Base64.NO_WRAP);
                            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                            if(bitmap!=null){preview=bitmap;publish("Detected board preview is ready in RAM.");}
                        }else if(type.equals("focus")){detectedPackage=text;getSharedPreferences("pilot",MODE_PRIVATE).edit().putString("lastGamePackage",text).apply();}
                        else {if(type.equals("ready")){JSONObject mode=deferredMode;deferredMode=null;if(mode!=null)sendObject(mode);}
                            if(type.equals("error"))lastRootError=text;publish(text);}
                    }catch(JSONException ignored){publish("Root: "+line.substring(0,Math.min(240,line.length())));}
                }
            }
            if(!closing){publish(lastRootError.isEmpty()?"Root process exited. Check root permission and device compatibility.":lastRootError);handler.post(this::stopSelf);}
        }catch(Exception e){publish("Root start failed: "+e.getMessage());handler.post(this::stopSelf);}
    }
    private void publish(String value){status=value;remember(value);
        handler.post(()->{if(!closing&&notification!=null){notification.setContentText(value).setStyle(new Notification.BigTextStyle().bigText(value));getSystemService(NotificationManager.class).notify(1,notification.build());}
            if(overlayStatus!=null)overlayStatus.setText(value.substring(0,Math.min(150,value.length())));
            Runnable observer=statusObserver;if(observer!=null)observer.run();});}
    private void sendObject(JSONObject value){
        if(closing)return;
        writes.execute(()->{try{BufferedWriter writer=control;if(writer!=null){writer.write(value.toString());writer.newLine();writer.flush();}
            else {String cmd=value.optString("cmd");if(cmd.equals("auto")||cmd.equals("inspect")||cmd.equals("pause"))deferredMode=value;}}
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
        if("show_controls".equals(action)){cancelScheduled();publish(overlay!=null?"Floating controls ready. Open Bead Out, then tap floating Start.":"Allow floating controls, then press Start automatic play again.");}
        else if("auto".equals(action)||"preview".equals(action)){
            scheduleAutomatic("auto".equals(action)?"auto":"inspect",5000);
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
    private void cancelScheduled(){if(scheduledAction!=null)handler.removeCallbacks(scheduledAction);scheduledAction=null;}
    private void scheduleAutomatic(String command,long delay){
        cancelScheduled();
        publish(delay>0?"Switch to Bead Out. Automatic detection starts in five seconds.":"Starting automatic play; keep Bead Out open.");
        scheduledAction=()->{scheduledAction=null;try{sendObject(new JSONObject().put("cmd",command).put("package",getSharedPreferences("pilot",MODE_PRIVATE).getString("lastGamePackage","")));}catch(JSONException ignored){}};
        if(delay==0)scheduledAction.run();else handler.postDelayed(scheduledAction,delay);
    }
    private void pauseAutomatic(){cancelScheduled();send("pause");publish("Pausing automatic play");}
    private void floatingButton(LinearLayout row,String label,Runnable action){
        Button button=new Button(this);button.setText(label);button.setTextSize(11);button.setAllCaps(false);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(0,0,0,0);button.setOnClickListener(v->action.run());
        row.addView(button,new LinearLayout.LayoutParams(0,(int)(42*getResources().getDisplayMetrics().density),1));
    }
    private void showOverlay(){
        if(!Settings.canDrawOverlays(this))return;
        wm=getSystemService(WindowManager.class);overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setBackgroundColor(0xdd203329);
        overlayStatus=new TextView(this);overlayStatus.setTextColor(Color.WHITE);overlayStatus.setTextSize(11);overlayStatus.setMaxLines(3);
        int dp=(int)(180*getResources().getDisplayMetrics().density);overlayStatus.setWidth(dp);overlayStatus.setPadding(8,4,8,0);overlayStatus.setText(status);overlay.addView(overlayStatus);
        LinearLayout controls=new LinearLayout(this);overlay.addView(controls,new LinearLayout.LayoutParams(dp,-2));
        floatingButton(controls,"Start",()->scheduleAutomatic("auto",0));
        floatingButton(controls,"Pause",this::pauseAutomatic);
        floatingButton(controls,"Stop",this::stopSelf);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.BOTTOM|Gravity.RIGHT;p.y=12;
        // Drag the status area out of the way. It never intercepts board-wide touches.
        overlayStatus.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;
            public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();ox=p.x;oy=p.y;return true;}
                if(e.getAction()==android.view.MotionEvent.ACTION_MOVE){p.x=Math.max(0,ox+Math.round(x-e.getRawX()));p.y=Math.max(0,oy+Math.round(y-e.getRawY()));wm.updateViewLayout(overlay,p);return true;}return true;}});
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

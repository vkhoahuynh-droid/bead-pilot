package com.beadout.bot;

import android.graphics.*;
import android.os.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.lang.Process;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/** One-button root player. The controller performs bundled OCR; pixels and commands stay in RAM. */
public final class AutomaticRootMain {
    private final Object controlLock=new Object(),outputLock=new Object();
    private volatile boolean stopped,requested,scanning,reset,preview;
    private volatile long heartbeat=System.nanoTime();
    private final AtomicLong controlVersion=new AtomicLong();
    private final AtomicReference<OcrRequest>pending=new AtomicReference<>();
    private final AtomicReference<OcrResult>latest=new AtomicReference<>(),latestFull=new AtomicReference<>();
    private final AutoLayout detector=new AutoLayout();
    private String gamePackage="";
    private Config config;
    private AutoPerception perception;
    private Tracker tracker;
    private final Planner planner=new Planner(Config.NAMES.length,64,5,60);
    private LevelCycle cycle=new LevelCycle();
    private Input input;
    private Bitmap lastPreview;
    private long epoch,sequence,lastOcr,lastFull,lastStatus,lastPlan,lastRefresh,modelSince;
    private final int[]slotGeneration=new int[4];
    private final AutoLayout.Stability layoutStability=new AutoLayout.Stability();
    private String lastBoard="",planKey="",tapBefore;
    private int stableBoard;
    private long tapAt;
    private int failedTaps;
    private final HashMap<String,Long> blockedTaps=new HashMap<>();
    private String tapKey;
    private Planner.Result plan;
    private static final class OcrRequest {
        long id,epoch,time;boolean full;int[]colors,generations;
    }
    private static final class OcrResult {
        OcrRequest request;TextScene scene;boolean ok;
    }
    public static void main(String[]args){AutomaticRootMain player=new AutomaticRootMain();try{
        if(android.os.Process.myUid()!=0)throw new IOException("Grant Bead Pilot root using UID 0");Looper.prepareMainLooper();
        try{Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);}catch(Exception ignored){}
        Thread commands=new Thread(player::commands,"control");commands.setDaemon(true);commands.start();player.run();
    }catch(Throwable e){player.emit("error",e.getClass().getSimpleName()+": "+e.getMessage());}
    finally{if(player.lastPreview!=null)player.lastPreview.recycle();player.emit("stopped","Root player stopped");System.exit(0);}}
    private void commands(){
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){String line;
            while((line=reader.readLine())!=null){if(line.length()>1_000_000)throw new IOException("Command too large");JSONObject j=new JSONObject(line);heartbeat=System.nanoTime();String cmd=j.optString("cmd");
                if(cmd.equals("ping"))continue;
                if(cmd.equals("ocr")){acceptOcr(j);continue;}
                if(cmd.equals("snapshot")||cmd.equals("snap")){preview=true;continue;}
                synchronized(controlLock){controlVersion.incrementAndGet();
                    if(cmd.equals("stop")){requested=false;scanning=false;stopped=true;return;}
                    if(cmd.equals("pause")){requested=false;scanning=false;emit("status","Paused");}
                    if(cmd.equals("auto")||cmd.equals("inspect")){requested=cmd.equals("auto");scanning=true;reset=true;String pkg=j.optString("package","");if(pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")&&!pkg.equals("com.beadout.bot"))gamePackage=pkg;}
                }
            }
        }catch(Exception e){emit("error","Control channel: "+e.getMessage());}finally{stopped=true;requested=false;}
    }
    private void acceptOcr(JSONObject j)throws JSONException{
        OcrRequest request=pending.get();if(request==null||j.optLong("id")!=request.id)return;if(!pending.compareAndSet(request,null))return;
        ArrayList<TextScene.Word>words=new ArrayList<>();JSONArray array=j.optJSONArray("words");if(array!=null)for(int i=0;i<array.length();i++){JSONObject w=array.getJSONObject(i);words.add(new TextScene.Word(w.getString("text"),(float)w.getDouble("x"),(float)w.getDouble("y"),(float)w.getDouble("w"),(float)w.getDouble("h")));}
        OcrResult result=new OcrResult();result.request=request;result.scene=new TextScene(words);result.ok=!j.has("error");latest.set(result);if(request.full)latestFull.set(result);
        if(!result.ok)emit("warning",j.optString("error","Offline text recognition is loading"));
    }
    private void emit(String type,String text){try{emitObject(new JSONObject().put("type",type).put("text",text));}catch(JSONException ignored){}}
    private void emitObject(JSONObject json){synchronized(outputLock){System.out.println(json);System.out.flush();}}
    private boolean fresh(OcrResult r,long now){return r!=null&&r.ok&&now-r.request.time<2_500_000_000L;}
    private void resetModel(){config=null;perception=null;tracker=null;plan=null;planKey="";tapBefore=null;layoutStability.clear();stableBoard=0;lastBoard="";epoch++;Arrays.fill(slotGeneration,0);}
    private void apply(AutoLayout.Detection d,Capture.Frame frame,String focus)throws Exception{
        layoutStability.clear();latest.set(null);latestFull.set(null);lastFull=0;
        config=d.config;config.packageName=focus;gamePackage=focus;perception=new AutoPerception(config);tracker=new Tracker(config.interval,true);epoch++;Arrays.fill(slotGeneration,0);modelSince=System.nanoTime();lastBoard="";stableBoard=0;plan=null;tapBefore=null;planKey="";
        emit("focus",gamePackage);emitObject(new JSONObject().put("type","layout").put("config",config.json()).put("text",d.summary));
        if(lastPreview!=null)lastPreview.recycle();lastPreview=frame.bitmap();sendPreview();emit("status",d.summary+". Measuring movement before tapping.");
    }
    private void sendPreview(){if(lastPreview==null){emit("status","Start automatic detection and open the game first");return;}try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){lastPreview.compress(Bitmap.CompressFormat.PNG,100,bytes);emit("snapshot",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));}catch(IOException ignored){}}
    private void requestOcr(Capture.Frame f,long now){
        OcrRequest old=pending.get();if(old!=null&&now-old.time<3_000_000_000L||now-lastOcr<500_000_000L)return;
        boolean full=config==null||cycle.transitioning()||now-lastFull>2_000_000_000L;int left=0,top=0,width=f.width,height=f.height;
        if(!full){float[]a=config.slots.get(0),b=config.slots.get(3);left=Math.max(0,Math.round(a[0]*f.width-42));int right=Math.min(f.width,Math.round(b[0]*f.width+42));top=Math.max(0,Math.round(a[1]*f.height-3));width=right-left;height=Math.min(f.height-top,75);}
        int[]pixels=new int[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=f.pixel(left+x,top+y);
        Bitmap bitmap=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);OcrRequest request=new OcrRequest();request.id=++sequence;request.time=f.nanos;request.epoch=epoch;request.full=full;request.generations=slotGeneration.clone();request.colors=perception==null?new int[]{-2,-2,-2,-2}:perception.slotColors(f);pending.set(request);
            emitObject(new JSONObject().put("type","ocr_request").put("id",request.id).put("full",full).put("screenWidth",f.width).put("screenHeight",f.height).put("left",left).put("top",top).put("png",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)));
            lastOcr=now;if(full)lastFull=now;
        }catch(Exception e){pending.set(null);emit("warning","Text recognition request: "+e.getMessage());}finally{bitmap.recycle();}
    }
    private void progress(Vision.Observation o,long now){OcrResult r=latest.get();if(!fresh(r,now)||r.request.epoch!=epoch)return;int[]numbers=r.scene.progress(config.slots);
        for(int i=0;i<4;i++)if(r.request.colors[i]==o.slots[i]&&r.request.generations[i]==slotGeneration[i])o.progress[i]=numbers[i];
    }
    private void run()throws Exception{
        emit("ready","Root connected. Press Start automatic play and open Bead Out.");
        try(Capture capture=new Capture()){
            while(!stopped){long now=System.nanoTime();if(now-heartbeat>8_000_000_000L)throw new IOException("Controller heartbeat lost");
                if(preview){preview=false;sendPreview();}
                if(reset){reset=false;resetModel();cycle=new LevelCycle();latest.set(null);latestFull.set(null);lastFull=0;failedTaps=0;blockedTaps.clear();}
                if(!scanning){cycle=new LevelCycle();Thread.sleep(100);continue;}
                long token=controlVersion.get();
                if(cycle.homeWaiting()){
                    if(cycle.update(now,false,false,false)==LevelCycle.Action.LAUNCH&&requested)launchGame(token);
                    Thread.sleep(50);continue;
                }
                capture.verifyGeometry();Capture.Frame frame=capture.next();now=System.nanoTime();
                if(stopped||!scanning)continue;
                if(now-frame.nanos>2_000_000_000L){Thread.sleep(30);continue;}
                requestOcr(frame,now);
                String focus=focus();if(focus.equals("com.beadout.bot")){status(now,"Waiting for you to return to Bead Out");Thread.sleep(250);continue;}
                OcrResult text=latestFull.get();boolean wordsFresh=fresh(text,now);boolean win=wordsFresh&&text.scene.win&&buttonColor(frame,text.scene.next,false);boolean menu=wordsFresh&&text.scene.menu&&buttonColor(frame,text.scene.play,true);
                if(gamePackage.isEmpty()&&(win||menu))gamePackage=focus;
                boolean game=!gamePackage.isEmpty()&&focus.equals(gamePackage);
                AutoLayout.Detection found=null;
                if(win||menu||cycle.transitioning()){
                    if(!win&&!menu&&game)try{found=detector.detect(frame);}catch(IllegalStateException ignored){}
                    boolean ready=found!=null;
                    LevelCycle.Action action=cycle.update(now,win,game,ready,menu);
                    if(action==LevelCycle.Action.HOME){if(pressHome(token)){cycle.homeSent(System.nanoTime());status(now,"Android Home for three seconds");}}
                    else if(action==LevelCycle.Action.NEXT){if(text!=null&&game&&tap(token,text.scene.next,frame,capture))status(now,"Next tapped. Watching for the new board or an ad.");}
                    else if(action==LevelCycle.Action.PLAY_MENU){if(text!=null&&game&&tap(token,text.scene.play,frame,capture))status(now,"Game menu recognized. Play tapped.");}
                    else if(action==LevelCycle.Action.RESET){resetModel();if(found!=null)apply(found,frame,focus);status(now,"New board detected. Resuming automatically.");}
                    else if(action==LevelCycle.Action.FAIL)throw new IOException("Could not recover the game after bounded Home/reopen attempts");
                    if(cycle.transitioning()||action==LevelCycle.Action.RESET){Thread.sleep(250);continue;}
                }
                if(!gamePackage.isEmpty()&&!game){status(now,"Game is not in the foreground; taps paused");Thread.sleep(250);continue;}
                if(config==null){
                    try{
                        if(found==null)found=detector.detect(frame);
                        boolean stable=layoutStability.observe(found.config,frame.width,frame.height);
                        status(now,found.summary+"; checking a second frame");
                        if(stable)apply(found,frame,focus);
                    }catch(IllegalStateException e){layoutStability.clear();status(now,e.getMessage());}
                    Thread.sleep(250);continue;
                }
                try{
                    boolean geometryChecked=false;
                    if(now-lastRefresh>500_000_000L){
                        lastRefresh=now;
                        try{found=detector.detect(frame,config);}catch(IllegalStateException e){resetModel();throw e;}
                        if(!AutoLayout.sameGeometry(config,found.config,frame.width,frame.height)){
                            plan=null;planKey="";
                            if(layoutStability.observe(found.config,frame.width,frame.height))apply(found,frame,focus);
                            status(now,"Layout changed; rechecking geometry before tapping");
                            Thread.sleep(250);continue;
                        }
                        layoutStability.clear();
                        config.buttons.clear();config.buttons.addAll(found.config.buttons);
                        config.clickable=found.config.clickable;config.unknownBoxes=found.config.unknownBoxes;config.unlockValues=found.config.unlockValues;
                        geometryChecked=true;
                    }
                    Vision.Observation observation=perception.read(frame);progress(observation,now);
                    String board=observation.boardKey();stableBoard=board.equals(lastBoard)?stableBoard+1:0;lastBoard=board;
                    if(stableBoard<1){Thread.sleep(150);continue;}
                    Planner.State state=tracker.update(observation);state.unlockValues=config.unlockValues.clone();
                    if(tracker.settling()){status(now,"Waiting for a filled square to leave");Thread.sleep(150);continue;}
                    if(tapBefore!=null){if(!tapBefore.equals(board)){tapBefore=null;failedTaps=0;}else if(now-tapAt>4_000_000_000L){if(tapKey!=null)blockedTaps.put(tapKey,now+30_000_000_000L);if(++failedTaps>=2){requested=false;scanning=false;emit("error","A square did not move twice. Automatic play paused; no repeated taps.");}throw new IllegalStateException("Square did not move; checking the layout again");}}
                    for(int i=0;i<state.board.length;i++){float[]p=config.buttons.get(i);String k=Math.round(p[0]*100)+":"+Math.round(p[1]*100);Long until=blockedTaps.get(k);if(until!=null&&until>now)state.board[i]=-1;}
                    int occupied=0;for(int c:state.colors)if(c>=0)occupied++;
                    String key=state.key();if((plan==null||now-lastPlan>500_000_000L)&&!key.equals(planKey)){plan=planner.search(state,false);planKey=key;lastPlan=now;}
                    OcrResult currentOcr=latest.get();boolean ocrReady=fresh(currentOcr,now)&&currentOcr.request.epoch==epoch;
                    if(geometryChecked&&requested&&tapBefore==null&&tracker.speedSamples()>=3&&ocrReady&&plan!=null&&plan.firstAction()>=0){
                        int index=plan.firstAction();if(index>=config.buttons.size()||state.board[index]<0){plan=null;continue;}
                        boolean safeFourth=occupied<3||plan.predicted.completed>state.completed;
                        if(safeFourth&&tap(token,config.buttons.get(index),frame,capture)){
                            int slot=state.empty();if(slot>=0){slotGeneration[slot]++;tracker.expectTap(state.board[index],slot);}float[]point=config.buttons.get(index);tapKey=Math.round(point[0]*100)+":"+Math.round(point[1]*100);tapBefore=board;tapAt=now;plan=null;
                            emit("action","Selected "+Config.NAMES[state.board[index]]+" square with a clear exit path");
                        }
                    }
                    long heap=(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576;
                    status(now,!ocrReady?"Loading offline text recognition; taps paused":(requested?"Automatic play":"Preview only")+": "+occupied+"/4 slots; "+config.buttons.size()+" board squares. "+tracker.note+"\nJava heap "+heap+"MiB; "+capture.backend);
                }catch(Exception e){
                    String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();status(now,message);
                    if(message.contains("direction reversed")){
                        ArrayList<float[]>p=config.lanes.get(0);float[]first=p.remove(0);Collections.reverse(p);p.add(0,first);tracker=new Tracker(config.interval,true);plan=null;epoch++;
                    }else if(!message.startsWith("Reading existing")){
                        if(now-modelSince>2_000_000_000L){resetModel();lastFull=0;}
                    }
                }
                Thread.sleep(250);
            }
        }
    }
    private void status(long now,String message){if(now-lastStatus<900_000_000L)return;emit("status",message);lastStatus=now;}
    private boolean tap(long token,float[]point,Capture.Frame frame,Capture capture)throws Exception{
        if(point==null||!focus().equals(gamePackage))return false;
        synchronized(controlLock){if(stopped||!requested||token!=controlVersion.get()||System.nanoTime()-frame.nanos>2_000_000_000L)return false;if(input==null)input=new Input();input.tap(point[0]*capture.physicalWidth,point[1]*capture.physicalHeight);return true;}
    }
    private boolean pressHome(long token)throws Exception{synchronized(controlLock){if(stopped||!requested||token!=controlVersion.get())return false;if(input==null)input=new Input();input.home();return true;}}
    private static boolean buttonColor(Capture.Frame frame,float[]p,boolean green){if(p==null)return false;int x=Math.round(p[0]*frame.width),y=Math.round(p[1]*frame.height),yes=0,n=0;
        for(int dy=-24;dy<=24;dy+=4)for(int dx=-40;dx<=40;dx+=4){int c=frame.pixel(x+dx,y+dy),r=(c>>16)&255,g=(c>>8)&255,b=c&255;n++;if(green?g>r*1.08&&g>b*1.2:b>r+30&&b>g-15&&g>45)yes++;}return yes>n*.24;
    }
    private static String command(String...args)throws Exception{Process p=new ProcessBuilder(args).redirectErrorStream(true).start();Thread timeout=new Thread(()->{try{if(!p.waitFor(4,TimeUnit.SECONDS))p.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"android-command-timeout");timeout.setDaemon(true);timeout.start();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream()))){StringBuilder b=new StringBuilder();String line;while((line=reader.readLine())!=null){if(b.length()+line.length()>1_048_576)throw new IOException("Android command response too large");b.append(line).append('\n');}if(p.waitFor()!=0)throw new IOException("Android system command failed");return b.toString().trim();}finally{p.destroy();}
    }
    private static String focus()throws Exception{
        String windows=command("/system/bin/dumpsys","window","displays");Matcher m=Pattern.compile("mCurrentFocus=.*? ([A-Za-z0-9_.]+)/",Pattern.MULTILINE).matcher(windows);if(m.find())return m.group(1);if(windows.contains("mCurrentFocus=")&&windows.contains("NotificationShade"))return "com.android.systemui";
        String activities=command("/system/bin/dumpsys","activity","activities");m=Pattern.compile("(?:mResumedActivity|topResumedActivity).*? ([A-Za-z0-9_.]+)/").matcher(activities);String pkg="";while(m.find())pkg=m.group(1);return pkg;
    }
    private void launchGame(long token)throws Exception{
        if(gamePackage.isEmpty())throw new IOException("Game package has not been identified yet");String user=command("/system/bin/am","get-current-user");if(!user.matches("[0-9]+"))throw new IOException("Cannot identify Android user");
        String result=command("/system/bin/cmd","package","resolve-activity","--brief","--user",user,"-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER",gamePackage),component="";
        for(String s:result.split("\\n"))if(s.trim().startsWith(gamePackage+"/"))component=s.trim();if(!component.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+"))throw new IOException("Cannot identify game launcher");
        synchronized(controlLock){if(!requested||stopped||token!=controlVersion.get())return;command("/system/bin/am","start","--user",user,"-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER","-n",component);}
    }
}

package com.beadout.bot;

import android.graphics.Bitmap;
import android.os.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.lang.Process;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.*;

/** Entrypoint loaded from APK dex using root app_process. Protocol: newline-delimited JSON on pipes. */
public final class RootMain {
    private static volatile boolean stopped=false,playing=false,probe=false,snapshot=false,inspect=false;
    private static volatile boolean autoResume=false;
    private static final AtomicReference<Config> pending=new AtomicReference<>();
    private static volatile long heartbeat=System.nanoTime();
    private static final Object outputLock=new Object();
    private static void emit(String type,String text){
        try{JSONObject j=new JSONObject().put("type",type).put("text",text);synchronized(outputLock){System.out.println(j);System.out.flush();}}
        catch(JSONException ignored){}
    }
    private static void commands(){
        try(BufferedReader r=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){
            String line;while((line=r.readLine())!=null){
                if(line.length()>262144)throw new IOException("Command too large");
                JSONObject j=new JSONObject(line);String cmd=j.getString("cmd");heartbeat=System.nanoTime();
                switch(cmd){
                    case "ping":break;
                    case "stop":autoResume=false;stopped=true;playing=false;return;
                    case "pause":autoResume=false;playing=false;probe=false;emit("status","Paused; inspection continues");break;
                    case "inspect":autoResume=false;inspect=true;playing=false;probe=false;break;
                    case "play":autoResume=true;playing=true;probe=false;inspect=true;break;
                    case "probe":autoResume=false;probe=true;playing=false;inspect=true;break;
                    case "snap":snapshot=true;break;
                    case "config":pending.set(Config.parse(j.getJSONObject("config")));autoResume=false;playing=false;probe=false;inspect=false;break;
                    default:emit("error","Unknown command");
                }
            }
        }catch(Exception e){emit("error","Control channel: "+e.getMessage());}
        finally{stopped=true;playing=false;}
    }
    private static String foreground()throws Exception{
        Process p=new ProcessBuilder("/system/bin/dumpsys","activity","activities").redirectError(new File("/dev/null")).start();
        Thread timeout=new Thread(()->{try{if(!p.waitFor(2,TimeUnit.SECONDS))p.destroyForcibly();}catch(InterruptedException ignored){}},"focus-timeout");timeout.setDaemon(true);timeout.start();
        String found="";
        try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){
            String s;Pattern pattern=Pattern.compile("(?:mResumedActivity|topResumedActivity).*? ([a-zA-Z0-9_.]+)/");
            while((s=r.readLine())!=null){Matcher m=pattern.matcher(s);if(m.find())found=m.group(1);}
        }finally{p.destroy();}
        if(found.isEmpty())throw new IOException("Cannot identify foreground game; input disabled");return found;
    }
    private static String commandText(String...args)throws Exception{
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        Thread timeout=new Thread(()->{try{if(!p.waitFor(5,TimeUnit.SECONDS))p.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"launch-timeout");timeout.setDaemon(true);timeout.start();
        StringBuilder text=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){
            String line;while((line=r.readLine())!=null){if(text.length()+line.length()>1_048_576)throw new IOException("Command response too large");text.append(line).append('\n');}
            if(p.waitFor()!=0)throw new IOException("Android launch command failed");return text.toString().trim();
        }finally{p.destroy();}
    }
    private static void launchGame(String pkg)throws Exception{
        String user=commandText("/system/bin/am","get-current-user");
        if(!user.matches("[0-9]+"))throw new IOException("Cannot determine current Android user");
        String resolved=commandText("/system/bin/cmd","package","resolve-activity","--brief","--user",user,"-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER",pkg);
        String component="";
        for(String line:resolved.split("\\n"))if(line.trim().startsWith(pkg+"/"))component=line.trim();
        if(!component.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+"))throw new IOException("Cannot resolve game's launcher activity");
        if(!playing||stopped)return;
        commandText("/system/bin/am","start","--user",user,"-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER","-n",component);
    }
    private static boolean newLevelReady(Vision.Observation o){
        if(o.lanes.isEmpty()||o.confidence()<.995)return false;
        for(int c:o.slots)if(c!=Vision.EMPTY)return false;
        boolean square=false;for(int c:o.board)square|=c>=0;
        int count=0;for(int[]lane:o.lanes){int occupied=0;for(int c:lane){if(c==Vision.UNKNOWN)return false;if(c>=0){count++;occupied++;}}if(lane.length==0||occupied<lane.length*.8)return false;}
        for(Vision.FeedObservation feed:o.feeds)for(int c:feed.beads){if(c<0)return false;count++;}
        return square&&count>=Planner.CAPACITY;
    }
    public static void main(String[]args){
        try{
            if(android.os.Process.myUid()!=0)throw new IOException("Root process required");
            Looper.prepareMainLooper();
            try{Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);}catch(Exception ignored){}
            Thread commandThread=new Thread(RootMain::commands,"control");commandThread.setDaemon(true);commandThread.start();
            run();
        }catch(Throwable e){emit("error",e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{stopped=true;emit("stopped","Root service exited");System.exit(0);}
    }
    private static void run()throws Exception{
        Config config=new Config();Vision vision=new Vision(config);Tracker tracker=new Tracker(config.interval);
        Input input=null;Planner planner=new Planner(Config.NAMES.length,config.width,config.depth,120);
        boolean verified=false,probeRunning=false;
        int probeBaseline=0;
        String expectedBoard=null;long tapTime=0,lastStatus=0,lastUseful=System.nanoTime(),lastFrame=0;
        int stability=0;String lastBoard="";
        long unstableSince=0;
        Planner.State lastState=null;
        long lastStateTime=0;
        Planner.Result cachedPlan=null;
        String plannedKey="";
        LevelCycle cycle=new LevelCycle();
        long cycleStarted=0,errorSince=0;
        String recognitionError=null;
        try(Capture capture=new Capture()){
            emit("ready","Android "+Build.VERSION.RELEASE+" / SDK "+Build.VERSION.SDK_INT+" / "+Build.MODEL+"; "+capture.backend);
            if(!capture.warning.isEmpty())emit("warning",capture.warning);
            while(!stopped){
                long tick=System.nanoTime();
                if(tick-heartbeat>8_000_000_000L)throw new IOException("Controller heartbeat lost; stopped");
                Config incoming=pending.getAndSet(null);
                if(incoming!=null){
                    config=incoming;vision=new Vision(config);tracker=new Tracker(config.interval);
                    planner=new Planner(Config.NAMES.length,config.width,config.depth,120);
                    verified=false;probeRunning=false;expectedBoard=null;lastState=null;stability=0;
                    cachedPlan=null;plannedKey="";
                    cycle=new LevelCycle();cycleStarted=0;recognitionError=null;errorSince=0;
                    emit("status","Calibration loaded. Open the game, then inspect.");
                }
                if(!playing&&cycle.transitioning()){cycle=new LevelCycle();cycleStarted=0;}
                if(playing&&cycle.homeWaiting()){
                    LevelCycle.Action action=cycle.update(tick,false,false,false);
                    if(action==LevelCycle.Action.LAUNCH&&!stopped&&playing){launchGame(config.packageName);emit("status","Three seconds elapsed; reopening Bead Out.");}
                    Thread.sleep(50);continue; // Do not capture the launcher during the three-second wait.
                }
                if(!snapshot&&!inspect){Thread.sleep(100);continue;}
                capture.verifyGeometry();long captureStart=System.nanoTime();Capture.Frame frame=capture.next();
                long captureMs=(System.nanoTime()-captureStart)/1_000_000;
                if(stopped)break;
                if(snapshot){
                    try{emit("focus",foreground());}catch(Exception e){emit("warning",e.getMessage());}
                    snapshot=false;Bitmap b=frame.bitmap();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try{b.compress(Bitmap.CompressFormat.PNG,100,bytes);emit("snapshot",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));}
                    finally{b.recycle();bytes.close();}
                }
                if(!inspect)continue;
                if(!config.confirmed||config.lanes.isEmpty()||config.packageName.isEmpty()){
                    emit("status","Calibrate lane paths, bead pitch, game package and square centers first.");inspect=false;continue;
                }
                if(!config.themeReady()||config.feeders.isEmpty()){
                    emit("warning","Recalibrate four EMPTY slots, background, loop lanes and left/right feeder paths for this layout.");autoResume=false;playing=false;probe=false;inspect=false;continue;
                }
                boolean autoCycle=playing&&config.autoNext&&config.navigationReady();
                if(autoCycle){
                    boolean win=config.winMarker.confidence(frame)>=.90&&config.nextMarker.confidence(frame)>=.90;
                    boolean menu=!win&&config.menuMarker!=null&&config.menuPlayMarker!=null&&config.menuMarker.confidence(frame)>=.90&&config.menuPlayMarker.confidence(frame)>=.90;
                    if(win||menu||cycle.transitioning()){
                        boolean game=config.packageName.equals(foreground());
                        boolean ready=!win&&!menu&&game&&newLevelReady(vision.read(frame));
                        LevelCycle.Action action=cycle.update(tick,win,game,ready,menu);
                        recognitionError=null;errorSince=0;
                        if(action==LevelCycle.Action.HOME){
                            if(!playing||stopped)continue;
                            if(input==null)input=new Input();
                            if(!playing||stopped)continue;
                            input.home();cycle.homeSent(System.nanoTime());cycleStarted=System.nanoTime();
                            emit("status","Leaving completion/ad screen via Android Home for three seconds.");
                        }else if(action==LevelCycle.Action.NEXT){
                            if(!playing||stopped)continue;
                            if(!game||!config.packageName.equals(foreground()))throw new IOException("Game lost focus before Next Level");
                            if(input==null)input=new Input();
                            if(!playing||stopped)continue;
                            input.tap(config.nextMarker.x*capture.physicalWidth,config.nextMarker.y*capture.physicalHeight);
                            emit("status","Next Level tapped once; waiting for a readable new board.");
                        }else if(action==LevelCycle.Action.PLAY_MENU){
                            if(!playing||stopped)continue;
                            if(config.menuMarker==null||config.menuPlayMarker==null||!game||!config.packageName.equals(foreground()))throw new IOException("Cannot confirm Bead Out menu before Play");
                            if(input==null)input=new Input();
                            if(!playing||stopped)continue;
                            input.tap(config.menuPlayMarker.x*capture.physicalWidth,config.menuPlayMarker.y*capture.physicalHeight);
                            emit("status","Bead Out Home menu recognized. Play tapped once; waiting for the level.");
                        }else if(action==LevelCycle.Action.RESET){
                            tracker=new Tracker(config.interval);lastState=null;cachedPlan=null;plannedKey="";expectedBoard=null;
                            verified=false;playing=autoResume;probe=false;
                            probeRunning=false;stability=0;lastBoard="";unstableSince=0;lastUseful=tick;cycleStarted=0;
                            emit("status","New level confirmed. Rechecking movement and resuming predictive play with multiple collectors.");
                        }else if(action==LevelCycle.Action.FAIL||(cycleStarted!=0&&tick-cycleStarted>40_000_000_000L)){
                            throw new IOException("Level recovery exhausted. Next was not repeated; no ad buttons were tapped.");
                        }
                        if(cycle.transitioning()||action==LevelCycle.Action.RESET){Thread.sleep(150);continue;}
                    }
                    if(recognitionError!=null){
                        if(tick-errorSince<2_000_000_000L){Thread.sleep(150);continue;}
                        autoResume=false;playing=false;probe=false;inspect=false;verified=false;lastState=null;tracker=new Tracker(config.interval);
                        emit("error",recognitionError);recognitionError=null;continue;
                    }
                }
                try{
                    Vision.Observation obs=vision.read(frame);
                    String board=obs.boardKey();stability=board.equals(lastBoard)?stability+1:0;lastBoard=board;
                    if(stability<1){
                        if(unstableSince==0)unstableSince=tick;
                        if(tick-unstableSince>3_000_000_000L)throw new Exception("Board animation or recognition did not settle");
                        Thread.sleep(150);continue;
                    }
                    unstableSince=0;
                    Planner.State state=tracker.update(obs);
                    if(tracker.completions()>0)verified=true;
                    if(lastState!=null&&state.beadCount()<lastState.beadCount())lastUseful=tick;
                    if(tracker.settling()){lastState=null;Thread.sleep(150);continue;}
                    if(expectedBoard!=null){
                        if(!board.equals(expectedBoard)){expectedBoard=null;lastUseful=tick;}
                        else if(tick-tapTime>5_000_000_000L)throw new Exception("Tap did not change board or active slots");
                    }
                    if(lastState!=null&&tracker.speedSamples()>=3){
                        Planner.State predicted=lastState.copy();
                        Planner.advance(predicted,(obs.nanos-lastStateTime)/1e9,false);
                        int discrepancy=Math.abs(predicted.beadCount()-state.beadCount());
                        if(discrepancy>Math.max(3,state.lanes.size()*2))throw new Exception("Pickup timing disagrees with simulation; recalibrate");
                    }
                    if(probeRunning){
                        boolean active=false;for(int c:state.colors)active|=c>=0;
                        if(!active&&expectedBoard==null&&tracker.completions()>probeBaseline){verified=true;probeRunning=false;lastUseful=tick;playing=autoResume;emit("status",autoResume?"100-bead probe verified. Resuming predictive play.":"100-bead probe verified. Predictive play is available.");}
                    }
                    lastState=state.copy();lastStateTime=obs.nanos;
                    long searchStart=System.nanoTime();Planner.Result plan=null;
                    boolean changed=stability==0||tick-lastFrame>2_000_000_000L;
                    String key=state.key();
                    if(!key.equals(plannedKey)||cachedPlan==null){cachedPlan=planner.search(state,false);plannedKey=key;}
                    plan=cachedPlan;
                    long searchMs=(System.nanoTime()-searchStart)/1_000_000;
                    int action=plan==null?Planner.WAIT:plan.firstAction();
                    boolean enabled=playing||(probe&&!probeRunning);
                    if(probe&&!probeRunning){
                        for(int c:state.colors)if(c>=0){probe=false;enabled=false;emit("warning","Start the pickup probe with all active slots empty.");break;}
                    }
                    boolean timingReady=tracker.speedSamples()>=3||(probe&&!probeRunning);
                    if(enabled&&stability>=1&&timingReady&&expectedBoard==null&&action>=0){
                        int color=state.board[action];boolean duplicate=false;for(int c:state.colors)duplicate|=c==color;
                        if(duplicate)throw new Exception("Duplicate-color collection order is uncalibrated; paused");
                        if(!config.packageName.equals(foreground()))throw new Exception("Game is not in foreground; input paused");
                        if(stopped||(!playing&&!probe))continue;
                        float[]point=config.buttons.get(action);
                        if(input==null)input=new Input();
                        if(stopped||(!playing&&!probe))continue;
                        input.tap(point[0]*capture.physicalWidth,point[1]*capture.physicalHeight);
                        tracker.expectTap(color,state.empty());expectedBoard=board;tapTime=tick;lastUseful=tick;
                        if(probe){probeBaseline=tracker.completions();probe=false;probeRunning=true;}
                        emit("action","Tapped "+Config.NAMES[color]+" at button "+(action+1)+"; "+plan.expanded+" predictive states");
                        lastState=null; // Await observed tap before predicting collection.
                    }
                    if(tick-lastStatus>1_000_000_000L){
                        long used=(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576;
                        String next=action<0?"wait":Config.NAMES[state.board[action]]+" #"+(action+1);
                        long nativeMb=Debug.getNativeHeapAllocatedSize()/1048576;
                        emit("status",capture.backend+" | capture "+captureMs+"ms search "+searchMs+"ms heap "+used+"MiB native "+nativeMb+"MiB PSS "+Debug.getPss()/1024+"MiB\n"+
                            tracker.note+"; next "+next+"; "+(verified?"completion observed":"no completion yet; other collectors may be needed")+"\n"+
                            (plan==null?"":describe(plan))+"\nAuto-next: "+(config.autoNext?(config.navigationReady()?"ready":"teach real Congrats header and Next button"):"off"));
                        lastStatus=tick;
                    }
                    if((playing||probeRunning)&&tick-lastUseful>90_000_000_000L)throw new Exception("No verified progress for 90 seconds");
                    if(changed)lastFrame=tick;
                }catch(Exception e){
                    if(autoCycle){recognitionError=e.getMessage();errorSince=tick;emit("status","Board changed; checking for the calibrated win screen before stopping.");}
                    else{autoResume=false;playing=false;probe=false;inspect=false;probeRunning=false;verified=false;lastState=null;tracker=new Tracker(config.interval);emit("error",e.getMessage());}
                }
                double interval=tracker.interval();long delay=(long)Math.max(80,Math.min(500,interval*2000));
                long elapsed=(System.nanoTime()-tick)/1_000_000;Thread.sleep(Math.max(10,delay-elapsed));
            }
        }
    }
    private static String describe(Planner.Result result){
        StringBuilder b=new StringBuilder("Plan: ");
        for(Planner.Event e:result.actions)b.append(e.index<0?"wait":"#"+(e.index+1)).append(" @").append(Math.round(e.at*10)/10.0).append("s; ");
        return b.append("predicted completions ").append(result.predicted.completed).toString();
    }
}

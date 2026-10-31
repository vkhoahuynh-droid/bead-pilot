package com.beadout.bot;

import org.json.*;
import java.util.*;

public final class Config {
    public static final String[] NAMES={"yellow","white","lavender","orange","green","blue","lime","purple","pink","peach","brown","olive","red","royal_blue"};
    public static final int[] RGB={0xfff5e300,0xffece7e8,0xff9682f5,0xffff5505,0xff00a307,0xff10bcf5,0xffafe100,0xff850092,0xffc3005f,0xffffa583,0xffae5810,0xff8ba013,0xffed1b35,0xff2879f5};
    public final AdaptivePalette palette=new AdaptivePalette();
    public final ArrayList<float[]> slots=new ArrayList<>(),buttons=new ArrayList<>(),trackOutline=new ArrayList<>();
    public final ArrayList<ArrayList<float[]>> lanes=new ArrayList<>();
    public static final class FeedPath {
        public final int lane;
        public final ArrayList<float[]> points;
        public boolean skipConnector=false;
        public FeedPath(int lane,List<float[]>points){this.lane=lane;this.points=new ArrayList<>(points);}
    }
    public final ArrayList<FeedPath> feeders=new ArrayList<>();
    public boolean confirmed=false;
    public boolean automatic=false;
    public int rowUnit=1,loopRows=0;
    public double slotLift=0;
    public boolean[] clickable=new boolean[0],unknownBoxes=new boolean[0];
    public int[] unlockValues=new int[0];
    public boolean requireBorder=true;
    public boolean autoNext=true;
    public SceneMarker winMarker,nextMarker,menuPlayMarker,menuMarker;
    public final int[] emptySlotRgb=new int[4];
    public final boolean[] emptySlotKnown=new boolean[4];
    public int backgroundRgb;
    public boolean backgroundKnown=false;
    public double pitch=7.0, interval=0.1;
    public int width=128,depth=5;
    public String packageName="";
    public boolean navigationReady(){return winMarker!=null&&nextMarker!=null&&winMarker.y<.4f;}
    public boolean themeReady(){if(!backgroundKnown)return false;for(boolean known:emptySlotKnown)if(!known)return false;return true;}
    public void clearLevelGeometry(){palette.clear();slots.clear();buttons.clear();trackOutline.clear();lanes.clear();feeders.clear();Arrays.fill(emptySlotKnown,false);backgroundKnown=false;confirmed=false;}
    public Config(){
        for(int x:new int[]{190,257,326,394})slots.add(new float[]{x/582f,600/1280f});
        int[][] pts={{180,707},{254,707},{329,707},{403,707},{180,800},{254,800},{329,800},{403,800},{180,886},{403,886},{180,968},{403,968},{254,1050},{329,1050},{403,1050}};
        for(int[]p:pts)buttons.add(new float[]{p[0]/582f,p[1]/1280f});
    }
    private static JSONArray points(List<float[]> pts)throws JSONException{
        JSONArray arr=new JSONArray();for(float[]p:pts)arr.put(new JSONArray().put(p[0]).put(p[1]));return arr;
    }
    public JSONObject json()throws JSONException{
        JSONObject j=new JSONObject().put("confirmed",confirmed).put("requireBorder",requireBorder).put("autoNext",autoNext).put("pitch",pitch).put("interval",interval)
            .put("automatic",automatic).put("rowUnit",rowUnit).put("loopRows",loopRows).put("slotLift",slotLift)
            .put("width",width).put("depth",depth).put("package",packageName).put("slots",points(slots)).put("buttons",points(buttons)).put("trackOutline",points(trackOutline));
        JSONArray paletteColors=new JSONArray();for(int i=0;i<palette.size();i++)paletteColors.put(palette.rgb(i));j.put("palette",paletteColors);
        JSONArray arr=new JSONArray();for(ArrayList<float[]> lane:lanes)arr.put(points(lane));j.put("lanes",arr);
        JSONArray fs=new JSONArray();for(FeedPath feed:feeders)fs.put(new JSONObject().put("lane",feed.lane).put("points",points(feed.points)).put("skipConnector",feed.skipConnector));j.put("feeders",fs);
        if(winMarker!=null)j.put("winMarker",winMarker.json());if(nextMarker!=null)j.put("nextMarker",nextMarker.json());
        if(menuPlayMarker!=null)j.put("menuPlayMarker",menuPlayMarker.json());
        if(menuMarker!=null)j.put("menuMarker",menuMarker.json());
        JSONArray empty=new JSONArray();for(int i=0;i<4;i++)empty.put(emptySlotKnown[i]?emptySlotRgb[i]:JSONObject.NULL);
        j.put("emptySlotRgb",empty);if(backgroundKnown)j.put("backgroundRgb",backgroundRgb);
        JSONArray open=new JSONArray(),unknown=new JSONArray(),unlocks=new JSONArray();for(boolean b:clickable)open.put(b);for(boolean b:unknownBoxes)unknown.put(b);for(int n:unlockValues)unlocks.put(n);
        return j.put("clickable",open).put("unknownBoxes",unknown).put("unlocks",unlocks);
    }
    private static void readPoints(JSONArray arr,List<float[]> into)throws JSONException{
        into.clear();for(int i=0;i<arr.length();i++){JSONArray p=arr.getJSONArray(i);float x=(float)p.getDouble(0),y=(float)p.getDouble(1);
            if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1)throw new JSONException("Point outside frame");into.add(new float[]{x,y});}
    }
    public static Config parse(JSONObject j)throws JSONException{
        Config c=new Config();JSONArray palette=j.optJSONArray("palette");if(palette!=null){if(palette.length()>AdaptivePalette.LIMIT)throw new JSONException("Too many palette colors");for(int i=0;i<palette.length();i++)c.palette.restore(palette.getInt(i));}
        c.confirmed=j.optBoolean("confirmed",false);c.requireBorder=j.optBoolean("requireBorder",true);c.pitch=j.optDouble("pitch",7);c.interval=j.optDouble("interval",.1);
        c.automatic=j.optBoolean("automatic",false);c.rowUnit=j.optInt("rowUnit",1);c.loopRows=j.optInt("loopRows",0);c.slotLift=j.optDouble("slotLift",0);
        c.autoNext=j.optBoolean("autoNext",true);
        if(j.has("winMarker"))c.winMarker=SceneMarker.parse(j.getJSONObject("winMarker"));
        if(j.has("nextMarker"))c.nextMarker=SceneMarker.parse(j.getJSONObject("nextMarker"));
        if(j.has("menuPlayMarker"))c.menuPlayMarker=SceneMarker.parse(j.getJSONObject("menuPlayMarker"));
        if(j.has("menuMarker"))c.menuMarker=SceneMarker.parse(j.getJSONObject("menuMarker"));
        JSONArray empty=j.optJSONArray("emptySlotRgb");
        if(empty!=null){if(empty.length()!=4)throw new JSONException("Four empty-slot colors required");for(int i=0;i<4;i++)if(!empty.isNull(i)){c.emptySlotRgb[i]=empty.getInt(i);c.emptySlotKnown[i]=true;}}
        if(j.has("backgroundRgb")){c.backgroundRgb=j.getInt("backgroundRgb");c.backgroundKnown=true;}
        c.width=j.optInt("width",128);c.depth=j.optInt("depth",5);c.packageName=j.optString("package","");
        if(j.has("trackOutline"))readPoints(j.getJSONArray("trackOutline"),c.trackOutline);
        if(c.trackOutline.size()>1000)throw new JSONException("Track outline is too large");
        if(j.has("slots"))readPoints(j.getJSONArray("slots"),c.slots);
        if(j.has("buttons"))readPoints(j.getJSONArray("buttons"),c.buttons);
        JSONArray opens=j.optJSONArray("clickable"),unknown=j.optJSONArray("unknownBoxes"),unlocks=j.optJSONArray("unlocks");
        if(opens!=null){c.clickable=new boolean[opens.length()];for(int i=0;i<opens.length();i++)c.clickable[i]=opens.getBoolean(i);}
        if(unknown!=null){c.unknownBoxes=new boolean[unknown.length()];for(int i=0;i<unknown.length();i++)c.unknownBoxes[i]=unknown.getBoolean(i);}
        if(unlocks!=null){c.unlockValues=new int[unlocks.length()];for(int i=0;i<unlocks.length();i++)c.unlockValues[i]=unlocks.getInt(i);}
        JSONArray lanes=j.optJSONArray("lanes");if(lanes!=null)for(int i=0;i<lanes.length();i++){ArrayList<float[]>lane=new ArrayList<>();readPoints(lanes.getJSONArray(i),lane);c.lanes.add(lane);}
        JSONArray feeds=j.optJSONArray("feeders");if(feeds!=null)for(int i=0;i<feeds.length();i++){
            JSONObject feed=feeds.getJSONObject(i);ArrayList<float[]>pts=new ArrayList<>();readPoints(feed.getJSONArray("points"),pts);
            int lane=feed.getInt("lane");if(lane<0||lane>=c.lanes.size()||pts.size()<2||pts.size()>1000)throw new JSONException("Invalid feeder path");FeedPath fp=new FeedPath(lane,pts);fp.skipConnector=feed.optBoolean("skipConnector",false);c.feeders.add(fp);
        }
        if(c.slots.size()!=4||c.buttons.size()>80||c.lanes.size()>16||c.feeders.size()>64||!Double.isFinite(c.pitch)||!Double.isFinite(c.interval)||c.pitch<3||c.pitch>40||c.interval<.01||c.interval>10||c.width<1||c.width>512||c.depth<1||c.depth>12)
            throw new JSONException("Invalid calibration limits");
        for(ArrayList<float[]>lane:c.lanes)if(lane.size()<3||lane.size()>1000)throw new JSONException("Trace each closed lane with at least 3 points");
        if((c.rowUnit!=1&&c.rowUnit!=5)||c.loopRows<0||c.loopRows>1000||!Double.isFinite(c.slotLift)||c.slotLift<0||c.slotLift>.1)throw new JSONException("Invalid automatic model");
        return c;
    }
}

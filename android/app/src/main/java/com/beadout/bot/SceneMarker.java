package com.beadout.bot;

import android.graphics.Bitmap;
import org.json.*;

/** Tiny numeric pixel fingerprint, not a screenshot file. Teach only static text/button regions. */
public final class SceneMarker {
    private static final int COLS=16,ROWS=8;
    public final float x,y,w,h;
    public final int[]rgb;
    public SceneMarker(float x,float y,float w,float h,int[]rgb){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(w)||!Float.isFinite(h)||x<0||x>1||y<0||y>1||w<=0||w>.5||h<=0||h>.3||rgb.length!=COLS*ROWS)
            throw new IllegalArgumentException("Invalid scene marker");
        this.x=x;this.y=y;this.w=w;this.h=h;this.rgb=rgb.clone();
    }
    public static SceneMarker teach(Bitmap b,float x,float y,boolean button){
        float w=(button?80f:110f)/b.getWidth(),h=(button?32f:40f)/b.getHeight();int[]pixels=new int[COLS*ROWS];
        for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){
            int px=Math.max(0,Math.min(b.getWidth()-1,Math.round((x-w/2+w*(c+.5f)/COLS)*b.getWidth())));
            int py=Math.max(0,Math.min(b.getHeight()-1,Math.round((y-h/2+h*(r+.5f)/ROWS)*b.getHeight())));
            pixels[r*COLS+c]=b.getPixel(px,py);
        }
        SceneMarker marker=new SceneMarker(x,y,w,h,pixels);
        if(!marker.informative())throw new IllegalArgumentException("Choose a region with static text and contrast, not a flat background");return marker;
    }
    public boolean informative(){
        double sum=0,sum2=0;for(int c:rgb){double v=(.2126*((c>>16)&255)+.7152*((c>>8)&255)+.0722*(c&255));sum+=v;sum2+=v*v;}
        return sum2/rgb.length-Math.pow(sum/rgb.length,2)>144;
    }
    public double confidence(Capture.Frame f){
        int matched=0;
        for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){
            int sample=f.pixel(Math.round((x-w/2+w*(c+.5f)/COLS)*f.width),Math.round((y-h/2+h*(r+.5f)/ROWS)*f.height));
            int expected=rgb[r*COLS+c];int dr=((sample>>16)&255)-((expected>>16)&255),dg=((sample>>8)&255)-((expected>>8)&255),db=(sample&255)-(expected&255);
            if(dr*dr+dg*dg+db*db<=45*45)matched++;
        }
        return (double)matched/rgb.length;
    }
    public JSONObject json()throws JSONException{
        JSONArray arr=new JSONArray();for(int value:rgb)arr.put(value);
        return new JSONObject().put("x",x).put("y",y).put("w",w).put("h",h).put("rgb",arr);
    }
    public static SceneMarker parse(JSONObject j)throws JSONException{
        JSONArray arr=j.getJSONArray("rgb");if(arr.length()!=COLS*ROWS)throw new JSONException("Invalid marker size");
        int[]p=new int[arr.length()];for(int i=0;i<p.length;i++)p[i]=arr.getInt(i);
        SceneMarker marker=new SceneMarker((float)j.getDouble("x"),(float)j.getDouble("y"),(float)j.getDouble("w"),(float)j.getDouble("h"),p);
        if(!marker.informative())throw new JSONException("Scene marker lacks contrast");return marker;
    }
}

package com.beadout.bot;

import android.graphics.*;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.view.Surface;
import java.io.*;
import java.lang.Process;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/** Root-side capture. No file or network output. Frames and fallback PNG stay in RAM. */
public final class Capture implements AutoCloseable {
    public static final class Frame {
        public final int width,height;
        public final int[] pixels;
        public final long nanos;
        private final ByteBuffer data;
        private final int rowStride,pixelStride;
        Frame(int w,int h,int[]p){this(w,h,p,System.nanoTime());}
        Frame(int w,int h,int[]p,long timestamp){width=w;height=h;pixels=p;nanos=timestamp;data=null;rowStride=pixelStride=0;}
        Frame(int w,int h,Image.Plane p,long timestamp){width=w;height=h;pixels=null;nanos=timestamp>0?timestamp:System.nanoTime();data=p.getBuffer();rowStride=p.getRowStride();pixelStride=p.getPixelStride();}
        int pixel(int x,int y){
            x=Math.max(0,Math.min(width-1,x));y=Math.max(0,Math.min(height-1,y));
            if(pixels!=null)return pixels[y*width+x];
            int at=y*rowStride+x*pixelStride;
            return 0xff000000|((data.get(at)&255)<<16)|((data.get(at+1)&255)<<8)|(data.get(at+2)&255);
        }
        Bitmap bitmap(){
            if(pixels!=null)return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
            int[]copy=new int[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)copy[y*width+x]=pixel(x,y);
            return Bitmap.createBitmap(copy,width,height,Bitmap.Config.ARGB_8888);
        }
    }
    public final int physicalWidth,physicalHeight,rotation,width,height;
    private final Object displayManager;
    private final Method displayInfo;
    private ImageReader reader;
    private Image acquired;
    private VirtualDisplay virtualDisplay;
    private int[][] buffers;
    private int cursor;
    public String backend,warning="";
    public Capture()throws Exception{
        Class<?> cls=Class.forName("android.hardware.display.DisplayManagerGlobal");
        displayManager=cls.getMethod("getInstance").invoke(null);displayInfo=cls.getMethod("getDisplayInfo",int.class);
        Object info=displayInfo.invoke(displayManager,0);if(info==null)throw new IOException("No primary display");
        physicalWidth=field(info,"logicalWidth");physicalHeight=field(info,"logicalHeight");rotation=field(info,"rotation");
        width=582;height=Math.round(width*(float)physicalHeight/physicalWidth);
        if(height>2200||height<700)throw new IOException("Use a portrait display");
        try{
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);
            Method create=android.hardware.display.DisplayManager.class.getMethod("createVirtualDisplay",String.class,int.class,int.class,int.class,Surface.class);
            virtualDisplay=(VirtualDisplay)create.invoke(null,"BeadPilot",width,height,0,reader.getSurface());
            backend="display mirror / RAM";
        }catch(Exception e){fallback(e);}
    }
    private static int field(Object o,String name)throws Exception{return o.getClass().getField(name).getInt(o);}
    public void verifyGeometry()throws Exception{
        Object info=displayInfo.invoke(displayManager,0);
        if(info==null||field(info,"logicalWidth")!=physicalWidth||field(info,"logicalHeight")!=physicalHeight||field(info,"rotation")!=rotation)
            throw new IOException("Display rotated or changed size; restart and recalibrate");
    }
    private void fallback(Exception e){
        if(virtualDisplay!=null){virtualDisplay.release();virtualDisplay=null;}
        if(reader!=null){reader.close();reader=null;}
        backend="screencap pipe / RAM";warning="Continuous capture unavailable: "+e.getClass().getSimpleName()+". Slower RAM-only fallback.";
    }
    public Frame next()throws Exception{
        if(acquired!=null){acquired.close();acquired=null;}
        if(reader!=null){
            try{
            long deadline=System.nanoTime()+2_000_000_000L;
            while(System.nanoTime()<deadline){
                Image img=reader.acquireLatestImage();
                if(img==null){Thread.sleep(15);continue;}
                acquired=img;
                return new Frame(width,height,img.getPlanes()[0],img.getTimestamp());
            }
            fallback(new IOException("No display frames within two seconds"));
            }catch(RuntimeException e){
                if(acquired!=null){acquired.close();acquired=null;}
                fallback(e);
            }
        }
        if(buffers==null)buffers=new int[][]{new int[width*height],new int[width*height]};
        int[] out=buffers[cursor++&1];
        Process p=new ProcessBuilder("/system/bin/screencap","-p").redirectError(new File("/dev/null")).start();
        Thread watchdog=new Thread(()->{try{if(!p.waitFor(5,TimeUnit.SECONDS))p.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"capture-timeout");
        watchdog.setDaemon(true);watchdog.start();
        try(InputStream input=p.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream(1024*1024)){
            byte[] chunk=new byte[16384];int n;while((n=input.read(chunk))!=-1){
                if(bytes.size()+n>32*1024*1024)throw new IOException("Oversized capture");bytes.write(chunk,0,n);
            }
            if(p.waitFor()!=0)throw new IOException("Root screencap failed");
            byte[] png=bytes.toByteArray();Bitmap full=BitmapFactory.decodeByteArray(png,0,png.length);
            if(full==null)throw new IOException("Invalid screencap stream");
            Bitmap scaled=null;
            try{scaled=Bitmap.createScaledBitmap(full,width,height,false);scaled.getPixels(out,0,width,0,0,width,height);}
            finally{if(scaled!=null&&scaled!=full)scaled.recycle();full.recycle();}
        }finally{p.destroy();}
        return new Frame(width,height,out);
    }
    public void close(){if(acquired!=null)acquired.close();if(virtualDisplay!=null)virtualDisplay.release();if(reader!=null)reader.close();}
}

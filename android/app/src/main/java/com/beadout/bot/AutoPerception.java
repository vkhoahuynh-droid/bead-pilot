package com.beadout.bot;

import java.util.*;

/** Runtime sampling of automatically discovered geometry. Progress text is authoritative. */
public final class AutoPerception {
    private final Config c;
    private final int[]r=new int[9],g=new int[9],b=new int[9];
    public AutoPerception(Config config){c=config;}
    private int rgb(Capture.Frame f,float x,float y){int px=Math.round(x*f.width),py=Math.round(y*f.height),i=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int v=f.pixel(px+dx,py+dy);r[i]=(v>>16)&255;g[i]=(v>>8)&255;b[i]=v&255;i++;}Arrays.sort(r);Arrays.sort(g);Arrays.sort(b);return 0xff000000|r[4]<<16|g[4]<<8|b[4];}
    private int color(int pixel){
        if(Vision.nearColor(pixel,c.backgroundRgb,10))return Vision.EMPTY;
        double rr=(pixel>>16)&255,gg=(pixel>>8)&255,bb=pixel&255,max=Math.max(rr,Math.max(gg,bb)),min=Math.min(rr,Math.min(gg,bb)),d=max-min,s=max==0?0:d/max,h=0;
        if(d>0){if(max==rr)h=60*((gg-bb)/d%6);else if(max==gg)h=60*((bb-rr)/d+2);else h=60*((rr-gg)/d+4);}if(h<0)h+=360;
        if(s>.5&&max>80){if(h<16||h>344)return 12;if(h<44)return max<180?10:3;if(h<72)return max<180?11:0;if(h<90)return 6;if(h<160)return 4;if(h<206)return 5;if(h<256)return 13;if(h<316)return 7;return 8;}
        int br=(c.backgroundRgb>>16)&255,bg=(c.backgroundRgb>>8)&255,bl=c.backgroundRgb&255;
        if(max<Math.max(br,Math.max(bg,bl))*.88&&s<.55)return Vision.EMPTY;
        return Vision.classify(pixel);
    }
    public int[]slotColors(Capture.Frame f){
        int[]out=new int[4];for(int i=0;i<4;i++){float[]p=c.slots.get(i);int upper=rgb(f,p[0],p[1]-(float)c.slotLift);int label=color(upper);
            if(label>=0){out[i]=label;continue;}
            int center=rgb(f,p[0],p[1]);out[i]=Vision.nearColor(center,c.emptySlotRgb[i],16)||Vision.nearColor(center,c.backgroundRgb,13)?Vision.EMPTY:color(center);
        }return out;
    }
    public Vision.Observation read(Capture.Frame f){
        int[]board=new int[c.buttons.size()];for(int i=0;i<board.length;i++){float[]p=c.buttons.get(i);board[i]=i<c.unknownBoxes.length&&c.unknownBoxes[i]?Vision.UNKNOWN:color(rgb(f,p[0],p[1]));if(board[i]>=0&&(i>=c.clickable.length||!c.clickable[i]))board[i]=Vision.EMPTY;}
        Vision.Observation o=new Vision.Observation(slotColors(f),board,f.nanos);o.rowUnit=c.rowUnit;
        PathGeometry loop=new PathGeometry(c.lanes.get(0),true,f.width,f.height);int n=c.loopRows;int[]ring=new int[n];
        for(int i=0;i<n;i++){float[]p=loop.point((i+.5)*loop.length/n);int v=color(rgb(f,p[0],p[1]));o.total++;if(v==Vision.UNKNOWN){o.unknown++;v=Vision.EMPTY;}ring[i]=v;}
        o.lanes.add(ring);o.fixedLoops.set(0);
        for(Config.FeedPath feed:c.feeders){PathGeometry path=new PathGeometry(feed.points,false,f.width,f.height);float[]origin=feed.points.get(0);double[]near=loop.nearest(origin[0],origin[1]);int entry=((int)Math.round(near[0]/loop.length*n-.5)+n)%n;
            float[]a=feed.points.get(0),b=feed.points.get(1);double skip=feed.skipConnector?Math.hypot((a[0]-b[0])*f.width,(a[1]-b[1])*f.height):0;
            int count=Math.max(0,Math.min(1000,(int)Math.round((path.length-skip)/c.pitch)));int[]rows=new int[count];int at=0;
            for(int i=0;i<count;i++){float[]p=path.point(skip+(i+.5)*c.pitch);int v=color(rgb(f,p[0],p[1]));o.total++;if(v==Vision.UNKNOWN){o.unknown++;continue;}if(v>=0)rows[at++]=v;}
            o.feeds.add(new Vision.FeedObservation(0,entry,Arrays.copyOf(rows,at)));
        }return o;
    }
}

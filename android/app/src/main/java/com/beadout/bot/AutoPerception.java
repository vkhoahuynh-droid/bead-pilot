package com.beadout.bot;

import java.util.*;

/** Runtime sampling of automatically discovered geometry. Progress text is authoritative. */
public final class AutoPerception {
    private final Config c;
    private final int[]r=new int[9],g=new int[9],b=new int[9],held={-1,-1,-1,-1};
    public AutoPerception(Config config){c=config;}
    private int rgb(Capture.Frame f,float x,float y){int px=Math.round(x*f.width),py=Math.round(y*f.height),i=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int v=f.pixel(px+dx,py+dy);r[i]=(v>>16)&255;g[i]=(v>>8)&255;b[i]=v&255;i++;}Arrays.sort(r);Arrays.sort(g);Arrays.sort(b);return 0xff000000|r[4]<<16|g[4]<<8|b[4];}
    private int color(int pixel){return AdaptivePalette.floor(pixel,c.backgroundRgb)?Vision.EMPTY:c.palette.match(pixel);}
    private void learn(Capture.Frame f){
        // Collectors provide stable object-color references before shaded bead
        // highlights. Otherwise a boundary shade can swallow two nearby colors.
        ArrayList<Integer>samples=new ArrayList<>();
        for(float[]p:c.slots)for(int d=-1;d<=1;d++)objectCandidate(samples,rgb(f,p[0]+d*1f/f.width,p[1]-(float)c.slotLift));
        for(int i=0;i<c.buttons.size();i++)if(i>=c.unknownBoxes.length||!c.unknownBoxes[i]){float[]p=c.buttons.get(i);for(int d=-1;d<=1;d++)objectCandidate(samples,rgb(f,p[0]+d*1f/f.width,p[1]));}
        c.palette.observe(samples);samples.clear();
        PathGeometry loop=new PathGeometry(c.lanes.get(0),true,f.width,f.height);
        int n=Math.max(80,c.loopRows*2);for(int i=0;i<n;i++){float[]p=loop.point((i+.5)*loop.length/n);candidate(samples,rgb(f,p[0],p[1]));}
        for(Config.FeedPath feed:c.feeders){PathGeometry path=new PathGeometry(feed.points,false,f.width,f.height);double skip=feed.skipConnector?path.distances[1]:0;
            for(double at=skip+c.pitch*.5;at<path.length;at+=c.pitch*.5){float[]p=path.point(at);candidate(samples,rgb(f,p[0],p[1]));}}
        c.palette.observe(samples);
    }
    private int objectColor(int pixel){return Vision.nearColor(pixel,c.backgroundRgb,8)?Vision.EMPTY:c.palette.match(pixel);}
    private void objectCandidate(List<Integer>samples,int pixel){if(!Vision.nearColor(pixel,c.backgroundRgb,8)&&!AdaptivePalette.darkUnknown(pixel))samples.add(pixel);}
    private void candidate(List<Integer>samples,int pixel){if(!AdaptivePalette.floor(pixel,c.backgroundRgb)&&!AdaptivePalette.darkUnknown(pixel))samples.add(pixel);}
    public int[]slotColors(Capture.Frame f){
        int[]out=new int[4];for(int i=0;i<4;i++){float[]p=c.slots.get(i);int upper=rgb(f,p[0],p[1]-(float)c.slotLift);int label=objectColor(upper);
            if(label>=0){if(held[i]>=0&&AdaptivePalette.distance(upper,c.palette.rgb(held[i]))<1.6)label=held[i];out[i]=label;held[i]=label;continue;}
            int center=rgb(f,p[0],p[1]);out[i]=Vision.nearColor(center,c.emptySlotRgb[i],16)||Vision.nearColor(center,c.backgroundRgb,13)?Vision.EMPTY:objectColor(center);if(out[i]==Vision.EMPTY)held[i]=-1;
        }return out;
    }
    public Vision.Observation read(Capture.Frame f){
        learn(f);
        int[]board=new int[c.buttons.size()];for(int i=0;i<board.length;i++){float[]p=c.buttons.get(i);board[i]=i<c.unknownBoxes.length&&c.unknownBoxes[i]?Vision.UNKNOWN:objectColor(rgb(f,p[0],p[1]));if(board[i]>=0&&(i>=c.clickable.length||!c.clickable[i]))board[i]=Vision.EMPTY;}
        Vision.Observation o=new Vision.Observation(slotColors(f),board,f.nanos);o.rowUnit=c.rowUnit;o.rightFirst=true;
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

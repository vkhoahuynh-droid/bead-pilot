package com.beadout.bot;

import java.util.*;

/** Calibrated centerline sampling; does not invent concealed bead colors. */
public final class Vision {
    public static final int EMPTY=-1,UNKNOWN=-2;
    private static final double[][] PALETTE=new double[Config.RGB.length][];
    private static final int[] VARIANT_COLORS={0xffa400ff,0xff21d600,0xff55d8ff};
    private static final int[] VARIANT_LABELS={7,4,5};
    private static final double[][] VARIANTS=new double[VARIANT_COLORS.length][];
    static{for(int i=0;i<PALETTE.length;i++)PALETTE[i]=lab(Config.RGB[i]);for(int i=0;i<VARIANTS.length;i++)VARIANTS[i]=lab(VARIANT_COLORS[i]);}
    public static final class Observation {
        final int[] slots,board;
        final ArrayList<int[]> lanes=new ArrayList<>();
        final BitSet fixedLoops=new BitSet();
        final int[]progress={-1,-1,-1,-1};
        int rowUnit=1;
        final ArrayList<FeedObservation> feeds=new ArrayList<>();
        boolean topologyValid=true;
        final long nanos;
        int unknown,total;
        Observation(int[]slots,int[]board,long nanos){this.slots=slots;this.board=board;this.nanos=nanos;}
        double confidence(){return total==0?0:1-(double)unknown/total;}
        int[] counts(){int size=Config.NAMES.length;for(int[]lane:lanes)for(int v:lane)size=Math.max(size,v+1);for(FeedObservation feed:feeds)for(int v:feed.beads)size=Math.max(size,v+1);int[]c=new int[size];for(int[]lane:lanes)for(int v:lane)if(v>=0)c[v]+=rowUnit;for(FeedObservation feed:feeds)for(int v:feed.beads)if(v>=0)c[v]+=rowUnit;return c;}
        String boardKey(){return Arrays.toString(slots)+Arrays.toString(board);}
    }
    public static final class FeedObservation {
        final int lane,entry;
        final int[]beads;
        FeedObservation(int lane,int entry,int[]beads){this.lane=lane;this.entry=entry;this.beads=beads;}
    }
    private final Config config;
    private final int[] red=new int[9],green=new int[9],blue=new int[9];
    public Vision(Config c){config=c;}
    static double f(double t){return t>.008856?Math.cbrt(t):7.787*t+16.0/116;}
    static double lin(int c){double v=c/255.0;return v>.04045?Math.pow((v+.055)/1.055,2.4):v/12.92;}
    static double[] lab(int rgb){double r=lin((rgb>>16)&255),g=lin((rgb>>8)&255),b=lin(rgb&255);
        double x=f((.4124564*r+.3575761*g+.1804375*b)/.95047),y=f(.2126729*r+.7151522*g+.0721750*b),z=f((.0193339*r+.1191920*g+.9503041*b)/1.08883);
        return new double[]{116*y-16,500*(x-y),200*(y-z)};
    }
    static int classify(int rgb){
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
        // Check mint first: its lightness is close enough to white to fool palette distance alone.
        if((g-r>8&&g-b>3&&g-r<65&&g-b<50&&r>120)||
            (Math.abs(r-g)<12&&Math.abs(b-g)<12&&g<180))return EMPTY;
        double[] a=lab(rgb);int best=UNKNOWN;double dist=24*24;
        for(int i=0;i<PALETTE.length;i++){double d=0;for(int k=0;k<3;k++)d+=(a[k]-PALETTE[i][k])*(a[k]-PALETTE[i][k]);if(d<dist){dist=d;best=i;}}
        for(int i=0;i<VARIANTS.length;i++){double d=0;for(int k=0;k<3;k++)d+=(a[k]-VARIANTS[i][k])*(a[k]-VARIANTS[i][k]);if(d<dist){dist=d;best=VARIANT_LABELS[i];}}
        return best;
    }
    static boolean nearColor(int a,int b,double threshold){double[]x=lab(a),y=lab(b);double d=0;for(int i=0;i<3;i++)d+=(x[i]-y[i])*(x[i]-y[i]);return d<=threshold*threshold;}
    private int rawSample(Capture.Frame f,float x,float y){
        int px=Math.round(x*f.width),py=Math.round(y*f.height);
        int[]r=red,g=green,b=blue;int i=0;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int c=f.pixel(px+dx,py+dy);r[i]=(c>>16)&255;g[i]=(c>>8)&255;b[i]=c&255;i++;}
        Arrays.sort(r);Arrays.sort(g);Arrays.sort(b);return 0xff000000|(r[4]<<16)|(g[4]<<8)|b[4];
    }
    private boolean background(int rgb){
        if(!config.backgroundKnown)return false;
        if(nearColor(rgb,config.backgroundRgb,10))return true;
        double[]a=hsv(rgb),b=hsv(config.backgroundRgb);double hue=Math.abs(a[0]-b[0]);hue=Math.min(hue,360-hue);
        // Dark, desaturated versions of the sampled floor are exposed rail, not blue/purple beads.
        return b[1]>.08&&hue<25&&a[1]<Math.min(.55,b[1]+.25)&&a[2]<b[2]*.85;
    }
    private static double[]hsv(int rgb){
        double r=((rgb>>16)&255)/255.0,g=((rgb>>8)&255)/255.0,b=(rgb&255)/255.0,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,h=0;
        if(d>0){if(max==r)h=60*((g-b)/d%6);else if(max==g)h=60*((b-r)/d+2);else h=60*((r-g)/d+4);}if(h<0)h+=360;
        return new double[]{h,max==0?0:d/max,max};
    }
    private int sample(Capture.Frame f,float x,float y){int rgb=rawSample(f,x,y);if(background(rgb))return EMPTY;return classify(rgb);}
    private int[] points(Capture.Frame f,List<float[]> pts,boolean slots){
        int[]v=new int[pts.size()];for(int i=0;i<v.length;i++){
            int rgb=rawSample(f,pts.get(i)[0],pts.get(i)[1]);
            if(slots&&i<4&&config.emptySlotKnown[i]&&nearColor(rgb,config.emptySlotRgb[i],12))v[i]=EMPTY;
            else if(background(rgb))v[i]=EMPTY;
            else v[i]=classify(rgb);
        }return v;
    }
    public Observation read(Capture.Frame f){
        Observation o=new Observation(points(f,config.slots,true),points(f,config.buttons,false),f.nanos);
        if(config.requireBorder)for(int i=0;i<o.board.length;i++)if(o.board[i]>=0&&!selectableBorder(f,config.buttons.get(i)))o.board[i]=EMPTY;
        ArrayList<PathGeometry>geometry=new ArrayList<>();
        for(Config.FeedPath feed:config.feeders)o.fixedLoops.set(feed.lane);
        for(int lane=0;lane<config.lanes.size();lane++){
            PathGeometry path=new PathGeometry(config.lanes.get(lane),true,f.width,f.height);geometry.add(path);
            int count=(int)Math.round(path.length/config.pitch);
            if(count<4||count>4000){o.unknown++;o.total++;continue;}
            int[]beads=new int[count];int at=0;
            for(int j=0;j<count;j++){
                float[]p=path.point((j+.5)*path.length/count);int color=sample(f,p[0],p[1]);
                o.total++;if(color==UNKNOWN)o.unknown++;
                if(color!=EMPTY||o.fixedLoops.get(lane))beads[at++]=color;
            }
            o.lanes.add(Arrays.copyOf(beads,at));
        }
        if(o.lanes.size()!=config.lanes.size()){o.topologyValid=false;return o;}
        for(Config.FeedPath feed:config.feeders){
            PathGeometry path=new PathGeometry(feed.points,false,f.width,f.height),loop=geometry.get(feed.lane);
            float[]junction=feed.points.get(0);double[]near=loop.nearest(junction[0],junction[1]);
            int n=o.lanes.get(feed.lane).length;
            if(n==0||near[1]>config.pitch*1.75){o.topologyValid=false;continue;}
            int entry=((int)Math.round(near[0]/loop.length*n-.5)+n)%n;
            int count=(int)Math.floor(path.length/config.pitch);if(count>4000){o.topologyValid=false;continue;}
            int[]beads=new int[count];int at=0;
            // The junction belongs to the loop; start one pitch upstream to avoid double counting it.
            for(int i=0;i<count;i++){
                float[]p=path.point((i+1)*config.pitch);int color=sample(f,p[0],p[1]);o.total++;if(color==UNKNOWN)o.unknown++;
                if(color!=EMPTY)beads[at++]=color;
            }
            o.feeds.add(new FeedObservation(feed.lane,entry,Arrays.copyOf(beads,at)));
        }
        return o;
    }
    static boolean selectableBorder(Capture.Frame f,float[]center){
        int x=Math.round(center[0]*f.width),y=Math.round(center[1]*f.height);
        for(int radius=28;radius<=46;radius+=2){
            int white=0,ry=Math.round(radius*f.height/1280f);
            for(int offset=-radius+6;offset<=radius-6;offset+=2)for(int side:new int[]{-1,1}){
                if(white(f.pixel(x+offset,y+side*ry)))white++;
                if(white(f.pixel(x+side*radius,y+Math.round(offset*f.height/1280f))))white++;
            }
            if(white>=12)return true;
        }return false;
    }
    private static boolean white(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r>215&&g>215&&b>215&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<25;}
}

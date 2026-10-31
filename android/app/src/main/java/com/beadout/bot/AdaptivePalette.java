package com.beadout.bot;

import java.util.*;

/** Per-level observed colors. IDs only append; old observations never get renumbered. */
public final class AdaptivePalette {
    public static final int LIMIT=64;
    private final ArrayList<Integer> colors=new ArrayList<>();
    private final ArrayList<double[]>labs=new ArrayList<>(),tones=new ArrayList<>();
    public int size(){return colors.size();}
    public void clear(){colors.clear();labs.clear();tones.clear();}
    public int rgb(int id){return colors.get(id);}
    public void restore(int rgb){if(colors.size()>=LIMIT)throw new IllegalStateException("Too many observed color groups");colors.add(rgb|0xff000000);labs.add(Vision.lab(rgb));tones.add(hsv(rgb));}
    public String describe(int id){return id<0?"unknown":String.format(java.util.Locale.US,"color %d (#%06X)",id+1,rgb(id)&0xffffff);}
    static double[]hsv(int c){double r=(c>>16)&255,g=(c>>8)&255,b=c&255,v=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=v-min,h=0;
        if(d>0){if(v==r)h=60*((g-b)/d%6);else if(v==g)h=60*((b-r)/d+2);else h=60*((r-g)/d+4);}if(h<0)h+=360;return new double[]{h,v==0?0:d/v,v/255};}
    static double hue(double a,double b){double d=Math.abs(a-b);return Math.min(d,360-d);}
    static double distance(int a,int b){return distance(hsv(a),Vision.lab(a),hsv(b),Vision.lab(b));}
    private static double distance(double[]x,double[]la,double[]y,double[]lb){double chroma=Math.min(1,Math.min(x[1],y[1])/.2);
        double h=hue(x[0],y[0])/18;double lab=0;
        for(int i=0;i<3;i++){double delta=la[i]-lb[i];lab+=delta*delta;}
        if(Math.min(x[1],y[1])<.25)return Math.max(h*h*chroma,lab/(28*28));
        double value=(x[2]-y[2])/.38;
        return h*h+value*value+.1*lab/(28*28);}
    public int match(int rgb){double best=1;int label=Vision.UNKNOWN;double[]tone=hsv(rgb),lab=Vision.lab(rgb);for(int i=0;i<colors.size();i++){double d=distance(tone,lab,tones.get(i),labs.get(i));if(d<best){best=d;label=i;}}return label;}
    /** Learn dense groups, not single highlighted/antialiased outliers. */
    public void observe(List<Integer>samples){
        ArrayList<Integer>pending=new ArrayList<>();ArrayList<Integer>votes=new ArrayList<>();ArrayList<long[]>sums=new ArrayList<>();
        for(int rgb:samples){if(match(rgb)>=0)continue;int k=-1;for(int i=0;i<pending.size();i++)if(distance(rgb,pending.get(i))<.5){k=i;break;}
            if(k<0){pending.add(rgb);votes.add(1);sums.add(new long[]{(rgb>>16)&255,(rgb>>8)&255,rgb&255});}else {votes.set(k,votes.get(k)+1);long[]sum=sums.get(k);sum[0]+=(rgb>>16)&255;sum[1]+=(rgb>>8)&255;sum[2]+=rgb&255;}
        }
        ArrayList<Integer>order=new ArrayList<>();for(int i=0;i<pending.size();i++)order.add(i);order.sort((a,b)->Integer.compare(votes.get(b),votes.get(a)));
        for(int i:order)if(votes.get(i)>=3){long[]sum=sums.get(i);int n=votes.get(i),representative=0xff000000|(int)(sum[0]/n)<<16|(int)(sum[1]/n)<<8|(int)(sum[2]/n);if(match(representative)<0)restore(representative);}
    }
    /** Theme-relative exposed floor. Saturated colors are never rejected by floor hue alone. */
    static boolean floor(int pixel,int background){
        if(Vision.nearColor(pixel,background,8))return true;double[]a=hsv(pixel),b=hsv(background);
        return b[1]>.08&&a[1]>b[1]*.4&&a[1]<Math.min(.6,b[1]+.3)&&hue(a[0],b[0])<22&&a[2]<b[2]*.98;
    }
    static boolean darkUnknown(int pixel){double[]h=hsv(pixel);return h[1]<.10&&h[2]<.48;}
}

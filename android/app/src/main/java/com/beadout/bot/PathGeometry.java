package com.beadout.bot;

import java.util.*;

/** Pixel-space polyline geometry shared by calibration and perception. */
public final class PathGeometry {
    public final List<float[]>points;
    public final double[]distances;
    public final double length;
    private final int width,height,segments;
    public PathGeometry(List<float[]>points,boolean closed,int width,int height){
        if(points.size()<2)throw new IllegalArgumentException("Path needs at least two points");
        this.points=points;this.width=width;this.height=height;segments=closed?points.size():points.size()-1;
        distances=new double[segments+1];
        for(int i=0;i<segments;i++){float[]a=points.get(i),b=points.get((i+1)%points.size());distances[i+1]=distances[i]+Math.hypot((a[0]-b[0])*width,(a[1]-b[1])*height);}
        length=distances[segments];
        if(length<1)throw new IllegalArgumentException("Path has no length");
    }
    public float[] point(double distance){
        distance=Math.max(0,Math.min(length,distance));int segment=0;while(segment<segments-1&&distances[segment+1]<distance)segment++;
        double t=(distance-distances[segment])/Math.max(.000001,distances[segment+1]-distances[segment]);float[]a=points.get(segment),b=points.get((segment+1)%points.size());
        return new float[]{(float)(a[0]+(b[0]-a[0])*t),(float)(a[1]+(b[1]-a[1])*t)};
    }
    /** Returns distance along path, then perpendicular distance, in pixels. */
    public double[] nearest(float x,float y){
        double best=Double.POSITIVE_INFINITY,along=0;
        for(int i=0;i<segments;i++){
            float[]a=points.get(i),b=points.get((i+1)%points.size());double dx=(b[0]-a[0])*width,dy=(b[1]-a[1])*height;
            double qx=(x-a[0])*width,qy=(y-a[1])*height;double norm=dx*dx+dy*dy;
            double t=norm<.000001?0:Math.max(0,Math.min(1,(qx*dx+qy*dy)/norm));double d=Math.hypot(qx-t*dx,qy-t*dy);
            if(d<best){best=d;along=distances[i]+t*Math.sqrt(norm);}
        }return new double[]{along,best};
    }
}

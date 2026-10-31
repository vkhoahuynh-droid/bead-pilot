package com.beadout.bot;

import java.util.*;

/** Image-derived geometry. No level number or screen coordinates are supplied by the user. */
public final class AutoLayout {
    public static final class Detection {
        public final Config config;
        public final String summary,signature;
        Detection(Config c,String summary,String signature){config=c;this.summary=summary;this.signature=signature;}
    }
    /** Consecutive observations are required after any rejected or changed geometry. */
    public static final class Stability {
        private Config previous;
        private int frames;
        public void clear(){previous=null;frames=0;}
        public boolean observe(Config next,int width,int height){
            frames=previous!=null&&sameGeometry(previous,next,width,height)?frames+1:1;
            previous=next;return frames>=2;
        }
    }
    private static final class Region {
        int id,x,y,w,h,area,tag;long sx,sy;
        double cx(){return (double)sx/area;}double cy(){return (double)sy/area;}
    }
    private static final class Components {
        int[]labels;ArrayList<Region>regions=new ArrayList<>();
    }
    private static final class Box {
        Region r;boolean empty;double x,y;
        Box(Region r,boolean empty){this.r=r;this.empty=empty;x=r.x+r.w*.5;y=r.y+(empty?r.h*.5:Math.min(r.h*.68,r.w*.75));}
    }
    private static final class Grid {
        final Capture.Frame frame;final int w,h,n;final int[]rgb,lum,sat,value;final int bg,bgLum;final byte[]bead;
        Grid(Capture.Frame f){frame=f;w=(f.width+1)/2;h=(f.height+1)/2;n=w*h;rgb=new int[n];lum=new int[n];sat=new int[n];value=new int[n];int[]hist=new int[32768];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int c=f.pixel(x*2,y*2),r=(c>>16)&255,g=(c>>8)&255,b=c&255,i=y*w+x;
                rgb[i]=c;lum[i]=(3*r+6*g+b)/10;int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));value[i]=max;sat[i]=max==0?0:(max-min)*255/max;
                if(x>w*.1&&x<w*.9&&y>h*.1&&y<h*.9)hist[(r/8)*1024+(g/8)*32+b/8]++;
            }
            int bin=0;for(int i=1;i<hist.length;i++)if(hist[i]>hist[bin])bin=i;
            int r=(bin/1024)*8+4,g=(bin/32%32)*8+4,b=(bin%32)*8+4;bg=0xff000000|r<<16|g<<8|b;bgLum=(3*r+6*g+b)/10;bead=new byte[n];double[]theme=AdaptivePalette.hsv(bg);
            for(int i=0;i<n;i++){
                boolean strong=(sat[i]>130&&value[i]>90)||(lum[i]>bgLum+15&&sat[i]<60);
                int pixel=rgb[i],dr=((pixel>>16)&255)-r,dg=((pixel>>8)&255)-g,db=(pixel&255)-b;
                if(!strong&&value[i]>85&&sat[i]<130&&dr*dr+dg*dg+db*db>26*26&&!(sat[i]<26&&value[i]<122)){
                    double[]tone=AdaptivePalette.hsv(rgb[i]);boolean floor=theme[1]>.08&&tone[1]>theme[1]*.4&&tone[1]<Math.min(.6,theme[1]+.3)&&AdaptivePalette.hue(tone[0],theme[0])<22&&tone[2]<theme[2]*.98;strong=!floor;
                }
                if(strong)bead[i]=1;
            }
        }
        boolean color(int i){return bead[i]!=0;}
        int at(double x,double y){return Math.max(0,Math.min(h-1,(int)Math.round(y)))*w+Math.max(0,Math.min(w-1,(int)Math.round(x)));}
    }
    public Detection detect(Capture.Frame frame){
        return detect(frame,null);
    }
    public Detection detect(Capture.Frame frame,Config previous){
        Grid g=new Grid(frame);ArrayList<Box>candidates=slotCandidates(g);
        List<Box>anchored=anchoredSlots(g,candidates,previous);if(anchored!=null)return build(g,anchored,previous);
        List<List<Box>>quads=quads(candidates,g);
        if(quads.isEmpty())throw new IllegalStateException("Looking for the four holding slots");
        String failure="Looking for a closed track";
        for(List<Box>quad:quads){
            try{return build(g,quad,previous);}catch(IllegalStateException e){failure=e.getMessage();}
        }
        throw new IllegalStateException(failure);
    }
    private Detection build(Grid g,List<Box>quad,Config previous){
        double slotY=0;for(Box b:quad)slotY+=b.y/4;
        int bottom=(int)(slotY-g.h*.055);if(bottom<g.h*.2)throw new IllegalStateException("Waiting for a clear game board");
        byte[]mask=new byte[g.n];
        for(int y=(int)(g.h*.055);y<bottom;y++)for(int x=0;x<g.w;x++){int i=y*g.w+x;if(g.lum[i]<g.bgLum-35||g.color(i))mask[i]=1;}
        mask=close(mask,g.w,g.h);
        Loop geometry=findLoop(g,mask,bottom,previous);
        ArrayList<float[]>loop=geometry.points;
        double width=geometry.width;
        Config c=new Config();c.clearLevelGeometry();c.automatic=true;c.rowUnit=5;c.requireBorder=true;c.backgroundKnown=true;c.backgroundRgb=g.bg;c.confirmed=true;
        double slotWidth=0;
        for(int i=0;i<4;i++){
            Box b=quad.get(i);c.slots.add(new float[]{(float)(b.x/g.w),(float)(b.y/g.h)});slotWidth+=b.r.w/4.0;
            int color=b.empty?g.rgb[g.at(b.x,b.y)]:mixWhite(g.bg,.45);c.emptySlotRgb[i]=color;c.emptySlotKnown[i]=true;
        }
        double gap=slotY-loop.get(0)[1];
        if(gap<slotWidth*.45||gap>slotWidth*3)throw new IllegalStateException("Four matching squares are too far from the loop exit to be holding slots");
        c.slotLift=slotWidth*.4/g.h;
        ArrayList<float[]>normalized=normalize(loop,g.w,g.h);c.lanes.add(normalized);c.trackOutline.addAll(normalize(geometry.outline,g.w,g.h));
        byte[]beads=new byte[g.n];for(int y=(int)(g.h*.055);y<bottom;y++)for(int x=0;x<g.w;x++){int i=y*g.w+x;if(g.color(i))beads[i]=1;}
        beads=close(beads,g.w,g.h);
        ArrayList<Double>pitches=new ArrayList<>();
        // Remove the observed loop band and its interior, rather than cutting the
        // image at the loop's left/right extrema. Feeders may bend on either side.
        PathGeometry loopPath=new PathGeometry(normalized,true,g.w,g.h);
        for(int i=0;i<g.n;i++)if(beads[i]!=0){
            double[]near=loopPath.nearest((i%g.w)/(float)g.w,(i/g.w)/(float)g.h);
            if(geometry.inside[i]!=0||near[1]<width*.75)beads[i]=0;
        }
        Components bodies=components(beads,g.w,g.h,true);
        ArrayList<Config.FeedPath>feeds=new ArrayList<>();
        for(Region body:bodies.regions){
            if(body.area<g.w*g.w*.008)continue;
            byte[]shape=new byte[g.n];for(int i=0;i<g.n;i++)if(bodies.labels[i]==body.id)shape[i]=1;
            shape=morph(morph(morph(morph(shape,g.w,g.h,true),g.w,g.h,true),g.w,g.h,false),g.w,g.h,false);
            shape=fillHoles(shape,g.w,g.h);
            int smoothing=Math.max(2,(int)Math.round(slotWidth*.1));
            for(int i=0;i<smoothing;i++)shape=morph(shape,g.w,g.h,false);
            for(int i=0;i<smoothing;i++)shape=morph(shape,g.w,g.h,true);
            byte[]sk=thin(shape,g.w,g.h);int toe=-1;double toeD=Double.POSITIVE_INFINITY,join=0;
            for(int i=0;i<g.n;i++)if(sk[i]!=0){
                double[]near=loopPath.nearest((i%g.w)/(float)g.w,(i/g.w)/(float)g.h);
                if(near[1]<toeD){toeD=near[1];toe=i;join=near[0];}
            }
            // A queue must actually approach this loop; unrelated UI is ignored.
            if(toe<0||toeD>width*2)continue;
            ArrayList<float[]>route=feederRoute(shape,sk,g.w,g.h,toe,slotWidth);
            if(route.size()<slotWidth)continue;
            double reach=0;for(float[]p:route)reach=Math.max(reach,loopPath.nearest(p[0]/g.w,p[1]/g.h)[1]);
             // A leftover strip of the loop is not a feeder.
            route=smoothOpen(route,2);
            ArrayList<float[]>feed=new ArrayList<>();float[]junction=loopPath.point(join);
            feed.add(new float[]{junction[0]*g.w,junction[1]*g.h});feed.addAll(route);
            Config.FeedPath fp=new Config.FeedPath(0,normalize(feed,g.w,g.h));fp.skipConnector=true;
            boolean tracked=false;if(previous!=null&&sameLoop(c,previous,g.w,g.h))for(Config.FeedPath old:previous.feeders)tracked|=feedOverlap(fp,old,g.w,g.h);
            double routeLength=0;for(int i=1;i<route.size();i++)routeLength+=Math.hypot(route.get(i)[0]-route.get(i-1)[0],route.get(i)[1]-route.get(i-1)[1]);
            if(reach<width*1.5||reach<width*2.5&&routeLength<Math.sqrt(body.area)&&!tracked)continue;
            feeds.add(fp);
            float[]head=route.get(route.size()-1);
            double pitch=measurePitch(g.frame,head[0]*2,head[1]*2,slotWidth*2);
            if(pitch>0)pitches.add(pitch);
        }
        // A drained queue may retain its previously observed path only while the
        // same loop/slots remain visible and its missing tail is visibly empty.
        if(previous!=null&&sameLoop(c,previous,g.w,g.h)){
            for(Config.FeedPath old:previous.feeders){
                int match=-1;for(int i=0;i<feeds.size();i++){
                    Config.FeedPath f=feeds.get(i);
                    if(feedOverlap(f,old,g.w,g.h))match=i;
                }
                if(emptyTail(g,old,match<0?null:feeds.get(match),width)){
                    if(match<0)feeds.add(old);else feeds.set(match,old);
                }
            }
        }
        if(feeds.size()>2&&previous!=null&&previous.feeders.size()==2&&sameLoop(c,previous,g.w,g.h)){
            outer:for(int i=0;i<feeds.size();i++)for(int j=0;j<feeds.size();j++)if(i!=j&&feedOverlap(feeds.get(i),previous.feeders.get(0),g.w,g.h)&&feedOverlap(feeds.get(j),previous.feeders.get(1),g.w,g.h)){
                Config.FeedPath first=feeds.get(i),second=feeds.get(j);feeds.clear();feeds.add(first);feeds.add(second);break outer;
            }
        }
        if(feeds.size()!=2)throw new IllegalStateException("Found "+feeds.size()+" feeder paths; waiting for two observed feeder paths");
        feeds.sort(Comparator.comparingDouble(f->f.points.get(0)[0]));c.feeders.addAll(feeds);
        c.pitch=pitches.isEmpty()?slotWidth*2*.12:median(pitches);c.pitch=Math.max(4,Math.min(18,c.pitch));
        c.loopRows=Math.max(20,Math.min(400,(int)Math.round(new PathGeometry(c.lanes.get(0),true,g.frame.width,g.frame.height).length/c.pitch)));
        refreshSquares(g,c,slotY);
        if(c.buttons.isEmpty())throw new IllegalStateException("No selectable squares are visible");
        String sig=geometrySignature(c,g.frame.width,g.frame.height);
        return new Detection(c,"Found 4 slots, "+c.buttons.size()+" squares, a loop and 2 feeder queues",sig);
    }
    public void refreshSquares(Capture.Frame f,Config c){Grid g=new Grid(f);double y=0;for(float[]p:c.slots)y+=p[1]*g.h/4;refreshSquares(g,c,y);}
    private static void refreshSquares(Grid g,Config c,double slotY){
        byte[]m=new byte[g.n];for(int y=(int)(slotY+g.w*.055);y<g.h*.9;y++)for(int x=0;x<g.w;x++){int i=y*g.w+x;if(g.color(i)||(g.sat[i]>110&&g.value[i]>90)||(g.lum[i]>g.bgLum+18&&g.sat[i]<50)||(AdaptivePalette.darkUnknown(g.rgb[i])&&g.lum[i]<g.bgLum-45))m[i]=1;}
        byte[]classes=colorClasses(g,m);ArrayList<Region>boxes=new ArrayList<>();
        // Preserve complete cup rims/shading. Opening is only a way to separate
        // a cup connected to a long same-color wall, not a replacement for its
        // original raised footprint.
        for(Region r:components(classes,g.w,g.h,true,true).regions)if(isBox(g,r,m))boxes.add(r);
        for(Region r:components(thickClasses(classes,g.w,g.h,Math.max(1,(int)Math.round(g.w*.012))),g.w,g.h,true,true).regions)if(isBox(g,r,m)){
            boolean overlaps=false;for(Region old:boxes)if(Math.abs(old.cx()-r.cx())<Math.max(old.w,r.w)*.5&&Math.abs(old.cy()-r.cy())<Math.max(old.h,r.h)*.5)overlaps=true;
            if(!overlaps)boxes.add(r);
        }
        mergeCupParts(boxes);
        boxes.sort(Comparator.comparingInt((Region r)->Math.round(r.y/(float)(g.w*.06))).thenComparingInt(r->r.x));
        c.buttons.clear();c.clickable=new boolean[boxes.size()];c.unknownBoxes=new boolean[boxes.size()];c.unlockValues=new int[boxes.size()];
        byte[]walls=wallMask(g,boxes,slotY);
        boolean enclosure=false;
        for(Region r:components(walls,g.w,g.h,true).regions)if(r.w>g.w*.3&&r.h>g.w*.08||r.h>g.w*.3&&r.w>g.w*.08)enclosure=true;
        if(!enclosure)throw new IllegalStateException("Waiting for a visible enclosure; exit paths are uncertain");
        for(int i=0;i<boxes.size();i++){Region r=boxes.get(i);c.buttons.add(new float[]{(float)((r.x+r.w*.5)/g.w),(float)((r.y+Math.min(r.h*.45,r.w*.45))/g.h)});
            c.unknownBoxes[i]=r.tag==16;
            boolean raised=r.h>=r.w*.98;
            c.clickable[i]=!c.unknownBoxes[i]&&raised&&escape(g,walls,boxes,i,-1,slotY);
        }
        for(int i=0;i<boxes.size();i++)if(c.clickable[i])for(int j=0;j<boxes.size();j++)if(i!=j&&!c.clickable[j]&&escape(g,walls,boxes,j,i,slotY))c.unlockValues[i]+=c.unknownBoxes[j]?2:1;
    }
    private static boolean isBox(Grid g,Region r,byte[]mask){
        if(!(r.w>=g.w*.055&&r.w<=g.w*.20&&r.h>=g.w*.045&&r.h<=g.w*.25&&r.area>(double)r.w*r.h*.3))return false;
        // Wall elbows are compact components too, but their middle is floor.
        // Require object material inside the proposed cup, not just an L rim.
        int x=r.x+r.w/2,y=r.y+Math.min(r.h/2,r.w/2),solid=0;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(mask[g.at(x+dx,y+dy)]!=0)solid++;
        return solid>=6;
    }
    private static void mergeCupParts(ArrayList<Region>boxes){
        for(int i=0;i<boxes.size();i++)for(int j=i+1;j<boxes.size();){Region a=boxes.get(i),b=boxes.get(j);
            int l=Math.max(a.x,b.x),t=Math.max(a.y,b.y),r=Math.min(a.x+a.w,b.x+b.w),bottom=Math.min(a.y+a.h,b.y+b.h);
            double overlap=Math.max(0,r-l)*Math.max(0,bottom-t);
            if(Math.min(a.w,b.w)>.5*Math.max(a.w,b.w)&&overlap>.65*Math.min(a.w*a.h,b.w*b.h)){
                int right=Math.max(a.x+a.w,b.x+b.w),low=Math.max(a.y+a.h,b.y+b.h);a.x=Math.min(a.x,b.x);a.y=Math.min(a.y,b.y);a.w=right-a.x;a.h=low-a.y;
                if(b.tag==16)a.tag=16;a.area+=b.area;a.sx+=b.sx;a.sy+=b.sy;boxes.remove(j);
            }else j++;
        }
    }
    private static ArrayList<Box>slotCandidates(Grid g){
        byte[]bright=new byte[g.n],colored=new byte[g.n];
        for(int y=(int)(g.h*.30);y<g.h*.86;y++)for(int x=0;x<g.w;x++){
            int i=y*g.w+x,r=(g.rgb[i]>>16)&255,gg=(g.rgb[i]>>8)&255,b=g.rgb[i]&255,br=(g.bg>>16)&255,bg=(g.bg>>8)&255,bb=g.bg&255;
            if(g.lum[i]>g.bgLum+8&&g.sat[i]<110&&(r-br)*(r-br)+(gg-bg)*(gg-bg)+(b-bb)*(b-bb)>144)bright[i]=1;
            if(g.color(i)||(g.sat[i]>110&&g.value[i]>90)||(g.lum[i]>g.bgLum+18&&g.sat[i]<50))colored[i]=1;
        }
        ArrayList<Box>out=new ArrayList<>();
        for(Region r:components(bright,g.w,g.h,true).regions)if(r.w>g.w*.045&&r.w<g.w*.19&&r.h>g.w*.035&&r.h<g.w*.17&&(double)r.w/r.h>1.05&&(double)r.w/r.h<1.8&&r.area>(double)r.w*r.h*.45)out.add(new Box(r,true));
        for(Region r:components(colorClasses(g,colored),g.w,g.h,true,true).regions)if(r.w>g.w*.055&&r.w<g.w*.19&&r.h>r.w*.5&&r.h<r.w*1.75&&r.area>(double)r.w*r.h*.35){Box b=new Box(r,false);boolean duplicate=false;for(Box old:out)if(Math.hypot(old.x-b.x,old.y-b.y)<r.w*.5){duplicate=true;break;}if(!duplicate)out.add(b);}
        return out;
    }
    private static List<Box>anchoredSlots(Grid g,ArrayList<Box>candidates,Config previous){
        if(previous==null||previous.slots.size()!=4||!Vision.nearColor(g.bg,previous.backgroundRgb,10))return null;
        ArrayList<Box>row=new ArrayList<>();double width=previous.slotLift*g.h/.4;if(width<g.w*.04||width>g.w*.2)return null;
        for(int i=0;i<4;i++){float[]point=previous.slots.get(i);double x=point[0]*g.w,y=point[1]*g.h;Box found=null;double best=g.w*.04;
            for(Box b:candidates){double d=Math.hypot(b.x-x,b.y-y);if(d<best){best=d;found=b;}}
            if(found==null){int support=0,total=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int at=g.at(x+dx*width*.22,y+dy*width*.22);total++;if(!Vision.nearColor(g.rgb[at],g.bg,6)&&(g.color(at)||Vision.nearColor(g.rgb[at],previous.emptySlotRgb[i],10)))support++;}
                if(support<total*.55)return null;
                Region r=new Region();r.x=(int)(x-width*.5);r.y=(int)(y-width*.35);r.w=(int)width;r.h=(int)(width*.7);r.area=r.w*r.h;r.sx=(long)(x*r.area);r.sy=(long)(y*r.area);found=new Box(r,false);
            }
            Box fixed=new Box(found.r,found.empty);fixed.x=x;fixed.y=y;row.add(fixed);
        }return row;
    }
    private static List<List<Box>>quads(ArrayList<Box>boxes,Grid g){
        ArrayList<List<Box>>out=new ArrayList<>();HashSet<String>seen=new HashSet<>();
        // Three visible cups/empty pockets can establish regular spacing; the
        // fourth still needs visible tray material at its inferred position.
        search:for(Box a:boxes)for(Box b:boxes)if(b.x>a.x&&Math.abs(a.y-b.y)<g.w*.04)for(int steps=1;steps<=3;steps++){
            double d=(b.x-a.x)/steps;if(d<g.w*.09||d>g.w*.2)continue;
            for(int column=0;column+steps<4;column++){
                double left=a.x-column*d,center=left+1.5*d;if(Math.abs(center-g.w*.5)>g.w*.09)continue;
                Box[]found=new Box[4];ArrayList<Double>ys=new ArrayList<>(),widths=new ArrayList<>();
                for(int i=0;i<4;i++){double best=Double.POSITIVE_INFINITY;for(Box p:boxes){double dx=Math.abs(p.x-left-i*d),dy=Math.abs(p.y-a.y);if(dx>d*.22||dy>g.w*.04)continue;double score=dx+dy*.5;if(score<best){best=score;found[i]=p;}}if(found[i]!=null){ys.add(found[i].y);widths.add((double)found[i].r.w);}}
                if(ys.size()<3)continue;double y=median(ys),width=median(widths);if(y>g.h*.82)continue;
                String key=Math.round(left/2)+":"+Math.round(y/2)+":"+Math.round(d/2);if(!seen.add(key))continue;
                ArrayList<Box>row=new ArrayList<>();boolean valid=true;
                for(int i=0;i<4;i++){Box p=found[i];double x=left+i*d;if(p==null){int support=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(g.color(g.at(x+dx*width*.2,y+dy*width*.2)))support++;if(support<6){valid=false;break;}
                        Region r=new Region();r.x=(int)(x-width*.5);r.y=(int)(y-width*.35);r.w=(int)width;r.h=(int)(width*.7);r.area=r.w*r.h;r.sx=(long)(x*r.area);r.sy=(long)(y*r.area);p=new Box(r,false);}
                    Box fixed=new Box(p.r,p.empty);fixed.x=x;fixed.y=y;row.add(fixed);
                }
                if(valid)out.add(row);if(out.size()>=64)break search;
            }
        }out.sort(Comparator.comparingDouble(q->q.get(0).y));return out;
    }
    private static final class Loop {
        byte[]inside;ArrayList<float[]>points,outline;double width;
    }
    private static Loop findLoop(Grid g,byte[]mask,int bottom,Config previous){
        byte[]inverse=new byte[g.n];for(int i=0;i<g.n;i++)inverse[i]=(byte)(1-mask[i]);
        Components holes=components(inverse,g.w,g.h,false);
        ArrayList<Region>candidates=new ArrayList<>();
        for(Region r:holes.regions)if(r.x>0&&r.y>g.h*.055&&r.x+r.w<g.w&&r.y+r.h<bottom&&r.area>g.n*.0015)candidates.add(r);
        candidates.sort((a,b)->Integer.compare(b.area,a.area));
        for(Region hole:candidates){
            byte[]inside=new byte[g.n];for(int i=0;i<g.n;i++)if(holes.labels[i]==hole.id)inside[i]=1;
            ArrayList<float[]>boundary=smooth(contour(inside,g.w,g.h),3);
            if(boundary.size()<40)continue;ensureClockwise(boundary);
            if(previous!=null&&!previous.trackOutline.isEmpty()&&nearPath(normalize(boundary,g.w,g.h),previous.trackOutline,true,g.w,g.h)&&nearPath(previous.trackOutline,normalize(boundary,g.w,g.h),true,g.w,g.h)){
                ArrayList<float[]>observed=new ArrayList<>();for(float[]p:previous.lanes.get(0))observed.add(new float[]{p[0]*g.w,p[1]*g.h});
                if(onBand(g,observed)){Loop retained=new Loop();retained.inside=inside;retained.outline=boundary;retained.points=observed;retained.width=previous.pitch*2.5;return retained;}
            }
            ArrayList<Double>widths=new ArrayList<>();int supported=0;
            for(int i=0;i<boundary.size();i++){
                float[]p=boundary.get(i),a=boundary.get((i+boundary.size()-6)%boundary.size()),b=boundary.get((i+6)%boundary.size());
                double dx=b[0]-a[0],dy=b[1]-a[1],norm=Math.hypot(dx,dy);if(norm<.1)continue;
                int first=-1,last=-1,gap=0;
                for(int t=1;t<g.w*.14;t++){
                    double x=p[0]+dy/norm*t,y=p[1]-dx/norm*t;
                    if(x<0||x>=g.w||y<0||y>=bottom)break;
                    boolean colored=g.color(g.at(x,y));
                    if(colored){if(first<0)first=t;last=t;gap=0;}else if(first>=0&&++gap>1)break;
                }
                if(first>=0&&last-first>g.w*.018&&last-first<g.w*.14){widths.add((double)(last-first));supported++;}
            }
            if(supported<boundary.size()*.55)continue;
            ArrayList<float[]>loop=offsetLoop(g,boundary);
            if(loop==null||!simple(loop)||!onBand(g,loop))loop=skeletonLoop(g,inside,bottom);
            if(loop==null)continue;
            loop=smooth(loop,2);ensureClockwise(loop);rotateToPickup(loop,hole.cx());
            if(!simple(loop)||!onBand(g,loop))continue;
            Loop result=new Loop();result.inside=inside;result.outline=boundary;result.points=loop;result.width=median(widths);return result;
        }
        throw new IllegalStateException("Waiting for a supported, closed loop outline");
    }
    // Real beads have shaded seams and gaps: their color skeleton need not be
    // closed even though the neutral rail is. Offset the observed closed inner
    // rail using measured local bead-band centers; reject unsupported crossings.
    private static ArrayList<float[]>offsetLoop(Grid g,ArrayList<float[]>boundary){
        double[]offset=new double[boundary.size()];Arrays.fill(offset,Double.NaN);
        ArrayList<Double>valid=new ArrayList<>(),widths=new ArrayList<>();
        for(int i=0;i<boundary.size();i++){
            float[]p=boundary.get(i),a=boundary.get((i+boundary.size()-6)%boundary.size()),b=boundary.get((i+6)%boundary.size());
            double dx=b[0]-a[0],dy=b[1]-a[1],norm=Math.hypot(dx,dy);if(norm<.1)continue;
            int first=-1,last=-1,gap=0;
            for(int t=1;t<g.w*.3;t++){
                double x=p[0]+dy/norm*t,y=p[1]-dx/norm*t;
                boolean color=x>=0&&x<g.w&&y>=0&&y<g.h&&g.color(g.at(x,y));
                if(color){if(first<0)first=t;last=t;gap=0;}else if(first>=0&&++gap>1)break;
            }
            if(first>=0&&last-first>g.w*.022&&last-first<g.w*.18){offset[i]=(first+last)/2.0;valid.add(offset[i]);widths.add((double)(last-first));}
        }
        if(valid.size()<boundary.size()*.55)return null;
        double center=median(valid),width=median(widths);ArrayList<float[]>loop=new ArrayList<>();
        for(int i=0;i<boundary.size();i++){
            float[]p=boundary.get(i),a=boundary.get((i+boundary.size()-6)%boundary.size()),b=boundary.get((i+6)%boundary.size());
            double dx=b[0]-a[0],dy=b[1]-a[1],norm=Math.max(.1,Math.hypot(dx,dy));
            double d=Double.isFinite(offset[i])&&Math.abs(offset[i]-center)<width*.4?offset[i]:center;
            loop.add(new float[]{(float)(p[0]+dy/norm*d),(float)(p[1]-dx/norm*d)});
        }
        return smooth(loop,3);
    }
    // An observed ring's skeleton is a cycle even when it is non-convex. Peel
    // open branches instead of offsetting normals through a concave corner.
    private static ArrayList<float[]>skeletonLoop(Grid g,byte[]inside,int bottom){
        int[]distance=new int[g.n],queue=new int[g.n];Arrays.fill(distance,-1);int head=0,tail=0;
        for(int i=0;i<g.n;i++)if(inside[i]!=0){distance[i]=0;queue[tail++]=i;}
        while(head<tail){int i=queue[head++],x=i%g.w,y=i/g.w;
            if(distance[i]>=g.w*.14)continue;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                int xx=x+dx,yy=y+dy;if(xx<0||xx>=g.w||yy<0||yy>=bottom)continue;int j=yy*g.w+xx;
                if(distance[j]<0){distance[j]=distance[i]+1;queue[tail++]=j;}
            }
        }
        byte[]band=new byte[g.n];for(int i=0;i<g.n;i++)if(distance[i]>0&&g.color(i))band[i]=1;
        band=close(band,g.w,g.h);byte[]sk=thin(band,g.w,g.h);
        // Capture adjacency before pruning; removing a corner must not invent a
        // new diagonal edge. Suppress diagonals bridged by an orthogonal pixel.
        int[][]edges=new int[g.n][];int[]degree=new int[g.n];head=0;tail=0;
        for(int i=0;i<g.n;i++)if(sk[i]!=0){
            int[]neighbors=new int[8];int count=0,x=i%g.w,y=i/g.w;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                int xx=x+dx,yy=y+dy;if(dx==0&&dy==0||xx<0||xx>=g.w||yy<0||yy>=g.h)continue;
                int j=yy*g.w+xx;if(sk[j]==0)continue;
                if(dx!=0&&dy!=0&&(sk[y*g.w+xx]!=0||sk[yy*g.w+x]!=0))continue;
                neighbors[count++]=j;
            }
            edges[i]=Arrays.copyOf(neighbors,count);degree[i]=count;if(count<2)queue[tail++]=i;
        }
        while(head<tail){int i=queue[head++];if(sk[i]==0)continue;sk[i]=0;
            for(int j:edges[i])if(sk[j]!=0&&--degree[j]==1)queue[tail++]=j;
        }
        byte[]seen=new byte[g.n];ArrayList<float[]>best=null;
        for(int start=0;start<g.n;start++)if(sk[start]!=0&&seen[start]==0){
            ArrayList<float[]>path=new ArrayList<>();int at=start,previous=-1;boolean closed=false;
            do{seen[at]=1;if(degree[at]!=2)break;path.add(new float[]{at%g.w,at/g.w});int next=-1;
                for(int j:edges[at])if(sk[j]!=0&&j!=previous){next=j;break;}
                previous=at;at=next;if(at==start){closed=true;break;}
            }while(at>=0&&seen[at]==0);
            if(closed&&path.size()>=40){
                int seed=-1;for(int i=0;i<inside.length;i++)if(inside[i]!=0){seed=i;break;}
                if(seed>=0&&contains(path,seed%g.w,seed/g.w)&&(best==null||path.size()>best.size()))best=path;
            }
        }
        if(best==null)return null;
        // Keep serialized geometry bounded and nearest-path searches affordable.
        ArrayList<float[]>sampled=new ArrayList<>();int count=Math.min(256,best.size());
        for(int i=0;i<count;i++)sampled.add(best.get(i*best.size()/count));return sampled;
    }
    private static boolean contains(List<float[]>p,double x,double y){
        boolean inside=false;for(int i=0,j=p.size()-1;i<p.size();j=i++){
            float[]a=p.get(i),b=p.get(j);if((a[1]>y)!=(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])inside=!inside;
        }return inside;
    }
    private static boolean onBand(Grid g,List<float[]>loop){
        int supported=0;for(float[]p:loop){int x=Math.round(p[0]),y=Math.round(p[1]);boolean found=false;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(x+dx>=0&&x+dx<g.w&&y+dy>=0&&y+dy<g.h&&(g.color((y+dy)*g.w+x+dx)||g.lum[(y+dy)*g.w+x+dx]<g.bgLum-25))found=true;
            if(found)supported++;
        }return supported>=loop.size()*.9;
    }
    private static double cross(float[]a,float[]b,float[]c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
    private static boolean simple(List<float[]>path){
        // Sample a bounded polyline so this remains cheap on a phone.
        int n=Math.min(160,path.size());ArrayList<float[]>p=new ArrayList<>();
        for(int i=0;i<n;i++)p.add(path.get(i*path.size()/n));
        for(int i=0;i<n;i++)for(int j=i+2;j<n;j++){
            if(i==0&&j==n-1)continue;float[]a=p.get(i),b=p.get((i+1)%n),c=p.get(j),d=p.get((j+1)%n);
            if(cross(a,b,c)*cross(a,b,d)<0&&cross(c,d,a)*cross(c,d,b)<0)return false;
        }return true;
    }
    private static byte[]thickClasses(byte[]m,int w,int h,int radius){
        byte[]core=new byte[m.length],out=new byte[m.length];
        for(int y=radius;y<h-radius;y++)for(int x=radius;x<w-radius;x++){
            int i=y*w+x;if(m[i]==0)continue;boolean full=true;
            outer:for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++)if(m[(y+dy)*w+x+dx]!=m[i]){full=false;break outer;}
            if(full)core[i]=m[i];
        }
        for(int y=radius;y<h-radius;y++)for(int x=radius;x<w-radius;x++)if(core[y*w+x]!=0){
            byte tag=core[y*w+x];for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++)if(m[(y+dy)*w+x+dx]==tag)out[(y+dy)*w+x+dx]=tag;
        }return out;
    }
    private static byte[]fillHoles(byte[]shape,int w,int h){
        byte[]outside=new byte[shape.length];int[]q=new int[shape.length];int head=0,tail=0;
        for(int i=0;i<shape.length;i++)if((i<w||i>=shape.length-w||i%w==0||i%w==w-1)&&shape[i]==0){outside[i]=1;q[tail++]=i;}
        while(head<tail){int at=q[head++],x=at%w,y=at/w;for(int d=0;d<4;d++){int xx=x+(d==0?-1:d==1?1:0),yy=y+(d==2?-1:d==3?1:0);if(xx<0||xx>=w||yy<0||yy>=h)continue;int k=yy*w+xx;if(shape[k]==0&&outside[k]==0){outside[k]=1;q[tail++]=k;}}}
        byte[]filled=shape.clone();for(int i=0;i<filled.length;i++)if(outside[i]==0)filled[i]=1;return filled;
    }
    private static ArrayList<float[]>feederRoute(byte[]body,byte[]skeleton,int w,int h,int toe,double width){
        // Find the remote end in the filled queue body, not a texture spur on
        // its skeleton. Bead seams can make a longer branch run down an edge.
        int[]distance=new int[body.length],q=new int[body.length];Arrays.fill(distance,-1);int head=0,tail=0,max=0;q[tail++]=toe;distance[toe]=0;
        while(head<tail){int at=q[head++],x=at%w,y=at/w;max=Math.max(max,distance[at]);
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h)continue;int k=yy*w+xx;if(body[k]!=0&&distance[k]<0){distance[k]=distance[at]+1;q[tail++]=k;}}
        }
        double x=0,y=0,count=0;for(int i=0;i<body.length;i++)if(distance[i]>=max-width*.4){x+=i%w;y+=i/w;count++;}
        if(count==0)return longestRoute(skeleton,w,h,toe);x/=count;y/=count;
        int end=-1;double nearest=Double.POSITIVE_INFINITY;
        for(int i=0;i<skeleton.length;i++)if(skeleton[i]!=0&&distance[i]>=0){double d=Math.hypot(i%w-x,i/w-y);if(d<nearest){nearest=d;end=i;}}
        return end<0?new ArrayList<>():route(skeleton,w,h,toe,end);
    }
    private static ArrayList<float[]>longestRoute(byte[]m,int w,int h,int start){
        int[]distance=new int[m.length],q=new int[m.length];Arrays.fill(distance,-1);int head=0,tail=0,end=start;q[tail++]=start;distance[start]=0;
        while(head<tail){int i=q[head++],x=i%w,y=i/w;end=i;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                int xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h)continue;int j=yy*w+xx;
                if(m[j]!=0&&distance[j]<0){distance[j]=distance[i]+1;q[tail++]=j;}
            }
        }return route(m,w,h,start,end);
    }
    static String geometrySignature(Config c,int w,int h){
        PathGeometry path=new PathGeometry(c.lanes.get(0),true,w,h);StringBuilder s=new StringBuilder();
        for(int i=0;i<24;i++){float[]p=path.point(i*path.length/24);s.append(Math.round(p[0]*100)).append(',').append(Math.round(p[1]*100)).append(';');}return s.toString();
    }
    /** Shape comparison uses both directions, so equal bounding boxes cannot hide a new loop. */
    static boolean sameGeometry(Config a,Config b,int w,int h){
        return a.feeders.size()==2&&b.feeders.size()==2&&sameLoop(a,b,w,h)&&nearFeeds(a,b,w,h)&&nearFeeds(b,a,w,h);
    }
    private static boolean sameLoop(Config a,Config b,int w,int h){
        if(a.lanes.size()!=1||b.lanes.size()!=1)return false;
        for(int i=0;i<4;i++)if(Math.hypot((a.slots.get(i)[0]-b.slots.get(i)[0])*w,(a.slots.get(i)[1]-b.slots.get(i)[1])*h)>w*.015)return false;
        List<float[]>pa=a.trackOutline.isEmpty()?a.lanes.get(0):a.trackOutline,pb=b.trackOutline.isEmpty()?b.lanes.get(0):b.trackOutline;
        return nearPath(pa,pb,true,w,h)&&nearPath(pb,pa,true,w,h);
    }
    private static boolean nearFeeds(Config a,Config b,int w,int h){
        for(Config.FeedPath f:a.feeders){boolean matched=false;
            for(Config.FeedPath g:b.feeders)if(feedOverlap(f,g,w,h))matched=true;
            if(!matched)return false;
        }return true;
    }
    private static boolean feedOverlap(Config.FeedPath a,Config.FeedPath b,int w,int h){
        PathGeometry p=new PathGeometry(a.points,false,w,h),q=new PathGeometry(b.points,false,w,h);
        if(p.length>q.length){PathGeometry swap=p;p=q;q=swap;}
        double start=p.distances[1]+w*.04;if(start>=p.length)return false;int supported=0;
        for(int i=0;i<24;i++){float[]point=p.point(start+(p.length-start)*i/23);if(q.nearest(point[0],point[1])[1]<w*.045)supported++;}
        return supported>=22;
    }
    private static boolean emptyTail(Grid g,Config.FeedPath old,Config.FeedPath visible,double width){
        PathGeometry path=new PathGeometry(old.points,false,g.w,g.h);
        PathGeometry current=visible==null?null:new PathGeometry(visible.points,false,g.w,g.h);
        int empty=0,total=0;double start=path.distances[1]+width;
        for(double along=start;along<path.length;along+=2){
            float[]p=path.point(along);if(current!=null&&current.nearest(p[0],p[1])[1]<g.w*.035)continue;
            int c=g.rgb[g.at(p[0]*g.w,p[1]*g.h)];int dr=((c>>16)&255)-((g.bg>>16)&255),dg=((c>>8)&255)-((g.bg>>8)&255),db=(c&255)-(g.bg&255);
            int max=Math.max((c>>16)&255,Math.max((c>>8)&255,c&255)),bm=Math.max((g.bg>>16)&255,Math.max((g.bg>>8)&255,g.bg&255));
            int chroma=Math.abs(((c>>16)&255)*255/Math.max(1,max)-((g.bg>>16)&255)*255/bm)+Math.abs(((c>>8)&255)*255/Math.max(1,max)-((g.bg>>8)&255)*255/bm)+Math.abs((c&255)*255/Math.max(1,max)-(g.bg&255)*255/bm);
            boolean rail=g.sat[g.at(p[0]*g.w,p[1]*g.h)]<110&&chroma<55&&max>bm*.4&&max<bm*.9;
            total++;if(dr*dr+dg*dg+db*db<26*26||rail)empty++;
        }return total>=3&&empty>=total*.95;
    }
    private static boolean nearPath(List<float[]>a,List<float[]>b,boolean closed,int w,int h){return nearPath(a,b,closed,w,h,w*.018);}
    private static boolean nearPath(List<float[]>a,List<float[]>b,boolean closed,int w,int h,double tolerance){
        PathGeometry p=new PathGeometry(a,closed,w,h),q=new PathGeometry(b,closed,w,h);
        for(int i=0;i<32;i++){float[]point=p.point(i*p.length/(closed?32:31));if(q.nearest(point[0],point[1])[1]>tolerance)return false;}return true;
    }
    private static int mixWhite(int c,double a){int r=(int)(((c>>16)&255)*(1-a)+255*a),g=(int)(((c>>8)&255)*(1-a)+255*a),b=(int)((c&255)*(1-a)+255*a);return 0xff000000|r<<16|g<<8|b;}
    private static double measurePitch(Capture.Frame f,double x,double y,double boxWidth){
        int count=(int)Math.min(130,f.height-y-2);if(count<35)return -1;double bestScore=-1,bestPeriod=-1;
        for(int offset:new int[]{-4,0,4}){
            double[]v=new double[count],r=new double[count];for(int i=0;i<count;i++){int p=f.pixel((int)x+offset,(int)y+i);v[i]=Math.max((p>>16)&255,Math.max((p>>8)&255,p&255));}
            for(int i=0;i<count;i++){double mean=0;int n=0;for(int j=Math.max(0,i-8);j<=Math.min(count-1,i+8);j++){mean+=v[j];n++;}r[i]=v[i]-mean/n;}
            int lo=Math.max(4,(int)(boxWidth*.07)),hi=Math.min(16,(int)(boxWidth*.19));double[]scores=new double[hi+2];Arrays.fill(scores,-1);
            for(int lag=lo;lag<=hi;lag++){double a=0,b=0,ab=0;for(int i=0;i<count-lag;i++){a+=r[i]*r[i];b+=r[i+lag]*r[i+lag];ab+=r[i]*r[i+lag];}scores[lag]=ab/Math.max(1e-6,Math.sqrt(a*b));}
            for(int lag=lo;lag<=hi;lag++)if(scores[lag]>bestScore){bestScore=scores[lag];double delta=0;if(lag>lo&&lag<hi){double denom=scores[lag-1]-2*scores[lag]+scores[lag+1];if(Math.abs(denom)>1e-6)delta=Math.max(-.5,Math.min(.5,.5*(scores[lag-1]-scores[lag+1])/denom));}bestPeriod=lag+delta;}
        }return bestScore>.25?bestPeriod:-1;
    }
    private static double median(List<Double>a){ArrayList<Double>b=new ArrayList<>(a);Collections.sort(b);return b.get(b.size()/2);}
    private static Region largest(Components c){Region best=null;for(Region r:c.regions)if(best==null||r.area>best.area)best=r;return best;}
    private static Components components(byte[]m,int w,int h,boolean eight){
        return components(m,w,h,eight,false);
    }
    private static Components components(byte[]m,int w,int h,boolean eight,boolean sameClass){
        Components c=new Components();c.labels=new int[m.length];int[]q=new int[m.length];int label=0;
        for(int start=0;start<m.length;start++)if(m[start]!=0&&c.labels[start]==0){Region r=new Region();r.id=++label;r.tag=m[start];r.x=w;r.y=h;int maxX=0,maxY=0,head=0,tail=0;q[tail++]=start;c.labels[start]=label;
            while(head<tail){int i=q[head++],x=i%w,y=i/w;r.area++;r.sx+=x;r.sy+=y;r.x=Math.min(r.x,x);r.y=Math.min(r.y,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dy==0||!eight&&dx!=0&&dy!=0)continue;int xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h)continue;int k=yy*w+xx;if(m[k]!=0&&c.labels[k]==0&&(!sameClass||m[k]==m[i])){c.labels[k]=label;q[tail++]=k;}}
            }r.w=maxX-r.x+1;r.h=maxY-r.y+1;c.regions.add(r);
        }return c;
    }
    private static byte[]colorClasses(Grid g,byte[]m){
        byte[]out=new byte[m.length];for(int i=0;i<m.length;i++)if(m[i]!=0){if(AdaptivePalette.darkUnknown(g.rgb[i])&&g.lum[i]<g.bgLum-45){out[i]=16;continue;}if(g.sat[i]<110){out[i]=(byte)(g.lum[i]>g.bgLum+15?15:17);continue;}
            int c=g.rgb[i];double r=(c>>16)&255,gg=(c>>8)&255,b=c&255,max=Math.max(r,Math.max(gg,b)),min=Math.min(r,Math.min(gg,b)),d=max-min,h=0;
            if(d>0){if(max==r)h=60*((gg-b)/d%6);else if(max==gg)h=60*((b-r)/d+2);else h=60*((r-gg)/d+4);}if(h<0)h+=360;
            out[i]=(byte)(h<16||h>344?1:h<44?2:h<72?3:h<160?4:h<206?5:h<256?6:h<316?7:8);
        }return out;
    }
    private static byte[]wallMask(Grid g,List<Region>boxes,double slotY){
        byte[]wall=new byte[g.n];
        // Every visible non-background structure is an obstacle regardless of hue.
        // Square interiors are removed below; their footprints are added by escape.
        for(int y=(int)slotY;y<g.h*.93;y++)for(int x=0;x<g.w;x++){
            int i=y*g.w+x,c=g.rgb[i];
            int dr=((c>>16)&255)-((g.bg>>16)&255),dg=((c>>8)&255)-((g.bg>>8)&255),db=(c&255)-(g.bg&255);
            if(dr*dr+dg*dg+db*db>26*26)wall[i]=1;
        }
        for(Region r:boxes){int pad=Math.max(2,(int)Math.round(r.w*.07));for(int y=Math.max(0,r.y-pad);y<Math.min(g.h,r.y+r.h+pad);y++)for(int x=Math.max(0,r.x-pad);x<Math.min(g.w,r.x+r.w+pad);x++)wall[y*g.w+x]=0;}
        return wall;
    }
    private static boolean escape(Grid g,byte[]walls,List<Region>boxes,int candidate,int removed,double slotY){
        Region own=boxes.get(candidate);int minY=Math.max(1,(int)(slotY+g.w*.01)),maxY=Math.min(g.h-2,(int)(g.h*.92));
        int width=g.w,height=maxY-minY+1;if(height<=0)return false;byte[]occupied=new byte[width*height];
        for(int y=minY;y<=maxY;y++)System.arraycopy(walls,y*width,occupied,(y-minY)*width,width);
        for(int i=0;i<boxes.size();i++)if(i!=candidate&&i!=removed){Region r=boxes.get(i);int bh=Math.min(r.h,(int)Math.round(r.w*.8)),top=r.y+r.h-bh;
            for(int y=Math.max(top,minY);y<Math.min(r.y+r.h,maxY+1);y++)for(int x=Math.max(0,r.x);x<Math.min(width,r.x+r.w);x++)occupied[(y-minY)*width+x]=1;
        }
        int stride=width+1;int[]sum=new int[stride*(height+1)];for(int y=0;y<height;y++){int run=0;for(int x=0;x<width;x++){run+=occupied[y*width+x];sum[(y+1)*stride+x+1]=sum[y*stride+x+1]+run;}}
        int hw=Math.max(2,(int)(own.w*.43)),hh=Math.max(2,(int)(own.w*.34));int startX=(int)Math.round(own.x+own.w*.5),startY=(int)Math.round(own.y+own.h-own.w*.4)-minY;
        int target=(int)(slotY+g.w*.075)-minY;startY=Math.max(hh,Math.min(height-hh-1,startY));
        int[]queue=new int[occupied.length];byte[]seen=new byte[occupied.length];int head=0,tail=0,start=startY*width+startX;if(start<0||start>=seen.length)return false;if(startX<hw||startX>=width-hw||startY<hh||startY>=height-hh)return false;
        int sl=startX-hw,sr=startX+hw+1,st=startY-hh,sb=startY+hh+1;
        if(sum[sb*stride+sr]-sum[st*stride+sr]-sum[sb*stride+sl]+sum[st*stride+sl]!=0)return false;
        queue[tail++]=start;seen[start]=1;
        while(head<tail){int at=queue[head++],x=at%width,y=at/width;if(y<=target)return true;
            for(int dir=0;dir<4;dir++){int xx=x+(dir==0?-1:dir==1?1:0),yy=y+(dir==2?-1:dir==3?1:0);if(xx<hw||xx>=width-hw||yy<hh||yy>=height-hh)continue;int id=yy*width+xx;if(seen[id]!=0)continue;
                int l=xx-hw,r=xx+hw+1,t=yy-hh,b=yy+hh+1;int count=sum[b*stride+r]-sum[t*stride+r]-sum[b*stride+l]+sum[t*stride+l];if(count!=0)continue;seen[id]=1;queue[tail++]=id;
            }
        }return false;
    }
    private static byte[]morph(byte[]m,int w,int h,boolean dilate){byte[]out=new byte[m.length];for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){boolean v=!dilate;outer:for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if((m[(y+dy)*w+x+dx]!=0)==dilate){v=dilate;break outer;}if(v)out[y*w+x]=1;}return out;}
    private static byte[]close(byte[]m,int w,int h){return morph(morph(m,w,h,true),w,h,false);}
    private static byte[]open(byte[]m,int w,int h){return morph(morph(m,w,h,false),w,h,true);}
    private static ArrayList<float[]>contour(byte[]m,int w,int h){
        int vw=w+1;int[]next=new int[(w+1)*(h+1)];Arrays.fill(next,-1);int start=-1;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(m[y*w+x]!=0){int a=y*vw+x,b=a+1,c=a+vw+1,d=a+vw;
            if(y==0||m[(y-1)*w+x]==0){next[a]=b;if(start<0)start=a;}
            if(x==w-1||m[y*w+x+1]==0)next[b]=c;
            if(y==h-1||m[(y+1)*w+x]==0)next[c]=d;
            if(x==0||m[y*w+x-1]==0)next[d]=a;
        }
        ArrayList<float[]>out=new ArrayList<>();int at=start;for(int i=0;at>=0&&i<10000;i++){out.add(new float[]{at%vw,at/vw});at=next[at];if(at==start)break;}return out;
    }
    private static ArrayList<float[]>smooth(List<float[]>p,int radius){ArrayList<float[]>out=new ArrayList<>();for(int i=0;i<p.size();i++){double x=0,y=0;for(int j=-radius;j<=radius;j++){float[]a=p.get((i+j+p.size())%p.size());x+=a[0];y+=a[1];}out.add(new float[]{(float)(x/(2*radius+1)),(float)(y/(2*radius+1))});}return out;}
    private static ArrayList<float[]>smoothOpen(List<float[]>p,int radius){ArrayList<float[]>out=new ArrayList<>();for(int i=0;i<p.size();i++){double x=0,y=0;int n=0;for(int j=Math.max(0,i-radius);j<=Math.min(p.size()-1,i+radius);j++){x+=p.get(j)[0];y+=p.get(j)[1];n++;}out.add(new float[]{(float)(x/n),(float)(y/n)});}return out;}
    private static void ensureClockwise(ArrayList<float[]>p){double area=0;for(int i=0;i<p.size();i++){float[]a=p.get(i),b=p.get((i+1)%p.size());area+=a[0]*b[1]-a[1]*b[0];}if(area<0)Collections.reverse(p);}
    private static void rotateToPickup(ArrayList<float[]>p,double cx){int best=0;double score=-Double.MAX_VALUE;for(int i=0;i<p.size();i++){float[]a=p.get(i);double v=a[1]-.1*Math.abs(a[0]-cx);if(v>score){score=v;best=i;}}Collections.rotate(p,-best);}
    private static ArrayList<float[]>normalize(List<float[]>p,int w,int h){ArrayList<float[]>out=new ArrayList<>();for(float[]a:p)out.add(new float[]{Math.max(0,Math.min(1,a[0]/w)),Math.max(0,Math.min(1,a[1]/h))});return out;}
    private static byte[]thin(byte[]input,int w,int h){byte[]m=input.clone(),remove=new byte[m.length];for(int iteration=0;iteration<100;iteration++){boolean changed=false;for(int phase=0;phase<2;phase++){
        Arrays.fill(remove,(byte)0);for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int i=y*w+x;if(m[i]==0)continue;int[]p={m[i-w],m[i-w+1],m[i+1],m[i+w+1],m[i+w],m[i+w-1],m[i-1],m[i-w-1]};int n=0,t=0;for(int k=0;k<8;k++){n+=p[k];if(p[k]==0&&p[(k+1)%8]!=0)t++;}if(n<2||n>6||t!=1)continue;boolean ok=phase==0?p[0]*p[2]*p[4]==0&&p[2]*p[4]*p[6]==0:p[0]*p[2]*p[6]==0&&p[0]*p[4]*p[6]==0;if(ok)remove[i]=1;}
        for(int i=0;i<m.length;i++)if(remove[i]!=0){m[i]=0;changed=true;}}
        if(!changed)break;
    }return m;}
    private static ArrayList<float[]>route(byte[]m,int w,int h,int start,int end){int[]parent=new int[m.length],q=new int[m.length];Arrays.fill(parent,-2);parent[start]=-1;int head=0,tail=0;q[tail++]=start;
        while(head<tail&&parent[end]==-2){int i=q[head++],x=i%w,y=i/w;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dy==0)continue;int xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h)continue;int j=yy*w+xx;if(m[j]!=0&&parent[j]==-2){parent[j]=i;q[tail++]=j;}}}
        ArrayList<float[]>out=new ArrayList<>();if(parent[end]==-2)return out;for(int at=end;at>=0;at=parent[at])out.add(new float[]{at%w,at/w});Collections.reverse(out);return out;
    }
}

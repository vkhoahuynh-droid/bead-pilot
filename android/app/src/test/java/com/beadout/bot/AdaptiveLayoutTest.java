package com.beadout.bot;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Synthetic pixel fixtures: varied geometry is generated, never supplied to the detector. */
public final class AdaptiveLayoutTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static Shape ring(int shape){
        if(shape==0)return new Ellipse2D.Double(164,180,254,440);
        if(shape==1)return new RoundRectangle2D.Double(164,180,254,440,70,70);
        Path2D p=new Path2D.Double();
        if(shape==3){p.moveTo(180,610);p.lineTo(240,180);p.lineTo(410,610);p.closePath();return p;}
        p.moveTo(180,180);p.lineTo(402,180);p.lineTo(402,620);p.lineTo(180,620);
        p.lineTo(180,450);p.lineTo(245,450);p.lineTo(245,350);p.lineTo(180,350);p.closePath();return p;
    }
    static Capture.Frame scene(int shape,int wall,int count,boolean blocked,boolean feeders,boolean broken){
        return scene(shape,wall,count,blocked,feeders,broken,false,false);
    }
    static Capture.Frame scene(int shape,int wall,int count,boolean blocked,boolean feeders,boolean broken,boolean irregular,boolean topFeed){
        BufferedImage image=new BufferedImage(582,1280,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(0xffced4df));g.fillRect(0,0,582,1280);
        // Bead bands plus neutral rails. No detector coordinate/config injection.
        if(feeders){
            Path2D left=new Path2D.Double();left.moveTo(75,220);left.lineTo(75,480);left.lineTo(170,480);
            if(topFeed){left.reset();left.moveTo(240,85);left.lineTo(290,100);left.lineTo(290,180);}
            Path2D right=new Path2D.Double();right.moveTo(505,220);right.lineTo(505,480);right.lineTo(412,480);
            g.setStroke(new BasicStroke(40,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(0xff00b948));g.draw(left);g.draw(right);
        }
        g.setStroke(new BasicStroke(48,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(0xff555555));g.draw(ring(shape));
        g.setStroke(new BasicStroke(36,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(0xffff5520));g.draw(ring(shape));
        if(broken){g.setColor(new Color(0xffced4df));g.fillRect(265,140,55,90);}
        g.setColor(new Color(0xffeeeeee));for(int i=0;i<4;i++)g.fillRoundRect(145+i*80,725,52,38,10,10);
        g.setColor(new Color(wall));g.setStroke(new BasicStroke(8));
        g.drawLine(98,795,98,1140);g.drawLine(484,795,484,1140);g.drawLine(98,1140,484,1140);
        if(blocked)g.drawLine(0,810,581,810);
        int[]colors={0xff2879f5,0xff00b948,0xffed1b35,0xffc3005f,0xff505050,0xfff5e300,0xff850092,0xff10bcf5};
        for(int i=0;i<count;i++){
            int x=irregular?140+(i%3)*95:130+(i%4)*80,y=irregular?835+(i/3)*100:850+(i/4)*105;
            g.setColor(new Color(colors[i]));g.fillRoundRect(x,y,56,70,8,8);
        }
        g.dispose();return new Capture.Frame(582,1280,image.getRGB(0,0,582,1280,null,0,582));
    }
    static AutoLayout.Detection detect(int shape,int wall,int count,boolean blocked){return new AutoLayout().detect(scene(shape,wall,count,blocked,true,false));}
    static void rejected(Capture.Frame f,String message){boolean rejected=false;try{new AutoLayout().detect(f);}catch(IllegalStateException expected){rejected=true;}check(rejected,message);}
    public static void main(String[]args){
        for(int shape=0;shape<3;shape++)for(int wall:new int[]{0xff8296eb,0xffe52c31,0xff16a84b,0xfff5e300,0xffeeeeee,0xff505050}){
            Config c=detect(shape,wall,7,false).config;
            check(c.slots.size()==4,"four image-derived slots");check(c.buttons.size()==7,"border must not become a square: shape "+shape+" color "+Integer.toHexString(wall)+" got "+c.buttons.size());
            int unknown=0,open=0;for(int i=0;i<c.buttons.size();i++){if(c.unknownBoxes[i]){unknown++;check(!c.clickable[i],"concealed square never selected");}if(c.clickable[i])open++;}
            check(unknown==1,"unknown square remains an obstacle");check(open>0,"unobstructed raised squares remain reachable");
            check(c.feeders.size()==2&&c.feeders.get(0).points.size()>5&&c.feeders.get(1).points.size()>5,"both bent queue paths observed");
            Shape expectedBand=new BasicStroke(32,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(ring(shape));
            boolean follows=true;for(float[]p:c.lanes.get(0))follows&=expectedBand.contains(p[0]*582,p[1]*1280);
            check(follows,"detected centerline follows the rendered loop, including its concavity");
            Config barred=detect(shape,wall,7,true).config;
            for(boolean clickable:barred.clickable)check(!clickable,"a border of any color blocks escape");
            check(barred.buttons.size()==7,"barrier not counted as square");
        }
        for(int n:new int[]{1,3,5,8})check(detect(1,0xffe52c31,n,false).config.buttons.size()==n,"changing square count "+n);
        Config ellipse=detect(0,0xffe52c31,7,false).config,rectangle=detect(1,0xffe52c31,7,false).config;
        check(!AutoLayout.sameGeometry(ellipse,rectangle,582,1280),"same bounding box with different loop shape invalidates model");
        check(AutoLayout.sameGeometry(ellipse,detect(0,0xfff5e300,3,false).config,582,1280),"border hue and removed squares do not change track geometry");
        Config changed=new AutoLayout().detect(scene(1,0xff2879f5,7,false,true,false,true,false)).config;
        check(changed.buttons.size()==7,"irregular three-column arrangement discovered");
        check(changed.buttons.get(3)[1]>rectangle.buttons.get(3)[1]+.04,"changed square positions come from pixels");
        Config upper=new AutoLayout().detect(scene(1,0xff2879f5,7,false,true,false,false,true)).config;
        boolean top=false;for(Config.FeedPath feed:upper.feeders)for(float[]p:feed.points)if(p[0]>.35&&p[0]<.65&&p[1]<.1)top=true;
        check(top,"a feeder above the loop is followed rather than replaced by a side queue");
        check(!AutoLayout.sameGeometry(rectangle,upper,582,1280),"a changed feeder route invalidates track geometry");
        Capture.Frame populated=scene(1,0xff8296eb,7,false,true,false);int[]emptyRing=populated.pixels.clone();
        for(int y=0;y<populated.height;y++)for(int x=0;x<populated.width;x++)if((emptyRing[y*582+x]&0xffffff)==0xff5520)emptyRing[y*582+x]=0xff555555;
        Config late=new AutoLayout().detect(new Capture.Frame(582,1280,emptyRing),rectangle).config;
        check(AutoLayout.sameGeometry(rectangle,late,582,1280),"an emptied loop retains geometry only when its closed rail is still observed");
        for(int shape:new int[]{0,1,3}){
            Capture.Frame colorful=scene(shape,0xffeeeeee,7,false,true,false);int[]neutral=colorful.pixels.clone();
            for(int i=0;i<neutral.length;i++){if((neutral[i]&0xffffff)==0x00b948)neutral[i]=0xff788b9c;if((neutral[i]&0xffffff)==0xced4df)neutral[i]=0xffe5d8bf;}
            Capture.Frame beige=new Capture.Frame(582,1280,neutral);Config adapted=new AutoLayout().detect(beige).config;
            check(adapted.feeders.size()==2,"gray bead feeders stay connected on a beige theme, shape "+shape);
            Vision.Observation seen=new AutoPerception(adapted).read(beige);int gray=adapted.palette.match(0xff788b9c);check(gray>=0,"gray is learned as an actual bead/collector color");
            boolean bead=false;for(Vision.FeedObservation feed:seen.feeds)for(int color:feed.beads)bead|=color==gray;check(bead,"gray feeder inventory is not discarded as empty rail");
        }
        Capture.Frame creamSource=scene(1,0xffeeeeee,7,false,true,false);int[]cream=creamSource.pixels.clone();
        for(int i=0;i<cream.length;i++){if((cream[i]&0xffffff)==0xced4df)cream[i]=0xffe5d8bf;if((cream[i]&0xffffff)==0xf5e300)cream[i]=0xffdfdedc;}
        Config whiteCup=new AutoLayout().detect(new Capture.Frame(582,1280,cream)).config;
        check(whiteCup.buttons.size()==7,"a shaded white cup is still detected on a beige background");
        Capture.Frame wallSource=scene(1,0xffeeeeee,7,false,true,false);int[]elbow=wallSource.pixels.clone();
        for(int y=1000;y<1048;y++)for(int x=448;x<490;x++)if(y<1016||x<464)elbow[y*582+x]=0xffeeeeee;
        for(int y=1000;y<1004;y++)for(int x=484;x<490;x++)elbow[y*582+x]=0xffeeeeee;
        Config corners=new AutoLayout().detect(new Capture.Frame(582,1280,elbow)).config;
        check(corners.buttons.size()==7,"white wall elbows are not added as phantom cups: "+corners.buttons.size());
        AutoLayout.Stability gate=new AutoLayout.Stability();
        check(!gate.observe(ellipse,582,1280),"one frame cannot enable taps");
        check(gate.observe(ellipse,582,1280),"two stable frames accepted");
        check(!gate.observe(rectangle,582,1280),"changed topology immediately withholds readiness");
        check(!gate.observe(ellipse,582,1280),"alternating shapes never accumulate stability");
        gate.clear();check(!gate.observe(ellipse,582,1280),"failed detection clears previous frame evidence");
        check(gate.observe(ellipse,582,1280),"stable recovery is automatic");
        Capture.Frame original=scene(1,0xff2879f5,7,false,true,false);int[]touching=original.pixels.clone();
        for(int y=858;y<866;y++)for(int x=98;x<133;x++)touching[y*582+x]=0xff2879f5;
        check(new AutoLayout().detect(new Capture.Frame(582,1280,touching)).config.buttons.size()==7,"thin border touching a same-color square is separated spatially");
        int[]flat=original.pixels.clone();for(int y=850;y<920;y++)for(int x=130;x<186;x++)flat[y*582+x]=y<880?0xffced4df:0xff2879f5;
        Config lower=new AutoLayout().detect(new Capture.Frame(582,1280,flat)).config;boolean flatFound=false;
        for(int i=0;i<lower.buttons.size();i++)if(Math.abs(lower.buttons.get(i)[0]-158/582f)<.015&&lower.buttons.get(i)[1]<.75){flatFound=true;check(!lower.clickable[i],"flat square is not eligible");}
        check(flatFound,"flat square remains a detected obstacle");
        rejected(scene(1,0xff8296eb,7,false,false,false),"missing feeders must not produce invented paths");
        Capture.Frame drained=scene(1,0xff8296eb,7,false,false,false);
        Config retained=new AutoLayout().detect(drained,rectangle).config;
        check(AutoLayout.sameGeometry(rectangle,retained,582,1280),"visibly drained queues retain only previously observed paths");
        int[]occluded=drained.pixels.clone();for(int y=200;y<500;y++)for(int x=50;x<100;x++)occluded[y*582+x]=0xff505050;
        boolean rejectedOcclusion=false;try{new AutoLayout().detect(new Capture.Frame(582,1280,occluded),rectangle);}catch(IllegalStateException expected){rejectedOcclusion=true;}
        check(rejectedOcclusion,"an obscured queue cannot be treated as an empty queue");
        rejected(scene(1,0xffced4df,7,false,true,false),"invisible enclosure withholds exit confidence");
        rejected(scene(1,0xff8296eb,7,false,true,true),"open loop must pause detection");
        System.out.println("PASS: "+checks+" adaptive-layout pixel checks");
    }
}

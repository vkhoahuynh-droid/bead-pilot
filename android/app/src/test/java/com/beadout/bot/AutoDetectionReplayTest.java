package com.beadout.bot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Real-frame test: input paths are supplied explicitly; user footage is not bundled in the APK. */
public final class AutoDetectionReplayTest {
    static int checks;
    static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    static Capture.Frame frame(BufferedImage original){int w=582,h=Math.round(w*(float)original.getHeight()/original.getWidth());BufferedImage scaled=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=scaled.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(original,0,0,w,h,null);g.dispose();return new Capture.Frame(w,h,scaled.getRGB(0,0,w,h,null,0,w));}
    static void blockerChecks(File source)throws Exception{
        Capture.Frame base=frame(ImageIO.read(source));int[]pixels=base.pixels.clone();int w=base.width;
        for(int y=808;y<822;y++)for(int x=0;x<w;x++)pixels[y*w+x]=0xff8296eb;
        Config barred=new AutoLayout().detect(new Capture.Frame(w,base.height,pixels)).config;
        check(barred.buttons.size()==9,"a blue border is not counted as a square");int open=0;for(boolean b:barred.clickable)if(b)open++;
        check(open==0,"a continuous border blocks even raised squares from reaching the slots");
        pixels=base.pixels.clone();for(int y=1054;y<1120;y++)for(int x=124;x<193;x++)pixels[y*w+x]=0xff505050;
        Config hidden=new AutoLayout().detect(new Capture.Frame(w,base.height,pixels)).config;boolean found=false;
        for(int i=0;i<hidden.buttons.size();i++)if(hidden.unknownBoxes[i]){found=true;check(!hidden.clickable[i],"unknown black square is never selected");}
        check(found,"black concealed square is retained as an obstacle");
        pixels=base.pixels.clone();for(int y=931;y<957;y++)for(int x=302;x<370;x++)pixels[y*w+x]=0xff2879f5;
        Config blocked=new AutoLayout().detect(new Capture.Frame(w,base.height,pixels)).config;int blue=-1;for(int i=0;i<blocked.buttons.size();i++){float[]p=blocked.buttons.get(i);if(Math.abs(p[0]-.576)<.04&&Math.abs(p[1]-.77)<.07)blue=i;}
        check(blue>=0&&!blocked.clickable[blue],"raised lower square stays blocked by a square above it");
    }
    static void movementChecks(String directory)throws Exception{
        Capture.Frame first=frame(ImageIO.read(new File(directory,"001.png")));Config c=new AutoLayout().detect(first).config;AutoPerception perception=new AutoPerception(c);Tracker tracker=new Tracker(.125,true);
        for(int i=0;i<7;i++){
            Capture.Frame pixels=frame(ImageIO.read(new File(directory,String.format("%03d.png",i+1))));Capture.Frame timed=new Capture.Frame(pixels.width,pixels.height,pixels.pixels,1_000_000_000L+i*500_000_000L);
            Vision.Observation o=perception.read(timed);for(int j=0;j<4;j++)if(o.slots[j]>=0)o.progress[j]=0;
            tracker.update(o);
        }
        check(tracker.speedSamples()>=3,"movement becomes ready from real frame timestamps without a recording-FPS assumption");
        check(tracker.interval()>.08&&tracker.interval()<.18,"live displacement estimates plausible bead-row speed");
        check(tracker.completions()==0,"startup animations do not falsely complete a collector");
    }
    static void sequenceChecks(String directory)throws Exception{
        AutoLayout detector=new AutoLayout();Config previous=null;AutoLayout.Stability stability=new AutoLayout.Stability();int stable=0;
        for(int i=1;i<=19;i++){
            Capture.Frame f=frame(ImageIO.read(new File(directory,String.format("%03d.png",i))));
            Config current;try{current=detector.detect(f,previous).config;}catch(IllegalStateException e){check(i<7,"settled gameplay must be recognized at frame "+i+": "+e.getMessage());stability.clear();continue;}
            if(previous!=null){check(AutoLayout.sameGeometry(previous,current,f.width,f.height),"moving bead colors must not be mistaken for a new track at frame "+i);}
            if(stability.observe(current,f.width,f.height))stable++;
            previous=current;
        }
        check(stable>=10,"real gameplay recovers from slot animation and stays recognized");
    }
    public static void main(String[]args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Pass a directory of 0.5-second frames extracted from the supplied clip");
        for(int number:new int[]{1,7,11,15,19}){
            BufferedImage original=ImageIO.read(new File(args[0],String.format("%03d.png",number)));int w=582,h=Math.round(w*(float)original.getHeight()/original.getWidth());
            BufferedImage scaled=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=scaled.createGraphics();graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(original,0,0,w,h,null);graphics.dispose();
            int[]pixels=scaled.getRGB(0,0,w,h,null,0,w);long start=System.nanoTime();
            AutoLayout.Detection found=new AutoLayout().detect(new Capture.Frame(w,h,pixels));Config c=found.config;
            check(c.slots.size()==4,"four slots, frame "+number);check(c.buttons.size()==(number==1?9:5),"remaining squares, frame "+number);
            check(c.lanes.size()==1&&c.feeders.size()==2,"closed centerline and two open feeders, frame "+number);
            check(c.rowUnit==5&&c.loopRows>60&&c.loopRows<140,"five-wide row model, frame "+number);
            check(c.themeReady()&&c.backgroundKnown,"theme inferred without marked points");
            Vision.Observation observed=new AutoPerception(c).read(new Capture.Frame(w,h,pixels));
            check(java.util.Arrays.equals(observed.slots,number==1?new int[]{-1,-1,-1,-1}:new int[]{12,7,5,13}),"empty/occupied holding-slot colors from pixels");
            int eligible=0;for(boolean open:c.clickable)if(open)eligible++;
            if(number==1)check(eligible==3,"only the three raised, unobstructed top squares are selectable");
            for(float[]p:c.slots)check(Math.abs(p[1]-.582)<.04,"slot row stays aligned");
            for(Config.FeedPath feed:c.feeders)check(feed.skipConnector&&feed.points.size()>10,"finite queue with separate junction connector");
            System.out.println("frame "+number+": "+found.summary+"; eligible="+eligible+" rows="+c.loopRows+" pitch="+c.pitch+" in "+(System.nanoTime()-start)/1e6+"ms");
        }
        blockerChecks(new File(args[0],"001.png"));
        sequenceChecks(args[0]);
        movementChecks(args[0]);
        System.out.println("PASS: "+checks+" real-frame automatic detection checks");
    }
}

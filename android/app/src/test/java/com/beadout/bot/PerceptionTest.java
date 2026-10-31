package com.beadout.bot;

import java.util.*;

/** Host tests use Android type stubs but never call Android runtime APIs. */
public final class PerceptionTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static int[] colors(int zeros,int ones){int[]a=new int[zeros+ones];Arrays.fill(a,zeros,a.length,1);return a;}
    static Vision.Observation obs(long t,int[]slots,int[]lane){
        Vision.Observation o=new Vision.Observation(slots,new int[]{0,1},t);
        o.lanes.add(lane);o.total=lane.length;return o;
    }
    static int[] empty(){return new int[]{-1,-1,-1,-1};}
    static void tracker()throws Exception{
        Tracker t=new Tracker(.1);
        t.update(obs(1_000_000_000L,empty(),colors(120,100)));
        t.expectTap(0,0);
        t.update(obs(1_100_000_000L,empty(),colors(120,100))); // Frame before tap animation is visible.
        check(t.completions()==0,"empty animated tray cannot falsely validate probe");
        Planner.State s=t.update(obs(1_200_000_000L,new int[]{0,-1,-1,-1},colors(115,100)));
        check(s.remaining[0]==95,"count pickups before first visible active-square frame");
        s=t.update(obs(1_300_000_000L,new int[]{0,-1,-1,-1},colors(20,100)));
        check(t.settling()&&t.completions()==0,"wait for completion animation after exact 100 beads");
        s=t.update(obs(1_500_000_000L,empty(),colors(20,100)));
        check(!t.settling()&&t.completions()==1&&s.empty()==0,"verify completion only after 100 beads and freed slot");
        boolean rejected=false;
        try{t.update(obs(1_600_000_000L,empty(),colors(21,100)));}catch(Exception e){rejected=true;}
        check(rejected,"unexpected increase in supply is rejected");

        t=new Tracker(.1);t.update(obs(1_000_000_000L,empty(),colors(100,100)));t.expectTap(0,0);
        s=t.update(obs(1_200_000_000L,empty(),colors(0,100)));
        check(t.completions()==1,"handle an entire completion between observations");

        t=new Tracker(.1);t.update(obs(1_000_000_000L,empty(),colors(100,100)));t.expectTap(0,0);
        t.update(obs(1_100_000_000L,new int[]{0,-1,-1,-1},colors(95,100)));
        rejected=false;try{t.update(obs(1_200_000_000L,empty(),colors(90,100)));}catch(Exception e){rejected=true;}
        check(rejected,"early square disappearance rejected");

        t=new Tracker(.2);int[]a=new int[100];Random r=new Random(55);for(int i=0;i<a.length;i++)a[i]=r.nextInt(5);
        t.update(obs(1_000_000_000L,empty(),a));
        for(int shift=1;shift<=3;shift++){int[]b=new int[a.length];for(int i=0;i<a.length;i++)b[i]=a[(i+shift)%a.length];t.update(obs(1_000_000_000L+shift*100_000_000L,empty(),b));}
        check(t.speedSamples()==3&&Math.abs(t.interval()-.1)<.000001,"learn forward circulation speed");
        t=new Tracker(.1);t.update(obs(1_000_000_000L,empty(),a));
        int[]shifts={2,3,5,6,8};
        for(int i=0;i<shifts.length;i++){int[]moved=new int[a.length];for(int k=0;k<a.length;k++)moved[k]=a[(k+shifts[i])%a.length];t.update(obs(1_000_000_000L+(i+1)*200_000_000L,empty(),moved));}
        check(t.speedSamples()>=3,"alternating one/two-row frame shifts do not block readiness");
        check(Math.abs(t.interval()-.125)<.000001,"cumulative movement recovers eight rows per second");
        t=new Tracker(.1);t.update(obs(1_000_000_000L,empty(),a));int[]b=new int[a.length];for(int i=0;i<a.length;i++)b[i]=a[(i+a.length-1)%a.length];
        rejected=false;try{t.update(obs(1_100_000_000L,empty(),b));}catch(Exception e){rejected=true;}
        check(rejected,"reversed lane direction rejected");
    }
    static void vision(){
        for(int i=0;i<Config.RGB.length;i++)check(Vision.classify(Config.RGB[i])==i,"palette "+Config.NAMES[i]);
        check(Vision.classify(0xffbaddca)==Vision.EMPTY,"mint background is empty, not white");
        Config c=new Config();c.buttons.clear();c.buttons.add(new float[]{.5f,.5f});
        int w=582,h=1280;int[]pixels=new int[w*h];Arrays.fill(pixels,0xffbaddca);
        for(int y=608;y<=672;y++)for(int x=259;x<=323;x++)pixels[y*w+x]=0xfff5f5f5;
        for(int y=612;y<=668;y++)for(int x=263;x<=319;x++)pixels[y*w+x]=Config.RGB[0];
        Capture.Frame frame=new Capture.Frame(w,h,pixels);Vision v=new Vision(c);
        check(v.read(frame).board[0]==0,"outlined selectable yellow square recognized");
        for(int y=608;y<=672;y++)for(int x=259;x<=323;x++)pixels[y*w+x]=Config.RGB[0];
        check(v.read(frame).board[0]<0,"colored but unoutlined square is blocked");
        c.requireBorder=false;
        check(v.read(frame).board[0]==0,"selectability rule can be calibrated");
        Vision.Observation first=v.read(frame);Arrays.fill(pixels,Config.RGB[3]);
        check(first.board[0]==0,"observations retain labels, not mutable frame pixels");
    }
    static void markers(){
        int w=582,h=1280;int[]pixels=new int[w*h],pattern=new int[128];
        for(int i=0;i<pattern.length;i++)pattern[i]=(i%3==0)?0xffeeeeee:0xff602080;
        SceneMarker m=new SceneMarker(.5f,.4f,.2f,.05f,pattern);
        check(m.informative(),"win text fingerprint requires contrast");
        int[]positions=new int[128];
        for(int r=0;r<8;r++)for(int c=0;c<16;c++){
            int x=Math.round((m.x-m.w/2+m.w*(c+.5f)/16)*w),y=Math.round((m.y-m.h/2+m.h*(r+.5f)/8)*h);
            positions[r*16+c]=y*w+x;pixels[y*w+x]=pattern[r*16+c];
        }
        Capture.Frame f=new Capture.Frame(w,h,pixels);
        check(m.confidence(f)==1,"exact taught screen matches");
        for(int i=0;i<10;i++)pixels[positions[i]]=0xff00ff00;
        check(m.confidence(f)>=.90,"small transient occlusion tolerated");
        for(int i=10;i<30;i++)pixels[positions[i]]=0xff00ff00;
        check(m.confidence(f)<.90,"different scene does not confirm win");
        SceneMarker other=new SceneMarker(.5f,.7f,.2f,.05f,pattern);
        check(other.confidence(f)<.90,"independent Next marker must also match");
        int[]flat=new int[128];Arrays.fill(flat,0xffaabbcc);
        check(!new SceneMarker(.5f,.4f,.2f,.05f,flat).informative(),"flat backgrounds cannot be taught as win text");
    }
    static void themesAndFeeds()throws Exception{
        Config c=new Config();c.slots.clear();for(int i=0;i<4;i++){c.slots.add(new float[]{.2f+i*.15f,.75f});c.emptySlotKnown[i]=true;c.emptySlotRgb[i]=0xffc7c5ff;}
        c.backgroundKnown=true;c.backgroundRgb=0xffb7b3fa;c.requireBorder=false;c.buttons.clear();c.buttons.add(new float[]{.5f,.9f});
        int w=100,h=100;int[]pixels=new int[w*h];Arrays.fill(pixels,c.backgroundRgb);
        for(float[]slot:c.slots){int x=Math.round(slot[0]*w),y=Math.round(slot[1]*h);for(int yy=y-2;yy<=y+2;yy++)for(int xx=x-2;xx<=x+2;xx++)pixels[yy*w+xx]=0xffc7c5ff;}
        Capture.Frame frame=new Capture.Frame(w,h,pixels);Vision v=new Vision(c);
        check(Arrays.equals(v.read(frame).slots,empty()),"purple empty slots use sampled colors");
        check(v.read(frame).board[0]==Vision.EMPTY,"purple floor is not a lavender square");
        for(int yy=88;yy<=92;yy++)for(int xx=48;xx<=52;xx++)pixels[yy*w+xx]=0xffed1b35;
        check(v.read(frame).board[0]==12,"red square recognized in new theme");
        for(int yy=88;yy<=92;yy++)for(int xx=48;xx<=52;xx++)pixels[yy*w+xx]=0xff6c73bf;
        check(v.read(frame).board[0]==Vision.EMPTY,"dark desaturated purple rail is empty background");
        for(int yy=88;yy<=92;yy++)for(int xx=48;xx<=52;xx++)pixels[yy*w+xx]=Config.RGB[2];
        check(v.read(frame).board[0]==2,"lavender square is not discarded as purple floor");
        check(Vision.classify(0xffa400ff)==7,"bright purple variant recognized");
        check(Vision.classify(0xff2879f5)==13,"royal blue is distinct from cyan");
        Config bad=new Config();bad.winMarker=new SceneMarker(.4f,.7f,.1f,.02f,new int[128]);bad.nextMarker=bad.winMarker;
        check(!bad.navigationReady(),"gameplay WIN marker cannot arm automatic navigation");

        ArrayList<float[]>loop=new ArrayList<>();loop.add(new float[]{.2f,.2f});loop.add(new float[]{.8f,.2f});loop.add(new float[]{.8f,.5f});loop.add(new float[]{.2f,.5f});
        PathGeometry g=new PathGeometry(loop,true,w,h);check(Math.abs(g.length-180)<.001,"closed geometry length");
        double[]near=g.nearest(.5f,.19f);check(Math.abs(near[0]-30)<.001&&Math.abs(near[1]-1)<.001,"junction projection onto loop");
        c.pitch=10;c.lanes.add(loop);c.feeders.add(new Config.FeedPath(0,Arrays.asList(new float[]{.5f,.2f},new float[]{.5f,0})));
        for(int i=0;i<18;i++){float[]p=g.point((i+.5)*10);int x=Math.round(p[0]*w),y=Math.round(p[1]*h);for(int yy=y-2;yy<=y+2;yy++)for(int xx=x-2;xx<=x+2;xx++)pixels[yy*w+xx]=Config.RGB[0];}
        for(int y:new int[]{0,10})for(int yy=Math.max(0,y-2);yy<=y+2;yy++)for(int xx=48;xx<=52;xx++)pixels[yy*w+xx]=Config.RGB[0];
        Vision.Observation seen=v.read(frame);
        check(seen.topologyValid&&seen.fixedLoops.get(0),"feeder marks loop as fixed capacity");
        check(seen.feeds.size()==1&&seen.feeds.get(0).beads.length==2,"open feeder does not get an invented closing segment");
        check(seen.counts()[0]==20,"junction is not double counted");

        Tracker t=new Tracker(.1);Vision.Observation initial=obs(1_000_000_000L,empty(),colors(10,10));initial.fixedLoops.set(0);initial.feeds.add(new Vision.FeedObservation(0,3,colors(90,0)));t.update(initial);t.expectTap(0,0);
        Vision.Observation after=obs(1_100_000_000L,new int[]{0,-1,-1,-1},colors(10,10));after.fixedLoops.set(0);after.feeds.add(new Vision.FeedObservation(0,3,colors(85,0)));
        Planner.State s=t.update(after);check(s.remaining[0]==95&&s.beadCount()==105,"refill transfers conserve total inventory and advance fill count");
        int[]ring=new int[20];Arrays.fill(ring,0,10,-1);Arrays.fill(ring,10,20,1);
        Vision.Observation done=obs(1_300_000_000L,empty(),ring);done.fixedLoops.set(0);done.feeds.add(new Vision.FeedObservation(0,3,new int[0]));s=t.update(done);
        check(t.completions()==1&&s.beadCount()==10,"fed completion verifies 100 beads across ring and queues");
    }
    public static void main(String[]args)throws Exception{tracker();vision();markers();themesAndFeeds();System.out.println("PASS: "+checks+" recognition/tracking checks");}
}

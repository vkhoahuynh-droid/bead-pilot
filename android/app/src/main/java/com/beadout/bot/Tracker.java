package com.beadout.bot;

import java.util.*;

/** Validates conservation of beads and learns circulation from observed color boundaries. */
public final class Tracker {
    private Vision.Observation previous,reference;
    private final int[] remaining=new int[4];
    private double interval;
    private int speedSamples;
    private final ArrayDeque<Double> motionTimes=new ArrayDeque<>(),motionSteps=new ArrayDeque<>();
    private int completions;
    private final long[] finishingSince=new long[4];
    private boolean tapped;
    private final boolean progressMode;
    private int pendingColor=-1,pendingSlot=-1;
    public String note="Waiting for empty active slots and moving beads";
    public Tracker(double initialInterval){this(initialInterval,false);}
    public Tracker(double initialInterval,boolean progressMode){interval=initialInterval;this.progressMode=progressMode;}
    public void expectTap(int color,int slot){tapped=true;pendingColor=color;pendingSlot=slot;}
    public double interval(){return interval;}
    public int speedSamples(){return speedSamples;}
    public int completions(){return completions;}
    public boolean settling(){for(long t:finishingSince)if(t!=0)return true;return false;}
    public Planner.State update(Vision.Observation o)throws Exception{
        if(progressMode)return updateProgress(o);
        if(!o.topologyValid)throw new Exception("A feeder junction does not connect to its loop; adjust its first point");
        if(o.lanes.isEmpty()||o.confidence()<.995)throw new Exception("Track recognition uncertain; adjust traced lanes, pitch or palette");
        for(int c:o.slots)if(c==Vision.UNKNOWN)throw new Exception("Cannot read an active slot");
        for(int i=0;i<o.lanes.size();i++)for(int c:o.lanes.get(i))if(c<0&&!(c==Vision.EMPTY&&o.fixedLoops.get(i)))throw new Exception("Unrecognized bead; calibration required");
        for(Vision.FeedObservation feed:o.feeds)for(int c:feed.beads)if(c<0)throw new Exception("Unrecognized feeder bead; calibration required");
        if(previous==null){
            for(int c:o.slots)if(c>=0)throw new Exception("Start inspection with all four active slots empty so fill counts are known");
            previous=o;reference=o;
        }else{
            int[]before=previous.counts(),after=o.counts();int[]removed=new int[before.length];
            for(int c=0;c<removed.length;c++){
                removed[c]=before[c]-after[c];
                if(removed[c]<0)throw new Exception("Bead count increased; path/pitch or queue-compaction model is incorrect");
                boolean had=tapped&&pendingColor==c;for(int slot:previous.slots)had|=slot==c;
                if(removed[c]>0&&!had)throw new Exception("Beads disappeared without a matching collector; recalibrate");
            }
            for(int i=0;i<4;i++){
                int old=previous.slots[i],now=o.slots[i];
                boolean newlyTapped=tapped&&i==pendingSlot&&old<0&&(now==pendingColor||removed[pendingColor]>=100);
                if(newlyTapped){old=pendingColor;remaining[i]=Planner.CAPACITY;tapped=false;pendingColor=-1;pendingSlot=-1;}
                if(old>=0){
                    int taken=Math.min(remaining[i],removed[old]);remaining[i]-=taken;removed[old]-=taken;
                    if(now!=old&&remaining[i]!=0)throw new Exception("Square disappeared before 100 counted beads; model mismatch");
                    if(now==old&&remaining[i]==0){
                        if(finishingSince[i]==0)finishingSince[i]=o.nanos;
                        if(o.nanos-finishingSince[i]>1_500_000_000L)throw new Exception("Predicted completion not observed; model mismatch");
                    }
                    if(now!=old&&remaining[i]==0){completions++;finishingSince[i]=0;}
                }
                if(now>=0&&now!=old){
                    if(!tapped)throw new Exception("Unexpected new active square");remaining[i]=Planner.CAPACITY;tapped=false;
                }
            }
            for(int value:removed)if(value!=0)throw new Exception("Unaccounted bead removal");
            learnSpeed(o);
            previous=o;
        }
        Planner.State state=new Planner.State(o.board);
        for(int i=0;i<4;i++){state.colors[i]=o.slots[i];state.remaining[i]=o.slots[i]>=0?remaining[i]:0;}
        for(int[]lane:o.lanes)state.lanes.add(new Planner.Lane(lane,interval/2,interval));
        for(Vision.FeedObservation feed:o.feeds)state.lanes.get(feed.lane).addFeed(feed.entry,feed.beads);
        note=speedSamples>=3?"Movement calibrated; waiting for simulation agreement":"Learning bead speed ("+speedSamples+"/3)";
        return state;
    }
    private Planner.State updateProgress(Vision.Observation o)throws Exception{
        if(o.lanes.isEmpty()||o.confidence()<.85)throw new Exception("Waiting for an unobscured board");
        for(int color:o.slots)if(color==Vision.UNKNOWN)throw new Exception("Waiting for clear holding slots");
        if(previous==null){
            for(int i=0;i<4;i++)if(o.slots[i]>=0){if(o.progress[i]<0)throw new Exception("Reading existing fill percentages");remaining[i]=100-o.progress[i];if(remaining[i]==0)finishingSince[i]=o.nanos;}
            reference=o;
        }else{
            for(int i=0;i<4;i++){
                int before=previous.slots[i],now=o.slots[i];
                if(before>=0&&now==Vision.EMPTY){remaining[i]=0;finishingSince[i]=0;completions++;}
                else if(before>=0&&now>=0&&before!=now)throw new Exception("Holding-slot animation is still changing");
                if(now>=0&&before!=now){remaining[i]=o.progress[i]>=0?100-o.progress[i]:100;tapped=false;}
                if(now>=0&&o.progress[i]>=0){int value=100-o.progress[i];if(value<=remaining[i])remaining[i]=value;}
                if(now>=0&&remaining[i]==0){if(finishingSince[i]==0)finishingSince[i]=o.nanos;if(o.nanos-finishingSince[i]>2_000_000_000L)throw new Exception("Rechecking a completion reading");}
            }
            learnSpeed(o);
        }
        previous=o;Planner.State s=new Planner.State(o.board);s.inventorySlack=o.rowUnit*4;
        for(int i=0;i<4;i++){s.colors[i]=o.slots[i];s.remaining[i]=o.slots[i]>=0?remaining[i]:0;}
        for(int[]lane:o.lanes)s.lanes.add(new Planner.Lane(lane,interval/2,interval,o.rowUnit));
        for(Vision.FeedObservation feed:o.feeds)s.lanes.get(feed.lane).addFeed(feed.entry,feed.beads);
        note=speedSamples>=3?"Movement tracked":"Measuring bead movement";return s;
    }
    private void learnSpeed(Vision.Observation o)throws Exception{
        if(reference==null){reference=o;return;}
        if(o.lanes.size()!=reference.lanes.size())throw new Exception("Lane count changed");
        double elapsed=(o.nanos-reference.nanos)/1e9;
        ArrayList<Double> estimates=new ArrayList<>();
        for(int i=0;i<o.lanes.size();i++){
            int[]a=reference.lanes.get(i),b=o.lanes.get(i);int n=a.length;
            if(n!=b.length){reference=o;return;}
            if(n<10)continue;
            int best=-1,bestErrors=Integer.MAX_VALUE,ties=0,low=0,high=0;
            int limit=Math.min(n/3,Math.max(4,(int)(elapsed/.01)));
            for(int shift=-limit;shift<=limit;shift++){
                int errors=0;for(int j=0;j<n;j++)if(b[j]!=a[(j+shift+n)%n])errors++;
                if(errors<bestErrors){bestErrors=errors;best=shift;low=high=shift;ties=1;}else if(errors==bestErrors){ties++;high=shift;}
            }
            boolean matched=bestErrors<=(progressMode?n*.12:0)&&(ties==1||progressMode&&high-low<=1);
            double shift=(low+high)/2.0;
            if(matched&&shift<0)throw new Exception("Trace direction reversed: trace FROM pickup AGAINST bead travel");
            if(matched&&shift>0){double measured=elapsed/shift;if(measured>=.01&&measured<=10)estimates.add(measured);}
        }
        if(!estimates.isEmpty()){
            Collections.sort(estimates);double instant=estimates.get(estimates.size()/2);
            motionTimes.addLast(elapsed);motionSteps.addLast(elapsed/instant);
            if(motionTimes.size()>8){motionTimes.removeFirst();motionSteps.removeFirst();}
            double seconds=0,steps=0;for(double t:motionTimes)seconds+=t;for(double n:motionSteps)steps+=n;
            double measured=seconds/steps;
            // Average elapsed time / travelled rows: single-frame shifts are integer-quantized.
            if(speedSamples>=3&&Math.abs(measured-interval)/interval>.35)speedSamples=0;
            interval=measured;
            speedSamples++;reference=o;
        }else if(elapsed>8){reference=o;}
    }
}

package com.beadout.bot;

import java.util.*;

/** Independent scalar comparison for fixed-capacity loops with finite feeder queues. */
public final class FeederTest {
    static int checks;
    static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    static int[] expand(Planner.Lane lane){int[]a=new int[lane.size()];int k=0;for(Planner.Run r:lane.runs)for(int i=0;i<r.count;i++)a[k++]=r.color;return a;}
    static void fixedCases(){
        Planner.State s=new Planner.State(new int[]{0});Planner.Lane lane=new Planner.Lane(new int[]{0,1,1},0,.01);
        int[]queue=new int[99];lane.addFeed(1,queue);s.lanes.add(lane);
        check(s.supply(2)[0]==100&&s.beadCount()==102,"supply includes visible feeder inventory");
        s.tap(0);Planner.advance(s,3,false);
        check(s.completed==1&&s.remaining[0]==0,"100-bead fill uses replacement beads from feeder");
        check(lane.size()==3&&s.beadCount()==2,"loop capacity remains fixed while collected beads leave");
        check(lane.feeds.get(0).queue.size()==0,"feeder is finite and drains exactly once");

        s=new Planner.State(new int[0]);lane=new Planner.Lane(new int[]{0,1,2,3},0,1);lane.addFeed(2,new int[]{7,8});s.lanes.add(lane);s.colors[0]=0;s.remaining[0]=100;
        Planner.advance(s,0,false);
        check(lane.feeds.get(0).queue.size()==2,"feed cannot overwrite occupied loop cell");
        Planner.advance(s,1,false);check(Arrays.equals(expand(lane),new int[]{2,3,7,1}),"feed fills gap only when it reaches junction");
        s.colors[1]=2;s.remaining[1]=100;Planner.advance(s,3,false);
        check(Arrays.equals(expand(lane),new int[]{7,1,8,3}),"FIFO order and multiple gap arrivals");
        Planner.State copy=s.copy();check(copy.key().equals(s.key()),"feed state participates in cache key");
        Planner.advance(copy,20,false);check(Arrays.equals(expand(lane),new int[]{7,1,8,3}),"search copies do not mutate observed ring");
        s=new Planner.State(new int[]{0});lane=new Planner.Lane(new int[]{0,1,1},0,.01);lane.addFeed(1,new int[99]);s.lanes.add(lane);
        check(new Planner(2,32,4,1000).search(s).firstAction()==0,"predictive choice can use future feeder supply");
    }
    static final class Scalar {
        final ArrayList<Integer>ring=new ArrayList<>();
        final int[]entries;
        final ArrayList<ArrayDeque<Integer>>queues=new ArrayList<>();
        double next,dt;
        Scalar(int[]ring,int[]entries,int[][]feeds,double next,double dt){for(int c:ring)this.ring.add(c);this.entries=entries;for(int[]f:feeds){ArrayDeque<Integer>q=new ArrayDeque<>();for(int c:f)q.add(c);queues.add(q);}this.next=next;this.dt=dt;}
    }
    static void randomized(){
        Random r=new Random(23100);
        for(int trial=0;trial<200;trial++){
            Planner.State s=new Planner.State(new int[]{0,1,2});for(int c=0;c<3;c++)s.tap(c);
            int[]remaining={100,100,100,0},colors={0,1,2,-1};int complete=0,collected=0;
            ArrayList<Scalar>lanes=new ArrayList<>();
            for(int li=0;li<1+trial%3;li++){
                int n=8+r.nextInt(25);int[]ring=new int[n];for(int i=0;i<n;i++)ring[i]=r.nextInt(5)-1;
                int[]entries={1,n-2};int[][]feeds={new int[5+r.nextInt(30)],new int[5+r.nextInt(30)]};for(int[]q:feeds)for(int i=0;i<q.length;i++)q[i]=r.nextInt(4);
                double dt=.01+r.nextInt(5)*.007,at=li*.003;
                Planner.Lane lane=new Planner.Lane(ring,at,dt);for(int i=0;i<2;i++)lane.addFeed(entries[i],feeds[i]);s.lanes.add(lane);
                lanes.add(new Scalar(ring,entries,feeds,at,dt));
            }
            double target=.1+r.nextDouble()*4;
            while(true){
                Scalar lane=null;for(Scalar l:lanes)if(lane==null||l.next<lane.next)lane=l;if(lane.next>target)break;
                int bead=lane.ring.remove(0),slot=-1;for(int i=0;i<4;i++)if(bead>=0&&colors[i]==bead){slot=i;break;}
                if(slot>=0){lane.ring.add(-1);remaining[slot]--;collected++;if(remaining[slot]==0){colors[slot]=-1;complete++;}}
                else lane.ring.add(bead);
                for(int i=0;i<lane.entries.length;i++)if(lane.ring.get(lane.entries[i])==-1&&!lane.queues.get(i).isEmpty())lane.ring.set(lane.entries[i],lane.queues.get(i).removeFirst());
                lane.next+=lane.dt;
            }
            Planner.advance(s,target,false);
            check(Arrays.equals(s.remaining,remaining)&&Arrays.equals(s.colors,colors),"collector state matches scalar "+trial);
            check(s.completed==complete&&s.collected==collected,"completion matches scalar "+trial);
            for(int i=0;i<lanes.size();i++){
                Scalar ref=lanes.get(i);int[]expected=ref.ring.stream().mapToInt(Integer::intValue).toArray();check(Arrays.equals(expand(s.lanes.get(i)),expected),"ring order matches scalar "+trial);
                for(int j=0;j<2;j++)check(Arrays.equals(expand(s.lanes.get(i).feeds.get(j).queue),ref.queues.get(j).stream().mapToInt(Integer::intValue).toArray()),"feed queue matches scalar "+trial);
            }
        }
    }
    public static void main(String[]args){fixedCases();randomized();System.out.println("PASS: "+checks+" feeder simulation checks");}
}

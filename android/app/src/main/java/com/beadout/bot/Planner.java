package com.beadout.bot;

import java.util.*;

/** Pure Java event simulator. Unknown board entries are absent, never guessed. */
public final class Planner {
    public static final int CAPACITY = 100;
    public static final int WAIT = -1;
    public static final class Run {
        public final int color;
        public int count;
        public Run(int color, int count) { this.color = color; this.count = count; }
        Run copy() { return new Run(color, count); }
    }
    public static final class Lane {
        public final ArrayList<Run> runs = new ArrayList<>();
        public final ArrayList<Feed> feeds=new ArrayList<>();
        public final int unit;
        public double next, interval;
        public Lane(int[] beads, double next, double interval) {
            this(beads,next,interval,1);
        }
        public Lane(int[] beads,double next,double interval,int unit){
            if (interval <= 0 || next < 0) throw new IllegalArgumentException("Invalid lane timing");
            if(unit!=1&&unit!=5)throw new IllegalArgumentException("Invalid bead-row weight");this.unit=unit;
            this.next = next; this.interval = interval;
            for (int c : beads) {
                if (c < -1) throw new IllegalArgumentException("Unknown bead color");
                if (!runs.isEmpty() && runs.get(runs.size()-1).color == c) runs.get(runs.size()-1).count++;
                else runs.add(new Run(c, 1));
            }
        }
        Lane copy() {
            Lane l = new Lane(new int[0], next, interval,unit);
            for (Run r : runs) l.runs.add(r.copy());
            for(Feed f:feeds)l.feeds.add(f.copy());
            return l;
        }
        public void addFeed(int entry,int[]beads){if(entry<0||entry>=size())throw new IllegalArgumentException("Feed junction outside loop");for(Feed f:feeds)if(f.entry==entry)throw new IllegalArgumentException("Two feeds share a junction");feeds.add(new Feed(entry,beads));}
        int cell(int index){for(Run r:runs){if(index<r.count)return r.color;index-=r.count;}throw new IndexOutOfBoundsException();}
        void insertAtGap(int index,int color){
            for(int i=0;i<runs.size();i++){Run r=runs.get(i);if(index>=r.count){index-=r.count;continue;}
                if(r.color!=-1)throw new IllegalStateException("Feed tried to overwrite a bead");
                int after=r.count-index-1;runs.remove(i);
                if(after>0)runs.add(i,new Run(-1,after));runs.add(i,new Run(color,1));if(index>0)runs.add(i,new Run(-1,index));
                for(int k=Math.max(0,i-1);k<runs.size()-1;)if(runs.get(k).color==runs.get(k+1).color){runs.get(k).count+=runs.remove(k+1).count;}else k++;
                return;
            }throw new IndexOutOfBoundsException();
        }
        void fedTick(boolean consumed){
            rotate(1,consumed);
            if(consumed){if(!runs.isEmpty()&&runs.get(runs.size()-1).color==-1)runs.get(runs.size()-1).count++;else runs.add(new Run(-1,1));}
            for(Feed feed:feeds)if(!feed.queue.runs.isEmpty()&&cell(feed.entry)==-1){int color=feed.queue.head();feed.queue.rotate(1,true);insertAtGap(feed.entry,color);}
        }
        int size() { int n = 0; for (Run r : runs) n += r.count; return n; }
        int head() { return runs.get(0).color; }
        void rotate(int count, boolean consumed) {
            if (count <= 0) return;
            Run r = runs.get(0); int color = r.color;
            r.count -= count;
            if (r.count == 0) runs.remove(0);
            if (!consumed) {
                if (!runs.isEmpty() && runs.get(runs.size()-1).color == color) runs.get(runs.size()-1).count += count;
                else runs.add(new Run(color, count));
            }
        }
    }
    public static final class Feed {
        public final int entry;
        public final Lane queue;
        Feed(int entry,int[]beads){for(int c:beads)if(c<0)throw new IllegalArgumentException("Unknown feeder bead");this.entry=entry;queue=new Lane(beads,0,1);}
        private Feed(int entry,Lane queue){this.entry=entry;this.queue=queue;}
        Feed copy(){return new Feed(entry,queue.copy());}
    }
    public static final class State {
        public final ArrayList<Lane> lanes = new ArrayList<>();
        public final int[] colors = {-1, -1, -1, -1};
        public final int[] remaining = new int[4];
        public int[] board;
        public double time;
        public int completed, collected;
        public int inventorySlack;
        public int[] unlockValues;
        public int unlockScore;
        public State(int[] board) { this.board = board.clone();unlockValues=new int[board.length]; }
        public State copy() {
            State s = new State(board);
            for (Lane l : lanes) s.lanes.add(l.copy());
            System.arraycopy(colors, 0, s.colors, 0, 4);
            System.arraycopy(remaining, 0, s.remaining, 0, 4);
            s.time = time; s.completed = completed; s.collected = collected;
            s.inventorySlack=inventorySlack;
            s.unlockValues=unlockValues.clone();s.unlockScore=unlockScore;
            return s;
        }
        public int empty() { for (int i=0;i<4;i++) if (colors[i]<0) return i; return -1; }
        public boolean tap(int index) {
            int slot = empty();
            if (slot < 0 || index < 0 || index >= board.length || board[index] < 0) return false;
            colors[slot] = board[index]; remaining[slot] = CAPACITY; board[index] = -1;
            if(index<unlockValues.length)unlockScore+=unlockValues[index];
            return true;
        }
        public int beadCount() { int n=0; for (Lane l: lanes){for(Run r:l.runs)if(r.color>=0)n+=r.count*l.unit;for(Feed f:l.feeds)for(Run r:f.queue.runs)n+=r.count*l.unit;}return n; }
        public int[] supply(int colorCount) {
            int[] n = new int[colorCount];
            for(Lane l:lanes){for(Run r:l.runs)if(r.color>=0)n[r.color]+=r.count*l.unit;for(Feed f:l.feeds)for(Run r:f.queue.runs)n[r.color]+=r.count*l.unit;}
            return n;
        }
        public boolean stranded(int colorCount) {
            int[] supply = supply(colorCount), need = new int[colorCount];
            for(int i=0;i<4;i++) if(colors[i]>=0) need[colors[i]] += remaining[i];
            for(int c=0;c<colorCount;c++) if(need[c]>supply[c]+inventorySlack) return true;
            return false;
        }
        String key() {
            StringBuilder b=new StringBuilder(Arrays.toString(board)).append(Arrays.toString(colors)).append(Arrays.toString(remaining));
            b.append('S').append(inventorySlack).append(Arrays.toString(unlockValues)).append('U').append(unlockScore);
            for(Lane l:lanes) { b.append('|').append(l.unit).append(':').append(Math.round((l.next-time)*1000)).append(':');
                for(Run r:l.runs)b.append(r.color).append(',').append(r.count).append(';');
                for(Feed f:l.feeds){b.append('F').append(f.entry).append(':');for(Run r:f.queue.runs)b.append(r.color).append(',').append(r.count).append(';');}}
            return b.toString();
        }
    }
    public static final class Event {
        public final int index;
        public final double at;
        Event(int index,double at){this.index=index;this.at=at;}
    }
    public static final class Result {
        public final List<Event> actions;
        public final State predicted;
        public final int expanded;
        Result(List<Event> actions,State predicted,int expanded){this.actions=actions;this.predicted=predicted;this.expanded=expanded;}
        public int firstAction(){return actions.isEmpty()?WAIT:actions.get(0).index;}
    }
    private final int colorCount, width, depth;
    private final long budgetNanos;
    public Planner(int colorCount,int width,int depth,long budgetMillis){
        this.colorCount=colorCount;this.width=width;this.depth=depth;this.budgetNanos=budgetMillis*1000000;
    }
    private static int collector(State s,int color){
        if(color<0)return -1;
        for(int i=0;i<4;i++)if(s.colors[i]==color)return i;
        return -1;
    }
    /** Advance exactly to target or first completion. Empty positions circulate too. */
    public static boolean advance(State s,double target,boolean stopOnCompletion){
        if(target<s.time)throw new IllegalArgumentException("Time reversed");
        int guard=0;
        while(++guard<200000){
            Lane lane=null;
            for(Lane l:s.lanes)if(!l.runs.isEmpty()&&(lane==null||l.next<lane.next))lane=l;
            if(lane==null||lane.next>target){s.time=target;return false;}
            int slot=collector(s,lane.head());
            // Batch a run up to the next competing lane event or the target time.
            double boundary=target;
            for(Lane other:s.lanes)if(other!=lane&&!other.runs.isEmpty())boundary=Math.min(boundary,other.next);
            int n=Math.min(lane.runs.get(0).count,Math.max(1,(int)Math.ceil((boundary-lane.next)/lane.interval)));
            if(!lane.feeds.isEmpty())n=1;
            if(slot>=0)n=Math.min(n,s.remaining[slot]/lane.unit);
            if(n<=0)throw new IllegalStateException("Capacity is not divisible by row size");
            s.time=lane.next+(n-1)*lane.interval;
            if(lane.feeds.isEmpty())lane.rotate(n,slot>=0);else lane.fedTick(slot>=0);
            lane.next+=n*lane.interval;
            if(slot>=0){
                s.remaining[slot]-=n*lane.unit;s.collected+=n*lane.unit;
                if(s.remaining[slot]==0){s.colors[slot]=-1;s.completed++;if(stopOnCompletion)return true;}
            }
        }
        throw new IllegalStateException("Simulation event limit exceeded");
    }
    private double lap(State s){double t=1;for(Lane l:s.lanes){int travel=l.size();if(!l.feeds.isEmpty()){travel*=3;for(Feed f:l.feeds)travel+=f.queue.size();}t=Math.max(t,travel*l.interval);}return t;}
    private static final class Node {
        State s;ArrayList<Event> path;double score;
        Node(State s,ArrayList<Event> path,double score){this.s=s;this.path=path;this.score=score;}
    }
    private double score(State s,double initialTime){
        int occupied=0;for(int c:s.colors)if(c>=0)occupied++;
        return s.completed*10000+s.collected*10-occupied*15-(s.time-initialTime)*2+s.unlockScore*40;
    }
    public Result search(State initial){
        return search(initial,true);
    }
    public Result search(State initial,boolean allowDuplicateCollectors){
        long deadline=System.nanoTime()+budgetNanos;
        ArrayList<Node> beam=new ArrayList<>();beam.add(new Node(initial.copy(),new ArrayList<>(),0));
        Node best=beam.get(0);int expanded=0;
        for(int d=0;d<depth&&System.nanoTime()<deadline;d++){
            PriorityQueue<Node> next=new PriorityQueue<>(Comparator.comparingDouble(n->n.score));
            HashSet<String> seen=new HashSet<>();
            outer:for(Node parent:beam){
                for(int action=-1;action<parent.s.board.length;action++){
                    if(System.nanoTime()>deadline)break outer;
                    State s=parent.s.copy();ArrayList<Event> path=new ArrayList<>(parent.path);
                    if(action>=0){
                        if(!allowDuplicateCollectors){boolean duplicate=false;for(int color:s.colors)duplicate|=color>=0&&color==s.board[action];if(duplicate)continue;}
                        if(!s.tap(action)||s.stranded(colorCount))continue;
                        path.add(new Event(action,s.time));
                    }else{
                        boolean active=false;for(int c:s.colors)active|=c>=0;
                        if(!active)continue;
                        if(!advance(s,s.time+lap(s)*1.05,true))continue;
                        path.add(new Event(WAIT,parent.s.time));
                    }
                    expanded++;
                    if(!seen.add(s.key()))continue;
                    if(seen.size()>width*8)seen.clear(); // Bound retained cache memory, even on large boards.
                    // Evaluate reachable completion, but keep the immediate state for further branching.
                    State future=s.copy();advance(future,future.time+lap(future)*1.05,false);
                    double value=score(future,initial.time);
                    Node node=new Node(s,path,value);
                    if(next.size()<width)next.add(node);
                    else if(value>next.peek().score){next.poll();next.add(node);}
                    if(value>best.score)best=new Node(future,path,value);
                }
            }
            if(next.isEmpty())break;
            beam=new ArrayList<>(next);beam.sort((a,b)->Double.compare(b.score,a.score));
        }
        return new Result(best.path,best.s,expanded);
    }
}

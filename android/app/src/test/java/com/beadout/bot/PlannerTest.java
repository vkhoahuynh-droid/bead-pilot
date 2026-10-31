package com.beadout.bot;

import java.util.*;

/** Dependency-free tests executable with a JDK; includes a separate scalar reference simulator. */
public final class PlannerTest {
    private static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static int[] repeat(int color,int n){int[]a=new int[n];Arrays.fill(a,color);return a;}
    static Planner.State state(int...board){return new Planner.State(board);}
    static void fixedCases(){
        Planner.State s=state(0);s.lanes.add(new Planner.Lane(repeat(0,100),0,.01));check(s.tap(0),"tap accepted");
        Planner.advance(s,.98,false);check(s.remaining[0]==1,"99 beads leave 1 percent");check(s.completed==0,"not prematurely complete");
        Planner.advance(s,.99,false);check(s.completed==1&&s.empty()==0&&s.beadCount()==0,"100 beads free a slot");

        s=state(1);s.lanes.add(new Planner.Lane(repeat(0,40),0,.1));s.tap(0);Planner.advance(s,20,false);
        check(s.beadCount()==40&&s.remaining[0]==100,"unmatched beads continue circulating");

        s=state(0);int[]beads=new int[200];Arrays.fill(beads,100,200,1);s.lanes.add(new Planner.Lane(beads,0,.01));s.tap(0);
        Planner.advance(s,.49,false);check(s.remaining[0]==50,"partial fill preserved");Planner.advance(s,6,false);
        check(s.completed==1&&s.beadCount()==100,"matching beads removed only once");

        s=state(0,0);s.lanes.add(new Planner.Lane(repeat(0,200),0,.01));s.tap(0);s.tap(1);Planner.advance(s,3,false);
        check(s.completed==2,"duplicate collectors receive exactly 100 each");

        s=state(0,1,2,3,4);for(int i=0;i<4;i++)check(s.tap(i),"four slots accepted");check(!s.tap(4),"fifth slot rejected");
        s.lanes.add(new Planner.Lane(repeat(0,100),0,.01));Planner.advance(s,2,false);check(s.tap(4),"completion frees slot for fifth square");

        s=state(0,1,-1);int[]supply=new int[199];Arrays.fill(supply,99,199,1);s.lanes.add(new Planner.Lane(supply,0,.01));
        Planner.Result r=new Planner(2,128,5,1000).search(s);
        check(r.firstAction()==1,"lookahead rejects abundant but insufficient 99-bead collector");
        check(s.board[1]==1&&s.colors[0]==-1,"search leaves observed state unchanged");
        s=state(-1,-2);s.lanes.add(new Planner.Lane(repeat(0,100),0,.01));
        check(new Planner(1,16,4,200).search(s).firstAction()==Planner.WAIT,"hidden squares are never invented");

        s=state(0,1);s.lanes.add(new Planner.Lane(repeat(0,100),0,.01));s.lanes.add(new Planner.Lane(repeat(1,100),0,.01));s.tap(0);s.tap(1);Planner.advance(s,2,false);
        check(s.completed==2,"simultaneous lane completions");

        s=state(0);s.lanes.add(new Planner.Lane(repeat(0,99),0,.01));s.tap(0);check(s.stranded(1),"insufficient remaining supply recognized");
        Planner.advance(s,10,false);check(s.remaining[0]==1&&s.completed==0,"99 cannot fill a 100-bead square");
    }
    static final class ScalarLane{
        ArrayList<Integer>beads=new ArrayList<>();double next,dt;
        ScalarLane(int[]arr,double next,double dt){for(int v:arr)beads.add(v);this.next=next;this.dt=dt;}
    }
    static void randomizedReference(){
        Random random=new Random(220100);
        for(int test=0;test<300;test++){
            Planner.State s=state(0,1,2);int[]remaining={100,100,100,0},colors={0,1,2,-1};
            for(int i=0;i<3;i++)s.tap(i);
            ArrayList<ScalarLane>ref=new ArrayList<>();
            for(int i=0;i<1+test%3;i++){
                int[]a=new int[20+random.nextInt(180)];for(int k=0;k<a.length;k++)a[k]=random.nextInt(4)-1;
                double next=i*.013,dt=.01+random.nextInt(4)*.007;
                s.lanes.add(new Planner.Lane(a,next,dt));ref.add(new ScalarLane(a,next,dt));
            }
            double target=.01+random.nextDouble()*8;int completed=0,collected=0;
            while(true){
                ScalarLane lane=null;for(ScalarLane l:ref)if(!l.beads.isEmpty()&&(lane==null||l.next<lane.next))lane=l;
                if(lane==null||lane.next>target)break;
                int color=lane.beads.remove(0),slot=-1;
                for(int i=0;i<4;i++)if(color>=0&&colors[i]==color){slot=i;break;}
                if(slot>=0){remaining[slot]--;collected++;if(remaining[slot]==0){colors[slot]=-1;completed++;}}
                else lane.beads.add(color);
                lane.next+=lane.dt;
            }
            Planner.advance(s,target,false);
            check(Arrays.equals(s.colors,colors)&&Arrays.equals(s.remaining,remaining),"scalar agreement case "+test);
            check(s.completed==completed&&s.collected==collected,"completion/conservation agreement "+test);
            for(int i=0;i<ref.size();i++){
                ArrayList<Integer>actual=new ArrayList<>();for(Planner.Run run:s.lanes.get(i).runs)for(int n=0;n<run.count;n++)actual.add(run.color);
                check(actual.equals(ref.get(i).beads),"circulation order agreement "+test);
            }
        }
    }
    public static void main(String[]args){fixedCases();randomizedReference();System.out.println("PASS: "+checks+" simulator/search checks");}
}

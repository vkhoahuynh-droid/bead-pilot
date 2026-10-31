package com.beadout.bot;

/** Dependency regression inferred from the supplied Level 23 clip, not a timing fit. */
public final class GameplayRegressionTest {
    private static final int Y=0,G=4,R=12,P=7,C=5,B=13;
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static int[] chunks(int...colors){int[]a=new int[colors.length*10];for(int i=0;i<a.length;i++)a[i]=colors[i/10];return a;}
    static Planner.State start(){
        // Incoming order at pickup. Split the yellow run at the sampling origin.
        int[]ring=new int[100];int at=0;
        for(int i=0;i<5;i++)ring[at++]=Y;
        for(int c:new int[]{G,Y,C,G,R,Y,G,Y,P})for(int i=0;i<10;i++)ring[at++]=c;
        while(at<100)ring[at++]=Y;
        Planner.State s=new Planner.State(new int[]{R,P,C});
        for(int i=0;i<5;i++){
            Planner.Lane lane=new Planner.Lane(ring,.0625,.125);
            // Rounded junction indices: this test verifies dependency, not exact observed timing.
            lane.addFeed(20,chunks(C,R,B,R));lane.addFeed(85,chunks(B,G,P,R));s.lanes.add(lane);
        }return s;
    }
    private static void coarseTimeline(){
        Planner.State s=start();s.board=new int[]{R,P,C,B};for(int i=0;i<4;i++)s.tap(i);
        // Manual percentage readings and measured red-tail top edge from exact video frames.
        // Native source is 388x854 at 30fps. Timing fit is deliberately only within one five-bead row.
        double[][]samples={{3,0,0,104},{3.5,0,15,104},{4,0,35,104},{4.5,0,50,104},
            {5,0,50,110},{5.5,0,50,138},{6,20,50,162},{6.5,40,50,162},
            {7,50,50,162},{7.5,50,50,174},{8,50,50,196},{8.5,50,50,220},{9,50,50,220}};
        for(double[]v:samples){
            Planner.advance(s,v[0],false);
            check(Math.abs((100-s.remaining[0])-v[1])<=5,"red progress within a row at "+v[0]);
            check(Math.abs((100-s.remaining[2])-v[2])<=5,"cyan progress within a row at "+v[0]);
            check(s.remaining[1]==100&&s.remaining[3]==100,"purple and royal blue remain zero in clip");
            check(s.lanes.get(0).feeds.get(0).queue.size()==40,"left feeder remains stationary in clip");
            double expectedRows=(v[3]-104)/5.8;
            double modelRows=40-s.lanes.get(0).feeds.get(1).queue.size();
            check(Math.abs(expectedRows-modelRows)<=1.2,"right feeder movement within a row plus pixel error at "+v[0]);
        }
    }
    public static void main(String[]args){
        Planner.State initial=start();check(initial.beadCount()==900,"five-wide strips: 500 loop + 200 left + 200 right");
        int[]supply=initial.supply(14);check(supply[R]==200&&supply[G]==200&&supply[Y]==200,"red, green and yellow supply");
        check(supply[P]==100&&supply[C]==100&&supply[B]==100,"purple, cyan and blue supply");
        for(int button=0;button<3;button++){
            Planner.State s=start();s.tap(button);Planner.advance(s,100,false);
            check(s.completed==0&&s.remaining[0]==50,"a single initially selectable collector can stall at 50 percent");
        }
        Planner.State s=start();for(int i=0;i<3;i++)s.tap(i);Planner.advance(s,100,false);
        check(s.completed==3,"multiple active colors unlock the feeder chain");
        check(s.beadCount()==600,"three 100-bead completions conserve inventory");
        Planner.Result result=new Planner(14,128,5,2000).search(start(),false);int taps=0;
        for(Planner.Event e:result.actions)if(e.index>=0)taps++;
        check(result.firstAction()>=0&&taps>=3,"lookahead plans several collectors instead of waiting for a single-square probe");
        check(result.predicted.completed>0,"planned dependency chain reaches a completion");
        Planner.State duplicate=new Planner.State(new int[]{R,P});int[]beads=new int[300];java.util.Arrays.fill(beads,0,200,R);java.util.Arrays.fill(beads,200,300,P);duplicate.lanes.add(new Planner.Lane(beads,0,.01));duplicate.colors[0]=R;duplicate.remaining[0]=100;
        check(new Planner(14,32,4,1000).search(duplicate,false).firstAction()!=0,"unknown duplicate-color priority is excluded from immediate actions rather than pausing all play");
        coarseTimeline();
        System.out.println("PASS: "+checks+" recorded-gameplay checks");
    }
}

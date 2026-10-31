package com.beadout.bot;

import java.util.*;

public final class AutomaticModeTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static TextScene.Word word(String text,float x,float y){return new TextScene.Word(text,x,y,.08f,.02f);}
    public static void main(String[]args)throws Exception{
        for(int n=0;n<=100;n+=5)check(TextScene.percent(n+"%")==n,"percentage "+n);
        check(TextScene.percent("5O %")==50,"OCR zero confusion normalized");
        check(TextScene.percent("101%")<0&&TextScene.percent("12%")<0&&TextScene.percent("Next 25")<0,"invalid progress rejected");
        TextScene win=new TextScene(Arrays.asList(word("Congrats",.5f,.1f),word("x2 Coins",.5f,.65f),word("Next",.4f,.77f)));
        check(win.win&&!win.menu&&win.next[1]>.7,"normal Next distinguished from reward label");
        check(!new TextScene(Arrays.asList(word("Next",.5f,.75f))).win,"Next alone does not prove a win");
        TextScene menu=new TextScene(Arrays.asList(word("LEVEL",.48f,.51f),word("Play",.5f,.75f),word("Home",.5f,.95f)));
        check(menu.menu&&!menu.win,"game menu requires Level, Play and Home");
        check(!new TextScene(Arrays.asList(word("LEVEL 1",.5f,.5f),word("Play",.5f,.7f))).menu,"ad-like Play/Level pair without game footer is insufficient");
        List<float[]>slots=Arrays.asList(new float[]{.29f,.58f},new float[]{.43f,.58f},new float[]{.57f,.58f},new float[]{.71f,.58f});
        TextScene progress=new TextScene(Arrays.asList(word("50%",.29f,.60f),word("0%",.43f,.60f),word("50",.56f,.60f),word("%",.59f,.60f),word("0%",.71f,.60f)));
        check(Arrays.equals(progress.progress(slots),new int[]{50,0,50,0}),"progress matched to four live holding slots");

        Tracker tracker=new Tracker(.125,true);Vision.Observation o=new Vision.Observation(new int[]{12,-1,-1,-1},new int[]{4},1_000_000_000L);o.rowUnit=5;o.total=40;o.progress[0]=50;int[]rows=new int[40];Arrays.fill(rows,4);o.lanes.add(rows);o.fixedLoops.set(0);o.feeds.add(new Vision.FeedObservation(0,20,new int[0]));
        Planner.State s=tracker.update(o);check(s.remaining[0]==50,"start on an already half-filled square without user-entered capacity");check(s.beadCount()==200,"five-wide rows have physical bead weight");
        Vision.Observation next=new Vision.Observation(new int[]{12,-1,-1,-1},new int[]{4},1_200_000_000L);next.rowUnit=5;next.total=40;next.progress[0]=55;next.lanes.add(rows.clone());next.fixedLoops.set(0);next.feeds.add(new Vision.FeedObservation(0,20,new int[]{12,12}));
        s=tracker.update(next);check(s.remaining[0]==45,"percentage controls remaining capacity even when visual inventory changes");
        Vision.Observation done=new Vision.Observation(new int[]{-1,-1,-1,-1},new int[]{4},1_400_000_000L);done.rowUnit=5;done.total=40;done.lanes.add(rows.clone());done.fixedLoops.set(0);done.feeds.add(new Vision.FeedObservation(0,20,new int[0]));tracker.update(done);
        check(tracker.completions()==1,"observed square departure completes current collector");
        Planner.State rowState=new Planner.State(new int[]{0});int[]rowBeads=new int[100];Arrays.fill(rowBeads,20,100,1);Planner.Lane lane=new Planner.Lane(rowBeads,0,.1,5);lane.addFeed(50,new int[0]);rowState.lanes.add(lane);rowState.tap(0);Planner.advance(rowState,2,false);
        check(rowState.completed==1&&rowState.beadCount()==400&&lane.size()==100,"20 five-bead rows fill 100 without shrinking loop positions");
        Planner.State hidden=new Planner.State(new int[]{-2});check(!hidden.tap(0),"concealed square cannot be tapped");
        System.out.println("PASS: "+checks+" automatic-mode interpretation checks");
    }
}

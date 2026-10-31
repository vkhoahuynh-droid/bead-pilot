package com.beadout.bot;
import java.util.*;
public final class ObservedPlannerTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Planner.State stuck(int count){Planner.State s=new Planner.State(new int[]{2});s.colors[0]=0;s.colors[1]=1;s.remaining[0]=s.remaining[1]=50;s.inventorySlack=20;int[]beads=new int[count+10];Arrays.fill(beads,0,5,0);Arrays.fill(beads,5,10,1);Arrays.fill(beads,10,beads.length,2);s.lanes.add(new Planner.Lane(beads,0,.01));return s;}
 public static void main(String[]a){Planner p=new Planner(4,32,4,500);
  Planner.State s=stuck(100);check(s.stranded(4),"existing blue/gray shortage reproduced");check(p.search(s,false).actions.isEmpty(),"legacy global supply veto reproduces the freeze");Planner.Result r=p.searchObserved(s,false);check(r.firstAction()==0&&r.predicted.completed>0,"an unrelated fully backed collector may complete despite old shortages");check(s.colors[2]<0,"search does not mutate observed slots");check(r.predicted.colors[1]==1&&r.predicted.remaining[1]==45,"cyan inventory never fills the separate royal-blue collector");
  s=stuck(25);check(p.searchObserved(s,false).actions.isEmpty(),"partial-only trial requires evidence of a stall");check(p.searchObserved(s,true).firstAction()==0,"stalled collectors may add a matching partial third color with a spare slot");s.colors[2]=3;s.remaining[2]=100;check(p.searchObserved(s,true).firstAction()<0,"partial recovery never fills the last slot");
  s=stuck(0);check(p.searchObserved(s,true).firstAction()<0,"no visible matching beads means no speculative recovery tap");s=stuck(100);s.board[0]=-2;check(p.searchObserved(s,true).firstAction()<0,"concealed color is not guessed during recovery");s=stuck(100);s.board[0]=0;check(p.searchObserved(s,true).firstAction()<0,"duplicate collectors remain excluded");
  s=stuck(100);s.board=new int[]{-1,-2};check(p.diagnosis(s).contains("No known reachable"),"reports no reachable square explicitly");s.board=new int[]{0};check(p.diagnosis(s).contains("already collecting"),"reports duplicate-only choices explicitly");s.board=new int[]{3};check(p.diagnosis(s).contains("no confidently matched"),"reports unmatched color inventory explicitly");
  for(int n:new int[]{15,30,60}){s=stuck(100);s.board=new int[n];Arrays.fill(s.board,-2);s.board[n-1]=2;check(p.searchObserved(s,false).firstAction()==n-1,"search considers real choice after many hidden/blocked squares "+n);}
  for(int n:new int[]{15,30,60}){s=GameplayRegressionTest.start();int[]board=new int[n];Arrays.fill(board,-2);System.arraycopy(s.board,0,board,0,s.board.length);s.board=board;s.unlockValues=new int[n];Planner.Result chain=new Planner(14,128,7,2000).searchObserved(s,false);int taps=0;for(Planner.Event e:chain.actions)if(e.index>=0)taps++;check(taps>=3&&chain.predicted.completed>0,"multi-color feeder dependency survives larger hidden boards "+n);}
  System.out.println("PASS: "+checks+" observed-planner recovery checks");
 }
}

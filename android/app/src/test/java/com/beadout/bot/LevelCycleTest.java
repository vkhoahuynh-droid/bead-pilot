package com.beadout.bot;

public final class LevelCycleTest {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static LevelCycle.Action step(LevelCycle c,long ms,boolean win,boolean game,boolean board){return c.update(ms*1_000_000L,win,game,board);}
 static LevelCycle.Action menu(LevelCycle c,long ms){return c.update(ms*1_000_000L,false,true,false,true);}
 static LevelCycle afterNext(){
  LevelCycle c=new LevelCycle();check(step(c,0,true,true,false)==LevelCycle.Action.NONE,"one win frame does not navigate");step(c,500,true,true,false);
  check(step(c,1000,true,true,false)==LevelCycle.Action.NEXT,"Next precedes Home after stable win");
  check(step(c,1500,true,true,false)==LevelCycle.Action.NONE,"unacknowledged Next is rate limited");
  check(step(c,2000,true,true,false)==LevelCycle.Action.NEXT,"failed/stale tap can be retried on confirmed Next");
  c.nextSent(2100_000_000L);check(c.stage()==LevelCycle.Stage.POST_NEXT_WAIT,"only actual successful tap starts two-second timer");
  check(step(c,4099,false,true,true)==LevelCycle.Action.NONE,"even a fast-loading board gets full two seconds before Home");
  check(step(c,4100,false,false,false)==LevelCycle.Action.HOME,"Home exactly two seconds after acknowledged Next");
  check(step(c,4500,false,false,false)==LevelCycle.Action.NONE,"Home waits for acknowledgment instead of assuming delivery");
  c.homeSent(4600_000_000L);
  check(step(c,7599,false,false,false)==LevelCycle.Action.NONE,"Home wait starts at actual key completion");
  check(step(c,7600,false,false,false)==LevelCycle.Action.LAUNCH,"reopen after full three-second Home wait");
  c.launchSent(7800_000_000L);return c;
 }
 static void normalAndMenu(){
  LevelCycle c=afterNext();step(c,8000,false,true,true);step(c,8300,false,true,true);
  check(step(c,8600,false,true,true)==LevelCycle.Action.RESET,"new board resets tracking and returns to continuous play");check(c.stage()==LevelCycle.Stage.MONITOR,"cycle returns to monitor after one level");
  step(c,10000,true,true,false);step(c,10500,true,true,false);check(step(c,11000,true,true,false)==LevelCycle.Action.NEXT,"second completed level continues without pressing Start again");
  c=afterNext();menu(c,8000);menu(c,8100);menu(c,8400);check(menu(c,8600)==LevelCycle.Action.PLAY_MENU,"Bead Out home menu triggers Play");
  check(menu(c,9000)==LevelCycle.Action.NONE,"Play is rate limited until delivery acknowledged");c.menuSent(9100_000_000L);
  step(c,9300,false,true,true);step(c,9600,false,true,true);check(step(c,9900,false,true,true)==LevelCycle.Action.RESET,"menu Play resumes the new board automatically");
  c=afterNext();step(c,8000,true,true,false);step(c,8100,true,true,false);step(c,8600,true,true,false);check(step(c,9200,true,true,false)==LevelCycle.Action.NEXT,"a returned Congrats screen can confirm Next again after reopen");
 }
 static void unknownRecovery(){
  LevelCycle c=afterNext();check(step(c,11799,false,false,false)==LevelCycle.Action.NONE,"allow load time after actual launch completion");
  check(step(c,11800,false,false,false)==LevelCycle.Action.HOME,"unknown/ad return uses system Home only");c.homeSent(11800_000_000L);
  check(step(c,14800,false,false,false)==LevelCycle.Action.LAUNCH,"ad recovery keeps full Home delay");c.launchSent(15000_000_000L);
  check(step(c,19000,false,false,false)==LevelCycle.Action.HOME,"third Home recovery is available");c.homeSent(19000_000_000L);step(c,22000,false,false,false);c.launchSent(22200_000_000L);
  check(step(c,26200,false,false,false)==LevelCycle.Action.NONE&&c.retryWaiting(),"recovery exhaustion becomes cooldown instead of terminating automatic play");
  check(step(c,36199,false,false,false)==LevelCycle.Action.NONE,"cooldown prevents rapid endless navigation");check(step(c,36200,false,false,false)==LevelCycle.Action.HOME,"recovery retries after cooldown without another Start");
  c.retryAfterError(40000_000_000L);step(c,40100,false,true,true);step(c,40400,false,true,true);check(step(c,40700,false,true,true)==LevelCycle.Action.RESET,"a readable board can interrupt cooldown and resume play");
  c=afterNext();boolean noUnsafe=true;int homes=0,reopens=0;
  for(long ms=8000;ms<240000;ms+=250){LevelCycle.Action action=step(c,ms,false,false,false);noUnsafe&=action!=LevelCycle.Action.FAIL&&action!=LevelCycle.Action.NEXT&&action!=LevelCycle.Action.PLAY_MENU;if(action==LevelCycle.Action.HOME){homes++;c.homeSent(ms*1_000_000L);}if(action==LevelCycle.Action.LAUNCH){reopens++;c.launchSent(ms*1_000_000L);}}
  check(noUnsafe,"four minutes of unknown ads never receive button taps or terminate automatic mode");check(homes>=6,"continuous recovery survives multiple retry bursts");check(reopens>=6,"continuous recovery repeatedly returns to the game");

 }
 static void transientAndStartupMenu(){
  LevelCycle c=new LevelCycle();step(c,0,true,true,false);step(c,100,false,true,false);check(c.stage()==LevelCycle.Stage.MONITOR,"transient win does not advance");
  check(step(c,2000,true,false,false)==LevelCycle.Action.NONE,"another app's win text cannot navigate");
  menu(c,3000);menu(c,3100);menu(c,3400);check(menu(c,3600)==LevelCycle.Action.PLAY_MENU,"starting continuous play from game home can press Play");c.menuSent(3600_000_000L);check(menu(c,6600)==LevelCycle.Action.HOME,"stalled menu Play reopens rather than spamming Play");
  c.retryAfterError(7000_000_000L);check(c.retryWaiting(),"transient navigation exception preserves active recovery");
 }
 public static void main(String[]args){normalAndMenu();unknownRecovery();transientAndStartupMenu();System.out.println("PASS: "+checks+" continuous level-cycle checks");}
}

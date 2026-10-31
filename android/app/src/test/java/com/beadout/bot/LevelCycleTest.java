package com.beadout.bot;

public final class LevelCycleTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static LevelCycle.Action step(LevelCycle c,long ms,boolean win,boolean foreground,boolean ready){return c.update(ms*1_000_000L,win,foreground,ready);}
    static LevelCycle awaitingNext(){
        LevelCycle c=new LevelCycle();step(c,0,true,true,false);step(c,500,true,true,false);
        check(step(c,1000,true,true,false)==LevelCycle.Action.HOME,"stable win presses Home once");
        c.homeSent(1100*1_000_000L);
        check(step(c,4099,false,false,false)==LevelCycle.Action.NONE,"wait full three seconds after actual Home key release");
        check(step(c,4100,false,false,false)==LevelCycle.Action.LAUNCH,"reopen at three seconds");
        check(step(c,4200,false,false,false)==LevelCycle.Action.NONE,"do not relaunch repeatedly");
        step(c,4500,true,true,false);step(c,4501,true,true,false);step(c,4800,true,true,false);
        check(step(c,5001,true,true,false)==LevelCycle.Action.NEXT,"reconfirm win/Next after reopening");return c;
    }
    static LevelCycle.Action menu(LevelCycle c,long ms){return c.update(ms*1_000_000L,false,true,false,true);}
    static void recoveryCases(){
        LevelCycle c=awaitingNext();
        check(step(c,7000,false,false,false)==LevelCycle.Action.NONE,"allow two seconds for post-Next transition");
        check(step(c,7001,false,false,false)==LevelCycle.Action.HOME,"post-Next ad is backgrounded, not tapped");
        c.homeSent(7100*1_000_000L);
        check(step(c,10099,false,false,false)==LevelCycle.Action.NONE,"ad recovery also waits full three seconds");
        check(step(c,10100,false,false,false)==LevelCycle.Action.LAUNCH,"reopen after post-Next ad");
        menu(c,10400);menu(c,10500);menu(c,10800);
        check(menu(c,11000)==LevelCycle.Action.PLAY_MENU,"return to Bead Out Home taps recognized Play");
        check(menu(c,11200)==LevelCycle.Action.NONE,"do not repeat Play while menu is transitioning");
        step(c,11400,false,true,true);step(c,11700,false,true,true);
        check(step(c,12000,false,true,true)==LevelCycle.Action.RESET,"menu Play returns to automatic prediction");

        c=new LevelCycle();menu(c,0);menu(c,100);menu(c,400);
        check(menu(c,600)==LevelCycle.Action.PLAY_MENU,"recognized menu can recover without seeing another win");
        check(menu(c,700)==LevelCycle.Action.NONE,"menu Play is issued once before recovery");
        check(menu(c,3600)==LevelCycle.Action.HOME,"stalled Play uses Home instead of repeated Play taps");
        c.homeSent(3600*1_000_000L);step(c,6600,false,false,false);
        menu(c,6700);menu(c,6800);menu(c,7100);
        check(menu(c,7300)==LevelCycle.Action.PLAY_MENU,"allow one Play retry only after reopening");
        menu(c,10300);c.homeSent(10300*1_000_000L);step(c,13300,false,false,false);
        menu(c,13400);menu(c,13500);menu(c,13800);
        check(menu(c,14000)==LevelCycle.Action.FAIL,"persistent menu is bounded to two Play attempts");

        c=awaitingNext();step(c,7001,false,false,false);c.homeSent(7001*1_000_000L);step(c,10001,false,false,false);
        check(step(c,14001,false,false,false)==LevelCycle.Action.HOME,"second recovery for persistent ad");
        c.homeSent(14001*1_000_000L);step(c,17001,false,false,false);
        check(step(c,21001,false,false,false)==LevelCycle.Action.FAIL,"home/ad recovery attempts are bounded");

        c=awaitingNext();step(c,7001,false,false,false);c.homeSent(7001*1_000_000L);step(c,10001,false,false,false);
        check(step(c,11000,true,true,false)==LevelCycle.Action.NONE,"old win after ad does not repeat Next");
        check(step(c,23000,true,true,false)==LevelCycle.Action.FAIL,"unchanged old win eventually stops");
    }
    public static void main(String[]args){
        LevelCycle c=awaitingNext();
        check(step(c,5100,true,true,false)==LevelCycle.Action.NONE,"Next is never spammed");
        step(c,5300,false,true,true);step(c,5600,false,true,true);
        check(step(c,5800,false,true,true)==LevelCycle.Action.RESET,"new board confirmed before resetting planner");
        check(c.stage()==LevelCycle.Stage.MONITOR,"automatic play can resume on next level");
        c=new LevelCycle();step(c,0,true,true,false);step(c,100,false,true,false);
        check(c.stage()==LevelCycle.Stage.MONITOR,"one transient win-like frame does not navigate");
        check(step(c,2000,true,false,false)==LevelCycle.Action.NONE,"win marker in another app cannot press Home");
        c=awaitingNext();
        check(step(c,21000,true,true,false)==LevelCycle.Action.FAIL,"unchanged win screen times out without repeated Next");
        check(step(c,22000,true,true,false)==LevelCycle.Action.NONE,"failed cycle sends no further actions");
        c=new LevelCycle();step(c,0,true,true,false);step(c,500,true,true,false);step(c,1000,true,true,false);c.homeSent(1000*1_000_000L);
        step(c,4000,false,false,false);
        check(step(c,17000,false,true,false)==LevelCycle.Action.FAIL,"ad or unreadable return screen is not clicked");
        recoveryCases();
        System.out.println("PASS: "+checks+" automatic level-cycle checks");
    }
}

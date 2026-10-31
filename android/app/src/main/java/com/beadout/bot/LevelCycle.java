package com.beadout.bot;

/** Pure navigation state machine. It never taps unknown/ad pixels. */
public final class LevelCycle {
    public enum Stage { MONITOR, CONFIRM_WIN, HOME_WAIT, RETURN_WAIT, CONFIRM_NEXT, NEW_LEVEL_WAIT, CONFIRM_MENU, MENU_LEVEL_WAIT, FAILED }
    public enum Action { NONE, HOME, LAUNCH, NEXT, PLAY_MENU, RESET, FAIL }
    private Stage stage=Stage.MONITOR;
    private long entered,stableSince,started=-1;
    private int stableFrames,homeVisits,menuAttempts;
    private boolean nextTapped;
    public Stage stage(){return stage;}
    public boolean transitioning(){return stage!=Stage.MONITOR;}
    public boolean homeWaiting(){return stage==Stage.HOME_WAIT;}
    public void homeSent(long now){if(stage==Stage.HOME_WAIT)entered=now;}
    private void set(Stage next,long now){stage=next;entered=now;stableSince=0;stableFrames=0;}
    private void begin(long now){started=now;homeVisits=0;menuAttempts=0;nextTapped=false;}
    private boolean stable(long now,long duration){if(stableFrames++==0)stableSince=now;return stableFrames>=3&&now-stableSince>=duration;}
    private Action fail(long now){set(Stage.FAILED,now);return Action.FAIL;}
    private Action home(long now){if(homeVisits>=3)return fail(now);homeVisits++;set(Stage.HOME_WAIT,now);return Action.HOME;}
    private Action ready(long now,boolean valid){
        if(valid){if(stable(now,500_000_000L)){set(Stage.MONITOR,now);started=-1;return Action.RESET;}}
        else{stableSince=0;stableFrames=0;}return Action.NONE;
    }
    public Action update(long now,boolean win,boolean gameForeground,boolean levelReady){return update(now,win,gameForeground,levelReady,false);}
    public Action update(long now,boolean win,boolean gameForeground,boolean levelReady,boolean menu){
        if(stage!=Stage.MONITOR&&stage!=Stage.FAILED&&started>=0&&now-started>60_000_000_000L)return fail(now);
        switch(stage){
            case MONITOR:
                if(gameForeground&&win){begin(now);set(Stage.CONFIRM_WIN,now);stable(now,1_000_000_000L);}
                else if(gameForeground&&menu){begin(now);set(Stage.CONFIRM_MENU,now);}
                return Action.NONE;
            case CONFIRM_WIN:
                if(!win||!gameForeground){set(Stage.MONITOR,now);started=-1;return Action.NONE;}
                return stable(now,1_000_000_000L)?home(now):Action.NONE;
            case HOME_WAIT:
                if(now-entered>=3_000_000_000L){set(Stage.RETURN_WAIT,now);return Action.LAUNCH;}
                return Action.NONE;
            case RETURN_WAIT:
                if(gameForeground&&menu){set(Stage.CONFIRM_MENU,now);return Action.NONE;}
                if(gameForeground&&win&&!nextTapped){set(Stage.CONFIRM_NEXT,now);return Action.NONE;}
                Action result=ready(now,gameForeground&&!win&&levelReady);
                if(result!=Action.NONE)return result;
                if(now-entered>12_000_000_000L)return fail(now);
                if(!win&&!menu&&!levelReady&&now-entered>=4_000_000_000L)return home(now);
                return Action.NONE;
            case CONFIRM_NEXT:
                if(!gameForeground||!win){set(Stage.RETURN_WAIT,now);return Action.NONE;}
                if(stable(now,500_000_000L)){nextTapped=true;set(Stage.NEW_LEVEL_WAIT,now);return Action.NEXT;}
                return Action.NONE;
            case NEW_LEVEL_WAIT:
            case MENU_LEVEL_WAIT:
                if(gameForeground&&menu&&stage==Stage.NEW_LEVEL_WAIT){set(Stage.CONFIRM_MENU,now);return Action.NONE;}
                Action complete=ready(now,gameForeground&&!win&&levelReady);
                if(complete!=Action.NONE)return complete;
                if(now-entered>15_000_000_000L)return fail(now);
                if(stage==Stage.MENU_LEVEL_WAIT&&menu&&now-entered>=3_000_000_000L)return home(now);
                // Unknown post-Next/Play screens may be ads. Only press system Home; never click the ad.
                if(!win&&!menu&&!levelReady&&now-entered>=2_000_000_000L)return home(now);
                return Action.NONE;
            case CONFIRM_MENU:
                if(!gameForeground||!menu){set(Stage.RETURN_WAIT,now);return Action.NONE;}
                if(stable(now,500_000_000L)){
                    if(menuAttempts>=2)return fail(now);
                    menuAttempts++;set(Stage.MENU_LEVEL_WAIT,now);return Action.PLAY_MENU;
                }
                return Action.NONE;
            case FAILED:return Action.NONE;
            default:throw new AssertionError(stage);
        }
    }
}

package com.beadout.bot;

/** Continuous, acknowledged navigation. No unknown/ad pixels are tapped. */
public final class LevelCycle {
    public enum Stage { MONITOR, CONFIRM_WIN, NEXT_PENDING, POST_NEXT_WAIT, HOME_PENDING, HOME_WAIT, RETURN_WAIT, CONFIRM_MENU, MENU_PENDING, MENU_LEVEL_WAIT, RETRY_WAIT }
    public enum Action { NONE, HOME, LAUNCH, NEXT, PLAY_MENU, RESET, FAIL }
    private Stage stage=Stage.MONITOR;
    private long entered,stableSince,started=-1,lastAttempt=-1;
    private int stableFrames,homeVisits,menuAttempts;
    public Stage stage(){return stage;}
    public boolean transitioning(){return stage!=Stage.MONITOR;}
    public boolean homeWaiting(){return stage==Stage.HOME_WAIT;}
    public boolean timerWaiting(){return stage==Stage.HOME_WAIT||stage==Stage.POST_NEXT_WAIT;}
    public boolean retryWaiting(){return stage==Stage.RETRY_WAIT;}
    private void set(Stage next,long now){stage=next;entered=now;stableSince=0;stableFrames=0;lastAttempt=-1;}
    private void begin(long now){started=now;homeVisits=0;menuAttempts=0;}
    public void homeSent(long now){if(stage==Stage.HOME_PENDING)set(Stage.HOME_WAIT,now);}
    public void nextSent(long now){if(stage==Stage.NEXT_PENDING)set(Stage.POST_NEXT_WAIT,now);}
    public void menuSent(long now){if(stage==Stage.MENU_PENDING){menuAttempts++;set(Stage.MENU_LEVEL_WAIT,now);}}
    public void launchSent(long now){if(stage==Stage.RETURN_WAIT)entered=now;}
    public void retryAfterError(long now){set(Stage.RETRY_WAIT,now);}
    private boolean stable(long now,long duration){if(stableFrames++==0)stableSince=now;return stableFrames>=3&&now-stableSince>=duration;}
    private Action attempt(long now,Action action){if(lastAttempt>=0&&now-lastAttempt<1_000_000_000L)return Action.NONE;lastAttempt=now;return action;}
    private Action home(long now){if(homeVisits>=3){set(Stage.RETRY_WAIT,now);return Action.NONE;}homeVisits++;set(Stage.HOME_PENDING,now);return attempt(now,Action.HOME);}
    private Action ready(long now,boolean valid){
        if(valid){if(stable(now,500_000_000L)){set(Stage.MONITOR,now);started=-1;return Action.RESET;}}
        else{stableSince=0;stableFrames=0;}return Action.NONE;
    }
    public Action update(long now,boolean win,boolean gameForeground,boolean levelReady){return update(now,win,gameForeground,levelReady,false);}
    public Action update(long now,boolean win,boolean gameForeground,boolean levelReady,boolean menu){
        // Slow transitions cool down and retry; they never end automatic play.
        if(stage!=Stage.MONITOR&&stage!=Stage.RETRY_WAIT&&!timerWaiting()&&started>=0&&now-started>60_000_000_000L){set(Stage.RETRY_WAIT,now);return Action.NONE;}
        switch(stage){
            case MONITOR:
                if(gameForeground&&win){begin(now);set(Stage.CONFIRM_WIN,now);stable(now,1_000_000_000L);}
                else if(gameForeground&&menu){begin(now);set(Stage.CONFIRM_MENU,now);}
                return Action.NONE;
            case CONFIRM_WIN:
                if(!win||!gameForeground){set(Stage.MONITOR,now);started=-1;return Action.NONE;}
                if(stable(now,1_000_000_000L)){set(Stage.NEXT_PENDING,now);return attempt(now,Action.NEXT);}return Action.NONE;
            case NEXT_PENDING:
                if(!win||!gameForeground){set(Stage.RETURN_WAIT,now);return Action.NONE;}
                return attempt(now,Action.NEXT); // Only until the actual tap is acknowledged.
            case POST_NEXT_WAIT:
                return now-entered>=2_000_000_000L?home(now):Action.NONE;
            case HOME_PENDING:return attempt(now,Action.HOME);
            case HOME_WAIT:
                if(now-entered>=3_000_000_000L){set(Stage.RETURN_WAIT,now);return Action.LAUNCH;}return Action.NONE;
            case RETURN_WAIT:
                if(gameForeground&&menu){set(Stage.CONFIRM_MENU,now);return Action.NONE;}
                if(gameForeground&&win){set(Stage.CONFIRM_WIN,now);return Action.NONE;}
                Action returned=ready(now,gameForeground&&levelReady&&!win&&!menu);if(returned!=Action.NONE)return returned;
                if(now-entered>=4_000_000_000L)return home(now);return Action.NONE;
            case CONFIRM_MENU:
                if(!gameForeground||!menu){set(Stage.RETURN_WAIT,now);return Action.NONE;}
                if(stable(now,500_000_000L)){
                    if(menuAttempts>=2){set(Stage.RETRY_WAIT,now);return Action.NONE;}
                    set(Stage.MENU_PENDING,now);return attempt(now,Action.PLAY_MENU);
                }return Action.NONE;
            case MENU_PENDING:
                if(!gameForeground||!menu){set(Stage.RETURN_WAIT,now);return Action.NONE;}
                return attempt(now,Action.PLAY_MENU);
            case MENU_LEVEL_WAIT:
                Action loaded=ready(now,gameForeground&&levelReady&&!win&&!menu);if(loaded!=Action.NONE)return loaded;
                if(gameForeground&&win){set(Stage.CONFIRM_WIN,now);return Action.NONE;}
                if(now-entered>=(menu?3_000_000_000L:2_000_000_000L))return home(now);return Action.NONE;
            case RETRY_WAIT:
                Action recovered=ready(now,gameForeground&&levelReady&&!win&&!menu);if(recovered!=Action.NONE)return recovered;
                if(now-entered>=10_000_000_000L){begin(now);
                    if(gameForeground&&menu){set(Stage.CONFIRM_MENU,now);return Action.NONE;}
                    if(gameForeground&&win){set(Stage.CONFIRM_WIN,now);return Action.NONE;}
                    return home(now);
                }return Action.NONE;
            default:throw new AssertionError(stage);
        }
    }
}

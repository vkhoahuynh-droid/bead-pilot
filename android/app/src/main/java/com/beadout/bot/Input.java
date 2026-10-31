package com.beadout.bot;

import android.os.SystemClock;
import android.view.*;
import java.lang.reflect.*;

/** Persistent Binder input, avoiding a new 'input' process for every tap. */
public final class Input {
    private final Object manager;
    private final Method inject;
    public Input()throws Exception{
        Class<?> type;
        try{type=Class.forName("android.hardware.input.InputManagerGlobal");}
        catch(ClassNotFoundException e){type=Class.forName("android.hardware.input.InputManager");}
        manager=type.getMethod("getInstance").invoke(null);
        inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);
    }
    public void tap(float x,float y)throws Exception{
        long down=SystemClock.uptimeMillis();boolean downSent=false;
        try{
            send(down,down,MotionEvent.ACTION_DOWN,x,y);downSent=true;
            Thread.sleep(35);
        }finally{if(downSent)send(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y);}
    }
    public void home()throws Exception{
        long down=SystemClock.uptimeMillis();boolean sent=false;
        try{sendKey(down,down,KeyEvent.ACTION_DOWN);sent=true;Thread.sleep(35);}
        finally{if(sent)sendKey(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP);}
    }
    private void sendKey(long down,long now,int action)throws Exception{
        KeyEvent event=new KeyEvent(down,now,action,KeyEvent.KEYCODE_HOME,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD);
        if(!Boolean.TRUE.equals(inject.invoke(manager,event,2)))throw new IllegalStateException("Home injection rejected");
    }
    private void send(long down,long now,int action,float x,float y)throws Exception{
        MotionEvent event=MotionEvent.obtain(down,now,action,x,y,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{if(!Boolean.TRUE.equals(inject.invoke(manager,event,2)))throw new IllegalStateException("Input injection rejected");}
        finally{event.recycle();}
    }
}

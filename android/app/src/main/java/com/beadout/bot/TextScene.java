package com.beadout.bot;

import java.util.*;
import java.util.regex.*;

/** Semantic interpretation of OCR words. Geometry is normalized to the captured screen. */
public final class TextScene {
    public static final class Word {
        public final String text;public final float x,y,w,h;
        public Word(String text,float x,float y,float w,float h){this.text=text;this.x=x;this.y=y;this.w=w;this.h=h;}
    }
    public final List<Word>words;
    public final float[]next,play;
    public final boolean win,menu;
    public TextScene(List<Word>words){
        this.words=words;boolean congrats=false,level=false,home=false;Word next=null,play=null;
        for(Word word:words){String s=word.text.toLowerCase(Locale.ROOT).trim();
            if(word.y<.35&&(s.contains("congrats")||s.contains("congratulations")||s.matches(".*level\\s+complete[d]?.*")))congrats=true;
            if(word.y>.5&&word.y<.9&&s.matches("next(?:\\b.*|[0-9]+)"))next=word;
            if(word.y>.5&&word.y<.9&&s.equals("play"))play=word;
            if(word.y>.25&&word.y<.78&&word.x>.2&&word.x<.8&&s.matches("level(?:\\b.*|[0-9]+)"))level=true;
            if(word.y>.85&&s.equals("home"))home=true;
        }
        win=congrats&&next!=null;menu=play!=null&&level&&home;this.next=next==null?null:new float[]{next.x,next.y};this.play=play==null?null:new float[]{play.x,play.y};
    }
    public static int percent(String text){
        String s=text.trim().replace('O','0').replace('o','0').replace('S','5').replace('I','1').replace('|','1').replace(" ","");
        if(!s.matches("(?:100|[0-9]{1,2})%"))return -1;int n=Integer.parseInt(s.substring(0,s.length()-1));return n%5==0?n:-1;
    }
    public int[]progress(List<float[]>slots){
        int[]out={-1,-1,-1,-1};double[]best={99,99,99,99};
        ArrayList<Word>candidates=new ArrayList<>(words);
        for(Word a:words)if(a.text.trim().matches("[0-9OSoI|]{1,3}"))for(Word b:words)if(b.text.trim().equals("%")&&Math.abs(a.y-b.y)<Math.max(a.h,b.h)&&b.x>a.x&&b.x-a.x<.06)candidates.add(new Word(a.text+"%",(a.x+b.x)/2,(a.y+b.y)/2,a.w+b.w,Math.max(a.h,b.h)));
        for(Word word:candidates){int n=percent(word.text);if(n<0)continue;
            for(int i=0;i<Math.min(4,slots.size());i++){float[]p=slots.get(i);double dx=Math.abs(word.x-p[0]),dy=word.y-p[1];if(dx>.06||dy<-.005||dy>.085)continue;double score=dx+Math.abs(dy-.018)*.4;if(score<best[i]){best[i]=score;out[i]=n;}}
        }return out;
    }
}

package com.beadout.bot;
import java.util.*;
public final class SceneEvidenceTest{
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static TextScene.Word word(String text,float y){return new TextScene.Word(text,.5f,y,.1f,.03f);}
 public static void main(String[]a){int[]same=new int[96],other=new int[96];Arrays.fill(other,7);SceneEvidence e=new SceneEvidence();
  List<TextScene.Word>labels=Arrays.asList(word("LEVEL 25",.5f),word("Home",.95f)),play=Arrays.asList(word("Play",.75f));
  check(!e.update(labels,1_000_000_000L,0,same).menu,"incomplete menu alone does not navigate");check(e.update(play,1_500_000_000L,0,same).menu,"partial OCR on the same visible game menu can combine Level/Home with Play");
  check(!new TextScene(play).menu,"raw percentage/scene result is not rewritten by accumulated navigation evidence");check(!e.update(play,1_800_000_000L,0,other).menu,"an ad/different screen cannot inherit game menu evidence");
  e.update(labels,2_000_000_000L,0,same);check(!e.update(play,2_200_000_000L,1,same).menu,"model changes cannot inherit old menu words");
  e.update(labels,3_000_000_000L,1,same);check(!e.update(play,6_000_000_000L,1,same).menu,"old screen evidence expires");
  e.update(labels,7_000_000_000L,1,same);int[]animated=same.clone();Arrays.fill(animated,0,8,1);check(e.update(play,7_500_000_000L,1,animated).menu,"small background animation does not discard stable menu labels");
  TextScene candidate=new TextScene(Arrays.asList(word("LEVEL 25",.5f),word("Play",.75f)));int w=582,h=1280;int[]pixels=new int[w*h];Arrays.fill(pixels,0xffdddddd);
  check(!candidate.gameMenu(new Capture.Frame(w,h,pixels)),"Play and LEVEL without a game footer still cannot identify an ad as the menu");
  for(int y=(int)(h*.94);y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=0xff284cac;
  check(!candidate.gameMenu(new Capture.Frame(w,h,pixels)),"blue footer alone is not enough to recover a missed Home word");
  for(int y=(int)(h*.865);y<h*.935;y++)for(int x=(int)(w*.44);x<w*.57;x++)pixels[y*w+x]=0xffdbba76;
  check(candidate.gameMenu(new Capture.Frame(w,h,pixels)),"game footer and house badge support menu recognition when OCR misses Home");
  TextScene boardHeader=new TextScene(Arrays.asList(word("LEVEL 25",.1f),word("Play",.75f)));check(!boardHeader.gameMenu(new Capture.Frame(w,h,pixels)),"gameplay header is not the central menu LEVEL label");
  System.out.println("PASS: "+checks+" scene-evidence checks");
 }
}

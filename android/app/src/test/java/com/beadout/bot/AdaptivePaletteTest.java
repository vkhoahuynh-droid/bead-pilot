package com.beadout.bot;
import java.util.*;
public final class AdaptivePaletteTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static int hue(int degrees){double h=degrees/60.0,x=1-Math.abs(h%2-1);int r=0,g=0,b=0;switch((int)h){case 0:r=255;g=(int)(255*x);break;case 1:r=(int)(255*x);g=255;break;case 2:g=255;b=(int)(255*x);break;case 3:g=(int)(255*x);b=255;break;case 4:r=(int)(255*x);b=255;break;default:r=255;b=(int)(255*x);}return 0xff000000|r<<16|g<<8|b;}
 static List<Integer>votes(int...colors){List<Integer>v=new ArrayList<>();for(int c:colors)for(int i=0;i<6;i++)v.add(c);return v;}
 public static void main(String[]args){
  for(int count:new int[]{2,5,18}){AdaptivePalette p=new AdaptivePalette();int[]c=new int[count];for(int i=0;i<count;i++)c[i]=hue(i*360/count);p.observe(votes(c));check(p.size()==count,"learn actual number of colors "+count);Set<Integer>seen=new HashSet<>();for(int v:c){int id=p.match(v);check(id>=0&&seen.add(id),"distinct observed hues get distinct IDs");}}
  AdaptivePalette p=new AdaptivePalette();p.observe(votes(0xff788b9c,0xffece7e8,0xffa35704,0xffff5505));check(p.size()==4,"gray, white, brown and orange are distinct observed colors");int gray=p.match(0xff788b9c);check(p.match(0xff637b90)==gray,"gray shade matches same bead/cup group");int red=p.size();p.observe(votes(0xffff0030));check(p.size()==red+1&&p.match(0xff788b9c)==gray,"newly revealed color appends without changing prior IDs");p.observe(votes(0xffff0030));check(p.size()==red+1,"same color does not keep adding groups");
  check(p.match(0xff00ffaa)<0,"unseen color is unknown before evidence");p.observe(Collections.singletonList(0xff00ffaa));check(p.match(0xff00ffaa)<0,"one outlier cannot create a new color");
  check(!AdaptivePalette.floor(0xff788b9c,0xffe5d8bf),"gray is a foreground color on beige theme");check(AdaptivePalette.floor(0xff8a7d65,0xffe5d8bf),"beige rail floor is excluded");check(AdaptivePalette.darkUnknown(0xff505050)&&!AdaptivePalette.darkUnknown(0xff536678),"unknown black tile distinguished from slate cup");
  Planner.State s=new Planner.State(new int[]{17});int[]lane=new int[100];Arrays.fill(lane,17);s.lanes.add(new Planner.Lane(lane,0,.1));check(new Planner(18,16,2,100).search(s).firstAction()==0,"planner handles learned IDs beyond legacy palette");
  p.clear();p.observe(votes(0xff788b9c));check(p.size()==1&&p.match(0xff788b9c)==0,"a new level can relearn fewer colors without old groups");
  System.out.println("PASS: "+checks+" adaptive palette checks");
 }
}

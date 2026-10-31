package com.beadout.bot;
import java.util.*;

/** Small OCR-word cache for the same visible screen; never combines different scenes/epochs. */
public final class SceneEvidence {
    private static final class Read {long time;List<TextScene.Word>words;Read(long t,List<TextScene.Word>w){time=t;words=new ArrayList<>(w);}}
    private final ArrayDeque<Read>reads=new ArrayDeque<>();
    private int[]signature;private long epoch=Long.MIN_VALUE;
    public TextScene update(List<TextScene.Word>words,long time,long modelEpoch,int[]pixels){
        boolean same=epoch==modelEpoch&&signature!=null&&pixels!=null&&signature.length==pixels.length&&pixels.length>0;
        if(same){int matches=0;for(int i=0;i<pixels.length;i++)if(signature[i]==pixels[i])matches++;same=matches>=pixels.length*.8;}
        if(!same)reads.clear();epoch=modelEpoch;signature=pixels==null?null:pixels.clone();
        while(!reads.isEmpty()&&time-reads.peekFirst().time>2_500_000_000L)reads.removeFirst();reads.addLast(new Read(time,words));while(reads.size()>4)reads.removeFirst();
        ArrayList<TextScene.Word>merged=new ArrayList<>();Iterator<Read>it=reads.descendingIterator();while(it.hasNext())merged.addAll(it.next().words);
        return new TextScene(merged);
    }
}

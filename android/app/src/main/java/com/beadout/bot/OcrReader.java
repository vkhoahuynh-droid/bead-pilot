package com.beadout.bot;

import android.graphics.*;
import android.util.Base64;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bundled, offline OCR in the normal app process. Root capture sends only RAM buffers. */
public final class OcrReader implements AutoCloseable {
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final AtomicBoolean busy=new AtomicBoolean();
    public void read(JSONObject request,Consumer<JSONObject>reply){
        if(!busy.compareAndSet(false,true))return;
        Bitmap image=null;
        try{
            byte[]bytes=Base64.decode(request.getString("png"),Base64.NO_WRAP);image=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            if(image==null)throw new IllegalArgumentException("Invalid OCR image");
            int sourceWidth=request.getInt("screenWidth"),sourceHeight=request.getInt("screenHeight"),left=request.getInt("left"),top=request.getInt("top");
            float factor=request.optBoolean("full",true)?1.5f:3f;
            Bitmap input=Bitmap.createScaledBitmap(image,Math.round(image.getWidth()*factor),Math.round(image.getHeight()*factor),true);if(input!=image)image.recycle();
            image=input;final Bitmap owned=input;final float scale=factor;
            recognizer.process(InputImage.fromBitmap(owned,0)).addOnCompleteListener(task->{
                JSONObject response=new JSONObject();try{
                    response.put("cmd","ocr").put("id",request.getLong("id"));JSONArray words=new JSONArray();
                    if(task.isSuccessful())for(Text.TextBlock block:task.getResult().getTextBlocks())for(Text.Line line:block.getLines()){
                        add(words,line.getText(),line.getBoundingBox(),left,top,scale,sourceWidth,sourceHeight);
                        for(Text.Element element:line.getElements())add(words,element.getText(),element.getBoundingBox(),left,top,scale,sourceWidth,sourceHeight);
                    }
                    if(!task.isSuccessful())response.put("error","Offline text recognition failed");response.put("words",words);reply.accept(response);
                }catch(JSONException ignored){}finally{owned.recycle();busy.set(false);}
            });
        }catch(Exception e){if(image!=null&&!image.isRecycled())image.recycle();busy.set(false);try{reply.accept(new JSONObject().put("cmd","ocr").put("id",request.optLong("id")).put("error",e.getMessage()).put("words",new JSONArray()));}catch(JSONException ignored){}}
    }
    private static void add(JSONArray out,String text,Rect rect,int left,int top,float scale,int width,int height)throws JSONException{
        if(rect==null)return;out.put(new JSONObject().put("text",text).put("x",(left+rect.exactCenterX()/scale)/width).put("y",(top+rect.exactCenterY()/scale)/height).put("w",rect.width()/scale/width).put("h",rect.height()/scale/height));
    }
    @Override public void close(){recognizer.close();}
}

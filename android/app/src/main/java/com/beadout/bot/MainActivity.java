package com.beadout.bot;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Normal flow intentionally contains no coordinate fields, tracing tools, or calibration steps. */
public final class MainActivity extends Activity {
    private TextView status;
    private DetectionView image;
    private final Runnable update=this::refresh;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Bead Pilot");title.setTextSize(28);body.addView(title);
        TextView instructions=new TextView(this);instructions.setText("Press Start, then switch to Bead Out. The app finds the holding slots, squares, loop and feeder queues itself. Screen images stay in RAM.");instructions.setPadding(0,16,0,16);instructions.setTextSize(16);body.addView(instructions);
        button(body,"Start automatic play",()->{start("auto");Toast.makeText(this,"Switch to Bead Out now",Toast.LENGTH_LONG).show();});
        button(body,"Pause",()->{if(PilotService.active)start("pause");});
        button(body,"Stop",()->stopService(new Intent(this,PilotService.class)));
        status=new TextView(this);status.setTextSize(15);status.setPadding(0,20,0,20);status.setTextIsSelectable(true);body.addView(status);
        button(body,"Copy recent status",()->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Bead Pilot status",PilotService.recentEvents()));Toast.makeText(this,"Recent status copied",Toast.LENGTH_SHORT).show();});
        button(body,"Show detected board",()->{if(PilotService.active)start("snapshot");image.setVisibility(image.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);refresh();});
        image=new DetectionView();image.setVisibility(View.GONE);body.addView(image,new LinearLayout.LayoutParams(-1,-2));
        if(!Settings.canDrawOverlays(this))button(body,"Allow floating Stop button",()->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        TextView note=new TextView(this);note.setText("Automatic detection is experimental. If the view is unclear, taps pause while the app looks again. Unknown ? squares are obstacles until the game reveals their colors.");note.setTextSize(13);note.setPadding(0,20,0,10);body.addView(note);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
    }
    private void button(LinearLayout body,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());body.addView(b);}
    private void start(String action){startForegroundService(new Intent(this,PilotService.class).setAction(action));}
    @Override protected void onResume(){super.onResume();PilotService.statusObserver=update;refresh();}
    @Override protected void onPause(){super.onPause();if(PilotService.statusObserver==update)PilotService.statusObserver=null;}
    private void refresh(){if(status!=null)status.setText(PilotService.status+"\n\nRecent activity (RAM only):\n"+PilotService.recentEvents());if(image!=null){image.requestLayout();image.invalidate();}}
    private final class DetectionView extends View {
        private final Paint paint=new Paint(3);private final Rect destination=new Rect();
        DetectionView(){super(MainActivity.this);}
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws);Bitmap b=PilotService.preview;setMeasuredDimension(w,b==null?150:Math.round(w*(float)b.getHeight()/b.getWidth()));}
        @Override protected void onDraw(Canvas canvas){Bitmap b=PilotService.preview;if(b==null){paint.setColor(Color.DKGRAY);paint.setTextSize(24);canvas.drawText("No board detected yet",10,70,paint);return;}
            destination.set(0,0,getWidth(),getHeight());canvas.drawBitmap(b,null,destination,paint);Config c=PilotService.detectedLayout;if(c==null)return;
            for(java.util.List<float[]>p:c.lanes)path(canvas,p,Color.CYAN,true);for(Config.FeedPath f:c.feeders)path(canvas,f.points,Color.rgb(255,150,0),false);
            for(int i=0;i<c.slots.size();i++)dot(canvas,c.slots.get(i),Color.MAGENTA,"S"+(i+1));
            for(int i=0;i<c.buttons.size();i++){boolean open=i<c.clickable.length&&c.clickable[i];boolean unknown=i<c.unknownBoxes.length&&c.unknownBoxes[i];dot(canvas,c.buttons.get(i),open?Color.GREEN:Color.DKGRAY,unknown?"?":""+(i+1));}
        }
        private void dot(Canvas c,float[]p,int color,String label){paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawCircle(p[0]*getWidth(),p[1]*getHeight(),7,paint);paint.setTextSize(20);c.drawText(label,p[0]*getWidth()+8,p[1]*getHeight(),paint);}
        private void path(Canvas c,java.util.List<float[]>points,int color,boolean close){if(points.isEmpty())return;paint.setColor(color);paint.setStrokeWidth(2);paint.setStyle(Paint.Style.STROKE);Path p=new Path();p.moveTo(points.get(0)[0]*getWidth(),points.get(0)[1]*getHeight());for(int i=1;i<points.size();i++)p.lineTo(points.get(i)[0]*getWidth(),points.get(i)[1]*getHeight());if(close)p.close();c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);}
    }
}

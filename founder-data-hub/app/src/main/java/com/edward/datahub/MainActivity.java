package com.edward.datahub;

import android.app.*;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private EventDb db;
    private LinearLayout root, timeline;
    private TextView stats, status;
    private final int ACCENT=Color.rgb(232,255,91);
    private final int BG=Color.rgb(11,13,16);
    private final int CARD=Color.rgb(24,28,33);
    private final int MUTED=Color.rgb(165,171,180);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new EventDb(this);
        build();
        requestNotificationPermissionIfNeeded();
        refresh();
    }

    private void build(){
        ScrollView sv=new ScrollView(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(26),dp(20),dp(40));
        root.setBackgroundColor(BG);
        sv.addView(root);

        TextView title=text("LIFEGRAPH",28,Color.WHITE,true);
        root.addView(title);
        root.addView(text("Founder MVP · local behavioral data lab",14,MUTED,false));
        addSpace(18);

        stats=text("",17,Color.WHITE,true); root.addView(stats);
        status=text("",13,MUTED,false); root.addView(status);
        addSpace(16);

        Button toggle=button("START / STOP CAPTURE");
        toggle.setOnClickListener(v->toggleCapture());
        root.addView(toggle);

        Button usage=button("GRANT USAGE ACCESS");
        usage.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        root.addView(usage);

        Button notif=button("ENABLE NOTIFICATION METADATA");
        notif.setOnClickListener(v->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        root.addView(notif);

        Button manual=button("ADD MANUAL INTENT");
        manual.setOnClickListener(v->addManualEvent());
        root.addView(manual);

        addSpace(20);
        root.addView(text("TIMELINE",15,ACCENT,true));
        root.addView(text("Tap an event to label intent, outcome and satisfaction.",12,MUTED,false));
        timeline=new LinearLayout(this);
        timeline.setOrientation(LinearLayout.VERTICAL);
        root.addView(timeline);

        addSpace(20);
        root.addView(text("CONTROL",15,ACCENT,true));
        Button exportJson=button("EXPORT JSON FILE");
        exportJson.setOnClickListener(v->exportFile("json"));
        root.addView(exportJson);

        Button exportCsv=button("EXPORT CSV FILE");
        exportCsv.setOnClickListener(v->exportFile("csv"));
        root.addView(exportCsv);

        Button receipt=button("DATA RECEIPT");
        receipt.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Data Receipt")
            .setMessage("Stored: on-device only\nUploaded: 0 events\nSold: 0 events\nNotification bodies: never stored\nAccessibility capture: disabled\nPrecise location: not collected")
            .setPositiveButton("OK",null).show());
        root.addView(receipt);

        Button delete=button("DELETE ALL LOCAL DATA");
        delete.setOnClickListener(v->confirmDelete());
        root.addView(delete);

        setContentView(sv);
    }

    private void refresh(){
        int count=db.countToday();
        int trajectories=db.countTrajectoriesToday();
        int xp=getPreferences(MODE_PRIVATE).getInt("xp",0);
        stats.setText(count+" events today   ·   "+trajectories+" trajectories   ·   "+xp+" XP");
        boolean running=getPreferences(MODE_PRIVATE).getBoolean("capture",false);
        status.setText((running?"● CAPTURE ON":"○ CAPTURE OFF")+"   |   Usage access: "+(hasUsageAccess()?"ON":"OFF")+"   |   Notifications: metadata only");
        renderTimeline();
    }

    private void renderTimeline(){
        timeline.removeAllViews();
        Cursor c=db.recent(40);
        SimpleDateFormat df=new SimpleDateFormat("HH:mm",Locale.getDefault());
        try{
            while(c.moveToNext()){
                long id=c.getLong(0), ts=c.getLong(1);
                String type=c.getString(2), source=c.getString(3), detail=c.getString(4);
                String intent=c.isNull(5)?"":c.getString(5);
                String outcome=c.isNull(6)?"":c.getString(6);
                int satisfaction=c.getInt(7);
                String line=df.format(new Date(ts))+"  "+type+"\n"+friendlySource(source)+"  "+detail;
                if(!intent.isEmpty()) line+="\nIntent: "+intent;
                if(!outcome.isEmpty()) line+="  ·  Outcome: "+outcome+(satisfaction>0?" "+satisfaction+"/5":"");
                TextView item=text(line,13,Color.WHITE,false);
                item.setPadding(dp(14),dp(12),dp(14),dp(12));
                item.setBackgroundColor(CARD);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.setMargins(0,dp(8),0,0);
                item.setLayoutParams(lp);
                final long eventId=id;
                item.setOnClickListener(v->labelEvent(eventId));
                timeline.addView(item);
            }
        } finally { c.close(); }
        if(timeline.getChildCount()==0) timeline.addView(text("No events yet. Grant Usage Access and start capture.",13,MUTED,false));
    }

    private String friendlySource(String s){
        if(s==null)return "";
        int i=s.lastIndexOf('.');
        return i>=0?s.substring(i+1):s;
    }

    private void toggleCapture(){
        SharedPreferences p=getPreferences(MODE_PRIVATE);
        boolean on=p.getBoolean("capture",false);
        if(on){
            stopService(new Intent(this,CaptureService.class));
            p.edit().putBoolean("capture",false).apply();
        }else{
            if(!hasUsageAccess()){
                Toast.makeText(this,"Grant Usage Access first.",Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                return;
            }
            Intent i=new Intent(this,CaptureService.class);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            p.edit().putBoolean("capture",true).apply();
        }
        refresh();
    }

    private boolean hasUsageAccess(){
        try{
            UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
            long now=System.currentTimeMillis();
            return usm!=null && !usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,now-60000,now).isEmpty();
        }catch(Exception e){ return false; }
    }

    private void addManualEvent(){
        final EditText input=new EditText(this);
        input.setHint("What are you trying to do?");
        new AlertDialog.Builder(this).setTitle("Manual intent")
            .setView(input)
            .setPositiveButton("Save",(d,w)->{
                String s=input.getText().toString().trim();
                if(!s.isEmpty()) db.add("manual_intent","user",s);
                refresh();
            }).setNegativeButton("Cancel",null).show();
    }

    private void labelEvent(long id){
        final String[] intents={"Work","Communication","Research","Shopping","Food","Travel","Entertainment","Finance","Health/Fitness","Other"};
        new AlertDialog.Builder(this).setTitle("Intent")
            .setItems(intents,(d,which)->chooseOutcome(id,intents[which]))
            .setNegativeButton("Cancel",null).show();
    }

    private void chooseOutcome(long id,String intent){
        final String[] outcomes={"Completed","Abandoned","Still deciding"};
        new AlertDialog.Builder(this).setTitle("Outcome")
            .setItems(outcomes,(d,which)->{
                String outcome=outcomes[which];
                if("Completed".equals(outcome)) chooseSatisfaction(id,intent,outcome);
                else saveLabel(id,intent,outcome,0);
            }).show();
    }

    private void chooseSatisfaction(long id,String intent,String outcome){
        final String[] values={"1","2","3","4","5"};
        new AlertDialog.Builder(this).setTitle("Satisfaction")
            .setItems(values,(d,which)->saveLabel(id,intent,outcome,which+1)).show();
    }

    private void saveLabel(long id,String intent,String outcome,int satisfaction){
        db.label(id,intent,outcome,satisfaction);
        int gained=db.rewardOnce(id);
        if(gained>0){
            SharedPreferences p=getPreferences(MODE_PRIVATE);
            p.edit().putInt("xp",p.getInt("xp",0)+gained).apply();
            Toast.makeText(this,"+"+gained+" XP · trajectory improved",Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void exportFile(String type){
        try{
            File dir=new File(getCacheDir(),"exports");
            if(!dir.exists())dir.mkdirs();
            File file=new File(dir,"lifegraph-"+System.currentTimeMillis()+"."+type);
            String content="json".equals(type)?db.toJson().toString(2):db.toCsv();
            try(FileOutputStream out=new FileOutputStream(file)){ out.write(content.getBytes("UTF-8")); }
            Uri uri=FileProvider.getUriForFile(this,"com.edward.datahub.files",file);
            Intent share=new Intent(Intent.ACTION_SEND);
            share.setType("json".equals(type)?"application/json":"text/csv");
            share.putExtra(Intent.EXTRA_STREAM,uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Export local data"));
        }catch(Exception e){ Toast.makeText(this,"Export failed: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }

    private void confirmDelete(){
        new AlertDialog.Builder(this).setTitle("Delete all local data?")
            .setMessage("This permanently deletes captured events and labels from this phone.")
            .setPositiveButton("Delete",(d,w)->{
                stopService(new Intent(this,CaptureService.class));
                getPreferences(MODE_PRIVATE).edit().putBoolean("capture",false).putInt("xp",0).apply();
                db.clearAll(); refresh();
            }).setNegativeButton("Cancel",null).show();
    }

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=getPackageManager().PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},44);
    }

    @Override protected void onResume(){ super.onResume(); if(db!=null)refresh(); }

    private TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if(bold)t.setTypeface(null,1);
        t.setLineSpacing(0,1.15f);
        return t;
    }

    private Button button(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setBackgroundColor(CARD);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48)); lp.setMargins(0,dp(9),0,0); b.setLayoutParams(lp);
        return b;
    }

    private void addSpace(int h){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h))); root.addView(s); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}

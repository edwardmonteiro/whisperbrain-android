package com.edward.datahub;

import android.app.*;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private EventDb db;
    private LinearLayout root,topApps,periods;
    private TextView hero,metrics,pattern,summary,status;
    private AttentionTimelineView timeline;
    private final int ACCENT=Color.rgb(232,255,91), BG=Color.rgb(11,13,16), CARD=Color.rgb(24,28,33), MUTED=Color.rgb(165,171,180);

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
        root.setPadding(dp(20),dp(24),dp(20),dp(48));
        root.setBackgroundColor(BG);
        sv.addView(root);

        root.addView(text("LIFEGRAPH",13,ACCENT,true));
        root.addView(text("Today",30,Color.WHITE,true));
        root.addView(text("Human attention reconstructed locally",13,MUTED,false));
        space(24);

        hero=text("0m",42,Color.WHITE,true); root.addView(hero);
        root.addView(text("Screen Time",14,MUTED,false));
        space(14);
        metrics=text("",17,Color.WHITE,false); root.addView(metrics);
        status=text("",12,MUTED,false); root.addView(status);

        space(24);
        section("ATTENTION TIMELINE");
        timeline=new AttentionTimelineView(this,db);
        root.addView(timeline);

        space(24);
        section("ATTENTION PATTERN");
        pattern=text("",16,Color.WHITE,false); root.addView(pattern);

        space(24);
        section("BY PERIOD");
        periods=new LinearLayout(this); periods.setOrientation(LinearLayout.VERTICAL); root.addView(periods);

        space(24);
        section("TOP APPS");
        topApps=new LinearLayout(this); topApps.setOrientation(LinearLayout.VERTICAL); root.addView(topApps);

        space(24);
        section("DAILY SUMMARY");
        summary=text("",15,Color.WHITE,false); summary.setLineSpacing(0,1.25f); root.addView(summary);

        space(28);
        section("LIFE");
        TextView eventsCard=card("Turn raw telemetry into explainable human events and a reconstructed daily story.");
        root.addView(eventsCard);
        Button eventsButton=button("Open Life → Events");
        eventsButton.setOnClickListener(v->startActivity(new Intent(this,HumanEventsActivity.class)));
        root.addView(eventsButton);

        space(28);
        section("TRAINING DATA");
        TextView lab=card("Turn phone sessions into permissioned AI training trajectories.");
        root.addView(lab);
        Button labButton=button("Open Training Data Lab");
        labButton.setOnClickListener(v->startActivity(new Intent(this,TrainingDataLabActivity.class)));
        root.addView(labButton);
        TextView episodeCount=text(db.countHumanEpisodesToday()+" Human Episodes today · "+db.countTrainingReadyEpisodes()+" training-ready",11,MUTED,false);
        root.addView(episodeCount);

        space(28);
        section("ACTION DATASET");
        TextView actionCard=card("Convert app transitions into state → action → next state sequences for agent training.");
        root.addView(actionCard);
        Button actionButton=button("Preview latest action trajectory");
        actionButton.setOnClickListener(v->showActionTrajectoryPreview());
        root.addView(actionButton);

        space(28);
        section("CONTROL");
        Button toggle=button("Start / stop local capture"); toggle.setOnClickListener(v->toggleCapture()); root.addView(toggle);
        Button usage=button("Grant Usage Access"); usage.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))); root.addView(usage);
        Button notif=button("Enable notification metadata"); notif.setOnClickListener(v->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))); root.addView(notif);
        Button monitor=button("Developer · Activity Monitor"); monitor.setOnClickListener(v->startActivity(new Intent(this,ActivityMonitorActivity.class))); root.addView(monitor);
        Button export=button("Export LifeGraph V2.1"); export.setOnClickListener(v->exportAll()); root.addView(export);
        Button delete=button("Delete all local activity data"); delete.setOnClickListener(v->confirmDelete()); root.addView(delete);

        setContentView(sv);
    }

    private void refresh(){
        DailyAggregator.recompute(db,System.currentTimeMillis());
        String key=EventDb.DayBounds.key(System.currentTimeMillis());
        Cursor d=db.dailySummary(key);
        long screen=0,longest=0; int phone=0,unlocks=0,switches=0,focused=0,shortSessions=0;
        double avg=0;
        try{
            if(d.moveToFirst()){
                screen=d.getLong(d.getColumnIndexOrThrow("screen_time_seconds"));
                phone=d.getInt(d.getColumnIndexOrThrow("phone_sessions"));
                unlocks=d.getInt(d.getColumnIndexOrThrow("device_unlocks"));
                avg=d.getDouble(d.getColumnIndexOrThrow("average_session_seconds"));
                longest=d.getLong(d.getColumnIndexOrThrow("longest_uninterrupted_seconds"));
                switches=d.getInt(d.getColumnIndexOrThrow("context_switches"));
                focused=d.getInt(d.getColumnIndexOrThrow("focused_blocks"));
                shortSessions=d.getInt(d.getColumnIndexOrThrow("short_sessions"));
            }
        }finally{d.close();}

        hero.setText(DailySummaryRules.formatDuration(screen));
        metrics.setText(unlocks+" Unlocks     "+phone+" Phone Sessions\n"+DailySummaryRules.formatDuration(Math.round(avg))+" Average Session");
        boolean running=getPreferences(MODE_PRIVATE).getBoolean("capture",false);
        status.setText((running?"● CAPTURE ON":"○ CAPTURE OFF")+"   ·   Usage Access "+(hasUsageAccess()?"ON":"OFF"));
        pattern.setText("Focused blocks        "+focused+"\nShort sessions        "+shortSessions+"\nContext switches      "+switches+"\nLongest app session   "+DailySummaryRules.formatDuration(longest));
        timeline.setDay(System.currentTimeMillis());
        renderPeriods();
        renderApps(screen);
        summary.setText(DailySummaryRules.generate(db,System.currentTimeMillis()));
    }

    private void renderApps(long screen){
        topApps.removeAllViews();
        Cursor a=db.dailyApps(EventDb.DayBounds.key(System.currentTimeMillis()));
        int shown=0;
        try{
            while(a.moveToNext()&&shown<8){
                final String pkg=a.getString(0), app=a.getString(1), cat=a.getString(2);
                long dur=a.getLong(3); int count=a.getInt(4); double pct=a.getDouble(6);
                TextView row=card(app+"\n"+DailySummaryRules.formatDuration(dur)+" · "+count+" sessions · "+Math.round(pct)+"% · "+cat);
                row.setOnLongClickListener(v->{editCategory(pkg,app,cat);return true;});
                topApps.addView(row); shown++;
            }
        }finally{a.close();}
        if(shown==0)topApps.addView(text("No reconstructed app sessions yet.",13,MUTED,false));
        else topApps.addView(text("Long-press an app to change its category.",11,MUTED,false));
    }

    private void renderPeriods(){
        periods.removeAllViews();
        String[] names={"Night","Morning","Afternoon","Evening"};
        int[][] windows={{23,29},{5,12},{12,18},{18,23}};
        long day=EventDb.DayBounds.startOfToday();
        for(int i=0;i<names.length;i++){
            long start,end;
            if(i==0){
                long a=periodDuration(day,day+5*3600000L);
                long b=periodDuration(day+23*3600000L,day+24*3600000L);
                periods.addView(card("Night\nScreen time: "+DailySummaryRules.formatDuration(a+b)));
                continue;
            }
            start=day+windows[i][0]*3600000L; end=day+windows[i][1]*3600000L;
            long dur=periodDuration(start,end);
            periods.addView(card(names[i]+"\nScreen time: "+DailySummaryRules.formatDuration(dur)+" · "+periodTopApp(start,end)));
        }
    }

    private long periodDuration(long start,long end){
        Cursor c=db.appSessionsBetween(start,end); long total=0;
        try{
            while(c.moveToNext()){
                if(!"human".equals(c.getString(7))||c.getInt(6)!=1)continue;
                long s=Math.max(start,c.getLong(1)),e=Math.min(end,c.getLong(2));
                total+=Math.max(0,(e-s)/1000);
            }
        }finally{c.close();}
        return total;
    }

    private String periodTopApp(long start,long end){
        Cursor c=db.appSessionsBetween(start,end); Map<String,Long> m=new HashMap<>();
        try{
            while(c.moveToNext()){
                if(!"human".equals(c.getString(7))||c.getInt(6)!=1)continue;
                String app=c.getString(5);
                long s=Math.max(start,c.getLong(1)),e=Math.min(end,c.getLong(2)),dur=Math.max(0,(e-s)/1000);
                m.put(app,m.getOrDefault(app,0L)+dur);
            }
        }finally{c.close();}
        String best="No active app";long max=0;
        for(Map.Entry<String,Long>x:m.entrySet())if(x.getValue()>max){max=x.getValue();best=x.getKey();}
        return "Top: "+best;
    }

    private void editCategory(String pkg,String app,String current){
        final String[] cats={"Communication","Work","AI","Learning","Creation","Entertainment","Social","Finance","Health","Navigation","Shopping","Utilities","System","Unknown"};
        new AlertDialog.Builder(this).setTitle(app+" category")
                .setSingleChoiceItems(cats,indexOf(cats,current),(d,which)->{
                    db.setCategory(pkg,app,cats[which],true); d.dismiss(); refresh();
                }).setNegativeButton("Cancel",null).show();
    }

    private int indexOf(String[] xs,String x){for(int i=0;i<xs.length;i++)if(xs[i].equals(x))return i;return xs.length-1;}

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
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            p.edit().putBoolean("capture",true).apply();
        }
        refresh();
    }

    private boolean hasUsageAccess(){
        try{
            UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
            long now=System.currentTimeMillis();
            return usm!=null&&!usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,now-60000,now).isEmpty();
        }catch(Exception e){return false;}
    }

    private void exportAll(){
        try{
            DailyAggregator.recompute(db,System.currentTimeMillis());
            File dir=new File(getCacheDir(),"exports/lifegraph-"+System.currentTimeMillis());
            if(!dir.mkdirs()&&!dir.exists())throw new IOException("Cannot create export folder");
            ArrayList<Uri> uris=new ArrayList<>();
            write(dir,"events.csv",db.eventsCsv(),uris);
            write(dir,"app_sessions.csv",db.appSessionsCsv(),uris);
            write(dir,"phone_sessions.csv",db.phoneSessionsCsv(),uris);
            write(dir,"daily_summary.csv",db.dailySummaryCsv(),uris);
            write(dir,"human_episodes.csv",db.inferredEpisodesCsv(),uris);
            write(dir,"episode_evidence.csv",db.episodeEvidenceCsv(),uris);
            write(dir,"training_episodes.csv",db.humanEpisodesCsv(),uris);
            write(dir,"action_trajectories.csv",buildActionTrajectoriesCsv(),uris);
            write(dir,"action_trajectories.json",buildActionTrajectoriesJson(),uris);
            write(dir,"lifegraph_export.json",db.fullExportJson().toString(2),uris);
            Intent share=new Intent(Intent.ACTION_SEND_MULTIPLE);
            share.setType("*/*"); share.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Export LifeGraph V2.1"));
        }catch(Exception e){Toast.makeText(this,"Export failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private String buildActionTrajectoriesCsv(){
        StringBuilder b=new StringBuilder("trajectory_id,phone_session_id,timestamp,state,action,next_state,app_name,goal,outcome,consent_scope\n");
        long start=EventDb.DayBounds.startOfToday(),end=System.currentTimeMillis()+1;
        Cursor p=db.phoneSessionsBetween(start,end);
        try{
            while(p.moveToNext()){
                String phoneId=p.getString(0);
                long ps=p.getLong(1),pe=p.getLong(2);
                Cursor a=db.appSessionsBetween(ps,pe);
                ArrayList<String[]> apps=new ArrayList<>();
                try{
                    while(a.moveToNext()){
                        if(!"human".equals(a.getString(7)))continue;
                        apps.add(new String[]{a.getString(5),String.valueOf(a.getLong(1))});
                    }
                }finally{a.close();}
                String trajectoryId="phone-"+phoneId;
                String previous="DEVICE_UNLOCKED";
                for(int i=0;i<apps.size();i++){
                    String app=apps.get(i)[0];
                    String next=i+1<apps.size()?apps.get(i+1)[0]:"SCREEN_OFF";
                    String action=i==0?"OPEN_APP":"SWITCH_APP";
                    String[] row={trajectoryId,phoneId,EventDb.iso(Long.parseLong(apps.get(i)[1])),previous,action,next,app,"","","not_licensed"};
                    for(int j=0;j<row.length;j++){
                        String s=row[j]==null?"":row[j].replace(""","""");
                        b.append(""").append(s).append(""");
                        if(j<row.length-1)b.append(",");
                    }
                    b.append("\n");
                    previous=app;
                }
            }
        }finally{p.close();}
        return b.toString();
    }

    private String buildActionTrajectoriesJson() throws Exception{
        org.json.JSONArray trajectories=new org.json.JSONArray();
        long start=EventDb.DayBounds.startOfToday(),end=System.currentTimeMillis()+1;
        Cursor p=db.phoneSessionsBetween(start,end);
        try{
            while(p.moveToNext()){
                String phoneId=p.getString(0);
                long ps=p.getLong(1),pe=p.getLong(2);
                Cursor a=db.appSessionsBetween(ps,pe);
                ArrayList<String[]> apps=new ArrayList<>();
                try{
                    while(a.moveToNext()){
                        if(!"human".equals(a.getString(7)))continue;
                        apps.add(new String[]{a.getString(5),String.valueOf(a.getLong(1))});
                    }
                }finally{a.close();}
                if(apps.isEmpty())continue;

                org.json.JSONObject t=new org.json.JSONObject();
                t.put("trajectory_id","phone-"+phoneId);
                t.put("phone_session_id",phoneId);
                t.put("start_time",EventDb.iso(ps));
                t.put("end_time",EventDb.iso(pe));
                t.put("goal",org.json.JSONObject.NULL);
                t.put("outcome",org.json.JSONObject.NULL);
                t.put("consent_scope","not_licensed");
                t.put("privacy","App transitions only. No UI text, messages, screenshots, audio or screen content.");

                org.json.JSONArray steps=new org.json.JSONArray();
                String previous="DEVICE_UNLOCKED";
                for(int i=0;i<apps.size();i++){
                    String app=apps.get(i)[0];
                    String next=i+1<apps.size()?apps.get(i+1)[0]:"SCREEN_OFF";
                    org.json.JSONObject s=new org.json.JSONObject();
                    s.put("timestamp",EventDb.iso(Long.parseLong(apps.get(i)[1])));
                    s.put("state",previous);
                    s.put("action",i==0?"OPEN_APP":"SWITCH_APP");
                    s.put("next_state",next);
                    s.put("app_name",app);
                    steps.put(s);
                    previous=app;
                }
                t.put("steps",steps);
                trajectories.put(t);
            }
        }finally{p.close();}
        org.json.JSONObject root=new org.json.JSONObject();
        root.put("schema_version","lam-actions-v1");
        root.put("generated_at",EventDb.iso(System.currentTimeMillis()));
        root.put("trajectories",trajectories);
        return root.toString(2);
    }

    private void write(File dir,String name,String content,ArrayList<Uri> uris)throws Exception{
        File file=new File(dir,name);
        try(FileOutputStream out=new FileOutputStream(file)){out.write(content.getBytes("UTF-8"));}
        uris.add(FileProvider.getUriForFile(this,"com.edward.datahub.files",file));
    }

    private void showActionTrajectoryPreview(){
        Cursor p=db.phoneSessionsBetween(EventDb.DayBounds.startOfToday(),System.currentTimeMillis()+1);
        long start=0,end=0;
        try{
            while(p.moveToNext()){
                if(p.getLong(3)>=60){start=p.getLong(1);end=p.getLong(2);}
            }
        }finally{p.close();}

        if(start==0){
            new AlertDialog.Builder(this).setTitle("No trajectory yet")
                    .setMessage("Use the phone for a few minutes, lock it to close the session, then return here.")
                    .setPositiveButton("OK",null).show();
            return;
        }

        Cursor a=db.appSessionsBetween(start,end);
        ArrayList<String> apps=new ArrayList<>();
        try{
            while(a.moveToNext()){
                if(!"human".equals(a.getString(7)))continue;
                apps.add(a.getString(5));
            }
        }finally{a.close();}

        if(apps.isEmpty()){
            new AlertDialog.Builder(this).setTitle("No app sequence found").setPositiveButton("OK",null).show();
            return;
        }

        StringBuilder b=new StringBuilder();
        b.append("ACTION TRAJECTORY\n\n");
        String previous="DEVICE_UNLOCKED";
        for(int i=0;i<apps.size();i++){
            String app=apps.get(i);
            String next=i+1<apps.size()?apps.get(i+1):"SCREEN_OFF";
            String action=i==0?"OPEN_APP":"SWITCH_APP";
            b.append("State: ").append(previous).append("\n");
            b.append("Action: ").append(action).append(" → ").append(app).append("\n");
            b.append("Next: ").append(next).append("\n\n");
            previous=app;
        }
        b.append("Privacy\nNo UI text, messages, screenshots, audio or screen content.");

        new AlertDialog.Builder(this)
                .setTitle("Latest action trajectory")
                .setMessage(b.toString())
                .setPositiveButton("Label goal",(d,w)->labelActionTrajectory(start,end,apps))
                .setNegativeButton("Close",null).show();
    }

    private void labelActionTrajectory(long start,long end,ArrayList<String> apps){
        final String[] goals={"Buy something","Research something","Work","Learn","Communicate","Navigate","Other"};
        new AlertDialog.Builder(this).setTitle("What was the goal?")
                .setItems(goals,(d,w)->labelActionOutcome(start,end,apps,goals[w])).show();
    }

    private void labelActionOutcome(long start,long end,ArrayList<String> apps,String goal){
        final String[] outcomes={"Completed","Abandoned","Still deciding"};
        new AlertDialog.Builder(this).setTitle("Outcome")
                .setItems(outcomes,(d,w)->{
                    StringBuilder sequence=new StringBuilder();
                    for(int i=0;i<apps.size();i++){
                        if(i>0)sequence.append(" → ");
                        sequence.append(apps.get(i));
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Agent training sample")
                            .setMessage("Goal\n"+goal+"\n\nSequence\n"+sequence+"\n\nOutcome\n"+outcomes[w]+
                                    "\n\nDuration\n"+DailySummaryRules.formatDuration((end-start)/1000)+
                                    "\n\nConsent\nNot licensed")
                            .setPositiveButton("Done",null).show();
                }).show();
    }

    private void confirmDelete(){
        new AlertDialog.Builder(this).setTitle("Delete all local activity data?")
                .setMessage("This removes raw events, reconstructed sessions and daily summaries from this phone.")
                .setPositiveButton("Delete",(d,w)->{
                    stopService(new Intent(this,CaptureService.class));
                    getPreferences(MODE_PRIVATE).edit().putBoolean("capture",false).apply();
                    db.clearAll();refresh();
                }).setNegativeButton("Cancel",null).show();
    }

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=getPackageManager().PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},44);
    }

    @Override protected void onResume(){super.onResume();if(db!=null)refresh();}

    private void section(String s){root.addView(text(s,12,ACCENT,true));space(10);}
    private TextView card(String s){TextView t=text(s,14,Color.WHITE,false);t.setPadding(dp(14),dp(13),dp(14),dp(13));t.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));t.setLayoutParams(lp);return t;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(null,1);t.setLineSpacing(0,1.18f);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,0,0,dp(9));b.setLayoutParams(lp);return b;}
    private void space(int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));root.addView(s);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}

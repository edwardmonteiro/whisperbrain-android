package com.edward.datahub;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.util.*;

public class SequenceLabActivity extends Activity {
    private EventDb db;
    private LinearLayout root;
    private final int BG=Color.rgb(11,13,16),CARD=Color.rgb(24,28,33),ACCENT=Color.rgb(232,255,91),MUTED=Color.rgb(165,171,180);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new EventDb(this);
        showLatest();
    }

    private void showLatest(){
        base("Sequence Lab","App-transition trajectories derived from local phone sessions.");
        Trajectory t=latest();
        if(t==null){
            root.addView(card("No eligible phone session yet.\nUse LifeGraph normally, switch between apps, then lock the phone."));
            return;
        }

        root.addView(card("Duration: "+DailySummaryRules.formatDuration((t.end-t.start)/1000)+"\nSteps: "+t.apps.size()+"\nConsent: not licensed"));
        root.addView(card(sequenceText(t)));

        Button label=button("Label goal and outcome");
        label.setOnClickListener(v->askGoal(t));
        root.addView(label);

        Button export=button("Export latest sequence");
        export.setOnClickListener(v->export(t));
        root.addView(export);

        root.addView(text("Privacy: app transitions only. No UI text, message bodies, screenshots, audio, or screen content.",11,MUTED,false));
    }

    private Trajectory latest(){
        Cursor p=db.phoneSessionsBetween(EventDb.DayBounds.startOfToday(),System.currentTimeMillis()+1);
        long start=0,end=0; String phoneId="";
        try{
            while(p.moveToNext()){
                if(p.getLong(3)>=60){
                    phoneId=p.getString(0);
                    start=p.getLong(1);
                    end=p.getLong(2);
                }
            }
        }finally{p.close();}
        if(start==0)return null;

        Cursor a=db.appSessionsBetween(start,end);
        ArrayList<AppStep> apps=new ArrayList<>();
        try{
            while(a.moveToNext()){
                if(!"human".equals(a.getString(7)))continue;
                apps.add(new AppStep(a.getLong(1),a.getString(5),a.getString(4)));
            }
        }finally{a.close();}
        if(apps.isEmpty())return null;

        Trajectory t=new Trajectory();
        t.phoneId=phoneId;t.start=start;t.end=end;t.apps=apps;
        return t;
    }

    private String sequenceText(Trajectory t){
        StringBuilder b=new StringBuilder();
        String previous="DEVICE_UNLOCKED";
        for(int i=0;i<t.apps.size();i++){
            AppStep s=t.apps.get(i);
            String next=i+1<t.apps.size()?t.apps.get(i+1).app:"SCREEN_OFF";
            String action=i==0?"OPEN_APP":"SWITCH_APP";
            b.append("State: ").append(previous).append("\n");
            b.append("Action: ").append(action).append(" → ").append(s.app).append("\n");
            b.append("Next: ").append(next).append("\n\n");
            previous=s.app;
        }
        return b.toString().trim();
    }

    private void askGoal(Trajectory t){
        String[] goals={"Buy something","Research something","Work","Learn","Communicate","Navigate","Other"};
        new AlertDialog.Builder(this).setTitle("What was the goal?")
                .setItems(goals,(d,w)->askOutcome(t,goals[w])).show();
    }

    private void askOutcome(Trajectory t,String goal){
        String[] outcomes={"Completed","Abandoned","Still deciding"};
        new AlertDialog.Builder(this).setTitle("Outcome")
                .setItems(outcomes,(d,w)->{
                    t.goal=goal;t.outcome=outcomes[w];
                    new AlertDialog.Builder(this)
                            .setTitle("Sequence labeled")
                            .setMessage("Goal\n"+t.goal+"\n\nOutcome\n"+t.outcome+"\n\nConsent\nNot licensed")
                            .setPositiveButton("OK",null).show();
                }).show();
    }

    private void export(Trajectory t){
        try{
            File dir=new File(getCacheDir(),"exports/sequence-"+System.currentTimeMillis());
            if(!dir.mkdirs()&&!dir.exists())throw new IOException("Cannot create export folder");
            File csv=new File(dir,"action_trajectory.csv");
            File json=new File(dir,"action_trajectory.json");
            write(csv,toCsv(t));
            write(json,toJson(t).toString(2));

            ArrayList<Uri> uris=new ArrayList<>();
            uris.add(FileProvider.getUriForFile(this,"com.edward.datahub.files",csv));
            uris.add(FileProvider.getUriForFile(this,"com.edward.datahub.files",json));

            Intent share=new Intent(Intent.ACTION_SEND_MULTIPLE);
            share.setType("*/*");
            share.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Export sequence"));
        }catch(Exception e){
            Toast.makeText(this,"Export failed: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private String toCsv(Trajectory t){
        StringBuilder b=new StringBuilder("trajectory_id,phone_session_id,timestamp,state,action,next_state,app_name,package_name,goal,outcome,consent_scope\n");
        String previous="DEVICE_UNLOCKED";
        String id="phone-"+t.phoneId;
        for(int i=0;i<t.apps.size();i++){
            AppStep s=t.apps.get(i);
            String next=i+1<t.apps.size()?t.apps.get(i+1).app:"SCREEN_OFF";
            String action=i==0?"OPEN_APP":"SWITCH_APP";
            addCsvRow(b,new String[]{id,t.phoneId,EventDb.iso(s.ts),previous,action,next,s.app,s.pkg,t.goal,t.outcome,"not_licensed"});
            previous=s.app;
        }
        return b.toString();
    }

    private JSONObject toJson(Trajectory t) throws JSONException{
        JSONObject root=new JSONObject();
        root.put("schema_version","action-sequence-v1");
        root.put("trajectory_id","phone-"+t.phoneId);
        root.put("phone_session_id",t.phoneId);
        root.put("start_time",EventDb.iso(t.start));
        root.put("end_time",EventDb.iso(t.end));
        root.put("goal",t.goal.isEmpty()?JSONObject.NULL:t.goal);
        root.put("outcome",t.outcome.isEmpty()?JSONObject.NULL:t.outcome);
        root.put("consent_scope","not_licensed");

        JSONArray steps=new JSONArray();
        String previous="DEVICE_UNLOCKED";
        for(int i=0;i<t.apps.size();i++){
            AppStep s=t.apps.get(i);
            String next=i+1<t.apps.size()?t.apps.get(i+1).app:"SCREEN_OFF";
            JSONObject o=new JSONObject();
            o.put("timestamp",EventDb.iso(s.ts));
            o.put("state",previous);
            o.put("action",i==0?"OPEN_APP":"SWITCH_APP");
            o.put("next_state",next);
            o.put("app_name",s.app);
            o.put("package_name",s.pkg);
            steps.put(o);
            previous=s.app;
        }
        root.put("steps",steps);
        return root;
    }

    private void addCsvRow(StringBuilder b,String[] cells){
        for(int i=0;i<cells.length;i++){
            String s=cells[i]==null?"":cells[i].replace(""","""");
            b.append(""").append(s).append(""");
            if(i<cells.length-1)b.append(",");
        }
        b.append("\n");
    }

    private void write(File file,String content)throws Exception{
        try(FileOutputStream out=new FileOutputStream(file)){out.write(content.getBytes("UTF-8"));}
    }

    private void base(String title,String subtitle){
        ScrollView sv=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(48));root.setBackgroundColor(BG);sv.addView(root);
        root.addView(text("LIFEGRAPH",12,ACCENT,true));
        root.addView(text(title,28,Color.WHITE,true));
        root.addView(text(subtitle,13,MUTED,false));
        space(20);setContentView(sv);
    }

    private TextView card(String s){TextView t=text(s,14,Color.WHITE,false);t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));t.setLayoutParams(lp);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,0,0,dp(9));b.setLayoutParams(lp);return b;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
    private void space(int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));root.addView(s);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    private static class AppStep{
        long ts;String app,pkg;
        AppStep(long ts,String app,String pkg){this.ts=ts;this.app=app;this.pkg=pkg;}
    }

    private static class Trajectory{
        String phoneId="";
        long start,end;
        String goal="";
        String outcome="";
        ArrayList<AppStep> apps=new ArrayList<>();
    }
}

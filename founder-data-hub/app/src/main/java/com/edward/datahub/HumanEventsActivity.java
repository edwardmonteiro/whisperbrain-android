package com.edward.datahub;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

public class HumanEventsActivity extends Activity {
    private EventDb db;
    private LinearLayout root,episodes;
    private TextView metrics,story;
    private final int BG=Color.rgb(11,13,16),CARD=Color.rgb(24,28,33),ACCENT=Color.rgb(232,255,91),MUTED=Color.rgb(165,171,180);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new EventDb(this);
        build();
        rebuild();
    }

    private void build(){
        ScrollView sv=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(48));root.setBackgroundColor(BG);sv.addView(root);

        root.addView(text("LIFE → EVENTS",12,ACCENT,true));
        root.addView(text("Today",30,Color.WHITE,true));
        root.addView(text("Technical signals reconstructed into human events.",13,MUTED,false));
        space(20);

        Button rebuild=button("Rebuild my timeline");
        rebuild.setOnClickListener(v->rebuild());
        root.addView(rebuild);

        metrics=text("",14,Color.WHITE,false);root.addView(metrics);
        space(20);

        root.addView(text("YOUR DAY",12,ACCENT,true));
        story=text("",15,Color.WHITE,false);story.setLineSpacing(0,1.25f);root.addView(story);
        space(22);

        root.addView(text("HUMAN EVENTS",12,ACCENT,true));
        episodes=new LinearLayout(this);episodes.setOrientation(LinearLayout.VERTICAL);root.addView(episodes);

        setContentView(sv);
    }

    private void rebuild(){
        long start=EventDb.DayBounds.startOfToday(),end=EventDb.DayBounds.endOf(System.currentTimeMillis());
        HumanEventEngine.Result r=new HumanEventEngine(db).rebuild(start,end);
        renderMetrics(r.metrics);
        story.setText(new DailyStoryEngine().build(db,System.currentTimeMillis()));
        renderEpisodes(start,end);
        Toast.makeText(this,"Inference Engine "+HumanEventEngine.VERSION+" rebuilt locally",Toast.LENGTH_SHORT).show();
    }

    private void renderMetrics(NoiseDetector.Metrics m){
        metrics.setText(
                "Raw events                 "+m.rawEventCount+"\n"+
                "Normalized signals         "+m.normalizedSignalCount+"\n"+
                "Activity clusters          "+m.clusterCount+"\n"+
                "Human episodes             "+m.humanEpisodeCount+"\n"+
                "Unknown episodes           "+m.unknownEpisodeCount+"\n"+
                "Compression ratio          "+fmt(m.compressionRatio)+"x\n"+
                "High-confidence coverage   "+Math.round(m.highConfidenceCoverage)+"%"
        );
    }

    private void renderEpisodes(long start,long end){
        episodes.removeAllViews();
        Cursor c=db.inferredEpisodesBetween(start,end);
        SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.getDefault());
        int count=0;
        try{
            while(c.moveToNext()){
                final String id=c.getString(0),type=c.getString(4),title=c.getString(6),desc=c.getString(7);
                final String feedback=c.getString(16),correction=c.getString(17);
                long ts=c.getLong(1),dur=c.getLong(3);
                double conf=c.getDouble(10);
                int evidence=c.getInt(12);
                String displayTitle=correction!=null&&!correction.isEmpty()?correction:title;
                String duration=dur>=60?" · "+DailySummaryRules.formatDuration(dur):"";
                TextView card=card(time.format(new Date(ts))+"  "+displayTitle+duration+"\n"+
                        Math.round(conf*100)+"% confidence · "+evidence+" signals"+
                        (feedback!=null&&!feedback.isEmpty()?" · "+feedback:""));
                card.setOnClickListener(v->showEvidence(id,type,title,desc));
                episodes.addView(card);
                count++;
            }
        }finally{c.close();}
        if(count==0)episodes.addView(text("No reconstructable human events yet.",13,MUTED,false));
    }

    private void showEvidence(String episodeId,String type,String title,String description){
        Cursor c=db.episodeEvidence(episodeId);
        StringBuilder b=new StringBuilder();
        SimpleDateFormat t=new SimpleDateFormat("HH:mm:ss",Locale.getDefault());
        int n=0;
        try{
            while(c.moveToNext()){
                if(n++>=30){b.append("\n… more evidence available in export");break;}
                b.append(t.format(new Date(c.getLong(1)))).append("\n")
                        .append(c.getString(2)).append("\n")
                        .append(c.getString(3)).append("  ").append(c.getString(4)==null?"":c.getString(4)).append("\n\n");
            }
        }finally{c.close();}
        new AlertDialog.Builder(this)
                .setTitle("Why we think this happened")
                .setMessage(title+"\n\n"+description+"\n\nSIGNALS\n\n"+b)
                .setPositiveButton("✓ Correct",(d,w)->saveFeedback(episodeId,type,title,"Correct",""))
                .setNeutralButton("✎ Edit",(d,w)->editEpisode(episodeId,type,title))
                .setNegativeButton("✕ Wrong",(d,w)->saveFeedback(episodeId,type,title,"Wrong",""))
                .show();
    }

    private void editEpisode(String id,String type,String original){
        EditText input=new EditText(this);input.setText(original);input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("What actually happened?")
                .setView(input)
                .setPositiveButton("Save",(d,w)->{
                    String corrected=input.getText().toString().trim();
                    if(!corrected.isEmpty())saveFeedback(id,type,original,"Edited",corrected);
                }).setNegativeButton("Cancel",null).show();
    }

    private void saveFeedback(String id,String type,String original,String feedback,String correction){
        db.saveEpisodeFeedback(id,feedback,correction,type,original);
        renderEpisodes(EventDb.DayBounds.startOfToday(),EventDb.DayBounds.endOf(System.currentTimeMillis()));
    }

    private String fmt(double d){return String.format(Locale.US,"%.1f",d);}
    private TextView card(String s){TextView t=text(s,14,Color.WHITE,false);t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));t.setLayoutParams(lp);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,0,0,dp(12));b.setLayoutParams(lp);return b;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
    private void space(int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));root.addView(s);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}

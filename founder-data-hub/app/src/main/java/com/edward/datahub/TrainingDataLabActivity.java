package com.edward.datahub;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class TrainingDataLabActivity extends Activity {
    private EventDb db;
    private LinearLayout root;
    private final int BG=Color.rgb(11,13,16), CARD=Color.rgb(24,28,33), ACCENT=Color.rgb(232,255,91), MUTED=Color.rgb(165,171,180);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new EventDb(this);
        showLanding();
    }

    private void showLanding(){
        base("Training Data Lab","Demo first. Then try with one of your real sessions.");
        root.addView(card("34 raw events\n↓\n6 app sessions\n↓\n1 human episode\n↓\n3 human labels\n↓\n1 AI-ready trajectory"));
        Button demo=button("Experience demo");
        demo.setOnClickListener(v->showDemoRaw());
        root.addView(demo);
        Button real=button("Try with my data");
        real.setOnClickListener(v->showRealCandidate());
        root.addView(real);
        root.addView(text("Demo data never enters your database or exports.",11,MUTED,false));
    }

    private void showDemoRaw(){
        base("Demo · Raw activity","Synthetic session, isolated from your real data.");
        root.addView(badge("DEMO DATA"));
        root.addView(card("11:02 DEVICE_UNLOCK\n11:02 WhatsApp · 3m\n11:05 Chrome · 6m\n11:11 ChatGPT · 5m\n11:16 Amazon · 5m\n11:21 YouTube · 4m\n11:25 Amazon · 4m\n11:29 SCREEN_OFF"));
        Button b=button("Build Human Episode");
        b.setOnClickListener(v->showDemoEpisode());
        root.addView(b);
    }

    private void showDemoEpisode(){
        base("Human Episode","LifeGraph compiles raw telemetry into a human trajectory.");
        root.addView(badge("DEMO DATA"));
        root.addView(card("Duration: 27m\nApps: 5\nContext switches: 5\nHuman confidence: 96%\n\nCommunication → Research → AI → Shopping → Research → Shopping"));
        Button a=button("Add human context");
        a.setOnClickListener(v->askDemoIntent());
        root.addView(a);
        Button s=button("View AI Training Sample");
        s.setOnClickListener(v->showDemoSample("Unlabeled","Unlabeled",0));
        root.addView(s);
    }

    private void askDemoIntent(){
        String[] x={"Buy something","Research something","Work","Learn","Entertainment","Other"};
        new AlertDialog.Builder(this).setTitle("What were you trying to do?")
                .setItems(x,(d,w)->askDemoOutcome(x[w])).show();
    }

    private void askDemoOutcome(String intent){
        String[] x={"Completed","Abandoned","Still deciding"};
        new AlertDialog.Builder(this).setTitle("Did you complete your goal?")
                .setItems(x,(d,w)->{
                    if("Completed".equals(x[w]))askDemoSatisfaction(intent,x[w]);
                    else showDemoSample(intent,x[w],0);
                }).show();
    }

    private void askDemoSatisfaction(String intent,String outcome){
        String[] x={"1","2","3","4","5"};
        new AlertDialog.Builder(this).setTitle("Satisfaction")
                .setItems(x,(d,w)->showDemoSample(intent,outcome,w+1)).show();
    }

    private void showDemoSample(String intent,String outcome,int satisfaction){
        int quality=60;
        if(!"Unlabeled".equals(intent))quality+=15;
        if(!"Unlabeled".equals(outcome))quality+=15;
        if(satisfaction>0)quality+=10;
        quality=Math.min(100,quality);
        String readiness=quality>=85?"Ready":quality>=65?"Almost ready":"Needs context";
        base("AI Training Sample","What a model buyer could receive.");
        root.addView(badge("DEMO DATA"));
        root.addView(card("Goal\n"+intent+"\n\nSequence\nCommunication → Research → AI → Shopping → Research → Shopping\n\nOutcome\n"+outcome+"\nSatisfaction\n"+(satisfaction>0?satisfaction+"/5":"Unlabeled")+"\n\nHuman confidence\n96%\nDataset quality\n"+quality+"/100\nTraining readiness\n"+readiness+"\n\nNo messages, typed text, contacts, exact location or identity."));
        Button m=button("View simulated Data Mission");
        final int q=quality;
        m.setOnClickListener(v->showMission(q));
        root.addView(m);
    }

    private void showMission(int quality){
        int match=Math.min(98,55+quality/2);
        base("Data Mission","SIMULATION · nothing leaves this phone.");
        root.addView(badge("SIMULATION"));
        root.addView(card("Use case\nShopping-agent training and evaluation\n\nBuyer match\n"+match+"%\n\nIncluded\n✓ app categories\n✓ sequence\n✓ duration\n✓ intent\n✓ outcome\n✓ satisfaction\n\nExcluded\n✕ message contents\n✕ search text\n✕ passwords\n✕ contacts\n✕ exact location\n✕ identity"));
        Button a=button("Approve simulation");
        a.setOnClickListener(v->showValue(quality,match));
        root.addView(a);
    }

    private void showValue(int quality,int match){
        base("Training value","Quality indicators only. No monetary claim.");
        root.addView(card("Dataset quality: "+quality+"/100\nTraining readiness: "+(quality>=85?"High":"Needs context")+"\nBuyer match: "+match+"%\n\nEstimated monetary value: Experimental"));
        Button b=button("Now try it with my data");
        b.setOnClickListener(v->showRealCandidate());
        root.addView(b);
    }

    private void showRealCandidate(){
        Cursor p=db.latestPhoneSessionForEpisode();
        try{
            if(!p.moveToFirst()){
                new AlertDialog.Builder(this).setTitle("No eligible session yet")
                        .setMessage("Use LifeGraph for a few minutes, lock the phone to close the session, then return here.")
                        .setPositiveButton("OK",null).show();
                return;
            }
            String phoneId=p.getString(0);
            long start=p.getLong(1), end=p.getLong(2);
            Cursor a=db.appSessionsForWindow(start,end);
            ArrayList<HumanEpisode.Step> steps=new ArrayList<>();
            try{
                while(a.moveToNext())steps.add(new HumanEpisode.Step(a.getString(4),a.getString(5),a.getString(8),a.getLong(3)));
            }finally{a.close();}
            if(steps.isEmpty()){
                new AlertDialog.Builder(this).setTitle("Session has no app data").setPositiveButton("OK",null).show();
                return;
            }
            HumanEpisode e=new HumanEpisode();
            e.episodeId=UUID.randomUUID().toString(); e.phoneSessionId=phoneId; e.startTs=start; e.endTs=end; e.demo=false; e.steps.addAll(steps);
            e.humanConfidence=Math.min(0.98,0.82+Math.min(0.12,steps.size()*0.02));
            showRealEpisode(e);
        }finally{p.close();}
    }

    private void showRealEpisode(HumanEpisode e){
        base("Your Human Episode","Derived locally from one real phone session.");
        StringBuilder seq=new StringBuilder();
        for(int i=0;i<e.steps.size();i++){
            if(i>0)seq.append(" → ");
            seq.append(e.steps.get(i).appName);
        }
        root.addView(card("Duration: "+DailySummaryRules.formatDuration(e.durationSeconds())+"\nApps: "+e.steps.size()+"\nContext switches: "+e.contextSwitches()+"\nHuman confidence: "+Math.round(e.humanConfidence*100)+"%\n\n"+seq));
        Button a=button("Label this session");
        a.setOnClickListener(v->askRealIntent(e));
        root.addView(a);
    }

    private void askRealIntent(HumanEpisode e){
        String[] x={"Buy something","Research something","Work","Learn","Entertainment","Communication","Other"};
        new AlertDialog.Builder(this).setTitle("What were you trying to do?")
                .setItems(x,(d,w)->askRealOutcome(e,x[w])).show();
    }

    private void askRealOutcome(HumanEpisode e,String intent){
        String[] x={"Completed","Abandoned","Still deciding"};
        new AlertDialog.Builder(this).setTitle("Did you complete your goal?")
                .setItems(x,(d,w)->{
                    if("Completed".equals(x[w]))askRealSatisfaction(e,intent,x[w]);
                    else saveRealEpisode(e,intent,x[w],0);
                }).show();
    }

    private void askRealSatisfaction(HumanEpisode e,String intent,String outcome){
        String[] x={"1","2","3","4","5"};
        new AlertDialog.Builder(this).setTitle("Satisfaction")
                .setItems(x,(d,w)->saveRealEpisode(e,intent,outcome,w+1)).show();
    }

    private void saveRealEpisode(HumanEpisode e,String intent,String outcome,int satisfaction){
        e.intent=intent; e.outcome=outcome; e.satisfaction=satisfaction;
        int q=60+15+15+(satisfaction>0?10:0);
        e.qualityScore=Math.min(100,q);
        e.readiness=e.qualityScore>=85?"Ready":"Almost ready";
        e.consentScope="not_licensed";
        try{
            db.saveHumanEpisode(e.episodeId,e.phoneSessionId,e.startTs,e.endTs,e.sequenceJson().toString(),e.intent,e.outcome,e.satisfaction,e.humanConfidence,e.qualityScore,e.readiness,e.consentScope);
            base("Training-ready episode","Saved locally. Not licensed or shared.");
            root.addView(card("Goal: "+e.intent+"\nOutcome: "+e.outcome+"\nSatisfaction: "+(e.satisfaction>0?e.satisfaction+"/5":"N/A")+"\nQuality: "+e.qualityScore+"/100\nReadiness: "+e.readiness+"\nConsent: not licensed"));
            Button done=button("Back to lab"); done.setOnClickListener(v->showLanding()); root.addView(done);
        }catch(Exception ex){
            Toast.makeText(this,"Could not save episode",Toast.LENGTH_LONG).show();
        }
    }

    private void base(String title,String subtitle){
        ScrollView sv=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(48)); root.setBackgroundColor(BG); sv.addView(root);
        root.addView(text("TRAINING DATA LAB",12,ACCENT,true));
        root.addView(text(title,28,Color.WHITE,true));
        root.addView(text(subtitle,13,MUTED,false));
        space(20); setContentView(sv);
    }

    private TextView badge(String s){TextView t=text(s,11,BG,true);t.setBackgroundColor(ACCENT);t.setPadding(dp(8),dp(5),dp(8),dp(5));return t;}
    private TextView card(String s){TextView t=text(s,14,Color.WHITE,false);t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));t.setLayoutParams(lp);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,0,0,dp(9));b.setLayoutParams(lp);return b;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(null,1);t.setLineSpacing(0,1.18f);return t;}
    private void space(int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));root.addView(s);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}

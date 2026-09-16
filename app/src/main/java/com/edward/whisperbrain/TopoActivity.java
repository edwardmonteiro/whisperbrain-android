package com.edward.whisperbrain;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Minimal Plaud-inspired launcher: Record, Live Coach, Library and Templates. */
public final class TopoActivity extends Activity {
    private static final int BG=Color.rgb(9,11,15), CARD=Color.rgb(20,23,29), INK=Color.rgb(242,244,248), MUTED=Color.rgb(151,158,172), ACCENT=Color.rgb(116,230,190), RED=Color.rgb(245,121,121);
    private LinearLayout root,library;
    private TextView liveStatus,timer,caption,context,tail;
    private ProgressBar meter;
    private Button record,coach,ask;
    private Spinner template;
    private Vault vault;
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean pendingCoach;

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);vault=new Vault(this);build();}
    @Override public void onResume(){super.onResume();TalkState.observer=this::render;render();main.post(tick);}
    @Override public void onPause(){TalkState.observer=null;main.removeCallbacks(tick);super.onPause();}

    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(30),dp(20),dp(50));scroll.addView(root);
        TextView brand=text("TOPO",14,ACCENT,true);brand.setLetterSpacing(.22f);root.addView(brand);
        root.addView(text("Conversation intelligence",30,INK,true));root.addView(text("Record. Understand. Respond better.",15,MUTED,false));gap(22);

        LinearLayout hero=card();liveStatus=text("Ready",15,MUTED,true);hero.addView(liveStatus);
        timer=text("00:00",42,INK,true);hero.addView(timer);meter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);meter.setMax(100);meter.setProgressTintList(ColorStateList.valueOf(ACCENT));meter.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(45,49,58)));hero.addView(meter,new LinearLayout.LayoutParams(-1,dp(5)));
        gap(hero,16);record=button("Record conversation",ACCENT,BG);hero.addView(record);coach=button("Live Coach",CARD,INK);hero.addView(coach);ask=button("Analyze now",Color.rgb(38,43,52),INK);hero.addView(ask);
        record.setOnClickListener(v->start(false));coach.setOnClickListener(v->start(true));ask.setOnClickListener(v->startService(new Intent(this,TopoService.class).setAction(TopoService.ASK)));

        LinearLayout live=card();live.addView(text("LIVE COACH",12,ACCENT,true));caption=text("Start Live Coach to see concise suggestions here.",22,INK,true);live.addView(caption);context=text("",15,MUTED,false);live.addView(context);gap(live,10);tail=text("",15,Color.rgb(203,208,218),false);tail.setMaxLines(8);live.addView(tail);

        LinearLayout t=card();t.addView(text("TEMPLATE",12,ACCENT,true));template=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,TopoTemplates.NAMES);template.setAdapter(adapter);t.addView(template);
        String saved=read("topo_template","Executive meeting");for(int i=0;i<TopoTemplates.NAMES.length;i++)if(TopoTemplates.NAMES[i].equals(saved))template.setSelection(i);
        t.addView(text("Controls what Topo should discover: decisions, actions, objections, risks, questions and more.",14,MUTED,false));

        LinearLayout controls=card();Button settings=button("OpenAI settings",Color.rgb(38,43,52),INK);controls.addView(settings);settings.setOnClickListener(v->settings());
        Button refresh=button("Refresh library",Color.rgb(38,43,52),INK);controls.addView(refresh);refresh.setOnClickListener(v->loadLibrary());
        controls.addView(text("Audio is always encrypted and recorded locally first. AI transcription and Live Coach run in parallel when connected.",13,MUTED,false));
        controls.addView(text("Get participant consent before recording.",13,MUTED,false));

        root.addView(text("LIBRARY",12,ACCENT,true));library=new LinearLayout(this);library.setOrientation(LinearLayout.VERTICAL);root.addView(library);loadLibrary();setContentView(scroll);
    }

    private void start(boolean coachMode){
        if(TalkState.active){startService(new Intent(this,TopoService.class).setAction(TopoService.STOP));return;}
        try{vault.put("topo_template",String.valueOf(template.getSelectedItem()));vault.put("save_transcript","true");vault.put("topo_realtime_model","gpt-realtime-2.1");vault.put("topo_text_model","gpt-5.6-sol");}
        catch(Exception e){toast("Could not save settings.");return;}
        pendingCoach=coachMode;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},91);else begin();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==91&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)begin();}
    private void begin(){
        try{String event=(pendingCoach?"Live Coach · ":"Recording · ")+new SimpleDateFormat("dd MMM HH:mm",new Locale("pt","BR")).format(new Date());String id=NotebookStore.get(this).createSession(event).getString("id");
            startForegroundService(new Intent(this,TopoService.class).setAction(TopoService.START).putExtra(TopoService.EXTRA_SESSION,id).putExtra(TopoService.EXTRA_COACH,pendingCoach));}
        catch(Exception e){toast("Could not create conversation.");}
    }

    private void render(){
        if(liveStatus==null)return;
        liveStatus.setText(TalkState.active?(TalkState.coachMode?"LIVE COACH · ":"RECORDING · ")+TalkState.status:"Ready");
        meter.setProgress(TalkState.level);record.setText(TalkState.active?"Stop":"Record conversation");record.setBackground(round(TalkState.active?RED:ACCENT,14));coach.setText(TalkState.active?"Stop":"Live Coach");ask.setEnabled(TalkState.active&&TalkState.connected&&!TalkState.requestInFlight);
        caption.setText(TalkState.tip.isEmpty()?(TalkState.coachMode&&TalkState.active?"Listening for a useful moment…":"Start Live Coach to see concise suggestions here."):TalkState.tip);
        context.setText(TalkState.active&&!TalkState.detail.isEmpty()?TalkState.detail:TalkState.context);tail.setText(TalkState.transcriptTail);
        if(TalkState.active){long s=(SystemClock.elapsedRealtime()-TalkState.started)/1000;timer.setText(String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60));}else timer.setText("00:00");
        if(!TalkState.active)loadLibrary();
    }
    private final Runnable tick=new Runnable(){public void run(){render();main.postDelayed(this,1000);}};

    private void loadLibrary(){
        if(library==null)return;library.removeAllViews();
        try{JSONArray sessions=NotebookStore.get(this).sessions();int shown=0;for(int i=0;i<sessions.length()&&shown<8;i++){JSONObject s=sessions.getJSONObject(i);JSONArray nodes=NotebookStore.get(this).nodes(s.getString("id"));boolean hasContent=false;for(int j=0;j<nodes.length();j++){String kind=nodes.getJSONObject(j).optString("kind");if("transcript".equals(kind)||"audio".equals(kind)){hasContent=true;break;}}if(!hasContent)continue;shown++;
                LinearLayout c=card(library);c.addView(text(s.optString("event","Conversation"),18,INK,true));c.addView(text(nodes.length()+" items",13,MUTED,false));Button analyze=button("Analyze with "+String.valueOf(template.getSelectedItem()),Color.rgb(38,43,52),INK);c.addView(analyze);String id=s.getString("id");analyze.setOnClickListener(v->analyze(id,analyze));}
            if(shown==0)library.addView(text("Your recorded conversations will appear here.",15,MUTED,false));
        }catch(Exception e){library.addView(text("Library unavailable.",15,MUTED,false));}
    }
    private void analyze(String id,Button b){String name=String.valueOf(template.getSelectedItem());b.setEnabled(false);b.setText("Analyzing…");try{vault.put("topo_template",name);TopoSummaryAi.analyze(this,id,name,(message,report)->{toast(message);b.setEnabled(true);b.setText("Analyze with "+name);loadLibrary();});}catch(Exception e){toast(e.getMessage());b.setEnabled(true);b.setText("Analyze with "+name);}}

    private void settings(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),0);
        body.addView(text("OpenAI API key",15,INK,true));EditText key=new EditText(this);key.setTextColor(INK);key.setHintTextColor(MUTED);key.setHint(read("api_key","").isEmpty()?"sk-…":"Key saved · leave blank to keep");key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);body.addView(key);
        body.addView(text("Realtime: gpt-realtime-2.1\nDeep analysis: gpt-5.6-sol",14,MUTED,false));
        new AlertDialog.Builder(this).setTitle("Topo · OpenAI").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{String value=key.getText().toString().trim();if(!value.isEmpty())vault.put("api_key",value);vault.put("topo_realtime_model","gpt-realtime-2.1");vault.put("topo_text_model","gpt-5.6-sol");toast("Saved locally.");}catch(Exception e){toast("Could not save.");}}).show();
    }

    private String read(String key,String fallback){try{return vault.get(key,fallback);}catch(Exception e){return fallback;}}
    private LinearLayout card(){LinearLayout c=card(root);gap(14);return c;}
    private LinearLayout card(LinearLayout parent){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(round(CARD,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(14));parent.addView(c,p);return c;}
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setLineSpacing(0,1.1f);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(0,dp(4),0,dp(4));return v;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(15);b.setAllCaps(false);b.setBackground(round(bg,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(8),0,0);b.setLayoutParams(p);return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private void gap(int h){View v=new View(this);root.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void gap(LinearLayout p,int h){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s==null?"":s,Toast.LENGTH_LONG).show();}
}

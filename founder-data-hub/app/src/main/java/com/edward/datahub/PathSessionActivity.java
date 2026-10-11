package com.edward.datahub;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;

public class PathSessionActivity extends Activity {
    private LinearLayout root;
    private final int BG=Color.rgb(11,13,16),CARD=Color.rgb(24,28,33),ACCENT=Color.rgb(232,255,91);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        render();
    }

    private void render(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(BG);
        TextView title=t("Path Capture",28,true);root.addView(title);

        PathSessionStore.ActiveSession active=PathSessionStore.active(this);
        root.addView(t(active==null?"No active session":"Capturing: "+active.goal,14,false));

        Button start=b("Start session");
        start.setOnClickListener(v->startSession());
        root.addView(start);

        Button stop=b("Stop session");
        stop.setOnClickListener(v->{PathSessionStore.stop(this);render();});
        root.addView(stop);

        Button access=b("Enable accessibility");
        access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(access);

        Button sessions=b("Recorded sessions");
        sessions.setOnClickListener(v->showSessions());
        root.addView(sessions);

        setContentView(root);
    }

    private void startSession(){
        EditText input=new EditText(this);
        input.setHint("Goal");
        new AlertDialog.Builder(this).setTitle("Session goal").setView(input)
                .setPositiveButton("Start",(d,w)->{
                    try{
                        String g=input.getText().toString().trim();
                        PathSessionStore.start(this,g.isEmpty()?"Unspecified goal":g);
                        render();
                    }catch(Exception ignored){}
                }).setNegativeButton("Cancel",null).show();
    }

    private void showSessions(){
        ArrayList<PathSessionStore.SessionInfo> list=PathSessionStore.list(this);
        if(list.isEmpty()){
            new AlertDialog.Builder(this).setMessage("No sessions yet").setPositiveButton("OK",null).show();
            return;
        }
        String[] names=new String[list.size()];
        for(int i=0;i<list.size();i++)names[i]=list.get(i).goal+" · "+list.get(i).eventCount+" events";
        new AlertDialog.Builder(this).setTitle("Sessions").setItems(names,(d,i)->share(list.get(i))).show();
    }

    private void share(PathSessionStore.SessionInfo s){
        try{
            File f=PathSessionStore.sessionFile(this,s.id);
            Uri u=FileProvider.getUriForFile(this,"com.edward.datahub.files",f);
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("application/x-ndjson");
            i.putExtra(Intent.EXTRA_STREAM,u);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Export session"));
        }catch(Exception ignored){}
    }

    private TextView t(String s,int size,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.WHITE);
        if(bold)v.setTypeface(null,1);return v;
    }
    private Button b(String s){
        Button x=new Button(this);x.setText(s);x.setTextColor(Color.WHITE);x.setBackgroundColor(CARD);return x;
    }
}

package com.edward.datahub;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.graphics.Color;\nimport android.database.Cursor;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class ActivityMonitorActivity extends Activity {
    private TextView live;
    private EventDb db;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refresh();h.postDelayed(this,1000);}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new EventDb(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(26),dp(22),dp(30));
        root.setBackgroundColor(Color.rgb(11,13,16));
        TextView title=t("Activity Monitor",28,Color.WHITE,true);root.addView(title);
        root.addView(t("Developer · live validation",13,Color.rgb(165,171,180),false));
        live=t("",18,Color.WHITE,false);live.setPadding(0,dp(28),0,0);root.addView(live);
        setContentView(root);
    }

    private void refresh(){
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        boolean screen=pm!=null&&pm.isInteractive();
        boolean unlocked=screen&&(km==null||!km.isDeviceLocked());

        String fg="-";
        if(screen&&unlocked){
            UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
            long now=System.currentTimeMillis();
            UsageEvents ue=usm==null?null:usm.queryEvents(now-15000,now);
            if(ue!=null){
                UsageEvents.Event e=new UsageEvents.Event(); long latest=0;
                while(ue.hasNextEvent()){ue.getNextEvent(e);if(e.getEventType()==UsageEvents.Event.ACTIVITY_RESUMED&&e.getTimeStamp()>latest){latest=e.getTimeStamp();fg=e.getPackageName();}}
            }
        }

        long now=System.currentTimeMillis(), start=EventDb.DayBounds.startOfToday();
        Cursor p=db.phoneSessionsBetween(start,now+1);
        int switches=0; long phoneStart=0;
        try{while(p.moveToNext()){switches+=p.getInt(6);phoneStart=p.getLong(1);}}finally{p.close();}

        live.setText(
                "Screen\n"+(screen?"ON":"OFF")+"\n\n"+
                "Device\n"+(unlocked?"UNLOCKED":"LOCKED")+"\n\n"+
                "Foreground\n"+fg+"\n\n"+
                "Phone session\n"+(phoneStart>0?fmt((now-phoneStart)/1000):"-")+"\n\n"+
                "Context switches today\n"+switches
        );
    }

    @Override protected void onResume(){super.onResume();h.post(tick);}
    @Override protected void onPause(){h.removeCallbacks(tick);super.onPause();}

    private String fmt(long s){return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);}
    private TextView t(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(null,1);return v;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}

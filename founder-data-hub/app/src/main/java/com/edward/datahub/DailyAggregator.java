package com.edward.datahub;

import android.content.ContentValues;
import android.database.Cursor;
import java.util.*;

public final class DailyAggregator {
    private DailyAggregator() {}

    public static void recompute(EventDb db,long anyTs) {
        long start=EventDb.DayBounds.startOf(anyTs);
        long end=EventDb.DayBounds.endOf(anyTs);
        String key=EventDb.DayBounds.key(anyTs);

        long screen=0,longestApp=0;
        int appCount=0,shortSessions=0,focused=0;
        Set<String> packages=new HashSet<>();
        Map<String,AppAgg> apps=new HashMap<>();

        Cursor a=db.appSessionsBetween(start,end);
        try {
            while(a.moveToNext()) {
                long s=Math.max(start,a.getLong(1)), e=Math.min(end,a.getLong(2));
                long dur=Math.max(0,(e-s)/1000);
                String pkg=a.getString(4), name=a.getString(5), cat=a.getString(8), interaction=a.getString(7);
                if(!"human".equals(interaction) || a.getInt(6)!=1) continue;
                screen+=dur; appCount++; longestApp=Math.max(longestApp,dur);
                if(dur<60) shortSessions++;
                if(dur>=10*60) focused++;
                packages.add(pkg);
                AppAgg x=apps.get(pkg);
                if(x==null){x=new AppAgg(pkg,name,cat);apps.put(pkg,x);}
                x.total+=dur; x.count++;
            }
        } finally { a.close(); }

        int phoneCount=0,switches=0;
        long phoneTotal=0,longestPhone=0,first=0,last=0,longestFree=0,prevEnd=start;
        Cursor p=db.phoneSessionsBetween(start,end);
        try {
            while(p.moveToNext()) {
                long s=Math.max(start,p.getLong(1)), e=Math.min(end,p.getLong(2));
                long dur=Math.max(0,(e-s)/1000);
                if(phoneCount==0) first=s;
                last=Math.max(last,e);
                if(s>prevEnd) longestFree=Math.max(longestFree,(s-prevEnd)/1000);
                prevEnd=Math.max(prevEnd,e);
                phoneCount++; phoneTotal+=dur; longestPhone=Math.max(longestPhone,dur); switches+=p.getInt(6);
            }
        } finally { p.close(); }
        if(prevEnd<end) longestFree=Math.max(longestFree,(end-prevEnd)/1000);

        int unlocks=0;
        Cursor ev=db.eventsBetween(start,end);
        try { while(ev.moveToNext()) if("DEVICE_UNLOCK".equals(ev.getString(2))) unlocks++; }
        finally { ev.close(); }

        ContentValues d=new ContentValues();
        d.put("screen_time_seconds",screen);
        d.put("phone_sessions",phoneCount);
        d.put("device_unlocks",unlocks);
        d.put("average_session_seconds",phoneCount==0?0:(double)phoneTotal/phoneCount);
        d.put("longest_session_seconds",longestPhone);
        d.put("foreground_apps_count",packages.size());
        d.put("context_switches",switches);
        d.put("context_switches_per_hour",screen==0?0:(switches/(screen/3600.0)));
        if(first>0)d.put("first_phone_use",first);
        if(last>0)d.put("last_phone_use",last);
        d.put("longest_phone_free_seconds",longestFree);
        d.put("focused_blocks",focused);
        d.put("short_sessions",shortSessions);
        d.put("longest_uninterrupted_seconds",longestApp);
        db.replaceDailySummary(key,d);

        db.clearDailyAppSummary(key);
        for(AppAgg x:apps.values()) {
            ContentValues v=new ContentValues();
            v.put("date_key",key); v.put("package_name",x.pkg); v.put("app_name",x.name); v.put("category",x.category);
            v.put("total_duration_seconds",x.total); v.put("number_of_sessions",x.count);
            v.put("average_session_seconds",x.count==0?0:(double)x.total/x.count);
            v.put("screen_time_percentage",screen==0?0:(x.total*100.0/screen));
            db.putDailyAppSummary(v);
        }
    }

    private static final class AppAgg {
        final String pkg,name,category; long total=0; int count=0;
        AppAgg(String p,String n,String c){pkg=p;name=n;category=c;}
    }
}

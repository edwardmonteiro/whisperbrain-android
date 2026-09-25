package com.edward.datahub;

import android.database.Cursor;
import java.text.*;
import java.util.*;

public final class DailySummaryRules {
    private DailySummaryRules() {}

    public static String generate(EventDb db,long ts) {
        String key=EventDb.DayBounds.key(ts);
        Cursor d=db.dailySummary(key);
        if(!d.moveToFirst()){ d.close(); return "No human phone activity has been reconstructed yet."; }
        long screen=d.getLong(d.getColumnIndexOrThrow("screen_time_seconds"));
        int sessions=d.getInt(d.getColumnIndexOrThrow("phone_sessions"));
        int switches=d.getInt(d.getColumnIndexOrThrow("context_switches"));
        long longest=d.getLong(d.getColumnIndexOrThrow("longest_session_seconds"));
        d.close();

        String topCat="Unknown"; double topPct=0;
        Map<String,Long> byCat=new HashMap<>();
        Cursor a=db.dailyApps(key);
        try {
            while(a.moveToNext()) {
                String c=a.getString(2); long dur=a.getLong(3);
                byCat.put(c,byCat.getOrDefault(c,0L)+dur);
            }
        } finally { a.close(); }
        for(Map.Entry<String,Long> e:byCat.entrySet()) {
            double pct=screen==0?0:e.getValue()*100.0/screen;
            if(pct>topPct){topPct=pct;topCat=e.getKey();}
        }

        String concentration=sessions<=3?"a few concentrated periods":sessions<=12?"several distinct periods":"many short periods";
        return "Your phone use was spread across "+concentration+".\n" +
                topCat+" represented "+Math.round(topPct)+"% of active screen time.\n" +
                "The longest uninterrupted phone session was "+formatDuration(longest)+".\n" +
                "You changed applications "+switches+" times.";
    }

    public static String periodName(int hour) {
        if(hour>=5&&hour<=11)return "Morning";
        if(hour>=12&&hour<=17)return "Afternoon";
        if(hour>=18&&hour<=22)return "Evening";
        return "Night";
    }

    public static String formatDuration(long sec) {
        if(sec<60)return sec+"s";
        long m=sec/60,h=m/60; m%=60;
        return h>0?h+"h "+m+"m":m+"m";
    }
}

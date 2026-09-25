package com.edward.datahub;

import java.util.List;

public final class UsageMetricRules {
    private UsageMetricRules(){}

    public static boolean countsAsScreenTime(String activityOrigin,boolean screenActive,String eventType){
        return "human".equals(activityOrigin) && screenActive &&
                ("APP_FOREGROUND".equals(eventType)||"APP_BACKGROUND".equals(eventType)||"APP_SESSION".equals(eventType));
    }

    public static String classifyEvent(String eventType,boolean screenOn,boolean unlocked,boolean foreground){
        if("DEVICE_UNLOCK".equals(eventType)||"USER_PRESENT".equals(eventType)) return "human";
        if("SCREEN_ON".equals(eventType)||"SCREEN_OFF".equals(eventType)||"DEVICE_LOCK".equals(eventType)) return "system";
        if(("NOTIFICATION_RECEIVED".equals(eventType)||"DOWNLOAD".equals(eventType))&&!unlocked) return "background";
        if(foreground&&screenOn&&unlocked) return "human";
        if(!screenOn||!unlocked) return "background";
        return "unknown";
    }

    public static int contextSwitches(List<String> foregroundSequence){
        if(foregroundSequence==null||foregroundSequence.size()<2)return 0;
        int switches=0; String last=null;
        for(String pkg:foregroundSequence){
            if(pkg==null)continue;
            if(last!=null&&!pkg.equals(last))switches++;
            last=pkg;
        }
        return switches;
    }

    public static long humanScreenSeconds(List<Interval> sessions){
        long total=0;
        if(sessions==null)return 0;
        for(Interval s:sessions)if(s.screenActive&&"human".equals(s.origin)&&s.endMs>s.startMs)total+=(s.endMs-s.startMs)/1000;
        return total;
    }

    public static final class Interval{
        public final long startMs,endMs; public final boolean screenActive; public final String origin;
        public Interval(long startMs,long endMs,boolean screenActive,String origin){
            this.startMs=startMs;this.endMs=endMs;this.screenActive=screenActive;this.origin=origin;
        }
    }
}

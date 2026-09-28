package com.edward.datahub;

import java.util.*;

public class ActivityClusterer {
    public ArrayList<ActivityCluster> cluster(List<NormalizedSignal> signals){
        ArrayList<ActivityCluster> out=new ArrayList<>();
        for(NormalizedSignal s:signals){
            ActivityCluster target=null;
            if(!out.isEmpty()){
                ActivityCluster last=out.get(out.size()-1);
                long gap=s.timestamp-last.endTs;
                long allowed=clusterWindow(last,s);
                if(gap<=allowed&&compatible(last,s))target=last;
            }
            if(target==null){
                target=new ActivityCluster();
                target.startTs=s.timestamp;
                target.endTs=s.endTimestamp;
                target.dominantType=s.semanticType;
                target.primaryApp=s.appName;
                out.add(target);
            }
            target.signals.add(s);
            target.endTs=Math.max(target.endTs,s.endTimestamp);
            if(priority(s.semanticType)>priority(target.dominantType)){
                target.dominantType=s.semanticType;
                target.primaryApp=s.appName;
            }
        }
        return out;
    }

    private long clusterWindow(ActivityCluster c,NormalizedSignal s){
        String t=s.semanticType;
        if(t.contains("ALARM"))return 12*60*1000L;
        if(t.contains("CAMERA"))return 2*60*1000L;
        if(t.contains("CALL"))return 2*60*1000L;
        if(t.contains("MESSAGE"))return 90*1000L;
        if(t.contains("CAR_CONNECTION"))return 2*60*1000L;
        return 45*1000L;
    }

    private boolean compatible(ActivityCluster c,NormalizedSignal s){
        if(c.dominantType.contains("CAMERA")||s.semanticType.contains("CAMERA"))
            return "Camera".equals(c.primaryApp)||"Camera".equals(s.appName);
        if(c.dominantType.contains("ALARM")||s.semanticType.contains("ALARM"))
            return "Clock".equals(c.primaryApp)||"Clock".equals(s.appName);
        if(c.dominantType.contains("CALL")||s.semanticType.contains("CALL"))
            return "Phone".equals(c.primaryApp)||"Phone".equals(s.appName)||"WhatsApp".equals(c.primaryApp)||"WhatsApp".equals(s.appName);
        if(c.dominantType.contains("MESSAGE")||s.semanticType.contains("MESSAGE"))
            return "WhatsApp".equals(c.primaryApp)&&"WhatsApp".equals(s.appName);
        if(c.dominantType.contains("CAR_CONNECTION")||s.semanticType.contains("CAR_CONNECTION"))
            return "Android Auto".equals(c.primaryApp)||"Android Auto".equals(s.appName);
        return Objects.equals(c.primaryApp,s.appName)||Objects.equals(c.dominantType,s.semanticType);
    }

    private int priority(String t){
        if(t==null)return 0;
        if(t.contains("MISSED_CALL"))return 100;
        if(t.contains("CALL"))return 90;
        if(t.contains("ALARM"))return 80;
        if(t.contains("CAMERA"))return 75;
        if(t.contains("SCREENSHOT"))return 70;
        if(t.contains("CAR_CONNECTION"))return 65;
        if(t.contains("MESSAGE"))return 60;
        return 10;
    }
}

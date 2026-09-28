package com.edward.datahub;

import java.util.*;

public class EventDeduplicator {
    private final Map<String,Long> windowsMs=new HashMap<>();

    public EventDeduplicator(){
        windowsMs.put("NOTIFICATION_SIGNAL",5000L);
        windowsMs.put("MESSAGE_SIGNAL",90000L);
        windowsMs.put("CAMERA_SIGNAL",30000L);
        windowsMs.put("CALL_SIGNAL",60000L);
        windowsMs.put("MISSED_CALL_SIGNAL",60000L);
        windowsMs.put("CAR_CONNECTION_SIGNAL",60000L);
        windowsMs.put("DOWNLOAD_SIGNAL",30000L);
        windowsMs.put("SCREENSHOT_SIGNAL",5000L);
        windowsMs.put("APP_FOREGROUND_SIGNAL",3000L);
        windowsMs.put("APP_BACKGROUND_SIGNAL",3000L);
    }

    public ArrayList<NormalizedSignal> deduplicate(List<NormalizedSignal> input){
        ArrayList<NormalizedSignal> out=new ArrayList<>();
        for(NormalizedSignal s:input){
            if(out.isEmpty()){out.add(s);continue;}
            NormalizedSignal last=out.get(out.size()-1);
            long window=windowsMs.getOrDefault(s.semanticType,2000L);
            boolean sameType=s.semanticType.equals(last.semanticType);
            boolean sameSource=Objects.equals(s.sourcePackage,last.sourcePackage);
            boolean close=s.timestamp-last.endTimestamp<=window;
            if(sameType&&sameSource&&close){
                last.endTimestamp=Math.max(last.endTimestamp,s.endTimestamp);
                last.rawEventIds.addAll(s.rawEventIds);
                if((last.detail==null||last.detail.isEmpty())&&s.detail!=null)last.detail=s.detail;
            }else{
                out.add(s);
            }
        }
        return out;
    }
}

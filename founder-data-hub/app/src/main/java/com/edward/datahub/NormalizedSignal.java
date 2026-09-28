package com.edward.datahub;

import java.util.*;

public class NormalizedSignal {
    public final ArrayList<Long> rawEventIds=new ArrayList<>();
    public long timestamp;
    public long endTimestamp;
    public String semanticType;
    public String sourcePackage;
    public String appName;
    public String category;
    public String detail;
    public boolean screenActive;
    public String activityOrigin;

    public NormalizedSignal(long timestamp,String semanticType,String sourcePackage,String appName,String category,String detail,boolean screenActive,String activityOrigin,long rawEventId){
        this.timestamp=timestamp;
        this.endTimestamp=timestamp;
        this.semanticType=semanticType;
        this.sourcePackage=sourcePackage;
        this.appName=appName;
        this.category=category;
        this.detail=detail;
        this.screenActive=screenActive;
        this.activityOrigin=activityOrigin;
        this.rawEventIds.add(rawEventId);
    }

    public int evidenceCount(){return rawEventIds.size();}
}

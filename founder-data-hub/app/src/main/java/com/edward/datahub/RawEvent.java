package com.edward.datahub;

public class RawEvent {
    public final long id;
    public final long timestamp;
    public final String eventType;
    public final String source;
    public final String detail;
    public final boolean screenActive;
    public final String activityOrigin;

    public RawEvent(long id,long timestamp,String eventType,String source,String detail,boolean screenActive,String activityOrigin){
        this.id=id;
        this.timestamp=timestamp;
        this.eventType=eventType==null?"":eventType;
        this.source=source==null?"":source;
        this.detail=detail==null?"":detail;
        this.screenActive=screenActive;
        this.activityOrigin=activityOrigin==null?"unknown":activityOrigin;
    }
}

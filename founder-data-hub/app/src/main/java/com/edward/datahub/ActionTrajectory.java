package com.edward.datahub;

import java.util.*;

public class ActionTrajectory {
    public String trajectoryId=UUID.randomUUID().toString();
    public long startTs;
    public long endTs;
    public String goal="";
    public String outcome="";
    public int satisfaction=0;
    public String consentScope="not_licensed";
    public double qualityScore=0;
    public final ArrayList<Step> steps=new ArrayList<>();

    public static class Step {
        public long timestamp;
        public String state;
        public String action;
        public String nextState;
        public String appName;
        public String packageName;

        public Step(long timestamp,String state,String action,String nextState,String appName,String packageName){
            this.timestamp=timestamp;
            this.state=state;
            this.action=action;
            this.nextState=nextState;
            this.appName=appName;
            this.packageName=packageName;
        }
    }
}

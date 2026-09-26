package com.edward.datahub;

import org.json.*;
import java.util.*;

public class HumanEpisode {
    public String episodeId;
    public String phoneSessionId;
    public long startTs;
    public long endTs;
    public String intent;
    public String outcome;
    public int satisfaction;
    public double humanConfidence;
    public int qualityScore;
    public String readiness;
    public String consentScope;
    public boolean demo;
    public final ArrayList<Step> steps=new ArrayList<>();

    public long durationSeconds(){return Math.max(0,(endTs-startTs)/1000);}
    public int contextSwitches(){return Math.max(0,steps.size()-1);}
    public JSONArray sequenceJson() throws JSONException{
        JSONArray a=new JSONArray();
        for(Step s:steps){
            JSONObject o=new JSONObject();
            o.put("app_name",s.appName); o.put("package_name",s.packageName); o.put("category",s.category);
            o.put("duration_seconds",s.durationSeconds);
            a.put(o);
        }
        return a;
    }

    public static class Step{
        public final String packageName,appName,category; public final long durationSeconds;
        public Step(String p,String a,String c,long d){packageName=p;appName=a;category=c;durationSeconds=d;}
    }
}

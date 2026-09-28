package com.edward.datahub;

import java.util.*;

public class InferredEpisode {
    public String id=UUID.randomUUID().toString();
    public long startTs;
    public long endTs;
    public String type;
    public String subtype="";
    public String title;
    public String description;
    public String primaryApp;
    public final LinkedHashSet<String> relatedApps=new LinkedHashSet<>();
    public double confidence;
    public int evidenceCount;
    public final LinkedHashSet<Long> rawEventIds=new LinkedHashSet<>();
    public double screenActiveRatio;
    public double humanProbability;
    public String inferenceVersion="human-event-v2";
    public String confidenceLabel;
    public String userCorrection="";
    public String userFeedback="";

    public long durationSeconds(){return Math.max(0,(endTs-startTs)/1000);}
}

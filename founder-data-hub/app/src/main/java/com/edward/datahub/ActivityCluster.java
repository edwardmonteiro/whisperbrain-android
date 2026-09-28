package com.edward.datahub;

import java.util.*;

public class ActivityCluster {
    public String clusterId=UUID.randomUUID().toString();
    public long startTs;
    public long endTs;
    public String dominantType;
    public String primaryApp;
    public final ArrayList<NormalizedSignal> signals=new ArrayList<>();

    public int evidenceCount(){
        int n=0;
        for(NormalizedSignal s:signals)n+=s.evidenceCount();
        return n;
    }

    public double screenActiveRatio(){
        if(signals.isEmpty())return 0;
        int n=0;
        for(NormalizedSignal s:signals)if(s.screenActive)n++;
        return n/(double)signals.size();
    }

    public Set<Long> rawEventIds(){
        LinkedHashSet<Long> ids=new LinkedHashSet<>();
        for(NormalizedSignal s:signals)ids.addAll(s.rawEventIds);
        return ids;
    }
}

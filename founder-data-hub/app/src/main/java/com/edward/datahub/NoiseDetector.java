package com.edward.datahub;

public class NoiseDetector {
    public static class Metrics{
        public int rawEventCount;
        public int normalizedSignalCount;
        public int clusterCount;
        public int humanEpisodeCount;
        public int unknownEpisodeCount;
        public double compressionRatio;
        public double highConfidenceCoverage;
    }

    public Metrics compute(int raw,int normalized,int clusters,java.util.List<InferredEpisode> episodes){
        Metrics m=new Metrics();
        m.rawEventCount=raw;
        m.normalizedSignalCount=normalized;
        m.clusterCount=clusters;
        m.humanEpisodeCount=episodes.size();
        int unknown=0,high=0;
        for(InferredEpisode e:episodes){
            if("UNKNOWN_ACTIVITY".equals(e.type))unknown++;
            if(e.confidence>=0.75)high++;
        }
        m.unknownEpisodeCount=unknown;
        m.compressionRatio=episodes.isEmpty()?0:raw/(double)episodes.size();
        m.highConfidenceCoverage=episodes.isEmpty()?0:high*100.0/episodes.size();
        return m;
    }
}

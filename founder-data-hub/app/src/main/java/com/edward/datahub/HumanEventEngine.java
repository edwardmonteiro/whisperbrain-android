package com.edward.datahub;

import java.util.*;

public class HumanEventEngine {
    public static final String VERSION="human-event-v2";

    private final EventDb db;
    private final EventNormalizer normalizer=new EventNormalizer();
    private final EventDeduplicator deduplicator=new EventDeduplicator();
    private final ActivityClusterer clusterer=new ActivityClusterer();
    private final HumanEpisodeClassifier classifier=new HumanEpisodeClassifier();
    private final NoiseDetector noiseDetector=new NoiseDetector();

    public static class Result{
        public final ArrayList<RawEvent> rawEvents;
        public final ArrayList<NormalizedSignal> normalizedSignals;
        public final ArrayList<ActivityCluster> clusters;
        public final ArrayList<InferredEpisode> episodes;
        public final NoiseDetector.Metrics metrics;

        Result(ArrayList<RawEvent> raw,ArrayList<NormalizedSignal> norm,ArrayList<ActivityCluster> clusters,
               ArrayList<InferredEpisode> episodes,NoiseDetector.Metrics metrics){
            this.rawEvents=raw;this.normalizedSignals=norm;this.clusters=clusters;this.episodes=episodes;this.metrics=metrics;
        }
    }

    public HumanEventEngine(EventDb db){this.db=db;}

    public Result rebuild(long start,long end){
        ArrayList<RawEvent> raw=db.rawEventsBetween(start,end);
        ArrayList<NormalizedSignal> normalized=new ArrayList<>();
        for(RawEvent e:raw)normalized.add(normalizer.normalize(e));
        normalized.sort(Comparator.comparingLong(s->s.timestamp));
        ArrayList<NormalizedSignal> unique=deduplicator.deduplicate(normalized);
        ArrayList<ActivityCluster> clusters=clusterer.cluster(unique);
        ArrayList<InferredEpisode> episodes=new ArrayList<>();
        for(ActivityCluster c:clusters){
            InferredEpisode e=classifier.classify(c);
            e.inferenceVersion=VERSION;
            episodes.add(e);
        }
        db.replaceInferredEpisodes(episodes,start,end);
        NoiseDetector.Metrics metrics=noiseDetector.compute(raw.size(),unique.size(),clusters.size(),episodes);
        return new Result(raw,unique,clusters,episodes,metrics);
    }
}

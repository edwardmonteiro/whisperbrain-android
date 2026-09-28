package com.edward.datahub;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HumanEventEngineRulesTest {
    private NormalizedSignal signal(long ts,String type,String app,String source,long id){
        return new NormalizedSignal(ts,type,source,app,"Unknown","",true,"human",id);
    }

    @Test public void alarmSequenceClustersCoherently(){
        ArrayList<NormalizedSignal> xs=new ArrayList<>();
        xs.add(signal(0,"ALARM_SIGNAL","Clock","com.sec.android.app.clockpackage",1));
        xs.add(signal(5*60*1000L,"ALARM_SIGNAL","Clock","com.sec.android.app.clockpackage",2));
        xs.add(signal(10*60*1000L,"ALARM_SIGNAL","Clock","com.sec.android.app.clockpackage",3));
        ArrayList<ActivityCluster> c=new ActivityClusterer().cluster(xs);
        assertEquals(1,c.size());
        InferredEpisode e=new HumanEpisodeClassifier().classify(c.get(0));
        assertEquals("ALARM",e.type);
    }

    @Test public void missedCallSignalsBecomeOneMissedCall(){
        ArrayList<NormalizedSignal> xs=new ArrayList<>();
        xs.add(signal(0,"CALL_SIGNAL","Phone","com.samsung.android.incallui",1));
        xs.add(signal(2000,"CALL_SIGNAL","Phone","com.samsung.android.incallui",2));
        xs.add(signal(4000,"CALL_SIGNAL","Phone","com.samsung.android.incallui",3));
        xs.add(signal(6000,"MISSED_CALL_SIGNAL","Phone","com.samsung.android.dialer",4));
        xs.add(signal(7000,"MISSED_CALL_SIGNAL","Phone","com.samsung.android.dialer",5));
        ArrayList<NormalizedSignal> dedup=new EventDeduplicator().deduplicate(xs);
        ArrayList<ActivityCluster> c=new ActivityClusterer().cluster(dedup);
        assertEquals(1,c.size());
        InferredEpisode e=new HumanEpisodeClassifier().classify(c.get(0));
        assertEquals("MISSED_CALL",e.type);
        assertTrue(e.confidence>=0.99);
    }

    @Test public void cameraStormCompressesToOneEpisode(){
        ArrayList<NormalizedSignal> xs=new ArrayList<>();
        for(int i=0;i<589;i++){
            long ts=i*(21*60*1000L/589);
            xs.add(signal(ts,"CAMERA_SIGNAL","Camera","com.sec.android.app.camera",i+1));
        }
        ArrayList<NormalizedSignal> dedup=new EventDeduplicator().deduplicate(xs);
        ArrayList<ActivityCluster> c=new ActivityClusterer().cluster(dedup);
        assertEquals(1,c.size());
        InferredEpisode e=new HumanEpisodeClassifier().classify(c.get(0));
        assertEquals("CAMERA_USE",e.type);
        assertEquals(589,e.evidenceCount);
    }

    @Test public void whatsappBurstBecomesMessagingEpisode(){
        ArrayList<NormalizedSignal> xs=new ArrayList<>();
        for(int i=0;i<12;i++)xs.add(signal(i*5000L,"MESSAGE_SIGNAL","WhatsApp","com.whatsapp",i+1));
        ArrayList<ActivityCluster> c=new ActivityClusterer().cluster(new EventDeduplicator().deduplicate(xs));
        assertEquals(1,c.size());
        assertEquals("MESSAGING",new HumanEpisodeClassifier().classify(c.get(0)).type);
    }

    @Test public void androidAutoDoesNotClaimDriving(){
        ArrayList<NormalizedSignal> xs=new ArrayList<>();
        xs.add(signal(0,"CAR_CONNECTION_SIGNAL","Android Auto","com.google.android.projection.gearhead",1));
        ActivityCluster c=new ActivityClusterer().cluster(xs).get(0);
        InferredEpisode e=new HumanEpisodeClassifier().classify(c);
        assertEquals("CAR_CONNECTION",e.type);
        assertFalse(e.title.toLowerCase(Locale.US).contains("driving"));
        assertTrue(e.description.toLowerCase(Locale.US).contains("does not by itself prove driving"));
    }
}

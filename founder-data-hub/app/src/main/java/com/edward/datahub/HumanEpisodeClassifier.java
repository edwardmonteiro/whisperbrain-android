package com.edward.datahub;

import java.util.*;

public class HumanEpisodeClassifier {
    private final ConfidenceEngine confidence=new ConfidenceEngine();

    public InferredEpisode classify(ActivityCluster c){
        InferredEpisode e=new InferredEpisode();
        e.startTs=c.startTs;
        e.endTs=Math.max(c.endTs,c.startTs+1000);
        e.primaryApp=c.primaryApp==null?"Unknown":c.primaryApp;
        for(NormalizedSignal s:c.signals){
            e.relatedApps.add(s.appName);
            e.rawEventIds.addAll(s.rawEventIds);
        }
        e.evidenceCount=c.evidenceCount();
        e.screenActiveRatio=c.screenActiveRatio();
        e.type=type(c);
        e.title=title(e.type,c);
        e.description=description(e.type,c);
        e.confidence=confidence.confidence(c,e.type);
        e.humanProbability=confidence.humanProbability(c,e.type);
        e.confidenceLabel=confidence.label(e.confidence);
        return e;
    }

    private String type(ActivityCluster c){
        if(has(c,"MISSED_CALL_SIGNAL"))return "MISSED_CALL";
        if(has(c,"CALL_SIGNAL"))return "PHONE_CALL";
        if(has(c,"ALARM_SIGNAL"))return "ALARM";
        if(has(c,"CAMERA_SIGNAL"))return "CAMERA_USE";
        if(has(c,"SCREENSHOT_SIGNAL"))return "SCREENSHOT";
        if(has(c,"CAR_CONNECTION_SIGNAL"))return "CAR_CONNECTION";
        if(has(c,"MESSAGE_SIGNAL"))return "MESSAGING";
        if(has(c,"PAYMENT_SIGNAL"))return "PAYMENT_ACTIVITY";
        if(has(c,"NAVIGATION_SIGNAL"))return "NAVIGATION";
        if(has(c,"MEDIA_SIGNAL"))return "MEDIA";
        if(has(c,"DOWNLOAD_SIGNAL"))return "DOWNLOAD";
        if(has(c,"APP_FOREGROUND_SIGNAL"))return "APP_USE";
        if(has(c,"UNLOCK_SIGNAL"))return "PHONE_USE";
        return "UNKNOWN_ACTIVITY";
    }

    private String title(String type,ActivityCluster c){
        switch(type){
            case "MISSED_CALL": return "Missed phone call";
            case "PHONE_CALL": return "Phone call activity";
            case "ALARM": return c.evidenceCount()>1?"Alarm sequence":"Alarm activity";
            case "CAMERA_USE": return "Camera activity";
            case "SCREENSHOT": return "Screenshot";
            case "CAR_CONNECTION": return "Android Auto connected";
            case "MESSAGING": return "WhatsApp activity burst";
            case "PAYMENT_ACTIVITY": return "Payment activity";
            case "NAVIGATION": return "Navigation activity";
            case "MEDIA": return "Media activity";
            case "DOWNLOAD": return "Download activity";
            case "APP_USE": return c.primaryApp+" use";
            case "PHONE_USE": return "Phone active";
            default: return "Unknown activity";
        }
    }

    private String description(String type,ActivityCluster c){
        if("CAMERA_USE".equals(type)&&c.endTs>c.startTs)
            return "Sustained camera-related activity reconstructed from "+c.evidenceCount()+" signals.";
        if("CAR_CONNECTION".equals(type))
            return "Android Auto connection detected. This does not by itself prove driving.";
        if("MESSAGING".equals(type))
            return "Messaging-related activity burst inferred from metadata only.";
        if("MISSED_CALL".equals(type))
            return "Missed-call metadata was observed.";
        return "Episode reconstructed from "+c.evidenceCount()+" technical signals.";
    }

    private boolean has(ActivityCluster c,String type){
        for(NormalizedSignal s:c.signals)if(type.equals(s.semanticType))return true;
        return false;
    }
}

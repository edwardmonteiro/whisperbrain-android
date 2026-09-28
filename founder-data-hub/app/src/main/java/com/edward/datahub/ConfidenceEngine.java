package com.edward.datahub;

public class ConfidenceEngine {
    public double confidence(ActivityCluster c,String episodeType){
        double score=0.45;
        int evidence=c.evidenceCount();
        score+=Math.min(0.20,evidence*0.02);
        score+=Math.min(0.15,c.screenActiveRatio()*0.15);

        if("MISSED_CALL".equals(episodeType)&&contains(c,"MISSED_CALL_SIGNAL"))score=0.99;
        else if("ALARM".equals(episodeType)&&"Clock".equals(c.primaryApp))score=Math.max(score,0.92);
        else if("CAMERA_USE".equals(episodeType)&&"Camera".equals(c.primaryApp))score=Math.max(score,0.88);
        else if("SCREENSHOT".equals(episodeType)&&"Screenshot".equals(c.primaryApp))score=Math.max(score,0.95);
        else if("CAR_CONNECTION".equals(episodeType)&&"Android Auto".equals(c.primaryApp))score=Math.max(score,0.82);
        else if("MESSAGING".equals(episodeType)&&"WhatsApp".equals(c.primaryApp))score=Math.max(score,0.78);
        else if("PHONE_CALL".equals(episodeType)&&"Phone".equals(c.primaryApp))score=Math.max(score,0.90);

        return Math.min(0.99,score);
    }

    public double humanProbability(ActivityCluster c,String episodeType){
        if("DOWNLOAD".equals(episodeType))return 0.15;
        if("CAR_CONNECTION".equals(episodeType))return 0.55;
        if("IDLE".equals(episodeType))return 0.10;
        if(c.screenActiveRatio()>=0.5)return 0.90;
        if("MISSED_CALL".equals(episodeType)||"ALARM".equals(episodeType))return 0.80;
        if("MESSAGING".equals(episodeType))return 0.70;
        return 0.50;
    }

    public String label(double c){
        int p=(int)Math.round(c*100);
        if(p>=90)return "Observed / Very High Confidence";
        if(p>=75)return "Strong Inference";
        if(p>=50)return "Probable";
        if(p>=25)return "Weak Signal";
        return "Unknown";
    }

    private boolean contains(ActivityCluster c,String type){
        for(NormalizedSignal s:c.signals)if(type.equals(s.semanticType))return true;
        return false;
    }
}

package com.edward.datahub;

import java.util.*;

public class AppIdentityResolver {
    private static final Map<String,String> KNOWN=new HashMap<>();
    static{
        KNOWN.put("com.whatsapp","WhatsApp");
        KNOWN.put("com.sec.android.app.camera","Camera");
        KNOWN.put("com.sec.android.app.clockpackage","Clock");
        KNOWN.put("com.samsung.android.dialer","Phone");
        KNOWN.put("com.samsung.android.incallui","Phone");
        KNOWN.put("com.android.incallui","Phone");
        KNOWN.put("com.google.android.projection.gearhead","Android Auto");
        KNOWN.put("com.samsung.android.app.smartcapture","Screenshot");
        KNOWN.put("com.google.android.apps.walletnfcrel","Google Wallet");
        KNOWN.put("com.android.providers.downloads","Download Manager");
        KNOWN.put("com.google.android.apps.maps","Google Maps");
        KNOWN.put("com.waze","Waze");
        KNOWN.put("com.spotify.music","Spotify");
        KNOWN.put("com.google.android.youtube","YouTube");
        KNOWN.put("com.openai.chatgpt","ChatGPT");
    }

    public String resolve(String source){
        if(source==null||source.isEmpty())return "System";
        String exact=KNOWN.get(source);
        if(exact!=null)return exact;
        String s=source.toLowerCase(Locale.US);
        if(s.contains("whatsapp"))return "WhatsApp";
        if(s.contains("camera"))return "Camera";
        if(s.contains("clock"))return "Clock";
        if(s.contains("dialer")||s.contains("incall"))return "Phone";
        if(s.contains("gearhead")||s.contains("androidauto"))return "Android Auto";
        if(s.contains("smartcapture")||s.contains("screenshot"))return "Screenshot";
        if(s.contains("wallet"))return "Google Wallet";
        if(s.contains("download"))return "Download Manager";
        if(s.contains("maps"))return "Google Maps";
        if(s.contains("waze"))return "Waze";
        if(s.contains("spotify"))return "Spotify";
        if(s.contains("youtube"))return "YouTube";
        int last=source.lastIndexOf('.');
        return last>=0&&last<source.length()-1?source.substring(last+1):source;
    }
}

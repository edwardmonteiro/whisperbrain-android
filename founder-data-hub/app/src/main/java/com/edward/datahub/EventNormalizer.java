package com.edward.datahub;

import java.util.*;

public class EventNormalizer {
    private final AppIdentityResolver resolver=new AppIdentityResolver();

    public NormalizedSignal normalize(RawEvent e){
        String app=resolver.resolve(e.source);
        String detail=e.detail==null?"":e.detail.toLowerCase(Locale.US);
        String type=semanticType(e.eventType,app,detail);
        String category=category(type,app);
        return new NormalizedSignal(e.timestamp,type,e.source,app,category,e.detail,e.screenActive,e.activityOrigin,e.id);
    }

    private String semanticType(String eventType,String app,String detail){
        String t=eventType==null?"":eventType.toUpperCase(Locale.US);
        if(detail.contains("missed_call")||detail.contains("missed call"))return "MISSED_CALL_SIGNAL";
        if(detail.contains("call")||t.contains("CALL"))return "CALL_SIGNAL";
        if("Clock".equals(app)&&(detail.contains("alarm")||detail.contains("timer")))return "ALARM_SIGNAL";
        if("Camera".equals(app))return "CAMERA_SIGNAL";
        if("Screenshot".equals(app)||detail.contains("screenshot"))return "SCREENSHOT_SIGNAL";
        if("Android Auto".equals(app))return "CAR_CONNECTION_SIGNAL";
        if("Download Manager".equals(app)||t.contains("DOWNLOAD"))return "DOWNLOAD_SIGNAL";
        if("WhatsApp".equals(app)&&t.contains("NOTIFICATION"))return "MESSAGE_SIGNAL";
        if("Google Wallet".equals(app)||detail.contains("payment"))return "PAYMENT_SIGNAL";
        if("Google Maps".equals(app)||"Waze".equals(app))return "NAVIGATION_SIGNAL";
        if("Spotify".equals(app)||"YouTube".equals(app))return "MEDIA_SIGNAL";
        if(t.equals("APP_FOREGROUND"))return "APP_FOREGROUND_SIGNAL";
        if(t.equals("APP_BACKGROUND"))return "APP_BACKGROUND_SIGNAL";
        if(t.equals("DEVICE_UNLOCK")||t.equals("USER_PRESENT"))return "UNLOCK_SIGNAL";
        if(t.equals("SCREEN_ON"))return "SCREEN_ON_SIGNAL";
        if(t.equals("SCREEN_OFF")||t.equals("DEVICE_LOCK"))return "SCREEN_OFF_SIGNAL";
        if(t.equals("NOTIFICATION_RECEIVED"))return "NOTIFICATION_SIGNAL";
        return t+"_SIGNAL";
    }

    private String category(String type,String app){
        if(type.contains("CALL")||type.contains("MESSAGE"))return "Communication";
        if(type.contains("CAMERA")||type.contains("SCREENSHOT")||type.contains("MEDIA"))return "Media";
        if(type.contains("CAR_CONNECTION")||type.contains("NAVIGATION"))return "Movement";
        if(type.contains("PAYMENT"))return "Phone";
        if(type.contains("ALARM")||type.contains("UNLOCK")||type.contains("SCREEN"))return "Phone";
        if("System".equals(app)||"Download Manager".equals(app))return "System";
        return "Unknown";
    }
}

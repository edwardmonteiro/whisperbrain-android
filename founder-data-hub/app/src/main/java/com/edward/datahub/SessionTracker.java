package com.edward.datahub;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.*;

public class SessionTracker {
    private final Context context;
    private final EventDb db;

    private boolean screenOn=false;
    private boolean unlocked=false;
    private String currentPackage=null;
    private String currentAppName=null;
    private long currentAppStart=0;

    private String phoneSessionId=null;
    private long phoneSessionStart=0;
    private boolean phoneSessionUnlock=false;
    private final LinkedHashSet<String> phoneApps=new LinkedHashSet<>();
    private int contextSwitches=0;

    public SessionTracker(Context context,EventDb db) {
        this.context=context.getApplicationContext();
        this.db=db;
    }

    public synchronized void restore(boolean screenOn,boolean unlocked) {
        this.screenOn=screenOn; this.unlocked=unlocked;
    }

    public synchronized void onScreenOn(long ts) {
        screenOn=true;
        db.addAt(ts,"SCREEN_ON","system","display became interactive","system",true);
    }

    public synchronized void onUserPresent(long ts) {
        screenOn=true; unlocked=true;
        db.addAt(ts,"USER_PRESENT","system","user present after keyguard","human",true);
        db.addAt(ts,"DEVICE_UNLOCK","system","device unlocked","human",true);
        startPhoneSession(ts,true);
    }

    public synchronized void onLocked(long ts) {
        unlocked=false;
        db.addAt(ts,"DEVICE_LOCK","system","device locked","system",screenOn);
        endCurrentApp(ts,"lock");
        endPhoneSession(ts);
    }

    public synchronized void onScreenOff(long ts) {
        screenOn=false; unlocked=false;
        db.addAt(ts,"SCREEN_OFF","system","display non-interactive","system",false);
        endCurrentApp(ts,"screen_off");
        endPhoneSession(ts);
    }

    public synchronized void onForeground(String pkg,long ts) {
        if(pkg==null||pkg.isEmpty()) return;
        if(!screenOn || !unlocked) {
            db.addAt(ts,"APP_FOREGROUND",pkg,"foreground while screen locked/off","background",false);
            return;
        }

        String app=appName(pkg);
        if(phoneSessionId==null) startPhoneSession(ts,false);

        if(pkg.equals(currentPackage)) return;

        if(currentPackage!=null) {
            endCurrentApp(ts,"context_switch");
            contextSwitches++;
        }

        currentPackage=pkg;
        currentAppName=app;
        currentAppStart=ts;
        phoneApps.add(pkg);
        db.addAt(ts,"APP_FOREGROUND",pkg,"app_name="+app,"human",true);
    }

    public synchronized void stop(long ts) {
        endCurrentApp(ts,"service_stop");
        endPhoneSession(ts);
    }

    private void endCurrentApp(long ts,String reason) {
        if(currentPackage==null||currentAppStart<=0) return;
        long end=Math.max(ts,currentAppStart+1000);
        String cat=db.getCategory(currentPackage,currentAppName);
        db.addAt(end,"APP_BACKGROUND",currentPackage,
                "app_name="+currentAppName+";duration_seconds="+Math.max(1,(end-currentAppStart)/1000)+";reason="+reason,
                "human",screenOn);
        db.insertAppSession(UUID.randomUUID().toString(),currentAppStart,end,currentPackage,currentAppName,true,"human",cat);
        currentPackage=null; currentAppName=null; currentAppStart=0;
    }

    private void startPhoneSession(long ts,boolean unlockTriggered) {
        if(phoneSessionId!=null) return;
        phoneSessionId=UUID.randomUUID().toString();
        phoneSessionStart=ts;
        phoneSessionUnlock=unlockTriggered;
        phoneApps.clear();
        contextSwitches=0;
    }

    private void endPhoneSession(long ts) {
        if(phoneSessionId==null||phoneSessionStart<=0) return;
        long end=Math.max(ts,phoneSessionStart+1000);
        db.insertPhoneSession(phoneSessionId,phoneSessionStart,end,phoneSessionUnlock,phoneApps.size(),contextSwitches);
        phoneSessionId=null; phoneSessionStart=0; phoneSessionUnlock=false; phoneApps.clear(); contextSwitches=0;
    }

    private String appName(String pkg) {
        try {
            PackageManager pm=context.getPackageManager();
            ApplicationInfo ai=pm.getApplicationInfo(pkg,0);
            CharSequence label=pm.getApplicationLabel(ai);
            return label==null?pkg:label.toString();
        } catch(Exception e) { return pkg; }
    }

    public synchronized boolean isScreenOn(){ return screenOn; }
    public synchronized boolean isUnlocked(){ return unlocked; }
    public synchronized String currentPackage(){ return currentPackage; }
    public synchronized long currentAppStart(){ return currentAppStart; }
    public synchronized long phoneSessionStart(){ return phoneSessionStart; }
    public synchronized int contextSwitches(){ return contextSwitches; }
}

package com.edward.datahub;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.net.*;
import android.os.*;

public class CaptureService extends Service {
    private static final String CH="local_capture";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private EventDb db;
    private String lastPackage="";
    private long lastPackageStart=0;
    private long lastContext=0;

    private final Runnable loop=new Runnable(){
        @Override public void run(){
            try { pollUsage(); pollContext(); }
            catch(Exception ignored) {}
            handler.postDelayed(this,15000);
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        db=new EventDb(this);
        createChannel();
        Notification n=new Notification.Builder(this,CH)
                .setContentTitle("LifeGraph capture is ON")
                .setContentText("Local only · no message bodies · tap app to review")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setOngoing(true).build();
        startForeground(1001,n);
        handler.post(loop);
    }

    private void createChannel(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null) nm.createNotificationChannel(new NotificationChannel(CH,"Local capture",NotificationManager.IMPORTANCE_LOW));
    }

    private void pollUsage(){
        UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
        if(usm==null)return;
        long now=System.currentTimeMillis();
        UsageEvents events=usm.queryEvents(now-30000,now);
        UsageEvents.Event e=new UsageEvents.Event();
        String newest=null;
        long newestTs=0;
        while(events.hasNextEvent()){
            events.getNextEvent(e);
            if(e.getEventType()==UsageEvents.Event.ACTIVITY_RESUMED && e.getTimeStamp()>newestTs){
                newest=e.getPackageName(); newestTs=e.getTimeStamp();
            }
        }
        if(newest!=null && !newest.equals(getPackageName()) && !newest.equals(lastPackage)){
            if(!lastPackage.isEmpty() && lastPackageStart>0){
                long duration=Math.max(1,(now-lastPackageStart)/1000);
                db.add("app_session",lastPackage,"duration_seconds="+duration);
            }
            db.add("app_foreground",newest,"foreground transition");
            lastPackage=newest;
            lastPackageStart=now;
        }
    }

    private void pollContext(){
        long now=System.currentTimeMillis();
        if(now-lastContext<5*60*1000L)return;
        lastContext=now;
        Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level=i==null?-1:i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int scale=i==null?-1:i.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        int status=i==null?-1:i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        int pct=(level>=0&&scale>0)?(level*100/scale):-1;
        boolean charging=status==BatteryManager.BATTERY_STATUS_CHARGING || status==BatteryManager.BATTERY_STATUS_FULL;

        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        String network="none";
        if(cm!=null){
            Network n=cm.getActiveNetwork();
            NetworkCapabilities caps=n==null?null:cm.getNetworkCapabilities(n);
            if(caps!=null){
                if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))network="wifi";
                else if(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))network="cellular";
                else network="other";
            }
        }
        db.add("device_context","android","battery="+pct+";charging="+charging+";network="+network);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){ return START_STICKY; }
    @Override public void onDestroy(){
        handler.removeCallbacks(loop);
        if(!lastPackage.isEmpty() && lastPackageStart>0){
            long duration=Math.max(1,(System.currentTimeMillis()-lastPackageStart)/1000);
            db.add("app_session",lastPackage,"duration_seconds="+duration);
        }
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent intent){ return null; }
}

package com.edward.datahub;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.util.*;

public class CaptureService extends Service {
    private static final String CH="local_capture";
    private static final long ACTIVE_POLL_MS=10000L;
    private static final long CONTEXT_INTERVAL_MS=5*60*1000L;

    private final Handler handler=new Handler(Looper.getMainLooper());
    private EventDb db;
    private SessionTracker tracker;
    private long usageCursor=0;
    private long lastContext=0;

    private final BroadcastReceiver stateReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            long ts=System.currentTimeMillis();
            String action=intent.getAction();
            if(Intent.ACTION_SCREEN_ON.equals(action)){
                tracker.onScreenOn(ts);
                scheduleNow();
            } else if(Intent.ACTION_USER_PRESENT.equals(action)){
                tracker.onUserPresent(ts);
                scheduleNow();
            } else if(Intent.ACTION_SCREEN_OFF.equals(action)){
                tracker.onLocked(ts);
                tracker.onScreenOff(ts);
                DailyAggregator.recompute(db,ts);
            }
        }
    };

    private final Runnable loop=new Runnable(){
        @Override public void run(){
            try {
                if(tracker.isScreenOn() && tracker.isUnlocked()) pollUsage();
                pollContext();
            } catch(Exception e) {
                db.add("COLLECTOR_ERROR","capture",e.getClass().getSimpleName(),"system",tracker.isScreenOn());
            }
            handler.postDelayed(this,tracker.isScreenOn()?ACTIVE_POLL_MS:60000L);
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        db=new EventDb(this);
        tracker=new SessionTracker(this,db);

        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        boolean screen=pm!=null && pm.isInteractive();
        boolean unlocked=km==null || !km.isDeviceLocked();
        tracker.restore(screen,screen&&unlocked);

        IntentFilter filter=new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(stateReceiver,filter);

        usageCursor=System.currentTimeMillis()-30000L;
        createChannel();
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,CH)
                .setContentTitle("LifeGraph · capture active")
                .setContentText("Local only · reconstructing human screen activity")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentIntent(pi)
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
        long from=Math.max(usageCursor-1000,now-2*60*1000L);
        UsageEvents events=usm.queryEvents(from,now);
        UsageEvents.Event e=new UsageEvents.Event();
        ArrayList<UsagePoint> points=new ArrayList<>();
        while(events.hasNextEvent()){
            events.getNextEvent(e);
            if(e.getTimeStamp()<=usageCursor) continue;
            int type=e.getEventType();
            if(type==UsageEvents.Event.ACTIVITY_RESUMED){
                points.add(new UsagePoint(e.getTimeStamp(),e.getPackageName()));
            }
        }
        Collections.sort(points,(a,b)->Long.compare(a.ts,b.ts));
        for(UsagePoint p:points){
            if(p.pkg==null || p.pkg.equals(getPackageName())) continue;
            tracker.onForeground(p.pkg,p.ts);
        }
        usageCursor=now;
    }

    private void pollContext(){
        long now=System.currentTimeMillis();
        if(now-lastContext<CONTEXT_INTERVAL_MS)return;
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
        db.add("DEVICE_CONTEXT","android","battery="+pct+";charging="+charging+";network="+network,
                "system",tracker.isScreenOn());
    }

    private void scheduleNow(){
        handler.removeCallbacks(loop);
        handler.post(loop);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){ return START_STICKY; }

    @Override public void onDestroy(){
        handler.removeCallbacks(loop);
        try{ unregisterReceiver(stateReceiver); }catch(Exception ignored){}
        if(tracker!=null) tracker.stop(System.currentTimeMillis());
        if(db!=null) DailyAggregator.recompute(db,System.currentTimeMillis());
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent){ return null; }

    private static final class UsagePoint {
        final long ts; final String pkg;
        UsagePoint(long t,String p){ts=t;pkg=p;}
    }
}

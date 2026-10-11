package com.edward.datahub;

import android.content.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class PathSessionStore {
    private static final String PREF="path_capture_session";
    private static final String K_ACTIVE="active";
    private static final String K_ID="session_id";
    private static final String K_GOAL="goal";
    private static final String K_STARTED="started_at";
    private static final Object LOCK=new Object();

    public static class ActiveSession {
        public final String id,goal;
        public final long startedAt;
        ActiveSession(String id,String goal,long startedAt){this.id=id;this.goal=goal;this.startedAt=startedAt;}
    }

    public static class SessionInfo {
        public String id,goal,startedAt,fileName;
        public int eventCount;
        public long modifiedAt;
    }

    public static ActiveSession start(Context c,String goal)throws Exception{
        synchronized(LOCK){
            String id=UUID.randomUUID().toString();
            long started=System.currentTimeMillis();
            SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            p.edit().putBoolean(K_ACTIVE,true).putString(K_ID,id).putString(K_GOAL,goal)
                    .putLong(K_STARTED,started).apply();

            JSONObject meta=new JSONObject();
            meta.put("type","session");
            meta.put("session_id",id);
            meta.put("goal",goal);
            meta.put("started_at",iso(started));
            meta.put("device",Build.MANUFACTURER+" "+Build.MODEL);
            appendRaw(c,id,meta.toString());
            return new ActiveSession(id,goal,started);
        }
    }

    public static void stop(Context c){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(K_ACTIVE,false).apply();
    }

    public static ActiveSession active(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(!p.getBoolean(K_ACTIVE,false))return null;
        String id=p.getString(K_ID,""),goal=p.getString(K_GOAL,"");
        long started=p.getLong(K_STARTED,0);
        if(id.isEmpty()||started==0)return null;
        return new ActiveSession(id,goal,started);
    }

    public static void append(Context c,JSONObject event)throws Exception{
        ActiveSession s=active(c);
        if(s==null)return;
        appendRaw(c,s.id,event.toString());
    }

    private static void appendRaw(Context c,String sessionId,String line)throws Exception{
        synchronized(LOCK){
            File dir=sessionsDir(c);
            if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create sessions directory");
            File f=new File(dir,"session-"+sessionId+".jsonl");
            try(FileOutputStream fos=new FileOutputStream(f,true);
                OutputStreamWriter osw=new OutputStreamWriter(fos,"UTF-8");
                BufferedWriter bw=new BufferedWriter(osw)){
                bw.write(line);
                bw.newLine();
                bw.flush();
            }
        }
    }

    public static File sessionsDir(Context c){
        File base=c.getExternalFilesDir("sessions");
        return base!=null?base:new File(c.getFilesDir(),"sessions");
    }

    public static File sessionFile(Context c,String id){
        return new File(sessionsDir(c),"session-"+id+".jsonl");
    }

    public static ArrayList<SessionInfo> list(Context c){
        ArrayList<SessionInfo> out=new ArrayList<>();
        File[] files=sessionsDir(c).listFiles((d,n)->n.endsWith(".jsonl"));
        if(files==null)return out;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files){
            SessionInfo info=new SessionInfo();
            info.fileName=f.getName();
            info.modifiedAt=f.lastModified();
            try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"))){
                String first=br.readLine();
                if(first!=null){
                    JSONObject m=new JSONObject(first);
                    info.id=m.optString("session_id","");
                    info.goal=m.optString("goal","");
                    info.startedAt=m.optString("started_at","");
                }
                int count=0;
                while(br.readLine()!=null)count++;
                info.eventCount=count;
            }catch(Exception ignored){}
            out.add(info);
        }
        return out;
    }

    public static String iso(long ts){
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date(ts));
    }
}

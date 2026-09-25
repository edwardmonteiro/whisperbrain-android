package com.edward.datahub;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.text.*;
import java.util.*;

public class EventDb extends SQLiteOpenHelper {
    public static final String DB_NAME = "lifegraph.db";
    private static final int VERSION = 2;

    public EventDb(Context c) { super(c, DB_NAME, null, VERSION); }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        createV1(db);
        createV2(db);
    }

    private void createV1(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "source TEXT," +
                "detail TEXT," +
                "intent TEXT," +
                "outcome TEXT," +
                "satisfaction INTEGER DEFAULT 0," +
                "rewarded INTEGER DEFAULT 0," +
                "activity_origin TEXT DEFAULT 'unknown'," +
                "screen_active INTEGER DEFAULT -1)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_ts ON events(ts DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_type_ts ON events(type,ts DESC)");
    }

    private void createV2(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS app_sessions (" +
                "session_id TEXT PRIMARY KEY," +
                "start_ts INTEGER NOT NULL," +
                "end_ts INTEGER NOT NULL," +
                "duration_seconds INTEGER NOT NULL," +
                "package_name TEXT NOT NULL," +
                "app_name TEXT," +
                "screen_active INTEGER NOT NULL DEFAULT 1," +
                "interaction_type TEXT NOT NULL DEFAULT 'human'," +
                "category TEXT NOT NULL DEFAULT 'Unknown')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_app_sessions_start ON app_sessions(start_ts)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_app_sessions_pkg ON app_sessions(package_name,start_ts)");

        db.execSQL("CREATE TABLE IF NOT EXISTS phone_sessions (" +
                "phone_session_id TEXT PRIMARY KEY," +
                "start_ts INTEGER NOT NULL," +
                "end_ts INTEGER NOT NULL," +
                "duration_seconds INTEGER NOT NULL," +
                "unlock_triggered INTEGER NOT NULL DEFAULT 0," +
                "apps_used INTEGER NOT NULL DEFAULT 0," +
                "context_switches INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_phone_sessions_start ON phone_sessions(start_ts)");

        db.execSQL("CREATE TABLE IF NOT EXISTS app_categories (" +
                "package_name TEXT PRIMARY KEY," +
                "app_name TEXT," +
                "category TEXT NOT NULL," +
                "user_override INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS daily_summary (" +
                "date_key TEXT PRIMARY KEY," +
                "screen_time_seconds INTEGER NOT NULL DEFAULT 0," +
                "phone_sessions INTEGER NOT NULL DEFAULT 0," +
                "device_unlocks INTEGER NOT NULL DEFAULT 0," +
                "average_session_seconds REAL NOT NULL DEFAULT 0," +
                "longest_session_seconds INTEGER NOT NULL DEFAULT 0," +
                "foreground_apps_count INTEGER NOT NULL DEFAULT 0," +
                "context_switches INTEGER NOT NULL DEFAULT 0," +
                "context_switches_per_hour REAL NOT NULL DEFAULT 0," +
                "first_phone_use INTEGER," +
                "last_phone_use INTEGER," +
                "longest_phone_free_seconds INTEGER NOT NULL DEFAULT 0," +
                "focused_blocks INTEGER NOT NULL DEFAULT 0," +
                "short_sessions INTEGER NOT NULL DEFAULT 0," +
                "longest_uninterrupted_seconds INTEGER NOT NULL DEFAULT 0," +
                "generated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS daily_app_summary (" +
                "date_key TEXT NOT NULL," +
                "package_name TEXT NOT NULL," +
                "app_name TEXT," +
                "category TEXT NOT NULL DEFAULT 'Unknown'," +
                "total_duration_seconds INTEGER NOT NULL DEFAULT 0," +
                "number_of_sessions INTEGER NOT NULL DEFAULT 0," +
                "average_session_seconds REAL NOT NULL DEFAULT 0," +
                "screen_time_percentage REAL NOT NULL DEFAULT 0," +
                "PRIMARY KEY(date_key,package_name))");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            addColumnIfMissing(db,"events","activity_origin","TEXT DEFAULT 'unknown'");
            addColumnIfMissing(db,"events","screen_active","INTEGER DEFAULT -1");
            createV2(db);
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db,String table,String column,String definition) {
        Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);
        boolean found=false;
        try { while(c.moveToNext()) if(column.equals(c.getString(1))) { found=true; break; } }
        finally { c.close(); }
        if(!found) db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);
    }

    public synchronized long add(String type,String source,String detail) {
        return add(type,source,detail,"unknown",-1);
    }

    public synchronized long add(String type,String source,String detail,String origin,boolean screenActive) {
        return add(type,source,detail,origin,screenActive?1:0);
    }

    public synchronized long add(String type,String source,String detail,String origin,int screenActive) {
        ContentValues v=new ContentValues();
        v.put("ts",System.currentTimeMillis());
        v.put("type",type);
        v.put("source",source==null?"":source);
        v.put("detail",detail==null?"":detail);
        v.put("activity_origin",origin==null?"unknown":origin);
        v.put("screen_active",screenActive);
        return getWritableDatabase().insert("events",null,v);
    }

    public synchronized long addAt(long ts,String type,String source,String detail,String origin,boolean screenActive) {
        ContentValues v=new ContentValues();
        v.put("ts",ts); v.put("type",type); v.put("source",source==null?"":source);
        v.put("detail",detail==null?"":detail); v.put("activity_origin",origin==null?"unknown":origin);
        v.put("screen_active",screenActive?1:0);
        return getWritableDatabase().insert("events",null,v);
    }

    public synchronized Cursor recent(int limit) {
        return getReadableDatabase().rawQuery(
                "SELECT id,ts,type,source,detail,intent,outcome,satisfaction,rewarded,activity_origin,screen_active " +
                "FROM events ORDER BY ts DESC LIMIT ?",new String[]{String.valueOf(limit)});
    }

    public synchronized Cursor eventsBetween(long start,long end) {
        return getReadableDatabase().rawQuery(
                "SELECT id,ts,type,source,detail,intent,outcome,satisfaction,rewarded,activity_origin,screen_active " +
                "FROM events WHERE ts>=? AND ts<? ORDER BY ts ASC",
                new String[]{String.valueOf(start),String.valueOf(end)});
    }

    public synchronized int countToday() {
        long start=DayBounds.startOfToday();
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events WHERE ts>=?",new String[]{String.valueOf(start)});
        try { return c.moveToFirst()?c.getInt(0):0; } finally { c.close(); }
    }

    public synchronized int countTrajectoriesToday() {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM phone_sessions WHERE start_ts>=?",
                new String[]{String.valueOf(DayBounds.startOfToday())});
        try { return c.moveToFirst()?c.getInt(0):0; } finally { c.close(); }
    }

    public synchronized void label(long id,String intent,String outcome,int satisfaction) {
        ContentValues v=new ContentValues();
        v.put("intent",intent); v.put("outcome",outcome); v.put("satisfaction",satisfaction);
        getWritableDatabase().update("events",v,"id=?",new String[]{String.valueOf(id)});
    }

    public synchronized int rewardOnce(long id) {
        SQLiteDatabase db=getWritableDatabase();
        Cursor c=db.rawQuery("SELECT rewarded FROM events WHERE id=?",new String[]{String.valueOf(id)});
        try { if(!c.moveToFirst()||c.getInt(0)==1) return 0; } finally { c.close(); }
        ContentValues v=new ContentValues(); v.put("rewarded",1);
        db.update("events",v,"id=?",new String[]{String.valueOf(id)});
        return 10;
    }

    public synchronized void insertAppSession(String id,long start,long end,String pkg,String app,boolean screenActive,String interaction,String category) {
        if(end<=start) return;
        ContentValues v=new ContentValues();
        v.put("session_id",id); v.put("start_ts",start); v.put("end_ts",end);
        v.put("duration_seconds",Math.max(1,(end-start)/1000));
        v.put("package_name",pkg); v.put("app_name",app); v.put("screen_active",screenActive?1:0);
        v.put("interaction_type",interaction); v.put("category",category);
        getWritableDatabase().insertWithOnConflict("app_sessions",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void insertPhoneSession(String id,long start,long end,boolean unlock,int appsUsed,int switches) {
        if(end<=start) return;
        ContentValues v=new ContentValues();
        v.put("phone_session_id",id); v.put("start_ts",start); v.put("end_ts",end);
        v.put("duration_seconds",Math.max(1,(end-start)/1000));
        v.put("unlock_triggered",unlock?1:0); v.put("apps_used",appsUsed); v.put("context_switches",switches);
        getWritableDatabase().insertWithOnConflict("phone_sessions",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized Cursor appSessionsBetween(long start,long end) {
        return getReadableDatabase().rawQuery(
                "SELECT session_id,start_ts,end_ts,duration_seconds,package_name,app_name,screen_active,interaction_type,category " +
                "FROM app_sessions WHERE start_ts<? AND end_ts>? ORDER BY start_ts ASC",
                new String[]{String.valueOf(end),String.valueOf(start)});
    }

    public synchronized Cursor phoneSessionsBetween(long start,long end) {
        return getReadableDatabase().rawQuery(
                "SELECT phone_session_id,start_ts,end_ts,duration_seconds,unlock_triggered,apps_used,context_switches " +
                "FROM phone_sessions WHERE start_ts<? AND end_ts>? ORDER BY start_ts ASC",
                new String[]{String.valueOf(end),String.valueOf(start)});
    }

    public synchronized String getCategory(String pkg,String appName) {
        Cursor c=getReadableDatabase().rawQuery("SELECT category FROM app_categories WHERE package_name=?",new String[]{pkg});
        try {
            if(c.moveToFirst()) return c.getString(0);
        } finally { c.close(); }
        String guessed=AppClassifier.guess(pkg,appName);
        setCategory(pkg,appName,guessed,false);
        return guessed;
    }

    public synchronized void setCategory(String pkg,String appName,String category,boolean override) {
        ContentValues v=new ContentValues();
        v.put("package_name",pkg); v.put("app_name",appName); v.put("category",category); v.put("user_override",override?1:0);
        getWritableDatabase().insertWithOnConflict("app_categories",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        if(override) {
            ContentValues s=new ContentValues(); s.put("category",category);
            getWritableDatabase().update("app_sessions",s,"package_name=?",new String[]{pkg});
        }
    }

    public synchronized void replaceDailySummary(String dateKey,ContentValues v) {
        v.put("date_key",dateKey); v.put("generated_at",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("daily_summary",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void clearDailyAppSummary(String dateKey) {
        getWritableDatabase().delete("daily_app_summary","date_key=?",new String[]{dateKey});
    }

    public synchronized void putDailyAppSummary(ContentValues v) {
        getWritableDatabase().insertWithOnConflict("daily_app_summary",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized Cursor dailySummary(String dateKey) {
        return getReadableDatabase().rawQuery("SELECT * FROM daily_summary WHERE date_key=?",new String[]{dateKey});
    }

    public synchronized Cursor dailyApps(String dateKey) {
        return getReadableDatabase().rawQuery(
                "SELECT package_name,app_name,category,total_duration_seconds,number_of_sessions,average_session_seconds,screen_time_percentage " +
                "FROM daily_app_summary WHERE date_key=? ORDER BY total_duration_seconds DESC",new String[]{dateKey});
    }

    public synchronized void clearAll() {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("events",null,null); db.delete("app_sessions",null,null); db.delete("phone_sessions",null,null);
            db.delete("daily_summary",null,null); db.delete("daily_app_summary",null,null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized JSONArray eventsJson() throws JSONException {
        JSONArray arr=new JSONArray(); Cursor c=recent(200000);
        try {
            while(c.moveToNext()) {
                JSONObject o=new JSONObject();
                o.put("id",c.getLong(0)); o.put("timestamp",iso(c.getLong(1)));
                o.put("event_type",c.getString(2)); o.put("source",c.getString(3)); o.put("detail",c.getString(4));
                o.put("intent",c.isNull(5)?JSONObject.NULL:c.getString(5)); o.put("outcome",c.isNull(6)?JSONObject.NULL:c.getString(6));
                o.put("satisfaction",c.getInt(7)); o.put("activity_origin",c.getString(9)); o.put("screen_active",c.getInt(10));
                arr.put(o);
            }
        } finally { c.close(); }
        return arr;
    }

    public synchronized JSONArray appSessionsJson() throws JSONException {
        JSONArray arr=new JSONArray();
        Cursor c=getReadableDatabase().rawQuery("SELECT session_id,start_ts,end_ts,duration_seconds,package_name,app_name,screen_active,interaction_type,category FROM app_sessions ORDER BY start_ts",null);
        try {
            while(c.moveToNext()) {
                JSONObject o=new JSONObject();
                o.put("session_id",c.getString(0)); o.put("start_time",iso(c.getLong(1))); o.put("end_time",iso(c.getLong(2)));
                o.put("duration_seconds",c.getLong(3)); o.put("package_name",c.getString(4)); o.put("app_name",c.getString(5));
                o.put("screen_active",c.getInt(6)==1); o.put("interaction_type",c.getString(7)); o.put("category",c.getString(8));
                arr.put(o);
            }
        } finally { c.close(); }
        return arr;
    }

    public synchronized JSONArray phoneSessionsJson() throws JSONException {
        JSONArray arr=new JSONArray();
        Cursor c=getReadableDatabase().rawQuery("SELECT phone_session_id,start_ts,end_ts,duration_seconds,unlock_triggered,apps_used,context_switches FROM phone_sessions ORDER BY start_ts",null);
        try {
            while(c.moveToNext()) {
                JSONObject o=new JSONObject();
                o.put("phone_session_id",c.getString(0)); o.put("start_time",iso(c.getLong(1))); o.put("end_time",iso(c.getLong(2)));
                o.put("duration_seconds",c.getLong(3)); o.put("unlock_triggered",c.getInt(4)==1);
                o.put("apps_used",c.getInt(5)); o.put("context_switches",c.getInt(6)); arr.put(o);
            }
        } finally { c.close(); }
        return arr;
    }

    public synchronized JSONObject fullExportJson() throws JSONException {
        JSONObject root=new JSONObject();
        root.put("schema_version",2); root.put("generated_at",iso(System.currentTimeMillis()));
        root.put("privacy","local-first; metadata only; no message content, typed text, passwords, or screen content");
        root.put("events",eventsJson()); root.put("app_sessions",appSessionsJson()); root.put("phone_sessions",phoneSessionsJson());
        return root;
    }

    public synchronized String eventsCsv() {
        StringBuilder b=new StringBuilder("id,timestamp,event_type,source,detail,intent,outcome,satisfaction,activity_origin,screen_active\n");
        Cursor c=recent(200000);
        try {
            while(c.moveToNext()) {
                csvRow(b,new String[]{c.getString(0),iso(c.getLong(1)),c.getString(2),c.getString(3),c.getString(4),
                        c.isNull(5)?"":c.getString(5),c.isNull(6)?"":c.getString(6),c.getString(7),c.getString(9),c.getString(10)});
            }
        } finally { c.close(); }
        return b.toString();
    }

    public synchronized String appSessionsCsv() {
        StringBuilder b=new StringBuilder("session_id,start_time,end_time,duration_seconds,package_name,app_name,screen_active,interaction_type,category\n");
        Cursor c=getReadableDatabase().rawQuery("SELECT session_id,start_ts,end_ts,duration_seconds,package_name,app_name,screen_active,interaction_type,category FROM app_sessions ORDER BY start_ts",null);
        try {
            while(c.moveToNext()) csvRow(b,new String[]{c.getString(0),iso(c.getLong(1)),iso(c.getLong(2)),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8)});
        } finally { c.close(); }
        return b.toString();
    }

    public synchronized String phoneSessionsCsv() {
        StringBuilder b=new StringBuilder("phone_session_id,start_time,end_time,duration_seconds,unlock_triggered,apps_used,context_switches\n");
        Cursor c=getReadableDatabase().rawQuery("SELECT phone_session_id,start_ts,end_ts,duration_seconds,unlock_triggered,apps_used,context_switches FROM phone_sessions ORDER BY start_ts",null);
        try {
            while(c.moveToNext()) csvRow(b,new String[]{c.getString(0),iso(c.getLong(1)),iso(c.getLong(2)),c.getString(3),c.getString(4),c.getString(5),c.getString(6)});
        } finally { c.close(); }
        return b.toString();
    }

    public synchronized String dailySummaryCsv() {
        StringBuilder b=new StringBuilder("date,screen_time_seconds,phone_sessions,device_unlocks,average_session_seconds,longest_session_seconds,foreground_apps_count,context_switches,context_switches_per_hour,first_phone_use,last_phone_use,longest_phone_free_seconds,focused_blocks,short_sessions,longest_uninterrupted_seconds\n");
        Cursor c=getReadableDatabase().rawQuery("SELECT date_key,screen_time_seconds,phone_sessions,device_unlocks,average_session_seconds,longest_session_seconds,foreground_apps_count,context_switches,context_switches_per_hour,first_phone_use,last_phone_use,longest_phone_free_seconds,focused_blocks,short_sessions,longest_uninterrupted_seconds FROM daily_summary ORDER BY date_key",null);
        try {
            while(c.moveToNext()) {
                String first=c.isNull(9)?"":iso(c.getLong(9)), last=c.isNull(10)?"":iso(c.getLong(10));
                csvRow(b,new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),first,last,c.getString(11),c.getString(12),c.getString(13),c.getString(14)});
            }
        } finally { c.close(); }
        return b.toString();
    }

    private static void csvRow(StringBuilder b,String[] cells) {
        for(int i=0;i<cells.length;i++) {
            String s=cells[i]==null?"":cells[i].replace("\"", "\"\"");
            b.append("\"").append(s).append("\""); if(i<cells.length-1)b.append(",");
        }
        b.append("\n");
    }

    public static String iso(long ts) {
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US);
        return f.format(new Date(ts));
    }

    public static final class DayBounds {
        public static long startOfToday() { return startOf(System.currentTimeMillis()); }
        public static long startOf(long ts) {
            Calendar c=Calendar.getInstance(); c.setTimeInMillis(ts);
            c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
            return c.getTimeInMillis();
        }
        public static long endOf(long ts) { return startOf(ts)+24L*60*60*1000; }
        public static String key(long ts) {
            return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(ts));
        }
    }
}

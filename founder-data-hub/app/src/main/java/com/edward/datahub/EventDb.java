package com.edward.datahub;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class EventDb extends SQLiteOpenHelper {
    public static final String DB_NAME = "lifegraph.db";
    private static final int VERSION = 1;

    public EventDb(Context c) { super(c, DB_NAME, null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "source TEXT," +
                "detail TEXT," +
                "intent TEXT," +
                "outcome TEXT," +
                "satisfaction INTEGER DEFAULT 0," +
                "rewarded INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_events_ts ON events(ts DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {}

    public synchronized long add(String type, String source, String detail) {
        ContentValues v = new ContentValues();
        v.put("ts", System.currentTimeMillis());
        v.put("type", type);
        v.put("source", source == null ? "" : source);
        v.put("detail", detail == null ? "" : detail);
        return getWritableDatabase().insert("events", null, v);
    }

    public synchronized Cursor recent(int limit) {
        return getReadableDatabase().rawQuery(
                "SELECT id,ts,type,source,detail,intent,outcome,satisfaction,rewarded FROM events ORDER BY ts DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
    }

    public synchronized int countToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        Cursor cur = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events WHERE ts>=?", new String[]{String.valueOf(c.getTimeInMillis())});
        try { return cur.moveToFirst() ? cur.getInt(0) : 0; } finally { cur.close(); }
    }

    public synchronized int countTrajectoriesToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        Cursor cur = getReadableDatabase().rawQuery("SELECT ts FROM events WHERE ts>=? ORDER BY ts ASC", new String[]{String.valueOf(c.getTimeInMillis())});
        int groups=0; long last=-1; final long GAP=20*60*1000L;
        try {
            while(cur.moveToNext()){
                long ts=cur.getLong(0);
                if(last<0 || ts-last>GAP) groups++;
                last=ts;
            }
        } finally { cur.close(); }
        return groups;
    }

    public synchronized void label(long id, String intent, String outcome, int satisfaction) {
        ContentValues v = new ContentValues();
        v.put("intent", intent);
        v.put("outcome", outcome);
        v.put("satisfaction", satisfaction);
        getWritableDatabase().update("events", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized int rewardOnce(long id) {
        SQLiteDatabase db=getWritableDatabase();
        Cursor c=db.rawQuery("SELECT rewarded FROM events WHERE id=?", new String[]{String.valueOf(id)});
        try {
            if(!c.moveToFirst() || c.getInt(0)==1) return 0;
        } finally { c.close(); }
        ContentValues v=new ContentValues(); v.put("rewarded",1);
        db.update("events",v,"id=?",new String[]{String.valueOf(id)});
        return 10;
    }

    public synchronized void clearAll() { getWritableDatabase().delete("events",null,null); }

    public synchronized JSONArray toJson() throws JSONException {
        JSONArray arr=new JSONArray();
        Cursor c=recent(100000);
        try{
            while(c.moveToNext()){
                JSONObject o=new JSONObject();
                o.put("id",c.getLong(0)); o.put("timestamp",c.getLong(1));
                o.put("type",c.getString(2)); o.put("source",c.getString(3)); o.put("detail",c.getString(4));
                o.put("intent",c.isNull(5)?JSONObject.NULL:c.getString(5));
                o.put("outcome",c.isNull(6)?JSONObject.NULL:c.getString(6));
                o.put("satisfaction",c.getInt(7));
                arr.put(o);
            }
        } finally { c.close(); }
        return arr;
    }

    public synchronized String toCsv() {
        StringBuilder b=new StringBuilder("id,timestamp,type,source,detail,intent,outcome,satisfaction\n");
        Cursor c=recent(100000);
        try{
            while(c.moveToNext()){
                for(int i=0;i<8;i++){
                    String s=c.isNull(i)?"":c.getString(i);
                    s=s.replace("\"", "\"\"");
                    b.append("\"").append(s).append("\"");
                    if(i<7)b.append(",");
                }
                b.append("\n");
            }
        } finally { c.close(); }
        return b.toString();
    }
}

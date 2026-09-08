package com.edward.whisperbrain;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Private SQLite graph. All human-readable records are AES-GCM encrypted with Android Keystore. */
public final class NotebookStore extends SQLiteOpenHelper {
    private static NotebookStore instance;
    private final Vault vault;
    private final Context context;
    public static synchronized NotebookStore get(Context context) {
        if(instance==null) instance=new NotebookStore(context.getApplicationContext()); return instance;
    }
    private NotebookStore(Context c) {super(c,"notebook.db",null,1);context=c;vault=new Vault(c);}
    @Override public void onConfigure(SQLiteDatabase db) {db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, created INTEGER NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE nodes (id TEXT PRIMARY KEY, session TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, created INTEGER NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX nodes_session ON nodes(session,created)");
        db.execSQL("CREATE TABLE edges (id TEXT PRIMARY KEY, source TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE, target TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE, created INTEGER NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX edge_source ON edges(source)"); db.execSQL("CREATE INDEX edge_target ON edges(target)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {throw new IllegalStateException("Migração não implementada para essa versão.");}
    private JSONObject decode(String table, Cursor c) throws Exception {return new JSONObject(vault.open(table+":"+c.getString(0),c.getString(1)));}
    private JSONArray rows(String table,String where,String[] args,String order) throws Exception {
        JSONArray out=new JSONArray();
        try(Cursor c=getReadableDatabase().query(table,new String[]{"id","payload"},where,args,null,null,order)) {while(c.moveToNext()) out.put(decode(table,c));}
        return out;
    }
    private JSONObject one(String table,String id) throws Exception {JSONArray a=rows(table,"id=?",new String[]{id},null);if(a.length()==0) throw new IllegalArgumentException("Registro não encontrado.");return a.getJSONObject(0);}
    private void write(String table,JSONObject item,boolean update) throws Exception {
        ContentValues v=new ContentValues();String id=item.getString("id");v.put("id",id);v.put("created",item.getLong("created"));
        if(table.equals("nodes")) v.put("session",item.getString("session"));
        if(table.equals("edges")){v.put("source",item.getString("from"));v.put("target",item.getString("to"));}
        v.put("payload",vault.seal(table+":"+id,item.toString()));
        if(update) {if(getWritableDatabase().update(table,v,"id=?",new String[]{id})!=1)throw new IllegalArgumentException("Registro removido.");}
        else getWritableDatabase().insertOrThrow(table,null,v);
    }
    public synchronized JSONObject createSession(String event) throws Exception {
        JSONObject s=new JSONObject().put("id",GraphData.id()).put("event",GraphData.required(event,120)).put("created",System.currentTimeMillis()).put("ended",0);
        write("sessions",s,false);return s;
    }
    public synchronized JSONObject session(String id) throws Exception {return one("sessions",id);}
    public synchronized JSONArray sessions() throws Exception {return rows("sessions",null,null,"created DESC");}
    public synchronized void renameSession(String id,String event) throws Exception {JSONObject s=session(id);s.put("event",GraphData.required(event,120));write("sessions",s,true);}
    public synchronized void endSession(String id,boolean ended) throws Exception {JSONObject s=session(id);s.put("ended",ended?System.currentTimeMillis():0);write("sessions",s,true);}
    public synchronized JSONObject node(String id) throws Exception {return one("nodes",id);}
    public synchronized JSONArray nodes(String session) throws Exception {return rows("nodes",session==null?null:"session=?",session==null?null:new String[]{session},"created ASC");}
    public synchronized JSONArray edges() throws Exception {return rows("edges",null,null,"created ASC");}
    public synchronized JSONObject addNode(String session,String title,String body,String kind,String origin) throws Exception {
        session(session);
        JSONObject n=new JSONObject().put("id",GraphData.id()).put("session",session).put("title",GraphData.required(title,160)).put("body",GraphData.text(body,20000))
                .put("kind",kind).put("origin",origin).put("created",System.currentTimeMillis()).put("updated",System.currentTimeMillis());
        write("nodes",n,false);return n;
    }
    public synchronized void updateNote(String id,String title,String body) throws Exception {
        JSONObject n=node(id);n.put("title",GraphData.required(title,160)).put("body",GraphData.required(body,20000)).put("updated",System.currentTimeMillis()).put("edited_by_user",true);write("nodes",n,true);
    }
    public synchronized JSONObject addAudio(String session,String attachment,String format,long duration) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {JSONObject n=addNode(session,"Áudio · "+duration/1000+" s","Gravação local desta conversa.","audio","user");
            n.put("attachment",attachment).put("format",format).put("duration",duration);write("nodes",n,true);db.setTransactionSuccessful();return n;}
        finally {db.endTransaction();}
    }
    public synchronized JSONObject link(String from,String to,String relation,String reason,String origin,String state) throws Exception {
        node(from);node(to);if(from.equals(to))throw new IllegalArgumentException("Escolha outro neurônio.");
        JSONObject e=new JSONObject().put("id",GraphData.id()).put("from",from).put("to",to).put("relation",GraphData.required(relation,80))
                .put("reason",GraphData.text(reason,2000)).put("origin",origin).put("state",state).put("created",System.currentTimeMillis());
        write("edges",e,false);return e;
    }
    public synchronized void acceptEdge(String id) throws Exception {JSONObject e=one("edges",id);e.put("state","accepted");write("edges",e,true);}
    public synchronized void deleteEdge(String id) {getWritableDatabase().delete("edges","id=?",new String[]{id});}
    public synchronized void deleteNode(String id) throws Exception {JSONObject n=node(id);getWritableDatabase().delete("nodes","id=?",new String[]{id});AudioArchive.delete(context,n.optString("attachment"));}
    public synchronized void deleteSession(String id) throws Exception {JSONArray n=nodes(id);getWritableDatabase().delete("sessions","id=?",new String[]{id});for(int i=0;i<n.length();i++)AudioArchive.delete(context,n.getJSONObject(i).optString("attachment"));}
    public synchronized JSONObject saveSuggestion(String focusId,JSONObject suggestion,String model) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            JSONObject focus=node(focusId), n=addNode(focus.getString("session"),suggestion.getString("title"),suggestion.getString("recommendation"),"recommendation","ai");
            n.put("model",model).put("whisper",suggestion.optString("whisper"));write("nodes",n,true);
            link(focusId,n.getString("id"),"recomenda", "Sugestão da IA para esta anotação.","ai","proposed");
            JSONArray links=suggestion.getJSONArray("connections");
            for(int i=0;i<links.length();i++){JSONObject x=links.getJSONObject(i);link(focusId,x.getString("target_id"),x.getString("relation"),x.getString("reason"),"ai","proposed");}
            db.setTransactionSuccessful();return n;
        } finally {db.endTransaction();}
    }
    public synchronized JSONObject snapshot() throws Exception {return new JSONObject().put("version",GraphData.VERSION).put("sessions",sessions()).put("nodes",nodes(null)).put("edges",edges());}
    /** Import uses new IDs, preserving existing sessions and rewiring every imported edge. */
    public synchronized void importSnapshot(JSONObject backup,Map<String,String> attachments) throws Exception {
        GraphData.validate(backup);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Map<String,String> map=new HashMap<>();
            for(String table:List.of("sessions","nodes","edges")) {
                JSONArray items=backup.getJSONArray(table);
                for(int i=0;i<items.length();i++) {
                    JSONObject n=new JSONObject(items.getJSONObject(i).toString());String old=n.getString("id"), id=GraphData.id();map.put(old,id);n.put("id",id);
                    if(table.equals("nodes")){n.put("session",map.get(n.getString("session")));String attachment=n.optString("attachment");if(!attachment.isEmpty()){if(!attachments.containsKey(attachment))throw new IllegalArgumentException("Áudio ausente no backup.");n.put("attachment",attachments.get(attachment));}}
                    if(table.equals("edges")){n.put("from",map.get(n.getString("from"))).put("to",map.get(n.getString("to")));}
                    write(table,n,false);
                }
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}

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
    private byte[] notificationSecret;
    private NotebookStore(Context c) {super(c,"notebook.db",null,3);context=c;vault=new Vault(c);}
    @Override public void onConfigure(SQLiteDatabase db) {db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, created INTEGER NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE nodes (id TEXT PRIMARY KEY, session TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, created INTEGER NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX nodes_session ON nodes(session,created)");
        db.execSQL("CREATE TABLE edges (id TEXT PRIMARY KEY, source TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE, target TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE, created INTEGER NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX edge_source ON edges(source)"); db.execSQL("CREATE INDEX edge_target ON edges(target)");
        notificationTables(db);
        dailyMapTables(db);
    }
    private void notificationTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE notification_threads (thread_key TEXT PRIMARY KEY, day TEXT NOT NULL, session TEXT REFERENCES sessions(id) ON DELETE SET NULL, last_node TEXT REFERENCES nodes(id) ON DELETE SET NULL)");
        db.execSQL("CREATE TABLE notification_seen (fingerprint TEXT PRIMARY KEY, source_time INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX notification_seen_time ON notification_seen(source_time)");
    }
    private void dailyMapTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS daily_maps (id TEXT PRIMARY KEY, created INTEGER NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS nodes_created ON nodes(created)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {if(oldVersion<2)notificationTables(db);if(oldVersion<3)dailyMapTables(db);}
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
        JSONObject n=node(id);n.put("title",GraphData.required(title,160)).put("body",GraphData.required(body,20000)).put("updated",System.currentTimeMillis()).put("edited_by_user",true);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{purgeDailyMaps(Set.of(id));write("nodes",n,true);db.setTransactionSuccessful();}finally{db.endTransaction();}
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
    public synchronized void deleteNode(String id) throws Exception {JSONObject n=node(id);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{purgeDailyMaps(Set.of(id));db.delete("nodes","id=?",new String[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}AudioArchive.delete(context,n.optString("attachment"));}
    public synchronized void deleteSession(String id) throws Exception {JSONArray n=nodes(id);Set<String> ids=new HashSet<>();for(int i=0;i<n.length();i++)ids.add(n.getJSONObject(i).getString("id"));SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{purgeDailyMaps(ids);db.delete("sessions","id=?",new String[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}for(int i=0;i<n.length();i++)AudioArchive.delete(context,n.getJSONObject(i).optString("attachment"));}
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
    public synchronized JSONObject snapshot() throws Exception {return new JSONObject().put("version",GraphData.VERSION).put("sessions",sessions()).put("nodes",nodes(null)).put("edges",edges()).put("daily_maps",rows("daily_maps",null,null,"created ASC"));}
    public synchronized JSONArray dailyNotifications(String day,String zone)throws Exception {
        return DailyMapData.day(rows("nodes","created>=? AND created<?",new String[]{Long.toString(DailyMapData.start(day,zone)),Long.toString(DailyMapData.end(day,zone))},"created ASC"),day,zone);
    }
    public synchronized JSONObject dailyMap(String day,String zone)throws Exception {JSONArray maps=rows("daily_maps","id=?",new String[]{DailyMapData.key(day,zone)},null);return maps.length()==0?null:maps.getJSONObject(0);}
    public synchronized void deleteDailyMap(String day,String zone){getWritableDatabase().delete("daily_maps","id=?",new String[]{DailyMapData.key(day,zone)});}
    /** Reject responses whose selected evidence changed or was deleted while the request was in flight. */
    public synchronized void saveDailyMap(JSONObject map,JSONArray originals)throws Exception {
        JSONArray fresh=new JSONArray();Set<String> expected=new HashSet<>();for(int i=0;i<originals.length();i++){String id=originals.getJSONObject(i).getString("id");expected.add(id);fresh.put(node(id));}
        if(!expected.equals(DailyMapData.ids(map.getJSONArray("source_ids")))||!DailyMapData.fingerprint(originals).equals(DailyMapData.fingerprint(fresh)))throw new IllegalArgumentException("As mensagens foram alteradas durante a análise. Atualize o dia.");
        DailyMapData.validateSaved(map,fresh);write("daily_maps",map,dailyMap(map.getString("day"),map.getString("zone"))!=null);
    }
    private void purgeDailyMaps(Set<String> deleted)throws Exception {
        if(deleted.isEmpty())return;JSONArray maps=rows("daily_maps",null,null,null);
        for(int i=0;i<maps.length();i++){JSONObject map=maps.getJSONObject(i);if(!Collections.disjoint(deleted,DailyMapData.ids(map.getJSONArray("source_ids"))))getWritableDatabase().delete("daily_maps","id=?",new String[]{map.getString("id")});}
    }
    private byte[] notificationSecret()throws Exception{
        if(notificationSecret==null){String encoded=vault.get("notification_hash_secret","");
            if(encoded.isEmpty()){byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);encoded=android.util.Base64.encodeToString(key,android.util.Base64.NO_WRAP);vault.put("notification_hash_secret",encoded);}
            notificationSecret=android.util.Base64.decode(encoded,android.util.Base64.NO_WRAP);
        }return notificationSecret;
    }
    /** Atomic deduplication and conversation assignment; deleting a note does not replay it. */
    public synchronized int captureNotifications(List<WhatsAppNotice> messages,long since,long receivedAt)throws Exception{
        if(messages.isEmpty())return 0;byte[] secret=notificationSecret();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int saved=0;
        try{
            // Entries older than the current admission cutoff can never be imported again.
            db.delete("notification_seen","source_time<=?",new String[]{Long.toString(since)});
            for(WhatsAppNotice m:messages){
                if(!WhatsAppNotice.allowed(m.source,true)||!WhatsAppNotice.newEnough(m.time,since,receivedAt))continue;
                ContentValues seen=new ContentValues();seen.put("fingerprint",m.fingerprint(secret));seen.put("source_time",m.time);
                if(db.insertWithOnConflict("notification_seen",null,seen,SQLiteDatabase.CONFLICT_IGNORE)==-1)continue;
                String thread=m.threadHash(secret),day=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(m.time));
                String sessionId="",previous="",priorDay="";
                try(Cursor c=db.query("notification_threads",new String[]{"session","last_node","day"},"thread_key=?",new String[]{thread},null,null,null)){
                    if(c.moveToFirst()){sessionId=c.isNull(0)?"":c.getString(0);previous=c.isNull(1)?"":c.getString(1);priorDay=c.getString(2);}
                }
                if(sessionId.isEmpty()||!day.equals(priorDay)||session(sessionId).optLong("ended")!=0){
                    JSONObject s=createSession((m.source.equals("com.whatsapp.w4b")?"WhatsApp Business · ":"WhatsApp · ")+m.chat);
                    sessionId=s.getString("id");s.put("created",m.time).put("source","whatsapp_notification").put("source_chat",m.chat).put("source_app",m.source);write("sessions",s,true);
                }
                String body="[WhatsApp · notificação]\nRemetente: "+m.sender+"\nConversa: "+m.chat+"\n\n"+m.text;
                JSONObject n=addNode(sessionId,GraphData.text(m.sender+" · "+m.text.replace('\n',' '),120),body,"note","notification");
                n.put("created",m.time).put("captured_at",receivedAt).put("sender",m.sender).put("source_chat",m.chat).put("source_app",m.source).put("notification_only",true).put("timestamp_source",m.timeSource).put("group",m.group);write("nodes",n,true);
                if(!previous.isEmpty())link(previous,n.getString("id"),"capturada depois","Mesma conversa do WhatsApp, em ordem de captura. A ligação não afirma causa ou concordância.","notification","accepted");
                ContentValues mapping=new ContentValues();mapping.put("thread_key",thread);mapping.put("day",day);mapping.put("session",sessionId);mapping.put("last_node",n.getString("id"));db.insertWithOnConflict("notification_threads",null,mapping,SQLiteDatabase.CONFLICT_REPLACE);saved++;
            }
            db.setTransactionSuccessful();return saved;
        }finally{db.endTransaction();}
    }
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
            JSONArray maps=backup.optJSONArray("daily_maps");
            if(maps!=null)for(int i=0;i<maps.length();i++){JSONObject imported=DailyMapData.remap(maps.getJSONObject(i),map);if(dailyMap(imported.getString("day"),imported.getString("zone"))==null)write("daily_maps",imported,false);}
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}

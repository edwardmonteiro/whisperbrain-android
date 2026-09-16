package com.edward.whisperbrain;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;

/** Plaud-style long-form capture. Local recording is authoritative; AI is an optional parallel layer. */
public final class TopoService extends Service {
    public static final String START="com.edward.whisperbrain.TOPO_START";
    public static final String STOP="com.edward.whisperbrain.TOPO_STOP";
    public static final String ASK="com.edward.whisperbrain.TOPO_ASK";
    public static final String EXTRA_SESSION="session";
    public static final String EXTRA_COACH="coach";
    private static final long SESSION_LIMIT_MS=180L*60_000L;
    private static final long AUDIO_CHUNK_BYTES=18_000_000L;

    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> transcriptItems=new HashSet<>();
    private RealtimeClient api;
    private AudioRecord recorder;
    private PowerManager.WakeLock wake;
    private volatile boolean capturing;
    private volatile int level;
    private boolean stopping, coach;
    private long generation, deadline, lastAdviceAt, lastSpeechStopAt;
    private String sessionId="", apiKey="", model="", goal="";
    private int reconnectAttempts;

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        String action=intent.getAction();
        if(STOP.equals(action))finish("Recording saved.");
        else if(ASK.equals(action))requestAdvice(true);
        else if(START.equals(action)&&!TalkState.active)start(intent);
        return START_NOT_STICKY;
    }

    private Notification notification(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel c=new NotificationChannel("topo","Topo recording",NotificationManager.IMPORTANCE_LOW);
        c.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);nm.createNotificationChannel(c);
        PendingIntent open=PendingIntent.getActivity(this,31,new Intent(this,TopoActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,32,new Intent(this,TopoService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"topo").setSmallIcon(R.drawable.ic_mic).setContentTitle("Topo · recording locally")
                .setContentText(coach?"Local recording · Live Coach when connected":"Local recording · transcription when connected")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
    }

    private void start(Intent intent){
        stopping=false;long id=++generation;coach=intent.getBooleanExtra(EXTRA_COACH,false);sessionId=intent.getStringExtra(EXTRA_SESSION);
        try{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){finish("Microphone permission is required.");return;}
            NotebookStore store=NotebookStore.get(this);
            if(sessionId==null||sessionId.isEmpty())sessionId=store.createSession("Conversation").getString("id");
            store.endSession(sessionId,false);TalkState.reset(sessionId,coach);
            startForeground(33,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            deadline=SystemClock.elapsedRealtime()+SESSION_LIMIT_MS;
            wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Topo:recording");
            wake.acquire(SESSION_LIMIT_MS+60_000L);
            transcriptItems.clear();lastAdviceAt=0;lastSpeechStopAt=0;reconnectAttempts=0;
            startCapture(id);

            Vault vault=new Vault(this);
            apiKey=vault.get("api_key","").trim();
            model=vault.get("topo_realtime_model","gpt-realtime-2.1").trim();
            String template=vault.get("topo_template","Executive meeting");
            goal="Transcribe accurately. Live coach objective: "+TopoTemplates.prompt(template)
                    +" Give brief on-screen suggestions only when useful. Never claim speaker identity unless explicitly stated.";
            if(apiKey.startsWith("sk-")&&!apiKey.matches("(?s).*\\s.*"))connectAi(id);
            else {
                TalkState.connected=false;TalkState.status="Recording locally";
                TalkState.detail="Audio is safe on this phone. Add an OpenAI API key for live transcription.";TalkState.changed();
            }
            main.post(tick);
        }catch(Exception e){finish("Could not start the phone recorder.");}
    }

    private void connectAi(long id){
        if(stopping||!TalkState.active||apiKey.isEmpty())return;
        RealtimeClient next=new RealtimeClient(new RealtimeClient.Listener(){
            @Override public void configured(){post(id,()->{
                if(api==null)return;reconnectAttempts=0;TalkState.connected=true;TalkState.status="Recording";
                TalkState.detail=coach?"Saved locally · Live Coach connected":"Saved locally · live transcription connected";TalkState.changed();
            });}
            @Override public void speechStarted(){post(id,()->{TalkState.status="Recording";TalkState.detail="Speech detected · audio saved locally";TalkState.changed();});}
            @Override public void speechStopped(){post(id,()->{lastSpeechStopAt=SystemClock.elapsedRealtime();TalkState.detail="Listening through the pause · recording continues";TalkState.changed();});}
            @Override public void committed(){post(id,()->{TalkState.turns++;TalkState.changed();});}
            @Override public void responseStarted(){post(id,()->{TalkState.requestInFlight=true;TalkState.changed();});}
            @Override public void transcript(String itemId,String text){post(id,()->saveTranscript(itemId,text));}
            @Override public void transcriptionFailed(){post(id,()->{TalkState.detail="One transcript segment failed; local audio is still recording.";TalkState.changed();});}
            @Override public void answer(String json,long tokens){post(id,()->handleAdvice(json));}
            @Override public void failed(String message){post(id,()->{RealtimeClient failed=api;if(failed!=null)aiFailed(failed,message,id);});}
        });
        api=next;TalkState.status="Recording locally";TalkState.detail="Connecting AI while local recording continues";TalkState.changed();
        try{next.connect(apiKey,model,goal,new JSONArray().toString(),"pt-BR",true);}
        catch(Exception e){aiFailed(next,"Could not open the AI connection.",id);}
    }

    private void aiFailed(RealtimeClient failed,String message,long id){
        if(stopping||generation!=id)return;
        if(api==failed)api=null;
        TalkState.connected=false;TalkState.requestInFlight=false;TalkState.status="Recording locally";
        TalkState.detail="AI disconnected; audio continues safely on this phone.";TalkState.changed();
        if(reconnectAttempts<3){
            int attempt=++reconnectAttempts;main.postDelayed(()->{
                if(TalkState.active&&!stopping&&generation==id&&!TalkState.connected){
                    TalkState.detail="Recording locally · reconnecting AI ("+attempt+"/3)";TalkState.changed();connectAi(id);
                }
            },3000L*attempt);
        }
    }

    @SuppressWarnings("MissingPermission") private void startCapture(long id){
        AudioArchive.Writer first=null;
        try{
            int min=AudioRecord.getMinBufferSize(24000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0){finish("24 kHz microphone input is unavailable.");return;}
            first=new AudioArchive.Writer(this);
            AudioRecord r=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(Math.max(min*2,9600)).build();
            recorder=r;if(r.getState()!=AudioRecord.STATE_INITIALIZED){first.discard();r.release();recorder=null;finish("Microphone initialization failed.");return;}
            for(AudioDeviceInfo d:getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_INPUTS))if(d.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC){r.setPreferredDevice(d);break;}
            r.startRecording();if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING){first.discard();r.release();recorder=null;finish("Another app may be using the microphone.");return;}
            capturing=true;final AudioArchive.Writer initial=first;
            new Thread(()->captureLoop(id,r,initial),"Topo-local-recorder").start();
            TalkState.connected=false;TalkState.status="Recording locally";TalkState.detail="Encrypted local audio is recording";TalkState.changed();
        }catch(Exception e){if(first!=null)first.discard();finish("Local audio storage could not start.");}
    }

    private void captureLoop(long id,AudioRecord r,AudioArchive.Writer first){
        AudioArchive.Writer chunk=first;byte[] pcm=new byte[4800];
        try{
            while(capturing&&generation==id){
                int n=r.read(pcm,0,pcm.length,AudioRecord.READ_BLOCKING);if(n<=0){post(id,()->finish("Microphone capture was interrupted."));break;}
                if(chunk.bytes+n>AUDIO_CHUNK_BYTES){saveChunk(chunk);chunk=new AudioArchive.Writer(this);}
                chunk.write(pcm,n);
                double sum=0;for(int i=0;i+1<n;i+=2){short s=(short)((pcm[i]&255)|(pcm[i+1]<<8));sum+=(double)s*s;}
                double rms=Math.sqrt(sum/Math.max(1,n/2))/32768.0;double db=20*Math.log10(Math.max(.000001,rms));level=Math.max(0,Math.min(100,(int)((db+60)*100/60)));
                RealtimeClient current=api;
                if(current!=null&&TalkState.connected)current.audio(pcm,n);
            }
        }catch(Exception e){post(id,()->finish("Local recording storage failed. Check free phone storage."));}
        finally{
            try{r.release();}catch(Exception ignored){}
            try{saveChunk(chunk);}catch(Exception ignored){}
        }
    }

    private void saveChunk(AudioArchive.Writer chunk)throws Exception{
        if(chunk==null)return;
        chunk.close();
        if(chunk.bytes>0)NotebookStore.get(this).addAudio(sessionId,chunk.id,"pcm24k",chunk.bytes*1000L/48000L);
        else chunk.discard();
    }

    private void saveTranscript(String itemId,String text){
        String clean=text==null?"":text.trim();if(clean.isEmpty()||!transcriptItems.add(itemId))return;
        try{NotebookStore.get(this).addNode(sessionId,"Transcript",clean,"transcript","asr");TalkState.appendTranscript(clean);}
        catch(Exception e){TalkState.detail="Transcript save failed; encrypted local audio continues.";TalkState.changed();}
    }

    private void requestAdvice(boolean manual){
        if(!TalkState.active||!TalkState.connected||api==null||TalkState.requestInFlight){
            if(TalkState.active&&!TalkState.connected){TalkState.detail="AI is offline; local recording is still active.";TalkState.changed();}
            return;
        }
        TalkState.requestInFlight=true;TalkState.status="Recording · Thinking";TalkState.detail=manual?"Analyzing now · recording continues":"Preparing a live caption · recording continues";TalkState.changed();
        api.advise(manual,false);lastAdviceAt=SystemClock.elapsedRealtime();
    }

    private void handleAdvice(String json){
        TalkState.requestInFlight=false;
        try{Advice a=Advice.parse(json);TalkState.context=a.context;TalkState.tip=a.text;
            if(!a.text.isEmpty()){TalkState.suggestions++;NotebookStore.get(this).addNode(sessionId,"Live Coach",a.text,"recommendation","ai");}
            TalkState.status="Recording";TalkState.detail=a.text.isEmpty()?"Listening · local recording continues":"New Live Coach caption · local recording continues";
        }catch(Exception e){TalkState.status="Recording";TalkState.detail="AI response could not be parsed; local recording continues.";}
        TalkState.changed();
    }

    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!TalkState.active||stopping)return;long now=SystemClock.elapsedRealtime();
        if(now>=deadline){finish("3-hour session saved.");return;}
        TalkState.level=level;
        if(coach&&TalkState.connected&&!TalkState.requestInFlight&&lastSpeechStopAt>0&&now-lastSpeechStopAt>900&&now-lastAdviceAt>12000)requestAdvice(false);
        TalkState.changed();main.postDelayed(this,500);
    }};

    private void post(long id,Runnable r){main.post(()->{if(generation==id&&TalkState.active&&!stopping)r.run();});}
    private void finish(String reason){
        if(stopping)return;stopping=true;generation++;capturing=false;main.removeCallbacksAndMessages(null);
        if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder=null;}
        if(api!=null){api.close();api=null;}if(wake!=null){if(wake.isHeld())wake.release();wake=null;}
        try{if(!sessionId.isEmpty())NotebookStore.get(this).endSession(sessionId,true);}catch(Exception ignored){}
        TalkState.stop(reason);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy(){if(!stopping)finish("Recording saved.");super.onDestroy();}
}

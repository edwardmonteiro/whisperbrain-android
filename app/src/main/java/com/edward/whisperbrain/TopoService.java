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

/** Plaud-style long-form capture with text-only live coaching. */
public final class TopoService extends Service {
    public static final String START="com.edward.whisperbrain.TOPO_START";
    public static final String STOP="com.edward.whisperbrain.TOPO_STOP";
    public static final String ASK="com.edward.whisperbrain.TOPO_ASK";
    public static final String EXTRA_SESSION="session";
    public static final String EXTRA_COACH="coach";

    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> transcriptItems=new HashSet<>();
    private RealtimeClient api;
    private AudioRecord recorder;
    private PowerManager.WakeLock wake;
    private volatile boolean capturing;
    private volatile int level;
    private boolean stopping, coach;
    private long generation, deadline, lastAdviceAt, lastSpeechStopAt;
    private String sessionId="";

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        String action=intent.getAction();
        if(STOP.equals(action))finish("Recording stopped.");
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
        return new Notification.Builder(this,"topo").setSmallIcon(R.drawable.ic_mic).setContentTitle("Topo · recording")
                .setContentText(coach?"Live Coach on · transcription active":"Transcription active")
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
            Vault vault=new Vault(this);String key=vault.get("api_key","").trim();
            if(!key.startsWith("sk-")||key.matches("(?s).*\\s.*")){finish("Add your OpenAI API key in Settings.");return;}
            String model=vault.get("topo_realtime_model","gpt-realtime").trim();
            String template=vault.get("topo_template","Executive meeting");
            String goal="Transcribe accurately. Live coach objective: "+TopoTemplates.prompt(template)
                    +" Give brief on-screen suggestions only when useful. Never claim speaker identity unless explicitly stated.";
            JSONArray memory=new JSONArray();
            deadline=SystemClock.elapsedRealtime()+180L*60_000L;
            wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Topo:recording");wake.acquire(181L*60_000L);
            transcriptItems.clear();lastAdviceAt=0;lastSpeechStopAt=0;
            api=new RealtimeClient(new RealtimeClient.Listener(){
                @Override public void configured(){post(id,()->startCapture(id));}
                @Override public void speechStarted(){post(id,()->{TalkState.status="Listening";TalkState.detail="Speech detected";TalkState.changed();});}
                @Override public void speechStopped(){post(id,()->{lastSpeechStopAt=SystemClock.elapsedRealtime();TalkState.detail="Pause detected";TalkState.changed();});}
                @Override public void committed(){post(id,()->{TalkState.turns++;TalkState.changed();});}
                @Override public void responseStarted(){post(id,()->{TalkState.requestInFlight=true;TalkState.changed();});}
                @Override public void transcript(String itemId,String text){post(id,()->saveTranscript(itemId,text));}
                @Override public void transcriptionFailed(){post(id,()->{TalkState.detail="One segment could not be transcribed.";TalkState.changed();});}
                @Override public void answer(String json,long tokens){post(id,()->handleAdvice(json));}
                @Override public void failed(String message){post(id,()->finish(message));}
            });
            TalkState.status="Connecting";TalkState.detail="Opening realtime transcription";TalkState.changed();
            api.connect(key,model,goal,memory.toString(),"pt-BR",true);
            main.post(tick);
        }catch(Exception e){finish("Could not start recording.");}
    }

    @SuppressWarnings("MissingPermission") private void startCapture(long id){
        try{
            int min=AudioRecord.getMinBufferSize(24000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0){finish("24 kHz microphone input is unavailable.");return;}
            AudioRecord r=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(Math.max(min*2,9600)).build();
            recorder=r;if(r.getState()!=AudioRecord.STATE_INITIALIZED){r.release();recorder=null;finish("Microphone initialization failed.");return;}
            for(AudioDeviceInfo d:getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_INPUTS))if(d.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC){r.setPreferredDevice(d);break;}
            r.startRecording();if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING){r.release();recorder=null;finish("Another app may be using the microphone.");return;}
            capturing=true;RealtimeClient live=api;
            new Thread(()->{
                byte[] pcm=new byte[4800];
                try{while(capturing&&generation==id){int n=r.read(pcm,0,pcm.length,AudioRecord.READ_BLOCKING);if(n<=0)break;
                    double sum=0;for(int i=0;i+1<n;i+=2){short s=(short)((pcm[i]&255)|(pcm[i+1]<<8));sum+=(double)s*s;}
                    double rms=Math.sqrt(sum/Math.max(1,n/2))/32768.0;double db=20*Math.log10(Math.max(.000001,rms));level=Math.max(0,Math.min(100,(int)((db+60)*100/60)));
                    if(!live.audio(pcm,n))break;}
                }catch(Exception ignored){}finally{try{r.release();}catch(Exception ignored){}}
            },"Topo-mic").start();
            TalkState.connected=true;TalkState.status="Recording";TalkState.detail=coach?"Live Coach is watching for useful moments":"Transcribing conversation";TalkState.changed();
        }catch(Exception e){finish("Microphone could not start.");}
    }

    private void saveTranscript(String itemId,String text){
        String clean=text==null?"":text.trim();if(clean.isEmpty()||!transcriptItems.add(itemId))return;
        try{NotebookStore.get(this).addNode(sessionId,"Transcript",clean,"transcript","asr");TalkState.appendTranscript(clean);}
        catch(Exception e){finish("Transcript could not be saved.");}
    }

    private void requestAdvice(boolean manual){
        if(!TalkState.active||!TalkState.connected||api==null||TalkState.requestInFlight)return;
        TalkState.requestInFlight=true;TalkState.status="Thinking";TalkState.detail=manual?"Analyzing now":"Preparing a live caption";TalkState.changed();
        api.advise(manual,false);lastAdviceAt=SystemClock.elapsedRealtime();
    }

    private void handleAdvice(String json){
        TalkState.requestInFlight=false;
        try{Advice a=Advice.parse(json);TalkState.context=a.context;TalkState.tip=a.text;
            if(!a.text.isEmpty()){TalkState.suggestions++;NotebookStore.get(this).addNode(sessionId,"Live Coach",a.text,"recommendation","ai");}
            TalkState.status="Recording";TalkState.detail=a.text.isEmpty()?"Listening":"New Live Coach caption";
        }catch(Exception e){TalkState.detail="AI response could not be parsed.";}
        TalkState.changed();
    }

    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!TalkState.active||stopping)return;long now=SystemClock.elapsedRealtime();
        if(now>=deadline){finish("3-hour session limit reached.");return;}
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
    @Override public void onDestroy(){if(!stopping)finish("Recording stopped.");super.onDestroy();}
}

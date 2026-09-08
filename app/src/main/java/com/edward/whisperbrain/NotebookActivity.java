package com.edward.whisperbrain;

import android.Manifest;
import android.app.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.*;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** The notebook works without a key, internet or microphone permission. */
public final class NotebookActivity extends ComponentActivity {
    private final OnBackPressedCallback back=new OnBackPressedCallback(false){@Override public void handleOnBackPressed(){act(()->{if(mode.equals("graph")&&!current.isEmpty())showSession(current);else showHome();});}};
    private NotebookUi ui;
    private NotebookStore store;
    private Vault vault;
    private String current="",mode="home",focus="";
    private EditText editor,search;
    private LinearLayout timeline,sessionList,graphList;
    private TextView aiStatus,sessionMeta,recStatus;
    private Switch across;
    private Button aiButton,recordButton;
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaRecorder recorder;
    private File recording;
    private long recordingStarted;
    private PrivateVoice voice;
    private JSONObject pendingAudio;
    private String pendingSpeech="";
    private boolean backupBusy,visible;
    private Runnable graphSearch;
    private final Runnable saveDraft=()->persistDraft();
    private interface Task{void run()throws Exception;}
    private void act(Task t){try{t.run();}catch(Exception e){message(e.getMessage()==null?"Não foi possível concluir. Seus registros continuam no telefone.":e.getMessage());}}
    private String value(String key,String fallback){try{return vault.get(key,fallback);}catch(Exception e){return fallback;}}
    private String date(long millis){return new SimpleDateFormat("dd MMM yyyy · HH:mm",new Locale("pt","BR")).format(new Date(millis));}
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle state){super.onCreate(state);getOnBackPressedDispatcher().addCallback(this,back);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);getWindow().setDecorFitsSystemWindows(false);ui=new NotebookUi(this);store=NotebookStore.get(this);vault=new Vault(this);
        if(state!=null){current=state.getString("session","");mode=state.getString("mode","home");}
        else current=getIntent().getStringExtra("session")==null?"":getIntent().getStringExtra("session");
        act(()->{if(mode.equals("graph"))showGraph(current);else if(!current.isEmpty())showSession(current);else showHome();});
    }
    @Override public void onSaveInstanceState(Bundle state){persistDraft();state.putString("session",current);state.putString("mode",mode);super.onSaveInstanceState(state);}
    @Override public void onResume(){super.onResume();visible=true;NotebookAi.observer=()->act(()->{if(mode.equals("graph"))refreshGraph();else refresh();});refresh();main.post(refreshLive);}
    @Override public void onPause(){visible=false;persistDraft();main.removeCallbacks(saveDraft);main.removeCallbacks(refreshLive);NotebookAi.observer=null;
        if(recorder!=null)finishRecording();if(voice!=null){voice.shutdown();voice=null;}super.onPause();}
    private final Runnable refreshLive=new Runnable(){public void run(){if(!visible)return;if((SessionState.active||SessionState.savingAudio)&&mode.equals("session"))refresh();main.postDelayed(this,2500);}};
    private void persistDraft(){if(editor!=null&&!current.isEmpty())try{vault.put("draft:"+current,editor.getText().toString());}catch(Exception e){message("Rascunho ainda não foi salvo. Use Salvar neurônio.");}}
    private void clearViews(){if(graphSearch!=null)main.removeCallbacks(graphSearch);editor=null;search=null;timeline=null;sessionList=null;graphList=null;aiStatus=null;sessionMeta=null;aiButton=null;across=null;recordButton=null;recStatus=null;}
    private LinearLayout shell(String title,String caption){clearViews();LinearLayout root=ui.root();root.addView(ui.text("WHISPERBRAIN  /  0.2",14,NotebookUi.TEAL));root.addView(ui.title(title,30));root.addView(ui.text(caption,15,NotebookUi.MUTED));return root;}
    private void showHome()throws Exception {
        if(recorder!=null)finishRecording();persistDraft();current="";mode="home";back.setEnabled(false);focus="";
        LinearLayout root=shell("Seu segundo cérebro", "Conversas viram notas. Notas criam conexões.");
        if(SessionState.active)ui.button(root,"Voltar à sessão com escuta ativa",false,()->act(()->showSession(SessionState.sessionId)));
        LinearLayout hero=ui.card(root);hero.addView(ui.title("Um espaço para cada conversa",20));hero.addView(ui.text("Escreva offline, grave um áudio ou acompanhe uma conversa ao vivo.",16,NotebookUi.MUTED));
        ui.button(hero,"+ Nova sessão",true,this::newSession);
        ui.button(root,"Grafo de todas as conversas",false,()->act(()->showGraph("")));
        search=ui.input(root,"Buscar evento, data ou conteúdo",120,false);sessionList=ui.col();root.addView(sessionList);ui.watch(search,()->act(this::listSessions));
        ui.button(root,"Configurar IA e voz",false,()->startActivity(new Intent(this,MainActivity.class).putExtra("settings",true)));
        ui.button(root,"Exportar caderno e áudios",false,this::exportNotebook);
        ui.button(root,"Importar um backup",false,this::importNotebook);
        root.addView(ui.text("Notas e grafo ficam criptografados neste telefone. A IA recebe contexto somente ao gerar sinapses ou iniciar a escuta.",14,NotebookUi.MUTED));
        listSessions();
    }
    private void listSessions()throws Exception {
        if(sessionList==null)return;sessionList.removeAllViews();String q=search==null?"":search.getText().toString().toLowerCase(Locale.ROOT).trim();
        JSONArray sessions=store.sessions();int count=0;
        for(int i=0;i<sessions.length();i++) {
            JSONObject s=sessions.getJSONObject(i);String id=s.getString("id");JSONArray nodes=store.nodes(id);String hay=s.getString("event")+" "+date(s.getLong("created"));
            if(!q.isEmpty())for(int j=0;j<nodes.length();j++){JSONObject n=nodes.getJSONObject(j);hay+=" "+n.optString("title")+" "+n.optString("body");}
            if(!hay.toLowerCase(Locale.ROOT).contains(q))continue;count++;
            LinearLayout card=ui.card(sessionList);card.addView(ui.text(date(s.getLong("created")),14,NotebookUi.TEAL));card.addView(ui.title("# "+s.getString("event"),21));
            card.addView(ui.text(nodes.length()+" neurônios · "+(s.optLong("ended")==0?"aberta":"encerrada"),14,NotebookUi.MUTED));
            ui.button(card,"Abrir conversa",false,()->act(()->showSession(id)));
        }
        if(count==0)sessionList.addView(ui.text(q.isEmpty()?"Seu caderno começa com a primeira sessão.":"Nenhuma conversa encontrada.",17,NotebookUi.MUTED));
    }
    private void newSession(){LinearLayout body=ui.col();body.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));body.addView(ui.text("A data e a hora são registradas automaticamente.",16,NotebookUi.INK));EditText event=ui.input(body,"Nome do evento · ex.: Planejamento",120,false);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Nova sessão").setView(body).setNegativeButton("Cancelar",null).setPositiveButton("Criar",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->act(()->{JSONObject s=store.createSession(event.getText().toString());d.dismiss();showSession(s.getString("id"));})));d.show();}
    private void showSession(String id)throws Exception {
        if(recorder!=null)finishRecording();persistDraft();JSONObject s=store.session(id);if(!current.equals(id))focus="";current=id;mode="session";back.setEnabled(true);
        LinearLayout root=shell("# "+s.getString("event"),date(s.getLong("created")));
        ui.button(root,"← Todas as sessões",false,()->act(this::showHome));
        sessionMeta=ui.text("",14,NotebookUi.TEAL);root.addView(sessionMeta);
        LinearLayout writing=ui.card(root);writing.addView(ui.title("Novo neurônio",22));writing.addView(ui.text("Uma ideia, um fato ou uma pergunta.",15,NotebookUi.MUTED));
        editor=ui.input(writing,"Escreva aqui. O microfone pode ficar desligado.",20000,true);editor.setText(value("draft:"+current,""));editor.setContentDescription("Texto do novo neurônio");
        ui.watch(editor,()->{main.removeCallbacks(saveDraft);main.postDelayed(saveDraft,800);});
        ui.button(writing,"Salvar neurônio",true,()->act(()->saveEditor()));
        across=new Switch(this);across.setText("Relacionar outras conversas");across.setTextColor(NotebookUi.INK);across.setTextSize(16);across.setChecked(Boolean.parseBoolean(value("across_sessions","true")));writing.addView(across);
        across.setOnCheckedChangeListener((button,checked)->act(()->vault.put("across_sessions",String.valueOf(checked))));
        aiButton=ui.button(writing,"Gerar sinapses com IA",false,()->act(()->{if(!editor.getText().toString().trim().isEmpty())saveEditor();if(focus.isEmpty()){JSONArray n=store.nodes(current);if(n.length()==0)throw new IllegalArgumentException("Salve uma nota primeiro.");focus=n.getJSONObject(n.length()-1).getString("id");}NotebookAi.analyze(this,focus,across.isChecked());refresh();}));
        writing.addView(ui.text("A IA recebe a nota foco, até 14 notas desta sessão e até 10 relacionadas de outras conversas, se ativado.",14,NotebookUi.MUTED));
        aiStatus=ui.text("",16,NotebookUi.AMBER);writing.addView(aiStatus);
        ui.button(root,"Grafo desta conversa",false,()->act(()->showGraph(current)));
        ui.button(root,"Escuta ao vivo · live whispering",false,()->act(this::openLive));
        recordButton=ui.button(root,"Gravar áudio local",false,this::recordPressed);recStatus=ui.text("Até 3 minutos por nota. A gravação para ao sair desta tela.",14,NotebookUi.MUTED);root.addView(recStatus);
        root.addView(ui.title("Linha do tempo",23));timeline=ui.col();root.addView(timeline);
        ui.button(root,s.optLong("ended")==0?"Encerrar esta sessão":"Retomar esta sessão",false,()->act(()->{guardIdle();store.endSession(current,s.optLong("ended")==0);showSession(current);}));
        ui.button(root,"Renomear evento",false,()->editEvent(s));
        ui.button(root,"Excluir esta sessão",false,()->confirmDeleteSession(id));
        refresh();
    }
    private String saveEditor()throws Exception {
        String text=GraphData.required(editor.getText().toString(),20000);String title=GraphData.text(text.split("\n",2)[0],90);
        JSONObject n=store.addNode(current,title,text,"note","user");focus=n.getString("id");editor.setText("");vault.put("draft:"+current,"");refresh();return focus;
    }
    private void refresh(){if(isFinishing()||ui==null)return;act(()->{
        if(aiStatus!=null)aiStatus.setText(NotebookAi.status);if(aiButton!=null){aiButton.setEnabled(!NotebookAi.running);aiButton.setText(NotebookAi.running?"Gerando sinapses…":"Gerar sinapses com IA");}
        if(mode.equals("session")&&timeline!=null){JSONArray nodes=store.nodes(current);sessionMeta.setText(nodes.length()+" neurônios · salvos no telefone"+(SessionState.active&&current.equals(SessionState.sessionId)?" · escuta ativa":""));timeline.removeAllViews();
            for(int i=nodes.length()-1;i>=0;i--)noteCard(timeline,nodes.getJSONObject(i));
            if(nodes.length()==0)timeline.addView(ui.text("Suas notas, transcrições e recomendações aparecerão aqui.",16,NotebookUi.MUTED));}
        else if(mode.equals("home")&&sessionList!=null)listSessions();
    });}
    private String kind(JSONObject n){switch(n.optString("kind")){case "audio":return "ÁUDIO LOCAL";case "transcript":return "TRANSCRIÇÃO · IA";case "summary":return "RESUMO · IA";case "recommendation":return "RECOMENDAÇÃO · IA";default:return "NOTA · VOCÊ";}}
    private void noteCard(LinearLayout parent,JSONObject n)throws Exception {
        String id=n.getString("id");LinearLayout card=ui.card(parent);card.addView(ui.text(kind(n)+" · "+date(n.getLong("created")),12,n.optString("origin").equals("ai")?NotebookUi.AMBER:NotebookUi.TEAL));
        card.addView(ui.title(n.getString("title"),19));TextView preview=ui.text(n.getString("body"),16,NotebookUi.INK);preview.setMaxLines(4);card.addView(preview);
        ui.button(card,"Abrir neurônio",false,()->act(()->openNode(id)));
    }
    private void openNode(String id)throws Exception {
        JSONObject n=store.node(id);focus=id;LinearLayout body=ui.col();body.setPadding(ui.dp(20),0,ui.dp(20),ui.dp(20));
        TextView text=ui.text(n.getString("body"),18,NotebookUi.INK);text.setTextIsSelectable(true);body.addView(text);body.addView(ui.text(kind(n)+" · "+date(n.getLong("created")),14,NotebookUi.MUTED));
        if(n.optBoolean("edited_by_user"))body.addView(ui.text("Editado por você; a origem original foi preservada.",14,NotebookUi.MUTED));
        ui.button(body,"Editar texto",false,()->editNode(n));
        if(n.optString("kind").equals("audio"))ui.button(body,"Transcrever áudio com IA",false,()->act(()->NotebookAi.transcribe(this,id)));
        else ui.button(body,"Gerar sinapses deste neurônio",false,()->act(()->NotebookAi.analyze(this,id,Boolean.parseBoolean(value("across_sessions","true")))));
        ui.button(body,"Criar ligação manual",false,()->act(()->chooseLink(id)));
        if(n.optString("kind").equals("audio"))ui.button(body,"Ouvir áudio em saída privada",false,()->play(n,""));
        else if(n.optString("origin").equals("ai"))ui.button(body,"Ouvir dica curta",false,()->play(null,n.optString("whisper",GraphData.text(n.optString("body"),180))));
        JSONArray edges=store.edges();boolean links=false;
        for(int i=0;i<edges.length();i++){JSONObject e=edges.getJSONObject(i);if(e.getString("from").equals(id)||e.getString("to").equals(id)){if(!links){body.addView(ui.title("Sinapses",20));links=true;}edgeCard(body,e);}}
        ui.button(body,"Abrir a conversa de origem",false,()->act(()->showSession(n.getString("session"))));
        ui.button(body,"Excluir neurônio",false,()->new AlertDialog.Builder(this).setTitle("Excluir neurônio?").setMessage("O áudio anexado e suas ligações também serão removidos.").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->act(()->{guardIdle();store.deleteNode(id);refreshGraph();})).show());
        ScrollView scroll=new ScrollView(this);scroll.addView(body);new AlertDialog.Builder(this).setTitle(n.getString("title")).setView(scroll).setPositiveButton("Fechar",null).show();
    }
    private void editNode(JSONObject n){LinearLayout body=ui.col();body.setPadding(ui.dp(20),0,ui.dp(20),ui.dp(20));EditText title=ui.input(body,"Título",160,false),text=ui.input(body,"Anotação",20000,true);title.setText(n.optString("title"));text.setText(n.optString("body"));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Editar neurônio").setView(body).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->act(()->{store.updateNote(n.getString("id"),title.getText().toString(),text.getText().toString());d.dismiss();refreshGraph();})));d.show();}
    private void chooseLink(String from)throws Exception {
        JSONArray nodes=store.nodes(null);ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();for(int i=0;i<nodes.length();i++){JSONObject n=nodes.getJSONObject(i);if(n.getString("id").equals(from))continue;ids.add(n.getString("id"));names.add(n.getString("title")+" · "+store.session(n.getString("session")).getString("event"));}
        if(ids.isEmpty())throw new IllegalArgumentException("Crie outro neurônio para fazer uma ligação.");
        new AlertDialog.Builder(this).setTitle("Conectar com qual neurônio?").setItems(names.toArray(new String[0]),(d,index)->{LinearLayout body=ui.col();body.setPadding(ui.dp(20),0,ui.dp(20),ui.dp(20));EditText relation=ui.input(body,"Relação · ex.: complementa",80,false);relation.setText("relaciona");
            new AlertDialog.Builder(this).setTitle("Nova sinapse").setView(body).setNegativeButton("Cancelar",null).setPositiveButton("Conectar",(dd,w)->act(()->{store.link(from,ids.get(index),relation.getText().toString(),"Ligação criada por você.","user","accepted");message("Sinapse criada.");refreshGraph();})).show();}).setNegativeButton("Cancelar",null).show();
    }
    private void edgeCard(LinearLayout parent,JSONObject e)throws Exception {
        String label=e.getString("relation")+" · "+(e.getString("state").equals("proposed")?"proposta IA":"aceita");
        ui.button(parent,label,false,()->act(()->openEdge(e.getString("id"))));
    }
    private void openEdge(String id)throws Exception {
        JSONArray edges=store.edges();JSONObject found=null;for(int i=0;i<edges.length();i++)if(edges.getJSONObject(i).getString("id").equals(id)){found=edges.getJSONObject(i);break;}
        if(found==null)throw new IllegalArgumentException("Sinapse não encontrada.");final JSONObject e=found;
        String message=store.node(e.getString("from")).getString("title")+"\n↓ "+e.getString("relation")+"\n"+store.node(e.getString("to")).getString("title")+"\n\n"+e.optString("reason");
        AlertDialog.Builder d=new AlertDialog.Builder(this).setTitle(e.getString("state").equals("proposed")?"Revisar sinapse da IA":"Sinapse aceita").setMessage(message).setNegativeButton("Fechar",null)
                .setNeutralButton("Remover ligação",(dd,w)->act(()->{store.deleteEdge(id);refreshGraph();}));
        if(e.getString("state").equals("proposed"))d.setPositiveButton("Aceitar",(dd,w)->act(()->{store.acceptEdge(id);refreshGraph();}));else d.setPositiveButton("Abrir destino",(dd,w)->act(()->openNode(e.getString("to"))));d.show();
    }
    private void refreshGraph()throws Exception {if(mode.equals("graph"))showGraph(current);else refresh();}
    private void showGraph(String session)throws Exception {
        if(recorder!=null)finishRecording();persistDraft();current=session;mode="graph";back.setEnabled(true);LinearLayout root=shell(session.isEmpty()?"Seu grafo completo":"Grafo da conversa",session.isEmpty()?"Conexões entre os seus eventos.":"# "+store.session(session).getString("event"));
        ui.button(root,"← Voltar",false,()->act(()->{if(current.isEmpty())showHome();else showSession(current);}));
        root.addView(ui.text("Verde: suas notas e ligações aceitas. Âmbar: IA; linhas tracejadas são propostas.",14,NotebookUi.MUTED));
        search=ui.input(root,"Filtrar por nota ou evento",120,false);LinearLayout canvas=ui.col();root.addView(canvas);graphList=ui.col();root.addView(graphList);
        Runnable update=()->act(()->{
            JSONArray all=store.nodes(session.isEmpty()?null:session),selected=new JSONArray();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            for(int i=all.length()-1;i>=0;i--){JSONObject n=all.getJSONObject(i);String hay=n.optString("title")+" "+n.optString("body")+" "+store.session(n.getString("session")).optString("event");if(hay.toLowerCase(Locale.ROOT).contains(q)&&selected.length()<180)selected.put(n);}
            JSONArray edges=GraphData.filterEdges(selected,store.edges());canvas.removeAllViews();graphList.removeAllViews();
            canvas.addView(ui.text(selected.length()+" neurônios visíveis · "+edges.length()+" sinapses"+(all.length()>180?" · filtre para explorar outros":""),14,NotebookUi.TEAL));
            BrainGraphView graph=new BrainGraphView(this,selected,edges,new BrainGraphView.Listener(){public void node(String id){act(()->openNode(id));}public void edge(String id){act(()->openEdge(id));}});canvas.addView(graph,new LinearLayout.LayoutParams(-1,ui.dp(420)));
            ui.button(canvas,"Centralizar grafo",false,graph::resetView);graphList.addView(ui.title("Explorar neurônios",22));for(int i=0;i<selected.length();i++)noteCard(graphList,selected.getJSONObject(i));
        });graphSearch=update;ui.watch(search,()->{main.removeCallbacks(update);main.postDelayed(update,250);});update.run();
    }
    private void editEvent(JSONObject session){LinearLayout body=ui.col();body.setPadding(ui.dp(20),0,ui.dp(20),ui.dp(20));EditText field=ui.input(body,"Nome do evento",120,false);field.setText(session.optString("event"));new AlertDialog.Builder(this).setTitle("Renomear evento").setView(body).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->act(()->{store.renameSession(current,field.getText().toString());showSession(current);})).show();}
    private void guardIdle(){if(SessionState.active||SessionState.savingAudio||NotebookAi.running||recorder!=null||backupBusy)throw new IllegalArgumentException("Finalize a escuta, gravação ou análise antes desta operação.");}
    private void confirmDeleteSession(String id){new AlertDialog.Builder(this).setTitle("Excluir a sessão?").setMessage("Notas, áudios e conexões desta conversa serão removidos do telefone.").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->act(()->{guardIdle();store.deleteSession(id);vault.put("draft:"+id,"");editor=null;showHome();})).show();}
    private void openLive()throws Exception {if(recorder!=null)throw new IllegalArgumentException("Finalize o áudio local primeiro.");if(SessionState.active&&!SessionState.sessionId.equals(current))throw new IllegalArgumentException("Já existe outra sessão em escuta. Encerre-a primeiro.");persistDraft();startActivity(new Intent(this,MainActivity.class).putExtra("session",current));}
    private void recordPressed(){if(recorder!=null){finishRecording();return;}if(SessionState.active){message("Encerre a escuta ao vivo antes de gravar uma nota.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},81);return;}act(this::startRecording);}
    private void startRecording()throws Exception {
        if(current.isEmpty()||!mode.equals("session")||SessionState.active)return;
        recording=File.createTempFile("wb-note-",".m4a",getCacheDir());recorder=new MediaRecorder(this);
        try{recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(64000);recorder.setAudioSamplingRate(24000);recorder.setOutputFile(recording.getAbsolutePath());recorder.setMaxDuration(180000);recorder.setOnInfoListener((mr,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)finishRecording();});recorder.prepare();recorder.start();recordingStarted=SystemClock.elapsedRealtime();recordButton.setText("Parar e salvar áudio");recStatus.setText("● Gravando no telefone. Toque em Parar para salvar.");}
        catch(Exception e){recorder.release();recorder=null;recording.delete();recording=null;throw e;}
    }
    private void finishRecording(){MediaRecorder r=recorder;if(r==null)return;recorder=null;File f=recording;recording=null;String attachment="";
        try{r.stop();r.release();try(InputStream in=new FileInputStream(f)){attachment=AudioArchive.save(this,in);}store.addAudio(current,attachment,"m4a",SystemClock.elapsedRealtime()-recordingStarted);message("Áudio salvo nesta conversa.");}
        catch(Exception e){try{r.release();}catch(Exception ignored){}if(!attachment.isEmpty())AudioArchive.delete(this,attachment);message("Áudio não salvo. Grave pelo menos alguns segundos e confira o microfone.");}
        finally{if(f!=null)f.delete();if(recordButton!=null)recordButton.setText("Gravar áudio local");if(recStatus!=null)recStatus.setText("Até 3 minutos por nota. A gravação para ao sair desta tela.");refresh();}
    }
    private void play(JSONObject audio,String speech){pendingAudio=audio;pendingSpeech=speech;
        if(SessionState.active||recorder!=null){message("Finalize a escuta ou gravação antes de ouvir este item.");return;}
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},82);return;}startPlayback();}
    private void startPlayback(){if(SessionState.active||recorder!=null)return;if(voice!=null)voice.shutdown();JSONObject item=pendingAudio;String words=pendingSpeech;
        voice=new PrivateVoice(this,new PrivateVoice.Listener(){public void ready(String route){act(()->{if(voice==null)return;if(item==null)voice.speak(words);else voice.playRecording(AudioArchive.playback(NotebookActivity.this,item.getString("attachment"),item.optString("format","m4a")));});}public void finished(){if(voice!=null)voice.shutdown();voice=null;}public void failed(String text){voice=null;message(text);}});voice.start(value("language","pt-BR"),Boolean.parseBoolean(value("headphones_only","false")));}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==81){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)act(this::startRecording);else message("Você pode continuar usando o caderno por texto.");}else if(code==82)startPlayback();}
    private void exportNotebook(){act(()->{guardIdle();persistDraft();new AlertDialog.Builder(this).setTitle("Exportar caderno").setMessage("O ZIP contém notas Markdown, grafo e áudios legíveis. Escolha onde guardá-lo. A chave da API não é incluída.").setNegativeButton("Cancelar",null).setPositiveButton("Escolher arquivo",(d,w)->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"WhisperBrain-"+new SimpleDateFormat("yyyy-MM-dd-HHmm",Locale.US).format(new Date())+".zip"),91)).show();});}
    private void importNotebook(){act(()->{guardIdle();startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip"),92);});}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;if(request!=91&&request!=92)return;backupBusy=true;message(request==91?"Exportando caderno…":"Importando caderno…");
        android.net.Uri uri=data.getData();new Thread(()->{String outcome;try{if(request==91){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();NotebookBackup.exportAll(this,out);}outcome="Backup exportado com notas, grafo e áudios.";}else{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();int count=NotebookBackup.importAll(this,in);outcome=count+" sessões importadas. As existentes foram preservadas.";}}}catch(Exception e){outcome="Operação não concluída. Confira o arquivo e o espaço livre; um arquivo exportado incompleto não deve ser usado.";}String text=outcome;main.post(()->{backupBusy=false;message(text);if(!isFinishing())refresh();});},"notebook-backup").start();}
}

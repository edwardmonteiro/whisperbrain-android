package com.edward.whisperbrain;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Date-scoped WhatsApp exploration. Data and previous analyses remain usable offline. */
public final class DailyMapActivity extends ComponentActivity {
    private NotebookUi ui;private NotebookStore store;private LinearLayout root;
    private LocalDate day;private String zone;
    private JSONObject input,map;
    private JSONArray notifications=new JSONArray();
    private TextView counts,coverage,status,capture;
    private Button analyze,cancel,next;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private long loadVersion,captureRevision=-1;
    private boolean visible,loading;
    AlertDialog detailDialog;
    private final Runnable poll=new Runnable(){public void run(){if(!visible)return;long revision=WhatsAppCapture.revision();if(revision!=captureRevision){captureRevision=revision;load(false);}if(capture!=null)capture.setText(WhatsAppCapture.status(DailyMapActivity.this));main.postDelayed(this,2500);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);getWindow().setDecorFitsSystemWindows(false);ui=new NotebookUi(this);store=NotebookStore.get(this);
        zone=saved==null?ZoneId.systemDefault().getId():saved.getString("zone",ZoneId.systemDefault().getId());day=saved==null?LocalDate.now(ZoneId.of(zone)):LocalDate.parse(saved.getString("day"));
        renderLoading();
    }
    @Override public void onSaveInstanceState(Bundle state){state.putString("day",day.toString());state.putString("zone",zone);super.onSaveInstanceState(state);}
    @Override public void onResume(){super.onResume();visible=true;DailyMapAi.observer=()->{if(!DailyMapAi.running)load(true);else updateActions();};captureRevision=WhatsAppCapture.revision();load(true);main.post(poll);}
    @Override public void onPause(){visible=false;main.removeCallbacks(poll);DailyMapAi.observer=null;super.onPause();}
    @Override public void onDestroy(){++loadVersion;io.shutdownNow();if(detailDialog!=null)detailDialog.dismiss();super.onDestroy();}
    private void renderLoading(){root=ui.root();root.addView(ui.title("Meu dia",30));root.addView(ui.text("Carregando mensagens salvas…",17,NotebookUi.MUTED));}
    private void load(boolean redraw){
        if(isDestroyed()||io.isShutdown())return;final long version=++loadVersion;String selectedDay=day.toString(),selectedZone=zone;loading=true;updateActions();
        io.execute(()->{try{JSONArray data=store.dailyNotifications(selectedDay,selectedZone);JSONObject selected=DailyMapData.input(data,selectedDay,selectedZone),saved=store.dailyMap(selectedDay,selectedZone);
            main.post(()->{if(isDestroyed()||version!=loadVersion)return;boolean same=map!=null&&saved!=null&&map.optString("id").equals(saved.optString("id"))&&map.optLong("created")==saved.optLong("created");input=selected;notifications=data;map=saved;loading=false;
                if(detailDialog!=null&&saved==null)detailDialog.dismiss();if(redraw||!same)render();else updateActions();});
        }catch(Exception e){main.post(()->{if(isDestroyed()||version!=loadVersion)return;loading=false;input=null;map=null;root=ui.root();root.addView(ui.title("Não foi possível abrir este dia",26));root.addView(ui.text("Tente novamente. Seus registros permanecem no telefone.",17,NotebookUi.MUTED));ui.button(root,"Tentar novamente",true,()->load(true));ui.button(root,"← Voltar",false,this::finish);});}});
    }
    private void changeDay(LocalDate selected){if(loading)return;day=selected;input=null;map=null;renderLoading();load(true);}
    private void chooseDate(){DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->changeDay(LocalDate.of(y,m+1,d)),day.getYear(),day.getMonthValue()-1,day.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();}
    private void render(){try{
        int scroll=0;if(root!=null&&root.getParent() instanceof ScrollView)scroll=((ScrollView)root.getParent()).getScrollY();root=ui.root();
        root.addView(ui.text("WHISPERBRAIN  /  0.4",13,NotebookUi.TEAL));root.addView(ui.title("Meu dia",30));
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);root.addView(nav);
        Button previous=navButton("‹","Dia anterior",()->changeDay(day.minusDays(1)));nav.addView(previous,new LinearLayout.LayoutParams(ui.dp(50),ui.dp(50)));
        Button date=navButton(day.format(DateTimeFormatter.ofPattern("dd MMM yyyy",new Locale("pt","BR"))),"Escolher a data",this::chooseDate);nav.addView(date,new LinearLayout.LayoutParams(0,ui.dp(50),1));
        next=navButton("›","Próximo dia",()->changeDay(day.plusDays(1)));next.setEnabled(day.isBefore(LocalDate.now(ZoneId.of(zone))));nav.addView(next,new LinearLayout.LayoutParams(ui.dp(50),ui.dp(50)));
        counts=ui.title("",18);root.addView(counts);capture=ui.text(WhatsAppCapture.status(this),14,NotebookUi.TEAL);capture.setOnClickListener(v->openCapture());root.addView(capture);
        coverage=ui.text("",14,NotebookUi.MUTED);root.addView(coverage);
        analyze=ui.button(root,map==null?"Analisar meu dia":"Atualizar análise do dia",true,this::analyze);
        status=ui.text("",15,NotebookUi.AMBER);root.addView(status);cancel=ui.button(root,"Cancelar análise",false,DailyMapAi::cancel);
        if(map!=null){
            JSONArray topics=map.getJSONArray("topics");
            if(topics.length()>0){DailyMapView graph=new DailyMapView(this,map,this::showTopic);root.addView(graph,new LinearLayout.LayoutParams(-1,ui.dp(320)));
                root.addView(ui.text("Toque em um tema. Arraste ou amplie. Ligações tracejadas são hipóteses da IA.",14,NotebookUi.MUTED));ui.button(root,"Centralizar mapa",false,graph::resetView);
                root.addView(ui.title("Temas e mensagens de origem",22));
                for(int i=0;i<topics.length();i++){JSONObject topic=topics.getJSONObject(i);LinearLayout card=ui.card(root);card.addView(ui.text("TEMA SUGERIDO · IA",12,NotebookUi.AMBER));card.addView(ui.title(topic.getString("label"),21));card.addView(ui.text(topic.getString("summary"),17,NotebookUi.INK));String id=topic.getString("id");ui.button(card,"Ver "+topic.getJSONArray("source_ids").length()+" mensagens de origem",false,()->showTopic(id));}
                JSONArray links=map.getJSONArray("links");if(links.length()>0)root.addView(ui.title("Conexões entre temas",22));
                for(int i=0;i<links.length();i++){JSONObject link=links.getJSONObject(i);LinearLayout card=ui.card(root);card.addView(ui.title(topicName(link.getString("from"))+" ↔ "+topicName(link.getString("to")),18));card.addView(ui.text(link.getString("label")+" · "+link.getString("reason"),16,NotebookUi.MUTED));ui.button(card,"Conferir evidências da ligação",false,()->showSources("Hipótese entre temas",link.optString("reason"),link.optJSONArray("source_ids")));}
            }else{LinearLayout card=ui.card(root);card.addView(ui.title("Nenhum tema claro nos trechos",22));card.addView(ui.text("A IA não encontrou temas com evidências suficientes. Você pode conferir as notificações ou analisar novamente depois.",17,NotebookUi.MUTED));}
        }else{LinearLayout card=ui.card(root);card.addView(ui.title(notifications.length()==0?"Seu mapa começa com uma mensagem":"Mensagens prontas para análise",22));card.addView(ui.text(notifications.length()==0?"Ative a captura e aguarde uma nova notificação do WhatsApp. Você também pode escolher uma data com mensagens salvas.":"A IA vai agrupar os assuntos dos trechos selecionados. Cada tema permitirá conferir as mensagens de origem.",17,NotebookUi.MUTED));}
        if(notifications.length()>0)ui.button(root,"Ver notificações deste dia",false,this::showDaySources);
        ui.button(root,"Configurar captura do WhatsApp",false,this::openCapture);ui.button(root,"Configurar IA",false,()->startActivity(new Intent(this,MainActivity.class).putExtra("settings",true)));
        root.addView(ui.text("Ao analisar, trechos das mensagens, nomes e horários selecionados são enviados à OpenAI usando sua chave. Há custo de API. Abrir um mapa salvo funciona offline.",14,NotebookUi.MUTED));
        root.addView(ui.text("Somente notificações recebidas. O mapa não comprova leitura, resposta ou participação. Conteúdo oculto, áudios e fotos sem texto não podem ser recuperados. Notas editadas ficam fora da análise. Fuso: "+zone+".",14,NotebookUi.MUTED));
        if(map!=null)ui.button(root,"Excluir análise deste dia",false,()->new AlertDialog.Builder(this).setTitle("Excluir o mapa?").setMessage("As notificações salvas continuam no caderno. Você poderá analisar este dia novamente.").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{if(DailyMapAi.running){toast("Cancele a análise em andamento antes de excluir.");return;}store.deleteDailyMap(day.toString(),zone);load(true);}).show());
        ui.button(root,"← Voltar ao caderno",false,this::finish);updateActions();final int y=scroll;root.post(()->{if(root.getParent() instanceof ScrollView)((ScrollView)root.getParent()).scrollTo(0,y);});
    }catch(Exception e){toast("Não foi possível montar o mapa. Tente abrir o dia novamente.");}}
    private Button navButton(String label,String description,Runnable click){Button button=new Button(this);button.setText(label);button.setTextColor(NotebookUi.INK);button.setTextSize(17);button.setAllCaps(false);button.setContentDescription(description);button.setBackground(ui.bg(NotebookUi.CARD));button.setOnClickListener(v->click.run());return button;}
    private void updateActions(){if(analyze==null||input==null)return;int count=input.optInt("total"),selected=input.optInt("selected");counts.setText(count+(count==1?" mensagem recebida":" mensagens recebidas")+" · "+input.optInt("conversations")+" conversas salvas");
        String info=map==null?"A próxima análise usará "+selected+" de "+count+" mensagens.":"Mapa: "+map.optInt("selected")+" de "+map.optInt("total")+" mensagens da análise.";
        if(map!=null&&!map.optString("input_fingerprint").equals(input.optString("fingerprint")))info+=" Há conteúdo novo ou diferente; atualize quando quiser.";
        int abbreviated=map==null?input.optInt("truncated"):map.optInt("truncated");if(abbreviated>0)info+=" "+abbreviated+" textos abreviados.";
        if(selected<count)info+=" A seleção alterna as mensagens recentes das conversas.";coverage.setText(info);
        analyze.setEnabled(!loading&&!DailyMapAi.running&&selected>0);analyze.setText(DailyMapAi.running?"Análise em andamento…":map==null?"Analisar meu dia · IA":"Atualizar análise do dia · IA");
        String key=DailyMapData.key(day.toString(),zone);String message=key.equals(DailyMapAi.dayKey)?DailyMapAi.status:DailyMapAi.running?"Há uma análise de outro dia em andamento.":"";status.setText(message);status.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);cancel.setVisibility(DailyMapAi.running?View.VISIBLE:View.GONE);
    }
    private void analyze(){if(input==null||loading)return;
        try{String key=new Vault(this).get("api_key","");if(!key.startsWith("sk-")){new AlertDialog.Builder(this).setTitle("Configure a IA de texto").setMessage("A captura é local. Para criar os temas, configure sua chave da API OpenAI; essa análise tem custo.").setNegativeButton("Depois",null).setPositiveButton("Configurar IA",(d,w)->startActivity(new Intent(this,MainActivity.class).putExtra("settings",true))).show();return;}
            JSONObject selected=new JSONObject(input.toString());new AlertDialog.Builder(this).setTitle("Analisar "+selected.getInt("selected")+" mensagens?")
                    .setMessage("Trechos das notificações de "+day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+", nomes e horários serão enviados à OpenAI com sua chave. A captura continua local. Há custo de API.")
                    .setNegativeButton("Cancelar",null).setPositiveButton("Analisar",(d,w)->{try{DailyMapAi.analyze(this,selected);updateActions();}catch(Exception e){toast(e.getMessage());}}).show();
        }catch(Exception e){toast("Não foi possível preparar a análise. Confira Configurar IA.");}}
    private String topicName(String id){JSONArray topics=map.optJSONArray("topics");for(int i=0;i<topics.length();i++){JSONObject topic=topics.optJSONObject(i);if(id.equals(topic.optString("id")))return topic.optString("label");}return id;}
    void showTopic(String id){try{if(map==null)return;JSONArray topics=map.getJSONArray("topics");for(int i=0;i<topics.length();i++){JSONObject topic=topics.getJSONObject(i);if(id.equals(topic.getString("id"))){showSources(topic.getString("label"),topic.getString("summary")+"\n\nPor que aparece: "+topic.getString("reason"),topic.getJSONArray("source_ids"));return;}}}catch(Exception e){toast("Não foi possível abrir o tema.");}}
    private void showDaySources(){try{JSONArray ids=new JSONArray();for(int i=notifications.length()-1;i>=Math.max(0,notifications.length()-120);i--)ids.put(notifications.getJSONObject(i).getString("id"));showSources("Notificações recebidas",notifications.length()>120?"120 mensagens mais recentes. As demais continuam no caderno.":"Mensagens salvas neste dia. A sua participação não foi identificada.",ids);}catch(Exception e){toast("Não foi possível abrir as notificações.");}}
    private void showSources(String title,String explanation,JSONArray ids){try{
        LinearLayout body=ui.col();body.setPadding(ui.dp(16),0,ui.dp(16),ui.dp(16));body.addView(ui.text(explanation,17,NotebookUi.INK));body.addView(ui.text("Confira as fontes. Interpretações da IA podem conter erros.",14,NotebookUi.AMBER));
        for(int i=0;i<ids.length();i++){JSONObject source=store.node(ids.getString(i));LinearLayout card=ui.card(body);String time=Instant.ofEpochMilli(source.getLong("created")).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("HH:mm"));
            card.addView(ui.text(time+" · "+source.optString("source_chat"),14,NotebookUi.TEAL));TextView text=ui.text(source.getString("body"),17,NotebookUi.INK);text.setTextIsSelectable(true);card.addView(text);String session=source.getString("session");ui.button(card,"Abrir conversa salva",false,()->startActivity(new Intent(this,NotebookActivity.class).putExtra("session",session)));}
        ScrollView scroll=new ScrollView(this);scroll.addView(body);if(detailDialog!=null)detailDialog.dismiss();detailDialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Fechar",null).create();detailDialog.show();detailDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }catch(Exception e){toast("Uma mensagem de origem mudou ou foi removida. Atualize o dia.");load(true);}}
    private void openCapture(){startActivity(new Intent(this,WhatsAppActivity.class));}
    private void toast(String text){Toast.makeText(this,text==null?"Não foi possível concluir.":text,Toast.LENGTH_LONG).show();}
}

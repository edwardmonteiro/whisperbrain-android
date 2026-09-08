package com.edward.whisperbrain;

import android.content.Context;
import android.os.*;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One user-requested analysis at a time; opening a day or receiving a notification never calls the API. */
public final class DailyMapAi {
    private static final Object lock=new Object();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final OkHttpClient http=new OkHttpClient.Builder().callTimeout(90,TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
    public static volatile boolean running;
    public static volatile String status="",dayKey="";
    public static Runnable observer;
    private static long generation;
    private static Call call;
    private DailyMapAi() {}
    private static void changed(){main.post(()->{if(observer!=null)observer.run();});}
    public static void analyze(Context context,JSONObject input)throws Exception {
        if(input.getInt("selected")==0)throw new IllegalArgumentException("Ainda não há notificações recebidas neste dia.");
        Context app=context.getApplicationContext();Vault vault=new Vault(app);String key=vault.get("api_key","").trim(),model=vault.get("text_model","gpt-4.1-mini").trim();
        if(!key.startsWith("sk-")||key.matches("(?s).*\\s.*"))throw new IllegalArgumentException("Configure uma chave da API de texto em Configurar IA.");
        if(!model.matches("[A-Za-z0-9._-]{1,100}"))throw new IllegalArgumentException("Confira o modelo de texto em Configurar IA.");
        JSONObject snapshot=new JSONObject(input.toString());
        Request request=new Request.Builder().url("https://api.openai.com/v1/responses").header("Authorization","Bearer "+key)
                .post(RequestBody.create(DailyMapProtocol.request(model,snapshot).toString(),MediaType.get("application/json"))).build();
        final long job;final Call pending;
        synchronized(lock){if(running)throw new IllegalArgumentException("Uma análise do dia já está em andamento.");job=++generation;running=true;dayKey=DailyMapData.key(input.getString("day"),input.getString("zone"));status="Analisando "+input.getInt("selected")+" mensagens… Até 90 segundos.";call=http.newCall(request);pending=call;}
        changed();
        pending.enqueue(new Callback(){
            @Override public void onFailure(Call c,IOException error){finish(job,"A API não respondeu. Confira sua conexão e tente novamente; suas mensagens continuam salvas.");}
            @Override public void onResponse(Call c,Response response){
                try(response){
                    if(!response.isSuccessful()){
                        int code=response.code();finish(job,code==429?"Limite ou saldo da API. Confira seu faturamento.":code==401?"Chave recusada pela API. Confira Configurar IA.":"A API recusou a análise (HTTP "+code+"). Confira chave e modelo.");return;
                    }
                    if(response.body()==null||response.body().contentLength()>500000)throw new IOException();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try(InputStream in=response.body().byteStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>500000)throw new IOException();bytes.write(buffer,0,n);}}
                    JSONObject reply=new JSONObject(bytes.toString("UTF-8"));if(!"completed".equals(reply.optString("status")))throw new IOException();
                    Set<String> allowed=new HashSet<>();JSONArray sent=snapshot.getJSONArray("messages");for(int i=0;i<sent.length();i++)allowed.add(sent.getJSONObject(i).getString("id"));
                    JSONObject parsed=DailyMapData.parse(RealtimeClient.responseText(reply,""),allowed);
                    synchronized(lock){if(job!=generation)return;NotebookStore.get(app).saveDailyMap(DailyMapData.result(parsed,snapshot,model,System.currentTimeMillis()),snapshot.getJSONArray("originals"));finish(job,"Mapa salvo no telefone. Os temas são interpretações da IA; confira as mensagens de origem.");}
                }catch(IllegalArgumentException error){finish(job,"Não foi possível salvar: "+error.getMessage());}
                catch(Exception error){finish(job,"Não foi possível validar o mapa. Tente novamente; o mapa anterior, se existir, foi preservado.");}
            }
        });
    }
    private static void finish(long job,String message){synchronized(lock){if(job!=generation)return;running=false;call=null;status=message;}changed();}
    public static void cancel(){synchronized(lock){if(!running)return;++generation;if(call!=null)call.cancel();call=null;running=false;status="Análise cancelada. A API pode já ter processado o pedido.";}changed();}
}

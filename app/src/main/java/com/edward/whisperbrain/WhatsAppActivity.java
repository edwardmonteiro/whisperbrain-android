package com.edward.whisperbrain;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.text.SimpleDateFormat;
import java.util.*;

/** A visible opt-in and pause control for notification-only local capture. */
public final class WhatsAppActivity extends ComponentActivity {
    private NotebookUi ui;private TextView state,counts,warning;private Button toggle;
    private final Handler main=new Handler(Looper.getMainLooper());private boolean visible;
    private final Runnable poll=new Runnable(){public void run(){if(!visible)return;refresh();main.postDelayed(this,1500);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);getWindow().setDecorFitsSystemWindows(false);ui=new NotebookUi(this);
        LinearLayout root=ui.root();root.addView(ui.text("WHISPERBRAIN  /  0.3",14,NotebookUi.TEAL));root.addView(ui.title("WhatsApp",30));
        root.addView(ui.text("Novas mensagens no seu caderno, mesmo com o microfone desligado.",17,NotebookUi.MUTED));
        LinearLayout card=ui.card(root);state=ui.title("",22);card.addView(state);counts=ui.text("",15,NotebookUi.MUTED);card.addView(counts);
        toggle=ui.button(card,"Ativar captura",true,()->{try{boolean enable=!WhatsAppCapture.enabled(this);WhatsAppCapture.setEnabled(this,enable);refresh();if(enable&&!WhatsAppCapture.permitted(this))permission();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});
        ui.button(card,"Permissão de notificações no Android",false,this::permission);
        Switch business=new Switch(this);business.setText("Incluir WhatsApp Business");business.setTextSize(16);business.setTextColor(NotebookUi.INK);business.setChecked(WhatsAppCapture.business(this));card.addView(business);
        business.setOnCheckedChangeListener((b,on)->{try{WhatsAppCapture.setBusiness(this,on);refresh();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});
        warning=ui.text("",15,NotebookUi.AMBER);root.addView(warning);
        ui.button(root,"Ver conversas salvas",false,()->{startActivity(new Intent(this,NotebookActivity.class).putExtra("query","WhatsApp"));finish();});
        root.addView(ui.title("Como funciona",22));
        root.addView(ui.text("Cada conversa ganha uma sessão por dia, com nome, remetente e horário. Mensagens repetidas são filtradas. Pausar preserva as notas já salvas.",16,NotebookUi.INK));
        root.addView(ui.text("O Android concede acesso às notificações. O app filtra apenas o WhatsApp e, se escolhido, o WhatsApp Business. Outros aplicativos são ignorados.",15,NotebookUi.MUTED));
        root.addView(ui.text("A captura considera mensagens posteriores à ativação. Mensagens durante a pausa e conteúdo oculto ficam fora. Falhas de conexão podem causar lacunas. Suas respostas podem não aparecer. Áudios e fotos aparecem somente como texto da notificação; os arquivos não são recebidos.",15,NotebookUi.MUTED));
        root.addView(ui.text("As notas ficam locais. A IA recebe contexto apenas quando você pede uma análise ou inicia a escuta ao vivo.",15,NotebookUi.MUTED));
        ui.button(root,"Reconectar captura",false,()->{WhatsAppCapture.reconnect(this);refresh();});
        ui.button(root,"Copiar diagnóstico do WhatsApp",false,()->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Diagnóstico",WhatsAppCapture.diagnostics(this)));Toast.makeText(this,"Diagnóstico copiado, sem mensagens ou nomes.",Toast.LENGTH_LONG).show();});
        ui.button(root,"Ajuda: configuração restrita",false,()->new AlertDialog.Builder(this).setTitle("Permissão bloqueada pelo Android?").setMessage("Se aparecer Configuração restrita, abra Informações do app → menu ⋮ → Permitir configurações restritas, quando essa opção estiver disponível. Depois volte à permissão de notificações. O Android pode pedir sua autenticação.").setNegativeButton("Fechar",null).setPositiveButton("Informações do app",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).show());
        ui.button(root,"← Voltar ao caderno",false,this::finish);refresh();
    }
    private void permission(){try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,WhatsAppCapture.component(this).flattenToString()));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}}
    @Override public void onResume(){super.onResume();visible=true;WhatsAppCapture.reconnect(this);main.post(poll);}
    @Override public void onPause(){visible=false;main.removeCallbacks(poll);super.onPause();}
    private void refresh(){state.setText(WhatsAppCapture.status(this));toggle.setText(WhatsAppCapture.enabled(this)?"Pausar captura":"Ativar captura");SharedPreferences p=WhatsAppCapture.prefs(this);long last=p.getLong("last_saved",0);
        counts.setText(p.getLong("saved",0)+" mensagens salvas desde a instalação"+(last==0?"\nAguardando uma nova mensagem.":"\nÚltima: "+new SimpleDateFormat("dd/MM · HH:mm:ss",new Locale("pt","BR")).format(new Date(last))));warning.setText(p.getString("error",""));}
}

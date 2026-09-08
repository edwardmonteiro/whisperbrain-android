package com.edward.whisperbrain;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import java.util.*;

/** Parses current message entries only. Historic entries, summaries and other apps are ignored. */
public final class WhatsAppNotificationParser {
    private WhatsAppNotificationParser(){}
    private static String text(CharSequence value,int limit){return GraphData.text(value==null?"":value.toString(),limit);}
    private static String personKey(Person p){if(p==null)return "";if(p.getKey()!=null)return p.getKey();if(p.getUri()!=null)return p.getUri();return text(p.getName(),200);}
    private static boolean isSelf(Person sender,Person self){
        if(sender==null)return true;
        if(self==null)return false;
        return sender.getKey()!=null&&sender.getKey().equals(self.getKey())||sender.getUri()!=null&&sender.getUri().equals(self.getUri());
    }
    public static List<WhatsAppNotice> parse(StatusBarNotification sbn,long since,long now,boolean business){
        List<WhatsAppNotice> out=new ArrayList<>();if(sbn==null||!WhatsAppNotice.allowed(sbn.getPackageName(),business))return out;
        Notification n=sbn.getNotification();
        if(n==null||(n.flags&(Notification.FLAG_GROUP_SUMMARY|Notification.FLAG_ONGOING_EVENT))!=0||!WhatsAppNotice.newEnough(sbn.getPostTime(),since,now))return out;
        if(Notification.CATEGORY_CALL.equals(n.category)||Notification.CATEGORY_TRANSPORT.equals(n.category)||Notification.CATEGORY_SERVICE.equals(n.category))return out;
        Bundle extras=n.extras;if(extras==null)return out;
        String thread=sbn.getUser().getIdentifier()+":"+(n.getShortcutId()!=null?"shortcut:"+n.getShortcutId():"notification:"+sbn.getKey());
        NotificationCompat.MessagingStyle style=NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
        if(style!=null){
            String chat=text(style.getConversationTitle(),100);if(chat.isEmpty())chat=text(extras.getCharSequence(Notification.EXTRA_TITLE),100);
            for(NotificationCompat.MessagingStyle.Message m:style.getMessages()){
                Person sender=m.getPerson();if(isSelf(sender,style.getUser())||!WhatsAppNotice.newEnough(m.getTimestamp(),since,now))continue;
                String name=text(sender.getName(),160),body=text(m.getText(),18000);
                if(body.isEmpty()&&m.getDataMimeType()!=null)body=m.getDataMimeType().startsWith("audio/")?"[Áudio indicado na notificação. O arquivo não foi capturado.]":"[Anexo indicado na notificação. O arquivo não foi capturado.]";
                if(body.isEmpty())continue;if(name.isEmpty())name="Remetente não informado";
                String title=chat.isEmpty()?name:chat;
                out.add(new WhatsAppNotice(sbn.getPackageName(),thread,title,name,personKey(sender),body,m.getTimestamp(),style.isGroupConversation(),"message"));
                if(out.size()>=100)break;
            }
        }else if(Notification.CATEGORY_MESSAGE.equals(n.category)){
            // A legacy notification has no per-message timestamps. Accept only its latest plain text.
            CharSequence[] lines=extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null&&lines.length>1)return out;
            String chat=text(extras.getCharSequence(Notification.EXTRA_TITLE),100);
            String body=text(extras.getCharSequence(Notification.EXTRA_TEXT),18000);
            if(body.isEmpty())body=text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT),18000);
            if(chat.isEmpty()||chat.equalsIgnoreCase("WhatsApp")||chat.equalsIgnoreCase("WhatsApp Business")||placeholder(body)||!WhatsAppNotice.newEnough(n.when,since,now))return out;
            out.add(new WhatsAppNotice(sbn.getPackageName(),thread,chat,"Remetente não informado",chat,body,n.when,false,"notification"));
        }
        out.sort(Comparator.comparingLong(m->m.time));return out;
    }
    private static boolean placeholder(String body){String value=body.trim().toLowerCase(Locale.ROOT);return value.isEmpty()||value.matches("(?:\\d+ )?(?:novas? mensagens?|mensagens? novas?|new messages?)")||Set.of("conteúdo oculto","conteúdo sensível oculto","content hidden","sensitive content hidden").contains(value);}
}

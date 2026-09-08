package com.edward.whisperbrain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Only the message fields needed by the local notebook; no notification actions or media URIs. */
public final class WhatsAppNotice {
    public final String source,thread,chat,sender,senderKey,text,timeSource;
    public final long time;
    public final boolean group;
    public WhatsAppNotice(String source,String thread,String chat,String sender,String senderKey,String text,long time,boolean group,String timeSource){
        this.source=source;this.thread=thread;this.chat=chat;this.sender=sender;this.senderKey=senderKey;
        this.text=text;this.time=time;this.group=group;this.timeSource=timeSource;
    }
    public static boolean allowed(String source,boolean business){return "com.whatsapp".equals(source)||(business&&"com.whatsapp.w4b".equals(source));}
    public static boolean newEnough(long time,long since,long now){return since>0&&time>since&&time<=now+300000L;}
    public String threadHash(byte[] key)throws Exception{return hash(key,"thread",source,thread);}
    public String fingerprint(byte[] key)throws Exception{return hash(key,"message",source,thread,Long.toString(time),senderKey,text);}
    static String hash(byte[] key,String... values)throws Exception{
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        for(String s:values){byte[] bytes=s.getBytes(StandardCharsets.UTF_8);mac.update(ByteBuffer.allocate(4).putInt(bytes.length).array());mac.update(bytes);}
        StringBuilder out=new StringBuilder();for(byte b:mac.doFinal())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
}

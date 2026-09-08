package com.edward.whisperbrain;

import org.junit.Test;
import static org.junit.Assert.*;

public class WhatsAppNoticeTest {
    private WhatsAppNotice notice(String source,String thread,String text,long time){return new WhatsAppNotice(source,thread,"Nome","Pessoa","sender",text,time,false,"message");}
    @Test public void allowsPersonalWhatsAppAndOnlyOptedInBusiness(){assertTrue(WhatsAppNotice.allowed("com.whatsapp",false));assertFalse(WhatsAppNotice.allowed("com.whatsapp.w4b",false));assertTrue(WhatsAppNotice.allowed("com.whatsapp.w4b",true));assertFalse(WhatsAppNotice.allowed("com.whatsapp.fake",true));assertFalse(WhatsAppNotice.allowed("com.android.messaging",true));}
    @Test public void rejectsHistoryAndInvalidFutureTimestamps(){assertFalse(WhatsAppNotice.newEnough(1000,1000,2000));assertFalse(WhatsAppNotice.newEnough(900,1000,2000));assertTrue(WhatsAppNotice.newEnough(1100,1000,2000));assertFalse(WhatsAppNotice.newEnough(400000,1000,2000));assertFalse(WhatsAppNotice.newEnough(1000,0,2000));}
    @Test public void repeatedMessagesHaveStablePrivateFingerprints()throws Exception{byte[] key=new byte[32];WhatsAppNotice n=notice("com.whatsapp","chat","Texto privado",1100);assertEquals(n.fingerprint(key),notice("com.whatsapp","chat","Texto privado",1100).fingerprint(key));assertEquals(64,n.fingerprint(key).length());assertFalse(n.fingerprint(key).contains("Texto"));}
    @Test public void identicalTextAtDifferentTimesIsANewMessage()throws Exception{byte[] key=new byte[32];assertNotEquals(notice("com.whatsapp","chat","ok",1100).fingerprint(key),notice("com.whatsapp","chat","ok",1200).fingerprint(key));}
    @Test public void contactsAccountsAndInstallationsRemainSeparate()throws Exception{byte[] first=new byte[32],second=new byte[32];second[0]=1;WhatsAppNotice n=notice("com.whatsapp","chat-a","ok",1100);assertNotEquals(n.threadHash(first),notice("com.whatsapp","chat-b","ok",1100).threadHash(first));assertNotEquals(n.threadHash(first),notice("com.whatsapp.w4b","chat-a","ok",1100).threadHash(first));assertNotEquals(n.fingerprint(first),n.fingerprint(second));}
    @Test public void hashPartsCannotCollideByConcatenation()throws Exception{assertNotEquals(WhatsAppNotice.hash(new byte[32],"ab","c"),WhatsAppNotice.hash(new byte[32],"a","bc"));}
}

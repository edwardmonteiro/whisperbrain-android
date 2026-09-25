package com.edward.datahub;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class UsageMetricRulesTest {
    @Test public void lockedPhoneDownloadsDoNotBecomeScreenTime(){
        assertEquals("background",UsageMetricRules.classifyEvent("DOWNLOAD",false,false,false));
        List<UsageMetricRules.Interval> s=Arrays.asList(new UsageMetricRules.Interval(0,30*60*1000,false,"background"));
        assertEquals(0,UsageMetricRules.humanScreenSeconds(s));
    }

    @Test public void fiveMinuteWhatsAppUnlockSessionCountsFiveMinutes(){
        List<UsageMetricRules.Interval> s=Arrays.asList(new UsageMetricRules.Interval(0,5*60*1000,true,"human"));
        assertEquals(300,UsageMetricRules.humanScreenSeconds(s));
        assertEquals("human",UsageMetricRules.classifyEvent("DEVICE_UNLOCK",true,true,false));
    }

    @Test public void whatsappChromeWhatsappCreatesTwoContextSwitches(){
        assertEquals(2,UsageMetricRules.contextSwitches(Arrays.asList("com.whatsapp","com.android.chrome","com.whatsapp")));
    }

    @Test public void spotifyWithScreenOffIsNotScreenTime(){
        List<UsageMetricRules.Interval> s=Arrays.asList(new UsageMetricRules.Interval(0,20*60*1000,false,"background"));
        assertEquals(0,UsageMetricRules.humanScreenSeconds(s));
    }

    @Test public void hundredLockedNotificationsAreBackgroundOnly(){
        for(int i=0;i<100;i++) assertEquals("background",UsageMetricRules.classifyEvent("NOTIFICATION_RECEIVED",false,false,false));
        assertFalse(UsageMetricRules.countsAsScreenTime("background",false,"NOTIFICATION_RECEIVED"));
    }
}

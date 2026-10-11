package com.edward.datahub;

import org.junit.Test;
import static org.junit.Assert.*;

public class PathPrivacyGuardTest {
    @Test public void passwordFieldIsSensitive(){
        assertTrue(PathPrivacyGuard.sensitiveField("login_password",true,true));
        assertEquals("[REDACTED]",PathPrivacyGuard.sanitizeText("secret123",true));
    }

    @Test public void editableFieldIsAlwaysRedacted(){
        assertTrue(PathPrivacyGuard.sensitiveField("search_box",false,true));
        assertEquals("[REDACTED]",PathPrivacyGuard.sanitizeText("produto qualquer",true));
    }

    @Test public void otpLikeTextIsRedacted(){
        assertEquals("[REDACTED]",PathPrivacyGuard.sanitizeText("483921",false));
    }

    @Test public void cardLikeNumberIsRedacted(){
        assertEquals("[REDACTED]",PathPrivacyGuard.sanitizeText("4111 1111 1111 1111",false));
    }

    @Test public void ordinaryLabelMayRemain(){
        assertEquals("Search",PathPrivacyGuard.sanitizeText("Search",false));
    }
}

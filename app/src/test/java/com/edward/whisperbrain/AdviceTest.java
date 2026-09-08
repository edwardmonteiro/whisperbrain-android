package com.edward.whisperbrain;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdviceTest {
    @Test public void silenceIsAValidSuccessfulResult() throws Exception {
        Advice result = Advice.parse("{\"context\":\"The group is reviewing a proposal.\",\"speak\":false,\"advice\":\"\",\"memory\":\"\"}");
        assertFalse(result.speak); assertEquals("", result.memory);
    }
    @Test public void absenceOfSpeakFlagFailsClosed() throws Exception {
        assertFalse(Advice.parse("{\"advice\":\"Ask who owns the next step.\"}").speak);
    }
    @Test public void emptyAdviceCannotBeSpoken() throws Exception {
        assertFalse(Advice.parse("{\"speak\":true,\"advice\":\"   \"}").speak);
    }
    @Test public void longMonologueIsNotReadAloud() throws Exception {
        String longText = String.join(" ", java.util.Collections.nCopies(25, "word"));
        assertFalse(Advice.parse("{\"speak\":true,\"advice\":\"" + longText + "\"}").speak);
    }
    @Test public void validNudgeRetainsAccents() throws Exception {
        Advice result = Advice.parse("{\"speak\":true,\"advice\":\"Pergunte qual decisão precisa ser tomada.\"}");
        assertTrue(result.speak); assertTrue(result.text.contains("decisão"));
    }
    @Test(expected = Exception.class) public void invalidOutputIsRejected() throws Exception {
        Advice.parse("Here is what I think you should do...");
    }
}

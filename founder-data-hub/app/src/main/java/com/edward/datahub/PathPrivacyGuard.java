package com.edward.datahub;

import android.content.*;
import java.util.*;
import java.util.regex.*;

public class PathPrivacyGuard {
    private static final Set<String> DEFAULT_PATTERNS=new LinkedHashSet<>(Arrays.asList(
            "authenticator","bitwarden","onepassword","1password","lastpass","keepass",
            "wallet","payments","nubank","itau","bradesco","santander","caixa","banco","bank",
            "com.google.android.gm","com.microsoft.office.outlook","com.samsung.android.email",
            "com.whatsapp","org.telegram.messenger","org.thoughtcrime.securesms",
            "com.google.android.apps.messaging","com.samsung.android.messaging","com.android.mms",
            "com.openai.chatgpt","com.anthropic.claude",
            "whatsapp","telegram","signal","messages","gmail","outlook"
    ));

    public static boolean defaultBlocked(String pkg){
        if(pkg==null)return false;
        String p=pkg.toLowerCase(Locale.US);
        for(String pattern:DEFAULT_PATTERNS)if(!pattern.isEmpty()&&p.contains(pattern))return true;
        return false;
    }

    public static boolean isBlocked(Context c,String pkg){
        if(defaultBlocked(pkg))return true;
        if(pkg==null)return false;
        String p=pkg.toLowerCase(Locale.US);
        for(String pattern:patterns(c))if(!pattern.isEmpty()&&p.contains(pattern))return true;
        return false;
    }

    public static Set<String> patterns(Context c){
        LinkedHashSet<String> out=new LinkedHashSet<>(DEFAULT_PATTERNS);
        String custom=c.getSharedPreferences("path_privacy",Context.MODE_PRIVATE).getString("custom_blocklist","");
        if(custom!=null)for(String s:custom.split("[,\n ]+"))if(!s.trim().isEmpty())out.add(s.trim().toLowerCase(Locale.US));
        return out;
    }

    public static void setCustomBlocklist(Context c,String text){
        c.getSharedPreferences("path_privacy",Context.MODE_PRIVATE).edit().putString("custom_blocklist",text==null?"":text).apply();
    }

    public static String customBlocklist(Context c){
        return c.getSharedPreferences("path_privacy",Context.MODE_PRIVATE).getString("custom_blocklist","");
    }

    public static boolean structuralOnly(String pkg){
        if(pkg==null)return false;
        String p=pkg.toLowerCase(Locale.US);
        return p.equals("com.android.systemui") || p.equals("com.sec.android.app.launcher");
    }

    public static boolean sensitiveField(String viewId,boolean password,boolean editable){
        if(password)return true;
        if(editable)return true;
        String v=viewId==null?"":viewId.toLowerCase(Locale.US);
        return v.contains("password")||v.contains("passwd")||v.contains("otp")||v.contains("pin")||
                v.contains("cvv")||v.contains("token")||v.contains("verification")||v.contains("auth_code");
    }

    public static String sanitizeText(CharSequence cs,boolean sensitive){
        if(cs==null)return "";
        if(sensitive)return "[REDACTED]";
        String s=cs.toString();
        if(s.length()>160)s=s.substring(0,160);
        if(Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}").matcher(s).find())return "[REDACTED]";
        if(Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)").matcher(s).find())return "[REDACTED]";
        if(Pattern.compile("(?:\\d[ -]?){10,19}").matcher(s).find())return "[REDACTED]";
        String lower=s.toLowerCase(Locale.US);
        if(lower.contains("current location")||lower.contains("signed in as")||lower.contains("verification code"))return "[REDACTED]";
        return s;
    }
}

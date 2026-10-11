package com.edward.datahub;

import android.content.*;
import java.util.*;
import java.util.regex.*;

public class PathPrivacyGuard {
    private static final Set<String> DEFAULT_PATTERNS=new LinkedHashSet<>(Arrays.asList(
            "authenticator","bitwarden","onepassword","1password","lastpass","keepass",
            "wallet","payments","nubank","itau","bradesco","santander","caixa","banco","bank",
            "whatsapp","telegram","signal","messages","gmail","outlook"
    ));

    public static boolean isBlocked(Context c,String pkg){
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
        if(Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)").matcher(s).find())return "[REDACTED]";
        if(Pattern.compile("(?:\\d[ -]?){13,19}").matcher(s).find())return "[REDACTED]";
        return s;
    }
}

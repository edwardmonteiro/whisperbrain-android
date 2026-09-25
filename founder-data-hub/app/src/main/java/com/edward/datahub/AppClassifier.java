package com.edward.datahub;

import java.util.Locale;

public final class AppClassifier {
    private AppClassifier() {}

    public static String guess(String pkg,String appName) {
        String s=((pkg==null?"":pkg)+" "+(appName==null?"":appName)).toLowerCase(Locale.US);
        if(s.contains("whatsapp")||s.contains("telegram")||s.contains("messages")||s.contains("messenger")||s.contains("signal")) return "Communication";
        if(s.contains("chatgpt")||s.contains("openai")||s.contains("claude")||s.contains("gemini")||s.contains("copilot")) return "AI";
        if(s.contains("gmail")||s.contains("outlook")||s.contains("teams")||s.contains("slack")||s.contains("office")||s.contains("docs")||s.contains("sheets")) return "Work";
        if(s.contains("youtube")||s.contains("netflix")||s.contains("spotify")||s.contains("primevideo")||s.contains("disney")) return "Entertainment";
        if(s.contains("instagram")||s.contains("facebook")||s.contains("tiktok")||s.contains("reddit")||s.contains("twitter")||s.contains("x.com")) return "Social";
        if(s.contains("coursera")||s.contains("duolingo")||s.contains("kindle")||s.contains("udemy")) return "Learning";
        if(s.contains("camera")||s.contains("gallery")||s.contains("photos")||s.contains("canva")||s.contains("capcut")) return "Creation";
        if(s.contains("maps")||s.contains("waze")||s.contains("uber")||s.contains("lyft")) return "Navigation";
        if(s.contains("bank")||s.contains("paypal")||s.contains("wallet")||s.contains("nubank")||s.contains("itau")) return "Finance";
        if(s.contains("health")||s.contains("oura")||s.contains("strava")||s.contains("fit")) return "Health";
        if(s.contains("amazon")||s.contains("shopping")||s.contains("mercadolibre")||s.contains("mercadolivre")) return "Shopping";
        if(s.contains("android")||s.contains("systemui")||s.contains("download")||s.contains("settings")||s.contains("launcher")) return "System";
        if(s.contains("calculator")||s.contains("clock")||s.contains("files")||s.contains("calendar")) return "Utilities";
        return "Unknown";
    }
}

package com.edward.datahub;

import android.database.Cursor;
import java.text.*;
import java.util.*;

public class DailyStoryEngine {
    public String build(EventDb db,long dayTs){
        long start=EventDb.DayBounds.startOf(dayTs), end=EventDb.DayBounds.endOf(dayTs);
        Cursor c=db.inferredEpisodesBetween(start,end);
        ArrayList<String> lines=new ArrayList<>();
        SimpleDateFormat t=new SimpleDateFormat("HH:mm",Locale.getDefault());
        try{
            while(c.moveToNext()){
                double confidence=c.getDouble(10);
                if(confidence<0.50)continue;
                String title=c.getString(6);
                long ts=c.getLong(1);
                String type=c.getString(4);
                String primary=c.getString(8);
                String line;
                if("CAR_CONNECTION".equals(type)){
                    line="Around "+t.format(new Date(ts))+", Android Auto connected. This alone does not prove driving.";
                }else if("CAMERA_USE".equals(type)){
                    line="Around "+t.format(new Date(ts))+", there was sustained camera activity.";
                }else if("MISSED_CALL".equals(type)){
                    line="At "+t.format(new Date(ts))+", a missed phone call was detected.";
                }else if("ALARM".equals(type)){
                    line="Around "+t.format(new Date(ts))+", an alarm sequence was detected.";
                }else if("MESSAGING".equals(type)){
                    line="Around "+t.format(new Date(ts))+", there was a WhatsApp activity burst.";
                }else{
                    line="At "+t.format(new Date(ts))+", "+title.toLowerCase(Locale.US)+" was detected.";
                }
                lines.add(line);
                if(lines.size()>=7)break;
            }
        }finally{c.close();}
        if(lines.isEmpty())return "Not enough evidence to reconstruct a reliable story yet.";
        StringBuilder b=new StringBuilder("Your day\n\n");
        for(String s:lines)b.append(s).append("\n\n");
        b.append("Every statement above is linked to local evidence.");
        return b.toString().trim();
    }
}

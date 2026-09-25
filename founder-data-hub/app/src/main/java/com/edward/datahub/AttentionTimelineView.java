package com.edward.datahub;

import android.content.Context;
import android.database.Cursor;
import android.graphics.*;
import android.view.View;
import java.util.*;

public class AttentionTimelineView extends View {
    private final EventDb db;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private long dayTs=System.currentTimeMillis();

    public AttentionTimelineView(Context c,EventDb db){super(c);this.db=db;setMinimumHeight(dp(170));}

    public void setDay(long ts){dayTs=ts;invalidate();}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        long start=EventDb.DayBounds.startOf(dayTs), end=EventDb.DayBounds.endOf(dayTs);
        float w=getWidth(), h=getHeight();
        paint.setTextSize(dp(10)); paint.setColor(Color.rgb(150,156,164));
        for(int hr=0;hr<24;hr+=3){
            float x=hr/24f*w;
            canvas.drawText(String.format(Locale.US,"%02d",hr),x+dp(2),h-dp(6),paint);
        }

        float top=dp(18), bottom=h-dp(26);
        paint.setColor(Color.rgb(232,255,91));
        Cursor c=db.appSessionsBetween(start,end);
        try{
            while(c.moveToNext()){
                if(!"human".equals(c.getString(7))||c.getInt(6)!=1)continue;
                long s=Math.max(start,c.getLong(1)), e=Math.min(end,c.getLong(2));
                float x1=(s-start)/(float)(end-start)*w;
                float x2=(e-start)/(float)(end-start)*w;
                if(x2-x1<dp(2))x2=x1+dp(2);
                canvas.drawRoundRect(x1,top,x2,bottom,dp(4),dp(4),paint);
            }
        }finally{c.close();}

        paint.setColor(Color.rgb(55,60,66));
        paint.setStrokeWidth(dp(1));
        for(int hr=0;hr<=24;hr+=3){
            float x=hr/24f*w;
            canvas.drawLine(x,top,x,bottom,paint);
        }
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}

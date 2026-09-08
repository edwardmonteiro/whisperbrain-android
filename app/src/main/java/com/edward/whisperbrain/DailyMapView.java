package com.edward.whisperbrain;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** A compact day tree with optional evidenced topic-to-topic links. The screen also provides a text list. */
public final class DailyMapView extends View {
    public interface Listener {void topic(String id);}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final DashPathEffect dashed=new DashPathEffect(new float[]{7,6},0);
    private final Map<String,PointF> points=new LinkedHashMap<>();
    private final Map<String,JSONObject> topics=new LinkedHashMap<>();
    private final Map<String,List<String>> labels=new HashMap<>();
    private final Map<String,RectF> boxes=new HashMap<>();
    private final RectF center=new RectF(-57,-32,57,32);
    private final JSONArray links;
    private final String day;
    private final ScaleGestureDetector pinch;
    private final GestureDetector gestures;
    private float scale=1,dx,dy;
    private boolean fitted;
    public DailyMapView(Context context,JSONObject map,Listener listener)throws Exception {
        super(context);setBackgroundColor(NotebookUi.BG);setContentDescription("Mapa dos temas recebidos no WhatsApp. Toque em um tema, arraste ou amplie com dois dedos. A lista abaixo contém os mesmos temas.");
        day=LocalDate.parse(map.getString("day")).format(DateTimeFormatter.ofPattern("dd MMM",new Locale("pt","BR")));links=map.getJSONArray("links");JSONArray list=map.getJSONArray("topics");
        paint.setTextSize(20);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        for(int i=0;i<list.length();i++){
            JSONObject topic=list.getJSONObject(i);String id=topic.getString("id");int side=i%2,rows=side==0?(list.length()+1)/2:list.length()/2;
            PointF point=new PointF(side==0?-170:170,(i/2-(rows-1)/2f)*112);points.put(id,point);topics.put(id,topic);boxes.put(id,new RectF(point.x-85,point.y-43,point.x+85,point.y+43));
            labels.put(id,wrap(topic.getString("label")));
        }
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){float prior=scale;scale=Math.max(.3f,Math.min(6f,scale*detector.getScaleFactor()));dx=detector.getFocusX()-(detector.getFocusX()-dx)*scale/prior;dy=detector.getFocusY()-(detector.getFocusY()-dy)*scale/prior;invalidate();return true;}});
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event){return true;}
            @Override public boolean onScroll(MotionEvent a,MotionEvent b,float x,float y){if(!pinch.isInProgress()){dx-=x;dy-=y;invalidate();}return true;}
            @Override public boolean onDoubleTap(MotionEvent e){resetView();return true;}
            @Override public boolean onSingleTapUp(MotionEvent e){float x=(e.getX()-dx)/scale,y=(e.getY()-dy)/scale;for(var item:boxes.entrySet())if(item.getValue().contains(x,y)){listener.topic(item.getKey());performClick();return true;}return true;}
        });
    }
    private List<String> wrap(String text){List<String> out=new ArrayList<>();String remain=text.trim();for(int i=0;i<2&&!remain.isEmpty();i++){int count=paint.breakText(remain,true,150,null);if(count<=0)break;if(count<remain.length()){int space=remain.lastIndexOf(' ',count);if(space>0)count=space;}String line=remain.substring(0,count).trim();remain=remain.substring(count).trim();if(i==1&&!remain.isEmpty())line=GraphData.text(line,Math.max(1,line.length()-1))+"…";out.add(line);}return out;}
    public int topicCount(){return topics.size();}
    public void resetView(){fitted=false;invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent e){getParent().requestDisallowInterceptTouchEvent(e.getActionMasked()!=MotionEvent.ACTION_UP&&e.getActionMasked()!=MotionEvent.ACTION_CANCEL);pinch.onTouchEvent(e);gestures.onTouchEvent(e);return true;}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(!fitted){float halfY=100;for(PointF point:points.values())halfY=Math.max(halfY,Math.abs(point.y)+70);scale=Math.min(getWidth()/560f,getHeight()/(2*halfY));dx=getWidth()/2f;dy=getHeight()/2f;fitted=true;}
        canvas.save();canvas.translate(dx,dy);canvas.scale(scale,scale);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f);paint.setColor(NotebookUi.TEAL);paint.setAlpha(155);
        for(PointF point:points.values()){float sign=Math.signum(point.x);path.reset();path.moveTo(sign*57,0);path.cubicTo(sign*84,0,sign*72,point.y,point.x-sign*85,point.y);canvas.drawPath(path,paint);}
        paint.setColor(NotebookUi.AMBER);paint.setAlpha(130);paint.setPathEffect(dashed);
        for(int i=0;i<links.length();i++){JSONObject link=links.optJSONObject(i);PointF a=points.get(link.optString("from")),b=points.get(link.optString("to"));if(a!=null&&b!=null)canvas.drawLine(a.x,a.y,b.x,b.y,paint);}
        paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setColor(Color.rgb(29,62,76));canvas.drawRoundRect(center,16,16,paint);
        paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(20);paint.setColor(NotebookUi.INK);canvas.drawText(day,0,-1,paint);paint.setTextSize(14);paint.setTypeface(Typeface.DEFAULT);paint.setColor(NotebookUi.MUTED);canvas.drawText("WhatsApp",0,21,paint);
        for(var item:points.entrySet()){
            String id=item.getKey();PointF p=item.getValue();paint.setColor(NotebookUi.CARD);canvas.drawRoundRect(boxes.get(id),15,15,paint);paint.setColor(NotebookUi.TEAL);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(20);
            List<String> lines=labels.get(id);for(int i=0;i<lines.size();i++)canvas.drawText(lines.get(i),p.x,p.y-13+i*22,paint);
            paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(14);paint.setColor(NotebookUi.MUTED);int count=topics.get(id).optJSONArray("source_ids").length();canvas.drawText(count+(count==1?" mensagem":" mensagens"),p.x,p.y+31,paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);canvas.restore();
    }
}

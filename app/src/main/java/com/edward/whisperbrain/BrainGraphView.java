package com.edward.whisperbrain;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** Interactive local graph. Text lists in the containing screen provide an accessible alternative. */
public final class BrainGraphView extends View {
    public interface Listener {void node(String id);void edge(String id);}
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String,PointF> points=new LinkedHashMap<>();
    private final Map<String,JSONObject> nodes=new LinkedHashMap<>();
    private final JSONArray edges;
    private final Listener listener;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private float scale=1,dx,dy;
    private boolean fitted;
    private final float density;
    public BrainGraphView(Context c,JSONArray data,JSONArray links,Listener listener)throws Exception {
        super(c);this.edges=links;this.listener=listener;density=getResources().getDisplayMetrics().density;
        setBackgroundColor(NotebookUi.BG);setContentDescription("Grafo local. Arraste, amplie com dois dedos e toque nos neurônios ou sinapses. Lista de notas disponível abaixo.");
        Map<String,List<String>> groups=new LinkedHashMap<>();
        for(int i=0;i<data.length();i++){JSONObject n=data.getJSONObject(i);String id=n.getString("id");nodes.put(id,n);groups.computeIfAbsent(n.getString("session"),k->new ArrayList<>()).add(id);}
        int gi=0;float orbit=groups.size()>1?Math.max(220,groups.size()*55):0;
        for(List<String> group:groups.values()) {
            double ga=2*Math.PI*gi++/Math.max(1,groups.size());float cx=(float)Math.cos(ga)*orbit,cy=(float)Math.sin(ga)*orbit;
            float radius=group.size()>1?Math.max(95,group.size()*14):0;
            for(int i=0;i<group.size();i++){double a=2*Math.PI*i/group.size()-Math.PI/2;points.put(group.get(i),new PointF(cx+(float)Math.cos(a)*radius,cy+(float)Math.sin(a)*radius));}
        }
        scaleDetector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float old=scale;scale=Math.max(.15f,Math.min(5f,scale*d.getScaleFactor()));dx=d.getFocusX()-(d.getFocusX()-dx)*scale/old;dy=d.getFocusY()-(d.getFocusY()-dy)*scale/old;invalidate();return true;}});
        gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
            @Override public boolean onScroll(MotionEvent a,MotionEvent b,float x,float y){if(!scaleDetector.isInProgress()){dx-=x;dy-=y;invalidate();}return true;}
            @Override public boolean onSingleTapUp(MotionEvent e){float x=(e.getX()-dx)/scale,y=(e.getY()-dy)/scale;String nearest=null;double best=Math.max(22,30*density/scale);
                for(var item:points.entrySet()){double d=Math.hypot(x-item.getValue().x,y-item.getValue().y);if(d<best){best=d;nearest=item.getKey();}}
                if(nearest!=null){listener.node(nearest);performClick();return true;}
                for(int i=0;i<edges.length();i++){JSONObject link=edges.optJSONObject(i);PointF from=points.get(link.optString("from")),to=points.get(link.optString("to"));if(from==null||to==null)continue;float vx=to.x-from.x,vy=to.y-from.y;float t=Math.max(0,Math.min(1,((x-from.x)*vx+(y-from.y)*vy)/(vx*vx+vy*vy+.01f)));if(Math.hypot(x-from.x-t*vx,y-from.y-t*vy)<12*density/scale){listener.edge(link.optString("id"));performClick();return true;}}
                return true;
            }
        });
    }
    public int nodeCount(){return nodes.size();}
    public void resetView(){fitted=false;invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent e){getParent().requestDisallowInterceptTouchEvent(e.getActionMasked()!=MotionEvent.ACTION_UP&&e.getActionMasked()!=MotionEvent.ACTION_CANCEL);scaleDetector.onTouchEvent(e);gestures.onTouchEvent(e);return true;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        if(points.isEmpty()){p.setColor(NotebookUi.MUTED);p.setTextSize(16*density);c.drawText("Salve uma nota para iniciar seu grafo.",20*density,getHeight()/2f,p);return;}
        if(!fitted){float maxX=100,maxY=100;for(PointF n:points.values()){maxX=Math.max(maxX,Math.abs(n.x)+65);maxY=Math.max(maxY,Math.abs(n.y)+65);}scale=Math.min(getWidth()/(2*maxX),getHeight()/(2*maxY));dx=getWidth()/2f;dy=getHeight()/2f;fitted=true;}
        c.save();c.translate(dx,dy);c.scale(scale,scale);
        for(int i=0;i<edges.length();i++){JSONObject e=edges.optJSONObject(i);PointF a=points.get(e.optString("from")),b=points.get(e.optString("to"));if(a==null||b==null)continue;
            boolean proposed=e.optString("state").equals("proposed");p.setColor(proposed?NotebookUi.AMBER:NotebookUi.TEAL);p.setAlpha(155);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);p.setPathEffect(proposed?new DashPathEffect(new float[]{7,6},0):null);c.drawLine(a.x,a.y,b.x,b.y,p);p.setPathEffect(null);
            double angle=Math.atan2(b.y-a.y,b.x-a.x);float tx=b.x-(float)Math.cos(angle)*19,ty=b.y-(float)Math.sin(angle)*19;
            c.drawLine(tx,ty,tx-(float)Math.cos(angle-.5)*10,ty-(float)Math.sin(angle-.5)*10,p);c.drawLine(tx,ty,tx-(float)Math.cos(angle+.5)*10,ty-(float)Math.sin(angle+.5)*10,p);
        }
        p.setAlpha(255);p.setStyle(Paint.Style.FILL);
        for(var item:points.entrySet()){PointF at=item.getValue();JSONObject node=nodes.get(item.getKey());boolean ai=node.optString("origin").equals("ai");p.setColor(ai?NotebookUi.AMBER:NotebookUi.TEAL);c.drawCircle(at.x,at.y,16,p);
            p.setColor(NotebookUi.BG);p.setTextSize(14);p.setTextAlign(Paint.Align.CENTER);String glyph=node.optString("kind").equals("audio")?"A":node.optString("kind").equals("transcript")?"T":ai?"IA":"N";c.drawText(glyph,at.x,at.y+5,p);
            p.setColor(NotebookUi.INK);p.setTextSize(13);String label=GraphData.text(node.optString("title"),23);c.drawText(label,at.x,at.y+36,p);
        }
        p.setTextAlign(Paint.Align.LEFT);c.restore();
    }
}

package com.edward.datahub;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.*;
import android.view.accessibility.*;
import org.json.*;

public class PathCaptureAccessibilityService extends AccessibilityService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject lastStableState=null;
    private String lastStablePackage="";
    private String lastActionKey="";
    private long lastActionAt=0;

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        PathSessionStore.ActiveSession session=PathSessionStore.active(this);
        if(session==null)return;

        int type=event.getEventType();

        String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
        long now=System.currentTimeMillis();

        if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            try{
                lastStableState=captureState(pkg);
                lastStablePackage=pkg;
            }catch(Exception ignored){}
        }

        if(PathPrivacyGuard.isBlocked(this,pkg)){
            try{
                JSONObject redacted=new JSONObject();
                redacted.put("session_id",session.id);
                redacted.put("goal",session.goal);
                redacted.put("t_ms",Math.max(0,now-session.startedAt));
                redacted.put("app",pkg);
                redacted.put("screen",safe(event.getClassName()));
                redacted.put("event","redacted_app");
                redacted.put("state_source","a11y");
                redacted.put("outcome","unknown");
                redacted.put("redaction_status","full");
                PathSessionStore.append(this,redacted);
            }catch(Exception ignored){}
            return;
        }

        if(!supported(type))return;

        try{
            JSONObject action=actionFrom(event);
            String actionKey=pkg+"|"+action.optString("type","")+"|"+String.valueOf(action.opt("target_node"));
            if(actionKey.equals(lastActionKey) && now-lastActionAt<180)return;
            lastActionKey=actionKey;
            lastActionAt=now;

            JSONObject current=captureState(pkg);
            JSONObject before=(lastStableState!=null && pkg.equals(lastStablePackage))?lastStableState:current;
            final JSONObject record=new JSONObject();
            record.put("session_id",session.id);
            record.put("goal",session.goal);
            record.put("t_ms",Math.max(0,now-session.startedAt));
            record.put("app",pkg);
            record.put("screen",safe(event.getClassName()));
            record.put("state",before);
            record.put("state_source","a11y");
            record.put("action",action);
            record.put("redaction_status",redactionStatus(before,action));

            handler.postDelayed(()->{
                try{
                    JSONObject after=captureState(pkg);
                    record.put("result_state",after);
                    record.put("outcome",stateChanged(before,after)?"success":"unknown");
                    PathSessionStore.append(this,record);
                    lastStableState=after;
                    lastStablePackage=pkg;
                }catch(Exception ignored){}
            },220);
        }catch(Exception ignored){}
    }

    @Override public void onInterrupt(){}

    private boolean supported(int type){
        return type==AccessibilityEvent.TYPE_VIEW_CLICKED ||
                type==AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                type==AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                type==AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
    }

    private JSONObject captureState(String pkg)throws Exception{
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null){
            JSONObject o=new JSONObject();o.put("missing",true);return o;
        }
        try{return A11yTreeSerializer.serialize(root,!PathPrivacyGuard.structuralOnly(pkg));}
        finally{root.recycle();}
    }

    private JSONObject actionFrom(AccessibilityEvent e)throws Exception{
        JSONObject a=new JSONObject();
        int type=e.getEventType();
        String action;
        if(type==AccessibilityEvent.TYPE_VIEW_CLICKED)action="tap";
        else if(type==AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)action="long_press";
        else if(type==AccessibilityEvent.TYPE_VIEW_SCROLLED)action="swipe";
        else action="type";
        a.put("type",action);

        AccessibilityNodeInfo src=e.getSource();
        String target=null;
        JSONArray xy=new JSONArray();
        String text=JSONObject.NULL.toString();
        try{
            if(src!=null){
                target=findNodePath(src);
                Rect r=new Rect();src.getBoundsInScreen(r);
                xy.put((r.left+r.right)/2);xy.put((r.top+r.bottom)/2);
                if("type".equals(action)){
                    text="[REDACTED]";
                }
            }
        }finally{if(src!=null)src.recycle();}
        a.put("target_node",target==null?JSONObject.NULL:target);
        a.put("xy",xy.length()==2?xy:JSONObject.NULL);
        a.put("text","type".equals(action)?"[REDACTED]":JSONObject.NULL);
        return a;
    }

    private String findNodePath(AccessibilityNodeInfo target){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return target.getViewIdResourceName();
        try{
            String found=findPath(root,target,"0",0);
            if(found!=null)return found;
            return target.getViewIdResourceName();
        }finally{root.recycle();}
    }

    private String findPath(AccessibilityNodeInfo node,AccessibilityNodeInfo target,String path,int depth){
        if(node==null||depth>8)return null;
        if(node.equals(target))return path;
        int count=Math.min(node.getChildCount(),40);
        for(int i=0;i<count;i++){
            AccessibilityNodeInfo child=node.getChild(i);
            if(child==null)continue;
            try{
                String found=findPath(child,target,path+"/"+i,depth+1);
                if(found!=null)return found;
            }finally{child.recycle();}
        }
        return null;
    }

    private String redactionStatus(JSONObject state,JSONObject action){
        String s=state.toString()+action.toString();
        return s.contains("[REDACTED]")?"partial":"none";
    }

    private boolean stateChanged(JSONObject before,JSONObject after){
        return !before.toString().equals(after.toString());
    }

    private String safe(CharSequence cs){
        if(cs==null)return "";
        String s=cs.toString();
        return s.length()>160?s.substring(0,160):s;
    }
}

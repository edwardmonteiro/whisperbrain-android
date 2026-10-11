package com.edward.datahub;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;

public final class A11yTreeSerializer {
    private static final int MAX_DEPTH=8;
    private static final int MAX_CHILDREN=40;

    private A11yTreeSerializer(){}

    public static JSONObject serialize(AccessibilityNodeInfo root)throws JSONException{
        return serializeNode(root,0,"0");
    }

    private static JSONObject serializeNode(AccessibilityNodeInfo node,int depth,String path)throws JSONException{
        JSONObject o=new JSONObject();
        if(node==null){o.put("missing",true);return o;}
        if(depth>MAX_DEPTH){o.put("truncated",true);return o;}

        String viewId=node.getViewIdResourceName();
        boolean sensitive=PathPrivacyGuard.sensitiveField(viewId,node.isPassword(),node.isEditable());

        o.put("path",path);
        o.put("class",safe(node.getClassName()));
        o.put("view_id",viewId==null?JSONObject.NULL:viewId);
        o.put("text",PathPrivacyGuard.sanitizeText(node.getText(),sensitive));
        o.put("content_desc",PathPrivacyGuard.sanitizeText(node.getContentDescription(),sensitive));
        o.put("editable",node.isEditable());
        o.put("password",node.isPassword());
        o.put("clickable",node.isClickable());
        o.put("focusable",node.isFocusable());
        o.put("scrollable",node.isScrollable());
        o.put("checked",node.isCheckable()?node.isChecked():JSONObject.NULL);
        o.put("selected",node.isSelected());

        Rect r=new Rect();
        node.getBoundsInScreen(r);
        JSONArray bounds=new JSONArray();
        bounds.put(r.left);bounds.put(r.top);bounds.put(r.right);bounds.put(r.bottom);
        o.put("bounds",bounds);

        JSONArray children=new JSONArray();
        int count=Math.min(node.getChildCount(),MAX_CHILDREN);
        for(int i=0;i<count;i++){
            AccessibilityNodeInfo child=node.getChild(i);
            if(child==null)continue;
            try{children.put(serializeNode(child,depth+1,path+"/"+i));}
            finally{child.recycle();}
        }
        if(node.getChildCount()>MAX_CHILDREN)o.put("children_truncated",true);
        o.put("children",children);
        return o;
    }

    private static String safe(CharSequence cs){
        if(cs==null)return "";
        String s=cs.toString();
        return s.length()>160?s.substring(0,160):s;
    }
}

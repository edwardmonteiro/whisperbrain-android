package com.edward.whisperbrain;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;

final class NotebookUi {
    static final int BG=Color.rgb(10,17,25),CARD=Color.rgb(21,33,46),INK=Color.rgb(239,247,252),MUTED=Color.rgb(164,184,204),TEAL=Color.rgb(108,236,193),AMBER=Color.rgb(255,194,116);
    final Activity a;
    NotebookUi(Activity a){this.a=a;}
    int dp(float n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
    LinearLayout col(){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    GradientDrawable bg(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(18));return d;}
    LinearLayout root(){ScrollView scroll=new ScrollView(a);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);LinearLayout l=col();l.setPadding(dp(20),dp(20),dp(20),dp(40));scroll.addView(l);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});a.setContentView(scroll);return l;}
    TextView text(String text,int size,int color){TextView t=new TextView(a);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(6));t.setLineSpacing(dp(3),1);return t;}
    TextView title(String text,int size){TextView t=text(text,size,INK);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    Button button(LinearLayout p,String text,boolean primary,Runnable action){Button b=new Button(a);b.setText(text);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(primary?BG:INK);b.setBackground(bg(primary?TEAL:CARD));b.setMinHeight(dp(52));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));p.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    EditText input(LinearLayout p,String hint,int limit,boolean multiline){EditText e=new EditText(a);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(18);e.setHint(hint);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(bg(CARD));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));e.setSingleLine(!multiline);if(multiline){e.setMinLines(4);e.setMaxLines(10);e.setGravity(Gravity.TOP);}p.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    LinearLayout card(LinearLayout p){LinearLayout c=col();c.setPadding(dp(16),dp(12),dp(16),dp(12));c.setBackground(bg(CARD));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));p.addView(c,lp);return c;}
    void watch(EditText e,Runnable r){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int f){}public void onTextChanged(CharSequence s,int st,int b,int c){r.run();}public void afterTextChanged(Editable x){}});}
}

package com.grammarfixer.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

// One-tap fix from Gboard: tap Gboard's globe key, this "keyboard" opens, fixes the text box
// right away and switches back to Gboard by itself. It has no keys and only sees the text box.
public class QuickFixIme extends InputMethodService {
    static final int ACCENT = 0xFF6366F1, VIOLET = 0xFFA855F7, GOOD = 0xFF10B981, ERROR = 0xFFEF4444;
    static final int DEFAULT_HEIGHT_DP = 280;   // used until the app has measured the real Gboard height

    final Handler ui = new Handler(Looper.getMainLooper());
    final Runnable goBack = this::goBack;
    LinearLayout content, actions;
    TextView status, undoBtn, retryBtn;
    ImageView logo;
    Ring ring;
    ObjectAnimator pulseX, pulseY;
    String undoText;
    EditorInfo lastInfo;
    int runId;
    float d;
    int cText, cMuted;

    int dp(float v) { return (int) (v * d + 0.5f); }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }   // never take over the screen in landscape

    @Override
    public View onCreateInputView() {
        d = getResources().getDisplayMetrics().density;
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        cText = dark ? 0xFFF3F4F6 : 0xFF1F2937;
        cMuted = dark ? 0xFF9CA3AF : 0xFF6B7280;

        // Same height as Gboard so the app behind doesn't jump when we swap in.
        int h = MainActivity.prefs(this).getInt("kbHeight", dp(DEFAULT_HEIGHT_DP));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? 0xFF202124 : 0xFFECEFF3);
        root.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, h));
        root.setMinimumHeight(h);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout badge = new FrameLayout(this);
        ring = new Ring(this);
        badge.addView(ring, new FrameLayout.LayoutParams(dp(84), dp(84)));
        logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        badge.addView(logo, new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER));
        content.addView(badge, new LinearLayout.LayoutParams(dp(84), dp(84)));

        status = new TextView(this);
        status.setTextSize(16);
        status.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(2);
        status.setPadding(dp(24), 0, dp(24), 0);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(14);
        content.addView(status, sl);

        actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        undoBtn = chip("Undo");
        undoBtn.setOnClickListener(v -> undo());
        retryBtn = chip("Try again");
        retryBtn.setOnClickListener(v -> { if (lastInfo != null) start(lastInfo); });
        TextView back = chip("Back to keyboard");
        back.setOnClickListener(v -> goBack());
        actions.addView(undoBtn, chipParams(false));
        actions.addView(retryBtn, chipParams(true));
        actions.addView(back, chipParams(true));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        al.topMargin = dp(16);
        content.addView(actions, al);

        root.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        pulseX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 1f, 1.08f);
        pulseY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 1f, 1.08f);
        for (ObjectAnimator a : new ObjectAnimator[]{pulseX, pulseY}) {
            a.setDuration(650);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setRepeatCount(ValueAnimator.INFINITE);
        }
        return root;
    }

    TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(ACCENT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x1F6366F1);
        g.setCornerRadius(dp(20));
        t.setBackground(g);
        return t;
    }

    LinearLayout.LayoutParams chipParams(boolean gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        if (gap) p.setMarginStart(dp(10));
        return p;
    }

    enum State { WORKING, DONE, INFO, FAILED }

    void show(String text, State st) {
        status.setText(text);
        status.setTextColor(st == State.DONE ? GOOD : st == State.FAILED ? ERROR : st == State.WORKING ? cText : cMuted);
        ring.set(st);
        boolean pulsing = st == State.WORKING;
        if (pulsing && !pulseX.isStarted()) { pulseX.start(); pulseY.start(); }
        if (!pulsing) {
            pulseX.cancel(); pulseY.cancel();
            logo.setScaleX(1f); logo.setScaleY(1f);
        }
        if (st == State.DONE) {   // small pop on success
            logo.setScaleX(0.85f); logo.setScaleY(0.85f);
            logo.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
        }
        undoBtn.setVisibility(st == State.DONE ? View.VISIBLE : View.GONE);
        retryBtn.setVisibility(st == State.FAILED ? View.VISIBLE : View.GONE);
        actions.setVisibility(st == State.WORKING || st == State.INFO ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (content == null || restarting) return;
        content.setAlpha(0f);
        content.setTranslationY(dp(14));
        content.animate().alpha(1f).translationY(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
        start(info);
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        runId++;   // left the text box: drop any result still on its way so it can't land in another box
        ui.removeCallbacks(goBack);
        if (pulseX != null) { pulseX.cancel(); pulseY.cancel(); }
        if (ring != null) ring.set(State.INFO);
    }

    void start(EditorInfo info) {
        final int id = ++runId;
        lastInfo = info;
        ui.removeCallbacks(goBack);
        undoText = null;
        if (FixKeyboardUtil.isPassword(info)) { show("Password box, nothing to fix", State.INFO); backIn(900); return; }
        final String key = MainActivity.prefs(this).getString("key", "");
        if (key.isEmpty()) { show("Open the Grammar Fixer app and add your Groq key", State.FAILED); return; }
        InputConnection ic = getCurrentInputConnection();
        ExtractedText et = ic == null ? null : ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et == null || et.text == null) { show("Can't read this box", State.FAILED); backIn(1500); return; }

        final String full = et.text.toString();
        int s = Math.min(et.selectionStart, et.selectionEnd), e = Math.max(et.selectionStart, et.selectionEnd);
        final boolean part = s >= 0 && e > s && e <= full.length();   // only fix the selected part if there is one
        final int from = part ? s : 0, to = part ? e : full.length();
        if (full.substring(from, to).trim().isEmpty()) { show("Type something first", State.INFO); backIn(700); return; }

        final String tone = MainActivity.prefs(this).getString("tone", "Natural");
        final boolean tr = MainActivity.prefs(this).getBoolean("translate", true);
        show(part ? "Fixing the selected part" : "Fixing your text", State.WORKING);
        new Thread(() -> {
            String out, err = null;
            try { out = Groq.fix(key, tone, tr, full.substring(from, to)); } catch (Exception ex) { out = null; err = ex.getMessage(); }
            final String fixed = out, error = err;
            ui.post(() -> {
                if (id != runId) return;
                if (fixed == null) { show(error, State.FAILED); return; }
                InputConnection now = getCurrentInputConnection();
                if (now == null) return;
                String result = full.substring(0, from) + fixed + full.substring(to);
                if (result.equals(full)) { show("Looks good already", State.DONE); undoBtn.setVisibility(View.GONE); backIn(600); return; }
                replaceAll(now, result);
                undoText = full;
                show("Fixed", State.DONE);
                backIn(750);
            });
        }).start();
    }

    void undo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || undoText == null) return;
        replaceAll(ic, undoText);
        undoText = null;
        show("Undone", State.INFO);
        backIn(500);
    }

    void backIn(long ms) { ui.removeCallbacks(goBack); ui.postDelayed(goBack, ms); }

    void goBack() {
        ui.removeCallbacks(goBack);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean ok;
        if (Build.VERSION.SDK_INT >= 28) {
            ok = switchToPreviousInputMethod();
        } else {
            IBinder token = getWindow().getWindow().getAttributes().token;
            ok = imm.switchToLastInputMethod(token);
        }
        if (!ok) imm.showInputMethodPicker();
    }

    static void replaceAll(InputConnection ic, String text) {
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        int len = et != null && et.text != null ? et.text.length() : 0;
        ic.beginBatchEdit();
        ic.setSelection(0, len);
        ic.commitText(text, 1);
        ic.endBatchEdit();
    }

    // Spinning gradient ring while working; fills green on success, red on error.
    static class Ring extends View {
        final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box = new RectF();
        final ValueAnimator spin = ValueAnimator.ofFloat(0f, 360f);
        ValueAnimator fill;
        float angle, sweep = 100f;
        State state = State.INFO;

        Ring(Context c) {
            super(c);
            float w = 4.5f * c.getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(w);
            track.setColor(0x226366F1);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(w);
            arc.setStrokeCap(Paint.Cap.ROUND);
            spin.setDuration(900);
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.setInterpolator(new LinearInterpolator());
            spin.addUpdateListener(a -> { angle = (float) a.getAnimatedValue(); invalidate(); });
        }

        void set(State s) {
            if (s == state && s != State.DONE) return;
            state = s;
            if (fill != null) fill.cancel();
            if (s == State.WORKING) {
                arc.setShader(null);
                sweep = 100f;
                if (!spin.isStarted()) spin.start();
            } else {
                spin.cancel();
                if (s == State.DONE || s == State.FAILED) {
                    fill = ValueAnimator.ofFloat(sweep, 360f);
                    fill.setDuration(260);
                    fill.addUpdateListener(a -> { sweep = (float) a.getAnimatedValue(); invalidate(); });
                    fill.start();
                } else {
                    sweep = 0f;
                }
            }
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float p = track.getStrokeWidth();
            box.set(p, p, w - p, h - p);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawOval(box, track);
            if (sweep <= 0f) return;
            if (state == State.WORKING) {
                arc.setColor(ACCENT);
                arc.setShader(new SweepGradient(box.centerX(), box.centerY(), new int[]{0x006366F1, ACCENT, VIOLET}, new float[]{0f, 0.2f, 0.28f}));
                c.save();
                c.rotate(angle, box.centerX(), box.centerY());
                c.drawArc(box, 0, sweep, false, arc);
                c.restore();
            } else {
                arc.setShader(null);
                arc.setColor(state == State.DONE ? GOOD : ERROR);
                c.drawArc(box, -90 + angle, sweep, false, arc);
            }
        }
    }
}

final class FixKeyboardUtil {
    static boolean isPassword(EditorInfo info) {
        int cls = info.inputType & InputType.TYPE_MASK_CLASS, v = info.inputType & InputType.TYPE_MASK_VARIATION;
        return (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            || (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }
}

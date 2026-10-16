package com.grammarfixer.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final int GOOD = 0xFF059669, BAD = 0xFFDC2626, MUTED = 0xFF6B7280;

    SharedPreferences prefs;
    EditText key, input;
    TextView status, toneHint, result;
    Button save, fix;
    View resultActions;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", MODE_PRIVATE); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = prefs(this);
        key = findViewById(R.id.key);
        input = findViewById(R.id.input);
        status = findViewById(R.id.status);
        toneHint = findViewById(R.id.toneHint);
        result = findViewById(R.id.result);
        save = findViewById(R.id.save);
        fix = findViewById(R.id.fix);
        resultActions = findViewById(R.id.resultActions);

        key.setText(prefs.getString("key", ""));
        if (key.length() == 0) setStatus("Paste your Groq key, then tap the button.", MUTED);
        else setStatus("✓  Key saved. Ready to use.", GOOD);

        Spinner tone = findViewById(R.id.tone);
        tone.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Groq.TONES));
        tone.setSelection(Math.max(0, java.util.Arrays.asList(Groq.TONES).indexOf(prefs.getString("tone", "Natural"))));
        tone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                toneHint.setText(Groq.TONE_HINTS[pos]);
                prefs.edit().putString("tone", Groq.TONES[pos]).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        Switch translate = findViewById(R.id.translate);
        translate.setChecked(prefs.getBoolean("translate", true));
        translate.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("translate", on).apply());

        findViewById(R.id.getKey).setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))));
        save.setOnClickListener(v -> saveKey());
        fix.setOnClickListener(v -> runFix());
        findViewById(R.id.copy).setOnClickListener(v -> copy(this, result.getText()));
        findViewById(R.id.share).setOnClickListener(v -> share(this, result.getText()));
        findViewById(R.id.quickEnable).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        // Remember the normal keyboard's height so the 1-tap Fix panel can match it (no jump when it swaps in).
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                int nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                if (ime - nav > getResources().getDisplayMetrics().density * 150)
                    prefs.edit().putInt("kbHeight", ime - nav).apply();
                return v.onApplyWindowInsets(insets);
            });
        }

        final String crash = prefs.getString("crash", null);
        if (crash != null) {
            findViewById(R.id.crashCard).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.crashText)).setText(crash);
            findViewById(R.id.crashCopy).setOnClickListener(v -> copy(this, crash));
            findViewById(R.id.crashDismiss).setOnClickListener(v -> {
                prefs.edit().remove("crash").apply();
                findViewById(R.id.crashCard).setVisibility(View.GONE);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings.Secure.ENABLED_INPUT_METHODS throws for targetSdk 34+, so ask InputMethodManager instead.
        boolean on = false;
        for (InputMethodInfo im : ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).getEnabledInputMethodList())
            if (im.getPackageName().equals(getPackageName())) on = true;
        TextView st = findViewById(R.id.quickStatus);
        st.setText(on ? "\u2713  1-tap Fix is ON" : "1-tap Fix is OFF");
        st.setTextColor(on ? GOOD : BAD);
        findViewById(R.id.quickEnable).setVisibility(on ? View.GONE : View.VISIBLE);
    }

    void setStatus(String t, int color) { status.setText(t); status.setTextColor(color); }

    void saveKey() {
        final String k = key.getText().toString().trim();
        if (k.isEmpty()) { setStatus("Paste your Groq API key first.", BAD); return; }
        setStatus("Checking your key...", MUTED);
        save.setEnabled(false);
        new Thread(() -> {
            String err = null;
            try { Groq.testKey(k); } catch (Exception e) { err = e.getMessage(); }
            final String error = err;
            runOnUiThread(() -> {
                save.setEnabled(true);
                if (error != null) { setStatus("✗  " + error, BAD); return; }
                prefs.edit().putString("key", k).apply();
                setStatus("✓  Key works. You're all set.", GOOD);
            });
        }).start();
    }

    void runFix() {
        final String text = input.getText().toString();
        final String k = prefs.getString("key", "");
        if (text.trim().isEmpty()) { Toast.makeText(this, "Type some text first", Toast.LENGTH_SHORT).show(); return; }
        if (k.isEmpty()) { setStatus("Save your Groq key first.", BAD); key.requestFocus(); return; }
        fix.setEnabled(false);
        fix.setText("Fixing...");
        final String tone = prefs.getString("tone", "Natural");
        final boolean tr = prefs.getBoolean("translate", true);
        new Thread(() -> {
            String out, err = null;
            try { out = Groq.fix(k, tone, tr, text); } catch (Exception e) { out = null; err = e.getMessage(); }
            final String fixed = out, error = err;
            runOnUiThread(() -> {
                fix.setEnabled(true);
                fix.setText("Fix my text");
                result.setVisibility(View.VISIBLE);
                result.setText(fixed != null ? fixed : error);
                result.setTextColor(fixed != null ? 0xFF1F2937 : BAD);
                resultActions.setVisibility(fixed != null ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    static void copy(Context c, CharSequence t) {
        ((ClipboardManager) c.getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Fixed text", t));
        Toast.makeText(c, "Copied", Toast.LENGTH_SHORT).show();
    }

    static void share(Context c, CharSequence t) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.toString());
        c.startActivity(Intent.createChooser(i, "Share fixed text"));
    }
}

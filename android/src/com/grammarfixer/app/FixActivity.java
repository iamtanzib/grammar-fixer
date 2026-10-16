package com.grammarfixer.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

// Opened from "Fix grammar" in any app's text menu, or from Share.
// In an editable box the selected text gets replaced. Otherwise the result is shown with Copy and Share.
public class FixActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_fix);
        Intent in = getIntent();
        CharSequence src;
        final boolean canReplace;
        if (Intent.ACTION_PROCESS_TEXT.equals(in.getAction())) {
            src = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            canReplace = !in.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false) && getCallingActivity() != null;
        } else {
            src = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            canReplace = false;
        }
        if (src == null || src.toString().trim().isEmpty()) { finish(); return; }

        final String key = MainActivity.prefs(this).getString("key", "");
        if (key.isEmpty()) {
            Toast.makeText(this, "Open Grammar Fixer and add your Groq key first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        final String text = src.toString();
        final String tone = MainActivity.prefs(this).getString("tone", "Natural");
        final boolean tr = MainActivity.prefs(this).getBoolean("translate", true);
        new Thread(() -> {
            String out, err = null;
            try { out = Groq.fix(key, tone, tr, text); } catch (Exception e) { out = null; err = e.getMessage(); }
            final String fixed = out, error = err;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (fixed != null && canReplace) {
                    setResult(RESULT_OK, new Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, fixed));
                    Toast.makeText(this, "Fixed", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                show(fixed, error);
            });
        }).start();
    }

    void show(final String fixed, String error) {
        findViewById(R.id.busy).setVisibility(View.GONE);
        findViewById(R.id.resultBox).setVisibility(View.VISIBLE);
        findViewById(R.id.actions).setVisibility(View.VISIBLE);
        TextView result = findViewById(R.id.result);
        result.setText(fixed != null ? fixed : error);
        result.setTextColor(fixed != null ? 0xFF1F2937 : MainActivity.BAD);
        findViewById(R.id.copy).setVisibility(fixed != null ? View.VISIBLE : View.GONE);
        findViewById(R.id.share).setVisibility(fixed != null ? View.VISIBLE : View.GONE);
        findViewById(R.id.copy).setOnClickListener(v -> { MainActivity.copy(this, fixed); finish(); });
        findViewById(R.id.share).setOnClickListener(v -> { MainActivity.share(this, fixed); finish(); });
        findViewById(R.id.close).setOnClickListener(v -> finish());
    }
}

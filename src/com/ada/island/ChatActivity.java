package com.ada.island;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Full-screen conversation with Ada (the island points here when you need a keyboard). */
public class ChatActivity extends Activity {

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private Prefs prefs;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private final List<AiClient.Msg> history = new ArrayList<>();
    private int bubbleCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(build());
        greet();
    }

    private View build() {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(0xFF08080C);
        root.setFitsSystemWindows(true);

        // header
        LinearLayout head = Ui.row(this);
        head.setBackgroundColor(0xFF0A0A10);
        head.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 12));

        TextView back = Ui.tv(this, "‹", 26f, Ui.TEXT, false);
        back.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(back);

        PulseView dot = new PulseView(this);
        head.addView(dot, Ui.lp(Ui.dp(this, 18), Ui.dp(this, 18)));

        LinearLayout titles = Ui.col(this);
        TextView t = Ui.tv(this, "ada", 15f, Ui.TEXT, true);
        TextView s = Ui.tv(this, prefs.hasKey() ? "bağlı · yanında" : "anahtar yok · ayarlardan ekle", 10.5f, Ui.MUTED, false);
        titles.addView(t);
        titles.addView(s);
        LinearLayout.LayoutParams tlLp = Ui.lpw(0, WRAP, 1f);
        tlLp.leftMargin = Ui.dp(this, 9);
        head.addView(titles, tlLp);

        TextView set = Ui.tv(this, "ayar", 11.5f, 0xFFB9B9C8, true);
        set.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        set.setBackground(Ui.round(0x18FFFFFF, Ui.dpf(this, 12), Math.max(1, Ui.dp(this, 1)), 0x1FFFFFFF));
        set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(set);
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        // messages
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        messages = Ui.col(this);
        messages.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        scroll.addView(messages, new ScrollView.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // input
        LinearLayout inputRow = Ui.row(this);
        inputRow.setBackgroundColor(0xFF0A0A10);
        inputRow.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 16));

        input = new EditText(this);
        input.setHint("ada'ya bir şey sor…");
        input.setHintTextColor(0xFF5E5E6E);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        input.setBackground(Ui.round(0xFF16161F, Ui.dpf(this, 16), Math.max(1, Ui.dp(this, 1)), 0x22FFFFFF));
        input.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11));
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    send();
                    return true;
                }
                return false;
            }
        });
        inputRow.addView(input, Ui.lpw(0, WRAP, 1f));

        final TextView sendBtn = Ui.tv(this, "↑", 18f, 0xFF0A0A11, true);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setBackground(Ui.gradient(new int[]{0xFF9B7BFF, 0xFF5EE0FF}, Ui.dpf(this, 18)));
        LinearLayout.LayoutParams sbLp = Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40));
        sbLp.leftMargin = Ui.dp(this, 8);
        inputRow.addView(sendBtn, sbLp);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });

        root.addView(inputRow, new LinearLayout.LayoutParams(MATCH, WRAP));
        return root;
    }

    private void greet() {
        bubble("Merhaba, ben ada. Ekranın üstünde yaşıyorum: müziği görüyorum, hangi uygulamada olduğunu biliyorum.", false);
        if (!prefs.hasKey()) {
            bubble("Şu an anahtarım yok; ana ekrandaki AI bölümünden bir API anahtarı ekle, sonra burada konuşalım.", false);
        }
        String[] chips = {"Bugün ne yapmalıyım?", "Şu an ne yapıyorum?", "Müzik öner"};
        LinearLayout row = Ui.col(this);
        for (int i = 0; i < chips.length; i++) {
            final String q = chips[i];
            TextView chip = Ui.tv(this, q, 12f, 0xFFE4E4EE, false);
            chip.setBackground(Ui.round(0x14FFFFFF, Ui.dpf(this, 14), Math.max(1, Ui.dp(this, 1)), 0x1FFFFFFF));
            chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
            p.bottomMargin = Ui.dp(this, 6);
            p.topMargin = Ui.dp(this, 6);
            row.addView(chip, p);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(q);
                    send();
                }
            });
        }
        messages.addView(row);
    }

    private void send() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        bubble(text, true);
        history.add(new AiClient.Msg("user", text));
        if (history.size() > 12) history.remove(0);

        final TextView typing = bubble("…", false);

        List<AiClient.Msg> payload = new ArrayList<>();
        ContextReader.Ctx ctx = ContextReader.read(this);
        payload.add(new AiClient.Msg("system", AiClient.SYSTEM_PROMPT
                + "\n\nŞu anki bağlam:\n" + ctx.block()));
        payload.addAll(history);

        AiClient.chat(this, payload, new AiClient.Cb() {
            @Override
            public void onOk(String reply) {
                typing.setText(reply);
                history.add(new AiClient.Msg("assistant", reply));
            }

            @Override
            public void onErr(String message) {
                typing.setTextColor(0xFFFF9A9A);
                typing.setText(message);
            }
        });
    }

    private TextView bubble(String text, boolean mine) {
        TextView t = Ui.tv(this, text, 14f, mine ? 0xFF0A0A11 : 0xFFEDEDF6, false);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11));
        if (mine) {
            t.setBackground(Ui.gradient(new int[]{0xFFCDBBFF, 0xFF8FE6FF}, Ui.dpf(this, 18)));
        } else {
            t.setBackground(Ui.round(0xFF16161F, Ui.dpf(this, 18), Math.max(1, Ui.dp(this, 1)), 0x1AFFFFFF));
        }
        LinearLayout wrap = Ui.col(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = Ui.dp(this, 8);
        wrap.setLayoutParams(p);
        wrap.setGravity(mine ? Gravity.END : Gravity.START);

        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(WRAP, WRAP);
        t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        t.setLayoutParams(tp);
        wrap.addView(t);
        messages.addView(wrap, p);
        bubbleCount++;

        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
        return t;
    }
}

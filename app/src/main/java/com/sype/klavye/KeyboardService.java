package com.sype.klavye;

import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.util.Locale;

public class KeyboardService extends InputMethodService {
  static final String[] THEME_NAMES = {"Açık", "Koyu", "AMOLED Siyah", "Mavi", "Pembe", "Yeşil"};
  // bg, key, special key, text, accent
  static final int[][] THEMES = {
    {0xFFE8EAED, 0xFFFFFFFF, 0xFFC9CED6, 0xFF202124, 0xFF1A73E8},
    {0xFF202124, 0xFF3C4043, 0xFF2D2F31, 0xFFE8EAED, 0xFF8AB4F8},
    {0xFF000000, 0xFF1C1C1C, 0xFF121212, 0xFFFFFFFF, 0xFF8AB4F8},
    {0xFF0D47A1, 0xFF1565C0, 0xFF0B3C8A, 0xFFFFFFFF, 0xFFFFCA28},
    {0xFFFCE4EC, 0xFFFFFFFF, 0xFFF8BBD0, 0xFF880E4F, 0xFFD81B60},
    {0xFF1B5E20, 0xFF2E7D32, 0xFF144A18, 0xFFFFFFFF, 0xFFFFEB3B}};
  static final String[] LETTERS = {"q w e r t y u ı o p ğ ü", "a s d f g h j k l ş i", "⇧ z x c v b n m ö ç ⌫"};
  static final String[][] SYMS = {
    {"1 2 3 4 5 6 7 8 9 0", "@ # ₺ _ & - + ( ) /", "1/2 * \" ' : ; ! ? ⌫"},
    {"~ ` | • √ π ÷ × { }", "€ $ £ ^ ° = [ ] % \\", "1/2 < > © ® ✓ … ⌫"}};
  static final String EMOJI = "😀 😂 🤣 😊 😍 😘 😎 🤔 😢 😭 😡 😱 👍 👎 👏 🙏 💪 👌 ✌️ 🤝 ❤️ 💔 🔥 ✨ 🎉 💯 ✅ ❌ 🌹 ☀️ 🌙 ⭐ 🍕 ☕ 🎂 ⚽ 🚗 ✈️ 🏠 📱 💰 🎁 😴 🤗 🙄 😅 😇 🥳";
  static final Locale TR = new Locale("tr", "TR");

  SharedPreferences p;
  int[] t;
  boolean shift, caps, sym, emoji, vib, sound, numRow, autoCap;
  int page, keyH;
  String lang;
  TextView status;
  final Handler h = new Handler(Looper.getMainLooper());
  Runnable repeat;
  PopupWindow pop;
  TextView popTv;

  void showPop(View v, String label) {
    if (pop == null) {
      popTv = new TextView(this);
      popTv.setGravity(Gravity.CENTER);
      popTv.setTextSize(30);
      pop = new PopupWindow(popTv);
      pop.setClippingEnabled(false);
      pop.setTouchable(false);
    }
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(dp(10));
    d.setColor(t[1]);
    d.setStroke(dp(1), t[2]);
    popTv.setBackground(d);
    popTv.setTextColor(t[3]);
    popTv.setText(label);
    int w = (int) (v.getWidth() * 1.4f), hh = dp(keyH + 22);
    int[] loc = new int[2];
    v.getLocationInWindow(loc);
    int x = loc[0] - (w - v.getWidth()) / 2, y = loc[1] - hh + dp(6);
    if (pop.isShowing()) pop.update(x, y, w, hh);
    else { pop.setWidth(w); pop.setHeight(hh); pop.showAtLocation(v, Gravity.NO_GRAVITY, x, y); }
  }

  void hidePop() { if (pop != null && pop.isShowing()) pop.dismiss(); }

  int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

  void load() {
    p = getSharedPreferences("k", 0);
    t = THEMES[Math.min(p.getInt("theme", 0), THEMES.length - 1)];
    vib = p.getBoolean("vib", true); sound = p.getBoolean("sound", false);
    numRow = p.getBoolean("num", false); autoCap = p.getBoolean("autocap", true);
    keyH = p.getInt("height", 48); lang = p.getString("lang", "en");
  }

  @Override public View onCreateInputView() { load(); return build(); }

  @Override public void onStartInputView(EditorInfo ei, boolean restarting) {
    load(); sym = emoji = shift = caps = false; page = 0;
    setInputView(build()); updateShift();
  }

  void redraw() { hidePop(); setInputView(build()); }

  View build() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(t[0]);
    root.setPadding(dp(3), 0, dp(3), dp(6));
    root.setOnApplyWindowInsetsListener((v, in) -> {
      v.setPadding(dp(3), 0, dp(3), dp(6) + in.getSystemWindowInsetBottom());
      return in;
    });
    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.addView(tool("⚙", v -> startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
    bar.addView(tool(emoji ? "⌨" : "😊", v -> { emoji = !emoji; sym = false; redraw(); }));
    bar.addView(tool("📋", v -> paste()));
    bar.addView(tool("文A → " + lang.toUpperCase(), v -> translate()));
    status = new TextView(this);
    status.setTextColor(t[4]);
    status.setGravity(Gravity.END);
    bar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(bar, new LinearLayout.LayoutParams(-1, dp(42)));
    if (emoji) {
      String[] e = EMOJI.split(" ");
      for (int i = 0; i < e.length; i += 8) {
        StringBuilder sb = new StringBuilder();
        for (int j = i; j < Math.min(i + 8, e.length); j++) sb.append(j > i ? " " : "").append(e[j]);
        addRow(root, sb.toString(), false);
      }
      addRow(root, "ABC SPACE ⌫", false);
      return root;
    }
    String[] rows = sym ? SYMS[page] : LETTERS;
    if (numRow && !sym) addRow(root, "1 2 3 4 5 6 7 8 9 0", false);
    for (int i = 0; i < 3; i++) addRow(root, rows[i], i == 0 && !sym);
    addRow(root, (sym ? "ABC" : "?123") + " , 🌐 SPACE . ⏎", false);
    return root;
  }

  TextView tool(String s, View.OnClickListener l) {
    TextView b = new TextView(this);
    b.setText(s); b.setTextSize(17); b.setTextColor(t[3]);
    b.setPadding(dp(12), 0, dp(12), 0); b.setGravity(Gravity.CENTER);
    b.setLayoutParams(new LinearLayout.LayoutParams(-2, -1));
    b.setOnClickListener(l);
    return b;
  }

  boolean special(String k) { return k.length() > 1 && !k.equals("SPACE") || "⇧⌫⏎".contains(k); }

  void addRow(LinearLayout root, String row, boolean digitsOnLong) {
    LinearLayout r = new LinearLayout(this);
    String[] ks = row.split(" ");
    for (int i = 0; i < ks.length; i++) {
      String k = ks[i];
      boolean isEmoji = emoji && !k.equals("ABC") && !k.equals("SPACE") && !k.equals("⌫");
      boolean sp = !isEmoji && special(k) && !k.equals("🌐");
      TextView b = new TextView(this);
      String label = k.equals("SPACE") ? "Türkçe" : (shift && !sym && !emoji && k.length() == 1 ? k.toUpperCase(TR) : k);
      if (k.equals("⇧") && caps) label = "⇪";
      b.setText(label);
      b.setGravity(Gravity.CENTER);
      b.setTextSize(k.equals("SPACE") ? 14 : (isEmoji ? 24 : 21));
      b.setTextColor(k.equals("⏎") || (k.equals("⇧") && shift) ? 0xFFFFFFFF : t[3]);
      GradientDrawable d = new GradientDrawable();
      d.setCornerRadius(dp(7));
      d.setColor(k.equals("⏎") || (k.equals("⇧") && shift) ? t[4] : (sp ? t[2] : t[1]));
      b.setBackground(d);
      float w = k.equals("SPACE") ? 4f : (sp && !k.equals("1/2") ? 1.5f : 1f);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(keyH), w);
      lp.setMargins(dp(3), dp(4), dp(3), dp(4));
      if (k.equals("⌫")) {
        b.setOnTouchListener((v, ev) -> {
          if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            press(v, k);
            repeat = new Runnable() { public void run() { press(v, "⌫"); h.postDelayed(this, 50); } };
            h.postDelayed(repeat, 400);
          } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            h.removeCallbacks(repeat);
          }
          return true;
        });
      } else {
        b.setOnClickListener(v -> press(v, k));
        if (!sp && !k.equals("SPACE")) {
          final String pl = label;
          b.setOnTouchListener((v, ev) -> {
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) showPop(v, pl);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) hidePop();
            return false;
          });
        }
      }
      if (digitsOnLong && i < 10) {
        final String dg = String.valueOf((i + 1) % 10);
        b.setOnLongClickListener(v -> { hidePop(); commit(dg); return true; });
      }
      r.addView(b, lp);
    }
    root.addView(r);
  }

  void feedback(View v) {
    if (vib) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    if (sound) ((AudioManager) getSystemService(AUDIO_SERVICE)).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
  }

  void commit(String s) {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) ic.commitText(s, 1);
  }

  void updateShift() {
    InputConnection ic = getCurrentInputConnection();
    EditorInfo ei = getCurrentInputEditorInfo();
    if (!autoCap || caps || sym || emoji || ic == null || ei == null) return;
    boolean s = ic.getCursorCapsMode(ei.inputType) != 0;
    if (s != shift) { shift = s; redraw(); }
  }

  void press(View v, String k) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    feedback(v);
    switch (k) {
      case "⇧":
        if (caps) caps = shift = false; else if (shift) caps = true; else shift = true;
        redraw(); return;
      case "1/2": page ^= 1; redraw(); return;
      case "?123": sym = true; page = 0; redraw(); return;
      case "ABC": sym = emoji = false; redraw(); updateShift(); return;
      case "⌫":
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); updateShift(); return;
      case "🌐":
        if (!switchToNextInputMethod(false))
          ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
        return;
      case "SPACE": ic.commitText(" ", 1); updateShift(); return;
      case "⏎": {
        EditorInfo ei = getCurrentInputEditorInfo();
        int a = ei == null ? 0 : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (ei != null && a > EditorInfo.IME_ACTION_NONE && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
          ic.performEditorAction(a);
        else ic.commitText("\n", 1);
        updateShift(); return;
      }
      default:
        ic.commitText(shift && !sym && !emoji ? k.toUpperCase(TR) : k, 1);
        if (shift && !caps) { shift = false; redraw(); }
        if (".!?".contains(k)) updateShift();
    }
  }

  void paste() {
    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    ClipData cd = cm.getPrimaryClip();
    if (cd != null && cd.getItemCount() > 0) commit(cd.getItemAt(0).coerceToText(this).toString());
    else status.setText("Pano boş");
  }

  void translate() {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    CharSequence sel = ic.getSelectedText(0);
    final boolean hasSel = sel != null && sel.length() > 0;
    ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
    String all;
    int st = 0;
    if (et != null && et.text != null) { all = et.text.toString(); st = et.startOffset; }
    else {
      CharSequence b = ic.getTextBeforeCursor(5000, 0), a = ic.getTextAfterCursor(5000, 0);
      all = (b == null ? "" : b.toString()) + (a == null ? "" : a.toString());
    }
    final String src = hasSel ? sel.toString() : all;
    final int start = st;
    if (src.trim().isEmpty()) { status.setText("Çevrilecek metin yok"); return; }
    status.setText("Çevriliyor…");
    final String tl = lang;
    new Thread(() -> {
      String r;
      try { r = tr(src, tl); } catch (Exception e) { r = null; android.util.Log.e("Klavye", "ceviri", e); }
      final String res = r;
      h.post(() -> {
        InputConnection c = getCurrentInputConnection();
        if (res == null || c == null) { status.setText("Çeviri hatası"); return; }
        if (!hasSel) c.setSelection(start, start + src.length());
        c.commitText(res, 1);
        status.setText("");
      });
    }).start();
  }

  static String tr(String q, String tl) throws Exception {
    URL u = new URL("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=" + tl + "&q=" + URLEncoder.encode(q, "UTF-8"));
    HttpURLConnection c = (HttpURLConnection) u.openConnection();
    c.setConnectTimeout(8000); c.setReadTimeout(8000);
    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
    StringBuilder sb = new StringBuilder(); String l;
    while ((l = br.readLine()) != null) sb.append(l);
    Object o = new JSONArray(sb.toString()).get(0);
    return o instanceof JSONArray ? ((JSONArray) o).getString(0) : o.toString();
  }
}

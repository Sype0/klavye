package com.sype.klavye;

import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.util.*;

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
  static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";

  SharedPreferences p;
  int[] t;
  boolean shift, caps, sym, emoji, vib, sound, numRow, autoCap, suggest;
  int page, keyH, navInset;
  String lang;
  LinearLayout root, bar;
  final Handler h = new Handler(Looper.getMainLooper());
  Runnable repeat;
  PopupWindow pop;
  TextView popTv;
  final ArrayList<String> dict = new ArrayList<>();
  final HashMap<String, Integer> learned = new HashMap<>();

  int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

  void msg(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

  @Override public void onCreate() {
    super.onCreate();
    new Thread(() -> {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("words.txt"), "UTF-8"))) {
        ArrayList<String> l = new ArrayList<>();
        String s;
        while ((s = br.readLine()) != null) l.add(s.trim());
        synchronized (dict) { dict.addAll(l); }
      } catch (Exception ignored) {}
      try (BufferedReader br = new BufferedReader(new InputStreamReader(openFileInput("user.txt"), "UTF-8"))) {
        String s;
        while ((s = br.readLine()) != null) {
          String[] a = s.split("\t");
          if (a.length == 2) synchronized (learned) { learned.put(a[0], Integer.parseInt(a[1])); }
        }
      } catch (Exception ignored) {}
    }).start();
  }

  void load() {
    p = getSharedPreferences("k", 0);
    t = THEMES[Math.min(p.getInt("theme", 0), THEMES.length - 1)];
    vib = p.getBoolean("vib", true); sound = p.getBoolean("sound", false);
    numRow = p.getBoolean("num", false); autoCap = p.getBoolean("autocap", true);
    suggest = p.getBoolean("suggest", true);
    keyH = p.getInt("height", 48); lang = p.getString("lang", "en");
  }

  @Override public View onCreateInputView() {
    load();
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setOnApplyWindowInsetsListener((v, in) -> {
      navInset = in.getSystemWindowInsetBottom();
      pad();
      return in;
    });
    build();
    return root;
  }

  void pad() { root.setPadding(dp(3), 0, dp(3), dp(6) + navInset); }

  @Override public void onStartInputView(EditorInfo ei, boolean restarting) {
    load(); sym = emoji = shift = caps = false; page = 0;
    build(); updateShift();
  }

  @Override public void onFinishInput() { super.onFinishInput(); saveLearned(); }

  @Override public void onUpdateSelection(int os, int oe, int ns, int ne, int cs, int ce) {
    super.onUpdateSelection(os, oe, ns, ne, cs, ce);
    updateBar();
  }

  void redraw() { build(); }

  void build() {
    if (root == null) return;
    hidePop();
    root.removeAllViews();
    root.setBackgroundColor(t[0]);
    pad();
    bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(bar, new LinearLayout.LayoutParams(-1, dp(42)));
    updateBar();
    if (emoji) {
      String[] e = EMOJI.split(" ");
      for (int i = 0; i < e.length; i += 8) {
        StringBuilder sb = new StringBuilder();
        for (int j = i; j < Math.min(i + 8, e.length); j++) sb.append(j > i ? " " : "").append(e[j]);
        addRow(sb.toString(), false);
      }
      addRow("ABC SPACE ⌫", false);
      return;
    }
    String[] rows = sym ? SYMS[page] : LETTERS;
    if (numRow && !sym) addRow("1 2 3 4 5 6 7 8 9 0", false);
    for (int i = 0; i < 3; i++) addRow(rows[i], i == 0 && !sym);
    addRow((sym ? "ABC" : "?123") + " , 🌐 SPACE . ⏎", false);
    root.requestApplyInsets();
  }

  void updateBar() {
    if (bar == null) return;
    bar.removeAllViews();
    final String w = curWord();
    List<String> sg = suggest && !emoji && !w.isEmpty() ? suggestions(w) : Collections.emptyList();
    if (sg.isEmpty()) {
      bar.addView(tool("⚙", v -> startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
      bar.addView(tool(emoji ? "⌨" : "😊", v -> { emoji = !emoji; sym = false; redraw(); }));
      bar.addView(tool("📋", v -> paste()));
      bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
      bar.addView(tool("文A → " + lang.toUpperCase(), v -> translate()));
    } else {
      for (String s : sg) {
        TextView b = tool(s, v -> pick(w, s));
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
      }
      bar.addView(tool("文A", v -> translate()));
    }
  }

  String curWord() {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return "";
    CharSequence c = ic.getTextBeforeCursor(40, 0);
    if (c == null) return "";
    int i = c.length();
    while (i > 0 && Character.isLetter(c.charAt(i - 1))) i--;
    return c.subSequence(i, c.length()).toString();
  }

  List<String> suggestions(String w) {
    String lw = w.toLowerCase(TR);
    LinkedHashSet<String> out = new LinkedHashSet<>();
    ArrayList<Map.Entry<String, Integer>> ls = new ArrayList<>();
    synchronized (learned) {
      for (Map.Entry<String, Integer> e : learned.entrySet())
        if (e.getKey().startsWith(lw) && !e.getKey().equals(lw)) ls.add(e);
    }
    ls.sort((a, b) -> b.getValue() - a.getValue());
    for (Map.Entry<String, Integer> e : ls) { if (out.size() >= 3) break; out.add(e.getKey()); }
    synchronized (dict) {
      for (String d : dict) { if (out.size() >= 3) break; if (d.startsWith(lw) && !d.equals(lw)) out.add(d); }
    }
    boolean up = Character.isUpperCase(w.charAt(0));
    ArrayList<String> r = new ArrayList<>();
    for (String s : out) r.add(up ? s.substring(0, 1).toUpperCase(TR) + s.substring(1) : s);
    return r;
  }

  void pick(String w, String s) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    ic.deleteSurroundingText(w.length(), 0);
    ic.commitText(s + " ", 1);
    learn(s);
    if (shift && !caps) { shift = false; redraw(); }
    updateShift();
  }

  void learn(String w) {
    if (w.length() < 2) return;
    synchronized (learned) { learned.merge(w.toLowerCase(TR), 1, Integer::sum); }
  }

  void saveLearned() {
    try (Writer wr = new OutputStreamWriter(openFileOutput("user.txt", 0), "UTF-8")) {
      synchronized (learned) {
        for (Map.Entry<String, Integer> e : learned.entrySet()) wr.write(e.getKey() + "\t" + e.getValue() + "\n");
      }
    } catch (Exception ignored) {}
  }

  TextView tool(String s, View.OnClickListener l) {
    TextView b = new TextView(this);
    b.setText(s); b.setTextSize(17); b.setTextColor(t[3]);
    b.setPadding(dp(12), 0, dp(12), 0); b.setGravity(Gravity.CENTER);
    b.setLayoutParams(new LinearLayout.LayoutParams(-2, -1));
    b.setOnClickListener(l);
    return b;
  }

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

  boolean special(String k) { return k.length() > 1 && !k.equals("SPACE") || "⇧⌫⏎".contains(k); }

  void addRow(String row, boolean digitsOnLong) {
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
      boolean hi = k.equals("⏎") || (k.equals("⇧") && shift);
      b.setTextColor(hi ? 0xFFFFFFFF : t[3]);
      GradientDrawable d = new GradientDrawable();
      d.setCornerRadius(dp(7));
      d.setColor(hi ? t[4] : (sp ? t[2] : t[1]));
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
      case "SPACE": learn(curWord()); ic.commitText(" ", 1); updateShift(); return;
      case "⏎": {
        learn(curWord());
        EditorInfo ei = getCurrentInputEditorInfo();
        int a = ei == null ? 0 : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (ei != null && a > EditorInfo.IME_ACTION_NONE && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
          ic.performEditorAction(a);
        else ic.commitText("\n", 1);
        updateShift(); return;
      }
      default:
        if (!Character.isLetter(k.charAt(0))) learn(curWord());
        ic.commitText(shift && !sym && !emoji ? k.toUpperCase(TR) : k, 1);
        if (shift && !caps) { shift = false; redraw(); }
        if (".!?".contains(k)) updateShift();
    }
  }

  void paste() {
    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    ClipData cd = cm.getPrimaryClip();
    if (cd != null && cd.getItemCount() > 0) commit(cd.getItemAt(0).coerceToText(this).toString());
    else msg("Pano boş");
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
    if (src.trim().isEmpty()) { msg("Çevrilecek metin yok"); return; }
    msg("Çevriliyor…");
    final String tl = lang;
    new Thread(() -> {
      String r, err = null;
      try { r = tr(src, tl); } catch (Exception e) { r = null; err = e.getClass().getSimpleName() + ": " + e.getMessage(); }
      final String res = r, er = err;
      h.post(() -> {
        InputConnection c = getCurrentInputConnection();
        if (res == null || c == null) { msg("Çeviri hatası: " + er); return; }
        if (!hasSel) c.setSelection(start, start + src.length());
        c.commitText(res, 1);
      });
    }).start();
  }

  static String tr(String q, String tl) throws Exception {
    URL u = new URL("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=" + tl + "&q=" + URLEncoder.encode(q, "UTF-8"));
    HttpURLConnection c = (HttpURLConnection) u.openConnection();
    c.setRequestProperty("User-Agent", UA);
    c.setConnectTimeout(8000); c.setReadTimeout(8000);
    if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
    StringBuilder sb = new StringBuilder(); String l;
    while ((l = br.readLine()) != null) sb.append(l);
    Object o = new JSONArray(sb.toString()).get(0);
    return o instanceof JSONArray ? ((JSONArray) o).getString(0) : o.toString();
  }
}

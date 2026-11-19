package com.sype.klavye;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.function.IntConsumer;

public class SettingsActivity extends Activity {
  static final String[] LANG_CODES = {"en", "tr", "de", "fr", "es", "it", "ru", "ar", "ja", "ko", "zh-CN", "pt", "nl", "az"};
  static final String[] LANG_NAMES = {"İngilizce", "Türkçe", "Almanca", "Fransızca", "İspanyolca", "İtalyanca", "Rusça", "Arapça", "Japonca", "Korece", "Çince", "Portekizce", "Hollandaca", "Azerice"};
  SharedPreferences p;
  LinearLayout l;

  int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    setTitle("Klavye Ayarları");
    p = getSharedPreferences("k", 0);
    l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setPadding(dp(16), dp(16), dp(16), dp(16));
    ScrollView sv = new ScrollView(this);
    sv.addView(l);
    setContentView(sv);

    btn("1. Klavyeyi etkinleştir", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
    btn("2. Klavyeyi seç", v -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
    label("Tema");
    spin(KeyboardService.THEME_NAMES, p.getInt("theme", 0), i -> p.edit().putInt("theme", i).apply());
    label("Çeviri hedef dili (klavyedeki 文A tuşu)");
    int li = java.util.Arrays.asList(LANG_CODES).indexOf(p.getString("lang", "en"));
    spin(LANG_NAMES, Math.max(li, 0), i -> p.edit().putString("lang", LANG_CODES[i]).apply());
    sw("Tuşa basınca titreşim", "vib", true);
    sw("Tuşa basınca ses", "sound", false);
    sw("Sayı satırı", "num", false);
    sw("Otomatik büyük harf", "autocap", true);
    sw("Kelime önerileri", "suggest", true);
    label("Tuş yüksekliği");
    SeekBar sb = new SeekBar(this);
    sb.setMax(28);
    sb.setProgress(p.getInt("height", 48) - 36);
    sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar s, int v, boolean u) { p.edit().putInt("height", v + 36).apply(); }
      public void onStartTrackingTouch(SeekBar s) {}
      public void onStopTrackingTouch(SeekBar s) {}
    });
    l.addView(sb);
    label("Deneme alanı");
    EditText et = new EditText(this);
    et.setHint("Buraya yazarak dene…");
    l.addView(et);
  }

  void btn(String s, View.OnClickListener c) { Button b = new Button(this); b.setText(s); b.setOnClickListener(c); l.addView(b); }

  void label(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(15); t.setPadding(0, dp(16), 0, dp(4)); l.addView(t); }

  void sw(String s, String key, boolean def) {
    Switch w = new Switch(this);
    w.setText(s); w.setTextSize(16); w.setPadding(0, dp(10), 0, dp(10));
    w.setChecked(p.getBoolean(key, def));
    w.setOnCheckedChangeListener((v, c) -> p.edit().putBoolean(key, c).apply());
    l.addView(w);
  }

  void spin(String[] items, int sel, IntConsumer on) {
    Spinner s = new Spinner(this);
    s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
    s.setSelection(sel);
    s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> a, View v, int i, long id) { on.accept(i); }
      public void onNothingSelected(AdapterView<?> a) {}
    });
    l.addView(s);
  }
}

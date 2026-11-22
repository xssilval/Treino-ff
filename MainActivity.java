package com.treinoff;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        web.addJavascriptInterface(new Ponte(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("intent:")) {
                    abrirApp(url);
                    return true;
                }
                return false;
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    // Abre o app instalado (Free Fire ou MAX) a partir do link intent: da pagina.
    private void abrirApp(String url) {
        try {
            Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            String pkg = parsed.getPackage();
            Intent launch = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                startActivity(launch);
            } else {
                Toast.makeText(this, "Free Fire nao esta instalado neste celular.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Nao foi possivel abrir o jogo.", Toast.LENGTH_LONG).show();
        }
    }

    // Funcoes reais chamadas pela pagina (window.Android.*)
    class Ponte {

        @JavascriptInterface
        public String status() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int nivel = -1, escala = 100, temp = 0, est = -1;
                if (b != null) {
                    nivel = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    escala = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    est = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                }
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                JSONObject o = new JSONObject();
                o.put("ramLivre", mi.availMem / 1048576L);
                o.put("ramTotal", mi.totalMem / 1048576L);
                o.put("armLivre", Math.round(sf.getAvailableBytes() / 1073741824.0 * 10) / 10.0);
                o.put("bateria", escala > 0 ? nivel * 100 / escala : nivel);
                o.put("temp", temp / 10.0);
                o.put("carregando", est == BatteryManager.BATTERY_STATUS_CHARGING
                        || est == BatteryManager.BATTERY_STATUS_FULL);
                o.put("economia", pm.isPowerSaveMode());
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        // Fecha os processos em segundo plano dos apps instalados (menos do sistema,
        // deste app e do Free Fire). O Android pode reabrir alguns depois.
        @JavascriptInterface
        public String liberarRam() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long antes = mi.availMem;
                int n = 0;
                List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
                for (ApplicationInfo a : apps) {
                    if ((a.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    String p = a.packageName;
                    if (p.equals(getPackageName()) || p.equals("com.dts.freefireth")
                            || p.equals("com.dts.freefiremax")) continue;
                    am.killBackgroundProcesses(p);
                    n++;
                }
                Thread.sleep(1000);
                ActivityManager.MemoryInfo mi2 = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi2);
                JSONObject o = new JSONObject();
                o.put("antes", antes / 1048576L);
                o.put("depois", mi2.availMem / 1048576L);
                o.put("apps", n);
                return o.toString();
            } catch (Exception e) {
                return "{\"erro\":true}";
            }
        }

        @JavascriptInterface
        public void abrir(final String o) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i;
                        if (o.equals("bateria")) {
                            i = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                        } else if (o.equals("ff")) {
                            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:com.dts.freefireth"));
                        } else if (o.equals("jogar")) {
                            i = getPackageManager().getLaunchIntentForPackage("com.dts.freefireth");
                            if (i == null) {
                                Toast.makeText(MainActivity.this,
                                        "Free Fire nao esta instalado neste celular.",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                        } else {
                            return;
                        }
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Nao foi possivel abrir.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

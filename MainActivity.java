package com.fahri.chat;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    static final String URL = "https://" + HOST + "/assets/fahri-chat.html";
    static final int RQ_PERM = 100, RQ_FILE = 101, RQ_GEO = 103, RQ_WEB = 104;

    WebView web;
    PermissionRequest pendingReq;
    GeolocationPermissions.Callback geoCb;
    String geoOrigin;
    ValueCallback<Uri[]> filePicker;
    SharedPreferences sp;
    int nid = 1000;

    boolean has(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    void js(final String s) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (web != null) web.evaluateJavascript(s, null);
            }
        });
    }

    String[] permsFor(String key) {
        switch (key) {
            case "mic": return new String[]{Manifest.permission.RECORD_AUDIO};
            case "cam": return new String[]{Manifest.permission.CAMERA};
            case "geo": return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
            case "not":
                if (Build.VERSION.SDK_INT >= 33) return new String[]{Manifest.permission.POST_NOTIFICATIONS};
                return new String[0];
            default: return new String[0];
        }
    }

    String stateOf(String key) {
        String[] ps = permsFor(key);
        if (key.equals("not") && Build.VERSION.SDK_INT < 33) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            return (nm != null && nm.areNotificationsEnabled()) ? "granted" : "denied";
        }
        boolean any = false, all = true;
        for (String p : ps) {
            if (has(p)) any = true; else all = false;
        }
        if (key.equals("geo") ? any : all) return "granted";
        boolean asked = sp.getBoolean("asked_" + key, false);
        boolean rationale = ps.length > 0 && shouldShowRequestPermissionRationale(ps[0]);
        return (asked && !rationale) ? "denied" : "prompt";
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("fahri", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " FahriApp/1.0");
        web.addJavascriptInterface(new Bridge(), "FahriNative");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Tidak ada aplikasi untuk membuka tautan", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest r) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        List<String> need = new ArrayList<>();
                        for (String res : r.getResources()) {
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && !has(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO);
                            else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && !has(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA);
                        }
                        if (need.isEmpty()) {
                            r.grant(r.getResources());
                            return;
                        }
                        if (pendingReq != null) pendingReq.deny();
                        pendingReq = r;
                        for (String p : need) sp.edit().putBoolean("asked_" + (p.equals(Manifest.permission.CAMERA) ? "cam" : "mic"), true).apply();
                        requestPermissions(need.toArray(new String[0]), RQ_WEB);
                    }
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback cb) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            cb.invoke(origin, true, false);
                            return;
                        }
                        geoCb = cb;
                        geoOrigin = origin;
                        sp.edit().putBoolean("asked_geo", true).apply();
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, RQ_GEO);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePicker != null) filePicker.onReceiveValue(null);
                filePicker = cb;
                try {
                    startActivityForResult(p.createIntent(), RQ_FILE);
                } catch (ActivityNotFoundException e) {
                    filePicker = null;
                    Toast.makeText(MainActivity.this, "Tidak ada aplikasi pemilih file", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        web.setDownloadListener(new android.webkit.DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    if (!url.startsWith("http")) return;
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    if (mime != null) r.setMimeType(mime);
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime));
                    ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
                    Toast.makeText(MainActivity.this, "Mengunduh...", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Gagal mengunduh", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (b == null) web.loadUrl(URL); else web.restoreState(b);
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        if (web != null) web.saveState(o);
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq == RQ_FILE && filePicker != null) {
            filePicker.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d));
            filePicker = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int rq, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rq, perms, res);
        if (rq == RQ_PERM) {
            js("window.onNativePerm&&window.onNativePerm()");
        } else if (rq == RQ_GEO && geoCb != null) {
            geoCb.invoke(geoOrigin, has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION), false);
            geoCb = null;
        } else if (rq == RQ_WEB && pendingReq != null) {
            List<String> ok = new ArrayList<>();
            for (String r : pendingReq.getResources()) {
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && has(Manifest.permission.RECORD_AUDIO)) ok.add(r);
                else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && has(Manifest.permission.CAMERA)) ok.add(r);
            }
            if (ok.isEmpty()) pendingReq.deny(); else pendingReq.grant(ok.toArray(new String[0]));
            pendingReq = null;
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(false);
        } catch (Exception e) { }
        if (web != null) web.destroy();
        super.onDestroy();
    }

    class Bridge {
        @JavascriptInterface
        public String status() {
            try {
                JSONObject o = new JSONObject();
                String[] ks = {"mic", "cam", "geo", "not"};
                for (String k : ks) o.put(k, stateOf(k));
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void request(final String csv) {
            runOnUiThread(new Runnable() {
                public void run() {
                    List<String> need = new ArrayList<>();
                    for (String k : csv.split(",")) {
                        k = k.trim();
                        boolean missing = false;
                        for (String p : permsFor(k)) if (!has(p)) { need.add(p); missing = true; }
                        if (missing) sp.edit().putBoolean("asked_" + k, true).apply();
                    }
                    if (need.isEmpty()) {
                        js("window.onNativePerm&&window.onNativePerm()");
                        return;
                    }
                    requestPermissions(need.toArray(new String[0]), RQ_PERM);
                }
            });
        }

        @JavascriptInterface
        public void settings() {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            });
        }

        @JavascriptInterface
        public void notify(String title, String body) {
            try {
                if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) return;
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm == null) return;
                nm.createNotificationChannel(new NotificationChannel("msg", "Pesan", NotificationManager.IMPORTANCE_HIGH));
                PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 0,
                        new Intent(MainActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                Notification n = new Notification.Builder(MainActivity.this, "msg")
                        .setSmallIcon(R.drawable.ic_stat)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build();
                nm.notify(nid++, n);
            } catch (Exception e) { }
        }

        @JavascriptInterface
        public void audio(final boolean on) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        am.setMode(on ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
                        am.setSpeakerphoneOn(on);
                    } catch (Exception e) { }
                }
            });
        }
    }
}

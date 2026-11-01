package com.mg.assistant;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.Message;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/index.html";
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private SpeechRecognizer sr;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final int REQ_PERM = 11, REQ_FILE = 12;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setUserAgentString(s.getUserAgentString() + " MGApp/1.0");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handleUrl(r.getUrl().toString());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> req.grant(req.getResources()));
            }
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, Message msg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                        handleUrl(r.getUrl().toString());
                        return true;
                    }
                });
                ((WebView.WebViewTransport) msg.obj).setWebView(tmp);
                msg.sendToTarget();
                return true;
            }
        });

        web.setDownloadListener((url, ua, cd, mime, len) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        askPerms();
        web.loadUrl(START);
    }

    private boolean handleUrl(String url) {
        if (url.startsWith("https://" + HOST)) return false;
        try {
            if (url.startsWith("intent:")) {
                Intent it = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                try {
                    startActivity(it);
                } catch (ActivityNotFoundException e) {
                    String fb = it.getStringExtra("browser_fallback_url");
                    if (fb != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fb)));
                    else {
                        String pkg = it.getPackage();
                        if (pkg != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
                    }
                }
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Link nahi khul paaya", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void askPerms() {
        ArrayList<String> need = new ArrayList<>();
        String[] all = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
        for (String p : all) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (sr != null) { sr.destroy(); sr = null; }
        super.onDestroy();
    }

    private void js(String code) {
        ui.post(() -> web.evaluateJavascript(code, null));
    }

    private static String esc(String t) {
        return t == null ? "" : t.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }

    public class Bridge {
        @JavascriptInterface
        public void startListening(final String lang) {
            ui.post(() -> {
                if (!SpeechRecognizer.isRecognitionAvailable(MainActivity.this)) {
                    js("window.__sr&&window.__sr('error','service-not-allowed','');window.__sr&&window.__sr('end','','')");
                    return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    askPerms();
                    js("window.__sr&&window.__sr('error','not-allowed','');window.__sr&&window.__sr('end','','')");
                    return;
                }
                if (sr != null) { sr.destroy(); sr = null; }
                sr = SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                sr.setRecognitionListener(new RecognitionListener() {
                    boolean errored = false;
                    public void onReadyForSpeech(Bundle p) { js("window.__sr&&window.__sr('start','','')"); }
                    public void onBeginningOfSpeech() {}
                    public void onRmsChanged(float v) {}
                    public void onBufferReceived(byte[] b) {}
                    public void onEndOfSpeech() {}
                    public void onEvent(int t, Bundle p) {}
                    public void onPartialResults(Bundle p) {
                        ArrayList<String> l = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (l != null && !l.isEmpty()) js("window.__sr&&window.__sr('result','" + esc(l.get(0)) + "','0')");
                    }
                    public void onResults(Bundle p) {
                        ArrayList<String> l = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (l != null && !l.isEmpty()) js("window.__sr&&window.__sr('result','" + esc(l.get(0)) + "','1')");
                        js("window.__sr&&window.__sr('end','','')");
                    }
                    public void onError(int e) {
                        String c;
                        switch (e) {
                            case SpeechRecognizer.ERROR_NO_MATCH:
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: c = "no-speech"; break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: c = "not-allowed"; break;
                            case SpeechRecognizer.ERROR_CLIENT: c = "aborted"; break;
                            case SpeechRecognizer.ERROR_NETWORK:
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: c = "network"; break;
                            default: c = "audio-capture";
                        }
                        js("window.__sr&&window.__sr('error','" + c + "','');window.__sr&&window.__sr('end','','')");
                    }
                });
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang == null || lang.isEmpty() ? "hi-IN" : lang);
                i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                sr.startListening(i);
            });
        }

        @JavascriptInterface
        public void stopListening() { ui.post(() -> { if (sr != null) sr.stopListening(); }); }

        @JavascriptInterface
        public void cancelListening() { ui.post(() -> { if (sr != null) sr.cancel(); }); }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            ui.post(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public boolean saveFile(String name, String b64, String mime) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) return false;
                try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(data); }
                ui.post(() -> Toast.makeText(MainActivity.this, "Downloads mein save ho gaya: " + name, Toast.LENGTH_LONG).show());
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}

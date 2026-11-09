package com.myvloggingstudio.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyVloggingStudio for Android: the phone editor running in a WebView.
 * Everything is served from the app's own files, so it works without internet.
 */
public class MainActivity extends Activity {

    // An https address served from the APK's assets: the page gets a secure
    // origin, which the browser video encoder/decoder (WebCodecs) needs.
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/index.html";
    private static final int PICK_FILES = 7;

    private FrameLayout root;
    private WebView web;
    private ValueCallback<Uri[]> pendingPick;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // keep the screen on while the editor is open (long exports)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Saver(), "MVSAndroid");
        web.setWebViewClient(new LocalClient());
        web.setWebChromeClient(new Chrome());

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (fullView != null) {
            if (fullCallback != null) fullCallback.onCustomViewHidden();
            hideFull();
            return;
        }
        // let the editor close an open panel first; leave the app only when nothing is open
        web.evaluateJavascript("(window.mvsBack&&window.mvsBack())?'1':'0'", value -> {
            if (!"\"1\"".equals(value)) finish();
        });
    }

    /* ---------- files served from the APK ---------- */
    private class LocalClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (!HOST.equals(u.getHost())) return null;
            String p = u.getPath();
            if (p == null || p.equals("/") || p.isEmpty()) p = "/index.html";
            String mime = mimeOf(p);
            try {
                InputStream in = getAssets().open("www" + p);
                WebResourceResponse r = new WebResourceResponse(mime, mime.startsWith("text/") || mime.endsWith("javascript") ? "utf-8" : null, in);
                Map<String, String> h = new HashMap<>();
                h.put("Cache-Control", "no-cache");
                h.put("Access-Control-Allow-Origin", "*");
                r.setResponseHeaders(h);
                return r;
            } catch (IOException e) {
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (HOST.equals(u.getHost())) return false;
            // links to other sites open in the phone's browser
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
            return true;
        }
    }

    private static String mimeOf(String p) {
        String l = p.toLowerCase();
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".js")) return "application/javascript";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".woff2")) return "font/woff2";
        if (l.endsWith(".woff")) return "font/woff";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    /* ---------- picking videos, photos and audio ---------- */
    private class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (pendingPick != null) pendingPick.onReceiveValue(null);
            pendingPick = callback;
            List<String> types = new ArrayList<>();
            for (String t : params.getAcceptTypes()) {
                for (String one : t.split(",")) {
                    one = one.trim();
                    if (one.contains("/")) types.add(one);
                }
            }
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            if (types.size() == 1) i.setType(types.get(0));
            else {
                i.setType("*/*");
                if (!types.isEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
            }
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(Intent.createChooser(i, "Elegí los archivos"), PICK_FILES);
            } catch (Exception e) {
                pendingPick = null;
                return false;
            }
            return true;
        }

        // full screen video (for example the player shown after exporting)
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullView != null) { callback.onCustomViewHidden(); return; }
            fullView = view;
            fullCallback = callback;
            root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            web.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            hideFull();
        }
    }

    private void hideFull() {
        if (fullView == null) return;
        root.removeView(fullView);
        fullView = null;
        fullCallback = null;
        web.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILES || pendingPick == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                result = new Uri[clip.getItemCount()];
                for (int k = 0; k < clip.getItemCount(); k++) result[k] = clip.getItemAt(k).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        pendingPick.onReceiveValue(result);
        pendingPick = null;
    }

    /* ---------- saving the exported video to the gallery ---------- */
    private class Saver {
        private final Map<String, OutputStream> outs = new ConcurrentHashMap<>();
        private final Map<String, Uri> uris = new ConcurrentHashMap<>();

        @JavascriptInterface
        public String begin(String name, String mime) {
            try {
                ContentResolver cr = getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mime == null || mime.isEmpty() ? "video/mp4" : mime);
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MyVloggingStudio");
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri u = cr.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv);
                if (u == null) return null;
                OutputStream o = cr.openOutputStream(u);
                if (o == null) { cr.delete(u, null, null); return null; }
                String id = UUID.randomUUID().toString();
                outs.put(id, new BufferedOutputStream(o, 1 << 20));
                uris.put(id, u);
                return id;
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public boolean chunk(String id, String base64) {
            OutputStream o = outs.get(id);
            if (o == null) return false;
            try {
                o.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String end(String id) {
            OutputStream o = outs.remove(id);
            Uri u = uris.remove(id);
            if (o == null || u == null) return null;
            try {
                o.close();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(u, cv, null, null);
                return "Películas/MyVloggingStudio";
            } catch (Exception e) {
                try { getContentResolver().delete(u, null, null); } catch (Exception ignored) { }
                return null;
            }
        }

        @JavascriptInterface
        public void abort(String id) {
            OutputStream o = outs.remove(id);
            Uri u = uris.remove(id);
            try { if (o != null) o.close(); } catch (Exception ignored) { }
            try { if (u != null) getContentResolver().delete(u, null, null); } catch (Exception ignored) { }
        }
    }
}

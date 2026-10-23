package com.ironfield.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Ironfield is a web game. The app shows it full screen and loads it from the published site,
 * so every update you publish reaches players the next time they open the app. Without a
 * connection it falls back to the last cached copy, then to the copy bundled in the APK.
 * It also checks GitHub Releases for a newer APK and offers it.
 */
public class MainActivity extends Activity {
    private WebView web;
    private boolean usedBundledCopy = false;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xFF10130D);
        setContentView(web);
        hideSystemBars();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // saves (research points, slots) live in localStorage
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setUserAgentString(s.getUserAgentString() + " IronfieldApp/" + BuildConfig.VERSION_CODE);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !usedBundledCopy) {
                    usedBundledCopy = true;
                    view.loadUrl("file:///android_asset/index.html");
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String url = u.toString();
                if (url.startsWith(BuildConfig.GAME_URL) || "file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(BuildConfig.GAME_URL);

        checkForNewApk();
    }

    /** Back works like Esc in the game (pause/menu). In the hangar it leaves the app. */
    @Override
    public void onBackPressed() {
        String js = "(function(){var h=document.getElementById('hangar');"
                + "if(!h||!h.hidden)return 'hangar';"
                + "window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));"
                + "window.dispatchEvent(new KeyboardEvent('keyup',{key:'Escape'}));return 'game';})()";
        web.evaluateJavascript(js, result -> {
            if (result == null || result.contains("hangar")) finish();
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); hideSystemBars(); }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    /** Asks GitHub for the newest "apk-N" release; if N is higher than this build, offer the download. */
    private void checkForNewApk() {
        if (BuildConfig.REPO.startsWith("YOUR-")) return;
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + BuildConfig.REPO + "/releases/latest");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "IronfieldApp");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONObject rel = new JSONObject(sb.toString());
                String tag = rel.optString("tag_name", "");
                if (!tag.startsWith("apk-")) return;
                int latest = Integer.parseInt(tag.substring(4));
                if (latest <= BuildConfig.VERSION_CODE) return;
                String download = rel.optString("html_url", "");
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name", "").endsWith(".apk")) { download = a.optString("browser_download_url", download); break; }
                    }
                }
                final String link = download;
                ui.post(() -> {
                    if (isFinishing()) return;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("App update available")
                            .setMessage("A new version of the Ironfield app is out. Download and install it now? Your progress is kept.")
                            .setPositiveButton("Download", (d, w) -> {
                                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))); } catch (Exception ignored) { }
                            })
                            .setNegativeButton("Later", null)
                            .show();
                });
            } catch (Exception ignored) {
                // no connection or no releases yet: nothing to offer
            }
        }).start();
    }
}

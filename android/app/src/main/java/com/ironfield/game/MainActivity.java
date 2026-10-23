package com.ironfield.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Ironfield is a web game. The app shows it full screen at the published site's address, but
 * the page itself comes from a copy stored on the phone (first the one bundled in the APK, then
 * whatever update the player installed). On open it asks the site for version.json; if the
 * published game is different it offers "Update", downloads it, checks it and reloads.
 * Serving the copy under the site's https address keeps saves (localStorage) and co-op working.
 * It also checks GitHub Releases for a newer APK and offers it.
 */
public class MainActivity extends Activity {
    private static final long CHECK_EVERY_MS = 10 * 60 * 1000;

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private File gameFile;
    private long lastGameCheck = -CHECK_EVERY_MS;
    private boolean updateDialogOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("game", MODE_PRIVATE);
        gameFile = new File(getFilesDir(), "game/index.html");

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
            /** The game page is always the installed copy, so it works offline and only changes when the player taps Update. */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!"GET".equals(request.getMethod()) || !isGamePage(request.getUrl().toString())) return null;
                try {
                    return new WebResourceResponse("text/html", "UTF-8", openInstalledGame());
                } catch (Exception e) {
                    return null;
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

    private static boolean isGamePage(String url) {
        int cut = url.indexOf('?');
        if (cut < 0) cut = url.indexOf('#');
        if (cut >= 0) url = url.substring(0, cut);
        return url.equals(BuildConfig.GAME_URL) || url.equals(BuildConfig.GAME_URL + "index.html");
    }

    private InputStream openInstalledGame() throws Exception {
        if (gameFile.exists()) return new FileInputStream(gameFile);
        return getAssets().open("index.html");
    }

    /** Version of the game the player has: the last update they installed, or the one bundled in the APK. */
    private String installedGameVersion() {
        if (gameFile.exists()) return prefs.getString("version", "");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("game-version.txt"), "UTF-8"))) {
            String v = r.readLine();
            return v == null ? "" : v.trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** Asks the site which game version is published; if it differs from the installed one, offer it. */
    private void checkForGameUpdate() {
        if (BuildConfig.GAME_URL.contains("YOUR-") || updateDialogOpen) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastGameCheck < CHECK_EVERY_MS) return;
        lastGameCheck = now;
        new Thread(() -> {
            try {
                JSONObject info = new JSONObject(httpGet(BuildConfig.GAME_URL + "version.json?t=" + System.currentTimeMillis()));
                String version = info.optString("version", "");
                String notes = info.optString("notes", "").trim();
                if (version.isEmpty() || version.equals(installedGameVersion())) return;
                ui.post(() -> offerGameUpdate(version, notes));
            } catch (Exception ignored) {
                // offline or site not published yet: keep playing the installed copy
            }
        }).start();
    }

    private void offerGameUpdate(String version, String notes) {
        if (isFinishing() || updateDialogOpen) return;
        updateDialogOpen = true;
        String msg = "A new version of Ironfield is ready.";
        if (!notes.isEmpty()) msg += "\n\nWhat's new: " + notes;
        msg += "\n\nUpdate now? Your progress is kept.";
        new AlertDialog.Builder(this)
                .setTitle("Game update available")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("Update", (d, w) -> installGameUpdate(version))
                .setNegativeButton("Later", (d, w) -> updateDialogOpen = false)
                .show();
    }

    /** Downloads the published game, checks it matches the advertised version, swaps it in and reloads. */
    private void installGameUpdate(String version) {
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Updating Ironfield")
                .setMessage("Downloading the new version…")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            boolean ok = false;
            File tmp = new File(gameFile.getParentFile(), "index.html.download");
            try {
                gameFile.getParentFile().mkdirs();
                HttpURLConnection c = open(BuildConfig.GAME_URL + "index.html?v=" + version);
                if (c.getResponseCode() == 200) {
                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); sha.update(buf, 0, n); }
                    }
                    StringBuilder hex = new StringBuilder();
                    for (byte b : sha.digest()) hex.append(String.format("%02x", b));
                    // version.json holds the start of the file's SHA-256, so a half-downloaded or stale file is rejected
                    if (hex.toString().startsWith(version) && tmp.renameTo(gameFile)) {
                        prefs.edit().putString("version", version).apply();
                        ok = true;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                tmp.delete();
            }
            final boolean done = ok;
            ui.post(() -> {
                progress.dismiss();
                updateDialogOpen = false;
                if (done) {
                    web.loadUrl(BuildConfig.GAME_URL);
                    Toast.makeText(this, "Ironfield updated", Toast.LENGTH_SHORT).show();
                } else {
                    lastGameCheck = -CHECK_EVERY_MS;
                    Toast.makeText(this, "Update failed. Check your connection and reopen the game.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setRequestProperty("User-Agent", "IronfieldApp");
        c.setUseCaches(false);
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        return c;
    }

    private static String httpGet(String address) throws Exception {
        HttpURLConnection c = open(address);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
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
    protected void onResume() { super.onResume(); web.onResume(); hideSystemBars(); checkForGameUpdate(); }

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

    /** Asks GitHub for the newest release, tagged "v<app>.<build>"; if <app> is higher than this build, offer the download. */
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
                int dot = tag.indexOf('.');
                if (!tag.startsWith("v") || dot < 2) return;
                int latest = Integer.parseInt(tag.substring(1, dot));
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

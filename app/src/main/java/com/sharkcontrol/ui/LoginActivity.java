package com.sharkcontrol.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.sharkcontrol.R;

import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    // OAuth2 parameters from traffic capture
    private static final String CLIENT_ID    = "rKDx9O18dBrY3eoJMTkRiBZHDvd9Mx1I";
    private static final String AUTH_BASE    = "https://logineu.sharkninja.com";
    private static final String API_BASE     = "https://api-eu.sharkninja.com";
    private static final String REDIRECT_URI = "com.sharkninja.shark://logineu.sharkninja.com/android/com.sharkninja.shark/callback";
    private static final String SCOPE        = "openid profile email offline_access read:users read:current_user read:user_idp_tokens";

    private WebView webView;
    private ProgressBar progressBar;
    private String codeVerifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Auto-login if token saved
        SharedPreferences prefs = getSharedPreferences("SharkControl", MODE_PRIVATE);
        if (prefs.getString("access_token", null) != null) {
            navigateToMain();
            return;
        }

        progressBar = findViewById(R.id.progress_bar);
        webView = findViewById(R.id.web_view);

        setupWebView();
        startOAuthFlow();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Catch the redirect after successful login
                if (url.startsWith("com.sharkninja.shark://")) {
                    handleCallback(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void startOAuthFlow() {
        try {
            // Generate PKCE code_verifier and code_challenge
            codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);

            String authUrl = AUTH_BASE + "/authorize"
                    + "?scope=" + URLEncoder.encode(SCOPE, "UTF-8")
                    + "&mobile_shark_app_version=rn1.01"
                    + "&ui_locales=de"
                    + "&screen_hint=signin"
                    + "&os=android"
                    + "&response_type=code"
                    + "&code_challenge=" + codeChallenge
                    + "&code_challenge_method=S256"
                    + "&client_id=" + CLIENT_ID
                    + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8");

            webView.loadUrl(authUrl);
        } catch (Exception e) {
            Toast.makeText(this, "Fehler beim Start: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleCallback(String callbackUrl) {
        webView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);

        Uri uri = Uri.parse(callbackUrl);
        String code = uri.getQueryParameter("code");
        String error = uri.getQueryParameter("error");

        if (error != null) {
            Toast.makeText(this, "Login fehlgeschlagen: " + error, Toast.LENGTH_LONG).show();
            webView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            return;
        }

        if (code == null) {
            Toast.makeText(this, "Kein Autorisierungscode erhalten", Toast.LENGTH_LONG).show();
            return;
        }

        // Exchange authorization code for access token
        exchangeCodeForToken(code);
    }

    private void exchangeCodeForToken(String code) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String body = "grant_type=authorization_code"
                        + "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8")
                        + "&code=" + URLEncoder.encode(code, "UTF-8")
                        + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                        + "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8");

                URL url = new URL(AUTH_BASE + "/oauth/token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

                int responseCode = conn.getResponseCode();
                String response = readStream(responseCode < 400
                        ? conn.getInputStream() : conn.getErrorStream());

                if (responseCode == 200) {
                    JSONObject json = new JSONObject(response);
                    String accessToken = json.getString("access_token");
                    String refreshToken = json.optString("refresh_token", "");

                    SharedPreferences.Editor editor = getSharedPreferences("SharkControl", MODE_PRIVATE).edit();
                    editor.putString("access_token", accessToken);
                    editor.putString("refresh_token", refreshToken);
                    editor.apply();

                    runOnUiThread(this::navigateToMain);
                } else {
                    String errMsg = "Token-Fehler HTTP " + responseCode + ": " + response;
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // PKCE helpers
    private String generateCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[32];
        sr.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String readStream(InputStream s) throws IOException {
        if (s == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(s, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

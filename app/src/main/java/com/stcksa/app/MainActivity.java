package com.stcksa.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final String TARGET_URL = "http://dclub.vfq.theclub.mobi/Campaign/APP.aspx?ctg=OF06test";
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{4})\\b");
    private static final int SMS_CONSENT_REQUEST = 200;
    private static final int PHONE_HINT_REQUEST = 201;
    private static final int OTP_INJECT_MAX_RETRIES = 12;
    private static final long OTP_INJECT_RETRY_DELAY_MS = 300;

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout errorLayout;
    private boolean smsReceiverRegistered;
    private boolean smsConsentRequested;
    private boolean phoneHintRequested;
    private boolean phoneHintCompleted;
    private boolean otpFlowStarted;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SignInClient signInClient;

    private final BroadcastReceiver smsRetrieverReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                return;
            }

            Bundle extras = intent.getExtras();
            if (extras == null) {
                return;
            }

            Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
            if (status == null) {
                return;
            }

            int statusCode = status.getStatusCode();
            if (statusCode == CommonStatusCodes.SUCCESS) {
                Intent consentIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    consentIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent.class);
                } else {
                    consentIntent = (Intent) extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
                }

                if (consentIntent != null) {
                    try {
                        startActivityForResult(consentIntent, SMS_CONSENT_REQUEST);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Consent UI unavailable.", e);
                    }
                } else {
                    Log.w(TAG, "Consent intent was null.");
                }
                smsConsentRequested = false;
            } else if (statusCode == CommonStatusCodes.TIMEOUT) {
                Log.w(TAG, "SMS User Consent timed out.");
                smsConsentRequested = false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        errorLayout = findViewById(R.id.error_layout);
        Button retryButton = findViewById(R.id.retry_button);
        signInClient = Identity.getSignInClient(this);

        retryButton.setOnClickListener(v -> loadUrl());

        setupWebView();
        loadUrl();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerSmsRetrieverReceiver();
    }

    @Override
    protected void onStop() {
        unregisterSmsRetrieverReceiver();
        super.onStop();
    }

    private void registerSmsRetrieverReceiver() {
        if (smsReceiverRegistered) {
            return;
        }

        IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsRetrieverReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(smsRetrieverReceiver, intentFilter);
        }
        smsReceiverRegistered = true;
    }

    private void unregisterSmsRetrieverReceiver() {
        if (!smsReceiverRegistered) {
            return;
        }

        try {
            unregisterReceiver(smsRetrieverReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "SMS retriever receiver was already unregistered.", e);
        }
        smsReceiverRegistered = false;
    }

    private void startSmsUserConsent() {
        if (smsConsentRequested) {
            return;
        }

        smsConsentRequested = true;
        SmsRetriever.getClient(this)
                .startSmsUserConsent(null)
                .addOnSuccessListener(unused -> Log.d(TAG, "SMS User Consent started after Send PIN click."))
                .addOnFailureListener(e -> {
                    smsConsentRequested = false;
                    Log.e(TAG, "Failed to start SMS User Consent.", e);
                });
    }

    private void requestPhoneNumberHint() {
        if (phoneHintRequested) {
            return;
        }

        phoneHintRequested = true;
        GetPhoneNumberHintIntentRequest request = GetPhoneNumberHintIntentRequest.builder().build();
        signInClient.getPhoneNumberHintIntent(request)
                .addOnSuccessListener(result -> {
                    PendingIntent pendingIntent = result;
                    try {
                        startIntentSenderForResult(
                                pendingIntent.getIntentSender(),
                                PHONE_HINT_REQUEST,
                                null,
                                0,
                                0,
                                0
                        );
                    } catch (IntentSender.SendIntentException e) {
                        phoneHintRequested = false;
                        Log.e(TAG, "Failed to launch phone number hint UI.", e);
                    }
                })
                .addOnFailureListener(e -> {
                    phoneHintRequested = false;
                    Log.w(TAG, "Phone number hint unavailable on this device.", e);
                });
    }

    private String extractOtp(String message) {
        if (message == null) {
            return null;
        }

        Matcher matcher = OTP_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void injectOtpIntoWebView(String otp) {
        if (otp == null || otp.isEmpty() || webView == null) {
            return;
        }
        attemptOtpInjection(otp, 0);
    }

    private void attemptOtpInjection(String otp, int attempt) {
        final String safeOtp = otp.replace("\\", "\\\\").replace("'", "\\'");
        final String javascript = "(function(){"
                + "var otpInput="
                + "document.querySelector(\"input[placeholder='XXXX'],input[placeholder='xxxx']\")||"
                + "document.getElementById('otp')||document.getElementById('pin')||"
                + "document.querySelector(\"input[name='otp'],input[name='pin']\")||"
                + "document.getElementById('txtMsisdn')||document.querySelector(\"input[name='txtMsisdn']\");"
                + "if(!otpInput){return false;}"
                + "otpInput.focus();"
                + "otpInput.value='" + safeOtp + "';"
                + "otpInput.dispatchEvent(new Event('input',{bubbles:true}));"
                + "otpInput.dispatchEvent(new Event('change',{bubbles:true}));"
                + "return true;"
                + "})();";

        runOnUiThread(() -> webView.evaluateJavascript(javascript, value -> {
            boolean success = "true".equalsIgnoreCase(value);
            if (success) {
                Log.d(TAG, "OTP injected into txtMsisdn field.");
                return;
            }
            if (attempt < OTP_INJECT_MAX_RETRIES) {
                mainHandler.postDelayed(() -> attemptOtpInjection(otp, attempt + 1), OTP_INJECT_RETRY_DELAY_MS);
            } else {
                Log.w(TAG, "OTP field not found after retries.");
            }
        }));
    }

    private void injectMsisdnIntoWebView(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber) || webView == null) {
            return;
        }

        String localMsisdn = toQatarLocalMsisdn(phoneNumber);
        if (TextUtils.isEmpty(localMsisdn)) {
            Log.w(TAG, "Could not derive 8-digit Qatar MSISDN from hint: " + phoneNumber);
            return;
        }

        String safeMsisdn = localMsisdn.replace("\\", "\\\\").replace("'", "\\'");
        final String javascript = "(function(){"
                + "window.__stcksaSuppressMsisdnHint=true;"
                + "var msisdn="
                + "document.querySelector(\"input[placeholder='XXXXXXXX'],input[placeholder='xxxxxxxx']\")||"
                + "document.getElementById('txtMsisdn')||document.querySelector(\"input[name='txtMsisdn'],input[type='tel']\");"
                + "if(!msisdn){window.__stcksaSuppressMsisdnHint=false;return false;}"
                + "msisdn.focus();"
                + "msisdn.value='" + safeMsisdn + "';"
                + "msisdn.dispatchEvent(new Event('input',{bubbles:true}));"
                + "msisdn.dispatchEvent(new Event('change',{bubbles:true}));"
                + "window.__stcksaSuppressMsisdnHint=false;"
                + "return true;"
                + "})();";

        runOnUiThread(() -> webView.evaluateJavascript(javascript, value -> {
            if ("true".equalsIgnoreCase(value)) {
                Log.d(TAG, "Qatar 8-digit MSISDN injected into txtMsisdn field.");
            } else {
                Log.w(TAG, "MSISDN field not found in WebView.");
            }
        }));
    }

    private String toQatarLocalMsisdn(String phoneNumber) {
        String digitsOnly = phoneNumber.replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(digitsOnly)) {
            return null;
        }

        if (digitsOnly.startsWith("00974")) {
            digitsOnly = digitsOnly.substring(5);
        } else if (digitsOnly.startsWith("974")) {
            digitsOnly = digitsOnly.substring(3);
        }

        if (digitsOnly.length() > 8) {
            digitsOnly = digitsOnly.substring(digitsOnly.length() - 8);
        }

        return digitsOnly.length() == 8 ? digitsOnly : null;
    }

    private void attachSubmitHook() {
        final String hookScript = "(function(){"
                + "if(window.__stcksaSubmitHook){return;}"
                + "window.__stcksaSubmitHook=true;"
                + "function textOf(el){return ((el.innerText||el.value||el.textContent||'')+'').toLowerCase();}"
                + "function shouldTrackOtp(el){var t=textOf(el);"
                + "return t.indexOf('send pin')>=0||t.indexOf('send otp')>=0||t.indexOf('get pin')>=0||t.indexOf('get otp')>=0||"
                + "t.indexOf('subscribe now')>=0||t.indexOf('confirm')>=0;}"
                + "function isVisible(el){if(!el){return false;}var r=el.getBoundingClientRect();return r.width>0&&r.height>0&&r.bottom>0&&r.right>0;}"
                + "function isMsisdnCandidate(el){"
                + "if(!el||!isVisible(el)){return false;}"
                + "var tag=(el.tagName||'').toLowerCase();"
                + "if(tag!=='input'&&tag!=='textarea'){return false;}"
                + "var type=((el.type||'text')+'').toLowerCase();"
                + "if(type!=='tel'&&type!=='number'&&type!=='text'){return false;}"
                + "var id=((el.id||'')+'').toLowerCase();"
                + "var name=((el.name||'')+'').toLowerCase();"
                + "var ph=((el.placeholder||'')+'').toLowerCase();"
                + "var max=((el.maxLength||'')+'').toLowerCase();"
                + "if(ph==='xxxx'||ph.indexOf(' otp')>=0||ph.indexOf('otp')>=0){return false;}"
                + "if(ph.indexOf('xxxxxxxx')>=0){return true;}"
                + "if(id.indexOf('msisdn')>=0||name.indexOf('msisdn')>=0){return true;}"
                + "if(id.indexOf('mobile')>=0||name.indexOf('mobile')>=0){return true;}"
                + "if(id.indexOf('phone')>=0||name.indexOf('phone')>=0){return true;}"
                + "if(ph.indexOf('mobile')>=0||ph.indexOf('phone')>=0||ph.indexOf('msisdn')>=0){return true;}"
                + "if(max==='8'){return true;}"
                + "return false;"
                + "}"
                + "var nodes=document.querySelectorAll(\"button,input[type='button'],input[type='submit'],a\");"
                + "for(var i=0;i<nodes.length;i++){var el=nodes[i];"
                + "if(shouldTrackOtp(el)&&!el.__stcksaOtpBound){el.__stcksaOtpBound=true;el.addEventListener('click',function(){window.__stcksaOtpPhase=true;if(window.AndroidOtpBridge&&AndroidOtpBridge.onOtpRequestStarted){AndroidOtpBridge.onOtpRequestStarted();}},true);}"
                + "}"
                + "if(!window.__stcksaMsisdnGlobalBound){window.__stcksaMsisdnGlobalBound=true;"
                + "document.addEventListener('focusin',function(e){"
                + "var el=e&&e.target?e.target:null;"
                + "if(window.__stcksaSuppressMsisdnHint||window.__stcksaOtpPhase||!isMsisdnCandidate(el)){return;}"
                + "if(window.AndroidOtpBridge&&AndroidOtpBridge.onMsisdnRequested){AndroidOtpBridge.onMsisdnRequested();}"
                + "},true);"
                + "document.addEventListener('click',function(e){"
                + "var el=e&&e.target?e.target:null;"
                + "if(window.__stcksaSuppressMsisdnHint||window.__stcksaOtpPhase||!isMsisdnCandidate(el)){return;}"
                + "if(window.AndroidOtpBridge&&AndroidOtpBridge.onMsisdnRequested){AndroidOtpBridge.onMsisdnRequested();}"
                + "},true);"
                + "}"
                + "})();";

        webView.evaluateJavascript(hookScript, null);
    }

    private class OtpJsBridge {
        @JavascriptInterface
        public void onOtpRequestStarted() {
            runOnUiThread(() -> {
                otpFlowStarted = true;
                Log.d(TAG, "Send PIN clicked. Starting SMS consent listener.");
                startSmsUserConsent();
            });
        }

        @JavascriptInterface
        public void onMsisdnRequested() {
            runOnUiThread(() -> {
                if (otpFlowStarted || phoneHintCompleted || phoneHintRequested) {
                    return;
                }
                Log.d(TAG, "MSISDN field interaction detected. Opening phone number chooser.");
                requestPhoneNumberHint();
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SMS_CONSENT_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                String message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
                String otp = extractOtp(message);
                if (otp != null) {
                    injectOtpIntoWebView(otp);
                } else {
                    Log.w(TAG, "Consent granted but no OTP found in SMS.");
                }
            } else {
                Log.w(TAG, "User denied SMS read consent.");
            }
            return;
        }

        if (requestCode == PHONE_HINT_REQUEST) {
            phoneHintRequested = false;
            if (resultCode == RESULT_OK && data != null) {
                try {
                    String phoneNumber = signInClient.getPhoneNumberFromIntent(data);
                    phoneHintCompleted = true;
                    injectMsisdnIntoWebView(phoneNumber);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse selected phone number.", e);
                }
            } else {
                Log.d(TAG, "Phone number hint canceled by user.");
            }
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new OtpJsBridge(), "AndroidOtpBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                webView.setVisibility(View.INVISIBLE);
                errorLayout.setVisibility(View.GONE);
                if (url != null && url.contains("APP.aspx")) {
                    phoneHintCompleted = false;
                    otpFlowStarted = false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                phoneHintRequested = false;
                attachSubmitHook();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                progressBar.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
                errorLayout.setVisibility(View.VISIBLE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });
    }

    private void loadUrl() {
        if (isNetworkAvailable()) {
            errorLayout.setVisibility(View.GONE);
            webView.loadUrl(TARGET_URL);
        } else {
            progressBar.setVisibility(View.GONE);
            webView.setVisibility(View.GONE);
            errorLayout.setVisibility(View.VISIBLE);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}

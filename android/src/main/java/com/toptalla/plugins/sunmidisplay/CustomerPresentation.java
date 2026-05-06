package com.toptalla.plugins.sunmidisplay;

import android.app.Presentation;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import com.getcapacitor.Bridge;

public class CustomerPresentation extends Presentation {

    interface EvaluationCallback {
        void onResult(String value);
        void onError(Exception error);
    }

    private static final String TAG = "SunmiCustomerDisplay";
    private static final String DEFAULT_BASE_URL = "https://localhost/";

    private final List<PendingEvaluation> pendingEvaluations = new ArrayList<>();
    private final Bridge bridge;
    private WebView webView;
    private String pendingHtml;
    private String pendingBaseUrl;
    private String pendingUrl;
    private boolean documentLoaded;

    public CustomerPresentation(android.content.Context outerContext, Display display, Bridge bridge) {
        super(outerContext, display);
        this.bridge = bridge;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(getContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.BLACK);

        webView = new WebView(getContext());
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(Color.BLACK);

        configureWebView(webView);
        root.addView(webView);
        setContentView(root);

        if (pendingUrl != null) {
            loadUrlInternal(pendingUrl);
        } else if (pendingHtml != null) {
            loadHtmlInternal(pendingHtml, pendingBaseUrl);
        }
    }

    public void loadHtml(String html, String baseUrl) {
        rejectPendingEvaluations(new IllegalStateException("A new HTML document was loaded."));
        documentLoaded = false;
        pendingUrl = null;
        pendingHtml = html;
        pendingBaseUrl = normalizeBaseUrl(baseUrl);

        if (webView != null) {
            loadHtmlInternal(pendingHtml, pendingBaseUrl);
        }
    }

    public void loadUrl(String url) {
        rejectPendingEvaluations(new IllegalStateException("A new URL was loaded."));
        documentLoaded = false;
        pendingHtml = null;
        pendingBaseUrl = null;
        pendingUrl = url;

        if (webView != null) {
            loadUrlInternal(pendingUrl);
        }
    }

    public void evaluateJavascript(String script, EvaluationCallback callback) {
        PendingEvaluation evaluation = new PendingEvaluation(script, callback);

        if (webView == null || !documentLoaded) {
            pendingEvaluations.add(evaluation);
            return;
        }

        runEvaluation(evaluation);
    }

    private void configureWebView(WebView targetWebView) {
        WebSettings settings = targetWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setDatabaseEnabled(true);
        WebView.setWebContentsDebuggingEnabled(isAppDebuggable());

        targetWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(
                        TAG,
                        consoleMessage.messageLevel() + " " +
                                consoleMessage.sourceId() + ":" +
                                consoleMessage.lineNumber() + " " +
                                consoleMessage.message()
                );
                return super.onConsoleMessage(consoleMessage);
            }
        });

        targetWebView.setWebViewClient(new CustomerDisplayWebViewClient());
    }

    private void loadHtmlInternal(String html, String baseUrl) {
        if (webView == null) {
            return;
        }

        documentLoaded = false;
        webView.loadDataWithBaseURL(
                normalizeBaseUrl(baseUrl),
                html,
                "text/html",
                "utf-8",
                null
        );
    }

    private void loadUrlInternal(String url) {
        if (webView == null) {
            return;
        }

        documentLoaded = false;
        webView.loadUrl(url);
    }

    private void drainPendingEvaluations() {
        if (pendingEvaluations.isEmpty()) {
            return;
        }

        List<PendingEvaluation> evaluations = new ArrayList<>(pendingEvaluations);
        pendingEvaluations.clear();
        for (PendingEvaluation evaluation : evaluations) {
            runEvaluation(evaluation);
        }
    }

    private void runEvaluation(PendingEvaluation evaluation) {
        if (webView == null) {
            evaluation.callback.onError(new IllegalStateException("WebView is not available."));
            return;
        }

        webView.evaluateJavascript(evaluation.script, value -> evaluation.callback.onResult(value));
    }

    private void destroyWebView() {
        rejectPendingEvaluations(new IllegalStateException("Customer display was dismissed."));

        if (webView == null) {
            return;
        }

        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.destroy();
        } catch (Exception ignored) {
            Log.w(TAG, "Failed to fully tear down customer display WebView", ignored);
        } finally {
            webView = null;
            documentLoaded = false;
        }
    }

    private void rejectPendingEvaluations(Exception error) {
        if (pendingEvaluations.isEmpty()) {
            return;
        }

        List<PendingEvaluation> evaluations = new ArrayList<>(pendingEvaluations);
        pendingEvaluations.clear();
        for (PendingEvaluation evaluation : evaluations) {
            evaluation.callback.onError(error);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return baseUrl.trim();
    }

    private boolean isAppDebuggable() {
        return (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    @Override
    protected void onStop() {
        destroyWebView();
        super.onStop();
    }

    private static class PendingEvaluation {
        private final String script;
        private final EvaluationCallback callback;

        private PendingEvaluation(String script, EvaluationCallback callback) {
            this.script = script;
            this.callback = callback;
        }
    }

    private class CustomerDisplayWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (bridge != null && bridge.getLocalServer() != null) {
                return bridge.getLocalServer().shouldInterceptRequest(request);
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (bridge != null) {
                return bridge.launchIntent(request.getUrl());
            }

            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d(TAG, "Customer display page started: " + url);
            documentLoaded = false;
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.d(TAG, "Customer display page finished: " + url);
            documentLoaded = true;
            drainPendingEvaluations();
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                android.webkit.WebResourceError error
        ) {
            if (request.isForMainFrame()) {
                Log.e(
                        TAG,
                        "Customer display load error: " +
                                error.getErrorCode() + " " +
                                error.getDescription() + " for " +
                                request.getUrl()
                );
            }
            super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedHttpError(
                WebView view,
                WebResourceRequest request,
                WebResourceResponse errorResponse
        ) {
            if (request.isForMainFrame()) {
                Log.e(
                        TAG,
                        "Customer display HTTP error: " +
                                errorResponse.getStatusCode() + " for " +
                                request.getUrl()
                );
            }
            super.onReceivedHttpError(view, request, errorResponse);
        }
    }
}

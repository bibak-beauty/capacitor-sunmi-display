package com.toptalla.plugins.sunmidisplay;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashSet;
import java.util.Set;

@CapacitorPlugin(name = "SunmiDisplay")
public class SunmiDisplayPlugin extends Plugin {

    private static final String TAG = "SunmiDisplayPlugin";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> presentationDisplayIds = new HashSet<>();
    private DisplayManager displayManager;
    private DisplayManager.DisplayListener displayListener;
    private CustomerPresentation presentation;

    @Override
    public void load() {
        try {
            ensureDisplayManager();
            registerDisplayListener();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize Sunmi display plugin", t);
            displayManager = null;
            displayListener = null;
            presentationDisplayIds.clear();
        }
    }

    @Override
    protected void handleOnDestroy() {
        mainHandler.post(this::dismissPresentationInternal);
        if (displayManager != null && displayListener != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        displayListener = null;
        presentationDisplayIds.clear();
        super.handleOnDestroy();
    }

    @PluginMethod
    public void isSecondaryDisplayAvailable(PluginCall call) {
        Display[] displays = getPresentationDisplays();
        JSObject ret = new JSObject();
        ret.put("available", displays.length > 0);
        ret.put("displayCount", displays.length);
        call.resolve(ret);
    }

    @PluginMethod
    public void showHtml(PluginCall call) {
        String html = call.getString("html");
        if (isBlank(html)) {
            call.reject("`html` is required.");
            return;
        }

        Display[] displays = getPresentationDisplays();
        if (displays.length == 0) {
            call.reject("No secondary display available.");
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            call.reject("No active activity.");
            return;
        }

        String baseUrl = call.getString("baseUrl");
        Display display = displays[0];
        Bridge bridge = getBridge();

        mainHandler.post(() -> {
            try {
                if (presentation != null && presentation.getDisplay() != null
                        && presentation.getDisplay().getDisplayId() == display.getDisplayId()) {
                    presentation.loadHtml(html, baseUrl);
                } else {
                    dismissPresentationInternal();
                    presentation = new CustomerPresentation(activity, display, bridge);
                    presentation.loadHtml(html, baseUrl);
                    presentation.setOnDismissListener(d -> presentation = null);
                    presentation.show();
                }

                JSObject ret = new JSObject();
                ret.put("shown", true);
                call.resolve(ret);
            } catch (Exception ex) {
                presentation = null;
                call.reject("Failed to show secondary display HTML: " + ex.getMessage(), ex);
            }
        });
    }

    @PluginMethod
    public void showUrl(PluginCall call) {
        String url = call.getString("url");
        if (isBlank(url)) {
            call.reject("`url` is required.");
            return;
        }

        Display[] displays = getPresentationDisplays();
        if (displays.length == 0) {
            call.reject("No secondary display available.");
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            call.reject("No active activity.");
            return;
        }

        Display display = displays[0];
        Bridge bridge = getBridge();

        mainHandler.post(() -> {
            try {
                if (presentation != null && presentation.getDisplay() != null
                        && presentation.getDisplay().getDisplayId() == display.getDisplayId()) {
                    presentation.loadUrl(url);
                } else {
                    dismissPresentationInternal();
                    presentation = new CustomerPresentation(activity, display, bridge);
                    presentation.loadUrl(url);
                    presentation.setOnDismissListener(d -> presentation = null);
                    presentation.show();
                }

                JSObject ret = new JSObject();
                ret.put("shown", true);
                call.resolve(ret);
            } catch (Exception ex) {
                presentation = null;
                call.reject("Failed to show secondary display URL: " + ex.getMessage(), ex);
            }
        });
    }

    @PluginMethod
    public void evaluateJavascript(PluginCall call) {
        String script = call.getString("script");
        if (isBlank(script)) {
            call.reject("`script` is required.");
            return;
        }

        mainHandler.post(() -> {
            if (presentation == null) {
                call.reject("Secondary display is not currently shown. Call showHtml first.");
                return;
            }

            try {
                presentation.evaluateJavascript(script, new CustomerPresentation.EvaluationCallback() {
                    @Override
                    public void onResult(String value) {
                        JSObject ret = new JSObject();
                        ret.put("value", value);
                        call.resolve(ret);
                    }

                    @Override
                    public void onError(Exception error) {
                        call.reject("Failed to evaluate JavaScript: " + error.getMessage(), error);
                    }
                });
            } catch (Exception ex) {
                call.reject("Failed to evaluate JavaScript: " + ex.getMessage(), ex);
            }
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        mainHandler.post(() -> {
            dismissPresentationInternal();
            call.resolve();
        });
    }

    private void dismissPresentationInternal() {
        if (presentation == null) {
            return;
        }

        try {
            presentation.dismiss();
        } catch (Exception ignored) {
            // Dialog may already be detached from window.
        } finally {
            presentation = null;
        }
    }

    private Display[] getPresentationDisplays() {
        ensureDisplayManager();
        if (displayManager == null) {
            return new Display[0];
        }
        return displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
    }

    private void ensureDisplayManager() {
        if (displayManager != null) {
            return;
        }

        Context context = getContext();
        if (context == null) {
            return;
        }

        displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    }

    private void registerDisplayListener() {
        if (displayManager == null || displayListener != null) {
            return;
        }

        refreshPresentationDisplayIds();
        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                if (!isPresentationDisplay(displayId) || !presentationDisplayIds.add(displayId)) {
                    return;
                }

                notifyListeners("secondaryDisplayConnected", payload(displayId), true);
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                if (!presentationDisplayIds.remove(displayId)) {
                    return;
                }

                if (getPresentationDisplayId() == displayId) {
                    dismissPresentationInternal();
                }

                notifyListeners("secondaryDisplayDisconnected", payload(displayId), true);
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // No-op: availability is represented by added/removed events.
            }
        };

        displayManager.registerDisplayListener(displayListener, mainHandler);
    }

    private void refreshPresentationDisplayIds() {
        presentationDisplayIds.clear();
        for (Display display : getPresentationDisplays()) {
            presentationDisplayIds.add(display.getDisplayId());
        }
    }

    private boolean isPresentationDisplay(int displayId) {
        for (Display display : getPresentationDisplays()) {
            if (display.getDisplayId() == displayId) {
                return true;
            }
        }
        return false;
    }

    private int getPresentationDisplayId() {
        if (presentation == null || presentation.getDisplay() == null) {
            return Display.INVALID_DISPLAY;
        }
        return presentation.getDisplay().getDisplayId();
    }

    private JSObject payload(int displayId) {
        JSObject obj = new JSObject();
        obj.put("displayId", displayId);
        return obj;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

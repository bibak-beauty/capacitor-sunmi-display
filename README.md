# @toptalla/capacitor-sunmi-display

Android-only Capacitor plugin for rendering HTML on the customer-facing screen of Sunmi dual-screen POS devices.

The plugin uses the standard Android `DisplayManager` + `Presentation` APIs. It does not require a Sunmi-specific AAR and does not support Sunmi T1-style separate customer-display APK flows.

## Install

```sh
npm install @toptalla/capacitor-sunmi-display
npx cap sync android
```

## API

```ts
import { SunmiDisplay } from '@toptalla/capacitor-sunmi-display';

const availability = await SunmiDisplay.isSecondaryDisplayAvailable();

if (availability.available) {
  await SunmiDisplay.showHtml({
    html: '<!doctype html><html><body><h1>Hello customer</h1></body></html>',
    baseUrl: window.location.origin,
  });

  await SunmiDisplay.evaluateJavascript({
    script: "document.body.style.background = '#fff7e8';",
  });
}

await SunmiDisplay.hide();
```

## Methods

### `isSecondaryDisplayAvailable()`

Returns whether Android exposes at least one presentation display.

### `showHtml({ html, baseUrl? })`

Shows the secondary display and loads the provided HTML into a WebView. `baseUrl` is optional, but pass your app origin when the HTML references relative assets.

### `evaluateJavascript({ script })`

Evaluates JavaScript inside the secondary-display WebView. Calls are queued until the HTML document finishes loading.

### `hide()`

Dismisses the secondary-display presentation. On Sunmi devices this typically returns the customer display to its default mirroring behavior.

### Events

```ts
const connected = await SunmiDisplay.addListener(
  'secondaryDisplayConnected',
  (event) => console.log(event.displayId),
);

const disconnected = await SunmiDisplay.addListener(
  'secondaryDisplayDisconnected',
  (event) => console.log(event.displayId),
);

await connected.remove();
await disconnected.remove();
```

## Notes

- Android only. The web implementation reports no display and throws for display-control methods.
- Test on physical Sunmi dual-screen hardware before shipping.
- Keep the HTML self-contained where possible. If you reference app assets, pass a `baseUrl`.

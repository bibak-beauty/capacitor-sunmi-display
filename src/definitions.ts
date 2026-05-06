import type { PluginListenerHandle } from '@capacitor/core';

export interface ShowHtmlOptions {
  /**
   * Complete HTML document to render on the secondary display.
   */
  html: string;
  /**
   * Optional base URL used by the WebView to resolve relative assets.
   */
  baseUrl?: string;
}

export interface ShowUrlOptions {
  /**
   * Absolute URL the secondary display WebView should navigate to.
   */
  url: string;
}

export interface EvaluateJavascriptOptions {
  /**
   * JavaScript to evaluate inside the secondary-display WebView.
   */
  script: string;
}

export interface IsAvailableResult {
  available: boolean;
  displayCount: number;
}

export interface ShowHtmlResult {
  shown: boolean;
}

export interface EvaluateJavascriptResult {
  value?: string;
}

export interface SecondaryDisplayEvent {
  displayId: number;
}

export type SunmiDisplayEventName =
  | 'secondaryDisplayConnected'
  | 'secondaryDisplayDisconnected';

export interface SunmiDisplayPlugin {
  isSecondaryDisplayAvailable(): Promise<IsAvailableResult>;
  showHtml(options: ShowHtmlOptions): Promise<ShowHtmlResult>;
  showUrl(options: ShowUrlOptions): Promise<ShowHtmlResult>;
  evaluateJavascript(
    options: EvaluateJavascriptOptions,
  ): Promise<EvaluateJavascriptResult>;
  hide(): Promise<void>;

  addListener(
    eventName: SunmiDisplayEventName,
    listenerFunc: (event: SecondaryDisplayEvent) => void,
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

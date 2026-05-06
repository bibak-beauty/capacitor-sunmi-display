import { WebPlugin } from '@capacitor/core';

import type {
  EvaluateJavascriptOptions,
  EvaluateJavascriptResult,
  IsAvailableResult,
  ShowHtmlOptions,
  ShowHtmlResult,
  ShowUrlOptions,
  SunmiDisplayPlugin,
} from './definitions';

const UNAVAILABLE = 'SunmiDisplay is only available on Android.';

export class SunmiDisplayWeb extends WebPlugin implements SunmiDisplayPlugin {
  async isSecondaryDisplayAvailable(): Promise<IsAvailableResult> {
    return { available: false, displayCount: 0 };
  }

  async showHtml(_options: ShowHtmlOptions): Promise<ShowHtmlResult> {
    throw this.unavailable(UNAVAILABLE);
  }

  async showUrl(_options: ShowUrlOptions): Promise<ShowHtmlResult> {
    throw this.unavailable(UNAVAILABLE);
  }

  async evaluateJavascript(
    _options: EvaluateJavascriptOptions,
  ): Promise<EvaluateJavascriptResult> {
    throw this.unavailable(UNAVAILABLE);
  }

  async hide(): Promise<void> {
    throw this.unavailable(UNAVAILABLE);
  }
}

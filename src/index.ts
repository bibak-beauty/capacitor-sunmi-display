import { registerPlugin } from '@capacitor/core';

import type { SunmiDisplayPlugin } from './definitions';

const SunmiDisplay = registerPlugin<SunmiDisplayPlugin>('SunmiDisplay', {
  web: () => import('./web').then((m) => new m.SunmiDisplayWeb()),
});

export * from './definitions';
export { SunmiDisplay };

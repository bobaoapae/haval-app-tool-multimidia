// Theme Lab only: reuse its allowlisted mock bridge without shipping it in OTA.
import { installApexGtSimulator } from './apex-gt-simulator.js';

export function installStarlightSimulator(frameWindow) {
  return installApexGtSimulator(frameWindow, {
    rootId: 'starlight',
    cleanupEvent: 'starlight-cleanup',
    mockName: 'Starlight',
  });
}

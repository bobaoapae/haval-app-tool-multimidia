import { setState } from './core/state.js';
import './core/main.js';

// Wait for the original main.js to initialize, then force light mode
setState('nightMode', false);
console.log('[AirControlLight] Theme forced to light mode');

/*
 * Debug-only live telemetry smoke test for the cluster WebView.
 *
 * Usage:
 *   frida -H 127.0.0.1:27042 -n br.com.redesurftank.havalshisuku \
 *     -l scripts/debug/inject-cluster-telemetry.js --quiet
 */

'use strict';

Java.perform(function () {
    const Projector = 'br.com.redesurftank.havalshisuku.projectors.InstrumentProjector2';
    const ValueCallback = Java.use('android.webkit.ValueCallback');
    const Callback = Java.registerClass({
        name: 'br.com.redesurftank.havalshisuku.debug.TelemetrySmokeCallback' + Date.now(),
        implements: [ValueCallback],
        methods: {
            onReceiveValue: function (value) {
                send({ type: 'telemetry-result', value: String(value) });
            }
        }
    });

    const expression = `(() => {
        const telemetry = {
            carSpeed: 87,
            engineRPM: 2450,
            evPowerKw: 31.4,
            fuelPercent: 63,
            batteryPercent: 78,
            fuelRange: 410,
            batteryRange: 126,
            gearState: 'D',
            drivingMode: '1',
            evMode: '3',
            clockTime: '18:07'
        };
        for (const [key, value] of Object.entries(telemetry)) {
            window.control(key, value);
        }
        const text = selector => document.querySelector(selector)?.textContent?.trim() || null;
        return JSON.stringify({
            controlAvailable: typeof window.control === 'function',
            injected: telemetry,
            rendered: {
                speed: text('.dashboard-speed-value'),
                rpm: text('.rpm-value'),
                gear: text('.dashboard-gear'),
                clock: text('.dashboard-clock'),
                fuel: text('.fuel-liters'),
                battery: text('.battery-percent')
            }
        });
    })()`;

    let matched = false;
    Java.choose(Projector, {
        onMatch: function (instance) {
            if (matched) return;
            matched = true;
            Java.scheduleOnMainThread(function () {
                try {
                    const webView = instance.getWebView();
                    if (webView === null) {
                        send({ type: 'telemetry-error', error: 'InstrumentProjector2 WebView is null' });
                        return;
                    }
                    webView.evaluateJavascript(expression, Callback.$new());
                } catch (error) {
                    send({ type: 'telemetry-error', error: String(error), stack: error.stack || '' });
                }
            });
        },
        onComplete: function () {
            if (!matched) {
                send({ type: 'telemetry-error', error: 'No InstrumentProjector2 instance found' });
            }
        }
    });
});

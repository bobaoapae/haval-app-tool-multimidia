/**
 * install-car.mjs
 * Instala o Haval Impulse + Shizuku no carro via telnet + HTTP local.
 * Uso: node install-car.mjs
 *
 * Detecta gateway automaticamente. Serve todos os binários localmente.
 */

import http from 'http';
import https from 'https';
import net from 'net';
import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RAW = path.join(__dirname, 'app/src/main/res/raw');

const APK_PATH          = path.join(__dirname, 'app/build/outputs/apk/debug/app-debug.apk');
const SYS_JS_PATH       = path.join(RAW, 'system_server.js');
const GRANT_NLS_PATH    = path.join(RAW, 'grant_nls.js');
const FRIDA_SERVER_PATH = path.join(RAW, 'fridaserver');
const FRIDA_INJECT_PATH = path.join(RAW, 'fridainject');
const SHIZUKU_PATH      = path.join(RAW, 'shizuku.apk');

const TELNET_PORT = 23;
const HTTP_PORT   = 8765;
const PKG         = 'br.com.redesurftank.havalshisuku';
const APK_DEST    = '/data/local/tmp/haval-app.apk';

// ── Detecta gateway e IP local ──────────────────────────────────────────────
function getNetwork() {
    const out = execSync('ipconfig', { encoding: 'buffer' }).toString('latin1');
    // Allow manual override via CAR_IP env var
    if (process.env.CAR_IP) {
        const match = out.match(/IPv4[^\d]+([\d.]+)/);
        const ip = process.env.LOCAL_IP || match?.[1] || '127.0.0.1';
        return { gateway: process.env.CAR_IP, ip };
    }
    try {
        const wifiSection = out.split(/Adaptador/).find(s => s.includes('Wi-Fi') && s.includes('Gateway'));
        if (!wifiSection) throw new Error('Wi-Fi não encontrado');
        const gateway = wifiSection.match(/Gateway[^\d]+([\d.]+)/)?.[1];
        const ip      = wifiSection.match(/IPv4[^\d]+([\d.]+)/)?.[1];
        if (!gateway || !ip) throw new Error('IP/Gateway não encontrado');
        return { gateway, ip };
    } catch (e) {
        console.error('Erro ao detectar rede:', e.message);
        process.exit(1);
    }
}

function log(msg) { process.stdout.write(`[${new Date().toLocaleTimeString('pt-BR')}] ${msg}\n`); }

// ── Download de arquivo via HTTPS (para Shizuku) ─────────────────────────────
function downloadFile(url, dest) {
    return new Promise((resolve, reject) => {
        log(`  Baixando: ${url.substring(0, 80)}...`);
        const file = fs.createWriteStream(dest);
        const request = (u) => {
            https.get(u, { headers: { 'User-Agent': 'haval-installer' } }, res => {
                if (res.statusCode === 301 || res.statusCode === 302) {
                    request(res.headers.location); return;
                }
                if (res.statusCode !== 200) { reject(new Error(`HTTP ${res.statusCode}`)); return; }
                let done = 0;
                const total = parseInt(res.headers['content-length'] || '0');
                res.on('data', c => {
                    done += c.length;
                    if (total) process.stdout.write(`\r  ${(done/1024/1024).toFixed(1)}/${(total/1024/1024).toFixed(1)} MB`);
                });
                res.pipe(file);
                res.on('end', () => { process.stdout.write('\n'); resolve(); });
            }).on('error', reject);
        };
        request(url);
        file.on('error', reject);
    });
}

async function ensureShizuku() {
    if (fs.existsSync(SHIZUKU_PATH) && fs.statSync(SHIZUKU_PATH).size > 1024 * 1024) {
        log('Shizuku já disponível localmente.');
        return;
    }
    log('Baixando Shizuku da última release do GitHub...');
    const apiRes = await new Promise((resolve, reject) => {
        https.get('https://api.github.com/repos/RikkaApps/Shizuku/releases/latest',
            { headers: { 'User-Agent': 'haval-installer' } }, res => {
                let data = '';
                res.on('data', c => data += c);
                res.on('end', () => resolve(JSON.parse(data)));
            }).on('error', reject);
    });
    const asset = apiRes.assets?.find(a => a.name.endsWith('.apk'));
    if (!asset) throw new Error('APK do Shizuku não encontrado na release');
    log(`Shizuku ${apiRes.tag_name} — ${(asset.size / 1024 / 1024).toFixed(1)} MB`);
    await downloadFile(asset.browser_download_url, SHIZUKU_PATH);
    log('Shizuku baixado.');
}

// ── HTTP server local ────────────────────────────────────────────────────────
function startHttpServer(myIp) {
    const files = {
        '/app.apk':          { path: APK_PATH,          type: 'application/octet-stream' },
        '/system_server.js': { path: SYS_JS_PATH,       type: 'application/javascript'   },
        '/grant_nls.js':     { path: GRANT_NLS_PATH,    type: 'application/javascript'   },
        '/fridaserver':      { path: FRIDA_SERVER_PATH,  type: 'application/octet-stream' },
        '/fridainject':      { path: FRIDA_INJECT_PATH,  type: 'application/octet-stream' },
        '/shizuku.apk':      { path: SHIZUKU_PATH,       type: 'application/octet-stream' },
    };
    return new Promise(resolve => {
        const server = http.createServer((req, res) => {
            const f = files[req.url];
            if (!f || !fs.existsSync(f.path)) { res.writeHead(404); res.end(); return; }
            const size = fs.statSync(f.path).size;
            log(`→ ${req.url} (${(size/1024/1024).toFixed(1)} MB)`);
            res.writeHead(200, { 'Content-Type': f.type, 'Content-Length': size });
            const stream = fs.createReadStream(f.path);
            let done = 0;
            stream.on('data', c => {
                done += c.length;
                process.stdout.write(`\r  enviado: ${(done/1024/1024).toFixed(1)}/${(size/1024/1024).toFixed(1)} MB`);
            });
            stream.on('end', () => process.stdout.write('\n'));
            stream.pipe(res);
        });
        server.listen(HTTP_PORT, '0.0.0.0', () => {
            log(`HTTP em http://${myIp}:${HTTP_PORT}/`);
            resolve(server);
        });
    });
}

// ── Telnet ───────────────────────────────────────────────────────────────────
function telnetConnect(gateway) {
    return new Promise((resolve, reject) => {
        log(`Telnet → ${gateway}:${TELNET_PORT}`);
        const socket = net.createConnection({ host: gateway, port: TELNET_PORT });
        const lines  = [];
        socket.on('connect', () => { log('Telnet conectado'); resolve({ socket, lines }); });
        socket.on('data', data => {
            const text = data.toString('latin1').replace(/\xFF[\xFB-\xFF]./g, '').replace(/[\x00-\x08\x0b-\x1f]/g, '');
            text.split('\n').forEach(l => { const t = l.replace(/\r/g,'').trim(); if (t) { lines.push(t); log(`  >> ${t}`); } });
        });
        socket.on('error', reject);
        socket.on('close', () => log('Telnet encerrado'));
    });
}

function cmd(socket, c, delay = 2000) {
    return new Promise(r => { log(`  $ ${c.substring(0, 120)}`); socket.write(c + '\n'); setTimeout(r, delay); });
}

// Aguarda SOMENTE linhas curtas novas (evita falso positivo no eco do comando)
function waitNew(lines, fromIdx, re, ms = 60000) {
    return new Promise((ok, fail) => {
        const deadline = Date.now() + ms;
        const iv = setInterval(() => {
            for (let i = fromIdx; i < lines.length; i++) {
                const l = lines[i].trim();
                if (l.length < 50 && re.test(l)) { clearInterval(iv); ok(l); return; }
            }
            if (Date.now() > deadline) { clearInterval(iv); fail(new Error(`Timeout: ${re}`)); }
        }, 300);
    });
}

// ── Main ──────────────────────────────────────────────────────────────────────
async function main() {
    // Verificações iniciais
    for (const [label, p] of [
        ['APK', APK_PATH], ['system_server.js', SYS_JS_PATH],
        ['fridaserver', FRIDA_SERVER_PATH], ['fridainject', FRIDA_INJECT_PATH]
    ]) {
        if (!fs.existsSync(p)) { console.error(`ERRO: ${label} não encontrado em ${p}`); process.exit(1); }
    }

    log(`APK:         ${(fs.statSync(APK_PATH).size / 1024 / 1024).toFixed(1)} MB`);
    log(`fridaserver: ${(fs.statSync(FRIDA_SERVER_PATH).size / 1024 / 1024).toFixed(1)} MB`);

    // Baixa Shizuku se necessário
    await ensureShizuku();

    const { gateway, ip } = getNetwork();
    log(`Rede detectada — Gateway: ${gateway} | IP local: ${ip}`);

    const server = await startHttpServer(ip);
    let socket;

    try {
        const { socket: s, lines } = await telnetConnect(gateway);
        socket = s;
        await new Promise(r => setTimeout(r, 2000));

        // ── FASE 1: Frida server ───────────────────────────────────────────
        log('\n── FASE 1: Frida server ──────────────────────────────────────────────');

        let idx = lines.length;
        await cmd(socket, 'pgrep -x fridaserver; echo PGREP_DONE', 2000);
        await waitNew(lines, idx, /PGREP_DONE/).catch(() => {});
        const fridaRunning = lines.slice(idx).some(l => /^\d+$/.test(l.trim()));

        if (!fridaRunning) {
            // Verifica se o binário existe (comparação exata via waitNew)
            idx = lines.length;
            await cmd(socket, 'test -f /data/local/tmp/fridaserver && echo FOUND || echo MISSING', 2000);
            const existResult = await waitNew(lines, idx, /^FOUND$|^MISSING$/, 5000).catch(() => 'MISSING');
            const hasBin = existResult === 'FOUND';
            log(`fridaserver no carro: ${hasBin ? 'SIM' : 'NÃO'}`);

            if (!hasBin) {
                log('Enviando fridaserver para o carro...');
                idx = lines.length;
                await cmd(socket, `curl -s -o /data/local/tmp/fridaserver "http://${ip}:${HTTP_PORT}/fridaserver" && echo FS_OK || echo FS_FAIL`, 500);
                const fsRes = await waitNew(lines, idx, /^FS_OK$|^FS_FAIL$/, 120000).catch(() => 'FS_FAIL');
                log(`fridaserver download: ${fsRes}`);
                if (fsRes !== 'FS_OK') { log('ERRO: falha no download do fridaserver'); }

                log('Enviando fridainject para o carro...');
                idx = lines.length;
                await cmd(socket, `curl -s -o /data/local/tmp/fridainject "http://${ip}:${HTTP_PORT}/fridainject" && echo FI_OK || echo FI_FAIL`, 500);
                const fiRes = await waitNew(lines, idx, /^FI_OK$|^FI_FAIL$/, 120000).catch(() => 'FI_FAIL');
                log(`fridainject download: ${fiRes}`);

                await cmd(socket, 'chmod +x /data/local/tmp/fridaserver /data/local/tmp/fridainject', 1500);
            }

            log('Iniciando fridaserver...');
            await cmd(socket, 'setsid /data/local/tmp/fridaserver >/dev/null 2>&1 </dev/null &', 4000);
            idx = lines.length;
            await cmd(socket, 'pgrep -x fridaserver; echo PGREP2_DONE', 2000);
            await waitNew(lines, idx, /PGREP2_DONE/, 6000).catch(() => {});
            const started = lines.slice(idx).some(l => /^\d+$/.test(l.trim()));
            log(started ? 'fridaserver rodando!' : 'AVISO: fridaserver pode não ter iniciado');
        } else {
            log('fridaserver já rodando.');
        }

        // ── FASE 2: Injeção no system_server ──────────────────────────────
        log('\n── FASE 2: Injeção no system_server ──────────────────────────────────');

        idx = lines.length;
        await cmd(socket, `curl -s -o /data/local/tmp/system_server.js "http://${ip}:${HTTP_PORT}/system_server.js" && echo JS_OK || echo JS_FAIL`, 500);
        const jsRes = await waitNew(lines, idx, /^JS_OK$|^JS_FAIL$/, 30000).catch(() => 'JS_FAIL');
        log(`system_server.js: ${jsRes}`);

        idx = lines.length;
        await cmd(socket, 'echo "SPID=$(pidof system_server)"', 2000);
        const spidRes = await waitNew(lines, idx, /^SPID=\d+$/, 5000).catch(() => '');
        const sPid = spidRes.replace('SPID=', '').trim();
        log(`system_server PID: ${sPid || '(não encontrado)'}`);

        if (sPid) {
            log('Injetando system_server.js...');
            await cmd(socket, `/data/local/tmp/fridainject -p ${sPid} -s /data/local/tmp/system_server.js &`, 1000);
            log('Aguardando injeção estabilizar (6s)...');
            await new Promise(r => setTimeout(r, 6000));
        }

        // ── FASE 3: Shizuku ───────────────────────────────────────────────
        log('\n── FASE 3: Shizuku ───────────────────────────────────────────────────');

        idx = lines.length;
        await cmd(socket, 'pm list packages | grep moe.shizuku; echo SHIZUKU_CHECK', 2000);
        await waitNew(lines, idx, /SHIZUKU_CHECK/, 5000).catch(() => {});
        const shizukuInstalled = lines.slice(idx).some(l => l.startsWith('package:moe.shizuku'));

        if (shizukuInstalled) {
            log('Shizuku já instalado.');
        } else {
            log('Baixando Shizuku para o carro...');
            idx = lines.length;
            await cmd(socket, `curl -s -o /data/local/tmp/shizuku.apk "http://${ip}:${HTTP_PORT}/shizuku.apk" && echo SHIZUKU_DL_OK || echo SHIZUKU_DL_FAIL`, 500);
            const shRes = await waitNew(lines, idx, /^SHIZUKU_DL_OK$|^SHIZUKU_DL_FAIL$/, 120000).catch(() => 'SHIZUKU_DL_FAIL');
            log(`Shizuku download: ${shRes}`);

            if (shRes === 'SHIZUKU_DL_OK') {
                log('Instalando Shizuku...');
                idx = lines.length;
                await cmd(socket, 'pm install /data/local/tmp/shizuku.apk; echo SHIZUKU_INST_DONE', 500);
                await waitNew(lines, idx, /SHIZUKU_INST_DONE/, 60000).catch(() => '');
                const shOk = lines.slice(idx).some(l => /success/i.test(l));
                log(shOk ? '✅ Shizuku instalado!' : '⚠️  Shizuku — verifique manualmente');
                await cmd(socket, 'rm -f /data/local/tmp/shizuku.apk', 1000);
            }
        }

        // Inicia serviço do Shizuku (necessário após instalar ou reiniciar)
        log('Iniciando serviço Shizuku...');
        await cmd(socket, 'am start -n moe.shizuku.privileged.api/moe.shizuku.manager.MainActivity', 1000);
        await new Promise(r => setTimeout(r, 6000));
        const startSh = '/data/user/0/moe.shizuku.privileged.api/start.sh';
        idx = lines.length;
        await cmd(socket, `test -f "${startSh}" && echo SH_EXISTS || echo SH_MISSING`, 2000);
        const shExists = await waitNew(lines, idx, /^SH_EXISTS$|^SH_MISSING$/, 8000).catch(() => 'SH_MISSING');
        if (shExists === 'SH_EXISTS') {
            log('Executando start.sh do Shizuku...');
            idx = lines.length;
            await cmd(socket, `sh "${startSh}"; echo SH_STARTED`, 500);
            await waitNew(lines, idx, /SH_STARTED/, 10000).catch(() => {});
            log('✅ Shizuku service iniciado!');
        } else {
            log('⚠️  start.sh não encontrado — Shizuku pode precisar ser aberto manualmente');
        }

        // ── FASE 4: Haval App ─────────────────────────────────────────────
        log('\n── FASE 4: Haval App ─────────────────────────────────────────────────');

        log('Desinstalando versão anterior (se existir)...');
        idx = lines.length;
        await cmd(socket, `pm uninstall ${PKG} 2>/dev/null; echo UNINST_DONE`, 1000);
        await waitNew(lines, idx, /UNINST_DONE/, 10000).catch(() => {});

        log('Baixando APK do Haval para o carro...');
        idx = lines.length;
        await cmd(socket, `curl -s -o ${APK_DEST} "http://${ip}:${HTTP_PORT}/app.apk" && echo APK_DL_OK || echo APK_DL_FAIL`, 500);
        const dlRes = await waitNew(lines, idx, /^APK_DL_OK$|^APK_DL_FAIL$/, 300000).catch(() => 'APK_DL_FAIL');
        log(`Download APK: ${dlRes}`);
        if (dlRes !== 'APK_DL_OK') { log('ERRO: falha no download do APK'); return; }

        // Verifica tamanho
        idx = lines.length;
        await cmd(socket, `ls -la ${APK_DEST}; echo LS_DONE`, 2000);
        await waitNew(lines, idx, /LS_DONE/, 5000).catch(() => {});

        log('Instalando Haval App...');
        idx = lines.length;
        await cmd(socket, `pm install ${APK_DEST}; echo INSTALL_DONE`, 500);
        const instDone = await waitNew(lines, idx, /INSTALL_DONE/, 120000).catch(() => '');
        const instOk = lines.slice(idx).some(l => /success/i.test(l));
        const instFail = lines.slice(idx).find(l => /failure|error/i.test(l));

        if (instOk) {
            log('✅ Haval App instalado com sucesso!');
        } else {
            log(`❌ Falha: ${instFail || 'resultado desconhecido'}`);
            lines.slice(idx).forEach(l => log(`   ${l}`));
        }

        // Ativa NotificationListenerService (necessário para Spotify/BT)
        log('Ativando NotificationListenerService...');
        const NL_COMP = `${PKG}/br.com.redesurftank.havalshisuku.services.MediaNotificationListenerService`;
        const BEANTECHS_NL = 'com.beantechs.operatorcenterservice/com.beantechs.operatorcenterservicesdk.BeanNotificationListener';
        idx = lines.length;
        await cmd(socket, `settings get secure enabled_notification_listeners; echo NL_GET_DONE`, 2000);
        await waitNew(lines, idx, /NL_GET_DONE/, 5000).catch(() => {});
        const existingNl = lines.slice(idx).find(l => l.startsWith('com.') || l.startsWith('br.') || l === 'null');
        const alreadyHas = existingNl && existingNl.includes(PKG);
        if (!alreadyHas) {
            const base = (existingNl && existingNl !== 'null') ? existingNl : BEANTECHS_NL;
            const newNl = base.includes(PKG) ? base : `${base}:${NL_COMP}`;
            idx = lines.length;
            await cmd(socket, `settings put secure enabled_notification_listeners "${newNl}"; echo NL_SET_DONE`, 2000);
            await waitNew(lines, idx, /NL_SET_DONE/, 5000).catch(() => {});
            log('NLS settings put OK');
        } else {
            log('NLS já estava na lista de settings.');
        }

        // Método 2: cmd notification allow_listener (Android 8+)
        idx = lines.length;
        await cmd(socket, `cmd notification allow_listener ${NL_COMP}; echo NL_CMD_DONE`, 3000);
        await waitNew(lines, idx, /NL_CMD_DONE/, 8000).catch(() => {});
        log('cmd notification allow_listener executado.');

        // Inicia o app para que o NLS possa vincular
        log('Iniciando o app Haval...');
        await cmd(socket, `am start -n ${PKG}/.SplashActivity; echo APP_STARTED`, 2000);

        // Aguarda NLS conectar (verifica logcat)
        log('Aguardando NLS vincular (até 10s)...');
        await new Promise(r => setTimeout(r, 8000));
        idx = lines.length;
        await cmd(socket, `logcat -d -s MediaNLS | tail -10; echo NLS_LOG_DONE`, 3000);
        await waitNew(lines, idx, /NLS_LOG_DONE/, 8000).catch(() => {});
        const nlsConnected = lines.slice(idx).some(l => l.includes('onListenerConnected'));
        const nlsAny = lines.slice(idx).filter(l => l.includes('MediaNLS')).join(' | ');
        if (nlsConnected) {
            log('✅ NotificationListenerService CONECTADO!');
        } else if (nlsAny) {
            log(`NLS log: ${nlsAny}`);
        } else {
            log('NLS: sem log ainda — pode conectar quando o app abrir no carro');
        }

        // Confirmação final
        idx = lines.length;
        await cmd(socket, `pm list packages | grep redesurftank; echo PKG_DONE`, 2000);
        await waitNew(lines, idx, /PKG_DONE/, 10000).catch(() => {});
        const pkgLine = lines.slice(idx).find(l => l.includes('package:br.com.redesurftank'));
        log(pkgLine ? `✅ Confirmado: ${pkgLine}` : '❌ Pacote não encontrado');

        await cmd(socket, `rm -f ${APK_DEST} /data/local/tmp/system_server.js`, 1000);

    } catch (err) {
        console.error('\nErro:', err.message);
    } finally {
        if (socket) socket.destroy();
        server.close();
        log('Pronto.');
    }
}

main();

'use strict';

const API = '/api';
const HLS_PORT = 8082;            // nginx-rtmp's HTTP port, see docker-compose.yml
const POLL_MS = 2000;
const MANIFEST_WAIT_MS = 60000;   // first segment appears a few seconds after FFmpeg connects

const el = (id) => document.getElementById(id);
const ui = {
    device: el('device'), deviceHint: el('deviceHint'), destination: el('destination'),
    startBtn: el('startBtn'), stopBtn: el('stopBtn'), loadBtn: el('loadBtn'), clearLogBtn: el('clearLogBtn'),
    state: el('state'), streamId: el('streamId'), startedAt: el('startedAt'),
    retryCount: el('retryCount'), lastError: el('lastError'),
    video: el('video'), hlsUrl: el('hlsUrl'), log: el('log'),
};

let streamId = null;
let pollTimer = null;
let hls = null;
let playerLoading = false;

document.addEventListener('DOMContentLoaded', () => {
    ui.startBtn.addEventListener('click', startStream);
    ui.stopBtn.addEventListener('click', stopStream);
    ui.loadBtn.addEventListener('click', () => loadPlayer(ui.hlsUrl.value.trim()));
    ui.clearLogBtn.addEventListener('click', () => (ui.log.innerHTML = ''));
    ui.destination.addEventListener('input', () => (ui.hlsUrl.value = hlsUrlFor(ui.destination.value)));
    loadCaptureInfo();
});

async function loadCaptureInfo() {
    try {
        const info = await api('GET', '/capture');
        ui.device.value = info.defaultDevice;
        ui.device.placeholder = info.defaultDevice;
        ui.deviceHint.textContent = `${info.backend}: ${info.deviceHint}. Enter "test" for a synthetic source.`;
        ui.destination.value = info.defaultDestination;
        ui.hlsUrl.value = hlsUrlFor(info.defaultDestination);
        log(`Capture backend: ${info.backend}`);
    } catch (e) {
        log(`Could not read capture info: ${e.message}`, 'error');
    }
}

async function startStream() {
    const device = ui.device.value.trim();
    const destination = ui.destination.value.trim();
    if (!device || !destination) {
        log('Device and destination are required.', 'error');
        return;
    }
    try {
        const s = await api('POST', '/streams', { device, destination });
        streamId = s.id;
        render(s);
        setRunning(true);
        ui.hlsUrl.value = hlsUrlFor(destination);
        log(`Stream ${s.id} created.`);
        pollTimer = setInterval(pollStatus, POLL_MS);
        pollStatus();
    } catch (e) {
        log(`Start failed: ${e.message}`, 'error');
    }
}

async function stopStream() {
    if (!streamId) return;
    try {
        const s = await api('DELETE', `/streams/${streamId}`);
        render(s);
        log(`Stream ${s.id} ${s.state.toLowerCase()}.`);
    } catch (e) {
        log(`Stop failed: ${e.message}`, 'error');
    } finally {
        finish();
    }
}

async function pollStatus() {
    if (!streamId) return;
    try {
        const s = await api('GET', `/streams/${streamId}`);
        render(s);
        if (s.state === 'RUNNING' && !hls && !playerLoading) {
            loadPlayer(ui.hlsUrl.value.trim());
        }
        if (s.state === 'STOPPED' || s.state === 'FAILED') {
            log(`Stream ${s.state.toLowerCase()}${s.lastError ? ': ' + s.lastError : ''}`,
                s.state === 'FAILED' ? 'error' : 'info');
            finish();
        }
    } catch (e) {
        log(`Status check failed: ${e.message}`, 'warning');
    }
}

function finish() {
    clearInterval(pollTimer);
    pollTimer = null;
    streamId = null;
    setRunning(false);
    destroyPlayer();
}

/** Waits for nginx to publish the playlist, then attaches hls.js. */
async function loadPlayer(url) {
    if (!url) {
        log('No HLS URL.', 'error');
        return;
    }
    playerLoading = true;
    destroyPlayer();
    log(`Waiting for ${url} …`);
    const ready = await waitForManifest(url, MANIFEST_WAIT_MS);
    playerLoading = false;
    if (!ready) {
        log('Playlist did not appear in time. Is the stream running and nginx-rtmp up?', 'error');
        return;
    }
    if (Hls.isSupported()) {
        // sit two segments behind the live edge instead of hls.js's default three
        hls = new Hls({ liveSyncDurationCount: 2, liveMaxLatencyDurationCount: 6 });
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
            log('Playlist loaded, playing.', 'success');
            ui.video.play().catch((e) => log(`Autoplay blocked: ${e.message}`, 'warning'));
        });
        hls.on(Hls.Events.ERROR, (_, data) => {
            if (data.fatal) log(`Player error: ${data.details}`, 'error');
        });
        hls.loadSource(url);
        hls.attachMedia(ui.video);
    } else if (ui.video.canPlayType('application/vnd.apple.mpegurl')) {
        ui.video.src = url;
        ui.video.play().catch(() => {});
        log('Using native HLS playback.');
    } else {
        log('This browser cannot play HLS.', 'error');
    }
}

async function waitForManifest(url, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        try {
            const r = await fetch(url, { method: 'HEAD', cache: 'no-store' });
            if (r.ok) return true;
        } catch (_) {
            // nginx not reachable yet
        }
        await sleep(1000);
    }
    return false;
}

function destroyPlayer() {
    if (hls) {
        hls.destroy();
        hls = null;
    }
    ui.video.removeAttribute('src');
    ui.video.load();
}

/** rtmp://host:1935/hls/<name> is served by nginx as http://<this host>:8082/hls/<name>/index.m3u8 */
function hlsUrlFor(rtmpUrl) {
    const name = rtmpUrl.trim().split('/').filter(Boolean).pop();
    return name ? `${location.protocol}//${location.hostname}:${HLS_PORT}/hls/${name}/index.m3u8` : '';
}

function render(s) {
    ui.state.textContent = s.state;
    ui.streamId.textContent = s.id;
    ui.startedAt.textContent = s.startedAt ? new Date(s.startedAt).toLocaleTimeString() : '—';
    ui.retryCount.textContent = s.retryCount;
    ui.lastError.textContent = s.lastError || '—';
}

function setRunning(running) {
    ui.startBtn.disabled = running;
    ui.stopBtn.disabled = !running;
    ui.device.disabled = running;
    ui.destination.disabled = running;
}

async function api(method, path, body) {
    const r = await fetch(API + path, {
        method,
        headers: body ? { 'Content-Type': 'application/json' } : {},
        body: body ? JSON.stringify(body) : undefined,
    });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(data.detail || data.title || `${r.status} ${r.statusText}`);
    return data;
}

function log(message, type = 'info') {
    const entry = document.createElement('div');
    entry.className = `log-entry ${type}`;
    entry.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
    ui.log.appendChild(entry);
    ui.log.scrollTop = ui.log.scrollHeight;
    while (ui.log.children.length > 200) ui.log.removeChild(ui.log.firstChild);
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

window.addEventListener('beforeunload', () => {
    clearInterval(pollTimer);
    destroyPlayer();
});

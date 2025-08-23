// Global variables
let player;
let currentStreamId = null;
let statusPollingInterval = null;
let isStreamActive = false;

// API Configuration
const API_BASE_URL = '/api/stream';

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
    initializePlayer();
    setupEventListeners();
    log('System initialized. Ready to start streaming.', 'info');
});

// Initialize simple native video player with HLS support
function initializePlayer() {
    const videoElement = document.getElementById('videoPlayer');
    
    // Use simple native video element instead of Video.js
    player = {
        media: videoElement,
        play: () => videoElement.play(),
        pause: () => videoElement.pause(),
        paused: () => videoElement.paused,
        hls: null
    };

    // Add HLS support
    if (Hls.isSupported()) {
        log('HLS.js is supported. Player initialized successfully.', 'success');
    } else if (videoElement.canPlayType('application/vnd.apple.mpegurl')) {
        // Native HLS support (Safari)
        log('Native HLS support detected. Player initialized successfully.', 'success');
    } else {
        log('HLS is not supported in this browser.', 'error');
    }

    // Player event listeners
    videoElement.addEventListener('error', function() {
        const error = videoElement.error;
        log(`Player error: ${error ? error.message : 'Unknown error'}`, 'error');
    });

    videoElement.addEventListener('loadstart', function() {
        log('Stream loading started...', 'info');
    });

    videoElement.addEventListener('loadeddata', function() {
        log('Stream data loaded successfully.', 'success');
    });

    videoElement.addEventListener('waiting', function() {
        log('Stream buffering...', 'warning');
    });

    videoElement.addEventListener('playing', function() {
        log('Stream is now playing.', 'success');
    });

    videoElement.addEventListener('pause', function() {
        log('Stream paused.', 'info');
    });

    videoElement.addEventListener('ended', function() {
        log('Stream ended.', 'info');
    });
}

// Setup event listeners for UI controls
function setupEventListeners() {
    // Stream management buttons
    document.getElementById('startStreamBtn').addEventListener('click', startStream);
    document.getElementById('stopStreamBtn').addEventListener('click', stopStream);
    document.getElementById('refreshStatusBtn').addEventListener('click', refreshStreamStatus);

    // Player controls
    document.getElementById('loadStreamBtn').addEventListener('click', loadStream);
    document.getElementById('playBtn').addEventListener('click', playStream);
    document.getElementById('pauseBtn').addEventListener('click', pauseStream);
    document.getElementById('reloadBtn').addEventListener('click', reloadStream);

    // Utility buttons
    document.getElementById('clearLogsBtn').addEventListener('click', clearLogs);

    // Auto-update playback URL when RTMP destination changes
    document.getElementById('rtmpDestination').addEventListener('input', updatePlaybackUrl);
}

// Start a new stream
async function startStream() {
    const webcamInput = document.getElementById('webcamInput').value.trim();
    const rtmpDestination = document.getElementById('rtmpDestination').value.trim();

    if (!webcamInput || !rtmpDestination) {
        log('Please fill in all required fields.', 'error');
        return;
    }

    if (!isValidRTMPUrl(rtmpDestination)) {
        log('Invalid RTMP destination URL format.', 'error');
        return;
    }

    showLoading(true);
    log('Starting stream...', 'info');

    try {
        const response = await fetch(`${API_BASE_URL}/create`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                inputStream: webcamInput,
                destination: rtmpDestination
            })
        });

        const data = await response.json();

        if (response.ok && data.success) {
            currentStreamId = data.streamId;
            isStreamActive = true;
            
            log(`Stream started successfully! Stream ID: ${currentStreamId}`, 'success');
            updateUIForActiveStream();
            startStatusPolling();
            
            // Auto-load the stream after a short delay
            setTimeout(() => {
                loadStream();
            }, 3000);
        } else {
            log(`Failed to start stream: ${data.message}`, 'error');
        }
    } catch (error) {
        log(`Error starting stream: ${error.message}`, 'error');
    } finally {
        showLoading(false);
    }
}

// Stop the current stream
async function stopStream() {
    if (!currentStreamId) {
        log('No active stream to stop.', 'warning');
        return;
    }

    showLoading(true);
    log('Stopping stream...', 'info');

    try {
        const response = await fetch(`${API_BASE_URL}/${currentStreamId}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            log('Stream stopped successfully.', 'success');
            isStreamActive = false;
            currentStreamId = null;
            updateUIForInactiveStream();
            stopStatusPolling();
            
            // Stop the player
            if (player && !player.paused()) {
                player.pause();
            }
        } else {
            log('Failed to stop stream.', 'error');
        }
    } catch (error) {
        log(`Error stopping stream: ${error.message}`, 'error');
    } finally {
        showLoading(false);
    }
}

// Load HLS stream into player
function loadStream() {
    const playbackUrl = document.getElementById('playbackUrl').value.trim();
    
    if (!playbackUrl) {
        log('Please enter a valid HLS playback URL.', 'error');
        return;
    }

    if (!isValidHLSUrl(playbackUrl)) {
        log('Invalid HLS URL format. Expected .m3u8 file.', 'error');
        return;
    }

    log(`Preparing to load stream: ${playbackUrl}`, 'info');
    log('Waiting 5 seconds before loading the stream...', 'info');
    
    // Add 5-second delay before loading the stream
    setTimeout(() => {
        log(`Now loading stream: ${playbackUrl}`, 'info');

    // First, test if the URL is accessible
    testHlsUrl(playbackUrl).then(isAccessible => {
        if (!isAccessible) {
            log('HLS URL is not accessible. Please check if the stream is running.', 'error');
            return;
        }

        try {
            if (Hls.isSupported()) {
                // Clean up previous HLS instance
                if (player.hls) {
                    player.hls.destroy();
                    player.hls = null;
                }

                // Use the simple, working approach from simple-hls-debug
                const hls = new Hls({
                    debug: true,
                    enableWorker: false
                });

                log('HLS instance created', 'success');

                // Add basic events (same as working debug page)
                hls.on(Hls.Events.MANIFEST_LOADING, () => {
                    log('MANIFEST_LOADING', 'success');
                });

                hls.on(Hls.Events.MANIFEST_LOADED, (event, data) => {
                    log('MANIFEST_LOADED', 'success');
                    log(`Levels: ${data.levels.length}`, 'info');
                });

                hls.on(Hls.Events.MANIFEST_PARSED, () => {
                    log('MANIFEST_PARSED', 'success');
                    log('Attempting to play...', 'info');
                    player.play().then(() => {
                        log('Play started successfully', 'success');
                    }).catch(e => {
                        log(`Play failed: ${e.message}`, 'error');
                    });
                });

                hls.on(Hls.Events.FRAG_LOADED, (event, data) => {
                    log(`Fragment loaded: ${data.frag.url}`, 'success');
                });

                hls.on(Hls.Events.BUFFER_APPENDED, (event, data) => {
                    log(`Buffer appended: ${data.type}`, 'success');
                });

                hls.on(Hls.Events.ERROR, (event, data) => {
                    log(`ERROR: ${data.details}`, 'error');
                    log(`Type: ${data.type}, Fatal: ${data.fatal}`, 'error');
                    if (data.url) log(`URL: ${data.url}`, 'error');
                    if (data.reason) log(`Reason: ${data.reason}`, 'error');
                });

                // Use the exact same sequence as the working debug page
                log('Loading source...', 'info');
                hls.loadSource(playbackUrl);
                log('Source loaded', 'success');

                log('Attaching media...', 'info');
                hls.attachMedia(player.media);
                log('Media attached', 'success');

                log('Starting load...', 'info');
                hls.startLoad();
                log('Load started', 'success');

                // Store hls instance for cleanup
                player.hls = hls;
            } else {
                // Use native HLS support
                player.src({
                    src: playbackUrl,
                    type: 'application/x-mpegURL'
                });
                log('Using native HLS support', 'info');
            }
        } catch (error) {
            log(`Error loading stream: ${error.message}`, 'error');
        }
    });
    }, 5000); // 5-second delay
}

// Test if HLS URL is accessible
async function testHlsUrl(url) {
    try {
        log(`Testing HLS URL accessibility: ${url}`, 'info');
        const response = await fetch(url, { 
            method: 'HEAD',
            mode: 'cors'
        });
        
        if (response.ok) {
            log('HLS URL is accessible', 'success');
            return true;
        } else {
            log(`HLS URL returned status: ${response.status}`, 'error');
            return false;
        }
    } catch (error) {
        log(`HLS URL test failed: ${error.message}`, 'error');
        return false;
    }
}

// Play the stream
function playStream() {
    if (player) {
        player.play();
        log('Play command sent to player.', 'info');
    }
}

// Pause the stream
function pauseStream() {
    if (player) {
        player.pause();
        log('Pause command sent to player.', 'info');
    }
}

// Reload the current stream
function reloadStream() {
    const playbackUrl = document.getElementById('playbackUrl').value.trim();
    if (playbackUrl) {
        log('Reloading stream...', 'info');
        loadStream();
    } else {
        log('No stream URL to reload.', 'warning');
    }
}

// Refresh stream status
async function refreshStreamStatus() {
    if (!currentStreamId) {
        log('No active stream to check status.', 'warning');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/${currentStreamId}/status`);
        
        if (response.ok) {
            const status = await response.json();
            updateStatusDisplay(status);
            log('Stream status refreshed.', 'info');
        } else {
            log('Failed to get stream status.', 'error');
        }
    } catch (error) {
        log(`Error refreshing status: ${error.message}`, 'error');
    }
}

// Start polling for stream status
function startStatusPolling() {
    if (statusPollingInterval) {
        clearInterval(statusPollingInterval);
    }
    
    statusPollingInterval = setInterval(() => {
        if (currentStreamId && isStreamActive) {
            refreshStreamStatus();
        }
    }, 5000); // Poll every 5 seconds
}

// Stop status polling
function stopStatusPolling() {
    if (statusPollingInterval) {
        clearInterval(statusPollingInterval);
        statusPollingInterval = null;
    }
}

// Update UI for active stream
function updateUIForActiveStream() {
    document.getElementById('startStreamBtn').disabled = true;
    document.getElementById('stopStreamBtn').disabled = false;
    document.getElementById('webcamInput').disabled = true;
    document.getElementById('rtmpDestination').disabled = true;
}

// Update UI for inactive stream
function updateUIForInactiveStream() {
    document.getElementById('startStreamBtn').disabled = false;
    document.getElementById('stopStreamBtn').disabled = true;
    document.getElementById('webcamInput').disabled = false;
    document.getElementById('rtmpDestination').disabled = false;
    
    // Clear status display
    document.getElementById('streamStatus').textContent = 'Not Started';
    document.getElementById('streamId').textContent = '-';
    document.getElementById('startTime').textContent = '-';
    document.getElementById('retryCount').textContent = '-';
}

// Update status display
function updateStatusDisplay(status) {
    document.getElementById('streamStatus').textContent = status.status || 'Unknown';
    document.getElementById('streamId').textContent = status.streamId || '-';
    document.getElementById('startTime').textContent = status.startTime ? new Date(status.startTime).toLocaleString() : '-';
    document.getElementById('retryCount').textContent = status.retryCount || '0';
}

// Update playback URL based on RTMP destination
function updatePlaybackUrl() {
    const rtmpUrl = document.getElementById('rtmpDestination').value.trim();
    if (rtmpUrl) {
        // Convert RTMP URL to HLS URL
        // Example: rtmp://localhost:1935/hls/test -> http://localhost:8082/hls/test/index.m3u8
        let hlsUrl = rtmpUrl
            .replace('rtmp://', 'http://')
            .replace(':1935/', ':8082/');
        
        // Extract the stream name from the RTMP URL
        const streamName = rtmpUrl.split('/').pop();
        hlsUrl = `http://localhost:8082/hls/${streamName}/index.m3u8`;
        
        document.getElementById('playbackUrl').value = hlsUrl;
        log(`Updated HLS URL: ${hlsUrl}`, 'info');
    }
}

// Validate RTMP URL format
function isValidRTMPUrl(url) {
    const rtmpPattern = /^rtmp:\/\/[a-zA-Z0-9.-]+(:[0-9]+)?(\/[a-zA-Z0-9._/-]*)?$/;
    return rtmpPattern.test(url);
}

// Validate HLS URL format
function isValidHLSUrl(url) {
    return url.includes('.m3u8') && (url.startsWith('http://') || url.startsWith('https://'));
}

// Show/hide loading overlay
function showLoading(show) {
    const overlay = document.getElementById('loadingOverlay');
    overlay.style.display = show ? 'flex' : 'none';
}

// Add log entry
function log(message, type = 'info') {
    const logContainer = document.getElementById('logContainer');
    const timestamp = new Date().toLocaleTimeString();
    const logEntry = document.createElement('div');
    logEntry.className = `log-entry ${type}`;
    logEntry.textContent = `[${timestamp}] ${message}`;
    
    logContainer.appendChild(logEntry);
    logContainer.scrollTop = logContainer.scrollHeight;
    
    // Keep only last 100 log entries
    while (logContainer.children.length > 100) {
        logContainer.removeChild(logContainer.firstChild);
    }
}

// Clear logs
function clearLogs() {
    const logContainer = document.getElementById('logContainer');
    logContainer.innerHTML = '';
    log('Logs cleared.', 'info');
}

// Handle page unload
window.addEventListener('beforeunload', function() {
    if (statusPollingInterval) {
        clearInterval(statusPollingInterval);
    }
    
    if (player && player.hls) {
        player.hls.destroy();
    }
    
    if (player) {
        player.dispose();
    }
});

// Handle visibility change (pause polling when tab is not visible)
document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
        if (statusPollingInterval) {
            clearInterval(statusPollingInterval);
            statusPollingInterval = null;
        }
    } else {
        if (currentStreamId && isStreamActive && !statusPollingInterval) {
            startStatusPolling();
        }
    }
});

// Keyboard shortcuts
document.addEventListener('keydown', function(event) {
    // Ctrl/Cmd + Enter to start stream
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        event.preventDefault();
        if (!isStreamActive) {
            startStream();
        } else {
            stopStream();
        }
    }
    
    // Space to play/pause
    if (event.code === 'Space' && event.target.tagName !== 'INPUT') {
        event.preventDefault();
        if (player) {
            if (player.paused()) {
                player.play();
            } else {
                player.pause();
            }
        }
    }
});

// Export functions for debugging
window.streamPlayer = {
    startStream,
    stopStream,
    loadStream,
    playStream,
    pauseStream,
    refreshStreamStatus,
    log
};

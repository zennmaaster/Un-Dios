package com.castor.core.common.hub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight embedded HTTP server that exposes the Un-Dios agent over the local network.
 *
 * The phone acts as a hub: a laptop or tablet on the same WiFi can open
 * `http://<phone-ip>:8484/` in any browser and interact with the on-device
 * AI agent through a terminal-styled web interface.
 *
 * Routes:
 * - `GET  /`            -- Web terminal UI (HTML page with Catppuccin Mocha theme)
 * - `POST /api/agent`   -- Send `{"input":"..."}` and receive `{"response":"..."}`
 * - `GET  /api/status`  -- Service status including model info and uptime
 * - `GET  /api/health`  -- Simple health check returning `{"healthy":true}`
 *
 * Design choices:
 * - Uses raw [ServerSocket] so that no extra library dependencies are required.
 * - Each connection is handled in its own coroutine on [Dispatchers.IO].
 * - The [orchestratorProvider] lambda decouples this class from the agent module,
 *   avoiding circular Gradle dependencies.
 * - All traffic is local-network only; no internet connectivity is used or required.
 *
 * @param port The TCP port to bind to. Defaults to [DEFAULT_PORT].
 * @param orchestratorProvider Suspend lambda that processes a user input string and
 *        returns a natural-language response. Typically wired to
 *        `AgentOrchestrator.processInput`.
 */
class HubServer(
    private val port: Int = DEFAULT_PORT,
    private val orchestratorProvider: suspend (String) -> String
) {

    companion object {
        /** Default port for the hub server. */
        const val DEFAULT_PORT = 8484

        /** Maximum allowed Content-Length to prevent abuse on local network. */
        private const val MAX_BODY_SIZE = 64 * 1024 // 64 KB

        /** Read timeout per connection in milliseconds. */
        private const val SOCKET_TIMEOUT_MS = 30_000
    }

    // ---------------------------------------------------------------------------------
    // Observable state
    // ---------------------------------------------------------------------------------

    private val _isRunning = MutableStateFlow(false)

    /** Whether the server is currently accepting connections. */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _connectedClients = MutableStateFlow(0)

    /** Number of HTTP requests currently being processed. */
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    // ---------------------------------------------------------------------------------
    // Internal state
    // ---------------------------------------------------------------------------------

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var scope: CoroutineScope? = null
    private val startTimeMs = MutableStateFlow(0L)
    private val activeClients = AtomicInteger(0)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---------------------------------------------------------------------------------
    // Serialization models
    // ---------------------------------------------------------------------------------

    @Serializable
    private data class AgentRequest(val input: String)

    @Serializable
    private data class AgentResponse(val response: String)

    @Serializable
    private data class StatusResponse(
        val status: String,
        val model: String,
        val uptime: Long
    )

    @Serializable
    private data class HealthResponse(val healthy: Boolean)

    // ---------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------

    /**
     * Start accepting HTTP connections on the configured [port].
     *
     * This method is idempotent -- calling it while already running has no effect.
     * The server socket is created on [Dispatchers.IO] and each accepted connection
     * is dispatched to its own coroutine.
     */
    fun start() {
        if (_isRunning.value) return

        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = serverScope
        startTimeMs.value = System.currentTimeMillis()

        acceptJob = serverScope.launch {
            try {
                val socket = ServerSocket(port)
                socket.reuseAddress = true
                serverSocket = socket
                _isRunning.value = true

                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        client.soTimeout = SOCKET_TIMEOUT_MS
                        serverScope.launch { handleClient(client) }
                    } catch (_: SocketException) {
                        // Socket was closed -- normal shutdown path
                        break
                    }
                }
            } catch (e: Exception) {
                // Bind failure or other fatal error
                _isRunning.value = false
            }
        }
    }

    /**
     * Gracefully stop the server and release all resources.
     *
     * In-flight request coroutines are cancelled via the [CoroutineScope].
     */
    fun stop() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
            // Ignore close errors
        }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        scope?.cancel()
        scope = null
        activeClients.set(0)
        _connectedClients.value = 0
    }

    // ---------------------------------------------------------------------------------
    // Connection handler
    // ---------------------------------------------------------------------------------

    /**
     * Handle a single HTTP connection.
     *
     * Parses the request line and headers manually, reads the body for POST requests,
     * routes to the appropriate handler, and writes the HTTP response.
     */
    private suspend fun handleClient(client: Socket) {
        val count = activeClients.incrementAndGet()
        _connectedClients.value = count

        try {
            withContext(Dispatchers.IO) {
                client.use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

                    // --- Parse request line ---
                    val requestLine = reader.readLine() ?: return@withContext
                    val parts = requestLine.split(" ", limit = 3)
                    if (parts.size < 2) {
                        sendResponse(writer, 400, "text/plain", "Bad Request")
                        return@withContext
                    }
                    val method = parts[0].uppercase()
                    val path = parts[1]

                    // --- Parse headers ---
                    val headers = mutableMapOf<String, String>()
                    var headerLine = reader.readLine()
                    while (!headerLine.isNullOrEmpty()) {
                        val colonIdx = headerLine.indexOf(':')
                        if (colonIdx > 0) {
                            val key = headerLine.substring(0, colonIdx).trim().lowercase()
                            val value = headerLine.substring(colonIdx + 1).trim()
                            headers[key] = value
                        }
                        headerLine = reader.readLine()
                    }

                    // --- Read body (POST only) ---
                    val body = if (method == "POST") {
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        if (contentLength > MAX_BODY_SIZE) {
                            sendResponse(writer, 413, "text/plain", "Payload Too Large")
                            return@withContext
                        }
                        if (contentLength > 0) {
                            val buffer = CharArray(contentLength)
                            var totalRead = 0
                            while (totalRead < contentLength) {
                                val read = reader.read(buffer, totalRead, contentLength - totalRead)
                                if (read == -1) break
                                totalRead += read
                            }
                            String(buffer, 0, totalRead)
                        } else {
                            ""
                        }
                    } else {
                        ""
                    }

                    // --- Route ---
                    routeRequest(method, path, body, writer)
                }
            }
        } catch (_: Exception) {
            // Connection error -- client disconnected or timeout
        } finally {
            val remaining = activeClients.decrementAndGet()
            _connectedClients.value = remaining
        }
    }

    // ---------------------------------------------------------------------------------
    // Routing
    // ---------------------------------------------------------------------------------

    /**
     * Route an HTTP request to the appropriate handler based on method and path.
     */
    private suspend fun routeRequest(
        method: String,
        path: String,
        body: String,
        writer: PrintWriter
    ) {
        when {
            method == "GET" && path == "/" -> handleWebTerminal(writer)
            method == "GET" && path == "/api/health" -> handleHealth(writer)
            method == "GET" && path == "/api/status" -> handleStatus(writer)
            method == "POST" && path == "/api/agent" -> handleAgent(body, writer)
            method == "OPTIONS" -> handleCors(writer)
            else -> sendResponse(writer, 404, "application/json", """{"error":"Not Found"}""")
        }
    }

    // ---------------------------------------------------------------------------------
    // Route handlers
    // ---------------------------------------------------------------------------------

    /**
     * `GET /api/health` -- Simple health probe.
     */
    private fun handleHealth(writer: PrintWriter) {
        val response = json.encodeToString(HealthResponse(healthy = true))
        sendJsonResponse(writer, 200, response)
    }

    /**
     * `GET /api/status` -- Returns server status including uptime.
     */
    private fun handleStatus(writer: PrintWriter) {
        val uptimeMs = System.currentTimeMillis() - startTimeMs.value
        val response = json.encodeToString(
            StatusResponse(
                status = "running",
                model = "Qwen2.5-3B-Instruct",
                uptime = uptimeMs
            )
        )
        sendJsonResponse(writer, 200, response)
    }

    /**
     * `POST /api/agent` -- Process user input through the orchestrator.
     *
     * Expects JSON body `{"input": "..."}`.
     * Returns JSON `{"response": "..."}`.
     */
    private suspend fun handleAgent(body: String, writer: PrintWriter) {
        try {
            val request = json.decodeFromString<AgentRequest>(body)
            if (request.input.isBlank()) {
                sendJsonResponse(writer, 400, """{"error":"Input cannot be empty"}""")
                return
            }

            val result = orchestratorProvider(request.input)
            val response = json.encodeToString(AgentResponse(response = result))
            sendJsonResponse(writer, 200, response)
        } catch (e: kotlinx.serialization.SerializationException) {
            sendJsonResponse(writer, 400, """{"error":"Invalid JSON. Expected: {\"input\": \"...\"}"}""")
        } catch (e: Exception) {
            sendJsonResponse(
                writer, 500,
                """{"error":"Internal error: ${e.message?.replace("\"", "'") ?: "unknown"}"}"""
            )
        }
    }

    /**
     * `OPTIONS` -- CORS preflight support for browser-based clients.
     */
    private fun handleCors(writer: PrintWriter) {
        sendResponse(writer, 204, "text/plain", "", extraHeaders = corsHeaders())
    }

    /**
     * `GET /` -- Serves the web terminal interface.
     *
     * A self-contained HTML page styled with the Catppuccin Mocha palette that
     * provides a chat-style terminal UI. Uses a simple polling approach (POST to
     * `/api/agent`) rather than websockets to keep the implementation minimal.
     */
    private fun handleWebTerminal(writer: PrintWriter) {
        sendResponse(writer, 200, "text/html; charset=utf-8", WEB_TERMINAL_HTML)
    }

    // ---------------------------------------------------------------------------------
    // HTTP response helpers
    // ---------------------------------------------------------------------------------

    /**
     * Send a JSON response with CORS headers.
     */
    private fun sendJsonResponse(writer: PrintWriter, statusCode: Int, jsonBody: String) {
        sendResponse(writer, statusCode, "application/json", jsonBody, extraHeaders = corsHeaders())
    }

    /**
     * Write a complete HTTP response to the client socket.
     *
     * @param writer The output stream writer.
     * @param statusCode HTTP status code (e.g. 200, 404).
     * @param contentType The Content-Type header value.
     * @param body The response body string.
     * @param extraHeaders Optional additional headers to include.
     */
    private fun sendResponse(
        writer: PrintWriter,
        statusCode: Int,
        contentType: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val statusText = when (statusCode) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }

        val bodyBytes = body.toByteArray(Charsets.UTF_8)

        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bodyBytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("Server: Un-Dios Hub/1.0\r\n")
        for ((key, value) in extraHeaders) {
            writer.print("$key: $value\r\n")
        }
        writer.print("\r\n")
        writer.print(body)
        writer.flush()
    }

    /**
     * Standard CORS headers for local-network browser access.
     */
    private fun corsHeaders(): Map<String, String> = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers" to "Content-Type"
    )
}

// =====================================================================================
// Web terminal HTML
// =====================================================================================

/**
 * Self-contained HTML page for the Un-Dios web terminal.
 *
 * Styled with the Catppuccin Mocha palette to match the native Android UI.
 * Implements a chat-style interface that POSTs to `/api/agent` and displays
 * responses. No external resources are loaded -- everything is inline.
 */
private val WEB_TERMINAL_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Un-Dios Hub Terminal</title>
<style>
  :root {
    --base:     #1e1e2e;
    --mantle:   #181825;
    --crust:    #11111b;
    --surface0: #313244;
    --surface1: #45475a;
    --surface2: #585b70;
    --text:     #cdd6f4;
    --subtext0: #a6adc8;
    --subtext1: #bac2de;
    --green:    #a6e3a1;
    --red:      #f38ba8;
    --blue:     #89b4fa;
    --mauve:    #cba6f7;
    --peach:    #fab387;
    --yellow:   #f9e2af;
    --teal:     #94e2d5;
    --overlay0: #6c7086;
  }

  * { margin: 0; padding: 0; box-sizing: border-box; }

  body {
    background: var(--base);
    color: var(--text);
    font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'SF Mono',
                 'Consolas', 'Liberation Mono', monospace;
    font-size: 14px;
    height: 100vh;
    display: flex;
    flex-direction: column;
  }

  /* ---------- Header ---------- */
  .header {
    background: var(--mantle);
    padding: 12px 20px;
    border-bottom: 1px solid var(--surface0);
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-shrink: 0;
  }
  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .header h1 {
    font-size: 16px;
    font-weight: 700;
    color: var(--mauve);
  }
  .header .subtitle {
    font-size: 11px;
    color: var(--overlay0);
  }
  .status-badge {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: var(--green);
    background: rgba(166, 227, 161, 0.08);
    border: 1px solid rgba(166, 227, 161, 0.25);
    padding: 4px 10px;
    border-radius: 4px;
  }
  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--green);
    animation: pulse 2s infinite;
  }
  @keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.4; }
  }

  /* ---------- Output area ---------- */
  .output {
    flex: 1;
    overflow-y: auto;
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .output::-webkit-scrollbar { width: 6px; }
  .output::-webkit-scrollbar-track { background: var(--base); }
  .output::-webkit-scrollbar-thumb { background: var(--surface1); border-radius: 3px; }

  .welcome {
    color: var(--overlay0);
    font-size: 12px;
    line-height: 1.6;
    padding: 12px;
    background: var(--mantle);
    border-radius: 6px;
    border-left: 3px solid var(--mauve);
  }
  .welcome strong { color: var(--mauve); }

  .msg {
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-width: 85%;
  }
  .msg.user { align-self: flex-end; }
  .msg.agent { align-self: flex-start; }

  .msg-label {
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  .msg.user .msg-label { color: var(--blue); text-align: right; }
  .msg.agent .msg-label { color: var(--green); }

  .msg-body {
    padding: 10px 14px;
    border-radius: 8px;
    line-height: 1.5;
    font-size: 13px;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .msg.user .msg-body {
    background: rgba(137, 180, 250, 0.1);
    border: 1px solid rgba(137, 180, 250, 0.2);
    color: var(--subtext1);
  }
  .msg.agent .msg-body {
    background: rgba(166, 227, 161, 0.06);
    border: 1px solid rgba(166, 227, 161, 0.15);
    color: var(--subtext1);
  }
  .msg.error .msg-body {
    background: rgba(243, 139, 168, 0.08);
    border: 1px solid rgba(243, 139, 168, 0.25);
    color: var(--red);
  }

  .msg-time {
    font-size: 9px;
    color: var(--overlay0);
  }
  .msg.user .msg-time { text-align: right; }

  .thinking {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    font-size: 12px;
    color: var(--overlay0);
    align-self: flex-start;
  }
  .thinking-dots span {
    display: inline-block;
    width: 6px;
    height: 6px;
    margin: 0 2px;
    background: var(--mauve);
    border-radius: 50%;
    animation: dot-bounce 1.4s ease-in-out infinite;
  }
  .thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
  .thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
  @keyframes dot-bounce {
    0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
    40% { transform: scale(1); opacity: 1; }
  }

  /* ---------- Input area ---------- */
  .input-area {
    background: var(--mantle);
    padding: 12px 20px;
    border-top: 1px solid var(--surface0);
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
  }
  .prompt-symbol {
    color: var(--green);
    font-weight: 700;
    font-size: 15px;
    flex-shrink: 0;
  }
  #input {
    flex: 1;
    background: var(--surface0);
    border: 1px solid var(--surface1);
    border-radius: 6px;
    padding: 10px 14px;
    font-family: inherit;
    font-size: 14px;
    color: var(--text);
    outline: none;
    transition: border-color 0.2s;
  }
  #input:focus { border-color: var(--mauve); }
  #input::placeholder { color: var(--surface2); }
  #input:disabled { opacity: 0.5; cursor: not-allowed; }

  #send-btn {
    background: var(--mauve);
    color: var(--crust);
    border: none;
    border-radius: 6px;
    padding: 10px 18px;
    font-family: inherit;
    font-size: 13px;
    font-weight: 700;
    cursor: pointer;
    transition: opacity 0.2s;
    flex-shrink: 0;
  }
  #send-btn:hover { opacity: 0.85; }
  #send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
</head>
<body>
  <div class="header">
    <div class="header-left">
      <h1>Un-Dios Hub</h1>
      <span class="subtitle">phone-as-hub &middot; local network</span>
    </div>
    <div class="status-badge">
      <div class="status-dot"></div>
      <span>Connected</span>
    </div>
  </div>

  <div class="output" id="output">
    <div class="welcome">
      <strong>Welcome to Un-Dios Hub Terminal</strong><br><br>
      Your phone is running as a local AI hub. All processing happens<br>
      on-device &mdash; nothing leaves your local network.<br><br>
      Type a command below to interact with the agent.
    </div>
  </div>

  <div class="input-area">
    <span class="prompt-symbol">&gt;</span>
    <input type="text" id="input" placeholder="Type a command..."
           autocomplete="off" autofocus />
    <button id="send-btn" onclick="sendMessage()">Send</button>
  </div>

<script>
  const output = document.getElementById('output');
  const input = document.getElementById('input');
  const sendBtn = document.getElementById('send-btn');
  let busy = false;

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !busy) {
      e.preventDefault();
      sendMessage();
    }
  });

  function timeStr() {
    return new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit',second:'2-digit'});
  }

  function addMessage(role, text) {
    const div = document.createElement('div');
    div.className = 'msg ' + role;
    div.innerHTML =
      '<div class="msg-label">' + (role === 'user' ? 'you' : 'un-dios') + '</div>' +
      '<div class="msg-body">' + escapeHtml(text) + '</div>' +
      '<div class="msg-time">' + timeStr() + '</div>';
    output.appendChild(div);
    output.scrollTop = output.scrollHeight;
    return div;
  }

  function showThinking() {
    const div = document.createElement('div');
    div.className = 'thinking';
    div.id = 'thinking';
    div.innerHTML = '<div class="thinking-dots"><span></span><span></span><span></span></div> Processing...';
    output.appendChild(div);
    output.scrollTop = output.scrollHeight;
  }

  function hideThinking() {
    const el = document.getElementById('thinking');
    if (el) el.remove();
  }

  function escapeHtml(text) {
    const d = document.createElement('div');
    d.textContent = text;
    return d.innerHTML;
  }

  async function sendMessage() {
    const text = input.value.trim();
    if (!text || busy) return;

    busy = true;
    input.disabled = true;
    sendBtn.disabled = true;

    addMessage('user', text);
    input.value = '';
    showThinking();

    try {
      const res = await fetch('/api/agent', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({input: text})
      });
      hideThinking();

      if (res.ok) {
        const data = await res.json();
        addMessage('agent', data.response || '(empty response)');
      } else {
        const err = await res.text();
        const div = addMessage('error', 'Error ' + res.status + ': ' + err);
        div.className = 'msg error';
      }
    } catch (e) {
      hideThinking();
      const div = addMessage('error', 'Connection failed: ' + e.message);
      div.className = 'msg error';
    }

    busy = false;
    input.disabled = false;
    sendBtn.disabled = false;
    input.focus();
  }
</script>
</body>
</html>
""".trimIndent()

package com.zybooks.boardbeats

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import java.lang.Exception
import kotlin.math.roundToInt

class CheckersActivity : AppCompatActivity() {

    // === Replace these constants with your values (you already provided them) ===
    companion object {
        // Your Spotify client id (from Spotify Dashboard)
        private const val CLIENT_ID = "c1537fce51da40b4b1a9e5a623b6b0c0"
        // The redirect URI you registered in Spotify dashboard
        private const val REDIRECT_URI = "com.zybooks.boardbeats://callback"

        // Scopes needed for Web Playback + playlist reading
        private const val SCOPES = "streaming user-read-playback-state user-modify-playback-state playlist-read-private"

        // Playlist ID extracted from your provided URL
        // https://open.spotify.com/playlist/7euQVeIjp3HbdLWtIGIVrK?si=... -> playlist id = 7euQVeIjp3HbdLWtIGIVrK
        private const val PLAYLIST_ID = "7euQVeIjp3HbdLWtIGIVrK"

        private const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
        private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

        private const val PREFS = "spotify_prefs"
    }

    // UI
    private lateinit var player1Losses: TextView
    private lateinit var player2Losses: TextView
    private lateinit var checkersView: CheckersView

    // music UI elements
    private lateinit var textSongName: TextView
    private lateinit var textArtistName: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var musicContainer: LinearLayout
    private lateinit var seekBar: SeekBar

    // WebView and token state
    private var webView: WebView? = null
    private var codeVerifier: String? = null
    private val httpClient = OkHttpClient()
    private var lastDurationMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var currentlyPlaying = false

    // For Checkers
    private var winListenerSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_checkers)

        // Existing UI wiring
        player1Losses = findViewById(R.id.player1Losses)
        player2Losses = findViewById(R.id.player2Losses)
        checkersView = findViewById(R.id.checkersView)

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        checkersView.winListener = object : CheckersView.OnWinListener {
            override fun onPlayerWin(winningPlayer: Int) {
                showWinScreen(winningPlayer)
            }
        }

        // Music UI (present in your activity_checkers.xml - IDs used in your paste)
        textSongName = findViewById(R.id.textSongName)
        textArtistName = findViewById(R.id.textArtistName)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        musicContainer = findViewById(R.id.musicPlayerContainer)

        // Create and insert a SeekBar programmatically (your xml had a ProgressBar).
        // This SeekBar allows user fast-forward/rewind.
        seekBar = SeekBar(this).apply {
            max = 1000 // we'll map to ms using lastDurationMs
            isEnabled = false
        }
        // Add SeekBar below existing songProgressBar (or simply to musicContainer)
        musicContainer.addView(seekBar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        // SeekBar user interaction
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var fromUser = false
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUserFlag: Boolean) {
                fromUser = fromUserFlag
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!fromUser) return
                val pct = seekBar?.progress ?: 0
                if (lastDurationMs > 0) {
                    val targetMs = (pct / 1000.0 * lastDurationMs).roundToInt().toLong()
                    seekTo(targetMs)
                }
            }
        })

        // Button bindings
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnNext.setOnClickListener { nextTrack() }
        btnPrev.setOnClickListener { prevTrack() }

        // Playlist button: open a popup list to choose track from predefined playlist
        // If your layout doesn't have a "choose playlist" button, we will reuse the song title click to open selection:
        textSongName.setOnClickListener { showPlaylistSelector() }
        textArtistName.setOnClickListener { showPlaylistSelector() }

        // If we already have an access token saved, initialize the hidden WebView player.
        val token = getAccessTokenOrRefresh()
        if (token != null) {
            initHiddenWebViewWithToken(token)
        } else {
            // no token: show a small "Login" prompt in the music area
            val loginBtn = Button(this).apply {
                text = "Login to Spotify"
                setOnClickListener { startSpotifyAuth() }
            }
            musicContainer.addView(loginBtn, 0)
        }
    }

    // --------------------------
    // Checkers UI & logic (unchanged)
    // --------------------------
    private fun showWinScreen(player: Int) {
        // Update score
        if (player == 1) {
            val losses = player2Losses.text.toString().substringAfter(": ").toInt() + 1
            player2Losses.text = "Player 2: $losses"
        } else {
            val losses = player1Losses.text.toString().substringAfter(": ").toInt() + 1
            player1Losses.text = "Player 1: $losses"
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Player $player Wins!")
        builder.setMessage("Game Over – Player $player has won.")

        builder.setPositiveButton("Play Again") { _, _ ->
            checkersView.resetGame()
        }

        builder.setNegativeButton("Back") { _, _ ->
            finish() // closes activity and returns to previous screen
        }

        builder.setCancelable(false)
        builder.show()
    }

    // --------------------------
    // Spotify PKCE auth flow
    // --------------------------
    private fun startSpotifyAuth() {
        codeVerifier = generateCodeVerifier()
        val codeChallenge = codeChallengeFromVerifier(codeVerifier!!)
        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", SCOPES)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, authUri)
        startActivity(intent)
    }

    // Catch the redirect when Spotify sends the authorization code back to our redirect URI.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthRedirect(intent)
    }

    override fun onResume() {
        super.onResume()
        // if the Activity got launched fresh with the redirect URI
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!uri.toString().startsWith(REDIRECT_URI)) return
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        if (error != null) {
            Toast.makeText(this, "Spotify auth error: $error", Toast.LENGTH_LONG).show()
            return
        }
        if (code != null) {
            exchangeCodeForToken(code)
        }
    }

    // PKCE helpers
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(64)
        secureRandom.nextBytes(code)
        // base64 url-safe no padding
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallengeFromVerifier(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // Exchange authorization code for access + refresh tokens (in-app)
    private fun exchangeCodeForToken(code: String) {
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .add("code_verifier", codeVerifier ?: "")
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@CheckersActivity, "Token exchange failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        runOnUiThread { Toast.makeText(this@CheckersActivity, "Token exchange failed (${it.code})", Toast.LENGTH_LONG).show() }
                        return
                    }
                    val body = it.body?.string() ?: ""
                    val json = JsonParser.parseString(body).asJsonObject
                    val accessToken = json.get("access_token").asString
                    val refreshToken = json.get("refresh_token")?.asString
                    val expiresIn = json.get("expires_in")?.asLong ?: 3600L
                    saveTokens(accessToken, refreshToken, System.currentTimeMillis() + expiresIn * 1000L)
                    runOnUiThread {
                        Toast.makeText(this@CheckersActivity, "Spotify login successful", Toast.LENGTH_SHORT).show()
                        initHiddenWebViewWithToken(accessToken)
                    }
                }
            }
        })
    }

    // Refresh access token using refresh token (synchronous network call)
    // Note: we run on background thread when calling this
    private fun refreshAccessToken(): String? {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", null) ?: return null

        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", CLIENT_ID)
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JsonParser.parseString(body).asJsonObject
                val accessToken = json.get("access_token").asString
                val expiresIn = json.get("expires_in")?.asLong ?: 3600L
                saveTokens(accessToken, refreshToken, System.currentTimeMillis() + expiresIn * 1000L)
                return accessToken
            }
        } catch (e: Exception) {
            Log.e("Spotify", "refresh failed", e)
            return null
        }
    }

    private fun getAccessTokenOrRefresh(): String? {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        val expiry = prefs.getLong("token_expiry", 0L)
        return if (token == null || System.currentTimeMillis() > expiry - 60_000) {
            // try refresh synchronously (this should be called from background thread if used in tight loops)
            refreshAccessToken()
        } else token
    }

    private fun saveTokens(accessToken: String, refreshToken: String?, expiryTs: Long) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("token_expiry", expiryTs)
            .apply()
    }

    // --------------------------
    // WebView player initialization
    // --------------------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun initHiddenWebViewWithToken(accessToken: String) {
        // If already created, re-init with new token
        if (webView == null) {
            webView = WebView(this)
            webView?.visibility = View.GONE
            // add webview to root layout so it stays alive while activity is alive
            (findViewById<ViewGroup>(android.R.id.content)).addView(webView, 0,
                ViewGroup.LayoutParams(1, 1)) // tiny hidden view

            webView?.settings?.javaScriptEnabled = true
            webView?.settings?.domStorageEnabled = true
            webView?.settings?.mediaPlaybackRequiresUserGesture = false
            webView?.webChromeClient = WebChromeClient()
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // call initPlayer with token (JS function defined in spotify_player.html)
                    val tokenEscaped = accessToken.replace("'", "\\'")
                    view?.evaluateJavascript("initPlayer('$tokenEscaped');", null)
                }
            }

            // JS -> Android bridge
            webView?.addJavascriptInterface(object {
                @JavascriptInterface
                fun postMessage(msg: String) {
                    runOnUiThread { handlePlayerMessage(msg) }
                }
            }, "AndroidBridge")
        }

        // load the player HTML (make sure you placed spotify_player.html into assets/)
        webView?.loadUrl("file:///android_asset/spotify_player.html")
    }

    // Generic helper to call JS functions in the WebView
    private fun callJs(js: String) {
        runOnUiThread {
            webView?.evaluateJavascript(js, null)
        }
    }

    private fun togglePlayPause() {
        callJs("togglePlayPause();")
    }

    private fun nextTrack() {
        callJs("nextTrack();")
    }

    private fun prevTrack() {
        callJs("previousTrack();")
    }

    // Request JS to seek to ms
    private fun seekTo(ms: Long) {
        callJs("seek($ms);")
    }

    // Play a track by spotify uri (e.g., "spotify:track:TRACK_ID")
    private fun playTrackUri(uri: String) {
        // Ensure token fresh and then call playTrack
        Thread {
            val token = getAccessTokenOrRefresh()
            if (token == null) {
                runOnUiThread { Toast.makeText(this, "Not authenticated with Spotify", Toast.LENGTH_SHORT).show() }
                return@Thread
            }
            // Re-init player with fresh token (safe)
            runOnUiThread {
                webView?.evaluateJavascript("initPlayer('${token.replace("'", "\\'")}');", null)
                // then call playTrack
                callJs("playTrack('$uri');")
            }
        }.start()
    }

    // Handle messages sent by spotify_player.html via AndroidBridge.postMessage(JSON.stringify(...))
    private fun handlePlayerMessage(jsonStr: String) {
        try {
            val jo = JsonParser.parseString(jsonStr).asJsonObject
            val type = jo.get("type")?.asString ?: return
            when (type) {
                "ready" -> {
                    // device ready
                    Log.d("Spotify", "Player ready; deviceId=${jo.get("deviceId")?.asString}")
                }
                "state" -> {
                    val playing = jo.get("playing")?.asBoolean ?: false
                    val position = jo.get("position")?.asLong ?: 0L
                    val duration = jo.get("duration")?.asLong ?: 0L
                    lastDurationMs = duration
                    lastPositionMs = position
                    currentlyPlaying = playing

                    val track = jo.getAsJsonObject("track")
                    val name = track?.get("name")?.asString ?: ""
                    val artists = track?.get("artists")?.asString ?: ""

                    textSongName.text = if (name.isNotEmpty()) name else "Song Title"
                    textArtistName.text = if (artists.isNotEmpty()) artists else "Artist Name"

                    // update seekbar progress
                    if (duration > 0) {
                        val pct = (position.toDouble() / duration.toDouble() * 1000.0).toInt().coerceIn(0, 1000)
                        seekBar.progress = pct
                        seekBar.isEnabled = true
                    } else {
                        seekBar.progress = 0
                        seekBar.isEnabled = false
                    }

                    // update play/pause icon
                    btnPlayPause.setImageResource(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                }
                "playStarted" -> {
                    // Track start acknowledged
                }
                "auth_error" -> {
                    runOnUiThread { Toast.makeText(this, "Spotify auth error from JS: ${jo.get("message")}", Toast.LENGTH_LONG).show() }
                }
                else -> {
                    Log.d("Spotify", "Unhandled message: $jsonStr")
                }
            }
        } catch (e: Exception) {
            Log.e("Spotify", "Error parsing player message", e)
        }
    }

    // --------------------------
    // Playlist fetch + selector (simple popup)
    // --------------------------
    private fun showPlaylistSelector() {
        val token = getAccessTokenOrRefresh()
        if (token == null) {
            Toast.makeText(this, "Please log in to Spotify first", Toast.LENGTH_SHORT).show()
            return
        }

        // fetch playlist tracks
        fetchPlaylistTracks(token, PLAYLIST_ID) { tracks ->
            if (tracks.isEmpty()) {
                runOnUiThread { Toast.makeText(this, "No tracks found in playlist", Toast.LENGTH_SHORT).show() }
                return@fetchPlaylistTracks
            }
            runOnUiThread {
                val titles = tracks.map { "${it.name} — ${it.artists}" }.toTypedArray()
                val builder = AlertDialog.Builder(this@CheckersActivity)
                builder.setTitle("Choose a song")
                builder.setItems(titles) { _, which ->
                    val t = tracks[which]
                    playTrackUri(t.uri)
                }
                builder.setNegativeButton("Cancel", null)
                builder.show()
            }
        }
    }

    private fun fetchPlaylistTracks(token: String, playlistId: String, callback: (List<Track>) -> Unit) {
        val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks?fields=items(track(name,uri,artists(name)))&limit=100"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Spotify", "Playlist fetch failed", e)
                callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e("Spotify", "Playlist fetch failed: ${it.code}")
                        callback(emptyList())
                        return
                    }
                    val body = it.body?.string() ?: ""
                    try {
                        val root = JsonParser.parseString(body).asJsonObject
                        val items = root.getAsJsonArray("items")
                        val list = mutableListOf<Track>()
                        items.forEach { elem ->
                            val track = elem.asJsonObject.getAsJsonObject("track")
                            val name = track.get("name").asString
                            val uri = track.get("uri").asString
                            val artistsArray = track.getAsJsonArray("artists")
                            val artists = artistsArray.joinToString(", ") { a -> a.asJsonObject.get("name").asString }
                            list.add(Track(name, artists, uri))
                        }
                        callback(list)
                    } catch (e: Exception) {
                        Log.e("Spotify", "Parse playlist JSON failed", e)
                        callback(emptyList())
                    }
                }
            }
        })
    }

    // --------------------------
    // Utilities & data classes
    // --------------------------
    private data class Track(val name: String, val artists: String, val uri: String)

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}

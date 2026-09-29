package com.alananasss.kittytune.data.yandex

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Yandex Music, written against the public (unofficial) api.music.yandex.net API — the same one
 * the open-source yandex-music-api clients use.
 *
 * Tracks coming from here have `source = "yandex"`, a share link
 * `https://music.yandex.ru/album/<albumId>/track/<trackId>` in `permalinkUrl` and an id offset by
 * [ID_OFFSET] so they can never collide with SoundCloud ids stored in the same tables. The real
 * Yandex id is always recovered from the share link, so tracks survive being saved to history,
 * likes, playlists and downloads.
 */

data class YandexAccount(val uid: Long, val login: String, val displayName: String, val hasPlus: Boolean)

data class YandexPlaylistInfo(
    val ownerUid: Long,
    val kind: Long,
    val title: String,
    val trackCount: Int,
    val coverUrl: String?
)

data class YandexDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int
)

class YandexAuth(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("yandex_music", Context.MODE_PRIVATE)

    var token: String
        get() = prefs.getString("oauth_token", "") ?: ""
        set(value) = prefs.edit().putString("oauth_token", value).apply()

    var uid: Long
        get() = prefs.getLong("uid", 0L)
        set(value) = prefs.edit().putLong("uid", value).apply()

    var displayName: String
        get() = prefs.getString("display_name", "") ?: ""
        set(value) = prefs.edit().putString("display_name", value).apply()

    var login: String
        get() = prefs.getString("login", "") ?: ""
        set(value) = prefs.edit().putString("login", value).apply()

    var hasPlus: Boolean
        get() = prefs.getBoolean("has_plus", false)
        set(value) = prefs.edit().putBoolean("has_plus", value).apply()

    fun isLoggedIn(): Boolean = token.isNotBlank() && uid != 0L

    fun logout() = prefs.edit().clear().apply()
}

class YandexApiException(message: String, val code: Int = 0) : Exception(message)

object YandexMusic {
    private const val TAG = "YandexMusic"

    const val SOURCE = "yandex"
    const val ID_OFFSET = 7_000_000_000_000_000_000L
    const val LIKES_PLAYLIST_ID = "ya_likes"
    const val WAVE_PLAYLIST_ID = "ya_wave"
    const val PLAYLIST_PREFIX = "ya_playlist:"

    private const val API = "https://api.music.yandex.net"
    private const val OAUTH = "https://oauth.yandex.ru"

    // Client credentials of the official Yandex Music Android app (public, used by every
    // third-party client for the device-code login).
    private const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
    private const val CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300"
    private const val SIGN_SALT = "XGRlBW9FXlekgbPrRHuSiA"

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val TRACK_LINK = Regex("""music\.yandex\.[a-z]+/album/(\d+)/track/(\d+)""")
    private val TRACK_LINK_NO_ALBUM = Regex("""music\.yandex\.[a-z]+/track/(\d+)""")

    private val streamCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private const val STREAM_CACHE_MS = 10 * 60 * 1000L

    @Volatile private var client: OkHttpClient? = null
    @Volatile private var waveSessionId: String? = null
    @Volatile private var waveBatchId: String? = null

    private fun http(context: Context): OkHttpClient =
        client ?: synchronized(this) {
            client ?: ProxyManager.getOkHttpClient(context).newBuilder()
                .readTimeout(25, TimeUnit.SECONDS)
                .build()
                .also { client = it }
        }

    fun auth(context: Context) = YandexAuth(context)

    // ── Low level ────────────────────────────────────────────────────────────

    private fun Request.Builder.yandexHeaders(context: Context): Request.Builder {
        val token = YandexAuth(context).token
        if (token.isNotBlank()) header("Authorization", "OAuth $token")
        header("X-Yandex-Music-Client", "YandexMusicAndroid/24023621")
        header("User-Agent", "Yandex-Music-API")
        header("Accept-Language", "ru")
        return this
    }

    private fun execute(context: Context, request: Request): String {
        http(context).newCall(request).execute().use { resp ->
            val body = resp.body.string()
            if (!resp.isSuccessful) {
                throw YandexApiException("HTTP ${resp.code}: ${body.take(200)}", resp.code)
            }
            return body
        }
    }

    private fun result(body: String): JsonElement {
        val root = JsonParser.parseString(body)
        if (root.isJsonObject && root.asJsonObject.has("result")) return root.asJsonObject.get("result")
        if (root.isJsonObject && root.asJsonObject.has("error")) {
            throw YandexApiException(root.asJsonObject.get("error").toString())
        }
        return root
    }

    private fun apiGet(context: Context, path: String): JsonElement =
        result(execute(context, Request.Builder().url("$API/$path").yandexHeaders(context).get().build()))

    private fun postForm(context: Context, path: String, form: Map<String, String>): JsonElement {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return result(execute(context, Request.Builder().url("$API/$path").yandexHeaders(context).post(body).build()))
    }

    private fun postJson(context: Context, path: String, json: String): JsonElement =
        result(
            execute(
                context,
                Request.Builder().url("$API/$path").yandexHeaders(context).post(json.toRequestBody(JSON)).build()
            )
        )

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private fun JsonObject.long(key: String): Long =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.toLongOrNull() ?: 0L

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun cover(uri: String?, size: String = "400x400"): String? {
        if (uri.isNullOrBlank()) return null
        val withSize = uri.replace("%%", size)
        return if (withSize.startsWith("http")) withSize else "https://$withSize"
    }

    // ── Login (device code: the user types a short code on ya.ru/device) ────

    suspend fun startDeviceLogin(context: Context): YandexDeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("client_id", CLIENT_ID).build()
        val json = JsonParser.parseString(
            execute(context, Request.Builder().url("$OAUTH/device/code").post(body).build())
        ).asJsonObject
        YandexDeviceCode(
            deviceCode = json.str("device_code") ?: throw YandexApiException("no device_code"),
            userCode = json.str("user_code") ?: throw YandexApiException("no user_code"),
            verificationUrl = json.str("verification_url") ?: "https://ya.ru/device",
            intervalSeconds = json.str("interval")?.toIntOrNull() ?: 5,
            expiresInSeconds = json.str("expires_in")?.toIntOrNull() ?: 300
        )
    }

    /** Polls until the user confirms the code. Returns the account, or null if the code expired. */
    suspend fun awaitDeviceLogin(context: Context, code: YandexDeviceCode): YandexAccount? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(code.intervalSeconds.coerceAtLeast(2) * 1000L)
            val body = FormBody.Builder()
                .add("grant_type", "device_code")
                .add("code", code.deviceCode)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            val raw = http(context).newCall(Request.Builder().url("$OAUTH/token").post(body).build())
                .execute().use { it.body.string() }
            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: continue
            val token = json.str("access_token")
            if (!token.isNullOrBlank()) {
                YandexAuth(context).token = token
                return@withContext refreshAccount(context)
            }
            when (json.str("error")) {
                "authorization_pending", "slow_down", null -> continue
                else -> throw YandexApiException(json.str("error_description") ?: json.str("error") ?: "login failed")
            }
        }
        null
    }

    suspend fun refreshAccount(context: Context): YandexAccount = withContext(Dispatchers.IO) {
        val res = apiGet(context, "account/status").asJsonObject
        val account = res.obj("account") ?: throw YandexApiException("no account")
        val uid = account.long("uid")
        if (uid == 0L) throw YandexApiException("Не удалось определить аккаунт Яндекса")
        val name = account.str("displayName") ?: account.str("fullName") ?: account.str("login") ?: "Яндекс"
        val plus = res.obj("plus")?.get("hasPlus")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        YandexAuth(context).apply {
            this.uid = uid
            this.displayName = name
            this.login = account.str("login") ?: ""
            this.hasPlus = plus
        }
        YandexAccount(uid, account.str("login") ?: "", name, plus)
    }

    fun logout(context: Context) {
        YandexAuth(context).logout()
        waveSessionId = null
        waveBatchId = null
        streamCache.clear()
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    fun isYandex(track: Track?): Boolean =
        track != null && (track.source == SOURCE || track.permalinkUrl?.contains("music.yandex.") == true)

    /** Real Yandex id (and album id when known) of a track, from its share link or offset id. */
    fun yandexIds(track: Track): Pair<String, String?>? {
        val link = track.permalinkUrl ?: track.permalink
        if (link != null) {
            TRACK_LINK.find(link)?.let { return it.groupValues[2] to it.groupValues[1] }
            TRACK_LINK_NO_ALBUM.find(link)?.let { return it.groupValues[1] to null }
        }
        if (track.id > ID_OFFSET) return (track.id - ID_OFFSET).toString() to null
        return null
    }

    fun toTrack(json: JsonObject): Track? {
        val rawId = json.str("id") ?: return null
        val numericId = rawId.toLongOrNull() ?: return null // user-uploaded tracks have non-numeric ids
        if (json.get("available")?.takeIf { it.isJsonPrimitive }?.asBoolean == false) return null
        val album = json.arr("albums").firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        val albumId = album?.str("id")
        val artists = json.arr("artists").mapNotNull { el ->
            val a = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = a.str("name") ?: return@mapNotNull null
            SpotifyArtistRef(
                id = "yandex:artist:${a.str("id") ?: name}",
                name = name,
                avatarUrl = cover(a.obj("cover")?.str("uri"), "200x200")
            )
        }
        val artistNames = artists.joinToString(", ") { it.name }.ifBlank { "Яндекс Музыка" }
        val version = json.str("version")?.takeIf { it.isNotBlank() }
        val title = (json.str("title") ?: "").let { if (version != null) "$it ($version)" else it }
        val coverUrl = cover(json.str("coverUri") ?: album?.str("coverUri"))
        val firstArtistId = json.arr("artists").firstOrNull()?.takeIf { it.isJsonObject }
            ?.asJsonObject?.long("id") ?: 0L
        val durationMs = json.long("durationMs")
        return Track(
            id = ID_OFFSET + numericId,
            title = title,
            artworkUrl = coverUrl,
            durationMs = durationMs,
            user = User(
                id = firstArtistId,
                username = artistNames,
                avatarUrl = artists.firstOrNull()?.avatarUrl
            ),
            permalinkUrl = if (albumId != null) {
                "https://music.yandex.ru/album/$albumId/track/$rawId"
            } else {
                "https://music.yandex.ru/track/$rawId"
            },
            genre = album?.str("genre"),
            fullDuration = durationMs,
            source = SOURCE,
            artists = artists.ifEmpty { null }
        )
    }

    private fun tracksFrom(array: JsonArray): List<Track> = array.mapNotNull { el ->
        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        // Playlists / wave wrap the track: {"id":..,"track":{...}}
        val inner = o.obj("track") ?: o
        toTrack(inner)
    }

    // ── Library ──────────────────────────────────────────────────────────────

    /** Full tracks for a list of "id" or "id:albumId" strings, fetched in chunks. */
    private fun fetchTracks(context: Context, ids: List<String>): List<Track> {
        val out = ArrayList<Track>(ids.size)
        for (chunk in ids.chunked(200)) {
            val res = postForm(context, "tracks", mapOf("track-ids" to chunk.joinToString(","), "with-positions" to "false"))
            if (res.isJsonArray) out += tracksFrom(res.asJsonArray)
        }
        return out
    }

    suspend fun likedTracks(context: Context, limit: Int = 1000): List<Track> = withContext(Dispatchers.IO) {
        val uid = YandexAuth(context).uid
        val res = apiGet(context, "users/$uid/likes/tracks").asJsonObject
        val items = res.obj("library")?.arr("tracks") ?: JsonArray()
        val ids = items.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val album = o.str("albumId")
            if (album != null) "$id:$album" else id
        }.take(limit)
        fetchTracks(context, ids)
    }

    suspend fun likedTrackIds(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val uid = YandexAuth(context).uid
        val res = apiGet(context, "users/$uid/likes/tracks").asJsonObject
        (res.obj("library")?.arr("tracks") ?: JsonArray()).mapNotNull {
            it.takeIf { e -> e.isJsonObject }?.asJsonObject?.str("id")
        }.toSet()
    }

    suspend fun playlists(context: Context): List<YandexPlaylistInfo> = withContext(Dispatchers.IO) {
        val uid = YandexAuth(context).uid
        val res = apiGet(context, "users/$uid/playlists/list")
        if (!res.isJsonArray) return@withContext emptyList()
        res.asJsonArray.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val c = o.obj("cover")
            val coverUri = c?.str("uri") ?: c?.arr("itemsUri")?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString
                ?: o.str("ogImage")
            YandexPlaylistInfo(
                ownerUid = o.obj("owner")?.long("uid")?.takeIf { it != 0L } ?: o.long("uid").takeIf { it != 0L } ?: uid,
                kind = o.long("kind"),
                title = o.str("title") ?: "Плейлист",
                trackCount = o.str("trackCount")?.toIntOrNull() ?: 0,
                coverUrl = cover(coverUri)
            )
        }
    }

    suspend fun playlistTracks(context: Context, ownerUid: Long, kind: Long): Pair<YandexPlaylistInfo?, List<Track>> =
        withContext(Dispatchers.IO) {
            val res = apiGet(context, "users/$ownerUid/playlists/$kind?richTracks=true").asJsonObject
            val items = res.arr("tracks")
            var tracks = tracksFrom(items)
            // Some playlists come back without the track bodies; fetch them by id then.
            if (tracks.isEmpty() && items.size() > 0) {
                val ids = items.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val id = o.str("id") ?: return@mapNotNull null
                    val album = o.str("albumId")
                    if (album != null) "$id:$album" else id
                }
                tracks = fetchTracks(context, ids)
            }
            val c = res.obj("cover")
            val info = YandexPlaylistInfo(
                ownerUid = ownerUid,
                kind = kind,
                title = res.str("title") ?: "Плейлист",
                trackCount = res.str("trackCount")?.toIntOrNull() ?: tracks.size,
                coverUrl = cover(c?.str("uri") ?: c?.arr("itemsUri")?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString ?: res.str("ogImage"))
            )
            info to tracks
        }

    suspend fun search(context: Context, query: String, page: Int = 0): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val res = apiGet(context, "search?text=$q&type=track&page=$page&nocorrect=false").asJsonObject
        tracksFrom(res.obj("tracks")?.arr("results") ?: JsonArray())
    }

    suspend fun setLiked(context: Context, track: Track, liked: Boolean): Boolean = withContext(Dispatchers.IO) {
        val auth = YandexAuth(context)
        if (!auth.isLoggedIn()) return@withContext false
        val (id, album) = yandexIds(track) ?: return@withContext false
        val trackRef = if (album != null) "$id:$album" else id
        val action = if (liked) "add-multiple" else "remove"
        runCatching {
            postForm(context, "users/${auth.uid}/likes/tracks/$action", mapOf("track-ids" to trackRef))
            true
        }.getOrElse {
            Log.w(TAG, "like/unlike failed", it)
            false
        }
    }

    // ── My Wave ("Моя волна") ────────────────────────────────────────────────

    /** Starts a fresh wave session and returns its first tracks. */
    suspend fun startWave(context: Context, seed: String = "user:onyourwave"): List<Track> = withContext(Dispatchers.IO) {
        val body = """{"seeds":["$seed"],"queue":[],"includeTracksInResponse":true,"includeWaveModel":false,"interactive":true}"""
        val res = postJson(context, "rotor/session/new", body).asJsonObject
        waveSessionId = res.str("radioSessionId")
        waveBatchId = res.str("batchId")
        tracksFrom(res.arr("sequence"))
    }

    /** Next tracks of the current wave session; [played] are the ids already queued. */
    suspend fun moreWave(context: Context, played: List<Track>): List<Track> = withContext(Dispatchers.IO) {
        val session = waveSessionId ?: return@withContext startWave(context)
        val queue = played.mapNotNull { t ->
            yandexIds(t)?.let { (id, album) -> if (album != null) "\"$id:$album\"" else "\"$id\"" }
        }.takeLast(20)
        val body = """{"queue":[${queue.joinToString(",")}]}"""
        val res = runCatching { postJson(context, "rotor/session/$session/tracks", body).asJsonObject }
            .getOrElse { return@withContext startWave(context) }
        res.str("batchId")?.let { waveBatchId = it }
        tracksFrom(res.arr("sequence"))
    }

    /** A few wave batches glued together, used when the wave is opened like a playlist. */
    suspend fun wavePlaylist(context: Context, size: Int = 30): List<Track> {
        val out = LinkedHashMap<Long, Track>()
        startWave(context).forEach { out[it.id] = it }
        var guard = 0
        while (out.size < size && guard++ < 6) {
            val next = moreWave(context, out.values.toList())
            if (next.isEmpty()) break
            next.forEach { out[it.id] = it }
        }
        return out.values.toList()
    }

    // ── Streaming ────────────────────────────────────────────────────────────

    /** A direct mp3 URL for the track (best bitrate the account is allowed). */
    suspend fun resolveStream(context: Context, track: Track): String? = withContext(Dispatchers.IO) {
        val (id, _) = yandexIds(track) ?: return@withContext null
        streamCache[id]?.let { (url, at) ->
            if (System.currentTimeMillis() - at < STREAM_CACHE_MS) return@withContext url
        }
        try {
            val infos = apiGet(context, "tracks/$id/download-info")
            if (!infos.isJsonArray) return@withContext null
            val best = infos.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                .filter { it.str("codec") == "mp3" }
                .maxByOrNull { it.str("bitrateInKbps")?.toIntOrNull() ?: 0 }
                ?: return@withContext null
            val infoUrl = best.str("downloadInfoUrl") ?: return@withContext null
            val xml = execute(context, Request.Builder().url(infoUrl).yandexHeaders(context).get().build())
            fun tag(name: String) = Regex("<$name>([^<]*)</$name>").find(xml)?.groupValues?.get(1)
            val host = tag("host") ?: return@withContext null
            val path = tag("path") ?: return@withContext null
            val ts = tag("ts") ?: return@withContext null
            val s = tag("s") ?: return@withContext null
            val sign = MessageDigest.getInstance("MD5")
                .digest((SIGN_SALT + path.removePrefix("/") + s).toByteArray())
                .joinToString("") { "%02x".format(it) }
            val url = "https://$host/get-mp3/$sign/$ts$path"
            streamCache[id] = url to System.currentTimeMillis()
            url
        } catch (e: Exception) {
            Log.e(TAG, "Could not resolve Yandex stream for $id", e)
            null
        }
    }
}

package com.gfchat.app.data

import com.gfchat.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val message: String, val authExpired: Boolean = false, val offline: Boolean = false) :
        Outcome<Nothing>
}

enum class SignUpResult { SignedIn, ConfirmEmail }

data class AppConfig(val minVersionCode: Int, val downloadUrl: String)

sealed interface PairState {
    data object None : PairState
    data class Waiting(val code: String) : PairState
    data class Paired(val isAdmin: Boolean) : PairState
}

/** Supabase Auth + REST ko seedha HTTP se call karta hai (koi extra SDK nahi). */
class SupabaseApi(private val session: SessionStore) {

    private val http = OkHttpClient()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json".toMediaType()

    val configured: Boolean get() = base.startsWith("https://") && anonKey.isNotBlank()

    private class Raw(val code: Int, val body: String)

    private suspend fun call(path: String, body: JSONObject, bearer: String? = null): Raw =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$base$path")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer ${bearer ?: anonKey}")
                .post(body.toString().toRequestBody(json))
                .build()
            http.newCall(req).execute().use { Raw(it.code, it.body?.string().orEmpty()) }
        }

    private fun errorText(raw: Raw): String {
        val o = runCatching { JSONObject(raw.body) }.getOrNull()
        return o?.optString("msg").orEmpty().ifBlank {
            o?.optString("error_description").orEmpty().ifBlank {
                o?.optString("message").orEmpty().ifBlank { "Error ${raw.code}" }
            }
        }
    }

    private fun friendly(msg: String): String = when {
        "Invalid login credentials" in msg -> "Email ya password galat hai."
        "already registered" in msg || "already been registered" in msg -> "Ye email pehle se registered hai. Login karo."
        "Password should be at least" in msg -> "Password kam se kam 8 character ka rakho."
        "Signups not allowed" in msg || "signup is disabled" in msg.lowercase() ->
            "Naye account band hain (sirf 2 users allowed)."
        "Email not confirmed" in msg -> "Pehle email me aaya confirmation link khol do."
        "rate limit" in msg.lowercase() -> "Bahut try ho gaye, thodi der baad dobara karo."
        "ALREADY_PAIRED" in msg -> "Aap pehle se judey hue ho."
        "CODE_INVALID" in msg -> "Code galat hai ya expire ho gaya."
        else -> msg
    }

    private inline fun <T> guard(block: () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (e: IOException) {
        Outcome.Err("Internet check karo.", offline = true)
    }

    private fun storeTokens(o: JSONObject) {
        session.save(
            access = o.getString("access_token"),
            refresh = o.getString("refresh_token"),
            expiresInSec = o.optLong("expires_in", 3600),
            email = o.optJSONObject("user")?.optString("email"),
        )
    }

    suspend fun signIn(email: String, password: String): Outcome<Unit> = guard {
        val raw = call(
            "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password),
        )
        if (raw.code in 200..299) {
            storeTokens(JSONObject(raw.body)); Outcome.Ok(Unit)
        } else Outcome.Err(friendly(errorText(raw)))
    }

    suspend fun signUp(email: String, password: String): Outcome<SignUpResult> = guard {
        val raw = call(
            "/auth/v1/signup",
            JSONObject().put("email", email).put("password", password),
        )
        if (raw.code in 200..299) {
            val o = JSONObject(raw.body)
            if (o.has("access_token")) {
                storeTokens(o); Outcome.Ok(SignUpResult.SignedIn)
            } else Outcome.Ok(SignUpResult.ConfirmEmail)
        } else Outcome.Err(friendly(errorText(raw)))
    }

    fun signOut() = session.clear()

    /** Login se pehle bhi chalta hai (anon key se). Table RLS me sirf public read allowed hai. */
    suspend fun fetchAppConfig(): Outcome<AppConfig> = guard {
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$base/rest/v1/app_config?select=min_version_code,download_url&id=eq.1")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use Outcome.Err("Error ${resp.code}")
                val arr = JSONArray(body)
                if (arr.length() == 0) return@use Outcome.Err("Config nahi mila")
                val o = arr.getJSONObject(0)
                Outcome.Ok(AppConfig(o.getInt("min_version_code"), o.getString("download_url")))
            }
        }
    }

    /** Valid access token deta hai; zarurat ho to refresh karta hai. */
    private suspend fun validToken(): Outcome<String> {
        val refresh = session.refreshToken ?: return Outcome.Err("Login karo.", authExpired = true)
        val access = session.accessToken
        if (access != null && session.expiresAtMs - System.currentTimeMillis() > 60_000) return Outcome.Ok(access)
        return guard {
            val raw = call("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))
            if (raw.code in 200..299) {
                storeTokens(JSONObject(raw.body))
                Outcome.Ok(session.accessToken!!)
            } else {
                session.clear()
                Outcome.Err("Session khatam ho gaya, dobara login karo.", authExpired = true)
            }
        }
    }

    private suspend fun rpc(fn: String, args: JSONObject = JSONObject()): Outcome<String> {
        val token = when (val t = validToken()) {
            is Outcome.Ok -> t.value
            is Outcome.Err -> return t
        }
        return guard {
            val raw = call("/rest/v1/rpc/$fn", args, bearer = token)
            when {
                raw.code in 200..299 -> Outcome.Ok(raw.body)
                raw.code == 401 -> Outcome.Err("Login karo.", authExpired = true)
                else -> Outcome.Err(friendly(errorText(raw)))
            }
        }
    }

    suspend fun pairStatus(): Outcome<PairState> = when (val r = rpc("pair_status")) {
        is Outcome.Err -> r
        is Outcome.Ok -> {
            val o = JSONObject(r.value)
            val state = when (o.optString("state")) {
                "paired" -> PairState.Paired(isAdmin = o.optBoolean("is_admin", false))
                "waiting" -> PairState.Waiting(o.optString("code"))
                else -> PairState.None
            }
            session.pairedCached = state is PairState.Paired
            Outcome.Ok(state)
        }
    }

    suspend fun createPairCode(): Outcome<String> = when (val r = rpc("create_pair_code")) {
        is Outcome.Err -> r
        // Function plain text return karta hai, JSON string me aata hai: "ABCD1234"
        is Outcome.Ok -> Outcome.Ok(r.value.trim().trim('"'))
    }

    suspend fun joinPairCode(code: String): Outcome<Unit> =
        when (val r = rpc("join_pair_code", JSONObject().put("code", code))) {
            is Outcome.Err -> r
            is Outcome.Ok -> {
                session.pairedCached = true
                Outcome.Ok(Unit)
            }
        }

    /** Sirf admin (jisne pairing code banaya tha) ke liye. */
    suspend fun adminSetMinVersion(minVersionCode: Int, downloadUrl: String?): Outcome<Unit> {
        val args = JSONObject().put("new_min", minVersionCode)
        if (!downloadUrl.isNullOrBlank()) args.put("new_url", downloadUrl)
        return when (val r = rpc("admin_set_min_version", args)) {
            is Outcome.Err -> r
            is Outcome.Ok -> Outcome.Ok(Unit)
        }
    }
}

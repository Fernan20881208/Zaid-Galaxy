package dev.busung.s25uroot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.busung.s25uroot.ui.theme.galaxyGlassBackground
import dev.busung.s25uroot.ui.theme.galaxyGlassBorder
import dev.busung.s25uroot.ui.theme.galaxyGlassPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val LICENSE_ENDPOINT =
    "https://zzlvupunploglgxbgllm.supabase.co/functions/v1/zaid-mods-keys"
// Public API identifier only. Administrative credentials remain on the server.
private const val PUBLIC_API_KEY = "sb_publishable_VrbAjar4NUk6PvE92Je8Ig_Y2HPQV50"
private val KEY_PATTERN = Regex("^ZZ-[A-Z0-9]{4}-[A-Z0-9]{3}Z$")

private class LicenseStore(context: Context) {
    private val prefs = context.getSharedPreferences("zaid_galaxy_license_v1", Context.MODE_PRIVATE)
    private val alias = "zaid_galaxy_license_aes_v1"

    fun deviceId(): String {
        val existing = prefs.getString("device_id", null)
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        check(prefs.edit().putString("device_id", id).commit())
        return id
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(
                    alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build())
            }.generateKey()
    }

    fun load(): String? {
        val value = prefs.getString("encrypted_key", null) ?: return null
        val pieces = value.split(':')
        if (pieces.size != 2) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
                .takeIf(KEY_PATTERN::matches)
        }.getOrNull()
    }

    fun save(key: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = Base64.encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        check(prefs.edit().putString("encrypted_key", "$iv:$encrypted").commit())
    }
}

internal data class GalaxyLicenseResult(val valid: Boolean, val reason: String, val leaseSeconds: Long)

internal fun parseGalaxyLicenseResponse(httpStatus: Int, body: String): GalaxyLicenseResult {
    val json = runCatching { JSONObject(body) }.getOrNull()
        ?: return GalaxyLicenseResult(false, "server_error", 0)
    val valid = httpStatus in 200..299 && json.optBoolean("ok", false) &&
        json.optBoolean("valid", false) && json.optLong("lease_seconds", 0) > 0
    return GalaxyLicenseResult(valid, json.optString("reason", "server_error"),
        if (valid) json.optLong("lease_seconds", 0).coerceAtMost(120) else 0)
}

private fun checkLicense(action: String, key: String, device: String): GalaxyLicenseResult {
    val conn = URL(LICENSE_ENDPOINT).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("apikey", PUBLIC_API_KEY)
        conn.setRequestProperty("Accept", "application/json")
        val payload = JSONObject().put("action", action).put("key", key)
            .put("device_id", device).toString().toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(payload.size)
        conn.outputStream.use { it.write(payload) }
        val status = conn.responseCode
        val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return parseGalaxyLicenseResponse(status, body)
    } finally {
        conn.disconnect()
    }
}

private data class LicenseRequest(val key: String, val activate: Boolean)

private fun licenseMessage(reason: String): String = when (reason) {
    "invalid_key" -> "Llave incorrecta."
    "invalid_format" -> "Formato requerido: ZZ-XXXX-XXXZ."
    "revoked" -> "Esta licencia ha sido revocada."
    "expired" -> "Esta licencia ha vencido."
    "device_limit" -> "Se alcanzó el límite de dispositivos de la licencia."
    "activate_required" -> "Esta instalación necesita activar la licencia."
    "rate_limited" -> "Demasiados intentos. Inténtalo más tarde."
    "network_error" -> "No hay conexión con el servidor. Revisa tu Internet."
    "storage_error" -> "No se pudo guardar la licencia de forma segura."
    else -> "No se pudo verificar la licencia. Inténtalo nuevamente."
}

@Composable
internal fun ZaidLicenseGate(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = remember(context) { LicenseStore(context) }
    var unlocked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var request by remember { mutableStateOf<LicenseRequest?>(null) }

    LaunchedEffect(store) {
        val saved = withContext(Dispatchers.IO) { store.load() }
        if (saved != null) request = LicenseRequest(saved, false) else busy = false
    }

    LaunchedEffect(request, lifecycle) {
        val req = request ?: return@LaunchedEffect
        var action = if (req.activate) "activate" else "verify"
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // A fresh online check is required every time the activity becomes visible.
            unlocked = false
            busy = true
            while (isActive) {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val response = checkLicense(action, req.key, store.deviceId())
                        if (response.valid && action == "activate") {
                            try {
                                store.save(req.key)
                            } catch (_: Exception) {
                                return@runCatching GalaxyLicenseResult(false, "storage_error", 0)
                            }
                        }
                        response
                    }.getOrElse { GalaxyLicenseResult(false, "network_error", 0) }
                }
                busy = false
                if (!result.valid) {
                    unlocked = false
                    input = req.key
                    message = licenseMessage(result.reason)
                    request = null
                    return@repeatOnLifecycle
                }
                action = "verify"
                input = ""
                message = ""
                unlocked = true
                delay((result.leaseSeconds - 10).coerceIn(1, 90) * 1000)
            }
        }
    }

    if (unlocked) {
        content()
        return
    }

    Box(
        modifier = galaxyGlassBackground().fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = galaxyGlassPanel()),
            border = galaxyGlassBorder(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(painterResource(R.drawable.ic_app_logo), contentDescription = null,
                    modifier = Modifier.size(76.dp), tint = Color.Unspecified)
                Text("Zaid Galaxy", style = MaterialTheme.typography.headlineLarge)
                Text("Acceso con licencia", style = MaterialTheme.typography.titleMedium)
                Text("Introduce tu llave de Zaid Mods para continuar.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase(Locale.ROOT).take(12); message = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Llave") },
                    placeholder = { Text("ZZ-XXXX-XXXZ") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy,
                    shape = MaterialTheme.shapes.large,
                )
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && KEY_PATTERN.matches(input),
                    onClick = {
                        busy = true
                        message = ""
                        request = LicenseRequest(input, true)
                    },
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Activar licencia")
                }
                if (busy) Text("Verificando licencia…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

package com.alananasss.kittytune.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.yandex.YandexAuth
import com.alananasss.kittytune.data.yandex.YandexDeviceCode
import com.alananasss.kittytune.data.yandex.YandexMusic
import com.alananasss.kittytune.ui.common.SettingsScaffold
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun YandexAccountScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { YandexAuth(context) }

    var loggedIn by remember { mutableStateOf(auth.isLoggedIn()) }
    var name by remember { mutableStateOf(auth.displayName) }
    var hasPlus by remember { mutableStateOf(auth.hasPlus) }
    var code by remember { mutableStateOf<YandexDeviceCode?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    fun copyCode(value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", value))
        Toast.makeText(context, context.getString(R.string.yandex_code_copied), Toast.LENGTH_SHORT).show()
    }

    fun startLogin() {
        message = null
        busy = true
        pollJob?.cancel()
        pollJob = scope.launch {
            try {
                val c = YandexMusic.startDeviceLogin(context)
                code = c
                busy = false
                val account = YandexMusic.awaitDeviceLogin(context, c)
                code = null
                if (account != null) {
                    loggedIn = true
                    name = account.displayName
                    hasPlus = account.hasPlus
                } else {
                    message = context.getString(R.string.yandex_login_expired)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                code = null
                busy = false
                message = context.getString(R.string.yandex_login_error, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.yandex_login_title),
        onBackClick = {
            pollJob?.cancel()
            onBackClick()
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (loggedIn) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.pref_account_yandex_subtitle_connected, name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(if (hasPlus) R.string.yandex_account_plus else R.string.yandex_account_no_plus),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        YandexMusic.logout(context)
                        loggedIn = false
                        name = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.yandex_logout)) }
            } else {
                Text(
                    stringResource(R.string.yandex_login_intro),
                    style = MaterialTheme.typography.bodyLarge
                )
                val current = code
                if (current == null) {
                    Button(
                        onClick = { startLogin() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.height(20.dp)) else Text(stringResource(R.string.yandex_login_start))
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        onClick = { copyCode(current.userCode) }
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(R.string.yandex_login_code_label), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                current.userCode,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Button(
                        onClick = {
                            copyCode(current.userCode)
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(current.verificationUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.yandex_login_open)) }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.yandex_login_waiting), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

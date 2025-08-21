package com.example.lettore_rfid

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import java.math.BigInteger
import java.util.Locale

const val PREFS_NAME = "LettoreRFIDPrefs"
const val PREF_LANGUAGE = "language"

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private var textInfoState by mutableStateOf("Avvicina un tag RFID/NFC")
    private var textDecState by mutableStateOf("---")
    private var showNfcDisabledDialog by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val language = sharedPreferences.getString(PREF_LANGUAGE, Locale.getDefault().language) ?: Locale.getDefault().language
        val locale = Locale(language)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, getString(R.string.nfc_not_supported), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        textInfoState = getString(R.string.label_nfc_ready_description)

        setContent {
            YourAppTheme {
                MainScreen(
                    infoText = textInfoState,
                    decText = textDecState,
                    onLanguageChange = { langCode ->
                        setLocale(langCode)
                    }
                )
                if (showNfcDisabledDialog) {
                    NfcDisabledAlertDialog(
                        onDismiss = { showNfcDisabledDialog = false },
                        onOpenSettings = {
                            showNfcDisabledDialog = false
                            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    private fun setLocale(languageCode: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString(PREF_LANGUAGE, languageCode)
            apply()
        }
        recreate()
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let {
            if (!it.isEnabled) {
                textInfoState = getString(R.string.enable_nfc)
                showNfcDisabledDialog = true
            } else {
                textInfoState = getString(R.string.label_nfc_ready_description)
                showNfcDisabledDialog = false // Assicurati che il dialogo sia chiuso se NFC è abilitato
                enableForegroundDispatch()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            val nfcV = NfcV.get(tag)
            if (nfcV == null) {
                Toast.makeText(this, getString(R.string.tech_not_supported), Toast.LENGTH_SHORT).show()
                return
            }

            try {
                nfcV.connect()
                val cmd = byteArrayOf(0x02, 0x20, 0x00)
                val response = nfcV.transceive(cmd)

                if (response[0].toInt() == 0) {
                    val blockData = response.copyOfRange(1, response.size)
                    val dataToConvert = blockData.copyOfRange(0, 4)

                    val hexValue = bytesToHexString(dataToConvert)
                    val decValue = BigInteger(hexValue, 16)

                    textInfoState = getString(R.string.tag_detected)
                    textDecState = decValue.toString()

                } else {
                    textInfoState = getString(R.string.block_read_error)
                    textDecState = "---"
                    Toast.makeText(this, getString(R.string.block_read_error), Toast.LENGTH_SHORT).show()
                }

            } catch (e: IOException) {
                textInfoState = getString(R.string.communication_error)
                textDecState = "---"
                Toast.makeText(this, getString(R.string.communication_error), Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            } finally {
                try {
                    nfcV.close()
                } catch (e: IOException) {
                    // Ignora
                }
            }
        }
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    private fun enableForegroundDispatch() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
        val techLists = arrayOf(arrayOf<String>(NfcV::class.java.name))

        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techLists)
    }
}

@Composable
fun YourAppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}

@Composable
fun MainScreen(
    infoText: String,
    decText: String,
    onLanguageChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.main_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_main_bag_logo),
                    contentDescription = stringResource(id = R.string.label_nfc_icon_description),
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = if (decText != "---") decText else "",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            Text(
                text = infoText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp), // Aggiunto padding orizzontale dentro la Card
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguageButton(iconResId = R.drawable.ic_flag_it, "it", onLanguageChange)
                LanguageButton(iconResId = R.drawable.ic_flag_en, "en", onLanguageChange)
                LanguageButton(iconResId = R.drawable.ic_flag_de, "de", onLanguageChange)
                LanguageButton(iconResId = R.drawable.ic_flag_pl, "pl", onLanguageChange)
            }
        }
    }
}

@Composable
fun LanguageButton(iconResId: Int, langCode: String, onClick: (String) -> Unit) {
    IconButton(onClick = { onClick(langCode) }) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = "Switch to $langCode",
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun NfcDisabledAlertDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nfc_disabled_dialog_title)) },
        text = { Text(stringResource(R.string.nfc_disabled_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.nfc_disabled_dialog_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nfc_disabled_dialog_dismiss))
            }
        }
    )
}

@Preview(showBackground = true, locale = "it")
@Composable
fun DefaultPreviewIt() {
    YourAppTheme {
        MainScreen(
            infoText = "Avvicina un tag RFID/NFC",
            decText = "1234567890",
            onLanguageChange = {}
        )
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
fun DefaultPreviewEn() {
    YourAppTheme {
        MainScreen(
            infoText = "Approach an RFID tag",
            decText = "0987654321",
            onLanguageChange = {}
        )
    }
}


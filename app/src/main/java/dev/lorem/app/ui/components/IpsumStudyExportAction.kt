package dev.lorem.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.lorem.app.data.local.toStudyJson
import dev.lorem.app.domain.IpsumStudyExport
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IpsumStudyExportAction(
    prepare: suspend () -> IpsumStudyExport?,
    isCurrent: suspend (IpsumStudyExport) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<IpsumStudyExport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val contract = remember {
        object : ActivityResultContracts.CreateDocument("application/json") {
            override fun createIntent(context: Context, input: String): Intent =
                super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }
    val save = rememberLauncherForActivityResult(contract) { uri ->
        val snapshot = pending
        if (uri == null || snapshot == null) { pending = null; busy = false }
        else scope.launch {
            try {
                if (!isCurrent(snapshot)) {
                    notice = "O perfil mudou. Inicie outra exportação."
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot.toStudyJson(appVersion)) }
                }
                notice = "Arquivo salvo no local escolhido."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                notice = "Não foi possível salvar o arquivo. Tente novamente."
            } finally { pending = null; busy = false }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dados do estudo", style = MaterialTheme.typography.titleLarge)
            Text("Salve tentativas encerradas em um arquivo local, sem handle ou nome. O arquivo usa um identificador local e registra os dados ausentes.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy, onClick = {
                busy = true; notice = null
                scope.launch {
                    try {
                        val snapshot = prepare()
                        if (snapshot == null) { notice = "Conecte um perfil antes de exportar."; busy = false }
                        else { pending = snapshot; save.launch("lorem-ipsum-study-${snapshot.exportedAtEpochMillis}.json") }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        notice = "Não foi possível preparar o arquivo. Tente novamente."; pending = null; busy = false
                    }
                }
            }) { Text(if (busy) "Preparando arquivo…" else "Exportar tentativas") }
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

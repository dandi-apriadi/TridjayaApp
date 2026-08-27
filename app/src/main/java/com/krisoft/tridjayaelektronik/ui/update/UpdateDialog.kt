package com.krisoft.tridjayaelektronik.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.krisoft.tridjayaelektronik.data.update.UpdateDownloadState
import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveShapes
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextButton

/**
 * Update prompt. When [available] is a force update the dialog can't be dismissed (no cancel
 * button, back/outside taps ignored) — the user must update to continue, and [download] is
 * already auto-progressing ([UpdateViewModel] starts it as soon as a force update is detected).
 * Optional updates offer "Nanti" and only start downloading once "Perbarui Sekarang" is tapped.
 */
@Composable
fun UpdateDialog(
    available: UpdateStatus.Available,
    download: UpdateDownloadState,
    onUpdate: () -> Unit,
    /** Tombol "Nanti" — penolakan SENGAJA. Mengunci prompt untuk versi ini. */
    onDismiss: (() -> Unit)?,
    /**
     * Back / ketuk di luar dialog — penutupan TAK SENGAJA.
     *
     * Dulu keduanya memanggil [onDismiss] yang sama, jadi satu ketukan meleset
     * di luar kotak dialog mengunci prompt untuk versi itu SELAMA Activity
     * hidup (`_versiPromptDitutup` cuma di memori — hanya kematian proses yang
     * membersihkannya). Di HP kerja yang seharian di-background, itu berarti
     * berhari-hari tanpa satu pun tanda bahwa ada pembaruan menunggu.
     *
     * Efek itu SECARA STRUKTURAL hanya bisa terjadi saat update TIDAK wajib —
     * saat wajib, `dismissOnBackPress`/`dismissOnClickOutside` di bawah
     * dua-duanya `false` dan tombol "Nanti" pun tak dirender.
     *
     * `null` = perlakukan seperti [onDismiss] (perilaku lama).
     */
    onTundaSementara: (() -> Unit)? = null
) {
    val force = available.force
    AlertDialog(
        onDismissRequest = { (onTundaSementara ?: onDismiss)?.invoke() },
        shape = ExpressiveShapes.Large,
        icon = {
            if (download is UpdateDownloadState.Failed) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            } else {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        title = {
            Text(
                text = when {
                    download is UpdateDownloadState.Downloading -> "Mengunduh Pembaruan"
                    download is UpdateDownloadState.Failed -> "Gagal Mengunduh"
                    force -> "Pembaruan Wajib"
                    else -> "Pembaruan Tersedia"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Versi ${available.latestVersionName} sudah tersedia.")
                if (available.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = available.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (download) {
                    is UpdateDownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress = download.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.height(4.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row {
                                CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mengunduh…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is UpdateDownloadState.Failed -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = download.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        if (force) {
                            // Peringatan tetap (bukan cuma teks changelog per rilis) — kebijakan
                            // 2026-08-27: mandatory HANYA untuk perubahan yang benar-benar wajib,
                            // tapi begitu dipakai, penggunanya harus tahu KONSEKUENSINYA tanpa
                            // bergantung admin ingat menulisnya di changelog tiap kali.
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Fungsi-fungsi penting aplikasi (mis. absen) tidak bisa " +
                                    "dipakai sampai Anda memperbarui ke versi ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (download) {
                is UpdateDownloadState.Downloading -> { /* no confirm button while downloading */ }
                is UpdateDownloadState.Failed ->
                    ExpressiveFilledButton(onClick = onUpdate) { Text("Coba Lagi") }
                is UpdateDownloadState.ReadyToInstall ->
                    ExpressiveFilledButton(onClick = onUpdate) { Text("Instal Sekarang") }
                UpdateDownloadState.Idle ->
                    ExpressiveFilledButton(onClick = onUpdate) { Text("Perbarui Sekarang") }
            }
        },
        dismissButton = if (force || onDismiss == null || download is UpdateDownloadState.Downloading) null else {
            { ExpressiveTextButton(onClick = onDismiss) { Text("Nanti") } }
        },
        properties = DialogProperties(
            dismissOnBackPress = !force && download !is UpdateDownloadState.Downloading,
            dismissOnClickOutside = !force && download !is UpdateDownloadState.Downloading
        )
    )
}

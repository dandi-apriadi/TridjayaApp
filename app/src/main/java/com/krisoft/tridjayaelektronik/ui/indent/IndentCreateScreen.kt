package com.krisoft.tridjayaelektronik.ui.indent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFormError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.util.kunciUnik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun IndentCreateScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: IndentCreateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    // State-swap screen (not a nav destination): route system back to the list, not Home.
    BackHandler(onBack = onBack)

    LaunchedEffect(state.isDone) {
        if (state.isDone) onCreated()
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addPhotos(uris) }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    TridjayaCollapsibleHeader(title = "Ajukan Indent", onBack = onBack) { contentModifier ->
        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp + navBottom)
        ) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Pemesan (otomatis)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(viewModel.pemesan, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Cabang (otomatis)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(viewModel.pemesanCabang, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box {
                ExpressiveTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Nama barang *",
                    placeholder = "Cari produk atau ketik manual"
                )
                if (state.suggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 58.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 4.dp
                    ) {
                        Column {
                            state.suggestions.forEach { product ->
                                SuggestionRow(product, onClick = { viewModel.selectSuggestion(product) })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveTextField(
                value = state.quantityText,
                onValueChange = viewModel::onQuantityChange,
                modifier = Modifier.fillMaxWidth(),
                label = "Jumlah",
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveTextField(
                value = state.keterangan,
                onValueChange = viewModel::onKeteranganChange,
                modifier = Modifier.fillMaxWidth(),
                label = "Keterangan (opsional)",
                singleLine = false
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Bukti pengajuan (opsional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val kunciFoto = kunciUnik(state.photos) { it.uri.toString() }
                itemsIndexed(state.photos, key = { i, _ -> kunciFoto.getOrElse(i) { "idx_$i" } }) { _, foto ->
                    PhotoThumbnail(foto = foto, onRemove = { viewModel.removePhoto(foto) })
                }
                item {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = "Tambah foto bukti", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                ExpressiveFormError(message = state.errorMessage ?: "")
            }

            Spacer(modifier = Modifier.height(24.dp))

            ExpressiveFilledButton(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.isSubmitting) "Mengirim..." else "Ajukan Indent")
            }
        }
    }
}

@Composable
private fun SuggestionRow(product: ProductAggregate, onClick: () -> Unit) {
    Surface(color = Color.Transparent, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(product.nama, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${product.kode} · ${product.kategori}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Pratinjau dibaca dari SALINAN CACHE, bukan dari `Uri` picker: sesudah tahap
 * pilih, berkas itulah satu-satunya sumber byte yang tak bergantung pada grant
 * Uri (lihat KDoc `FotoBukti`). Sekaligus membuang cabang `loadThumbnail`
 * (API 29+) vs `decodeStream` tanpa batas (API 24-28) — cabang kedua mendekode
 * gambar UTUH cuma untuk kotak 72dp, jadi `inSampleSize` di bawah bukan sekadar
 * penyeragaman melainkan pengurangan alokasi puluhan MB per thumbnail.
 */
@Composable
private fun PhotoThumbnail(foto: FotoBukti, onRemove: () -> Unit) {
    var bitmap by remember(foto.file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(foto.file) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { dekodeKecil(foto.file, TARGET_THUMBNAIL_PX) }.getOrNull()
        }
    }
    Box(modifier = Modifier.size(72.dp)) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(
            modifier = Modifier.size(20.dp).padding(2.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error,
            onClick = onRemove
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, contentDescription = "Hapus foto", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(2.dp))
            }
        }
    }
}

private const val TARGET_THUMBNAIL_PX = 160

/**
 * Dekode berkas gambar ke bitmap yang cukup besar untuk kotak [targetPx],
 * memakai `inSampleSize` supaya foto 108 MP tak pernah masuk heap utuh.
 * `null` = tak bisa didekode (berkas rusak / format tak didukung ROM).
 */
private fun dekodeKecil(file: File, targetPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= targetPx) sampleSize *= 2
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}

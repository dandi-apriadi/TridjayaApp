package com.krisoft.tridjayaelektronik.ui.indent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.domain.indent.CreateIndentUseCase
import com.krisoft.tridjayaelektronik.domain.indent.SearchProductsUseCase
import com.krisoft.tridjayaelektronik.domain.indent.UploadIndentProofUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max

/** Server caps proof uploads at 5 MB — compress toward this so camera photos never bounce. */
private const val MAX_UPLOAD_BYTES = 4 * 1024 * 1024
private const val MAX_DIMENSION = 1920

data class IndentCreateUiState(
    val namaBarang: String = "",
    val productSku: String? = null,
    val productCategory: String? = null,
    val unitPriceSnapshot: Double? = null,
    val quantityText: String = "1",
    val keterangan: String = "",
    val searchQuery: String = "",
    val suggestions: List<ProductAggregate> = emptyList(),
    val photos: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isDone: Boolean = false
)

@HiltViewModel
class IndentCreateViewModel @Inject constructor(
    private val createIndentUseCase: CreateIndentUseCase,
    private val uploadIndentProofUseCase: UploadIndentProofUseCase,
    private val searchProductsUseCase: SearchProductsUseCase,
    tokenStore: TokenStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(IndentCreateUiState())
    val uiState: StateFlow<IndentCreateUiState> = _uiState.asStateFlow()

    val pemesan: String = tokenStore.userName ?: "-"
    val pemesanCabang: String = tokenStore.cabangName ?: "-"

    fun onSearchChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, namaBarang = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        viewModelScope.launch {
            val results = searchProductsUseCase(query).take(8)
            _uiState.update { it.copy(suggestions = results) }
        }
    }

    fun selectSuggestion(product: ProductAggregate) {
        _uiState.update {
            it.copy(
                namaBarang = product.nama,
                searchQuery = product.nama,
                productSku = product.kode,
                productCategory = product.kategori,
                unitPriceSnapshot = product.harga.takeIf { price -> price > 0 },
                suggestions = emptyList()
            )
        }
    }

    fun onQuantityChange(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(quantityText = value) }
        }
    }

    fun onKeteranganChange(value: String) {
        _uiState.update { it.copy(keterangan = value) }
    }

    /**
     * Memilih foto yang SAMA dua kali di pemilih galeri mengirimkan `Uri` yang
     * identik. Tanpa dedup, dua thumbnail kembar itu menjadi dua kunci
     * `LazyRow` yang sama dan Compose menjatuhkan app dengan
     * `IllegalArgumentException: Key ... was already used` — bukan sekadar
     * duplikat yang jelek di layar. Dedup di sini, BUKAN cuma di kunci daftar,
     * karena bukti kembar juga akan diunggah dua kali oleh [submit].
     */
    fun addPhotos(uris: List<Uri>) {
        _uiState.update { st -> st.copy(photos = (st.photos + uris).distinct()) }
    }

    fun removePhoto(uri: Uri) {
        _uiState.update { it.copy(photos = it.photos - uri) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.namaBarang.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nama barang wajib diisi") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            // Upload proof photos first (if any) — a create request only ever references
            // bukti paths that already exist server-side, never raw device URIs.
            val buktiUrls = mutableListOf<String>()
            for (uri in state.photos) {
                val prepared = withContext(Dispatchers.Default) { prepareProofUpload(uri) }
                if (prepared == null) {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Gagal membaca salah satu foto bukti") }
                    return@launch
                }
                val (bytes, mimeType) = prepared
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                when (val result = uploadIndentProofUseCase(bytes, "bukti_${System.currentTimeMillis()}.$extension", mimeType)) {
                    is AuthResult.Success -> buktiUrls += result.data
                    is AuthResult.Failure -> {
                        _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                        return@launch
                    }
                }
            }

            val request = CreateIndentRequest(
                productSku = state.productSku,
                productCategory = state.productCategory,
                unitPriceSnapshot = state.unitPriceSnapshot,
                quantity = state.quantityText.toIntOrNull(),
                namaBarang = state.namaBarang.trim(),
                keterangan = state.keterangan.trim().ifBlank { null },
                buktiUrls = buktiUrls.ifEmpty { null }
            )
            when (val result = createIndentUseCase(request)) {
                is AuthResult.Success -> _uiState.update { it.copy(isSubmitting = false, isDone = true) }
                is AuthResult.Failure -> _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
            }
        }
    }

    /**
     * Reads the picked file and, for images, recompresses toward the server's 5 MB proof cap
     * (raw camera photos routinely exceed it and bounced the whole submission). Returns the
     * upload bytes + mime type; PDFs and undecodable files pass through untouched.
     */
    private fun prepareProofUpload(uri: Uri): Pair<ByteArray, String>? {
        val raw = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        val mimeType = appContext.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.'))
            ?: "image/jpeg"
        if (mimeType == "application/pdf") return raw to mimeType
        val compressed = runCatching { compressImage(raw) }.getOrNull()
        return if (compressed != null) compressed to "image/webp" else raw to mimeType
    }

    /** Downscale to [MAX_DIMENSION], fix EXIF rotation, then WebP-compress under [MAX_UPLOAD_BYTES]. */
    private fun compressImage(raw: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) sampleSize *= 2
        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null

        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxSide
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }

        // Re-encoding drops EXIF, so bake the camera orientation into the pixels first.
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(raw))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        // WEBP (bukan WEBP_LOSSY, yang butuh API 30+) — deprecated tapi tetap
        // lossy & fungsional; lihat catatan sama di `PhotoWatermark.kt`.
        @Suppress("DEPRECATION")
        val format = Bitmap.CompressFormat.WEBP
        var quality = 85
        var out = ByteArrayOutputStream().apply { bitmap.compress(format, quality, this) }.toByteArray()
        while (out.size > MAX_UPLOAD_BYTES && quality > 40) {
            quality -= 15
            out = ByteArrayOutputStream().apply { bitmap.compress(format, quality, this) }.toByteArray()
        }
        return out
    }
}

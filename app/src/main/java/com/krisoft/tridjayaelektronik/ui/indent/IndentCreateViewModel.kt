package com.krisoft.tridjayaelektronik.ui.indent

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.domain.indent.CreateIndentUseCase
import com.krisoft.tridjayaelektronik.domain.indent.SearchProductsUseCase
import com.krisoft.tridjayaelektronik.domain.indent.UploadIndentProofUseCase
import com.krisoft.tridjayaelektronik.util.ImagePixelPipeline
import com.krisoft.tridjayaelektronik.util.bacaInfoBerkas
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Server caps proof uploads at 5 MB — compress toward this so camera photos never bounce. */
private const val MAX_UPLOAD_BYTES = 4L * 1024 * 1024
private const val MAX_DIMENSION = 1920

// WEBP (bukan WEBP_LOSSY, yang butuh API 30+) — deprecated tapi tetap lossy & fungsional;
// lihat catatan sama di `PhotoWatermark.kt`.
@Suppress("DEPRECATION")
private val IMAGE_PARAMS = ImagePixelPipeline.Params(
    maxDimension = MAX_DIMENSION,
    format = Bitmap.CompressFormat.WEBP,
    startQuality = 85,
    minQuality = 40,
    step = 15,
    maxBytes = MAX_UPLOAD_BYTES,
)

/**
 * Satu foto bukti yang SUDAH ada salinannya di `cacheDir/indent/`.
 *
 * [uri] disimpan hanya sebagai identitas (dedup pilihan ganda + kunci `LazyRow`),
 * BUKAN untuk dibaca lagi — grant Photo Picker tak persistable, jadi satu-satunya
 * sumber byte yang sah setelah tahap pilih adalah [file]. [nama] & [mimeType]
 * dibaca sekali saat menyalin, sehingga jalur kirim tak menyentuh ContentResolver
 * sama sekali.
 *
 * [uploadedUrl] terisi setelah unggahannya berhasil, supaya penekanan tombol
 * "Ajukan Indent" berikutnya tidak mengunggah berkas yang sama dua kali.
 */
data class FotoBukti(
    val uri: Uri,
    val file: File,
    val nama: String,
    val mimeType: String,
    val uploadedUrl: String? = null,
)

/** Hasil menyalin satu pilihan ke cache — sebab kegagalan ikut, tidak ditelan. */
private sealed interface SalinFoto {
    data class Berhasil(val foto: FotoBukti) : SalinFoto
    data class Gagal(val label: String) : SalinFoto
}

/** `null` → `"tak diketahui"`, supaya label kegagalan tak pernah berakhir "()". */
private fun Throwable?.namaKelas(): String = this?.javaClass?.simpleName ?: "tak diketahui"

private fun FotoBukti.denganUrl(peta: Map<Uri, String>): FotoBukti =
    peta[uri]?.let { copy(uploadedUrl = it) } ?: this

/**
 * Foto yang gagal DISALIN di tahap pilih. `null` = tak ada yang gagal.
 *
 * Menyebut yang mana + sebabnya disengaja: `FileNotFoundException` (berkas Google
 * Foto yang masih di cloud), `SecurityException` (grant sudah dicabut), dan
 * `IOException` (unduhan putus) menuntut langkah yang berbeda-beda dari
 * pemiliknya, sementara "gagal membaca salah satu foto bukti" — kalimat yang
 * dipakai sampai vc116 — tak menuntun ke satu pun.
 */
internal fun pesanFotoGagalDisalin(label: List<String>): String? {
    if (label.isEmpty()) return null
    return "${label.size} foto tidak bisa dibaca dan tidak ikut dilampirkan: " +
        "${label.joinToString(", ")}. Coba buka di Galeri lalu simpan ulang sebagai JPG."
}

/**
 * Foto yang sudah tersalin tapi gagal DISIAPKAN saat mengirim (mis. gambar
 * korup, memori tak cukup). `null` = tak ada yang gagal.
 *
 * Ekor "tekan lagi" ada karena pengajuannya memang BERHENTI: foto yang gagal
 * dilepas dari daftar, dan melanjutkan tanpa bukti itu harus jadi keputusan
 * pemiliknya, bukan keputusan diam-diam app.
 */
internal fun pesanFotoDilewati(label: List<String>): String? {
    if (label.isEmpty()) return null
    return "${label.size} foto bukti gagal disiapkan dan sudah dilepas dari daftar: " +
        "${label.joinToString(", ")}. Tekan \"Ajukan Indent\" lagi untuk melanjutkan tanpa foto itu."
}

data class IndentCreateUiState(
    val namaBarang: String = "",
    val productSku: String? = null,
    val productCategory: String? = null,
    val unitPriceSnapshot: Double? = null,
    val quantityText: String = "1",
    val keterangan: String = "",
    val searchQuery: String = "",
    val suggestions: List<ProductAggregate> = emptyList(),
    val photos: List<FotoBukti> = emptyList(),
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
     * Foto disalin ke `cacheDir/indent/` SEKARANG JUGA, bukan saat [submit].
     *
     * ## Kenapa disalin, bukan menyimpan `Uri`-nya
     *
     * Grant `Uri` dari Photo Picker TIDAK persistable: ia hidup selama Activity
     * pemanggilnya hidup, dan tak seorang pun pernah memanggil
     * `takePersistableUriPermission` di sini. Menyimpannya lalu membacanya
     * belakangan berarti bergantung pada izin yang bisa hilang di antara "pilih"
     * dan "Ajukan".
     *
     * **Jujur soal seberapa besar dampaknya:** layar ini TIDAK punya pemulihan
     * state (`IndentCreateUiState` hidup di ViewModel biasa, bukan
     * `SavedStateHandle`), jadi kalau prosesnya benar-benar mati, isian formnya
     * ikut hilang dan orangnya mengulang dari nol — grant yang basi bukan
     * kerugian tambahan di skenario itu. Yang benar-benar dibayar salinan ini
     * ada tiga, dan ketiganya nyata tanpa perlu proses mati: kegagalan baca
     * dilaporkan saat MEMILIH (bukan setelah menunggu unggahan), nama asli
     * berkas + MIME terbaca sekali di sini sehingga [submit] tak lagi menyentuh
     * ContentResolver sama sekali, dan pembacaan diska keluar dari jalur kirim.
     *
     * Dedup tetap by-`Uri` (bukan by-berkas): memilih foto yang SAMA dua kali
     * mengirim `Uri` yang identik, dan tanpa dedup dua thumbnail kembar menjadi
     * dua kunci `LazyRow` yang sama — Compose menjatuhkan app dengan
     * `IllegalArgumentException: Key … was already used`, bukan sekadar
     * duplikat yang jelek di layar. Menyalin dulu baru dedup akan kehilangan
     * sifat itu, karena tiap salinan berkasnya unik.
     */
    fun addPhotos(uris: List<Uri>) {
        val sudahAda = _uiState.value.photos.map { it.uri }.toSet()
        val baru = uris.distinct().filterNot { it in sudahAda }
        if (baru.isEmpty()) return
        viewModelScope.launch {
            val hasil = withContext(Dispatchers.IO) { baru.map { salinKeCache(it) } }
            val berhasil = hasil.filterIsInstance<SalinFoto.Berhasil>().map { it.foto }
            val gagal = hasil.filterIsInstance<SalinFoto.Gagal>().map { it.label }
            _uiState.update { st ->
                st.copy(
                    photos = st.photos + berhasil,
                    errorMessage = pesanFotoGagalDisalin(gagal) ?: st.errorMessage,
                )
            }
        }
    }

    fun removePhoto(foto: FotoBukti) {
        _uiState.update { it.copy(photos = it.photos - foto) }
        // Salinan cache tak berguna lagi begitu thumbnailnya dilepas. Gagal
        // hapus tak perlu dilaporkan — OS membersihkan cacheDir sendiri.
        runCatching { foto.file.delete() }
    }

    /**
     * Salin satu `Uri` pilihan ke cache + baca nama & MIME-nya sekali di sini.
     * Sebab kegagalan DIBAWA IKUT (`e.javaClass.simpleName`) — `getOrNull()`
     * yang lama menelannya, sehingga "gagal membaca salah satu foto bukti"
     * adalah satu-satunya keterangan yang pernah dilihat siapa pun.
     */
    private fun salinKeCache(uri: Uri): SalinFoto {
        val resolver = appContext.contentResolver
        val (namaAsli, _) = bacaInfoBerkas(resolver, uri)
        val nama = namaAsli.ifBlank { "foto" }
        val mimeType = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.'))
            ?: "image/jpeg"
        val target = File(appContext.cacheDir, "indent/bukti_${System.nanoTime()}")
            .apply { parentFile?.mkdirs() }
        return runCatching {
            resolver.openInputStream(uri)?.use { inp ->
                target.outputStream().use { out -> inp.copyTo(out) }
            } ?: error("openInputStream null")
        }.fold(
            onSuccess = { SalinFoto.Berhasil(FotoBukti(uri, target, nama, mimeType)) },
            onFailure = { e ->
                runCatching { target.delete() }
                SalinFoto.Gagal("$nama (${e.javaClass.simpleName})")
            },
        )
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
            val gagalSiap = mutableListOf<FotoBukti>()
            val labelGagal = mutableListOf<String>()
            val urlBaru = mutableMapOf<Uri, String>()
            for (foto in state.photos) {
                // URL dari percobaan sebelumnya dipakai ulang: satu foto yang
                // gagal disiapkan menghentikan pengajuan (lihat di bawah), dan
                // tanpa ini penekanan tombol kedua akan mengunggah ULANG berkas
                // yang sudah ada di server — berkas yatim yang tak pernah
                // dirujuk baris indent mana pun.
                val sudah = foto.uploadedUrl
                if (sudah != null) {
                    buktiUrls += sudah
                    continue
                }
                val siap = prepareProofUpload(foto)
                val isi = siap.getOrNull()
                if (isi == null) {
                    gagalSiap += foto
                    labelGagal += "${foto.nama} (${siap.exceptionOrNull().namaKelas()})"
                    continue
                }
                val (bytes, mimeType) = isi
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                when (val result = uploadIndentProofUseCase(bytes, "bukti_${System.currentTimeMillis()}.$extension", mimeType)) {
                    is AuthResult.Success -> {
                        buktiUrls += result.data
                        urlBaru[foto.uri] = result.data
                    }
                    is AuthResult.Failure -> {
                        // Kegagalan JARINGAN — bisa sembuh sendiri, jadi
                        // fotonya dipertahankan (beserta yang sudah naik) dan
                        // pesannya dari server apa adanya.
                        _uiState.update { st ->
                            st.copy(
                                isSubmitting = false,
                                errorMessage = result.message,
                                photos = st.photos.map { it.denganUrl(urlBaru) },
                            )
                        }
                        return@launch
                    }
                }
            }

            // Foto yang tak bisa disiapkan DILEPAS dari daftar (thumbnailnya
            // hilang = umpan balik yang terlihat) dan pengajuannya BERHENTI di
            // sini, bukan lanjut diam-diam tanpa bukti itu: bukti indent adalah
            // alasan pengajuannya disetujui, dan membuangnya tanpa sepengetahuan
            // pemiliknya kelas kesalahannya lebih mahal daripada satu tekan
            // tombol tambahan. Tekan "Ajukan Indent" lagi = lanjut tanpa foto itu.
            //
            // Sebelum ini SATU foto rusak membatalkan seluruh pengajuan dengan
            // "Gagal membaca salah satu foto bukti" — tanpa menyebut yang mana,
            // dan tanpa jalan keluar selain menebak lalu menghapus satu per satu.
            if (gagalSiap.isNotEmpty()) {
                _uiState.update { st ->
                    st.copy(
                        isSubmitting = false,
                        errorMessage = pesanFotoDilewati(labelGagal),
                        photos = st.photos.filterNot { it in gagalSiap }.map { it.denganUrl(urlBaru) },
                    )
                }
                return@launch
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
     * Membaca salinan cache lalu, untuk gambar, mengompres ulang menuju batas
     * bukti 5 MB milik server (foto kamera mentah rutin menembusnya dan dulu
     * memantulkan seluruh pengajuan). Mengembalikan byte upload + MIME; PDF dan
     * berkas yang tak bisa didekode lewat apa adanya.
     *
     * **`Result`, bukan `null`.** Versi lama membungkus segalanya dengan
     * `.getOrNull()` sehingga `OutOfMemoryError` saat dekode, berkas korup, dan
     * `IOException` sama-sama menjadi `null` — lalu dilaporkan sebagai satu
     * kalimat "gagal membaca salah satu foto bukti" yang tak menyebut foto mana
     * pun maupun sebabnya. Sekarang sebabnya sampai ke layar (lihat
     * [pesanFotoDilewati]).
     *
     * `runCatching` (bukan `try/catch (e: Exception)`) memang disengaja: yang paling mungkin
     * dilempar dari dalam `ImagePixelPipeline.compress` adalah `OutOfMemoryError`, turunan
     * `Error` yang tak tertangkap `catch (e: Exception)` dan akan menutup app lewat
     * `viewModelScope` — kelas kegagalan yang sama dengan `PhotoWatermark.prepareWatermarkedJpeg`
     * (`ImagePixelPipeline.compress` sendiri SUDAH membungkus badannya dengan `runCatching`
     * juga, dijaga `ImagePixelPipelineGuardTest`; lapis di sini menangkap `foto.file.readBytes()`
     * yang berada DI LUAR pipeline).
     *
     * `suspend` + `withContext(Dispatchers.Default)` membungkus SELURUH badan (bukan cuma bagian
     * kompresi) supaya `ImagePixelPipeline.compress` dipanggil di fungsi yang sama dengan
     * pemindahan dispatcher-nya — dijaga `ImagePixelPipelineGuardTest`. Ini murni pemindahan
     * tempat: `submit()` dulu membungkus PANGGILAN ke fungsi ini dengan
     * `withContext(Dispatchers.Default) { prepareProofUpload(foto) }`; utasnya sama persis,
     * cuma baris `withContext`-nya kini di dalam, bukan di kalinya `submit()`.
     */
    private suspend fun prepareProofUpload(foto: FotoBukti): Result<Pair<ByteArray, String>> =
        withContext(Dispatchers.Default) {
            runCatching {
                val raw = foto.file.readBytes()
                if (raw.isEmpty()) error("berkas salinan kosong")
                if (foto.mimeType == "application/pdf") return@runCatching raw to foto.mimeType
                val compressed = ImagePixelPipeline.compress(raw, IMAGE_PARAMS)?.first
                if (compressed != null) compressed to "image/webp" else raw to foto.mimeType
            }
        }
}

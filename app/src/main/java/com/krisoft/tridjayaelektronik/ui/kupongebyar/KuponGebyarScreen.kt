package com.krisoft.tridjayaelektronik.ui.kupongebyar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarBarisDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Tag logcat layar ini — dipakai untuk menelusuri kegagalan siapkan-foto. */
private const val TAG_GEBYAR = "KuponGebyar"

/**
 * Pembungkus yang MEMISAHKAN dua kegagalan yang kebetulan memakai kelas
 * exception yang sama.
 *
 * `contentResolver.openInputStream()` dan `File.outputStream()` sama-sama
 * melempar [java.io.FileNotFoundException], padahal artinya berlawanan: yang
 * pertama "foto sumbernya tak bisa dibuka", yang kedua "penyimpanan HP kita
 * bermasalah". Lebih menyesatkan lagi, Android memetakan SELURUH `ErrnoException`
 * ke `FileNotFoundException` — jadi penyimpanan penuh (ENOSPC) pun muncul dengan
 * nama kelas yang berbunyi "berkas tidak ditemukan".
 *
 * Selama keduanya menyatu dalam satu penangkap, pesan ke sales selalu
 * menyalahkan fotonya, dan orang yang penyimpanannya penuh diberi saran yang
 * tak mungkin menolongnya. Dilaporkan dari lapangan 2026-08-28 20:40.
 */
private class GagalBacaSumber(cause: Throwable) : Exception(cause)

private class GagalTulisCache(cause: Throwable) : Exception(cause)

/**
 * "Konsumen Gebyar" — daftar konsumen cabang yang berhak kupon doorprize, dan
 * tombol untuk mencatat bahwa undangannya sudah dikirim.
 *
 * **Yang tampil di sini hanya konsumen yang PUNYA nomor.** Server menyaringnya
 * (`service.rs`), bukan layar ini — dan jumlah yang tersaring ikut ditampilkan
 * (`tanpaNomor`) supaya pekerjaan yang tak bisa dikerjakan dari HP tidak lenyap
 * diam-diam dari kesadaran cabang.
 *
 * **Satu konsumen = satu kupon, apa pun nilai belanjanya.** Kalau layar ini
 * kelak menampilkan "x kupon", angkanya tetap dari server; jangan pernah
 * menghitungnya dari `totalBelanja / ambang` di klien.
 */
@Composable
fun KuponGebyarScreen(
    onBack: () -> Unit,
    viewModel: KuponGebyarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.muat() }

    // Baris yang kameranya baru dibuka. WAJIB dicatat: callback kamera tak
    // membawa konteks apa pun, jadi tanpa ini bukti bisa mendarat di konsumen
    // yang salah kalau karyawan menggulir daftar sementara kamera terbuka —
    // dan kupon orang lain hangus tanpa satu pun galat.
    var buktiUntuk by rememberSaveable { mutableStateOf<String?>(null) }

    // `cacheDir/kupon-gebyar/` WAJIB punya entri di res/xml/file_paths.xml.
    // Tanpa itu `getUriForFile` melempar, dan karena panggilannya ada di dalam
    // `remember` ia melempar saat KOMPOSISI — app tutup begitu layar dibuka,
    // bukan saat tombol ditekan. Dijaga `FileProviderPathsTest`.
    val fileFoto = remember {
        File(context.cacheDir, "kupon-gebyar").apply { mkdirs() }.let { File(it, "bukti.jpg") }
    }
    val uriFoto = remember(fileFoto) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileFoto)
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val kode = buktiUntuk
        val baris = state.items.firstOrNull { it.kodeRekanan == kode }
        buktiUntuk = null
        when {
            ok && baris != null -> viewModel.unggahBukti(baris, fileFoto, catatan = null)
            // DULU: `if (ok && baris != null)` tanpa cabang lain — jadi kamera
            // yang gagal menulis TIDAK menghasilkan apa pun. Nol toast, nol
            // unggahan, nol jejak. Sales melihat app kamera terbuka dan rana
            // bekerja, lalu kembali ke daftar yang tampak normal, dan menyangka
            // buktinya terkirim. Itulah kenapa "Kamera berfungsi" TIDAK PERNAH
            // bisa dipakai sebagai bukti bahwa penyimpanannya sehat — dan
            // kenapa laporan "hanya 1 yang terupload, sisanya tidak" bisa
            // muncul tanpa satu pun pesan galat.
            //
            // `ok == false` di sini berarti app kamera tak jadi menyimpan:
            // dibatalkan, ATAU gagal menulis ke `cacheDir/kupon-gebyar/`
            // (direktori sudah dibersihkan sistem / penyimpanan penuh).
            // Keduanya tak bisa dibedakan dari sini — kontrak `TakePicture`
            // cuma memberi Boolean — jadi kalimatnya menyebut keduanya.
            !ok && baris != null -> Toast.makeText(
                context,
                "Foto tidak jadi tersimpan. Kalau tadi tidak sengaja membatalkan, " +
                    "periksa sisa penyimpanan HP lalu coba lagi.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Kamera ATAU galeri — keduanya bermuara ke `fileFoto` yang sama supaya
    // jalur unggahnya (watermark + POST) cuma satu implementasi.
    //
    // Picker-nya `PickVisualMedia` (Photo Picker) dan **tidak butuh izin apa
    // pun**; di bawah Android 11/13 ia turun sendiri ke SAF. JANGAN ditambal
    // dengan `READ_MEDIA_*`/`READ_EXTERNAL_STORAGE` — pola yang sama sudah
    // ditulis di `AktivitasScreen.kt`.
    //
    // Sampai 2026-08-28 baris ini memakai `GetContent()` sambil menuliskan
    // komentar "GetContent() = Android Photo Picker". Itu KELIRU dan bukan
    // sekadar salah istilah: `GetContent()` memicu `ACTION_GET_CONTENT`
    // (pemilih dokumen lama) yang diarahkan ke aplikasi galeri bawaan HP, dan
    // URI yang dikembalikannya tidak selalu bisa dibuka — foto yang masih di
    // cloud dan belum terunduh, atau hibah izin URI yang hilang saat proses
    // app sempat dimatikan sistem. Photo Picker menyerahkan URI ber-hibah baca
    // yang stabil. Dilaporkan dari lapangan: sales Haurgeulis gagal 17:33 lalu
    // berhasil 17:41 di versi app yang SAMA — gejala sesaat, bukan blokir.
    val galeri = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val kode = buktiUntuk
        val baris = state.items.firstOrNull { it.kodeRekanan == kode }
        buktiUntuk = null
        if (uri == null || baris == null) return@rememberLauncherForActivityResult
        // `runCatching { ... }.isSuccess` DULU membuang exception-nya, jadi
        // keempat sebab di atas menghasilkan satu pesan identik dan tak ada
        // yang bisa tahu mana yang kena — termasuk saat menyelidiki laporan
        // nyata. Sebabnya sekarang dibawa sampai ke layar.
        // `openInputStream` SENDIRI yang melempar (SecurityException saat hibah
        // URI hilang, FileNotFoundException saat berkasnya di cloud), jadi ia
        // WAJIB ada DI DALAM `runCatching`. Menaruhnya di luar membuat
        // kegagalan yang sedang kita tangani ini justru menutup app.
        //
        // Penyalinannya di `Dispatchers.IO`, BUKAN di badan callback. Callback
        // picker dipanggil di main thread, dan justru foto yang jadi keluhan
        // di sini — yang masih di cloud — membuat `openInputStream` MENGUNDUH
        // dulu dari jaringan sebelum byte pertama keluar. Menyalinnya di main
        // thread berarti layar membeku selama unduhan itu, dan pada berkas
        // besar/sinyal jelek berujung ANR: "aplikasi tidak merespons" alih-alih
        // pesan yang bisa dikerjakan. Pola `withContext` ini sudah dipakai enam
        // pemanggil `PhotoWatermark` lain (mis. `HomeServiceLaporViewModel`).
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // `mkdirs()` DI SINI, bukan cuma di `remember{}` yang membuat
                    // `fileFoto`. Blok `remember` jalan SEKALI saat layar pertama
                    // disusun; Android boleh mengosongkan `cacheDir` kapan saja
                    // saat penyimpanan menipis — termasuk SELAGI layar ini
                    // terbuka dan pemilih foto sedang menutupinya. Kalau itu
                    // terjadi, direktorinya lenyap dan `outputStream()` melempar
                    // **FileNotFoundException (ENOENT)** — exception yang SAMA
                    // dengan kegagalan membaca foto sumber, sehingga pesannya
                    // menyalahkan "fotonya masih di cloud" padahal yang hilang
                    // justru direktori milik kita sendiri. Pola per-tulis ini
                    // sudah dipakai `AktivitasScreen.kt:145` dan
                    // `SpkItemCard.kt:522,618`; layar ini ketinggalan.
                    fileFoto.parentFile?.mkdirs()
                    // BACA dan TULIS dipisah tegas, dan itu bukan kerapian:
                    // `openInputStream` DAN `outputStream()` sama-sama melempar
                    // `FileNotFoundException`, jadi satu penangkap membuat dua
                    // sebab yang berlawanan arah tampil identik — "foto sumber
                    // tak terbaca" vs "penyimpanan HP kita bermasalah". Selama
                    // keduanya menyatu, kalimat ke sales SELALU menyalahkan
                    // fotonya, dan saran "buka dulu di galeri" tak akan pernah
                    // menolong orang yang penyimpanannya penuh.
                    val masuk = try {
                        context.contentResolver.openInputStream(uri)
                            ?: error("galeri tak memberi isi berkas")
                    } catch (e: Exception) {
                        throw GagalBacaSumber(e)
                    }
                    masuk.use { input ->
                        try {
                            fileFoto.outputStream().use { output -> input.copyTo(output) }
                        } catch (e: Exception) {
                            throw GagalTulisCache(e)
                        }
                    }
                }
            }.fold(
                onSuccess = { viewModel.unggahBukti(baris, fileFoto, catatan = null, dariGaleri = true) },
                onFailure = { e ->
                    // `e.message` IKUT dicatat ke logcat. Ia memuat path + string
                    // errno ("ENOSPC (No space left on device)", "ENOENT (No such
                    // file or directory)") — satu-satunya hal yang memisahkan
                    // penyimpanan penuh dari direktori hilang. Membuangnya adalah
                    // kelemahan yang sama seperti `.isSuccess` dulu, satu tingkat
                    // lebih dalam: dulu exception-nya yang hilang, lalu pesannya.
                    Log.w(TAG_GEBYAR, "gagal menyiapkan foto galeri", e)
                    val pesan = when (e) {
                        is GagalTulisCache ->
                            "Foto gagal disimpan di HP — kemungkinan besar penyimpanan penuh. " +
                                "Kosongkan sedikit ruang lalu coba lagi."
                        is GagalBacaSumber ->
                            "Foto itu tidak bisa dibaca (${e.cause?.javaClass?.simpleName ?: "?"}). " +
                                "Kalau fotonya masih di cloud dan belum terunduh, buka dulu di galeri " +
                                "sampai tampil penuh, lalu pilih lagi — atau ambil ulang lewat Kamera."
                        else ->
                            "Foto gagal disiapkan (${e.javaClass.simpleName}). Coba ulangi, " +
                                "atau ambil ulang lewat Kamera."
                    }
                    Toast.makeText(context, pesan, Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    // Snackbar penuh butuh Scaffold yang layar ini tak punya (header kolaps
    // menyediakan bodinya sendiri) — Toast dipakai seperti layar lain di app.
    LaunchedEffect(state.pesanSukses) {
        state.pesanSukses?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.bersihkanPesanSukses()
        }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.bersihkanActionError()
        }
    }

    TridjayaCollapsibleHeader(title = "Konsumen Gebyar", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            ExpressiveTextField(
                value = state.cari,
                onValueChange = viewModel::onCariChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "Cari nama atau nomor konsumen",
            )

            RingkasanCabang(state)

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && state.items.isEmpty() -> Column(modifier = Modifier.padding(top = 4.dp)) {
                        repeat(6) {
                            SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }

                    state.error != null && state.items.isEmpty() -> ScrollableCenter {
                        ExpressiveErrorState(
                            // 403 di sini berarti cabang di luar program (Manado)
                            // atau akun tak terikat cabang — pesannya datang dari
                            // server, yang sengaja membedakan keduanya.
                            message = state.error ?: "Gagal memuat daftar konsumen Gebyar.",
                            onRetry = viewModel::muat,
                        )
                    }

                    state.items.isEmpty() -> ScrollableCenter {
                        ExpressiveEmptyState(
                            icon = {
                                Icon(
                                    Icons.Rounded.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                            },
                            title = if (state.cari.isBlank()) "Semua undangan sudah dikirim."
                            else "Tidak ada yang cocok.",
                            subtitle = if (state.cari.isBlank()) {
                                "Tidak ada konsumen tersisa di cabang ini yang belum " +
                                    "dikirimi undangan Gebyar."
                            } else {
                                "Coba kata kunci lain, atau kosongkan pencarian."
                            },
                        )
                    }

                    else -> TridjayaPullRefresh(
                        isRefreshing = state.loading,
                        onRefresh = viewModel::muat,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 24.dp + navBottom,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.items, key = { it.kodeRekanan }) { baris ->
                                BarisKonsumen(
                                    baris = baris,
                                    mengunggah = state.mengunggah,
                                    tertunda = state.buktiTertunda.containsKey(baris.kodeRekanan),
                                    onKirimKamera = {
                                        buktiUntuk = baris.kodeRekanan
                                        // Sama seperti jalur galeri: direktori cache bisa
                                        // sudah dibersihkan sistem sejak layar dibuka, dan
                                        // app kamera menulis lewat FileProvider yang TIDAK
                                        // membuat direktori induk sendiri — hasilnya foto
                                        // gagal tersimpan tanpa sebab yang jelas.
                                        fileFoto.parentFile?.mkdirs()
                                        kamera.launch(uriFoto)
                                    },
                                    onKirimGaleri = {
                                        buktiUntuk = baris.kodeRekanan
                                        galeri.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    onSimpanUlang = {
                                        viewModel.simpanUlang(baris.kodeRekanan, catatan = null)
                                    },
                                    onChat = { nomor -> bukaWhatsApp(context, nomor) },
                                )
                            }

                            if (adaHalamanLagi(state.items.size, state.total)) {
                                item {
                                    TextButton(
                                        onClick = viewModel::muatLagi,
                                        enabled = !state.memuatLagi,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            if (state.memuatLagi) "Memuat…"
                                            else "Muat ${state.items.size} / ${state.total} — tampilkan lagi",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RingkasanCabang(state: KuponGebyarUiState) {
    if (state.namaCabang.isBlank() && state.total == 0) return
    val capaian = teksCapaian(state.sudahDikirim, state.jumlahKupon, state.persen)
    val rasio = rasioCapaian(state.persen)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (state.namaCabang.isNotBlank()) {
            Text(
                text = "Cabang: ${state.namaCabang}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (capaian != null && rasio != null) {
            Text(
                text = capaian,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            LinearProgressIndicator(
                progress = { rasio },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
            )
        }
        if (state.tanpaNomor > 0) {
            // Dipisah supaya cabang tak terlihat lalai untuk sebab yang bukan
            // salahnya: nomornya memang tak ada di sistem, jadi undangannya
            // tak bisa dikirim dari layar ini sama sekali.
            Text(
                text = "${state.tanpaNomor} konsumen berhak tidak tampil — nomornya tidak ada " +
                    "di data. Cari nomornya lewat admin cabang.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BarisKonsumen(
    baris: KuponGebyarBarisDto,
    mengunggah: Boolean,
    tertunda: Boolean,
    onKirimKamera: () -> Unit,
    onKirimGaleri: () -> Unit,
    onSimpanUlang: () -> Unit,
    onChat: (String) -> Unit,
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = baris.nama.ifBlank { baris.kodeRekanan },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = baris.hp?.takeIf { it.isNotBlank() } ?: "nomor tidak ada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (baris.sudahDikirim) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${formatRupiahGebyar(baris.totalBelanja)} · ${baris.transaksi} transaksi · " +
                    "terakhir ${baris.tanggalTerakhir}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = statusBaris(baris),
                style = MaterialTheme.typography.labelSmall,
                color = if (baris.sudahDikirim) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (baris.perluNomorPengganti) {
                // Nomor yang tercatat adalah nomor KARYAWAN. Undangan yang
                // dikirim ke sana tak pernah sampai ke konsumennya, dan kuponnya
                // hangus tanpa siapa pun tahu — jadi ini peringatan, bukan
                // catatan kecil.
                Text(
                    text = "Nomor ini nomor karyawan — cari nomor konsumen yang sebenarnya " +
                        "sebelum mengirim undangan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (bolehKirim(baris)) {
                Spacer(modifier = Modifier.height(10.dp))
                if (tertunda) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        nomorWa(baris.hp)?.let { nomor ->
                            OutlinedButton(onClick = { onChat(nomor) }) {
                                Icon(
                                    Icons.Rounded.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chat WA")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = onSimpanUlang,
                            enabled = !mengunggah,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (mengunggah) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // "Simpan ulang" = fotonya SUDAH terunggah, yang
                                // gagal cuma pencatatannya. Memotret ulang di
                                // keadaan itu menambah berkas tanpa induk di server.
                                Text("Simpan ulang")
                            }
                        }
                    }
                } else {
                    // Chat WA di barisnya sendiri (ukuran alami, seperti semula),
                    // Kamera/Galeri di baris terpisah di bawahnya — tiga tombol
                    // berebut satu baris membuat Kamera/Galeri kehabisan lebar
                    // sampai tulisannya melipat per-huruf (pil jadi tinggi
                    // memanjang, bukan lebar).
                    nomorWa(baris.hp)?.let { nomor ->
                        OutlinedButton(onClick = { onChat(nomor) }) {
                            Icon(
                                Icons.Rounded.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chat WA")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Kamera ATAU galeri — keduanya bermuara ke berkas cache
                        // yang sama (lihat `fileFoto` di KuponGebyarScreen)
                        // supaya jalur unggahnya cuma satu implementasi.
                        Button(
                            onClick = onKirimKamera,
                            enabled = !mengunggah,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (mengunggah) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kamera")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onKirimGaleri,
                            enabled = !mengunggah,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Galeri")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Buka WhatsApp ke [nomor] (format `62…`, sudah divalidasi [nomorWa]).
 *
 * Tanpa teks siap-pakai: isi undangan berbeda per cabang dan per konsumen, dan
 * pesan yang dikarang app akan terkirim apa adanya oleh karyawan yang buru-buru.
 * Kalau WhatsApp tak terpasang, katakan begitu — jangan diam.
 */
private fun bukaWhatsApp(context: android.content.Context, nomor: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$nomor"))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "WhatsApp tidak terpasang di HP ini.", Toast.LENGTH_SHORT).show()
    }
}

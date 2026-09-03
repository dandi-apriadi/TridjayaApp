package com.krisoft.tridjayaelektronik.ui.deliveryflow

import android.widget.Toast
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.krisoft.tridjayaelektronik.data.model.formatWaktuId
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.KontributorDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.KontrolSaringan
import com.krisoft.tridjayaelektronik.data.model.SaringanAntrian
import com.krisoft.tridjayaelektronik.data.model.indikatorTerpotong
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import com.krisoft.tridjayaelektronik.data.model.parseTimestampMillis
import com.krisoft.tridjayaelektronik.ui.home.formatRupiahShort
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.MoneyTextField
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import com.krisoft.tridjayaelektronik.util.PESAN_KAMERA_TAK_TERSIMPAN
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Meta status ──────────────────────────────────────────────────────────────

private fun statusMeta(status: String): Pair<String, Color> = when (status) {
    DeliveryStatusKey.PENDING_DISCOUNT -> "Tunggu Diskon" to Color(0xFFB5670C)
    DeliveryStatusKey.PENDING_PDI -> "Antri PDI" to Color(0xFF6941C6)
    DeliveryStatusKey.PENDING_PERBAIKAN -> "Ditahan — Perbaikan" to Color(0xFFF04438)
    DeliveryStatusKey.PENDING_SPK -> "Antri Kasir" to Color(0xFF0086C9)
    DeliveryStatusKey.PENDING_DELIVERY_NOTE -> "Surat Jalan" to Color(0xFF0E9384)
    DeliveryStatusKey.PENDING_SCHEDULING -> "Penjadwalan" to Color(0xFF0E9384)
    DeliveryStatusKey.ASSIGNED -> "Siap Berangkat" to Color(0xFF1565C0)
    DeliveryStatusKey.IN_TRANSIT -> "Dalam Perjalanan" to Color(0xFF1E63E9)
    DeliveryStatusKey.DELIVERED -> "Terkirim" to Color(0xFF12B76A)
    DeliveryStatusKey.CANCELLED -> "Batal" to Color(0xFFF04438)
    else -> status to Color(0xFF667085)
}

// ── Klaim PDI (111) ──────────────────────────────────────────────────────────

/** Keadaan klaim PDI dari sudut pandang SATU penonton. */
internal enum class PdiClaimView {
    /** Server belum kenal klaim (atau konteks gagal dimuat) — alur PDI lama persis. */
    TAK_DIDUKUNG,
    BELUM_DIKLAIM,
    MILIK_SAYA,
    MILIK_ORANG_LAIN,

    /**
     * Ada nama pengklaim TAPI pegangannya sudah lewat batas waktu — server sudah
     * membebaskan unitnya. Bukan [MILIK_ORANG_LAIN]: siapa pun boleh mengambil
     * dan mengerjakannya sekarang.
     */
    KEDALUWARSA,
}

/**
 * Apakah klaim sudah lewat batas waktu — CERMINAN klausa `WHERE` `claim_pdi`
 * backend: `pdi_claimed_at < NOW() - INTERVAL <ttl> HOUR`.
 *
 * Kenapa app perlu menghitungnya sendiri: server mengevaluasi batas itu HANYA
 * saat ada yang mencoba mengklaim ulang — tak ada worker pembersih, jadi kolom
 * `pdiClaimedBy` menyimpan nama pengklaim lama SELAMANYA. App yang cuma membaca
 * kolom itu menutup form PDI atas nama orang yang berhenti mengerjakannya
 * berhari-hari lalu, sambil menjanjikan "klaimnya lepas sendiri setelah N jam"
 * — janji yang tak pernah terlihat ditepati. Terukur di produksi 2026-08-15:
 * 59 unit di 9 cabang memegang klaim lewat batas (tertua 371 jam ≈ 15 hari)
 * sementara hanya SATU klaim yang benar-benar masih hidup.
 *
 * Dua keadaan sengaja TIDAK divonis bebas, keduanya meniru server: batas waktu
 * tak diketahui (konteks belum/gagal dimuat) dan stempel waktu hilang/tak
 * terbaca (`pdi_claimed_at IS NULL` juga bukan kedaluwarsa di SQL-nya).
 *
 * Salah vonis aman di dua arah: mengira bebas padahal belum → server menjawab
 * 409 berisi nama pemegangnya; mengira masih dipegang padahal sudah bebas →
 * persis perilaku sebelum fungsi ini ada.
 *
 * `java.time` HARAM di modul ini (minSdk 24 tanpa desugaring) — parsing lewat
 * [parseTimestampMillis] yang sudah ada (`SimpleDateFormat`), bukan util baru.
 */
internal fun klaimKedaluwarsa(
    pdiClaimedAt: String?,
    ttlJam: Int?,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    if (ttlJam == null || ttlJam <= 0) return false
    val mulai = parseTimestampMillis(pdiClaimedAt?.trim()?.takeIf { it.isNotEmpty() }?.replace(' ', 'T'))
        ?: return false
    return nowMs - mulai > ttlJam * 3_600_000L
}

/**
 * Aturan tampilan klaim PDI — fungsi MURNI supaya empat keadaannya bisa diuji
 * tanpa Compose/jaringan.
 *
 * `pdiClaimedBy` kosong punya DUA arti dan bedanya penting: server yang sudah
 * kenal fitur ini selalu mengirim `pdiClaimTtlHours` di `/delivery/context`,
 * jadi ketiadaan TTL berarti "jangan tawarkan apa pun" — bukan "belum
 * diklaim". Tanpa pembedaan itu, APK ini akan menawarkan "Ambil PDI" ke server
 * lama yang pasti menjawab 404/405, atau (lebih buruk) ke server yang
 * konteksnya sedang gagal dimuat. Klaim SENGAJA opsional di server, jadi
 * keadaan [TAK_DIDUKUNG] tak pernah boleh memblokir apa pun.
 *
 * @param serverSupportsClaim `pdiClaimTtlHours != null`. Hanya membedakan
 *   [BELUM_DIKLAIM] vs [TAK_DIDUKUNG] — daftar antrian yang cuma menampilkan
 *   label (tak menawarkan tombol ambil) boleh membiarkannya `false`.
 */
internal fun pdiClaimView(
    pdiClaimedBy: String?,
    currentUserId: String,
    serverSupportsClaim: Boolean = false,
    pdiClaimedAt: String? = null,
    ttlJam: Int? = null,
    nowMs: Long = System.currentTimeMillis(),
): PdiClaimView = when {
    pdiClaimedBy.isNullOrBlank() -> if (serverSupportsClaim) PdiClaimView.BELUM_DIKLAIM else PdiClaimView.TAK_DIDUKUNG
    // Batas waktu diperiksa SEBELUM identitas: klaim milik sendiri yang sudah
    // lewat batas juga sudah lepas di server, jadi menampilkan "Lepas Klaim"
    // untuk klaim yang tak lagi dipegang siapa pun cuma menyesatkan.
    klaimKedaluwarsa(pdiClaimedAt, ttlJam, nowMs) -> PdiClaimView.KEDALUWARSA
    pdiClaimedBy == currentUserId && currentUserId.isNotBlank() -> PdiClaimView.MILIK_SAYA
    else -> PdiClaimView.MILIK_ORANG_LAIN
}

private fun namaPengklaim(claimedByName: String?): String =
    claimedByName?.trim()?.ifBlank { null } ?: "petugas lain"

/** Label klaim (kartu antrian & detail); `null` = tak ada klaim, tak ada label. */
internal fun pdiClaimLabel(view: PdiClaimView, claimedByName: String?): String? = when (view) {
    PdiClaimView.MILIK_SAYA -> "Kamu sedang memproses"
    // Nama BISA kosong (job lama / nama aktor tak terekam) — jangan menampilkan
    // "Diproses oleh " menggantung.
    PdiClaimView.MILIK_ORANG_LAIN -> "Diproses oleh ${namaPengklaim(claimedByName)}"
    PdiClaimView.KEDALUWARSA -> "Pernah diambil ${namaPengklaim(claimedByName)}, belum selesai"
    else -> null
}

/**
 * Kalimat penjelas di bawah label: APA keadaannya, KENAPA, dan APA langkah
 * berikutnya bagi yang membaca. `null` = keadaan biasa, tak perlu kalimat.
 *
 * Dipisah dari [pdiClaimLabel] supaya bisa diuji tanpa Compose — label itu
 * judul, ini yang menghentikan orang menebak.
 */
internal fun pdiClaimKeterangan(
    view: PdiClaimView,
    claimedByName: String?,
    ttlJam: Int?,
    bolehKlaim: Boolean,
): String? = when (view) {
    PdiClaimView.KEDALUWARSA -> {
        val batas = ttlJam?.let { " Batas memegang unit $it jam." } ?: ""
        val langkah = if (bolehKlaim) " Unit ini sudah bebas — tekan \"Ambil PDI\" lalu kerjakan."
        else " Unit ini sudah bebas dan bisa dikerjakan petugas PDI mana pun."
        "${namaPengklaim(claimedByName)} mengambil unit ini tapi belum menyelesaikannya.$batas$langkah"
    }
    PdiClaimView.MILIK_ORANG_LAIN ->
        "Unit ini sedang dikerjakan petugas lain, jadi form PDI-nya ditutup di sini." +
            (ttlJam?.let { " Kalau tidak dilanjutkan, unit bebas sendiri setelah $it jam sejak diambil; admin atau manajer bisa melepasnya lebih cepat." } ?: "")
    else -> null
}

private fun rupiah(v: Double?): String = "Rp" + ribuan(v)

@Composable
private fun StatusChip(status: String) {
    val (label, color) = statusMeta(status)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        // Jarak label→nilai HARUS dari padding, BUKAN dari `SpaceBetween`.
        // `SpaceBetween` cuma membagikan ruang SISA: begitu nilainya cukup
        // panjang untuk membungkus (mis. alasan diskon), sisanya nol dan
        // celahnya ikut nol — labelnya menempel ke nilainya dan terbaca sebagai
        // kerusakan ("AlasanKonsumen ambil 2 unit sekaligus…"). Terlihat di
        // screenshot HP; cuma baris bernilai multi-baris yang kena, itu sebabnya
        // ia lama tak ketahuan. `weight(fill = false)` mengurung nilainya di
        // ruang sisa supaya labelnya tak pernah terdorong keluar.
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.End,
        )
    }
}

// Tujuan pengambilan aki — WAJIB salah satu slug enum backend (aki.rs TUJUAN_VALID).
private val AKI_TUJUAN_OPTIONS = listOf(
    "pemasangan_unit_baru" to "Pemasangan unit baru",
    "penggantian_garansi" to "Penggantian garansi",
    "service_repair" to "Service / repair",
    "display" to "Display",
    "lainnya" to "Lainnya…",
)
internal fun akiTujuanLabel(slug: String?): String =
    AKI_TUJUAN_OPTIONS.firstOrNull { it.first == slug }?.second ?: (slug ?: "-")

// Merk aki dari data BATERAI GS (erp_mirror_stok, kategori BATERAI) — merk nyata yang dipakai.
// "Lainnya…" = ketik manual (item aki merk baru yang belum ada di daftar).
private const val AKI_MERK_LAINNYA = "__lainnya__"
private val AKI_MERK_OPTIONS = listOf(
    "GODA", "EXOTIC", "SAIGE", "AVIATOR", "CHILWEE", "SELIS",
    "U-WINFLY", "DUBBS", "PACIFIC", "AIMA", "SOLOS", "QUEEN",
)
// Kapasitas umum dari nama barang BATERAI GS (tegangan×kapasitas).
private val AKI_KAPASITAS_OPTIONS = listOf("36V12AH", "48V12AH", "48V20AH")
// 1 set baterai sepeda listrik = 4 pcs fisik (48V pack = 4× baterai 12V).
private const val AKI_PCS_PER_SET = 4

// ── Antrian per-tahap ────────────────────────────────────────────────────────

@Composable
fun DeliveryQueueScreen(
    title: String,
    status: String?,
    view: String? = null,
    reorderable: Boolean = false,
    /** Sales antar sendiri (2026-07-24): treat aktor sales sbg driver (job self-delivery
     *  miliknya sendiri) — dikirim layar "Tugas Antar", driver asli tak terpengaruh. */
    asDriver: Boolean = false,
    /**
     * Baris chip periode di atas daftar — HANYA Riwayat SPK. Default `false`
     * supaya enam layar pemakai lain tak berubah sama sekali.
     *
     * SENGAJA tak dipasang di antrian kerja per-tahap: isinya pekerjaan yang
     * HARUS dikerjakan, dan menyaringnya ke "hari ini" menyembunyikan tunggakan
     * kemarin tanpa satu pun error — petugas cuma melihat antrian yang lebih
     * pendek lalu menyimpulkan sudah beres.
     */
    periodeFilter: Boolean = false,
    /**
     * Kontrol saringan yang dirender di atas daftar — bendera EKSPLISIT per
     * rute, disetel di `ActivityNavHost`. Default nihil supaya layar yang belum
     * disetel tak berubah sama sekali.
     *
     * TIDAK disimpulkan dari `status`/role: `kodeDealer` diabaikan server di
     * Riwayat PDI dan justru MENGOSONGKAN daftar di Antri Kasir (rantai kasir
     * mengisi `cabang_bayar`, bukan `kode_dealer`, lalu keduanya di-AND). Lihat
     * KDoc `SaringanAntrian`.
     */
    kontrolSaringan: KontrolSaringan = KontrolSaringan.NIHIL,
    /**
     * Baris chip "Semua / Lewat tenggat / Belum 24 jam" di atas daftar —
     * dipasang di **Konfirmasi Pembayaran** (SPK Gantung), layar divisi kasir.
     * Default `false` supaya layar pemakai lain tak berubah.
     *
     * Boleh di sini padahal [periodeFilter] dilarang di antrian kerja, dan
     * alasannya berdiri sendiri: saringan periode menyembunyikan tunggakan LAMA
     * di balik "hari ini", sedangkan saringan ini membelah daftar dengan tenggat
     * yang SAMA seperti kartu Activity dan embernya yang menonjol justru berisi
     * yang tertua. Penjagaannya (chip cuma muncul saat daftarnya bercampur, dan
     * saringan diabaikan saat chip tak muncul) hidup di `GantungFilter.kt`.
     *
     * Hanya masuk akal di tahap yang `deliveredAt`-nya sudah terisi. Menyalakannya
     * di tahap sebelum serah terima menghasilkan satu ember kosong permanen —
     * tak ada galat, chip-nya cuma tak pernah muncul.
     */
    gantungFilter: Boolean = false,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: DeliveryFlowViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var periode by remember { mutableStateOf(PeriodeSpk.HARI_INI) }
    var saringan by remember { mutableStateOf(SaringanAntrian.KOSONG) }
    // Dihitung ulang tiap recomposition (bukan di-`remember`): `muatUlang` di
    // bawah ikut dibuat ulang bersamanya, jadi pull-to-refresh dan tombol
    // coba-lagi selalu membawa periode TERPILIH — bukan rentang yang
    // ter-capture saat komposisi pertama.
    val rentang = if (periodeFilter) rentangPeriode(periode) else RentangTanggal(null, null)
    // `rentang` DAN `saringan` WAJIB ikut jadi kunci; tanpa itu memilih chip /
    // menekan cari tak memuat apa pun, dan pull-to-refresh menjatuhkan pilihan
    // yang sedang aktif.
    LaunchedEffect(status, view, rentang, saringan) {
        viewModel.loadQueue(status, view, asDriver, rentang.dari, rentang.sampai, saringan)
    }
    val muatUlang = { viewModel.loadQueue(status, view, asDriver, rentang.dari, rentang.sampai, saringan) }

    // ── Aksi level-SPK (2026-08-06) ──────────────────────────────────────────
    // Backend mem-FAN-OUT surat jalan, penugasan driver, konfirmasi kasir,
    // klaim PDI, dan PDI barang kecil ke SELURUH unit satu SPK. Antrian ini
    // dulu murni daftar unit, jadi petugas menekan tombol yang sama N kali
    // untuk pekerjaan yang server sudah selesaikan pada tekanan PERTAMA —
    // panggilan ke-2 dst dijawab 400 "sudah tidak di tahap ini" dan terbaca
    // sebagai kegagalan. Sekarang unit dikelompokkan per SPK dan tombolnya
    // hidup di kepala grup.
    val groups = remember(state.items) { groupJobsBySpk(state.items) }
    // Batas berlaku klaim PDI. `loadQueue` sudah menarik konteksnya (fail-soft),
    // jadi `null` di sini = konteks belum/gagal termuat = jangan pernah memvonis
    // sebuah klaim kedaluwarsa.
    val ttlKlaimJam = state.deliveryContext?.pdiClaimTtlHours

    // Saringan umur untuk antrian SPK Gantung. `SEMUA` sebagai default DISENGAJA:
    // antrian kerja tak boleh memulai hidupnya dalam keadaan tersaring — petugas
    // yang membuka layar dan langsung melihat daftar pendek tak punya cara tahu
    // ada tumpukan lain.
    //
    // Jamnya dibaca SEKALI per daftar yang termuat (`remember(state.items)`),
    // bukan tiap recomposition: pembelah embernya adalah waktu, jadi membaca
    // `System.currentTimeMillis()` langsung di badan komposisi membuat sebuah SPK
    // bisa berpindah ember di tengah guliran — tanpa satu pun perbuatan petugas.
    // Tiap muat ulang menghasilkan `state.items` baru, jadi jamnya ikut segar.
    var saringGantung by remember { mutableStateOf(GantungSaring.SEMUA) }
    val nowGantung = remember(state.items) { System.currentTimeMillis() }
    val hasilGantung = if (gantungFilter) {
        saringPerGantung(groups, nowGantung, saringGantung)
    } else {
        null
    }
    val groupsTampil = hasilGantung?.terlihat ?: groups


    val terbitkanLangsung = status == DeliveryStatusKey.PENDING_DELIVERY_NOTE && viewModel.access.note

    var terbitkanGrup by remember { mutableStateOf<SpkBatchGroup?>(null) }
    terbitkanGrup?.let { grup ->
        TerbitkanSuratJalanDialog(
            grup = grup,
            submitting = state.submitting,
            onDismiss = { terbitkanGrup = null },
            onSubmit = { cabang ->
                // Anchor = unit pertama grup; server menyeret sisanya.
                viewModel.issueDeliveryNote(grup.jobs.first().id, cabang) {
                    terbitkanGrup = null
                    muatUlang()
                }
            },
        )
    }


    TridjayaCollapsibleHeader(title = title, onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && state.items.isNotEmpty(),
            onRefresh = muatUlang,
            modifier = contentModifier,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
              // Baris chip DI LUAR `when` di bawah — kalau ia ikut hilang saat
              // daftar kosong/gagal/loading, orang yang menyaring ke "Hari ini"
              // lalu mendapat nol hasil tak punya jalan kembali ke "Semua" dan
              // membacanya sebagai data yang hilang.
              if (periodeFilter) PeriodeFilterRow(dipilih = periode, onPilih = { periode = it })
              // Alasan yang sama dengan baris chip di atas: bilah saringan DI
              // LUAR `when`, supaya orang yang menyaring ke satu cabang lalu
              // mendapat nol hasil masih punya jalan kembali.
              SaringanAntrianBar(
                  kontrol = kontrolSaringan,
                  saringan = saringan,
                  onUbah = { saringan = it },
              )
              // Sama seperti baris di atas: DI LUAR `when`, supaya kasir yang
              // menyaring ke satu ember lalu mengosongkannya tetap punya jalan
              // kembali ke "Semua".
              if (hasilGantung?.tampilkanChip == true) {
                  GantungFilterRow(dipilih = saringGantung, hasil = hasilGantung, onPilih = { saringGantung = it })
              }
              // "Menampilkan N dari M" — diam kalau daftarnya utuh ATAU kalau
              // server belum mengirim `total` (APK baru di atas server lama).
              IndikatorTerpotongRow(
                  indikatorTerpotong(
                      ditampilkan = state.items.size,
                      total = state.totalAntrian,
                  )
              )
              Box(modifier = Modifier.weight(1f)) {
                when {
                state.loading && state.items.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.items.isEmpty() ->
                    ScrollableCenter {
                        // `muatUlang`, bukan `loadQueue(status, view)`: pemanggilan
                        // pendek itu menjatuhkan `asDriver` DAN rentang periodenya.
                        ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = muatUlang)
                    }
                // `groupsTampil.isEmpty()` ikut jadi syarat: saringan gantung
                // MURNI klien (`loadQueue` tak menyaringnya), jadi `state.items`
                // bisa penuh sementara embernya sendiri kosong — tanpa baris ini
                // kasir yang memilih "Belum 24 jam" pada hari semuanya sudah
                // lewat tenggat melihat daftar kosong TANPA pesan sama sekali.
                state.items.isEmpty() || groupsTampil.isEmpty() ->
                    ScrollableCenter {
                        ExpressiveEmptyState(
                            icon = { Icon(Icons.Rounded.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                            title = "Antrian kosong",
                            // Kosong karena disaring ≠ kosong karena tak ada datanya.
                            // Urutan ini disengaja: saringan yang BARU SAJA
                            // ditekan orangnya lebih menjelaskan hasil nol
                            // daripada periode yang mungkin sudah lama dipilih.
                            subtitle = when {
                                saringan.adaYangAktif ->
                                    "Tidak ada yang cocok dengan saringan di atas. Hapus pencarian atau pilih \"Semua cabang\"."
                                hasilGantung?.tampilkanChip == true && groupsTampil.isEmpty() ->
                                    "Tidak ada yang cocok dengan saringan umur di atas. Pilih \"Semua\" untuk melihat semuanya."
                                periodeFilter ->
                                    "Tidak ada SPK pada periode ini (${periode.keterangan}). Ganti periode di atas."
                                else -> "Belum ada job pada tahap ini."
                            }
                        )
                    }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    state.actionError?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // SELURUH antrian kini SATU KARTU PER SPK (permintaan user
                        // 2026-08-06): PDI, kasir, surat jalan, penjadwalan, driver,
                        // konfirmasi pembayaran, riwayat. Alasannya sama di semua
                        // tahap - satu SPK adalah satu penjualan, satu konsumen,
                        // satu alamat; memajangnya sebagai N baris membuat petugas
                        // mengira ada N pekerjaan, dan sejak server mem-fan-out-kan
                        // hampir semua endpoint tahap, N-1 di antaranya memang
                        // pekerjaan hantu. Rincian per unit hidup di layar detail,
                        // yang kini memuat seluruh unit SPK.
                        if (reorderable) {
                            // Manifest driver: kartu SPK digeser sebagai SATU BLOK.
                            // Kontrak server tak berubah (tetap daftar id unit) -
                            // lihat `moveLoadSpk`, yang meratakan grup jadi urutan
                            // id. Justru inilah yang menjamin unit satu SPK selalu
                            // berdampingan; penggeseran per unit yang lama tidak.
                            itemsIndexed(groupsTampil, key = { _, g -> g.kode }) { index, grup ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SpkRingkasCard(grup, viewModel.currentUserId, ttlKlaimJam) { onOpen(grup.jobs.first().id) }
                                    }
                                    Column {
                                        IconButton(onClick = { viewModel.moveLoadSpk(grup.kode, up = true) }, enabled = index > 0) {
                                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Naikkan urutan")
                                        }
                                        IconButton(onClick = { viewModel.moveLoadSpk(grup.kode, up = false) }, enabled = index < groupsTampil.size - 1) {
                                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Turunkan urutan")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(groupsTampil, key = { it.kode }) { grup ->
                                SpkRingkasCard(
                                    grup = grup,
                                    currentUserId = viewModel.currentUserId,
                                    ttlKlaimJam = ttlKlaimJam,
                                    // Tombol tahap jadi KAKI kartu, bukan tombol
                                    // mengambang di bawahnya. Tahap tanpa aksi
                                    // level-SPK tak mengirim slot ini sama sekali.
                                    // HANYA surat jalan yang menaruh tombol di
                                    // kartu. Antrian PDI mengikuti bentuk antrian
                                    // kasir (permintaan user 2026-08-06): kartu
                                    // polos, ketuk untuk masuk detail, dan seluruh
                                    // tombolnya - Ambil PDI, PDI massal barang
                                    // kecil, formulir per unit - hidup di sana.
                                    aksi = if (terbitkanLangsung) {
                                        {
                                            ExpressiveFilledButton(
                                                onClick = { terbitkanGrup = grup },
                                                enabled = !state.submitting,
                                                modifier = Modifier.weight(1f),
                                            ) { Text("Terbitkan Surat Jalan") }
                                        }
                                    } else null,
                                ) { onOpen(grup.jobs.first().id) }
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

/**
 * SATU kartu untuk satu SPK — dipakai antrian yang pekerjaannya memang per SPK
 * (kasir). Ketuk = buka detail SPK-nya lewat unit pertama; rincian tiap unit
 * dan tombol tahapnya hidup di sana.
 *
 * Kartu ini mewakili satu
 * PENJUALAN. Kasir menyalinnya ke GS sebagai satu transaksi satu nomor, jadi
 * menampilkan N baris untuk satu penjualan membuatnya mengira ada N pekerjaan
 * — persis keluhan yang memicu fan-out di server.
 */
@Composable
private fun SpkRingkasCard(
    grup: SpkBatchGroup,
    currentUserId: String = "",
    /** `pdiClaimTtlHours` dari `/delivery/context`; `null` = batas tak diketahui
     *  → klaim tak pernah divonis kedaluwarsa (lihat [klaimKedaluwarsa]). */
    ttlKlaimJam: Int? = null,
    /**
     * Tombol tahap, dirender DI DALAM kartu sebagai kaki. Sebelumnya ia
     * mengambang di bawah kartu, sehingga tiap baris antrian terbaca sebagai
     * dua benda — kartu, lalu tombol yatim yang tak jelas milik SPK yang mana.
     */
    aksi: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val anchor = grup.jobs.first()
    val n = grup.jobs.size
    ClayCard(modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.fillMaxWidth()) {
        // `clickable` DI BARIS ISI, bukan di seluruh kartu: kalau kartunya yang
        // diberi klik, menekan tombol di kaki ikut membuka detail — dua aksi
        // untuk satu ketukan.
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        grup.kode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // Satu chip HANYA kalau seluruh barang memang sestatus.
                    // Kalau tidak, chip anchor itu bohong — dan menggantinya
                    // dengan peringatan "status beda tiap barang" cuma
                    // memindahkan kebohongan jadi teka-teki: orang tetap tak
                    // tahu bedanya apa tanpa membuka SPK-nya. Yang dipajang
                    // sekarang komposisinya sendiri (mis. "Terkirim 2",
                    // "Antri PDI 1"), jadi jawabannya ada di kartu.
                    if (grup.jobs.map { it.status }.distinct().size <= 1) {
                        StatusChip(anchor.status)
                    }
                }
                val perStatus = grup.jobs.groupingBy { it.status }.eachCount()
                if (perStatus.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // `Row` biasa, bukan `FlowRow`: satu SPK praktis tak pernah
                    // punya lebih dari 2-3 status sekaligus, jadi tak perlu
                    // menarik API eksperimental hanya untuk pembungkusan yang
                    // takkan terjadi.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Urut mengikuti kemunculan di daftar, bukan abjad:
                        // daftar sudah diurut server (terbaru dulu).
                        perStatus.forEach { (status, jumlah) ->
                            val (label, warna) = statusMeta(status)
                            Surface(color = warna.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                                Text(
                                    "$label $jumlah", color = warna,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    anchor.customerName ?: "-", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Semua barangnya disebut, bukan cuma yang pertama + "dst":
                // kasir memakai daftar ini untuk mencocokkan dengan penjualan
                // yang sedang dia ketik di GS.
                grup.jobs.forEach { j ->
                    Text(
                        "• ${j.namaBarang ?: j.kodeBarang ?: "-"}${j.tipe?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                        Text(
                            if (n > 1) "$n unit · 1 transaksi GS" else "1 unit",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    // Vonis "menggantung" MILIK SERVER (`mandek`/`eskalasi`,
                    // `DeliveryJobDto.umurTahapJam`) — app menampilkan apa adanya,
                    // tidak menghitung ulang dari `createdAt`. Dinilai atas
                    // SELURUH grup: satu unit yang mandek sudah cukup membuat
                    // SPK-nya butuh tindakan, dan jam yang dipajang adalah yang
                    // TERLAMA supaya petugas tidak diberi kesan lebih baik dari
                    // kenyataan.
                    val jobMandek = grup.jobs.filter { it.mandek }
                    if (jobMandek.isNotEmpty()) {
                        val jamTerlama = jobMandek.mapNotNull { it.umurTahapJam }.maxOrNull()
                        val adaEskalasi = jobMandek.any { it.eskalasi }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (jamTerlama != null) "Menggantung ${jamTerlama}j" else "Menggantung",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (adaEskalasi) MaterialTheme.colorScheme.error else Color(0xFFB5670C),
                        )
                    }
                    // Penanda per-unit yang dulu hidup di kartu per-unit ikut naik ke
                    // kartu SPK — sejak antrian tak lagi memajang kartu per
                    // unit, tanpa ini informasinya HILANG, bukan cuma pindah.
                    // Dinilai atas SELURUH grup (`any`), karena satu barang
                    // ber-COD sudah cukup membuat SPK-nya perlu diperlakukan
                    // sebagai COD.
                    if (grup.jobs.any { it.pdiRequired == false }) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "PDI Mandiri", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFB5670C),
                        )
                    }
                    when {
                        grup.jobs.any { it.deliveryMethod == "self_pickup" } -> {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Diambil Sendiri", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFF0E9384),
                            )
                        }
                        grup.jobs.any { it.deliveryMethod == "sales_delivery" } -> {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Sales Antar", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFF1565C0),
                            )
                        }
                    }
                    if (grup.jobs.any { it.driverTerimaUang == true }) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "COD", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFF9E4B00),
                        )
                    }
                    // Lokasi pembayaran (2026-08-12) — HANYA kalau bayarnya
                    // BUKAN di cabang stok. Di SPK biasa badge ini cuma mengulang
                    // kolom "Cabang" yang sudah ada di kartu, dan badge yang
                    // selalu menyala berhenti dibaca. Dinilai atas anchor: lokasi
                    // bayar milik SPK, bukan milik barang.
                    badgeBayarDiLuarCabangStok(anchor)?.let { nama ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Bayar di $nama", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            // Badge terpanjang di baris ini (nama cabang, bukan
                            // kata pendek seperti "COD"). Tanpa `weight` ia
                            // diukur pada lebar intrinsiknya lalu TERPOTONG di
                            // tepi Row tanpa elipsis — terbaca sebagai teks
                            // rusak. `fill = false` menjaga badge pendek tetap
                            // rapat ke kiri saat ruangnya cukup.
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                // Klaim PDI dinilai se-SPK, sejalan dengan servernya: `claim-pdi`
                // fan-out mengunci SELURUH unit ke satu petugas. "Milik saya"
                // menang atas "milik orang lain" bila SPK terlanjur terbelah
                // (unit yang sudah dipegang orang lain memang DILEWATI server,
                // bukan direbut) — yang perlu diketahui petugas adalah bahwa dia
                // punya pekerjaan di sini, bukan bahwa ada yang tidak.
                //
                // Klaim yang sudah LEWAT BATAS WAKTU tidak lagi dihitung sebagai
                // "dipegang orang" — server sudah membebaskannya, dan label lama
                // ("Diproses oleh X") membuat unit yang ditinggalkan berhari-hari
                // terlihat persis seperti unit yang sedang dikerjakan.
                val hidup = grup.jobs.filterNot { klaimKedaluwarsa(it.pdiClaimedAt, ttlKlaimJam) }
                val klaimSaya = currentUserId.isNotBlank() && hidup.any { it.pdiClaimedBy == currentUserId }
                val klaimOrangLain = hidup.firstOrNull {
                    !it.pdiClaimedBy.isNullOrBlank() && it.pdiClaimedBy != currentUserId
                }
                val klaimTerbengkalai = grup.jobs.firstOrNull {
                    !it.pdiClaimedBy.isNullOrBlank() && klaimKedaluwarsa(it.pdiClaimedAt, ttlKlaimJam)
                }
                when {
                    klaimSaya -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Kamu sedang memproses", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFF12B76A),
                        )
                    }
                    klaimOrangLain != null -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Diproses oleh ${klaimOrangLain.pdiClaimedByName?.trim()?.ifBlank { null } ?: "petugas lain"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFB5670C),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    klaimTerbengkalai != null -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            pdiClaimLabel(PdiClaimView.KEDALUWARSA, klaimTerbengkalai.pdiClaimedByName)
                                + " — bebas diambil",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFB5670C),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        aksi?.let {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = it,
            )
        }
      }
    }
}

/**
 * Dialog terbit surat jalan dari daftar antrian — isian sama dengan aksi di
 * layar detail (`DeliveryNoteAction`), cuma dibungkus dialog supaya DC tak
 * perlu keluar-masuk detail satu per satu.
 *
 * Sejak 2026-08-05 endpoint-nya fan-out se-SPK: SATU nomor surat jalan untuk
 * seluruh unit, karena dokumen fisiknya memang satu lembar untuk satu
 * pengiriman.
 */
@Composable
private fun TerbitkanSuratJalanDialog(
    grup: SpkBatchGroup,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val anchor = grup.jobs.first()
    var cabang by remember(grup.kode) { mutableStateOf(anchor.kodeDealer.orEmpty()) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Terbitkan Surat Jalan") },
        text = {
            Column {
                Text("SPK ${grup.kode}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                grup.jobs.forEach { j ->
                    Text(
                        "• ${j.namaBarang ?: j.kodeBarang ?: "-"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (grup.jobs.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Satu nomor surat jalan untuk ${grup.jobs.size} unit — satu pengiriman, satu lembar.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(cabang, { cabang = it }, label = "Cabang sumber unit (wajib)", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(cabang) }, enabled = !submitting && cabang.trim().isNotEmpty()) {
                Text(if (submitting) "Menerbitkan…" else "Terbitkan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Batal") } },
    )
}

/**
 * Daftar barang satu SPK, dirender DI DALAM kartu identitas SPK pada layar
 * detail (bukan di area aksi).
 *
 * SENGAJA TIDAK menampilkan `kodePengiriman` per unit maupun penanda "unit mana
 * yang diketuk dari antrian". Keduanya sempat ada dan dibuang atas masukan user
 * (2026-08-06), dengan alasan yang berlaku seterusnya:
 * - Kode unit dalam satu SPK berawalan SAMA (`DLV-Mxxxxxxxx-`), yang beda cuma
 *   akhiran `-1u1`/`-2u1`. Memajangnya per baris = mengulang kode SPK yang
 *   sudah tertulis di kepala kartu, N kali.
 * - "Lewat unit mana layar ini dibuka" adalah artefak navigasi (detail memuat
 *   satu id), bukan informasi yang dipakai kasir. Yang dia lihat SPK-nya.
 */
@Composable
private fun SpkUnitList(units: List<DeliveryJobDto>) {
    units.forEachIndexed { i, u ->
        Spacer(Modifier.height(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    // Nomor urut, bukan kode unit: menjawab "barang ke berapa"
                    // tanpa mengulang kode SPK yang sudah ada di kepala kartu.
                    Text(
                        "${i + 1}.", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${u.namaBarang ?: u.kodeBarang ?: "-"}${u.tipe?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        // Merk/warna, serial, dan No PO adalah milik UNIT, bukan
                        // milik SPK — dulu dipajang sekali di kepala kartu dari
                        // unit yang kebetulan dibuka, sehingga SPK banyak barang
                        // memperlihatkan serial satu unit seolah berlaku semua.
                        listOfNotNull(
                            listOfNotNull(u.merk, u.warna).joinToString(" · ").ifBlank { null },
                            u.preOrderId?.takeIf { it.isNotBlank() }?.let { "PO $it" },
                        ).joinToString(" · ").ifBlank { null }?.let {
                            Text(
                                it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // SN punya BARIS SENDIRI, bukan digabung ke baris
                        // merk/warna/PO. Alasannya bukan estetika: baris gabungan
                        // itu ber-`maxLines = 2` di kolom sempit, jadi merk/warna
                        // yang panjang MEMOTONG SN-nya lewat ellipsis — nomor
                        // yang justru dicocokkan dengan unit fisik hilang tanpa
                        // jejak. Selalu dirender (nilai atau "—") supaya "unit ini
                        // belum ber-SN" tak lagi terlihat sama dengan "SN-nya
                        // kepotong". SN memang OPSIONAL sejak 2026-07-23 —
                        // kosong bukan kesalahan, karena itu netral, bukan merah.
                        Text(
                            "SN " + (u.serialNumber?.takeIf { it.isNotBlank() } ?: "—"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (u.serialNumber.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (u.driverTerimaUang == true) {
                            Text(
                                "COD ${if (u.codPaymentMode == "dp") "DP" else "Full"} · tagih " +
                                    (u.driverTerimaNominal?.let { rupiah(it) } ?: "-"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFF9E4B00),
                            )
                        }
                        // Selisih warna SKU vs kolom isian (2026-08-10). Di
                        // baris UNIT, bukan di kepala kartu: tiap unit punya
                        // vonisnya sendiri, dan SPK banyak barang bisa cuma
                        // satu unitnya yang bermasalah.
                        pesanWarnaSelisih(u.warnaSelisih)?.let { w ->
                            Spacer(Modifier.height(4.dp))
                            val warnaTeks =
                                if (w.nada == NadaSelisih.PERINGATAN) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(
                                w.judul,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = warnaTeks,
                            )
                            Text(
                                w.penjelasan,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        u.hargaOtr?.let {
                            Text(rupiah(it), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        u.diskon?.takeIf { it > 0 }?.let {
                            Text(
                                "−${rupiah(it)}", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFFB5670C),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Baris kelengkapan (baterai/charger/kaca spion) di daftar barang SPK.
 *
 * Dibedakan dari baris unit lewat penanda "Kelengkapan" dan tanpa harga:
 * barang-barang ini tidak punya harga OTR sendiri - nilainya sudah termasuk
 * di unit yang menaunginya. Menampilkan kolom harga kosong akan terbaca
 * sebagai data yang hilang, bukan sebagai barang yang memang tak berharga
 * sendiri.
 */
@Composable
private fun SpkKelengkapanList(items: List<KelengkapanUnit>, nomorMulai: Int) {
    items.forEachIndexed { i, k ->
        Spacer(Modifier.height(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    // Nomor MELANJUTKAN nomor unit (permintaan user 2026-08-06:
                    // "ditampilkan seperti unit"), bukan penanda "+" tersendiri.
                    // Bagi konsumen dan petugas, sepeda listrik + baterai +
                    // charger adalah tiga barang yang diserahkan — pembedaannya
                    // urusan internal server, bukan pemandangan mereka.
                    Text(
                        "${nomorMulai + i}.", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${k.label} x${k.qty}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        // Menempati slot yang SAMA dengan baris merk/warna/PO
                        // pada unit, jadi bentuk barisnya tetap sama persis.
                        Text(
                            listOfNotNull("Kelengkapan unit", k.catatan).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Detail + aksi per-tahap ──────────────────────────────────────────────────

@Composable
fun DeliveryJobDetailScreen(id: String, onBack: () -> Unit, viewModel: DeliveryFlowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(id) { viewModel.loadDetail(id) }
    LaunchedEffect(state.actionDone) { if (state.actionDone) onBack() }

    val job = state.detail
    // Judul mengikuti TAHAP, bukan satu nama untuk semua. Di antrian kasir yang
    // sedang dilihat adalah SPK-nya (satu penjualan, satu transaksi GS, bisa
    // banyak barang) — menyebutnya "Detail Pengiriman" salah alamat: belum ada
    // pengiriman apa pun pada tahap itu, barangnya bahkan belum dijadwalkan.
    // Tahap sesudahnya tetap "Detail Pengiriman" karena di sanalah pengiriman
    // benar-benar jadi pokok bahasannya.
    val judul = if (job?.status == DeliveryStatusKey.PENDING_SPK) "Detail SPK" else "Detail Pengiriman"
    TridjayaCollapsibleHeader(title = judul, onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // `refreshDetail`, BUKAN `loadDetail`: yang terakhir mengosongkan foto
        // yang sudah dijepret tapi belum terkirim (lihat VM) — tarikan tak
        // sengaja saat petugas memotret bukti serah terima = bukti hilang.
        TridjayaPullRefresh(
            isRefreshing = state.loading && job != null,
            onRefresh = { viewModel.refreshDetail(id) },
            modifier = contentModifier,
        ) {
            when {
                state.loading && job == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                job == null -> ScrollableCenter {
                    ExpressiveErrorState(message = state.error ?: "Data tidak ditemukan", onRetry = { viewModel.loadDetail(id) })
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom)
                ) {
                    ClayCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(job.kodePengiriman, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                StatusChip(job.status)
                            }
                            // Urutan kartu (permintaan user 2026-08-06): KONSUMEN →
                            // BARANG → TOTAL → PEMBAYARAN → PENGIRIMAN, lalu cabang/
                            // sumber order sebagai ekor. Urutan lamanya campur
                            // (metode pengiriman & PDI nyempil di antara data
                            // konsumen; merk/serial/PO di kepala kartu padahal milik
                            // unit), sehingga SPK banyak barang memperlihatkan serial
                            // SATU unit seolah berlaku untuk semuanya.
                            //
                            // `unitSpk` = seluruh unit SPK bila termuat, kalau tidak
                            // unit yang dibuka saja. Jadi tata letaknya sama persis
                            // untuk SPK satu barang maupun banyak barang.
                            val unitSpk = state.batchUnits.ifEmpty { listOf(job) }

                            // ── 1. KONSUMEN ──────────────────────────────────────
                            Spacer(Modifier.height(6.dp))
                            Text(job.customerName ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            // Urutan: nama → HP → NIK → alamat (permintaan user
                            // 2026-08-06). Alamat sengaja PALING BAWAH di blok ini:
                            // ia satu-satunya nilai yang biasanya membungkus
                            // beberapa baris, jadi menaruhnya di tengah memutus
                            // barisan pendek yang enak dipindai di atasnya.
                            InfoLine("No. HP", job.customerPhone)
                            InfoLine("NIK", job.customerNik)
                            InfoLine("Alamat", job.customerAddress)
                            InfoLine("Sosmed", listOfNotNull(
                                job.sosmedTiktok?.let { "TikTok $it" },
                                job.sosmedFacebook?.let { "FB $it" },
                                job.sosmedInstagram?.let { "IG $it" },
                            ).joinToString(" · ").ifBlank { null })

                            // ── 2. LIST BARANG ───────────────────────────────────
                            // Baterai/charger/kaca spion yang IKUT diserahkan
                            // bersama unitnya, diturunkan dari form aki DISETUJUI
                            // (2026-08-06). Di server ia bukan baris
                            // `delivery_jobs` - sengaja, karena baris job berarti
                            // unit fisik ber-antrian PDI, penugasan driver, dan
                            // hitungan kiriman sendiri. Yang berubah cuma cara
                            // membacanya: di daftar barang ia berdiri sebagai
                            // barisnya sendiri, persis barang lain.
                            val kelengkapan = remember(state.batchAkiForms) {
                                kelengkapanDariAkiForms(state.batchAkiForms)
                            }
                            val totalItem = unitSpk.size + kelengkapan.size
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Barang ($totalItem)",
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            SpkUnitList(unitSpk)
                            SpkKelengkapanList(kelengkapan, nomorMulai = unitSpk.size + 1)

                            // ── 3. TOTAL UNIT ────────────────────────────────────
                            // Dijumlah dari unit yang termuat, BUKAN dari `job`
                            // sendirian: kolom harga di baris `delivery_jobs` itu
                            // per unit, jadi menampilkan angka unit yang kebetulan
                            // dibuka sebagai "total SPK" akan mengecilkan nilai
                            // penjualan tanpa terlihat salah.
                            val totalOtr = unitSpk.mapNotNull { it.hargaOtr }.sum()
                            val totalDiskon = unitSpk.mapNotNull { it.diskon }.sum()
                            val totalNilai = unitSpk.mapNotNull { it.hargaTotal }.sum()
                            Spacer(Modifier.height(10.dp))
                            Text("Total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            // Angka gabungan DULU (itu yang dicari: "SPK ini
                            // isinya berapa barang"), rinciannya menyusul. Unit
                            // fisik dan kelengkapan sengaja tetap dibedakan: yang
                            // pertama punya antrian PDI, driver, dan hitungan
                            // kiriman di server; yang kedua tidak. Menyatukannya
                            // jadi satu angka "unit" akan berselisih dengan
                            // statistik pengiriman tanpa ada yang tahu sebabnya.
                            InfoLine(
                                "Jumlah Barang",
                                if (kelengkapan.isEmpty()) "${unitSpk.size} unit"
                                else "$totalItem barang (${unitSpk.size} unit + ${kelengkapan.size} kelengkapan)",
                            )
                            InfoLine("Total OTR", totalOtr.takeIf { it > 0 }?.let { rupiah(it) })
                            InfoLine("Total Diskon", totalDiskon.takeIf { it > 0 }?.let { rupiah(it) })
                            // Angka yang dicari orang lebih dulu dari seluruh kartu.
                            InfoLine("Total Nilai", totalNilai.takeIf { it > 0 }?.let { rupiah(it) })

                            // ── 4. PEMBAYARAN ────────────────────────────────────
                            Spacer(Modifier.height(10.dp))
                            Text("Pembayaran", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            // Cash vs Credit mengubah seluruh cara membaca sisa
                            // kartu (ada/tidaknya fincoy, angsuran, tenor), jadi ia
                            // ditebalkan — bukan sekadar salah satu baris.
                            InfoLine("Metode Bayar", job.paymentType?.replaceFirstChar { it.uppercase() })
                            // "Bayar di", BUKAN "Cabang" — label kedua sudah
                            // dipakai seksi ekor untuk cabang STOK, dan dua baris
                            // bernama mirip yang artinya beda adalah persis cara
                            // uang mendarat di kasir yang salah. Namanya datang
                            // UTUH dari server (`bayarDealerName`); app tak
                            // menurunkannya sendiri.
                            InfoLine("Bayar di", namaCabangBayar(job))
                            InfoLine("No. Transaksi GS", job.noTransaksi)
                            if (job.paymentType == "credit") {
                                InfoLine("Fincoy", job.fincoy)
                                InfoLine("DP Net", job.dpNet?.let { rupiah(it) })
                                InfoLine("Pembayaran 1", job.pembayaran1?.let { rupiah(it) })
                                InfoLine("Angsuran", job.angsuran?.let { rupiah(it) })
                                InfoLine("Tenor", job.tenor?.let { "$it bln" })
                            }
                            // COD (2026-07-25): uang diambil driver saat kirim — cuma ada
                            // kalau ada driver beneran (bukan diambil sendiri/sales antar sendiri).
                            if (job.driverTerimaUang == true) {
                                InfoLine("Metode COD", if (job.codPaymentMode == "dp") "DP" else "Full Payment")
                                if (job.codPaymentMode == "dp") {
                                    InfoLine("DP Rencana (Sales)", job.codDpAmount?.let { rupiah(it) })
                                    InfoLine("DP Diterima Kasir", job.kasirDpDiterima?.let { rupiah(it) })
                                }
                                InfoLine("Sisa Diambil Driver", job.driverTerimaNominal?.let { rupiah(it) })
                                InfoLine("Kasir Konfirmasi Bayar", if (job.kasirKonfirmasiPembayaran) "Sudah" else "Belum")
                                InfoLine("Setoran Driver→Kasir", job.setoranKasirNominal?.let { "${rupiah(it)} · ${job.setoranKasirByNama ?: "-"}" })
                            }

                            // ── 5. PENGIRIMAN ────────────────────────────────────
                            Spacer(Modifier.height(10.dp))
                            Text("Pengiriman", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            // Setara "Metode Bayar" di seksi sebelumnya: nilai yang
                            // menentukan sisa barisnya masuk akal atau tidak (job
                            // "diambil sendiri" tak pernah punya driver & jadwal).
                            InfoLine("Metode Pengiriman", when (job.deliveryMethod) {
                                "self_pickup" -> "Diambil Sendiri"
                                "sales_delivery" -> "Sales Antar Sendiri"
                                else -> "Driver"
                            })
                            // Badge ini menampilkan APA YANG DIPILIH sales/admin saat SPK
                            // dibuat (toggle "Siapa yang mengecek unit" di SpkItemCard) —
                            // TIDAK diubah jadi "Mandiri" begitu saja, supaya pilihan asli
                            // tetap jujur ditampilkan. Tapi untuk self_pickup/sales_delivery,
                            // `is_self_pdi` backend TETAP mengizinkan sales pemilik SPK
                            // mengerjakan sendiri terlepas dari pilihan "Tim PDI cabang" ini
                            // (unitnya memang tak pernah lepas dari tangan sales) — tanpa
                            // catatan ini, badge "PDI (tim PDI)" menyesatkan: sales/admin
                            // mengira ada tim lain yang akan mengerjakan dan menunggu
                            // notifikasi yang bisa jadi tak pernah dikirim/tak ada
                            // penerimanya (cabang tanpa staf PDI), padahal sales sendiri
                            // sudah boleh langsung lanjut ke PDI.
                            val pdiBadge = when {
                                job.pdiRequired == false -> "PDI Mandiri (sales)"
                                job.deliveryMethod == "self_pickup" || job.deliveryMethod == "sales_delivery" ->
                                    "PDI (tim PDI) — sales tetap bisa kerjakan sendiri"
                                else -> "PDI (tim PDI)"
                            }
                            InfoLine("PDI", pdiBadge)
                            InfoLine("Surat Jalan", job.deliveryNoteNo)
                            InfoLine("Driver", job.assignedDriverName)
                            InfoLine("Jadwal", job.scheduledDate?.let(::formatWaktuId))
                            InfoLine("Chat Konsumen", job.consumerChatAt?.let(::formatWaktuId))
                            job.reviewRating?.let { InfoLine("Rating", "★".repeat(it) + (job.reviewComment?.let { c -> " · $c" } ?: "")) }
                            if (job.status == DeliveryStatusKey.CANCELLED) InfoLine("Alasan Batal", job.cancelReason)
                            job.customerMapUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                Spacer(Modifier.height(4.dp))
                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Buka Lokasi Maps") }
                            }

                            // ── Ekor: cabang & asal order ────────────────────────
                            // Di luar lima seksi yang diminta, tapi TIDAK dibuang:
                            // cabang stok menentukan siapa yang memegang barangnya,
                            // dan komisi KBK ikut dibayarkan dari SPK ini.
                            Spacer(Modifier.height(10.dp))
                            Text("Cabang & Asal Order", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            InfoLine("Cabang Stok", job.dealerName)
                            InfoLine("Cabang Asal Sales", job.salesDealerName)
                            InfoLine("Sales", job.salesName)
                            InfoLine("Sumber", when {
                                job.orderSource == "kbk" -> "KBK · ${job.kbkBrokerNama ?: job.kbkBrokerKode ?: "-"}"
                                job.orderSource != null -> "Sales"
                                else -> null
                            })
                            InfoLine("Komisi KBK", job.komisiKbk?.let { rupiah(it) })
                            InfoLine("No. HP KBK", job.noHpKbk)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SpkTimelineCard(job, state.jobDiscounts, state.akiForms)
                    // Foto bukti (PDI siap kirim / serah terima / terima uang) — dimuat
                    // ter-autentikasi via VM (kasir/DC/driver bisa verifikasi dari HP).
                    if (state.jobPhotos.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        JobPhotosCard(state.jobPhotos)
                    }
                    if (state.kontributor.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        KontributorCard(state.kontributor)
                    }
                    // Sunting isi SPK (2026-08-01) — administrator saja, dan hanya
                    // selagi unitnya belum di-PDI + belum tercatat di GS. Kartunya
                    // TIDAK dirender kalau syaratnya tak terpenuhi (bukan
                    // dirender-lalu-dinonaktifkan): tombol mati yang servernya
                    // jawab 400 cuma bikin orang menebak.
                    if (bolehSuntingSpk(job, viewModel.isAdminViewer, viewModel.currentUserId)) {
                        Spacer(Modifier.height(14.dp))
                        EditSpkAction(job, viewModel, state.submitting)
                    }
                    // Lokasi maps punya aksinya SENDIRI (2026-08-30): jendelanya
                    // sampai unit terkirim, jauh lebih lebar dari "Ubah Isi SPK"
                    // yang berhenti sebelum PDI. Driver tak bisa dijadwalkan
                    // tanpa lokasi, jadi menutupnya bersama jendela sunting
                    // berarti unitnya diam sampai ada yang membuka dashboard web.
                    if (bolehIsiMapsSpk(job, viewModel.isAdminViewer, viewModel.currentUserId)) {
                        Spacer(Modifier.height(14.dp))
                        IsiMapsAction(job, viewModel, state.submitting)
                    }
                    Spacer(Modifier.height(14.dp))
                    val shareContext = LocalContext.current
                    ExpressiveOutlinedButton(onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "Lacak pengiriman Anda: " + com.krisoft.tridjayaelektronik.BuildConfig.API_BASE_URL.trimEnd('/') + "/cek-resi/" + job.id)
                        }
                        shareContext.startActivity(android.content.Intent.createChooser(send, "Bagikan resi"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Bagikan Resi")
                    }
                    Spacer(Modifier.height(14.dp))
                    state.actionError?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    // Aksi per-tahap DIGATE role viewer (SpkAccessPolicy — mirror backend):
                    // buka job lewat Riwayat jangan menampilkan tombol yang pasti 403
                    // (mis. driver lihat "Assign Driver"). Backend tetap otoritatif.
                    val access = viewModel.access
                    val isMyDriverJob = viewModel.isAdminViewer ||
                        (access.driverAction && job.assignedDriverId == viewModel.currentUserId)
                    // Self-PDI: sales pemilik SPK boleh PDI unitnya sendiri — "bertanggung
                    // jawab penuh". Syaratnya (mirror `submit_pdi` backend 2026-07-27):
                    // toggle PDI Mandiri (`pdiRequired=false`) ATAU metode diambil-sendiri/
                    // antar-sendiri. `pdiRequired=false` masuk sejak rute skip PDI dibuang —
                    // dulu kombinasi itu tak pernah mampir ke `pending_pdi` sama sekali.
                    // Backend otoritatif, ini murni gate UI (paritas web `PdiDetailPage`).
                    val isSelfPdiJob = (job.pdiRequired == false ||
                        job.deliveryMethod == "self_pickup" || job.deliveryMethod == "sales_delivery") &&
                        !job.salesUserId.isNullOrBlank() && job.salesUserId == viewModel.currentUserId
                    if (job.driverTerimaUang != null && isMyDriverJob &&
                        (job.status == DeliveryStatusKey.ASSIGNED || job.status == DeliveryStatusKey.IN_TRANSIT)
                    ) {
                        ChatConsumerCard(job, viewModel, state.submitting)
                        Spacer(Modifier.height(14.dp))
                    }
                    when {
                        // `pending_perbaikan` ikut: unit tertahan HANYA bisa keluar lewat
                        // PDI ulang di sini (jalur a) — tanpa cabang ini form-nya tak pernah
                        // muncul dan unit terkunci dari sisi app, tanpa error apa pun.
                        (job.status == DeliveryStatusKey.PENDING_PDI || job.status == DeliveryStatusKey.PENDING_PERBAIKAN) && (access.pdi || isSelfPdiJob) ->
                            PdiAction(job, state.batchUnits, viewModel, state.submitting, state.checklist, state.requiresAki, state.akiForms)
                        job.status == DeliveryStatusKey.PENDING_SPK && access.kasir ->
                            KasirConfirmSpkAction(job, state.batchUnits, viewModel, state.submitting)
                        job.status == DeliveryStatusKey.PENDING_DELIVERY_NOTE && access.note ->
                            DeliveryNoteAction(job, viewModel, state.submitting)
                        // Diambil sendiri (2026-07-24): konsumen ambil unit di cabang — TIDAK
                        // lewat assign-driver. Sales pemilik SPK yang serah-terima langsung
                        // (foto+rating wajib, 2026-07-26 — konsisten pola PDI mandiri), DC/admin
                        // tetap bisa (ditambah, bukan dicabut).
                        job.status == DeliveryStatusKey.PENDING_SCHEDULING && (access.jadwal || isSelfPdiJob) && job.deliveryMethod == "self_pickup" ->
                            SelfPickupCompleteAction(job, viewModel, state.submitting)
                        job.status == DeliveryStatusKey.PENDING_SCHEDULING && access.jadwal ->
                            AssignAction(job, viewModel, state.submitting, state.drivers)
                        job.status == DeliveryStatusKey.ASSIGNED && isMyDriverJob ->
                            Column {
                                // Fan-out `dispatch` (2026-08-05): unit lain SPK ini
                                // yang dipegang driver yang SAMA ikut berangkat.
                                // Unit driver lain tak tersentuh — SPK yang dipecah
                                // ke dua driver tetap berangkat masing-masing.
                                SpkFanOutNote("Sekali tekan memberangkatkan semua unit SPK ini yang ditugaskan ke kamu.")
                                Spacer(Modifier.height(8.dp))
                                SimpleAction("Berangkat (Dispatch)", state.submitting) { viewModel.dispatch(job.id) {} }
                            }
                        // DC/admin atas unit yang SUDAH punya driver. Ditaruh
                        // SESUDAH cabang `isMyDriverJob` di atas dengan sengaja:
                        // orang yang merangkap driver + DC harus melihat tombol
                        // KERJANYA dulu (Berangkat), bukan alat kelolanya.
                        job.status == DeliveryStatusKey.ASSIGNED && access.jadwal ->
                            KelolaDriverAction(job, viewModel, state.submitting, state.drivers, bolehBatal = true)
                        job.status == DeliveryStatusKey.IN_TRANSIT && isMyDriverJob ->
                            DeliverAction(job, viewModel, state.submitting, state.driverChecklist, state.driverChecklistError)
                        // Sudah berangkat: "batal" tak lagi punya arti operasional
                        // (barangnya fisik di tangan orang), tapi PINDAH masih —
                        // motor mogok / driver sakit di tengah jalan itu nyata.
                        job.status == DeliveryStatusKey.IN_TRANSIT && access.jadwal ->
                            KelolaDriverAction(job, viewModel, state.submitting, state.drivers, bolehBatal = false)
                        // Unit sudah sampai konsumen tapi uangnya belum tercatat masuk.
                        // Berlaku SEMUA jenis pembayaran (2026-07-28) — sebelumnya
                        // non-COD tak punya titik konfirmasi sama sekali.
                        //
                        // Syaratnya SE-SPK, bukan `job.setoranKasirAt` unit ini saja
                        // (2026-08-22). Sejak formnya menutup seluruh SPK sekali jalan,
                        // memakai unit yang dibuka sebagai syarat meninggalkan lubang:
                        // kiriman yang separuh berhasil membuat unit yang dibuka
                        // tersetor sementara saudaranya belum, lalu layar ini menutup
                        // aksinya sama sekali — sisa barang tak bisa disetor dari kartu
                        // SPK mana pun. `unitMenungguSetoran` sendiri fail-soft ke satu
                        // unit, jadi perilaku lama tetap berlaku saat batch tak termuat.
                        job.status == DeliveryStatusKey.DELIVERED && access.kasir &&
                            unitMenungguSetoran(state.batchUnits, job).isNotEmpty() ->
                            SetoranKasirAction(job, viewModel, state.submitting)
                        // Diskon DITOLAK = SPK kembali ke sales (2026-08-06). Sebelum
                        // ini penolakan otomatis melepas unit ke antrian PDI; sekarang
                        // unitnya TETAP tertahan sampai sales memilih. Tanpa cabang ini
                        // layar cuma bilang "Tidak ada aksi pada tahap ini" dan SPK-nya
                        // mandek selamanya dari sisi app — tanpa satu pun error.
                        job.status == DeliveryStatusKey.PENDING_DISCOUNT &&
                            (viewModel.isAdminViewer || (!job.salesUserId.isNullOrBlank() && job.salesUserId == viewModel.currentUserId)) ->
                            DiskonTertahanAction(job, state.jobDiscounts, viewModel, state.submitting)
                        else -> Text(
                            when (job.status) {
                                DeliveryStatusKey.PENDING_PDI -> "Tahap ini ditangani tim PDI cabang."
                                DeliveryStatusKey.PENDING_PERBAIKAN -> "Unit ditahan (checklist Tidak) — menunggu perbaikan & PDI ulang, atau pelepasan kepala cabang."
                                DeliveryStatusKey.PENDING_SPK -> "Tahap ini ditangani kasir cabang."
                                DeliveryStatusKey.PENDING_DELIVERY_NOTE, DeliveryStatusKey.PENDING_SCHEDULING -> "Tahap ini ditangani Delivery Control."
                                DeliveryStatusKey.ASSIGNED, DeliveryStatusKey.IN_TRANSIT -> "Tahap ini ditangani driver yang ditugaskan."
                                else -> "Tidak ada aksi pada tahap ini."
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Batalkan (admin/DC, status non-terminal) — backend `cancel_job`.
                    val cancellable = job.status != DeliveryStatusKey.DELIVERED && job.status != DeliveryStatusKey.CANCELLED
                    if (cancellable && (viewModel.isAdminViewer || access.note)) {
                        Spacer(Modifier.height(10.dp))
                        CancelJobButton(job.id, viewModel, state.submitting)
                    }
                }
            }
        }
    }
}

private data class TimelineStep(
    val label: String,
    val timestamp: String?,
    val subtitle: String? = null,
    val skipped: Boolean = false,
    /** done|active|pending|rejected|cancelled (dari server). Fallback lokal
     *  membiarkannya null → status disimpulkan dari ada/tidaknya timestamp. */
    val tone: String? = null,
)

/** Riwayat status SPK (2026-07-26) — mirror `Timeline`/`TimelineStep` web
 *  (`components/delivery/Timeline.tsx`) supaya info lengkap di satu layar,
 *  tak perlu tebak-tebak dari status chip doang. Langkah assign/berangkat
 *  di-skip utk `self_pickup` (job lompat pending_scheduling→delivered
 *  langsung, tak pernah lewat driver beneran). */
@Composable
private fun SpkTimelineCard(
    job: DeliveryJobDto,
    discounts: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = emptyList(),
    akiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
) {
    // Alur beda per metode (2026-07-26, "sales antar sendiri: penugasan +
    // surat tugas otomatis tanpa Delivery Control"):
    // - "driver" (default): Dibuat -> PDI -> Kasir -> Surat Jalan -> Ditugaskan
    //   -> Berangkat -> Terkirim.
    // - "sales_delivery": surat jalan TETAP dibuat (nomor+cap waktu ke-generate
    //   OTOMATIS begitu kasir konfirmasi, bukan manual DC) + auto-assign sales
    //   sbg driver-nya sendiri — SEMUA step (Surat Jalan/Ditugaskan/Berangkat)
    //   tetap tampil, cuma "siapa yang ngerjain" yang beda (sistem, bukan DC).
    //
    // - "self_pickup": konsumen ambil sendiri ke toko — surat jalan DAN
    //   assign/berangkat driver dua-duanya tak pernah kejadian sama sekali
    //   (kasir konfirmasi lompat LANGSUNG ke pending_scheduling, sales tandai
    //   selesai sendiri).
    //
    // TAK ADA LAGI TAHAP PDI YANG DILEWATI (backend 2026-07-27): `pdiRequired`
    // cuma menentukan SIAPA yang mengerjakan — false = PDI Mandiri oleh sales
    // pemilik SPK, true = tim PDI cabang. Checklist + foto wajib di dua-duanya.
    val isSelfPickup = job.deliveryMethod == "self_pickup"
    val isSalesDelivery = job.deliveryMethod == "sales_delivery"
    val isSelfPdiMethod = isSelfPickup || isSalesDelivery
    val isPdiMandiri = job.pdiRequired == false || isSelfPdiMethod
    // SERVER yang menyusun timeline sejak 2026-07-27 (`delivery/timeline.rs`) —
    // satu sumber, jadi app & web tak bisa lagi diam-diam berbeda isi. Blok
    // penyusun lokal di bawah CUMA fallback untuk server lama yang belum
    // mengirim field `timeline`; jangan tambahkan tahap baru di sini, tambahkan
    // di backend supaya semua klien ikut sekaligus.
    val serverSteps = job.timeline.map { TimelineStep(it.label, it.timestamp, it.detail, tone = it.tone) }
    val steps = serverSteps.ifEmpty { buildList {
        add(TimelineStep("SPK Dibuat", job.createdAt))
        // Peristiwa dari TABEL SAMPING (bug 2026-07-27: dua-duanya tak pernah
        // muncul di timeline mobile). Approval diskon menahan job di
        // `pending_discount`, approval form aki menahan `submit_pdi` — urutannya
        // mengikuti kejadian nyata, jadi keduanya sebelum langkah PDI.
        discounts.firstOrNull()?.let { d ->
            val nilai = if (d.discountType == "percent") "${d.value.toInt()}%" else formatRupiahShort(d.value)
            when (d.status) {
                // Catatan persetujuan ikut DI SINI, setara alasan penolakan di
                // baris bawah: approver bisa menuliskannya (web sejak lama, app
                // sejak 2026-08-09) dan server mengirimkannya ke WA pengaju —
                // tanpa baris ini catatan itu ada di mana-mana KECUALI di app.
                "approved" -> add(TimelineStep(
                    "Diskon Disetujui", d.decidedAt,
                    listOfNotNull(
                        "$nilai oleh ${d.decidedByName ?: "-"}",
                        d.decisionNote?.trim()?.takeIf { it.isNotEmpty() },
                    ).joinToString(" · "),
                ))
                "rejected" -> add(TimelineStep("Diskon Ditolak", d.decidedAt, d.decisionNote ?: d.decidedByName))
                // Status BARU 2026-08-07. Tanpa arm ini ia jatuh ke `else` dan
                // timeline menulis "Menunggu Approval Diskon" untuk barang yang
                // justru sudah selesai diurus. `decidedAt` sengaja tak dipakai:
                // server TIDAK menimpanya saat menandai `dilepas` (itu jejak
                // penolakan approver, bukan waktu sales melepas).
                "dilepas" -> add(TimelineStep("Lanjut Tanpa Diskon", null, "Menunggu barang lain SPK ini tuntas"))
                else -> add(TimelineStep("Menunggu Approval Diskon", null, "$nilai diajukan ${d.requestedByName ?: "-"}"))
            }
        }
        // Form terbaru yang menentukan gate sekarang (form bisa >1 kalau pernah ditolak lalu diajukan ulang).
        akiForms.maxByOrNull { it.createdAt }?.let { f ->
            add(TimelineStep("Form Pengambilan Aki Diisi", f.createdAt, "${f.merkTipe} oleh ${f.createdByNama}"))
            when (f.approvalStatus) {
                "approved" -> add(TimelineStep("Form Aki Disetujui", f.akiApproverApprovedAt, f.akiApproverApprovedNama))
                "rejected" -> add(TimelineStep("Form Aki Ditolak", f.rejectedAt, f.rejectedReason ?: f.rejectedByNama))
                else -> add(TimelineStep("Menunggu Approval Form Aki", null, "approver pusat belum memutuskan"))
            }
        }
        add(TimelineStep(if (isPdiMandiri) "PDI Mandiri Selesai" else "PDI Selesai", job.pdiAt, job.pdiByName))
        add(TimelineStep("Kasir Konfirmasi", job.spkConfirmedAt))
        if (!isSelfPickup) {
            add(TimelineStep(if (isSalesDelivery) "Surat Jalan Terbit (Otomatis)" else "Surat Jalan Terbit", job.deliveryNoteAt, job.deliveryNoteNo))
            add(TimelineStep(if (isSalesDelivery) "Ditugaskan (Sales Sendiri, Otomatis)" else "Ditugaskan ke Driver", job.assignedAt, job.assignedDriverName))
            add(TimelineStep("Berangkat", job.dispatchedAt))
            // Chat H-1 (088) = SYARAT serah terima; tanpa step ini tak kelihatan
            // kenapa job "diam" di assigned/in_transit. Job pre-088 tak punya cap
            // waktu ini — tampilkan hanya kalau relevan.
            if (!job.consumerChatAt.isNullOrBlank() ||
                job.status == DeliveryStatusKey.ASSIGNED || job.status == DeliveryStatusKey.IN_TRANSIT
            ) {
                add(TimelineStep("Chat Konsumen (H-1)", job.consumerChatAt))
            }
        }
        add(TimelineStep(if (isSelfPickup) "Diambil Konsumen" else "Terkirim", job.deliveredAt, job.deliveredBy))
        // Setoran uang COD driver → kasir (105): terjadi SETELAH delivered dan
        // non-blocking, jadi tanpa step ini tak ada tempat mana pun di detail SPK
        // yang menunjukkan uangnya sudah disetor atau belum.
        if (job.driverTerimaUang == true) {
            add(TimelineStep("Setoran Uang ke Kasir", job.setoranKasirAt, job.setoranKasirByNama))
        }
        if (job.status == DeliveryStatusKey.CANCELLED) add(TimelineStep("Dibatalkan", job.updatedAt, job.cancelReason))
    } }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            steps.forEachIndexed { i, step ->
                val rejected = step.tone == "rejected"
                val done = !rejected && !step.timestamp.isNullOrBlank()
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        when {
                            rejected -> Icons.Rounded.Cancel
                            done -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = when {
                            rejected -> MaterialTheme.colorScheme.error
                            done -> Color(0xFF12B76A)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            step.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                rejected -> MaterialTheme.colorScheme.error
                                done -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (rejected) {
                            Text(
                                listOfNotNull(step.timestamp?.let(::formatWaktuId), step.subtitle).joinToString(" · ").ifBlank { "Ditolak" },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                            )
                        } else if (step.skipped) {
                            Text("Dilewati (tanpa PDI)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (done) {
                            Text(
                                listOfNotNull(step.timestamp?.let(::formatWaktuId), step.subtitle).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("Menunggu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
                if (i != steps.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Karyawan yang BENAR-BENAR menangani unit ini — beda dari direktori petugas
 * (Panduan Alur) yang daftarnya jabatan se-cabang. Saat unit bermasalah, yang
 * dicari orang adalah "siapa yang meng-PDI unit INI".
 *
 * Nomor sudah dinormalisasi server (`628…`); yang tak punya nomor tetap tampil
 * tanpa tombol — kehilangan cara menghubungi tak boleh berarti kehilangan
 * informasi siapa. Cerminan modal "Kontributor SPK" di web (`PdiDetailPage`),
 * yang di sana namanya juga menautkan ke halaman statistik karyawan; app belum
 * punya layar itu, jadi di sini tindakannya WhatsApp saja.
 */
@Composable
private fun KontributorCard(orang: List<KontributorDto>) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Yang Menangani Unit Ini", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            orang.forEach { k ->
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(k.nama, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = k.peran.joinToString(" · ") { it.label },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val wa = k.whatsapp?.takeIf { it.isNotBlank() }
                    if (wa != null) {
                        TextButton(onClick = { runCatching { uriHandler.openUri("https://wa.me/$wa") } }) {
                            Text("WhatsApp")
                        }
                    }
                }
            }
        }
    }
}

/** Foto bukti job (dimuat ter-autentikasi via VM) — label per jenis. */
@Composable
private fun JobPhotosCard(photos: Map<String, Bitmap>) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Foto Bukti", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            listOf(
                "pdi" to "Foto PDI (unit siap kirim)",
                "delivery" to "Foto serah terima",
                "cash" to "Foto terima uang",
            ).forEach { (key, label) ->
                photos[key]?.let { bmp ->
                    Spacer(Modifier.height(10.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

/** Tombol Batalkan + dialog alasan (admin/delivery-control, non-terminal). */
@Composable
private fun CancelJobButton(id: String, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var show by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    OutlinedButton(onClick = { show = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
        Text("Batalkan Pengiriman", color = MaterialTheme.colorScheme.error)
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("Batalkan pengiriman?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Unit keluar dari pipeline (tidak bisa di-undo).", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    ExpressiveTextField(reason, { reason = it }, label = "Alasan", modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { show = false; vm.cancel(id, reason) {} }) { Text("Batalkan", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Kembali") } }
        )
    }
}

@Composable
private fun SimpleAction(label: String, submitting: Boolean, onClick: () -> Unit) {
    ExpressiveFilledButton(onClick = onClick, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Spacer(Modifier.width(4.dp)); Text(label)
    }
}

/**
 * Catatan "aksi ini berlaku se-SPK".
 *
 * Layar detail hanya memuat SATU unit — ia tak punya daftar saudaranya, jadi
 * kalimatnya sengaja tak menyebut jumlah. Menyebut angka yang tidak diketahui
 * lebih buruk daripada tidak menyebut: petugas akan memercayainya.
 */
@Composable
private fun SpkFanOutNote(teks: String) {
    Text(
        teks,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * `rememberSaveable` untuk peta jawaban checklist — tak ada Saver bawaan untuk
 * [SnapshotStateMap]. Petanya DIRATAKAN jadi `[kunci, nilai, kunci, nilai, …]`;
 * cukup karena kunci dan nilainya sama-sama String.
 *
 * Ini yang paling penting diselamatkan dari seluruh form berkamera: `hasil`
 * default-nya "ok" untuk SEMUA item, jadi checklist yang hangus tidak kembali
 * dalam keadaan kosong melainkan dalam keadaan LULUS SEMUA — petugas yang tadi
 * menandai "tidak" beserta catatannya akan mengirim unit cacat sebagai unit
 * mulus tanpa satu pun peringatan.
 */
internal val petaJawabanSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { peta -> peta.entries.flatMap { listOf(it.key, it.value) } },
    restore = { rata ->
        mutableStateMapOf<String, String>().apply {
            rata.chunked(2).forEach { pasangan -> put(pasangan[0], pasangan[1]) }
        }
    },
)

@Composable
private fun PdiAction(
    job: DeliveryJobDto,
    batchUnits: List<DeliveryJobDto>,
    vm: DeliveryFlowViewModel, submitting: Boolean,
    checklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>,
    requiresAki: Boolean, akiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>
) {
    val id = job.id
    // PREFILL dari job — SN/engine yang diisi saat input SPK tampil di form
    // (dulu mulai kosong → SN dari SPK tertimpa NULL di backend, bug live
    // testing 2026-07-24; backend kini juga COALESCE sbg lapis kedua).
    var serial by rememberSaveable(job.id) { mutableStateOf(job.serialNumber.orEmpty()) }
    var engine by rememberSaveable(job.id) { mutableStateOf(job.engineNumber.orEmpty()) }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/pdi_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val photoState by vm.state.collectAsState()
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // `ok == false` tak lagi ditelan — lihat [PESAN_KAMERA_TAK_TERSIMPAN].
        if (ok) vm.onPdiPhotoCaptured(file) else Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
    }

    // Hasil checklist per item.id: hasil (ok/tidak/na) default "ok" + catatan.
    val hasil = rememberSaveable(checklist, saver = petaJawabanSaver) {
        mutableStateMapOf<String, String>().apply { checklist.forEach { put(it.id, "ok") } }
    }
    val catatan = rememberSaveable(checklist, saver = petaJawabanSaver) { mutableStateMapOf<String, String>() }

    photoState.pdiPhoto?.takeIf { !photoState.pdiPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakePdiPhoto() }, onConfirm = { vm.confirmPdiPhoto() })
    }

    // ── Klaim PDI (111) ──────────────────────────────────────────────────────
    // Server SENGAJA tidak mewajibkan klaim (APK lama tak tahu cara mengklaim),
    // jadi seluruh blok ini murni tampilan: ia mencegah dua petugas mengerjakan
    // unit yang sama, tapi tak pernah menghalangi pekerjaan saat datanya tak ada.
    val ttlJam = photoState.deliveryContext?.pdiClaimTtlHours
    val claim = pdiClaimView(
        job.pdiClaimedBy,
        vm.currentUserId,
        serverSupportsClaim = ttlJam != null,
        pdiClaimedAt = job.pdiClaimedAt,
        ttlJam = ttlJam,
    )
    // Cerminan `PDI_ROLES` backend (pdi/admin/superadmin) = gate yang SAMA dengan
    // submit PDI, lewat `access.pdi` yang sudah melipat divisi. Sales PDI Mandiri
    // sampai ke sini lewat `isSelfPdiJob` tapi TIDAK berhak mengklaim (403) —
    // jangan menawarkan tombolnya ke dia.
    val bolehKlaim = vm.access.pdi
    pdiClaimLabel(claim, job.pdiClaimedByName)?.let { label ->
        Text(
            label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = if (claim == PdiClaimView.MILIK_SAYA) Color(0xFF12B76A) else Color(0xFFB5670C),
        )
        Spacer(Modifier.height(8.dp))
    }
    pdiClaimKeterangan(claim, job.pdiClaimedByName, ttlJam, bolehKlaim)?.let { keterangan ->
        Text(
            keterangan,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
    }
    if (claim == PdiClaimView.MILIK_ORANG_LAIN) {
        // Jalan keluar kalau pengklaimnya pulang sebelum TTL habis (backend
        // mengizinkan admin/superadmin/manager merebut).
        if (vm.isAdminViewer) {
            TextButton(onClick = { vm.releasePdiClaim(id) }, enabled = !submitting) { Text("Lepas Klaim (Paksa)") }
        }
        return
    }
    // KEDALUWARSA sengaja TIDAK `return`: server sudah membebaskan unitnya, jadi
    // menutup form di sini berarti app lebih ketat dari aturan yang berlaku —
    // dan itulah yang mengunci 59 unit di produksi. Tombolnya sama dengan
    // BELUM_DIKLAIM di bawah; bedanya cuma kalimat penjelas di atas.
    if ((claim == PdiClaimView.BELUM_DIKLAIM || claim == PdiClaimView.KEDALUWARSA) && bolehKlaim) {
        ExpressiveFilledButton(onClick = { vm.claimPdi(id) }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
            Text("Ambil PDI")
        }
        Spacer(Modifier.height(14.dp))
    } else if (claim == PdiClaimView.MILIK_SAYA) {
        ExpressiveOutlinedButton(onClick = { vm.releasePdiClaim(id) }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
            Text("Lepas Klaim")
        }
        Spacer(Modifier.height(14.dp))
    }

    // PDI MASSAL BARANG KECIL — pindah ke sini dari antrian (permintaan user
    // 2026-08-06: antrian PDI dibuat sama seperti antrian kasir, tombolnya di
    // detail). Server menyelesaikan SEKALIGUS semua unit `pending_pdi` sebatch
    // yang harga OTR-nya di bawah ambang, tanpa checklist & nomor rangka.
    //
    // Anchor WAJIB unit KECIL — unit besar dijawab 400. Karena itu id yang
    // dikirim diambil dari hasil [unitPdiKecil], BUKAN `job.id`: layar ini bisa
    // saja sedang membuka barang besar, dan barang kecil di SPK yang sama tetap
    // berhak diselesaikan lewat jalur ini.
    val kecil = unitPdiKecil(batchUnits.ifEmpty { listOf(job) }, photoState.deliveryContext?.barangBesarThreshold)
    if (kecil.isNotEmpty()) {
        ExpressiveFilledButton(
            onClick = { vm.submitPdiKecil(kecil.first().id) {} },
            enabled = !submitting, modifier = Modifier.fillMaxWidth(),
        ) { Text("Selesaikan PDI (${kecil.size} barang kecil)") }
        Text(
            "Tanpa checklist & nomor rangka. Barang besar tetap diisi formulir di bawah.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
    }

    Text("PDI / Inspeksi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    ExpressiveTextField(
        serial, { serial = it }, label = "Nomor serial (opsional)", modifier = Modifier.fillMaxWidth(),
        trailingIcon = { BarcodeScanButton { serial = it } }
    )
    Spacer(Modifier.height(10.dp))
    ExpressiveTextField(engine, { engine = it }, label = "Nomor mesin (opsional)", modifier = Modifier.fillMaxWidth())

    if (checklist.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Checklist", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        checklist.sortedBy { it.urutan }.forEach { item ->
            Spacer(Modifier.height(6.dp))
            Text(item.itemLabel + if (item.wajib) " *" else "", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ok" to "OK", "tidak" to "Tidak", "na" to "N/A").forEach { (k, l) ->
                    val sel = hasil[item.id] == k
                    Surface(onClick = { hasil[item.id] = k }, shape = RoundedCornerShape(50),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                        Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                }
            }
            if (hasil[item.id] == "tidak") {
                Spacer(Modifier.height(4.dp))
                ExpressiveTextField(catatan[item.id].orEmpty(), { catatan[item.id] = it }, label = "Catatan (wajib untuk Tidak)", modifier = Modifier.fillMaxWidth())
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    GpsStatusRow(photoState) { vm.refreshGps() }
    Spacer(Modifier.height(8.dp))
    // Label TANPA `*` selama masih tahap peringatan: di berkas ini `*` berarti
    // WAJIB (checklist menempelkannya dari `item.wajib`), jadi memasangnya
    // sebelum tombolnya benar-benar diblokir adalah janji yang tak ditepati —
    // dan janji semacam itu mengajari petugas bahwa `*` boleh diabaikan, yang
    // merusak artinya di seluruh form. Tambahkan `*` di sini pada saat yang
    // sama dengan mengembalikan `fotoPdiSiap` ke `enabled` tombol Simpan.
    PhotoBox(photoState.pdiPhoto, "Foto unit siap") { cam.launch(uri) }

    // Form REJECTED dikecualikan (paritas gate backend pasca-093): semua form
    // rejected = wajib buat form BARU — form create dirender lagi (dulu
    // `akiForms.isEmpty()` → sekali ditolak, PDI tak bisa bikin form baru dari
    // mobile sama sekali, dead-end; temuan review 2026-07-23).
    val activeAkiForms = akiForms.filter { it.approvalStatus != "rejected" }
    val akiPending = requiresAki && activeAkiForms.isEmpty()
    if (requiresAki) {
        Spacer(Modifier.height(14.dp))
        if (akiPending) {
            var tujuan by rememberSaveable { mutableStateOf("") }
            var tujuanLainnya by rememberSaveable { mutableStateOf("") }
            // Merk: dropdown merk GS + "Lainnya…" (ketik manual). merkPilih = slug dropdown,
            // merkManual = teks bila pilih Lainnya. merkFinal = yang dikirim.
            var merkPilih by rememberSaveable { mutableStateOf("") }
            var merkManual by rememberSaveable { mutableStateOf("") }
            var kapasitas by rememberSaveable { mutableStateOf("") }
            // Jumlah SET baterai (bukan pcs) — default 1 set, tiap set = 4 pcs (auto keterangan).
            var jumlahSet by rememberSaveable { mutableStateOf("1") }
            var ambilCharger by rememberSaveable { mutableStateOf(false) }
            var ambilSpion by rememberSaveable { mutableStateOf(false) }
            var keteranganAki by rememberSaveable { mutableStateOf("") }
            // Foto bukti aki (2026-07-24, wajib) — capture→watermark→upload
            // langsung, pola sama foto PO per-barang. URL-nya WAJIB ikut
            // diselamatkan: unggahannya sudah terjadi, jadi kehilangan URL ini
            // memaksa foto yang sama diunggah dua kali.
            var akiPhotoUrl by rememberSaveable { mutableStateOf("") }
            var akiPhotoUploading by remember { mutableStateOf(false) }
            val akiScope = rememberCoroutineScope()
            val akiPhotoFile = remember { File(context.cacheDir, "delivery/aki_$id.jpg").apply { parentFile?.mkdirs() } }
            val akiPhotoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", akiPhotoFile) }
            val akiCam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                // `ok == false` tak lagi ditelan — lihat [PESAN_KAMERA_TAK_TERSIMPAN].
                if (!ok) {
                    Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }
                akiPhotoUploading = true
                akiScope.launch {
                    val url = vm.uploadAkiPhoto(akiPhotoFile)
                    akiPhotoUploading = false
                    if (url != null) akiPhotoUrl = url
                }
            }
            val merkFinal = if (merkPilih == AKI_MERK_LAINNYA) merkManual.trim() else merkPilih
            val setN = jumlahSet.toIntOrNull() ?: 0
            val jumlahKet = if (setN > 0) "$setN set = ${setN * AKI_PCS_PER_SET} pcs" else ""

            Text("Form Pengambilan Aki (wajib)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AkiTujuanDropdown(tujuan, { tujuan = it })
            if (tujuan == "lainnya") {
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(tujuanLainnya, { tujuanLainnya = it }, label = "Tujuan lainnya *", modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            AkiOptionDropdown(
                label = "Merk / Tipe *",
                options = AKI_MERK_OPTIONS,
                selected = merkPilih,
                allowLainnya = true,
                lainnyaSlug = AKI_MERK_LAINNYA,
                onSelect = { merkPilih = it },
            )
            if (merkPilih == AKI_MERK_LAINNYA) {
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(merkManual, { merkManual = it }, label = "Merk lainnya *", modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            AkiOptionDropdown(
                label = "Kapasitas (opsional)",
                options = AKI_KAPASITAS_OPTIONS,
                selected = kapasitas,
                allowLainnya = false,
                onSelect = { kapasitas = it },
            )
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(
                jumlahSet, { jumlahSet = it.filter { c -> c.isDigit() } },
                label = "Jumlah (set baterai)", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth()
            )
            if (jumlahKet.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(jumlahKet, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ambilCharger, onCheckedChange = { ambilCharger = it })
                Text("Ambil charger", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(16.dp))
                Checkbox(checked = ambilSpion, onCheckedChange = { ambilSpion = it })
                Text("Ambil kaca spion", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(keteranganAki, { keteranganAki = it }, label = "Keterangan (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("Foto Bukti Aki *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (akiPhotoUrl.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Foto terunggah", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    IconButton(onClick = { akiPhotoUrl = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus foto") }
                }
            } else {
                Surface(
                    onClick = { if (!akiPhotoUploading) akiCam.launch(akiPhotoUri) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (akiPhotoUploading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Mengunggah…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ambil / unggah foto", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            ExpressiveOutlinedButton(
                onClick = {
                    vm.createAkiForm(
                        id,
                        com.krisoft.tridjayaelektronik.data.model.CreateAkiFormBody(
                            tujuan = tujuan, merkTipe = merkFinal, jumlahPcs = setN,
                            tujuanLainnya = if (tujuan == "lainnya") tujuanLainnya.trim().ifBlank { null } else null,
                            kapasitas = kapasitas.trim().ifBlank { null },
                            jumlahKeterangan = jumlahKet.ifBlank { null },
                            keterangan = keteranganAki.trim().ifBlank { null },
                            ambilCharger = ambilCharger,
                            ambilKacaSpion = ambilSpion,
                            photoUrl = akiPhotoUrl,
                        )
                    ) {}
                },
                enabled = !submitting && tujuan.isNotBlank() && (tujuan != "lainnya" || tujuanLainnya.trim().isNotEmpty()) &&
                    merkFinal.isNotEmpty() && setN > 0 && akiPhotoUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                else Text("Simpan Form Aki")
            }
        } else if (activeAkiForms.all { it.approvalStatus == "approved" }) {
            Text("Form aki disetujui ✓ (${activeAkiForms.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF12B76A))
        } else {
            Text(
                "Form aki menunggu persetujuan approver pusat — PDI belum bisa disimpan sampai lengkap.",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFFB5670C)
            )
        }
        // Info form yang DITOLAK (beda dari "menunggu" — teks lama menyesatkan).
        akiForms.count { it.approvalStatus == "rejected" }.takeIf { it > 0 }?.let { n ->
            Spacer(Modifier.height(4.dp))
            Text(
                "$n form aki ditolak — lihat alasan di menu Pengambilan Aki.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
            )
        }
    }
    Spacer(Modifier.height(14.dp))

    // Backend meng-gate PDI sampai >=1 form non-rejected & SEMUANYA disetujui
    // lengkap (3 slot) — cek approvalStatus supaya tombol tak "sukses lalu
    // ditolak backend". Form rejected diabaikan (paritas 093).
    val akiApproved = activeAkiForms.isNotEmpty() && activeAkiForms.all { it.approvalStatus == "approved" }
    val missingCatatan = checklist.any { hasil[it.id] == "tidak" && catatan[it.id].orEmpty().isBlank() }
    // Foto unit siap DITAGIH, belum diblokir (2026-08-29) — di APK ia selama
    // ini opsional sementara form web `PdiDetailPage` sudah mewajibkannya
    // sejak lama, dan tak ada yang pernah menyebutnya. Asimetrinya bukan
    // sekadar tak rapi: PDI adalah satu-satunya tahap yang memotret barangnya
    // SEBELUM keluar gudang, jadi baris yang lolos tanpa foto meninggalkan
    // lubang tepat di titik yang paling dibutuhkan saat konsumen mengeluh unit
    // datang cacat — dan lubang itu hanya muncul pada baris yang dikerjakan
    // lewat HP, tanpa satu pun tanda di data bahwa penyebabnya platform.
    //
    // **TAHAP 1: PERINGATAN, BELUM MEMBLOKIR** (keputusan user 2026-08-29).
    // Draf pertama perubahan ini mematikan tombol Simpan, dan angka produksi
    // menunjukkan itu terlalu tajam untuk langsung: dari 701 baris PDI unit
    // besar Agustus, 149 (21,3%) tak berfoto — dan sebarannya timpang, satu
    // petugas menyumbang 80 dari 94 barisnya sendiri (85%). Mematikan tombol
    // tanpa pemberitahuan berarti orang itu membuka unit pertamanya besok
    // pagi, mengisi checklist seperti biasa, lalu menyimpulkan app-nya rusak —
    // dan antrian PDI cabangnya berhenti sampai ada yang menjelaskan. Jadi
    // sekarang: peringatan merah yang tak bisa dilewatkan mata, tombol tetap
    // hidup. Menaikkannya jadi gate = kembalikan `fotoPdiSiap` ke `enabled`
    // di bawah, SETELAH petugas PDI diberi tahu.
    //
    // Server SENGAJA tak ikut ditegakkan sampai kapan pun soal ini diputuskan:
    // `submit_pdi` menerima `readyPhotoUrl` opsional demi kompat APK lama
    // (`delivery.rs:4702-4705`), jadi menyalakannya mematikan PDI di seluruh
    // APK yang masih beredar, bukan cuma yang baru.
    //
    // `pdiPhotoConfirmed` ikut disyaratkan, bukan cuma `pdiPhoto != null` —
    // pola sama `hasPhoto` di tiga tahap serah terima (`SetoranKasirAction`,
    // `DeliverAction`, `SelfPickupCompleteAction`). Disebut NAMA, bukan nomor
    // baris: berkas ini 4.700 baris dan sitasi baris di dalamnya sudah basi
    // sebelum commit-nya mendarat — versi pertama komentar ini menunjuk :2419
    // /:2897/:2968 yang sudah meleset 22 baris gara-gara hunk di atasnya
    // sendiri, dan tiga tempat yang ditunjuknya tetap terlihat masuk akal
    // sehingga kesalahannya tak menimbulkan kecurigaan.
    val fotoPdiSiap = photoState.pdiPhoto != null && photoState.pdiPhotoConfirmed
    if (!fotoPdiSiap) {
        // Merah, bukan kuning: kuning di berkas ini (`Color(0xFFB5670C)`)
        // dipakai untuk keadaan yang memang sedang menunggu pihak lain —
        // approval aki. Yang ini menunggu petugas itu sendiri, dan ia akan
        // melewatinya kalau warnanya tak menuntut apa-apa.
        Text(
            "Foto unit siap belum diambil — akan diwajibkan. Ambil sekarang selagi unitnya di depan kamu.",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
    }
    ExpressiveFilledButton(
        onClick = {
            val bodies = checklist.map { com.krisoft.tridjayaelektronik.data.model.PdiChecklistItemBody(item = it.itemLabel, hasil = hasil[it.id] ?: "ok", catatan = catatan[it.id]?.trim()?.ifBlank { null }) }
            vm.submitPdi(id, serial, engine, bodies) {}
        },
        enabled = !submitting && !missingCatatan && (!requiresAki || akiApproved),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting && !akiPending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(
            when {
                missingCatatan -> "Isi catatan item 'Tidak'"
                akiPending -> "Isi form aki dulu"
                requiresAki && !akiApproved -> "Tunggu approval form aki"
                else -> "Simpan PDI"
            }
        )
    }
}

/** Konfirmasi SPK kasir (2026-07-26) — backend WAJIB `noTransaksi` non-kosong
 *  sejak migrasi 105 (endpoint dulu tanpa body sama sekali, root cause error
 *  415: `Json` extractor axum menolak request tanpa `Content-Type: application/
 *  json`, yg terjadi kalau body kosong).
 *
 *  COD (`driverTerimaUang`): centang konfirmasi pembayaran OPSIONAL — pada COD
 *  uangnya justru belum ada di kasir, driver baru menagihnya di tempat
 *  konsumen, jadi mewajibkannya membuat SPK mandek permanen di antrian kasir.
 *  Uangnya dikonfirmasi belakangan di tab "Setoran Driver". Nominal DP mode
 *  `dp` TETAP wajib: DP dibayar konsumen di toko, uangnya nyata ada di kasir.
 *  Mirror web `KasirDashboardPage`.
 *
 *  MULTI-UNIT (2026-08-06): [batchUnits] = seluruh unit `pending_spk` SPK ini
 *  (dimuat `loadBatchUnits`). Nomor transaksi GS diketik SEKALI — di GS, SPK
 *  banyak barang adalah SATU transaksi satu nomor, dan server mem-fan-out-kan
 *  konfirmasi ini ke semuanya. Nominal DP diketik PER UNIT: tiap unit COD `dp`
 *  punya DP-nya sendiri, dan server menolak 400 daftar `units[]` yang
 *  menyisakan salah satunya kosong.
 *
 *  [batchUnits] kosong (gagal dimuat / server lama) = jatuh balik ke satu unit,
 *  persis perilaku sebelum fitur ini. Konfirmasinya tetap sah; yang hilang cuma
 *  kesempatan mengetik DP unit lain, yang lalu memakai rencana sales. */
@Composable
private fun KasirConfirmSpkAction(
    job: DeliveryJobDto,
    batchUnits: List<DeliveryJobDto>,
    vm: DeliveryFlowViewModel,
    submitting: Boolean,
) {
    val units = batchUnits.ifEmpty { listOf(job) }
    val multi = units.size > 1
    var noTransaksi by rememberSaveable(job.id) { mutableStateOf(job.noTransaksi.orEmpty()) }
    var konfirmasiBayar by rememberSaveable(job.id) { mutableStateOf(false) }
    // Nominal DP per unit-id. Kunci `job.id`: berpindah SPK harus mengosongkan
    // isian, kalau tidak DP SPK sebelumnya ikut terkirim.
    val dp = rememberSaveable(job.id, saver = petaJawabanSaver) { mutableStateMapOf<String, String>() }

    val adaCod = units.any { it.driverTerimaUang == true }
    val unitCodDp = units.filter { it.driverTerimaUang == true && it.codPaymentMode == "dp" }
    // Hanya nominal DP yang menahan tombol; centang konfirmasi tidak (lihat dok di atas).
    val dpLengkap = unitCodDp.all { (dp[it.id]?.toDoubleOrNull() ?: 0.0) > 0.0 }

    Text("Konfirmasi SPK (Kasir)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    // Kasir di HP memutuskan uang yang sama dengan kasir di web, jadi ia harus
    // melihat cabang tempat bayar SEBELUM menekan konfirmasi — bukan cuma di
    // layar detail. Namanya dari server (`bayarDealerName`), tak diturunkan
    // ulang di app.
    namaCabangBayar(job)?.let { nama ->
        Spacer(Modifier.height(4.dp))
        Text(
            "Bayar di: $nama",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (multi) {
        Spacer(Modifier.height(4.dp))
        SpkFanOutNote("${units.size} barang dalam SPK ini dikonfirmasi sekaligus — satu nomor transaksi GS untuk semuanya.")
    }
    Spacer(Modifier.height(8.dp))
    ExpressiveTextField(noTransaksi, { noTransaksi = it }, label = "No. Transaksi GS (wajib)", modifier = Modifier.fillMaxWidth())

    // HANYA isian DP yang tinggal di sini. Rincian barangnya sudah dipajang di
    // kartu identitas SPK di atas ([SpkUnitList]) — mengulanginya di area aksi
    // membuat layar memuat daftar yang sama dua kali dan mendorong tombolnya
    // makin jauh ke bawah.
    unitCodDp.forEach { u ->
        Spacer(Modifier.height(10.dp))
        MoneyTextField(
            dp[u.id].orEmpty(), { v -> dp[u.id] = v },
            // Label menyebut BARANGNYA, bukan cuma "DP": dengan beberapa unit
            // COD dp, kolom bernama sama semua tak bisa dibedakan isinya.
            label = if (multi) {
                "DP ${u.namaBarang ?: u.kodeBarang ?: u.kodePengiriman} (wajib) *"
            } else {
                "DP diterima kasir (wajib) *"
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (adaCod) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { konfirmasiBayar = !konfirmasiBayar }) {
            Checkbox(checked = konfirmasiBayar, onCheckedChange = { konfirmasiBayar = it })
            Text("Sudah cek pembayaran benar (opsional)", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Uang yang ditagih driver dikonfirmasi nanti di tab Setoran Driver, setelah barang diantar.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(14.dp))
    ExpressiveFilledButton(
        onClick = {
            vm.confirmSpk(
                id = job.id,
                noTransaksi = noTransaksi,
                kasirKonfirmasiPembayaran = if (adaCod) konfirmasiBayar else null,
                // Field lama dipakai HANYA saat satu unit; di SPK banyak unit
                // `units[]` sudah memuat nominal unit ini juga, dan mengirim
                // keduanya membuat dua sumber untuk angka yang sama.
                kasirDpDiterima = if (!multi) dp[job.id]?.toDoubleOrNull() else null,
                units = if (multi) {
                    unitCodDp.map {
                        com.krisoft.tridjayaelektronik.data.model.ConfirmSpkUnitBody(
                            id = it.id, kasirDpDiterima = dp[it.id]?.toDoubleOrNull(),
                        )
                    }
                } else null,
            ) {}
        },
        enabled = !submitting && noTransaksi.trim().isNotEmpty() && dpLengkap,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(
            when {
                !dpLengkap -> "Isi DP tiap unit COD"
                multi -> "Konfirmasi SPK (${units.size} unit)"
                else -> "Konfirmasi SPK"
            }
        )
    }
}

/**
 * Kasir menutup buku satu unit: nominal yang benar-benar diterima + foto bukti.
 * Non-blocking (tak mengubah status) dan boleh diulang — server menimpa.
 */
/**
 * SPK tertahan di `pending_discount` — apa yang bisa dilakukan pemiliknya.
 *
 * Sampai 2026-08-05, penolakan diskon melepas unit ke antrian PDI dengan
 * sendirinya, jadi tahap ini tak pernah butuh tombol. Sejak 2026-08-06
 * penolakan MENAHAN unit dan mengembalikan SPK ke sales — perubahan yang tak
 * menghasilkan error apa pun di app lama, cuma SPK yang diam.
 *
 * Tiga jalan keluar versi server, KETIGANYA kini ada di app (2026-08-07):
 * ajukan ulang diskon ([RevisiDiskonAction]), sunting isi SPK (kartu "Ubah Isi
 * SPK" terpisah), atau lanjut tanpa diskon (tombol di sini). Yang pertama dulu
 * absen — `POST /inventory/discount-requests` tak pernah dideklarasikan di
 * `DeliveryFlowApi.kt`, jadi teksnya melempar sales ke web di tengah alur yang
 * seluruhnya dikerjakan dari HP.
 */
@Composable
private fun DiskonTertahanAction(
    job: DeliveryJobDto,
    riwayat: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>,
    vm: DeliveryFlowViewModel,
    submitting: Boolean,
) {
    // `createdAt` ISO-8601 → urut leksikografis = urut waktu.
    val terakhir = riwayat.maxByOrNull { it.createdAt }
    val ditolak = terakhir?.takeIf { it.status == "rejected" }
    var konfirmasi by remember(job.id) { mutableStateOf(false) }

    Text("Menunggu Keputusan Diskon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))

    if (ditolak == null) {
        Text(
            when {
                terakhir == null ->
                    "Pengajuan diskon SPK ini sedang menunggu approver. Unit belum masuk antrian PDI sampai ada keputusan."
                // `dilepas`/`approved` = barang INI sudah tuntas, tapi unitnya
                // masih tertahan karena SPK baru lanjut setelah SELURUH barangnya
                // tuntas (2026-08-07). Tanpa arm ini layar menulis "menunggu
                // keputusan approver" untuk barang yang tak menunggu siapa pun,
                // dan sales mengejar approver yang sudah selesai bekerja.
                barisTuntas(terakhir.status) ->
                    "Barang ini sudah tuntas. Unit SPK masuk antrian PDI setelah SELURUH barang SPK ini tuntas."
                else -> "Pengajuan diskon masih menunggu keputusan approver."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Diskon ditolak", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
            )
            ditolak.decisionNote?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("Alasan: $it", style = MaterialTheme.typography.bodySmall)
            }
            ditolak.decidedByName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text("Oleh $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "SPK ini TIDAK otomatis lanjut. Pilihanmu: ajukan ulang diskon, " +
                    "ubah isi SPK lewat tombol di atas, atau lanjutkan tanpa diskon.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    RevisiDiskonAction(job, ditolak, vm, submitting)

    Spacer(Modifier.height(8.dp))
    ExpressiveFilledButton(
        onClick = { konfirmasi = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth(),
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text("Lanjut Tanpa Diskon")
    }

    if (konfirmasi) {
        AlertDialog(
            onDismissRequest = { if (!submitting) konfirmasi = false },
            title = { Text("Lanjut tanpa diskon?", fontWeight = FontWeight.Bold) },
            text = {
                // 2026-08-07: TIDAK lagi menjanjikan SPK langsung masuk PDI —
                // server cuma menandai barang ini `dilepas`, dan unitnya baru
                // lepas setelah barang LAIN di SPK ini ikut tuntas. Janji lama
                // membuat sales mengira SPK-nya jalan lalu melapor "PDI tak
                // menerima" atas alur yang bekerja normal.
                Text(
                    "Harga barang ini kembali normal. SPK masuk antrian PDI setelah SELURUH barangnya tuntas. " +
                        "Tak bisa dibatalkan dari sini — kalau masih mau menawar, batalkan dan pakai " +
                        "\"Ajukan Ulang Diskon\"."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = { vm.lanjutTanpaDiskon(ditolak.id, job.id); konfirmasi = false },
                ) { Text("Ya, lanjutkan") }
            },
            dismissButton = { TextButton(onClick = { konfirmasi = false }, enabled = !submitting) { Text("Batal") } },
        )
    }
}

/**
 * Ajukan ULANG diskon setelah ditolak — jalan keluar yang dulu cuma ada di web.
 *
 * Nilainya RUPIAH dan bersifat TAMBAHAN di atas diskon yang sudah menempel
 * (server: `new_diskon = diskon_current + value`), sama seperti web. Persen
 * sengaja tak ditawarkan: form SPK mobile pun mengetik rupiah.
 *
 * Server MENOLAK bila baris ini masih punya pengajuan `pending` (anti-tumpuk,
 * 2026-08-07) — pesannya ditampilkan apa adanya lewat `actionError`, karena
 * "tunggu keputusan yang sudah kamu ajukan" adalah jawaban yang benar, bukan
 * kegagalan yang perlu disembunyikan.
 */
@Composable
private fun RevisiDiskonAction(
    job: DeliveryJobDto,
    ditolak: com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto,
    vm: DeliveryFlowViewModel,
    submitting: Boolean,
) {
    var buka by remember(job.id) { mutableStateOf(false) }
    var nilai by remember(job.id) { mutableStateOf("") }
    var alasan by remember(job.id) { mutableStateOf("") }
    var pesan by remember(job.id) { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsState()
    // `baris` job = baris pengajuan; job lama tanpa baris tak bisa direvisi
    // dari sini (server menuntut `baris >= 1`).
    val baris = job.baris ?: ditolak.baris

    if (baris == null) return

    ExpressiveOutlinedButton(
        onClick = { nilai = ""; alasan = ""; pesan = null; buka = true },
        enabled = !submitting, modifier = Modifier.fillMaxWidth(),
    ) { Text("Ajukan Ulang Diskon") }
    pesan?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    if (buka) {
        val nominal = nilai.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
        AlertDialog(
            onDismissRequest = { if (!submitting) buka = false },
            title = { Text("Ajukan ulang diskon", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Barang ke-$baris · ${job.namaBarang ?: job.kodeBarang ?: "-"}",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Potongan per unit, di ATAS diskon yang sudah menempel. Approver memutuskan lagi " +
                            "untuk seluruh SPK.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ExpressiveTextField(
                        nilai, { nilai = it.filter { c -> c.isDigit() } },
                        label = "Potongan (Rp)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    ExpressiveTextField(
                        alasan, { alasan = it },
                        label = "Alasan pengajuan (wajib)",
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.actionError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting && nominal > 0.0 && alasan.isNotBlank(),
                    onClick = {
                        vm.ajukanUlangDiskon(
                            spkBatchKode = ditolak.spkBatchKode,
                            baris = baris,
                            nilai = nominal,
                            alasan = alasan.trim(),
                            jobId = job.id,
                        ) { buka = false; pesan = "Pengajuan terkirim — menunggu approver." }
                    },
                ) { Text(if (submitting) "Mengirim..." else "Ajukan") }
            },
            dismissButton = { TextButton(onClick = { buka = false }, enabled = !submitting) { Text("Batal") } },
        )
    }
}

@Composable
private fun SetoranKasirAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    val state by vm.state.collectAsState()
    // Unit se-SPK yang masih menunggu setoran; FAIL-SOFT ke satu unit kalau
    // `batchUnits` belum/gagal termuat — aturan lengkapnya di `SetoranKasirGate.kt`.
    val menunggu = unitMenungguSetoran(state.batchUnits, job)
    val multi = menunggu.size > 1
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/setoran_${job.id}.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // `ok == false` tak lagi ditelan — lihat [PESAN_KAMERA_TAK_TERSIMPAN].
        if (ok) vm.onDeliverPhotoCaptured(file) else Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
    }
    // Nominal per unit-id. Kunci `job.id` supaya berpindah SPK mengosongkan
    // isian — kalau tidak, angka SPK sebelumnya ikut terkirim (pola sama
    // [ConfirmSpkAction], dan alasan Saver-nya ada di [petaJawabanSaver]).
    val nominal = rememberSaveable(job.id, saver = petaJawabanSaver) { mutableStateMapOf<String, String>() }

    state.deliverPhoto?.takeIf { !state.deliverPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakeDeliverPhoto() }, onConfirm = { vm.confirmDeliverPhoto() })
    }

    Text("Konfirmasi Pembayaran Diterima", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        if (job.driverTerimaUang == true) "Uang COD yang disetor driver ke kasir."
        else "Pembayaran penjualan ini (transfer/tunai di toko).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Cabang tempat bayar (2026-08-12) — kasir cabang lain yang membuka layar
    // ini perlu tahu uangnya memang bukan miliknya sebelum mencatat setoran.
    namaCabangBayar(job)?.let { nama ->
        Spacer(Modifier.height(4.dp))
        Text(
            "Bayar di: $nama",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (multi) {
        Spacer(Modifier.height(4.dp))
        SpkFanOutNote(
            "${menunggu.size} barang dalam SPK ini dikonfirmasi sekaligus — SATU foto bukti " +
                "setor untuk semuanya, nominalnya tetap diisi per barang.",
        )
    }
    menunggu.forEach { u ->
        Spacer(Modifier.height(12.dp))
        Text(
            "${u.namaBarang ?: u.kodeBarang ?: u.kodePengiriman}${u.tipe?.let { " · $it" } ?: ""}",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        )
        // Angka RUJUKAN, sengaja bukan prefill kolom. `hargaTotal` cuma sama
        // dengan uang yang diterima kasir pada COD full; kredit menerima DP-nya
        // saja dan COD `dp` menerima sisanya. Mengisikannya otomatis menaruh
        // angka yang TERLIHAT benar di dua dari tiga jenis pembayaran, dan kasir
        // yang percaya kolom terisi tak punya cara tahu ia salah.
        rujukanSetoran(u)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        MoneyTextField(
            nominal[u.id].orEmpty(), { v -> nominal[u.id] = v },
            // Label menyebut barangnya saat lebih dari satu: kolom bernama sama
            // semua tak bisa dibedakan isinya.
            label = if (multi) {
                "Diterima untuk ${u.namaBarang ?: u.kodeBarang ?: u.kodePengiriman} (wajib) *"
            } else {
                "Nominal diterima (wajib) *"
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(12.dp))
    PhotoBox(state.deliverPhoto, if (multi) "Foto bukti setor — satu untuk SPK ini (wajib)" else "Foto bukti (wajib)") { cam.launch(uri) }
    Spacer(Modifier.height(14.dp))
    val hasPhoto = state.deliverPhoto != null && state.deliverPhotoConfirmed
    // Seluruh aturannya (termasuk KENAPA nominal harus > 0, bukan >= 0, dan
    // kenapa fan-out klien boleh di endpoint ini) hidup di `SetoranKasirGate.kt`
    // sebagai fungsi murni yang diuji — jangan menyalinnya balik ke sini.
    val rencana = setoranSpkRencana(menunggu.map { SetoranBaris(it.id, nominal[it.id].orEmpty()) }, hasPhoto)
    ExpressiveFilledButton(
        onClick = { vm.setoranKasirSpk(rencana.kiriman) {} },
        enabled = !submitting && rencana.bolehKirim, modifier = Modifier.fillMaxWidth(),
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(rencana.label)
    }
}

/**
 * Baris angka rujukan di bawah nama barang — FAKTA dari server saja, tanpa satu
 * pun angka turunan. `null` = tak ada yang bisa disebutkan (server lama), dan
 * barisnya tidak dirender sama sekali.
 *
 * Sengaja TIDAK menampilkan "sisa yang harus ditagih": penurunannya berbeda per
 * jenis pembayaran (kredit lewat fincoy tak menagih sisa ke konsumen sama
 * sekali), jadi satu rumus di sini akan salah untuk sebagian SPK tanpa terlihat
 * salah.
 */
private fun rujukanSetoran(u: DeliveryJobDto): String? {
    val bagian = buildList {
        u.hargaTotal?.takeIf { it > 0 }?.let { add("Harga ${rupiah(it)}") }
        u.kasirDpDiterima?.takeIf { it > 0 }?.let { add("DP sudah diterima ${rupiah(it)}") }
    }
    return bagian.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun DeliveryNoteAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var source by remember { mutableStateOf(job.kodeDealer.orEmpty()) }
    Text("Terbitkan Surat Jalan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    SpkFanOutNote("Nomor surat jalan yang sama dipakai untuk SELURUH unit SPK ini — satu pengiriman, satu lembar.")
    Spacer(Modifier.height(8.dp))
    ExpressiveTextField(source, { source = it }, label = "Cabang sumber unit (wajib)", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    ExpressiveFilledButton(onClick = { vm.issueDeliveryNote(job.id, source) {} }, enabled = !submitting && source.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Terbitkan Surat Jalan")
    }
}

@Composable
private fun AssignAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean, drivers: List<com.krisoft.tridjayaelektronik.data.model.DriverDto>) {
    val id = job.id
    var driverId by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    // minSdk 24 tanpa coreLibraryDesugaring (dicek app/build.gradle.kts) — java.time.LocalDate
    // butuh API 26, jadi pakai SimpleDateFormat.
    var date by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) }
    var mapUrl by remember { mutableStateOf(job.customerMapUrl.orEmpty()) }

    Text("Assign Driver + Jadwal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    // Fan-out `assign_driver` (2026-08-05). Konsekuensi operasional yang WAJIB
    // disebut: memecah satu SPK ke dua driver tak bisa lewat assign satu-satu —
    // tugaskan sekali, lalu pindahkan unitnya lewat "Pindah Driver" (jalur
    // `reassign` sengaja TIDAK difan-out justru supaya pemecahan tetap mungkin).
    // Tanpa kalimat ini DC akan mengira app-nya rusak.
    //
    // Kalimat "lewat menu reassign di WEB" dibuang 2026-08-28: sejak
    // `KelolaDriverAction` ada, jalurnya sudah ada di HP dan menyuruh DC pindah
    // ke browser justru menyembunyikan tombol yang tepat di layar berikutnya.
    SpkFanOutNote(
        "Driver & jadwal ini berlaku untuk SELURUH unit SPK yang masih menunggu penjadwalan. " +
            "Unit \"diambil sendiri\" dilewati. Mau dipecah ke dua driver? Tugaskan sekali dulu, " +
            "lalu buka unitnya dan pakai \"Pindah Driver\"."
    )
    Spacer(Modifier.height(8.dp))
    // SEMUA driver ditampilkan, lintas cabang/region — se-cabang di atas, cabang
    // asal ditulis di tiap baris. Alasan lengkap kenapa penyaringan region dibuang
    // (dan kenapa `cabang_name` tak boleh jadi kunci) ada di `DriverPicker.kt`.
    val pickable = driverBisaDitugaskan(drivers, job.kodeDealer)
    // Sales antar sendiri (2026-07-24): fallback manual kalau auto-assign backend
    // gagal (map_url kosong saat surat jalan terbit) — DC bisa pilih sales pembuat
    // SPK sbg driver, sama seperti opsi driver asli.
    if (job.deliveryMethod == "sales_delivery" && !job.salesUserId.isNullOrBlank()) {
        Text("Sales antar sendiri", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val salesId = job.salesUserId
        val salesLabel = job.salesName?.takeIf { it.isNotBlank() } ?: salesId
        val sel = driverId == salesId
        Surface(
            onClick = { driverId = salesId; driverName = salesLabel },
            shape = RoundedCornerShape(12.dp),
            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sales: $salesLabel", color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
    }
    if (pickable.isNotEmpty()) {
        Text(
            "Pilih driver (semua cabang)",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            pickable.forEach { d ->
                val sel = driverId == d.effectiveId
                Surface(onClick = { driverId = d.effectiveId; driverName = d.name }, shape = RoundedCornerShape(12.dp),
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(d.name.ifBlank { d.effectiveId }, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Cabang asal driver dipajang apa adanya — inilah pengganti
                        // filter region: DC melihat driver itu dari cabang mana dan
                        // memutuskan sendiri, alih-alih daftarnya dipangkas diam-diam.
                        val cabang = d.cabangName.trim()
                        if (cabang.isNotEmpty()) {
                            Text(cabang, style = MaterialTheme.typography.labelSmall,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    } else {
        // Satu-satunya sebab daftarnya kosong sekarang: rosternya sendiri tak
        // terpakai (role tak berizin / endpoint kosong / semua id kosong) — BUKAN
        // lagi "tak ada driver se-region". Input manual tetap ada supaya DC tak
        // terkunci saat API mati; server menerima user id apa pun (`assign_driver`
        // cuma memeriksa role si penugas).
        Text(
            "Daftar driver tidak bisa dimuat — isi nama & ID driver secara manual.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        ExpressiveTextField(driverName, { driverName = it }, label = "Nama driver", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        ExpressiveTextField(driverId, { driverId = it }, label = "ID driver (user id)", modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    ExpressiveTextField(date, { date = it }, label = "Jadwal kirim (yyyy-mm-dd)", modifier = Modifier.fillMaxWidth())
    if (job.customerMapUrl.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        ExpressiveTextField(mapUrl, { mapUrl = it }, label = "Link Google Maps konsumen (wajib)", modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(14.dp))
    ExpressiveFilledButton(
        onClick = { vm.assign(id, driverId, driverName, date, mapUrl.trim().ifBlank { null }) {} },
        enabled = !submitting && driverId.trim().isNotEmpty() && date.trim().isNotEmpty() && mapUrl.trim().isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Assign Driver")
    }
}

/**
 * Kelola driver pada unit yang SUDAH ditugaskan — Batalkan & Pindahkan.
 *
 * **Kenapa ini ada.** Sampai 2026-08-28 kedua endpoint-nya (`unassign`,
 * `reassign`) hanya punya klien di web, jadi DC yang bekerja dari HP wajib
 * pindah ke browser untuk koreksi salah-tugas — dan layar ini sendiri
 * menyuruhnya begitu ("pindahkan unitnya lewat menu reassign di web").
 *
 * [bolehBatal] `false` saat unit sudah `in_transit`: server memang menolak
 * `unassign` sesudah berangkat, jadi menampilkan tombolnya cuma menghasilkan
 * pesan validasi. PINDAH tetap boleh sampai unit diterima konsumen.
 *
 * **Jalur ini TIDAK di-fan-out se-SPK** (beda dari `assign`), dan itu justru
 * fiturnya: memecah satu SPK ke dua driver hanya bisa lewat sini.
 */
@Composable
private fun KelolaDriverAction(
    job: DeliveryJobDto,
    vm: DeliveryFlowViewModel,
    submitting: Boolean,
    drivers: List<com.krisoft.tridjayaelektronik.data.model.DriverDto>,
    bolehBatal: Boolean,
) {
    var mode by remember(job.id) { mutableStateOf("") }
    var driverId by remember(job.id) { mutableStateOf("") }
    var driverName by remember(job.id) { mutableStateOf("") }
    var tanggal by remember(job.id) { mutableStateOf("") }
    var alasan by remember(job.id) { mutableStateOf("") }

    Text("Kelola Driver", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    SpkFanOutNote(
        if (bolehBatal) {
            "Berlaku untuk UNIT INI SAJA, bukan seluruh SPK — inilah cara memecah satu SPK ke dua driver."
        } else {
            "Unit sudah berangkat: penugasan tak bisa dibatalkan, tapi masih bisa dipindahkan ke driver lain."
        }
    )
    Spacer(Modifier.height(8.dp))
    job.assignedDriverName?.takeIf { it.isNotBlank() }?.let {
        Text(
            "Driver sekarang: $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        if (bolehBatal) {
            OutlinedButton(
                onClick = { mode = if (mode == "batal") "" else "batal" },
                enabled = !submitting,
                modifier = Modifier.weight(1f)
            ) { Text(if (mode == "batal") "Tutup" else "Batalkan") }
            Spacer(Modifier.width(8.dp))
        }
        OutlinedButton(
            onClick = { mode = if (mode == "pindah") "" else "pindah" },
            enabled = !submitting,
            modifier = Modifier.weight(1f)
        ) { Text(if (mode == "pindah") "Tutup" else "Pindah Driver") }
    }

    when (mode) {
        "batal" -> {
            Spacer(Modifier.height(10.dp))
            // Alasan OPSIONAL di server, tapi ia masuk teks notifikasi driver
            // yang unitnya ditarik — tanpa itu ia cuma tahu tugasnya hilang.
            ExpressiveTextField(
                alasan, { alasan = it },
                label = "Alasan (dikirim ke driver)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            ExpressiveFilledButton(
                onClick = { vm.unassign(job.id, alasan) { mode = ""; alasan = "" } },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Batalkan Penugasan")
            }
        }
        "pindah" -> {
            Spacer(Modifier.height(10.dp))
            // `driverBisaDitugaskan` + `effectiveId` dipakai ULANG dari jalur
            // assign — himpunan driver yang sah sama persis, dan `effectiveId`
            // (bukan `id`) itu yang dibandingkan server: `delivery_jobs
            // .assigned_driver_id` huruf kecil sementara `auth_users.id`
            // UPPERCASE.
            val pindahable = driverBisaDitugaskan(drivers, job.kodeDealer)
                // Driver yang SEDANG memegang unit ini dibuang dari pilihan —
                // server menolaknya ("Driver baru sama dengan driver sekarang"),
                // jadi menampilkannya cuma menawarkan tombol yang pasti gagal.
                .filterNot { d ->
                    job.assignedDriverId?.equals(d.effectiveId, ignoreCase = true) == true
                }
            if (pindahable.isEmpty()) {
                Text(
                    "Tak ada driver lain yang bisa dipilih untuk cabang ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pindahable.forEach { d ->
                    val sel = driverId == d.effectiveId
                    Surface(
                        onClick = { driverId = d.effectiveId; driverName = d.name },
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                d.name.ifBlank { d.effectiveId },
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            val cabang = d.cabangName.trim()
                            if (cabang.isNotEmpty()) {
                                Text(
                                    cabang, style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // KOSONG = pertahankan tanggal yang ada. Sengaja tidak di-prefill
            // dengan hari ini: memindahkan driver tak selalu berarti menggeser
            // jadwal, dan prefill membuat penggeseran jadi efek samping senyap.
            ExpressiveTextField(
                tanggal, { tanggal = it },
                label = "Jadwal baru (kosongkan = tetap)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            ExpressiveFilledButton(
                onClick = {
                    vm.reassign(job.id, driverId, driverName, tanggal) {
                        mode = ""; driverId = ""; driverName = ""; tanggal = ""
                    }
                },
                enabled = !submitting && driverId.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Pindahkan ke Driver Ini")
            }
        }
    }
}

/**
 * Timestamp backend → epoch millis, penafsiran ikut BENTUK nilainya.
 *
 * Meneruskan ke [parseTimestampMillis] (router yang sama dipakai label
 * notifikasi & umur SPK) alih-alih parser lokal — dulu berkas ini punya
 * `parseUtcMillis` sendiri yang SELALU menafsir UTC. Sejak backend mengirim WIB
 * polos tanpa penanda (kontrak `tridjaya_shared::waktu`, 2026-07-30), penafsiran
 * itu menaruh `consumerChatAt` 7 jam di MASA DEPAN: `elapsedMin` jadi negatif,
 * jadi gate serah terima menahan driver ~7 jam + jeda padahal server sudah
 * melepasnya setelah `chatMinMinutes`.
 *
 * Nilai lama ber-`Z` tetap dibaca UTC oleh router yang sama.
 */
private fun parseWaktuMillis(ts: String?): Long? =
    parseTimestampMillis(ts?.trim()?.takeIf { it.isNotEmpty() }?.replace(' ', 'T'))

@Composable
private fun DeliverAction(
    job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean,
    driverChecklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>,
    checklistError: String?
) {
    val id = job.id
    var rating by rememberSaveable { mutableStateOf(5) }
    var comment by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/deliver_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val photoState by vm.state.collectAsState()
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // `ok == false` tak lagi ditelan — lihat [PESAN_KAMERA_TAK_TERSIMPAN].
        if (ok) vm.onDeliverPhotoCaptured(file) else Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
    }
    // 088: foto bukti terima uang (wajib bila job.driverTerimaUang == true)
    val needCash = job.driverTerimaUang == true
    val cashFile = remember { File(context.cacheDir, "delivery/cash_$id.jpg").apply { parentFile?.mkdirs() } }
    val cashUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cashFile) }
    val cashCam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // `ok == false` tak lagi ditelan — lihat [PESAN_KAMERA_TAK_TERSIMPAN].
        if (ok) vm.onCashPhotoCaptured(cashFile) else Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
    }
    // 088: checklist serah-terima stage=driver (fail-open bila kosong)
    val hasil = rememberSaveable(driverChecklist, saver = petaJawabanSaver) {
        mutableStateMapOf<String, String>().apply { driverChecklist.forEach { put(it.id, "ok") } }
    }
    val catatan = rememberSaveable(driverChecklist, saver = petaJawabanSaver) { mutableStateMapOf<String, String>() }

    photoState.deliverPhoto?.takeIf { !photoState.deliverPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakeDeliverPhoto() }, onConfirm = { vm.confirmDeliverPhoto() })
    }
    photoState.cashPhoto?.takeIf { !photoState.cashPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakeCashPhoto() }, onConfirm = { vm.confirmCashPhoto() })
    }

    Text("Serah Terima", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    // Fan-out `deliver` (2026-08-05): foto + rating + checklist SEKALI berlaku
    // untuk semua unit SPK ini yang kamu pegang. Yang TIDAK ikut: unit driver
    // lain. Gate-nya (chat H-1 / checklist / foto uang) dinilai server atas
    // unit yang dipanggil — karena itu buka SPK dari unit COD kalau ada, supaya
    // foto uangnya tetap ditagih. Baris DB tetap per unit, jadi hitungan
    // kiriman driver tidak berubah.
    SpkFanOutNote(
        "Foto, rating, dan checklist ini berlaku untuk semua unit SPK ini yang kamu antar sekaligus." +
            if (!needCash) {
                " Kalau SPK ini punya barang COD, buka serah terimanya DARI barang COD itu — " +
                    "foto uang cuma diminta pada barang yang dibuka."
            } else ""
    )
    Spacer(Modifier.height(8.dp))
    GpsStatusRow(photoState) { vm.refreshGps() }
    Spacer(Modifier.height(8.dp))
    PhotoBox(photoState.deliverPhoto, "Foto serah terima (wajib)") { cam.launch(uri) }
    if (needCash) {
        Spacer(Modifier.height(10.dp))
        PhotoBox(photoState.cashPhoto, "Foto serah terima uang (wajib${job.driverTerimaNominal?.let { " · ${rupiah(it)}" } ?: ""})") { cashCam.launch(cashUri) }
    }
    // FAIL-HARD checklist (088): gagal fetch → blok submit + retry. Tanpa ini
    // checklist null terkirim → 400 backend tanpa petunjuk (temuan audit).
    if (checklistError != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Gagal memuat checklist serah terima: $checklistError",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(6.dp))
        ExpressiveOutlinedButton(onClick = { vm.loadDriverChecklist(job) }, modifier = Modifier.fillMaxWidth()) {
            Text("Muat Ulang Checklist")
        }
    }
    if (driverChecklist.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Checklist Serah Terima", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        driverChecklist.sortedBy { it.urutan }.forEach { item ->
            Spacer(Modifier.height(6.dp))
            Text(item.itemLabel + if (item.wajib) " *" else "", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ok" to "OK", "tidak" to "Tidak", "na" to "N/A").forEach { (k, l) ->
                    val sel = hasil[item.id] == k
                    Surface(onClick = { hasil[item.id] = k }, shape = RoundedCornerShape(50),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                        Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                }
            }
            if (hasil[item.id] == "tidak") {
                Spacer(Modifier.height(4.dp))
                ExpressiveTextField(catatan[item.id].orEmpty(), { catatan[item.id] = it }, label = "Catatan (wajib untuk Tidak)", modifier = Modifier.fillMaxWidth())
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Rating pengiriman (wajib)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Row {
        (1..5).forEach { i ->
            Icon(
                if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "Rating $i", tint = Color(0xFFF6B10A),
                modifier = Modifier.size(36.dp).clickable { rating = i }.padding(2.dp)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    ExpressiveTextField(comment, { comment = it }, label = "Komentar (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
    // 088: gate chat H-1 — PARITAS backend `deliver_job` (wajib chat ≥1 jam
    // sebelum serah terima; admin bypass). Gate klien AKTIF hanya bila
    // kill-switch server ON (context.driverGateEnabled — review 2026-07-23:
    // hard-block sepihak saat prod OFF memaksa driver menunggu 60 mnt utk
    // syarat yang server tidak menegakkan). Server OFF / backend lama tanpa
    // field → warning pembiasaan saja, tombol tetap aktif.
    val gate088 = job.driverTerimaUang != null // penanda backend 088 aktif
    val serverGateOn = photoState.deliveryContext?.driverGateEnabled == true
    // Jeda minimum chat dari SERVER (menit; 0 = chat wajib tanpa tunggu —
    // pelonggaran live testing 2026-07-23). Backend lama tanpa field → 60.
    val chatMinMin: Long = (photoState.deliveryContext?.chatMinMinutes ?: 60).coerceAtLeast(0).toLong()
    val chatMillis = parseWaktuMillis(job.consumerChatAt)
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(chatMillis) {
        while (true) { nowMillis = System.currentTimeMillis(); delay(30_000) }
    }
    val chatWaitLeftMin: Long? = if (!gate088 || chatMillis == null || chatMinMin <= 0) null else {
        val elapsedMin = (nowMillis - chatMillis) / 60_000
        // coerceIn: jam device mundur (skew) jangan menampilkan sisa > jeda penuh.
        if (elapsedMin >= chatMinMin) null else (chatMinMin - elapsedMin).coerceIn(1, chatMinMin)
    }
    val chatBlocked = serverGateOn && !vm.isAdminViewer && gate088 &&
        (job.consumerChatAt == null || chatWaitLeftMin != null)
    // Teks status chat H-1 — hanya utk non-admin (admin di-bypass server, pesan
    // bergaya blocking di atas tombol aktif = menyesatkan).
    if (!vm.isAdminViewer && gate088 && job.consumerChatAt == null) {
        Spacer(Modifier.height(8.dp))
        val syarat = if (chatMinMin > 0) " (wajib ≥$chatMinMin menit sebelum serah terima)" else ""
        if (serverGateOn) {
            Text("Belum chat konsumen — tandai chat dulu$syarat.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        } else {
            Text("Belum chat konsumen — biasakan tandai chat dulu (aturan wajib segera diberlakukan).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (!vm.isAdminViewer && serverGateOn && chatWaitLeftMin != null) {
        Spacer(Modifier.height(8.dp))
        Text("Chat konsumen tercatat — tunggu ±$chatWaitLeftMin menit lagi (syarat minimal $chatMinMin menit).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(14.dp))
    val missingCatatan = driverChecklist.any { hasil[it.id] == "tidak" && catatan[it.id].orEmpty().isBlank() }
    val hasPhoto = photoState.deliverPhoto != null && photoState.deliverPhotoConfirmed
    val hasCashPhoto = photoState.cashPhoto != null && photoState.cashPhotoConfirmed
    // Checklist fail-hard hanya saat gate server ON (server OFF menerima
    // checklist null — jangan kunci seluruh serah terima gara-gara fetch gagal).
    val checklistBlocked = serverGateOn && checklistError != null
    val canDeliver = hasPhoto && (!needCash || hasCashPhoto) && !missingCatatan &&
        !chatBlocked && !checklistBlocked
    ExpressiveFilledButton(
        onClick = {
            val bodies = driverChecklist.map { com.krisoft.tridjayaelektronik.data.model.PdiChecklistItemBody(item = it.itemLabel, hasil = hasil[it.id] ?: "ok", catatan = catatan[it.id]?.trim()?.ifBlank { null }) }
            vm.deliver(id, rating, comment, bodies) {}
        },
        enabled = !submitting && canDeliver, modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(when {
            checklistBlocked -> "Muat ulang checklist dulu"
            chatBlocked && job.consumerChatAt == null -> "Tandai chat konsumen dulu"
            chatBlocked -> "Tunggu ±$chatWaitLeftMin mnt (chat H-1)"
            !hasPhoto -> "Ambil foto dulu"
            needCash && !hasCashPhoto -> "Ambil foto uang dulu"
            missingCatatan -> "Isi catatan item 'Tidak'"
            else -> "Tandai Terkirim"
        })
    }
}

/** Diambil sendiri (2026-07-24): konsumen ambil unit langsung di cabang — DC/admin
 *  tandai selesai, foto+rating wajib (sama standar [DeliverAction]), TANPA gate
 *  chat-H1/checklist-driver/cash-photo (tak relevan, bukan diantar). Transisi
 *  langsung `pending_scheduling → delivered`. */
@Composable
private fun SelfPickupCompleteAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    val id = job.id
    var rating by rememberSaveable { mutableStateOf(5) }
    var comment by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/selfpickup_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val photoState by vm.state.collectAsState()
    // Reuse slot foto [DeliveryFlowViewModel.onDeliverPhotoCaptured] — job self_pickup
    // (pending_scheduling) tak pernah bareng job in_transit (deliver) di layar yang sama.
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // `ok == false` tak lagi ditelan — lihat [PESAN_KAMERA_TAK_TERSIMPAN].
        if (ok) vm.onDeliverPhotoCaptured(file) else Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
    }

    photoState.deliverPhoto?.takeIf { !photoState.deliverPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakeDeliverPhoto() }, onConfirm = { vm.confirmDeliverPhoto() })
    }

    Text("Selesai — Diambil Sendiri", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("Konsumen mengambil unit langsung di cabang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    PhotoBox(photoState.deliverPhoto, "Foto serah terima (wajib)") { cam.launch(uri) }
    Spacer(Modifier.height(12.dp))
    Text("Rating (wajib)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Row {
        (1..5).forEach { i ->
            Icon(
                if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "Rating $i", tint = Color(0xFFF6B10A),
                modifier = Modifier.size(36.dp).clickable { rating = i }.padding(2.dp)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    ExpressiveTextField(comment, { comment = it }, label = "Komentar (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    val hasPhoto = photoState.deliverPhoto != null && photoState.deliverPhotoConfirmed
    ExpressiveFilledButton(
        onClick = { vm.selfPickupComplete(id, rating, comment) {} },
        enabled = !submitting && hasPhoto, modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(if (!hasPhoto) "Ambil foto dulu" else "Tandai Selesai")
    }
}

/** 088: chat konsumen H-1 — wajib ≥1 jam sebelum serah terima (gate backend). */
@Composable
private fun ChatConsumerCard(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Chat Konsumen (H-1)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (job.consumerChatAt != null) {
                Text("Sudah chat: ${job.consumerChatAt}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF12B76A), fontWeight = FontWeight.SemiBold)
            } else {
                Text("Wajib chat konsumen minimal 1 jam sebelum serah terima.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val phone = job.customerPhone?.filter { it.isDigit() }.orEmpty()
                    .let { if (it.startsWith("0")) "62" + it.drop(1) else it }
                ExpressiveOutlinedButton(
                    onClick = { if (phone.isNotBlank()) runCatching { uriHandler.openUri("https://wa.me/$phone") } },
                    enabled = phone.isNotBlank(), modifier = Modifier.weight(1f)
                ) { Text("Chat WA") }
                if (job.consumerChatAt == null) {
                    ExpressiveFilledButton(onClick = { vm.chatConsumer(job.id) }, enabled = !submitting, modifier = Modifier.weight(1f)) {
                        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Tandai Sudah Chat")
                    }
                }
            }
        }
    }
}

/** Status GPS detail (pola sama kartu status di AttendanceScreen) — dipakai di atas [PhotoBox] pada
 *  PDI/serah-terima supaya user tahu lokasi sudah terkunci (+akurasi) SEBELUM jepret, bukan baru
 *  ketauan gagal setelah lihat watermark. */
@Composable
private fun GpsStatusRow(state: DeliveryFlowUiState, onRetry: () -> Unit) {
    val context = LocalContext.current

    // Setelah user diarahkan ke Pengaturan izin & kembali (ON_RESUME), coba lagi otomatis — tanpa
    // ini "Buka Pengaturan" jadi jalan buntu: user balik ke app tapi kartu masih nampilkan status
    // ditolak yang lama sampai keluar-masuk layar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.gpsDenied) onRetry()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val label: String
    val detail: String
    val fg: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    when {
        state.gpsDenied -> {
            label = "Izin lokasi ditolak"
            detail = "Aktifkan izin lokasi untuk HP ini di Pengaturan, lalu tekan Perbarui."
            fg = Color(0xFFF04438); icon = Icons.Rounded.LocationOff
        }
        state.gpsLocating -> {
            label = "Mendeteksi lokasi…"
            detail = "Mohon tunggu, GPS sedang mencari sinyal."
            fg = MaterialTheme.colorScheme.onSurfaceVariant; icon = Icons.Rounded.MyLocation
        }
        state.gpsError != null -> {
            label = "Gagal ambil lokasi"
            detail = state.gpsError
            fg = Color(0xFFB5670C); icon = Icons.Rounded.LocationOff
        }
        state.gpsLat != null && state.gpsLng != null -> {
            label = "Lokasi terkunci" + (state.gpsAccuracyM?.let { " · akurasi ±${it.toInt()}m" } ?: "")
            // Alamat terbaca (kota/kabupaten/tempat) diutamakan — angka lat/lng cuma fallback
            // selagi geocode masih jalan atau gagal (offline dsb.), bukan tampilan utama.
            detail = when {
                state.gpsAddress != null -> state.gpsAddress
                state.gpsAddressLoading -> "Mencari nama lokasi…"
                else -> "Lat %.6f, Lng %.6f".format(state.gpsLat, state.gpsLng)
            }
            fg = Color(0xFF12B76A); icon = Icons.Rounded.MyLocation
        }
        else -> {
            label = "Lokasi belum diambil"
            detail = "Foto akan diberi watermark tanpa koordinat."
            fg = MaterialTheme.colorScheme.onSurfaceVariant; icon = Icons.Rounded.LocationOff
        }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = fg.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.gpsLocating) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
            else Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!state.gpsLocating) {
                if (state.gpsDenied) {
                    // Sekali user pilih "jangan tanya lagi", sistem tak pernah munculkan dialog izin
                    // lagi — satu-satunya jalan keluar adalah halaman Pengaturan izin app ini.
                    TextButton(onClick = {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                        )
                    }) { Text("Buka Pengaturan") }
                } else {
                    TextButton(onClick = onRetry) { Text("Perbarui") }
                }
            }
        }
    }
}

@Composable
private fun PhotoBox(bitmap: Bitmap?, label: String, onCapture: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Surface(onClick = onCapture, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().height(170.dp)) {
        if (bitmap != null) {
            // Pola sama AttendanceScreen: render Bitmap hasil watermark LANGSUNG dari state, bukan
            // baca-ulang file lewat Coil — tak ada cache untuk stale, tak ada race timing capture.
            // alignment=BottomCenter (bukan default Center): watermark digambar di bar PALING BAWAH
            // gambar asli (lihat PhotoWatermark.drawWatermark) — foto portrait di-crop ke kotak
            // pendek-lebar ini akan kehilangan tepi atas+bawah kalau alignment default Center dipakai,
            // memotong habis bar watermark. BottomCenter memotong dari ATAS saja, bar selalu utuh.
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = "Foto",
                contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp)); Text("Ketuk untuk ambil foto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Review pasca-jepret full-screen: kamera sistem (bukan kamera dalam-app) tidak bisa ditempeli
 * overlay saat live — jadi konfirmasi "gambarnya sudah benar" (watermark kebaca dsb.) dilakukan DI
 * SINI, langsung setelah jepret, sebelum foto dianggap final. `ContentScale.Fit` (bukan Crop seperti
 * [PhotoBox]) sengaja dipakai supaya seluruh gambar + bar watermark kelihatan utuh tanpa terpotong.
 */
@Composable
private fun PhotoReviewDialog(bitmap: Bitmap, onRetake: () -> Unit, onConfirm: () -> Unit) {
    // Inset dibaca DI LUAR Dialog — dari jendela Activity, bukan jendela dialog.
    // Jendela dialog sering melaporkan systemBars = 0 (percobaan sebelumnya
    // membacanya dari dalam dan tombolnya TETAP tertutup tombol navigasi
    // 3-tombol; laporan lapangan 2026-08-03). Jendela Activity selalu tahu
    // tinggi bar yang sebenarnya.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    Dialog(
        onDismissRequest = onRetake,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // decorFitsSystemWindows=false WAJIB: defaultnya true bikin dialog "auto-fit" system bar,
        // tapi di device gesture-nav banyak OEM window tetap dianggap sudah fit padahal gesture pill
        // masih digambar DI ATAS konten dialog — WindowInsets.systemBars di bawah lalu terbaca 0,
        // tombol nempel ke tepi bawah persis walau padding-nya sudah ditulis (bug lama, komentar
        // sebelumnya salah kira sudah beres). Matikan auto-fit → insets sungguhan didorong ke sini.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
        }
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Cek hasil foto — pastikan watermark jam & lokasi terbaca",
                    color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 12.dp + systemBars.calculateTopPadding())
                )
                Image(
                    bitmap = bitmap.asImageBitmap(), contentDescription = "Pratinjau foto",
                    contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            // Naikkan tombol dari tepi layar: inset navigasi + jarak nyaman.
                            // `coerceAtLeast` = lantai pengaman kalau inset TETAP
                            // terbaca 0 di suatu OEM: 24.dp masih menyisakan jarak
                            // walau tombol navigasi tak terlaporkan sama sekali.
                            bottom = 32.dp + systemBars.calculateBottomPadding().coerceAtLeast(24.dp),
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveOutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Ambil Ulang") }
                    ExpressiveFilledButton(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Pakai Foto Ini") }
                }
            }
        }
    }
}

// ── Input SPK ────────────────────────────────────────────────────────────────

@Composable
fun CreateSpkScreen(
    onBack: () -> Unit,
    /** PDI Mandiri (2026-07-26): dipanggil alih-alih [onBack] kalau SPK yang baru
     *  dibuat butuh sales langsung isi form PDI-nya sendiri (bukan skip). */
    onCreated: (String) -> Unit = {},
    viewModel: DeliveryFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadDeliveryContextForCreate() }

    // Header — Pelanggan
    var pelanggan by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var mapUrl by remember { mutableStateOf("") }
    var nik by remember { mutableStateOf("") }
    var sosTiktok by remember { mutableStateOf("") }
    var sosFb by remember { mutableStateOf("") }
    var sosIg by remember { mutableStateOf("") }
    var keterangan by remember { mutableStateOf("") }
    // Metode pengiriman (2026-07-24): driver biasa (default) | self_pickup | sales_delivery.
    var deliveryMethodSel by remember { mutableStateOf("driver") }
    // Lokasi pembayaran (2026-08-12, migrasi 213). Default form = "asal"
    // (cabang login sales) atas keputusan user — SENGAJA BEDA dari default
    // server, yang membaca field ABSEN sbg "tujuan" demi menjaga perilaku SPK
    // lama. Nilai yang benar-benar dikirim datang dari `lokasiBayarKontrol` di
    // bawah, BUKAN langsung dari state ini: sebagian keadaan memaksa nilainya.
    var lokasiBayarSel by remember { mutableStateOf(LOKASI_BAYAR_ASAL) }
    // Barang multi-unit
    var spkCabang by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<SpkItemDraft>()) }
    var barangSearch by remember { mutableStateOf("") }
    var brokerSearch by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var sec1 by remember { mutableStateOf(true) }
    var sec2 by remember { mutableStateOf(true) }
    // Tiga kolom sosial media disembunyikan sampai diminta (2026-08-12). Ini
    // HANYA "sudah pernah dibuka" — syarat tampil sebenarnya ikut memeriksa
    // isian ketiga kolomnya, supaya tak ada nilai yang terkirim dari balik
    // bagian yang tertutup.
    var sosmedTerbuka by remember { mutableStateOf(false) }
    var gantiCabangTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.deliveryContext) {
        if (spkCabang.isBlank()) state.deliveryContext?.kodeDealer?.let { spkCabang = it }
    }
    LaunchedEffect(barangSearch, spkCabang) { delay(300); viewModel.searchStok(barangSearch, spkCabang) }
    LaunchedEffect(brokerSearch) { delay(300); viewModel.searchBrokers(brokerSearch) }

    // PDI Mandiri: barang pertama ber-toggle "PDI Mandiri" (pdiRequired=false) ->
    // langsung lompat ke form PDI barang itu (bukan balik ke daftar) supaya sales
    // isi checklist+foto saat itu juga. Tak ada barang mandiri -> balik daftar.
    // Sejak backend 2026-07-27 syarat metode self_pickup/sales_delivery DIBUANG:
    // rute skip PDI tak ada lagi, jadi pdiRequired=false pada metode "driver" pun
    // kini mendarat di pending_pdi dan menunggu sales. Tanpa perubahan ini SPK
    // begitu nyangkut senyap di antrian PDI — sales balik ke daftar, tak tahu
    // masih ada yang harus dia kerjakan.
    // id diambil LANGSUNG dari `result.ids` (sejajar `kodePengiriman`, backend
    // 2026-07-26) — BUKAN reverse-lookup search antrian (percobaan pertama:
    // rapuh, kena filter status role-scoped sales + limit pagination, gagal
    // senyap tanpa error yang kelihatan).
    LaunchedEffect(state.lastCreateResult) {
        val result = state.lastCreateResult ?: return@LaunchedEffect
        val mandiriBaris = items.indexOfFirst { !it.pdiRequired }.takeIf { it >= 0 }?.plus(1)
        val targetIdx = if (mandiriBaris != null) {
            result.kodePengiriman.indexOfFirst { it.contains("-${mandiriBaris}u") }
        } else -1
        val id = result.ids.getOrNull(targetIdx)
        if (id != null) onCreated(id) else onBack()
    }

    fun applyCabangChange(next: String) {
        spkCabang = next; items = emptyList(); barangSearch = ""
        // Lokasi pembayaran TERIKAT ke pasangan cabang asal↔tujuan, jadi ia
        // TIDAK boleh bertahan lintas-cabang: "tujuan" yang dipilih untuk
        // Soklat akan terbaca sebagai "tujuan" untuk Cikampek tanpa sales
        // pernah memilihnya lagi. Balik ke default form.
        lokasiBayarSel = LOKASI_BAYAR_ASAL
        viewModel.searchStok("", next); viewModel.clearSerialCache()
    }

    val totalUnits = items.sumOf { it.qtyInt ?: 0 }
    // Dihitung ulang tiap recomposition: hasilnya bergantung pada cabang yang
    // dipilih DAN pada isi barang (seluruhnya COD full memaksa "tujuan").
    val lokasiBayar = lokasiBayarKontrol(
        pilihanUser = lokasiBayarSel,
        kodeDealerAsal = state.deliveryContext?.kodeDealer,
        namaDealerAsal = state.deliveryContext?.dealerName,
        spkCabang = spkCabang,
        semuaCodFull = semuaCodFullPayment(items),
    )
    val itemsValid = items.isNotEmpty() && items.all { it.issues().isEmpty() }
    val mapUrlWajib = deliveryMethodSel == "sales_delivery"
    // Isi yang SUDAH diketik tapi tak bisa dibuka driver = salah, apa pun
    // metode kirimnya. Sengaja TIDAK ikut `mapUrlWajib`: server menolaknya
    // untuk semua metode (`create_delivery`, 2026-08-30), dan menandainya hanya
    // pada satu metode berarti sales metode lain baru tahu setelah 400.
    val mapUrlSalahBentuk = mapUrl.isNotBlank() && !mapsTerpakai(mapUrl)
    val mapUrlKurang = (mapUrlWajib && mapUrl.isBlank()) || mapUrlSalahBentuk
    val blocker = spkSubmitBlocker(
        pelanggan = pelanggan, telepon = telepon, nik = nik, mapUrl = mapUrl,
        deliveryMethod = deliveryMethodSel, spkCabang = spkCabang,
        itemsCount = items.size, itemsValid = itemsValid, totalUnits = totalUnits,
    )
    val canSubmit = blocker == null

    TridjayaCollapsibleHeader(title = "Input SPK", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            contentModifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpkSection("1. Pelanggan & cara kirim", sec1, { sec1 = !sec1 }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpkGrupLabel("Siapa yang membeli")
                    // Dirapikan saat FOKUS LEPAS, bukan tiap ketukan tombol:
                    // sales melihat hasil seragamnya sebelum menyimpan (jadi
                    // tak kaget kalau berubah), tapi kursornya tak pernah
                    // dipindahkan di tengah mengetik. Nilai yang dikirim
                    // dinormalkan lagi saat submit — ini murni pratinjau.
                    ExpressiveTextField(
                        pelanggan, { pelanggan = it }, label = "Nama pelanggan *",
                        modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                            if (!f.isFocused && pelanggan.isNotBlank()) pelanggan = rapikanNama(pelanggan)
                        },
                    )
                    ExpressiveTextField(
                        telepon, { telepon = it }, label = "No. HP *",
                        keyboardType = KeyboardType.Phone,
                        supportingText = "Disimpan sebagai 62… (mis. 6285172083358)",
                        modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                            if (!f.isFocused && telepon.isNotBlank()) telepon = rapikanNomorHp(telepon)
                        },
                    )
                    ExpressiveTextField(alamat, { alamat = it }, label = "Alamat", singleLine = false, modifier = Modifier.fillMaxWidth())

                    // Metode pengiriman naik ke ATAS Link Maps (2026-08-12): dialah
                    // yang menentukan wajib-tidaknya link itu. Urutan lama menyuruh
                    // sales mengisi field dulu, baru memberitahu bahwa ia wajib —
                    // dan bintang "*" pada field yang sudah dilewati tak pernah
                    // terbaca lagi.
                    SpkGrupLabel("Cara barang sampai ke konsumen")
                    DeliveryMethodDropdown(deliveryMethodSel) { next ->
                        deliveryMethodSel = next
                        // COD = uang diambil DRIVER — tak relevan tanpa driver (diambil
                        // sendiri/sales antar sendiri). Clear biar tak nyangkut/ke-submit
                        // diam-diam (koreksi 2026-07-26).
                        if (next != "driver") {
                            items = items.map { it.copy(driverTerimaUang = false, codPaymentMode = "", codDpAmount = "") }
                        }
                    }
                    ExpressiveTextField(
                        mapUrl, { mapUrl = it },
                        label = if (mapUrlWajib) "Link Lokasi Maps *" else "Link Lokasi Maps",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth(),
                        // Bentuk salah menyala SEKETIKA (tanpa menunggu
                        // `attemptedSubmit`): sales sedang menatap field yang
                        // baru ia isi, dan itu momen termurah untuk memperbaiki.
                        // Yang KOSONG tetap menunggu percobaan kirim — menyalakan
                        // merah pada field yang belum sempat diisi cuma derau.
                        isError = mapUrlSalahBentuk || (attemptedSubmit && mapUrlKurang),
                        supportingText = when {
                            mapUrlSalahBentuk ->
                                "Belum berupa link atau koordinat — tempel link dari Google Maps, atau kosongkan dulu."
                            mapUrlWajib ->
                                "Wajib untuk Sales Antar Sendiri — tanpa ini job masuk antrian Delivery Control, bukan ke kamu."
                            else ->
                                "Boleh dikosongkan — kamu akan diingatkan lagi setelah PDI selesai."
                        }
                    )

                    SpkGrupLabel("Data tambahan (boleh dilewati)")
                    // NIK KTP = 16 digit; backend menolak <16 digit (delivery.rs
                    // "NIK konsumen minimal 16 digit angka") — filter + gate di sini
                    // supaya tak mentok 400 saat submit.
                    ExpressiveTextField(
                        nik, { nik = it.filter(Char::isDigit).take(16) }, label = "NIK",
                        keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth(),
                        isError = nik.isNotEmpty() && nik.length < 16,
                        supportingText = if (nik.isNotEmpty() && nik.length < 16) "NIK harus 16 digit angka (${nik.length}/16)" else null
                    )
                    // Tiga kolom sosial media jarang dipakai tapi selalu memakan
                    // tiga baris. Syarat bukanya memuat "sudah ada isinya", jadi
                    // nilai yang pernah diketik TAK PERNAH bisa ikut terkirim dari
                    // balik bagian yang tertutup.
                    if (sosmedTerbuka || sosTiktok.isNotBlank() || sosFb.isNotBlank() || sosIg.isNotBlank()) {
                        ExpressiveTextField(sosTiktok, { sosTiktok = it }, label = "TikTok", modifier = Modifier.fillMaxWidth())
                        ExpressiveTextField(sosFb, { sosFb = it }, label = "Facebook", modifier = Modifier.fillMaxWidth())
                        ExpressiveTextField(sosIg, { sosIg = it }, label = "Instagram", modifier = Modifier.fillMaxWidth())
                    } else {
                        TextButton(onClick = { sosmedTerbuka = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Akun sosial media pelanggan")
                        }
                    }
                    ExpressiveTextField(keterangan, { keterangan = it }, label = "Keterangan (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
                }
            }

            SpkSection("2. Barang (${items.size} barang · $totalUnits unit)", sec2, { sec2 = !sec2 }) {
                SpkGrupLabel("Cabang stok & tempat bayar")
                Spacer(Modifier.height(8.dp))
                CabangSelector(
                    selected = spkCabang,
                    onSelect = { next ->
                        if (next.isBlank() || next == spkCabang) return@CabangSelector
                        if (items.isNotEmpty()) gantiCabangTarget = next else applyCabangChange(next)
                    }
                )
                Spacer(Modifier.height(10.dp))
                // Merender spasi ekornya sendiri — saat tak ada yang perlu
                // dikatakan (cabang belum dipilih) ia benar-benar nol tinggi,
                // bukan celah kosong yang terbaca sebagai kerusakan.
                LokasiBayarField(lokasiBayar) { lokasiBayarSel = it }
                if (spkCabang.isNotBlank()) {
                    SpkGrupLabel("Tambah barang")
                    Spacer(Modifier.height(8.dp))
                    // Cabang barang dilekatkan saat SUBMIT dari `spkCabang`, bukan dibawa
                    // tiap baris — jadi daftar milik cabang lain WAJIB tak bisa ditap sama
                    // sekali. Respons pencarian cabang sebelumnya bisa mendarat setelah
                    // selektor pindah (insiden DLV-M84149DA0: barang Pagaden ter-submit
                    // sebagai Soklat, unitnya masuk antrian PDI cabang yang tak
                    // memegangnya). ViewModel juga membatalkan pencarian lama; penyaring
                    // ini yang membuat invariannya tak bergantung pada urutan balapan.
                    val stokRows = stokRowsForCabang(state.stokResults, state.stokDealer, spkCabang)
                    // Batas server (`MAX_MANUAL_LINES`): hasil pencarian tak
                    // boleh bisa ditap lagi setelah 10 baris. Membiarkannya
                    // ditap = SPK bertambah baris ke-11 yang baru ditolak saat
                    // submit, setelah sales mengisi seluruh detailnya.
                    val barisPenuh = items.size >= MAX_SPK_BARIS
                    ExpressiveTextField(barangSearch, { barangSearch = it }, label = "Cari & tambah barang (min. 2 karakter)", modifier = Modifier.fillMaxWidth())
                    if (barisPenuh) {
                        Text(
                            "Maksimal $MAX_SPK_BARIS barang per SPK — hapus salah satu untuk menambah yang lain.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    when {
                        state.stokLoading -> Text("Mencari…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        state.stokAttempted && stokRows.isEmpty() -> Text("Tidak ditemukan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (stokRows.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            stokRows.forEach { row ->
                                // Baris stok-nol yang muncul HANYA karena sudah punya SPK
                                // berjalan: tampilkan supaya sales tahu barangnya sudah
                                // dipesan (bukan "cabang ini tak punya"), tapi JANGAN bisa
                                // dipilih — memilihnya membuat SPK kedua atas unit yang
                                // sudah dipesan, dan kalau notanya sudah masuk GS, sudah
                                // terjual ke orang lain.
                                val terkunci = row.terkunciKarenaDipesan || barisPenuh
                                Surface(
                                    onClick = {
                                        // Prepend + collapse kartu lain (baru = fokus)
                                        items = listOf(newSpkItemDraft(row)) + items.map { it.copy(expanded = false) }
                                        barangSearch = ""
                                        viewModel.ensureSerials(spkCabang, row.kode.trim())
                                    },
                                    enabled = !terkunci,
                                    shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        // 3 baris, BUKAN 1. Nama barang GS menaruh tipe/model di
                                        // BELAKANG ("AC AQUA 1PK AQA-KR9VQCL", "1 SET ACCU DUBSS
                                        // 6-EVF-45.5"), jadi memotongnya di satu baris membuang
                                        // persis bagian yang membedakan satu varian dari varian
                                        // lain — sales melihat beberapa hasil cari yang terlihat
                                        // identik dan tak punya cara memilih yang benar.
                                        Text(row.nama.trim(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        Text("${row.kode} · ${row.kategori} · ${row.merk}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        // Stok + harga langsung di opsi hasil cari (paritas web
                                        // `renderStockRow`): sales tak perlu memilih dulu baru tahu
                                        // barangnya ada berapa. `stok` null = server tak mengirim
                                        // kolomnya -> "-", BUKAN 0 (0 itu pernyataan "habis").
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val stok = row.stok
                                            val stokWarna = when {
                                                stok == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                                stok > 0 -> Color(0xFF12B76A)
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                            Surface(color = stokWarna.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                                                Text(
                                                    "Stok: ${stok?.toString() ?: "-"}",
                                                    color = stokWarna,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                rupiah(row.harga),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (row.dipesan > 0) {
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    color = Color(0xFFF79009).copy(alpha = 0.16f),
                                                    shape = RoundedCornerShape(50),
                                                ) {
                                                    Text(
                                                        "Sudah dipesan · ${row.dipesan} SPK",
                                                        color = Color(0xFFB54708),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        if (terkunci) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Unit ini sudah masuk SPK lain. Kalau SPK itu batal, minta admin " +
                                                    "membatalkannya dulu — stok terbaca lagi setelah GS diperbarui.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("Pilih Cabang SPK dulu untuk mencari stok.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (items.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    SpkGrupLabel("Barang di SPK ini (${items.size})")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEachIndexed { idx, item ->
                            val key = "$spkCabang|${item.kodeBarang}"
                            val usedElsewhere = items.filterIndexed { i, o -> i != idx && o.serialNumber.isNotBlank() }.map { it.serialNumber }
                            SpkItemCard(
                                index = idx,
                                item = item,
                                issues = if (attemptedSubmit) item.issues() else emptyList(),
                                serialOptions = (state.serialOptions[key] ?: emptyList()).filter { it.serialNumber !in usedElsewhere },
                                brokerResults = state.brokerResults,
                                brokerSearch = brokerSearch,
                                onBrokerSearch = { brokerSearch = it },
                                onUpdate = { updated ->
                                    // Maks 1 kartu expand — expand kartu ini = collapse lainnya
                                    // (state pencarian broker dibagi bersama; cegah bocor antar kartu).
                                    val collapseOthers = updated.expanded && !item.expanded
                                    items = items.mapIndexed { i, o ->
                                        if (i == idx) updated else if (collapseOthers) o.copy(expanded = false) else o
                                    }
                                },
                                onRemove = { items = items.filterIndexed { i, _ -> i != idx } },
                                onSerialFocus = { viewModel.ensureSerials(spkCabang, item.kodeBarang) },
                                uploadPoPhoto = { file -> viewModel.uploadPoPhoto(file) },
                                uploadBuktiAcc = { file, dariGaleri ->
                                    viewModel.uploadBuktiAccPhoto(file, dariGaleri)
                                },
                                deliveryMethod = deliveryMethodSel,
                            )
                        }
                    }
                }
            }

            state.actionError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            if (attemptedSubmit) blocker?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            ExpressiveFilledButton(
                onClick = {
                    attemptedSubmit = true
                    if (!canSubmit) {
                        // Membuka apa yang harus diperbaiki, bukan cuma
                        // menampilkan kalimatnya. Dua kartu seksi bisa tertutup
                        // dan hanya SATU kartu barang boleh terbuka sekaligus,
                        // jadi pesan merah di bawah tombol bisa menunjuk field
                        // yang tak ada di layar sama sekali — sales membaca
                        // "cek tanda merah di kartu" tanpa satu pun tanda merah
                        // yang bisa dilihat.
                        if (spkBlockerDiPelanggan(pelanggan, telepon, nik, mapUrl, deliveryMethodSel)) {
                            sec1 = true
                        } else {
                            sec2 = true
                            val rusak = items.indexOfFirst { it.issues().isNotEmpty() }
                            if (rusak >= 0) items = items.mapIndexed { i, o -> o.copy(expanded = i == rusak) }
                        }
                        return@ExpressiveFilledButton
                    }
                    val body = CreateDeliveryBody(
                        // Diseragamkan di sini, bukan saat mengetik: mengubah
                        // teks di tengah pengetikan memindahkan kursor dan
                        // justru bikin salah ketik. Lihat `FormatKonsumen.kt`.
                        customerName = rapikanNama(pelanggan), customerPhone = rapikanNomorHp(telepon),
                        customerAddress = alamat.trim().ifBlank { null },
                        customerMapUrl = mapUrl.trim().ifBlank { null },
                        customerNik = nik.trim().ifBlank { null },
                        salesNik = null,
                        deliveryMethod = deliveryMethodSel.takeIf { it != "driver" },
                        // Diisi EKSPLISIT dan selalu non-null: Retrofit Json
                        // memakai `encodeDefaults = false`, jadi field yang
                        // dibiarkan default TIDAK ikut terkirim tanpa error apa
                        // pun. Nilainya dari `lokasiBayarKontrol`, bukan dari
                        // state tombol — sebagian keadaan memaksanya.
                        lokasiPembayaran = lokasiBayar.nilai,
                        sosmedTiktok = sosTiktok.trim().ifBlank { null },
                        sosmedFacebook = sosFb.trim().ifBlank { null },
                        sosmedInstagram = sosIg.trim().ifBlank { null },
                        keterangan = keterangan.trim().ifBlank { null },
                        items = items.map { it.toItemBody(spkCabang, BranchRegions.dealerRegion(spkCabang)) }
                    )
                    viewModel.createSpk(body)
                },
                enabled = !state.submitting, modifier = Modifier.fillMaxWidth()
            ) {
                if (state.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (totalUnits > 0) "Catat Penjualan ($totalUnits unit)" else "Catat Penjualan")
            }
            Text("Tiap unit fisik jadi baris antrian PDI terpisah.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    gantiCabangTarget?.let { next ->
        AlertDialog(
            onDismissRequest = { gantiCabangTarget = null },
            title = { Text("Ganti cabang?", fontWeight = FontWeight.Bold) },
            text = { Text("Ganti cabang akan mengosongkan semua barang terpilih. Lanjutkan?") },
            confirmButton = { TextButton(onClick = { applyCabangChange(next); gantiCabangTarget = null }) { Text("Ya") } },
            dismissButton = { TextButton(onClick = { gantiCabangTarget = null }) { Text("Batal") } }
        )
    }
}

/**
 * Judul kelompok DI DALAM kartu Input SPK ("1. Pelanggan…" / "2. Barang…").
 *
 * Menyebut apa yang sedang diputuskan ("Cara barang sampai ke konsumen"),
 * bukan nama fieldnya — kartu yang isinya sepuluh kolom sederajat memaksa sales
 * membaca semuanya untuk tahu mana yang relevan buat SPK yang sedang ia buat.
 *
 * Warna `primary` supaya jelas ini penanda kelompok, bukan label field ke-sekian
 * (label field memakai warna teks biasa). Token tema, bukan hex.
 */
@Composable
private fun SpkGrupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Kartu section collapsible untuk Input SPK — header tap buka/tutup isi. */
@Composable
private fun SpkSection(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** Selektor Cabang SPK — wajib, tanpa opsi kosong. Pola visual mirror
 *  `OptionDropdownField` (`ui/leads/AddLeadScreen.kt`), grouped per region.
 *  `internal` + [label] sejak layar Input Serial Number ikut memilih cabang
 *  (registry SN terpusat) — daftar 13 cabang cukup punya SATU selektor. */
@Composable
internal fun CabangSelector(
    selected: String,
    onSelect: (String) -> Unit,
    label: String = "Cabang SPK *"
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = BranchRegions.DEALER_LABEL[selected] ?: ""
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLabel.ifBlank { "Pilih cabang…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentLabel.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BranchRegions.cabangOptionsByRegion().forEach { group ->
                    Text(
                        group.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    group.cabang.forEach { c ->
                        DropdownMenuItem(text = { Text(c.label) }, onClick = { onSelect(c.kodeDealer); expanded = false })
                    }
                }
            }
        }
    }
}

/** Dropdown Metode Pengiriman (2026-07-24) — driver biasa / diambil sendiri / sales
 *  antar sendiri (pola sama [CabangSelector]). */
@Composable
private fun DeliveryMethodDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "driver" to "Driver (standar)",
        "self_pickup" to "Diambil Sendiri",
        "sales_delivery" to "Sales Antar Sendiri",
    )
    val label = options.firstOrNull { it.first == selected }?.second ?: options[0].second
    Column {
        Text("Metode Pengiriman", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (k, l) ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(k); expanded = false })
                }
            }
        }
    }
}

/**
 * Lokasi pembayaran SPK (2026-08-12) — di cabang mana konsumen membayar.
 *
 * SENGAJA BUKAN [CabangSelector]: itu daftar 13 cabang, dan di sini sales tak
 * boleh bisa memilih cabang KETIGA. Hanya dua kemungkinan, dan keduanya sudah
 * ditentukan oleh SPK-nya sendiri.
 *
 * Label tombolnya memakai NAMA CABANG konkret, bukan kata "asal"/"tujuan":
 * sales tahu "Pagaden" dan "Soklat", bukan istilah kolom database. Subteksnya
 * yang menjelaskan perannya.
 *
 * Merender spasi ekornya sendiri supaya keadaan "tak ada yang perlu dikatakan"
 * benar-benar nol tinggi — bukan celah kosong yang terbaca sebagai kerusakan.
 */
@Composable
private fun LokasiBayarField(kontrol: LokasiBayarKontrol, onSelect: (String) -> Unit) {
    if (!kontrol.bolehPilih && kontrol.catatan == null) return
    Column(Modifier.fillMaxWidth()) {
        Text("Lokasi Pembayaran", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        if (kontrol.bolehPilih) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LokasiBayarSegment(
                    judul = kontrol.namaAsal, subteks = "cabang Anda",
                    terpilih = kontrol.nilai == LOKASI_BAYAR_ASAL,
                    modifier = Modifier.weight(1f),
                ) { onSelect(LOKASI_BAYAR_ASAL) }
                LokasiBayarSegment(
                    judul = kontrol.namaTujuan, subteks = "cabang stok",
                    terpilih = kontrol.nilai == LOKASI_BAYAR_TUJUAN,
                    modifier = Modifier.weight(1f),
                ) { onSelect(LOKASI_BAYAR_TUJUAN) }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    kontrol.catatan.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun LokasiBayarSegment(
    judul: String,
    subteks: String,
    terpilih: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (terpilih) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (terpilih) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                judul.ifBlank { "-" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (terpilih) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(subteks, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Dropdown tujuan pengambilan aki — slug enum backend (pola CabangSelector/ItemFincoyDropdown). */
@Composable
private fun AkiTujuanDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Tujuan *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = akiTujuanLabel(selected).let { if (selected.isBlank()) "Pilih tujuan…" else it },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AKI_TUJUAN_OPTIONS.forEach { (slug, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(slug); expanded = false })
                }
            }
        }
    }
}

/** Dropdown opsi form aki (merk/kapasitas) — daftar tetap + opsional "Lainnya…" (ketik manual,
 *  di-render terpisah oleh pemanggil). Pola visual sama [AkiTujuanDropdown]. */
@Composable
private fun AkiOptionDropdown(
    label: String,
    options: List<String>,
    selected: String,
    allowLainnya: Boolean,
    onSelect: (String) -> Unit,
    lainnyaSlug: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    val display = when {
        selected.isBlank() -> "Pilih…"
        allowLainnya && selected == lainnyaSlug -> "Lainnya…"
        else -> selected
    }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = display,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
                }
                if (allowLainnya) {
                    DropdownMenuItem(text = { Text("Lainnya…") }, onClick = { onSelect(lainnyaSlug); expanded = false })
                }
            }
        }
    }
}

// ── Approval Diskon per-baris ────────────────────────────────────────────────
// BATAS_RINGKAS pindah ke DiskonPotongan.kt bersama `ringkasDaftar` — barang
// yang belum tuntas tak boleh ikut terpotong (tombolnya ada di barisnya).

@Composable
fun DiscountApprovalScreen(
    onBack: () -> Unit,
    onDetailSpk: (String) -> Unit,
    viewModel: DeliveryFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Default HARI_INI atas permintaan user ("pada approval discount tambahkan
    // filter serupa"), DENGAN MATA TERBUKA: antrian ini pekerjaan TERTUNGGAK,
    // jadi default hari-ini menyembunyikan pengajuan kemarin yang belum diputus
    // — persis kelas kekeliruan yang membuat filter ini tak dipasang di antrian
    // kerja per-tahap. Penawarnya baris "menampilkan N pengajuan · <periode>" di
    // bawah chip: approver yang melihat 0 tahu daftarnya sedang TERSARING, bukan
    // habis. Kalau ada laporan "pengajuan diskon hilang", cek chip ini dulu.
    var periode by remember { mutableStateOf(PeriodeSpk.HARI_INI) }
    val rentang = rentangPeriode(periode)
    // Status "pending" TETAP — periode menyaring tanggal, bukan tahap keputusan.
    LaunchedEffect(rentang) { viewModel.loadDiscounts("pending", rentang.dari, rentang.sampai) }
    val muatUlang = { viewModel.loadDiscounts("pending", rentang.dari, rentang.sampai) }
    // Id PENGAJUAN yang sedang ditolak — bukan lagi "anchor" se-SPK: sejak
    // 2026-08-07 penolakan cuma mengenai barang yang ditunjuk.
    var rejectId by remember { mutableStateOf<String?>(null) }
    // Approve JUGA lewat dialog (2026-08-09). Dulu tombol "Setujui" langsung
    // mengirim `note = ""`, dan itu dua masalah sekaligus: (1) `decisionNote`
    // ikut ke WA + push pengaju (discounts.rs `approve_request`), jadi approve
    // dari HP selalu sampai ke sales TANPA konteks sementara approve dari web
    // membawanya; (2) approve tak bisa dianulir (`UPDATE … WHERE status =
    // 'pending'`) dan justru MELEPAS SPK ke PDI kalau barang ini yang terakhir,
    // sedangkan tombolnya cuma 12dp dari "Tolak" yang punya dialog. Yang
    // irreversible-lah yang paling butuh jeda.
    var approveId by remember { mutableStateOf<String?>(null) }

    TridjayaCollapsibleHeader(title = "Approval Diskon", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && state.discounts.isNotEmpty(),
            onRefresh = muatUlang,
            modifier = contentModifier,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
              // Chip + baris jumlah DI LUAR `when` di bawah: keduanya harus tetap
              // terlihat saat daftar kosong/gagal/loading, kalau tidak approver
              // yang tersaring ke "Hari ini" kehilangan jalan kembali ke "Semua".
              PeriodeFilterRow(dipilih = periode, onPilih = { periode = it })
              if (!(state.loading && state.discounts.isEmpty())) {
                  // `total` server, bukan `discounts.size`: responsnya BERHALAMAN
                  // (limit 100), jadi ukuran daftar adalah isi halaman. Menyebutnya
                  // sebagai jumlah membuat approver yakin sudah melihat semuanya.
                  val ditampilkan = state.discounts.size
                  Text(
                      if (state.diskonTotal > ditampilkan)
                          "Menampilkan $ditampilkan dari ${state.diskonTotal} pengajuan · ${periode.keterangan}"
                      else "Menampilkan $ditampilkan pengajuan · ${periode.keterangan}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                  )
              }
              Box(modifier = Modifier.weight(1f)) {
                when {
                state.loading && state.discounts.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.discounts.isEmpty() ->
                    ScrollableCenter {
                        ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = muatUlang)
                    }
                state.discounts.isEmpty() ->
                    ScrollableCenter {
                        ExpressiveEmptyState(
                            icon = { Icon(Icons.Rounded.Discount, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                            title = "Tidak ada pengajuan diskon",
                            // Sudah diputus ≠ tersaring keluar. Menyamakan keduanya
                            // membuat approver menutup layar padahal tunggakan
                            // kemarin masih menunggu di periode lain.
                            subtitle = "Tidak ada pengajuan pending pada ${periode.keterangan}. Ganti periode di atas untuk melihat yang lebih lama."
                        )
                    }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.actionError?.let { item { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) } }
                    // Satu kartu per SPK, tapi keputusannya PER BARANG
                    // (2026-08-07, membalik fan-out 2026-08-06): server tak lagi
                    // menyeret sibling, dan SPK baru lanjut ke PDI setelah
                    // SELURUH barangnya tuntas. Kartu tetap per-SPK karena
                    // itulah satuan yang bergerak — approver perlu melihat
                    // barang mana yang masih menghambat, bukan daftar datar.
                    val grup = state.discounts.groupBy { it.spkBatchKode }.entries.toList()
                    items(grup, key = { it.key }) { (kode, pengajuan) ->
                        // Urut baris: server mengirim `created_at DESC`, jadi
                        // barang ke-3 bisa tampil di atas barang ke-1.
                        val urut = urutPengajuanSpk(pengajuan)
                        DiscountSpkCard(
                            kode = kode,
                            pengajuan = urut,
                            submitting = state.diskonSubmitting,
                            buktiFoto = state.diskonBuktiPhotos,
                            onApprove = { id -> approveId = id },
                            onReject = { id -> rejectId = id },
                            onDetail = { onDetailSpk(kode) },
                        )
                    }
                }
                }
              }
            }
        }
    }

    approveId?.let { id ->
        val target = state.discounts.firstOrNull { it.id == id }
        if (target == null) {
            // Pengajuan bisa lenyap dari daftar selagi dialog terbuka (ganti
            // periode, pull-refresh, atau approver lain menutupnya duluan).
            // Menutup dialog lebih jujur daripada mengirim keputusan atas
            // barang yang tak lagi ada di layar.
            LaunchedEffect(id) { approveId = null }
        } else {
            var note by remember(id) { mutableStateOf("") }
            val seSpk = state.discounts.filter { it.spkBatchKode == target.spkBatchKode }
            val kemajuan = kemajuanSpk(seSpk)
            // −1 karena barang INI belum ikut terhitung tuntas saat dialog dibuka.
            val sisa = (kemajuan.total - kemajuan.tuntas - 1).coerceAtLeast(0)
            AlertDialog(
                onDismissRequest = { approveId = null },
                title = { Text("Setujui diskon barang ini?", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            listOfNotNull(
                                target.jobSummary?.namaBarang?.takeIf { it.isNotBlank() }
                                    ?: "Seluruh SPK",
                                ringkasHarga(target).takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        // Yang tak terlihat dari tombolnya: approve TIDAK bisa
                        // dianulir (server hanya menerima transisi dari
                        // `pending`), dan kalau barang ini yang terakhir,
                        // `release_batch_kalau_tuntas` melepas SELURUH unit SPK
                        // ke antrian PDI sekaligus. Sebutkan sisanya supaya
                        // approver tahu ketukan ini menutup pintu atau tidak.
                        Text(
                            if (sisa > 0)
                                "Hanya barang ini yang disetujui. $sisa barang lain SPK ini belum " +
                                    "tuntas — SPK baru masuk antrian PDI setelah semuanya selesai. " +
                                    "Persetujuan tidak bisa dibatalkan."
                            else
                                "Barang terakhir SPK ini. Menyetujuinya melepas SELURUH unit SPK " +
                                    "ke antrian PDI. Persetujuan tidak bisa dibatalkan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        // Opsional — beda dari alasan penolakan. Tapi bukan
                        // hiasan: server menyelipkannya ke WA + push pengaju,
                        // jadi inilah satu-satunya cara approver menjelaskan
                        // "disetujui, tapi…" kepada sales.
                        ExpressiveTextField(
                            note, { note = it },
                            label = "Catatan persetujuan (opsional)",
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.approveDiscount(id, note.trim()); approveId = null }
                    ) { Text("Setujui") }
                },
                dismissButton = { TextButton(onClick = { approveId = null }) { Text("Batal") } }
            )
        }
    }

    rejectId?.let { id ->
        var note by remember { mutableStateOf("") }
        // `decisionNote` WAJIB saat menolak (discounts.rs `reject_request`):
        // tanpa isi, server membalas 400 "decisionNote wajib diisi saat menolak".
        // Label sempat menulis "opsional" tanpa `enabled` — tolak dari HP selalu
        // gagal tanpa penjelasan.
        AlertDialog(
            onDismissRequest = { rejectId = null },
            title = { Text("Tolak diskon barang ini?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    // Yang tak terlihat dari tombolnya: penolakan TIDAK melepas
                    // unit — SPK berhenti sampai sales-nya memilih. Approver yang
                    // mengira "ditolak = lanjut tanpa diskon" akan menahan SPK
                    // orang tanpa sadar. Cakupannya berubah 2026-08-07 (dulu
                    // menyapu SELURUH barang SPK, kini hanya barang ini).
                    Text(
                        "Hanya barang ini yang ditolak, dan unitnya TIDAK otomatis lanjut — " +
                            "barang ini kembali ke sales untuk direvisi atau dilanjutkan tanpa diskon. " +
                            "SPK baru masuk antrian PDI setelah seluruh barangnya tuntas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ExpressiveTextField(
                        note, { note = it },
                        label = "Alasan penolakan (wajib)",
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = note.isNotBlank(),
                    onClick = { viewModel.rejectDiscount(id, note.trim()); rejectId = null }
                ) { Text("Tolak") }
            },
            dismissButton = { TextButton(onClick = { rejectId = null }) { Text("Batal") } }
        )
    }
}

/**
 * Satu SPK = satu kartu, berisi baris per barang + total potongan.
 *
 * Keputusan diambil PER BARANG (2026-08-07) karena begitulah server sekarang
 * memutuskannya — tombolnya ada di barisnya masing-masing, bukan di dasar
 * kartu. Kartu tetap per-SPK karena SPK-lah yang bergerak: ia baru lanjut ke
 * PDI setelah SELURUH barangnya tuntas, jadi approver harus melihat barang mana
 * yang masih menghambat.
 */
@Composable
private fun DiscountSpkCard(
    kode: String,
    pengajuan: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>,
    submitting: Set<String>,
    buktiFoto: Map<String, AkiPhotoState>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onDetail: () -> Unit,
) {
    // BUKAN `sumOf { it.value }` — lihat [potonganPengajuan]: `value` itu nilai
    // PER UNIT (dan sebuah persen kalau discountType="percent"), sementara
    // server menempelkannya ke SETIAP unit sebaris.
    val total = totalPotonganSpk(pengajuan)
    val konsumen = pengajuan.firstOrNull()?.jobSummary?.customerName ?: "-"
    // Yang SAMA untuk seluruh SPK ditulis SEKALI di header; yang berbeda tetap
    // di barisnya (lihat [nilaiSeragam] — keseragamannya dihitung di sana, di
    // luar composable, supaya bisa diuji).
    val pengaju = nilaiSeragam(pengajuan) { it.requestedByName?.trim()?.ifBlank { null } }
    val accSeragam = nilaiSeragam(pengajuan) { it.accOleh?.trim()?.ifBlank { null } }
    // Tanggal lewat `formatWaktuId` DULU baru dipotong: `createdAt` itu UTC,
    // jadi memotong 10 karakter pertamanya salah tanggal untuk pengajuan sore
    // (kontrak repo: semua waktu WIB).
    val tanggal = nilaiSeragam(pengajuan) { formatWaktuId(it.createdAt).substringBefore(' ').takeIf { t -> t != "-" } }
    val kemajuan = kemajuanSpk(pengajuan)
    // Daftar diringkas di BATAS_RINGKAS, TAPI barang yang belum tuntas tak
    // pernah ikut terpotong — sejak tombol keputusan pindah ke barisnya
    // masing-masing, menyembunyikan barang = menyembunyikan tombolnya. Angka
    // keputusan (total potongan) juga tak pernah disembunyikan.
    var semuaBarang by remember(kode) { mutableStateOf(false) }
    val tampil = if (semuaBarang) pengajuan else ringkasDaftar(pengajuan)
    val tersembunyi = pengajuan.size - tampil.size
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    kode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Kemajuan menggantikan "N barang": sejak keputusan per barang,
                // yang perlu diketahui approver bukan berapa barangnya melainkan
                // berapa yang MASIH menahan SPK ini.
                Surface(
                    color = if (kemajuan.semuaTuntas) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        kemajuan.teks, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(konsumen, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            listOfNotNull(
                pengaju?.let { "Diajukan $it" },
                tanggal,
                accSeragam?.let { "acc $it (di luar sistem)" },
            ).takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            tampil.forEach { d ->
                Spacer(Modifier.height(10.dp))
                DiscountBaris(
                    d = d,
                    bukti = buktiFoto[d.id],
                    tampilkanPengaju = pengaju == null,
                    tampilkanAcc = accSeragam == null,
                    submitting = d.id in submitting,
                    onApprove = { onApprove(d.id) },
                    onReject = { onReject(d.id) },
                )
            }
            // Yang tersembunyi SELALU barang yang sudah tuntas (lihat
            // `ringkasDaftar`) — tak ada tombol yang hilang di baliknya.
            if (tersembunyi > 0 || semuaBarang) {
                TextButton(onClick = { semuaBarang = !semuaBarang }, modifier = Modifier.align(Alignment.Start)) {
                    Text(
                        if (semuaBarang) "Ringkas daftar" else "Lihat $tersembunyi barang tuntas lainnya",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total potongan SPK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(rupiah(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFB5670C))
            }
            // Aturan pelepasan 2026-08-07: barang yang disetujui TIDAK jalan
            // sendirian. Tanpa kalimat ini approver menyimpulkan SPK sudah
            // bergerak setelah satu approve, lalu heran kenapa PDI tak menerima.
            Text(
                if (kemajuan.semuaTuntas) "Seluruh barang tuntas — SPK masuk antrian PDI."
                else "SPK baru masuk antrian PDI setelah SELURUH barangnya tuntas.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            )
            // Pengajuan cuma memuat baris yang MENGAJUKAN diskon; SPK-nya bisa
            // berisi barang lain yang tak berdiskon dan ikut tertahan sampai
            // seluruh pengajuan tuntas. Detail SPK utuh dimuat on-demand —
            // approver dengan 20 SPK menunggu tak perlu membayar 20 request
            // untuk yang tak dibukanya.
            TextButton(onClick = onDetail, modifier = Modifier.align(Alignment.Start)) {
                Text("Lihat detail SPK", style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Satu barang di dalam kartu SPK — 2 baris (3 kalau ada alasan), bukan 6.
 *
 * Bentuk lama (tabel `InfoLine` 6 baris per barang) menghabiskan satu layar
 * penuh untuk SPK 3 barang, sementara batas SPK sekarang 10. Yang dibuang
 * BUKAN informasinya melainkan pengulangannya: "Harga sebelum"/"Harga sesudah"
 * jadi satu baris panah ([ringkasHarga]), dan field yang seragam se-SPK naik ke
 * header kartu ([nilaiSeragam]) — kalau tidak seragam ia dikembalikan ke sini
 * lewat [tampilkanPengaju]/[tampilkanAcc].
 *
 * Jumlah unit tetap WAJIB kelihatan: potongan baris = potongan per unit ×
 * jumlah unit, dan tanpa angka itu approver tak bisa memeriksa sendiri kenapa
 * "harga sesudah" 12,5 jt menghasilkan potongan 1 jt.
 */
@Composable
private fun DiscountBaris(
    d: com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto,
    bukti: AkiPhotoState?,
    tampilkanPengaju: Boolean,
    tampilkanAcc: Boolean,
    submitting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    // Alasan panjang di-clamp 1 baris; approver TETAP wajib bisa membacanya
    // utuh, jadi barisnya bisa diketuk untuk membuka. `terpotong` diisi dari
    // hasil layout — "selengkapnya" cuma muncul kalau memang ada yang terpotong,
    // supaya alasan pendek tak menambah tinggi kartu tanpa guna.
    var alasanPenuh by remember(d.id) { mutableStateOf(false) }
    var terpotong by remember(d.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            // Nomor baris = penanda kecil, bukan baris teks sendiri.
            Text(
                d.baris?.toString() ?: "·", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(18.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    d.jobSummary?.namaBarang ?: d.jobSummary?.kodeBarang ?: "-",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ringkasHarga(d, sertakanAcc = tampilkanAcc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "−${rupiah(potonganPengajuan(d))}", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = Color(0xFFB5670C),
            )
            // Barang yang SUDAH diputus cukup menampilkan statusnya di sini —
            // tekanan kedua hanya memanen "sudah diputuskan" dari server.
            if (d.status != "pending") {
                Spacer(Modifier.width(4.dp))
                StatusBarisChip(d.status)
            }
        }
        // Penolakan TIDAK melepas unit: barang ini menunggu SALES, bukan
        // approver. Tanpa kalimat ini chip "Ditolak" terbaca seperti "beres",
        // dan approver menyimpulkan SPK sudah bergerak.
        if (d.status == "rejected") {
            Text(
                "Menunggu sales: revisi diskon atau lanjut tanpa diskon.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 18.dp, top = 2.dp),
            )
        }
        if (d.reason.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 18.dp, top = 2.dp)
                    .clickable { alasanPenuh = !alasanPenuh },
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "“${d.reason}”", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (alasanPenuh) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    onTextLayout = { if (!alasanPenuh) terpotong = it.hasVisualOverflow },
                )
                if (terpotong && !alasanPenuh) {
                    Text(
                        " selengkapnya", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        // Tombol keputusan: BARIS SENDIRI, berlabel teks, rata kanan.
        //
        // Sebelumnya dua ikon 32dp tanpa label menempel di ujung baris harga.
        // Itu menghemat ruang tapi salah untuk tombol yang MENYETUJUI UANG:
        // pengguna harus menebak arti ✕ dan ✓, target sentuhnya di bawah
        // anjuran 48dp, dan keduanya bersebelahan tanpa jarak — satu meleset
        // menyetujui diskon yang mestinya ditolak. Satu baris tambahan per
        // barang jauh lebih murah daripada satu keputusan uang yang salah.
        if (d.status == "pending") {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    ExpressiveOutlinedButton(
                        onClick = onReject,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Tolak", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // Jarak 12dp antara Tolak dan Setujui: dua tombol berdempetan
                    // membuat salah-pencet jadi soal milimeter.
                    Spacer(Modifier.width(12.dp))
                    ExpressiveFilledButton(
                        onClick = onApprove,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Setujui", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        // "Acc oleh" TIDAK lagi punya baris sendiri — ia ikut `ringkasHarga` di
        // atas. Sebagai `InfoLine` ia merentang selebar kartu sehingga keluar
        // dari indentasi barangnya dan terbaca sebagai keterangan SPK.
        when (bukti) {
            // Bisa ditekan untuk ukuran penuh — tulisan di tangkapan layar
            // WA/kwitansi tak terbaca pada thumbnail 140dp ber-Crop.
            is AkiPhotoState.Ada -> {
                Spacer(Modifier.height(8.dp))
                // 72dp, bukan 140dp selebar kartu: SPK 3 barang dulu ±810dp
                // tinggi, sehingga tombol Setujui/Tolak jatuh di bawah lipatan
                // dan kartu terbaca sebagai "tak ada tombolnya". Ketuk untuk
                // ukuran penuh (BuktiFotoThumbnail sudah punya lightbox +
                // cubit-perbesar) — tulisan di tangkapan layar WA tak pernah
                // terbaca pada thumbnail berapa pun ukurannya.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BuktiFotoThumbnail(
                        bitmap = bukti.bitmap,
                        deskripsi = "Bukti acc diskon",
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Bukti acc — ketuk untuk perbesar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is AkiPhotoState.Memuat -> {
                Spacer(Modifier.height(8.dp))
                Text("Memuat bukti acc…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is AkiPhotoState.Gagal -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bukti acc gagal dimuat — file tidak ada di server atau jaringan putus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            null -> Unit // sales memang tak melampirkan bukti — bukan kegagalan
        }
        if (tampilkanPengaju) InfoLine("Diajukan", d.requestedByName)
    }
}

/** Status barang yang sudah diputus — pengganti tombol di ujung baris. */
@Composable
private fun StatusBarisChip(status: String) {
    val warna = when {
        barisTuntas(status) -> MaterialTheme.colorScheme.primary
        status == "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = warna.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            labelStatusBaris(status), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = warna, maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * SPK utuh di balik kartu diskon — satu-satunya layar approval yang dulu tak
 * punya jalan ke detail, padahal keputusannya menyapu seluruh SPK.
 *
 * Isinya SELURUH unit se-SPK, termasuk yang TIDAK berdiskon: approver menilai
 * dampak satu keputusan atas SPK penuh, jadi barang yang ikut tertahan tapi
 * tak mengajukan diskon justru wajib kelihatan.
 *
 * Yang TIDAK ditampilkan (dan tak dikirim server): NIK, alamat, titik lokasi,
 * sosmed, komisi/no HP KBK, nomor rangka/mesin, foto-foto. Approver diskon
 * bisa siapa saja pemegang page-grant, LINTAS CABANG — jangan menambahkannya.
 */
@Composable
fun SpkDiskonDetailScreen(
    kode: String,
    onBack: () -> Unit,
    viewModel: DeliveryFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Halaman ini memakai ViewModel yang SAMA dengan layar approval, jadi tak
    // ada state ganda: membukanya memicu pemuatan yang sama.
    LaunchedEffect(kode) { viewModel.bukaDetailSpkDiskon(kode) }
    val detail = state.spkDiskonDetail
    val error = state.spkDiskonDetailError

    TridjayaCollapsibleHeader(title = kode, onBack = onBack) { contentModifier ->
        Column(
            contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
                when {
                    error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    detail == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Memuat detail SPK…", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        InfoLine("Cabang", listOfNotNull(detail.dealerName, detail.kodeDealer.ifBlank { null }).joinToString(" · ").ifBlank { null })
                        InfoLine("Tanggal jual", detail.tanggalJual)
                        InfoLine("Konsumen", detail.customerName)
                        InfoLine("No. HP", detail.customerPhone)
                        // KBK = order lewat broker; nama brokernya yang relevan,
                        // bukan sales yang menginput.
                        if (detail.orderSource == "kbk") {
                            InfoLine("Sumber order", "KBK")
                            InfoLine("Broker", detail.kbkBrokerNama)
                        }
                        InfoLine("Sales", detail.salesName)
                        InfoLine("Metode kirim", detail.deliveryMethod)
                        InfoLine("Pembayaran", if (detail.paymentType == "credit") "Kredit" else detail.paymentType?.let { "Cash" })
                        if (detail.paymentType == "credit") {
                            InfoLine("Fincoy", detail.fincoy)
                            InfoLine("DP net", detail.dpNet?.let { rupiah(it) })
                            InfoLine("Pembayaran 1", detail.pembayaran1?.let { rupiah(it) })
                            InfoLine("Angsuran", detail.angsuran?.let { rupiah(it) })
                            InfoLine("Tenor", detail.tenor?.let { "$it bulan" })
                            InfoLine("Biaya adm", detail.biayaAdm?.let { rupiah(it) })
                            InfoLine("Angsuran pertama", detail.angsuranPertama?.let { rupiah(it) })
                        }
                        InfoLine("Keterangan", detail.keterangan)

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Barang dalam SPK ini (${detail.units.size} unit)",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        detail.units.forEach { u ->
                            Spacer(Modifier.height(8.dp))
                            Column(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        u.namaBarang ?: u.kodeBarang,
                                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    // Penanda berdiskon/tidak: unit tanpa diskon
                                    // ikut tertahan oleh keputusan ini, dan itu
                                    // tak terlihat dari daftar pengajuan.
                                    val berdiskon = (u.diskon ?: 0.0) > 0.0
                                    Surface(
                                        color = if (berdiskon) Color(0xFFB5670C).copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(50),
                                    ) {
                                        Text(
                                            if (berdiskon) "diskon" else "tanpa diskon",
                                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                            color = if (berdiskon) Color(0xFFB5670C) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    "baris ${u.baris} · unit ${u.unitSeq} · ${u.status}" +
                                        (u.warna?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                InfoLine("Harga OTR", u.hargaOtr?.let { rupiah(it) })
                                if ((u.diskon ?: 0.0) > 0.0) InfoLine("Diskon", "−${rupiah(u.diskon)}")
                                InfoLine("Total", u.hargaTotal?.let { rupiah(it) })
                                if (u.driverTerimaUang) {
                                    InfoLine(
                                        "COD",
                                        if (u.codPaymentMode == "dp") "DP ${rupiah(u.codDpAmount)} ditagih driver"
                                        else "Full payment ditagih driver",
                                    )
                                }
                            }
                        }

                        // Pengajuan SPK ini yang masih menunggu keputusan. Tanpa
                        // baris turunannya, SPK yang belum punya satu pun diskon
                        // disetujui menampilkan "Diskon berjalan −Rp 0" + harga
                        // PENUH — approver membacanya sebagai "diskonnya tidak
                        // masuk", bukan sebagai "keputusannya belum dibuat".
                        val menunggu = potonganMenunggu(state.spkDiskonPengajuan)
                        Spacer(Modifier.height(12.dp))
                        InfoLine("Total OTR SPK", rupiah(detail.totalHargaOtr))
                        InfoLine("Diskon berjalan", "−${rupiah(detail.totalDiskonBerjalan)}")
                        InfoLine("Setelah diskon", rupiah(detail.totalSetelahDiskon))
                        if (menunggu > 0.0) {
                            Spacer(Modifier.height(6.dp))
                            InfoLine("Sedang diajukan", "−${rupiah(menunggu)}")
                            InfoLine("Bila disetujui", rupiah(detail.totalSetelahDiskon - menunggu))
                        }
                        Text(
                            if (menunggu > 0.0)
                                "\"Diskon berjalan\" = potongan yang SUDAH disetujui & menempel. " +
                                    "\"Sedang diajukan\" = yang kamu putuskan sekarang — belum menempel " +
                                    "sampai kamu menekan Setujui."
                            else
                                "\"Diskon berjalan\" = potongan yang SUDAH disetujui & menempel. " +
                                    "Tak ada pengajuan yang menunggu keputusan di SPK ini.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            Spacer(Modifier.height(24.dp))
        }
    }
}


/**
 * Isi/perbaiki **lokasi maps** unit ini.
 *
 * Aksi TERPISAH dari [EditSpkAction] karena jendelanya berbeda: sunting SPK
 * berhenti sebelum PDI, lokasi maps boleh sampai unit terkirim. Endpointnya pun
 * berbeda (`.../map-url`), dan menumpangkannya ke dialog sunting berarti
 * menawarkan puluhan field lain di saat server hanya mengizinkan satu.
 *
 * Isi lama DITAMPILKAN apa adanya saat tak bisa dipakai. Sales yang menulis
 * "hgl" merasa sudah mengisi — tanpa melihatnya ia akan mengira layar ini
 * keliru. (1.008 dari 2.224 unit produksi berisi teks semacam itu, lebih banyak
 * daripada yang kosong.)
 */
@Composable
private fun IsiMapsAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var show by remember { mutableStateOf(false) }
    // `job.id` sebagai kunci — berpindah unit harus mengosongkan isian, kalau
    // tidak lokasi unit A tersimpan ke unit B.
    var nilai by remember(job.id) { mutableStateOf("") }
    var pesan by remember(job.id) { mutableStateOf<String?>(null) }
    val isiLama = job.customerMapUrl.orEmpty().trim()
    val sudahBaik = mapsTerpakai(isiLama)

    OutlinedButton(
        onClick = { nilai = if (sudahBaik) isiLama else ""; show = true },
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (sudahBaik) "Ubah Lokasi Maps" else "Isi Lokasi Maps") }
    if (!sudahBaik) {
        Spacer(Modifier.height(6.dp))
        Text(
            if (isiLama.isEmpty()) {
                "Belum ada lokasi — driver tidak bisa dijadwalkan."
            } else {
                "Lokasi \"$isiLama\" tidak bisa dibuka driver."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    pesan?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    if (show) {
        val sah = mapsTerpakai(nilai)
        AlertDialog(
            onDismissRequest = { if (!submitting) show = false },
            title = { Text("Lokasi Maps Konsumen") },
            text = {
                Column {
                    Text(
                        "Driver memakai ini untuk menemukan alamat. Buka Google Maps -> cari " +
                            "lokasi -> Bagikan -> Salin tautan.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (isiLama.isNotEmpty() && !sudahBaik) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sekarang terisi \"$isiLama\" — tidak bisa dibuka.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ExpressiveTextField(
                        value = nilai,
                        onValueChange = { nilai = it },
                        label = "Link atau koordinat",
                        placeholder = "https://maps.app.goo.gl/...",
                        isError = nilai.isNotEmpty() && !sah,
                        supportingText = if (nilai.isNotEmpty() && !sah) {
                            "Belum berupa link atau koordinat."
                        } else {
                            "Boleh juga koordinat: -6.123456, 106.789012"
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting && sah,
                    onClick = {
                        vm.setMapUrl(job.id, nilai.trim()) {
                            show = false
                            pesan = "Lokasi maps tersimpan"
                        }
                    }
                ) { Text(if (submitting) "Menyimpan..." else "Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }, enabled = !submitting) { Text("Batal") }
            }
        )
    }
}

/**
 * Koreksi salah input SPK oleh administrator (2026-08-01). Dialognya
 * data-driven dari [SPK_EDIT_FIELDS] — menambah field cukup di daftar itu,
 * tak ada 29 `remember` yang harus dijaga sinkron dengan backend.
 *
 * Yang dikirim hanya SELISIH-nya ([buildSpkEditPatch]); field yang tak
 * disentuh tak ikut, supaya dua administrator yang membuka SPK yang sama tak
 * saling menimpa isian yang tak mereka ubah.
 */
@Composable
private fun EditSpkAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var show by remember { mutableStateOf(false) }
    // `job.id` sebagai kunci: berpindah unit harus memuat ulang isian, kalau
    // tidak koreksi unit A tersimpan ke unit B.
    var form by remember(job.id) { mutableStateOf(spkEditFormFromJob(job)) }
    var alasan by remember(job.id) { mutableStateOf("") }
    var pesan by remember(job.id) { mutableStateOf<String?>(null) }

    OutlinedButton(
        onClick = { form = spkEditFormFromJob(job); alasan = ""; show = true },
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Ubah Isi SPK") }
    pesan?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    if (show) {
        val patch = buildSpkEditPatch(form, job, alasan)
        AlertDialog(
            onDismissRequest = { if (!submitting) show = false },
            title = { Text("Ubah Isi SPK", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(job.kodePengiriman, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Koreksi salah input. Diskon tak bisa diubah di sini — ajukan lewat menu " +
                            "diskon supaya tetap ada approver yang memutuskan. Total dihitung ulang " +
                            "otomatis dari harga OTR dikurangi diskon berjalan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpkEditGrup.entries.forEach { grup ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            grup.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SPK_EDIT_FIELDS.filter { it.grup == grup }.forEach { f ->
                            Spacer(Modifier.height(8.dp))
                            ExpressiveTextField(
                                value = form[f.key].orEmpty(),
                                onValueChange = { v -> form = form + (f.key to v) },
                                label = f.label,
                                singleLine = f.tipe != SpkEditTipe.TEKS_PANJANG,
                                keyboardType = if (f.tipe == SpkEditTipe.ANGKA) KeyboardType.Number else KeyboardType.Text,
                                supportingText = if (f.tipe == SpkEditTipe.METODE_BAYAR) "cash atau credit" else null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ExpressiveTextField(
                        value = alasan,
                        onValueChange = { alasan = it },
                        label = "Alasan koreksi (wajib)",
                        placeholder = "mis. sales salah pilih varian warna",
                        isError = alasan.isNotEmpty() && !spkEditAlasanValid(alasan),
                        supportingText = "Tersimpan di log aktivitas beserta nama Anda.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting && patch != null && spkEditAlasanValid(alasan),
                    onClick = {
                        val body = patch ?: return@TextButton
                        vm.editJob(job.id, body) { konsumenDiubah ->
                            show = false
                            pesan = if (konsumenDiubah > 1) {
                                "Tersimpan · data konsumen ikut diperbarui di $konsumenDiubah unit SPK ini"
                            } else {
                                "Tersimpan"
                            }
                        }
                    }
                ) { Text(if (submitting) "Menyimpan..." else "Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }, enabled = !submitting) { Text("Batal") }
            }
        )
    }
}

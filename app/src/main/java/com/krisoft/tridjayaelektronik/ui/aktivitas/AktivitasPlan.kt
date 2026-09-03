package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto

/**
 * Bagian murni layar Input Aktivitas (tanpa Android/Compose) supaya bisa diuji
 * JUnit biasa — pola sama `ui/activity/ActivityPlan.kt`.
 */

/**
 * Posisi aktivitas milik seorang karyawan berdasarkan `divisi`-nya.
 *
 * **BUKAN port 1:1 dari web — komentar lama di sini mengklaim begitu dan itu
 * sudah tidak benar.** Web (`utils/aktivitasDivisiMatch.ts`
 * `cariDivisiAktivitas`) menormalkan KEDUA sisi lewat
 * `normalizeAktivitasMatchKey`/`kunciDivisi` dan punya TINGKAT KETIGA yang
 * dinilai per-tag dengan aturan ">1 kandidat = BERHENTI"; fungsi ini
 * membandingkan `lowercase().trim()` MENTAH dan berhenti di `contains`.
 * Urutan tingkatnya pun berbeda.
 *
 * Diukur di produksi 2026-08-27: hasilnya berbeda untuk **0 dari 164 akun**
 * (34 akun memakai jalur ini, 36 tag unik, nol tag mengandung spasi maupun
 * underscore), jadi ini utang dokumentasi — bukan cacat yang sedang berjalan.
 * Kalau kodenya suatu saat dikerjakan, tulis ULANG sebagai cerminan `cocok
 * (tingkat)` (loop per-tingkat di LUAR, per-divisi di DALAM), bukan tiga
 * tambalan normalisasi — tambalan melestarikan perbedaan urutannya.
 *
 * `null` saat tak ada yang cocok — SENGAJA tak jatuh ke posisi pertama: karyawan
 * yang divisinya tak ada di master akan dinilai (dan didenda) memakai aktivitas
 * divisi lain. Cocokkan longgar (`contains`) menutup divisi multi-nilai CSV
 * ("admin,driver") hasil divisi-driven-access.
 */
internal fun matchAktivitasPosition(
    divisi: String,
    positions: List<AktivitasPositionDto>,
): AktivitasPositionDto? {
    val normalized = divisi.lowercase().trim()
    if (normalized.isBlank()) return null
    return positions.firstOrNull { it.id.lowercase() == normalized }
        ?: positions.firstOrNull { it.posisi.lowercase() == normalized }
        ?: positions.firstOrNull {
            // `isNotBlank` bukan hiasan: `contains("")` selalu true, jadi entri
            // master yang id/posisinya kosong akan menyambar semua orang.
            (it.id.isNotBlank() && normalized.contains(it.id.lowercase())) ||
                (it.posisi.isNotBlank() && normalized.contains(it.posisi.lowercase()))
        }
}

/**
 * Penempatan KPI orang yang sedang login — TIGA keadaan, bukan dua.
 *
 * Web memisahkan "belum termuat" (`undefined`) dari "tak punya penempatan"
 * (`null`) dan keduanya menuntut perilaku BERLAWANAN: yang pertama harus
 * menunggu, yang kedua harus jatuh ke tag. Kotlin tak punya beda itu pada
 * `String?`, jadi ia dimodelkan eksplisit — mencampurnya adalah satu-satunya
 * cara serius merusak layar ini, dan `String?` telanjang mengundangnya.
 */
internal sealed interface PenempatanSaya {
    /** Permintaan belum selesai. Layar JANGAN memutuskan apa pun dulu. */
    data object BelumTermuat : PenempatanSaya

    /**
     * Orangnya belum punya baris `kpi_assignments`, ATAU permintaannya gagal.
     * Keduanya jatuh ke jalur TAG — perilaku sebelum perbaikan ini, jadi
     * kegagalan jaringan tak memperkenalkan keadaan baru.
     */
    data object TidakAda : PenempatanSaya

    data class Ada(val positionId: String) : PenempatanSaya
}

/**
 * Seluruh isi `GET /aktivitas-harian/penempatan-saya` yang dipakai layar ini.
 *
 * **Dibungkus jadi satu, BUKAN dua panggilan.** Blok `chatTrainee` menumpang
 * endpoint yang sudah ada justru supaya tak ada rute baru dan tak ada
 * round-trip kedua di jalur yang dibuka orang tiap pagi. Memisahnya jadi
 * `chatTraineeSetting()` sendiri akan membatalkan alasan itu.
 *
 * [chatTrainee] `null` = gerbang chat tidak berlaku ATAU permintaannya gagal —
 * satu arti, dan itu disengaja (fail-open). Lihat KDoc [AmbangChatTrainee].
 */
internal data class PenempatanSayaHasil(
    val penempatan: PenempatanSaya,
    val chatTrainee: AmbangChatTrainee? = null,
)

/**
 * Normalisasi kunci pencocokan — cerminan `normalizeAktivitasMatchKey` di
 * `frontend/src/utils/aktivitasDivisiMatch.ts`. Underscore & spasi jadi `-`.
 */
internal fun normalizeAktivitasMatchKey(value: String?): String =
    (value ?: "").trim().lowercase().replace(Regex("[_\\s]+"), "-")

/**
 * Penempatan KPI yang id-nya TIDAK identik dengan id divisi mana pun di Master
 * Aktivitas, tapi aktivitasnya memang aktivitas divisi lain.
 *
 * **Cerminan `POSISI_KPI_KE_DIVISI_AKTIVITAS`** di web DAN
 * `POSISI_KPI_KE_DIVISI_AKTIVITAS` di `kinerja-service/src/kpi/domain.rs` —
 * isinya HARUS sama bertiga. Kalau berbeda, HP menampilkan daftar aktivitas
 * yang berbeda dari yang menilai orangnya, dan tak ada error yang menandainya.
 */
internal val POSISI_KPI_KE_DIVISI_AKTIVITAS: Map<String, String> = mapOf(
    "sales-1-3-bulan" to "sales",
    "sales-4-6-bulan" to "sales",
    "hrd-ga" to "hrd",
)

private fun kunciDivisi(d: AktivitasPositionDto): List<String> =
    listOf(d.id, d.posisi).map(::normalizeAktivitasMatchKey).filter { it.isNotBlank() }

/** Hasil pencarian divisi dari PENEMPATAN — tiga cabang, seperti di web. */
internal sealed interface DivisiDariPenempatan {
    data object TanpaPenempatan : DivisiDariPenempatan
    /** Penempatannya ada TAPI tak punya divisi aktivitas. JANGAN jatuh ke tag. */
    data object PosisiTanpaAktivitas : DivisiDariPenempatan
    data class Ketemu(val divisi: AktivitasPositionDto) : DivisiDariPenempatan
}

/**
 * Cerminan `cariDivisiAktivitasDariPenempatan` di web. Cocok PERSIS saja —
 * tidak ada pencocokan longgar seperti jalur tag, karena id penempatan itu
 * nilai terkontrol dari `kpi_positions`, bukan teks bebas yang diketik admin.
 */
internal fun cariDivisiDariPenempatan(
    positionId: String?,
    positions: List<AktivitasPositionDto>,
): DivisiDariPenempatan {
    val posisi = normalizeAktivitasMatchKey(positionId)
    if (posisi.isBlank()) return DivisiDariPenempatan.TanpaPenempatan
    val target = POSISI_KPI_KE_DIVISI_AKTIVITAS[posisi] ?: posisi
    val divisi = positions.firstOrNull { d -> kunciDivisi(d).any { it == target } }
    return if (divisi != null) DivisiDariPenempatan.Ketemu(divisi)
    else DivisiDariPenempatan.PosisiTanpaAktivitas
}

/**
 * Daftar aktivitas yang harus DITAMPILKAN ke orangnya — cerminan
 * `pilihDivisiUntukInput` di web.
 *
 * **Kenapa ini ada.** Sampai perbaikan ini HP memilih daftar lewat TAG
 * (`auth_users.divisi`), sementara gerbang absen pulang & penilaian KPI sudah
 * memakai PENEMPATAN. Akibatnya nyata dan sedang berjalan: pemegang tag
 * `admin-penjualan,kasir` melihat 8 butir KASIR di HP padahal dinilai atas 6
 * butir ADMIN PENJUALAN. Ia mengisi yang salah, lalu dinilai kurang.
 *
 * Urutannya mengikat:
 * 1. `BelumTermuat` -> `null`. Menunggu, BUKAN jatuh ke tag — jatuh ke tag
 *    sekejap lalu berganti membuat kotak isian berkedip dan berpindah isi.
 * 2. Penempatan ketemu -> pakai itu. Ini jalur normalnya.
 * 3. Penempatan ADA tapi tanpa divisi aktivitas -> `null`, JANGAN jatuh ke tag.
 *    Tag akan memberi daftar milik divisi lain, dan mengisi daftar orang lain
 *    lebih buruk daripada tak punya daftar.
 * 4. Tak punya penempatan -> jalur TAG lama. Ini cadangan untuk orang yang
 *    belum punya baris `kpi_assignments`, dan untuk permintaan yang gagal.
 */
internal fun pilihAktivitasUntukInput(
    divisi: String,
    positions: List<AktivitasPositionDto>,
    penempatan: PenempatanSaya,
): AktivitasPositionDto? = when (penempatan) {
    PenempatanSaya.BelumTermuat -> null
    PenempatanSaya.TidakAda -> matchAktivitasPosition(divisi, positions)
    is PenempatanSaya.Ada -> when (val d = cariDivisiDariPenempatan(penempatan.positionId, positions)) {
        is DivisiDariPenempatan.Ketemu -> d.divisi
        DivisiDariPenempatan.PosisiTanpaAktivitas -> null
        DivisiDariPenempatan.TanpaPenempatan -> matchAktivitasPosition(divisi, positions)
    }
}

enum class AktivitasRowStatus { BELUM, MENUNGGU, DISETUJUI, DITOLAK }

/** Status satu baris aktivitas dari raport yang sudah terkirim hari itu. */
internal fun rowStatus(item: AktivitasItemDto?): AktivitasRowStatus = when {
    item == null -> AktivitasRowStatus.BELUM
    item.reviewStatus == "approved" -> AktivitasRowStatus.DISETUJUI
    item.reviewStatus == "rejected" -> AktivitasRowStatus.DITOLAK
    else -> AktivitasRowStatus.MENUNGGU
}

/**
 * Baris terkirim di-index by `jobdeskIndex` (nama field di KABEL, ejaan lama
 * sengaja dipertahankan). Server mengizinkan index apa pun (raport lama bisa
 * memakai master aktivitas versi sebelumnya) — baris yang
 * indexnya di luar daftar sekarang tetap dipegang di peta, tinggal tak punya
 * baris untuk ditempeli.
 */
internal fun submittedByIndex(items: List<AktivitasItemDto>): Map<Int, AktivitasItemDto> =
    items.associateBy { it.jobdeskIndex }

/**
 * Baris terkirim yang BOLEH ditempelkan ke daftar aktivitas [aktivitas] yang
 * sedang dirender.
 *
 * **Kenapa index saja TIDAK cukup — dan ini bug yang muncul begitu jabatan
 * seseorang diganti.** Raport disimpan server ber-`jobdeskIndex`, yaitu posisi
 * di dalam daftar aktivitas DIVISI SAAT ITU. Kalau orangnya dipindah jabatan,
 * daftar yang dirender berganti tapi baris lamanya tetap ada untuk tanggal yang
 * sama — dan `jobdeskIndex = 3` milik divisi LAMA lalu menempel ke aktivitas
 * ke-3 divisi BARU, yaitu pekerjaan yang sama sekali berbeda.
 *
 * Akibatnya bukan sekadar tampilan salah: barisnya terbaca "sudah dikirim"
 * (bahkan "disetujui"), jadi orangnya TIDAK mengisinya — lalu dinilai kurang
 * atas pekerjaan yang memang tak pernah ia laporkan. Persis kelas kekeliruan
 * yang sudah tercatat di [pilihAktivitasUntukInput] ("Ia mengisi yang salah,
 * lalu dinilai kurang"), cuma lewat pintu lain.
 *
 * **Penjaganya TEKS aktivitas, bukan nama divisi.** `jobdeskText` adalah
 * satu-satunya kolom yang benar-benar menyebut PEKERJAAN apa yang dilaporkan;
 * `divisiName` menyebut divisi saat pengiriman, yang bisa berbeda ejaan dari
 * nama posisi di master tanpa berarti barisnya milik orang lain.
 *
 * **Arah amannya MEMBUANG, bukan meloloskan.** Kalau teksnya tak cocok, baris
 * itu dianggap bukan milik aktivitas ini dan barisnya tampil BELUM — orangnya
 * mengisi ulang, dan server upsert sehingga pengisian ulang aman. Kebalikannya
 * (meloloskan yang meragukan) menghasilkan pekerjaan yang tak pernah dikerjakan
 * tapi terlihat selesai, dan itu tak bisa diperbaiki siapa pun karena tak ada
 * yang tahu ia salah.
 */
internal fun terkirimUntukAktivitas(
    items: List<AktivitasItemDto>,
    aktivitas: List<String>,
): Map<Int, AktivitasItemDto> =
    submittedByIndex(items).filter { (index, item) ->
        val teksBaris = aktivitas.getOrNull(index) ?: return@filter false
        samaTeksAktivitas(teksBaris, item.jobdeskText)
    }

/**
 * Pembanding teks aktivitas: spasi berlebih diciutkan dan besar-kecil huruf
 * diabaikan.
 *
 * Longgar HANYA pada hal kosmetik, dan itu disengaja. Master aktivitas diketik
 * admin lewat `app_settings`, jadi perbedaan spasi ganda atau kapitalisasi
 * adalah kejadian sehari-hari yang TIDAK berarti aktivitasnya berganti —
 * menolaknya akan membuat baris yang sah tampil BELUM tiap kali ada yang
 * merapikan ketikan. Perbedaan kata tetap dianggap aktivitas yang berbeda.
 */
private fun samaTeksAktivitas(a: String, b: String): Boolean =
    ciutkanSpasi(a).equals(ciutkanSpasi(b), ignoreCase = true)

private fun ciutkanSpasi(teks: String): String =
    teks.trim().split(Regex("""\s+""")).joinToString(" ")

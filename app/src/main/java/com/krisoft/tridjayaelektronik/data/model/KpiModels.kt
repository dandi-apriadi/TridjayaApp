package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * KPI karyawan — kinerja-service `kpi.rs` (`GET /api/kpi/me`,
 * `GET /api/kpi/karyawan`, `GET /api/kpi/karyawan/{id}`), semua `camelCase`.
 * Kontrak: `docs/api/android-api.md` §15.
 *
 * `/kpi/me` TIDAK ber-gate role (cuma identitas) — karyawan yang belum
 * di-assign posisi dapat amplop kosong (`position: null`, `items: []`), bukan
 * error. `/kpi/karyawan*` ber-gate `KPI_MANAGE_ROLES` (kemampuan `kpi.manage`).
 */
@Serializable
data class KpiPositionDto(
    val id: String = "",
    val judul: String = "",
    /**
     * `"sales"` | `"non_sales"` — sejak model bonus per indikator (Excel Sept
     * 2026) keduanya cuma memilih TABEL NOMINAL (sales 2jt/1,5jt/750rb/0,
     * non-sales 1,5jt/750rb/500rb/0), BUKAN lagi "sales = insentif persen,
     * non_sales = reward/punishment rupiah" seperti tertulis di sini sampai
     * 2026-08-28. Insentif persen sudah dicabut — server selalu mengirim
     * `insentif: null` untuk vonis model baru.
     */
    val bracketMode: String = "",
    /** Posisi dipilih otomatis dari masa kerja (hr_roster.tgl_masuk). */
    val auto: Boolean = false
)

@Serializable
data class KpiKaryawanDto(
    val nama: String = "",
    val nik: String = "",
    val jabatan: String = "",
    val divisi: String = "",
    val cabangName: String = ""
)

@Serializable
data class KpiItemDto(
    val indicatorId: Long = 0,
    val indikator: String = "",
    val target: Double = 0.0,
    val bobot: Double = 0.0,
    val keterangan: String? = null,
    /** `null` = belum ada nilai sama sekali (bukan nol). */
    val actual: Double? = null,
    /** actual / target — rasio, bukan persen. Bisa > 1. */
    val achievement: Double = 0.0,
    val hasilBobot: Double = 0.0,
    /**
     * Kategori Excel indikator ini: `BAGUS SEKALI` / `BAGUS` / `SEDANG` /
     * `KURANG` (`ScoredItem.kategori`). `null` untuk vonis model LAMA yang
     * tersimpan di snapshot periode terkunci.
     */
    val kategori: String? = null,
    /**
     * Rupiah yang indikator INI sumbangkan ke bonus. **Di sinilah uangnya
     * sekarang** — `hasilBobot` tinggal bahan skor total yang dipajang;
     * menjumlahkan kolom ini = bonus orang itu. `null` di snapshot model lama.
     */
    val bonusRp: Long? = null,
    /** "auto" (dihitung sistem) | "manual" (input HR) | null (belum ada). */
    val source: String? = null
)

/**
 * Satu baris ALASAN kenapa bonus tidak penuh — cerminan `BracketAlasan`
 * (kinerja-service `kpi/scoring.rs`), diurutkan server dari rupiah hilang
 * terbesar.
 *
 * **Hampir semuanya nullable, dan itu cerminan data nyata, bukan kelonggaran.**
 * `kpi_snapshot` periode TERKUNCI (Juli & Agustus 2026) menyimpan bentuk LAMA
 * yang hanya punya [dampakPct] — [bonusRp]/[hilangRp]/[kategori] memang tak ada
 * di sana. Menjadikannya non-null ber-default `0` membuat layar mencetak
 * "−Rp 0" untuk SETIAP indikator: daftar yang terbaca "tak ada yang hilang"
 * padahal sebabnya cuma tak terbaca. Web menuliskan jebakan yang sama persis di
 * `useKpiStore.ts`.
 */
@Serializable
data class KpiBracketAlasanDto(
    val indikator: String = "",
    /** Capaian indikator (1.0 = tepat target). Nol untuk yang belum dinilai. */
    val achievement: Double = 0.0,
    val bobot: Double = 0.0,
    val kategori: String? = null,
    /** Rupiah yang benar-benar didapat dari indikator ini. */
    val bonusRp: Long? = null,
    /** Rupiah kalau indikator ini BAGUS SEKALI. */
    val bonusMaksRp: Long? = null,
    /** `bonusMaksRp − bonusRp`. Selalu >= 0 — indikator tak pernah MENGURANGI
     *  bonus indikator lain, ia hanya gagal menambah. */
    val hilangRp: Long? = null,
    /** Bentuk LAMA (poin persen). HANYA ada di snapshot periode terkunci. */
    val dampakPct: Double? = null,
    /** `false` = belum dinilai HR. Bedanya penting: capaian rendah itu kinerja,
     *  indikator kosong itu penilaian yang belum dikerjakan — dan di model bonus
     *  keduanya sama-sama membayar Rp 0. */
    val dinilai: Boolean = false
)

/**
 * Vonis rupiah. **Sejak model bonus per indikator (Excel Sept 2026) BERLAKU
 * UNTUK KEDUA `bracketMode`** — posisi sales tak lagi dibayar sebagai persen.
 */
@Serializable
data class KpiBracketDto(
    /**
     * `"reward"` saat bonus > 0, `"netral"` saat Rp 0.
     *
     * **`"punishment"` SUDAH TIDAK PERNAH TERBIT** sejak 2026-08-19 — model
     * Excel tak punya satu pun angka negatif, dan `scoring.rs::payload` menulis
     * `if amount > 0 { "reward" } else { "netral" }`. Nilainya tetap mungkin
     * MUNCUL karena `kpi_snapshot` periode terkunci (Juli & Agustus 2026)
     * menyimpan vonis aturan lama apa adanya; layar wajib bisa merendernya,
     * bukan menganggapnya data rusak.
     */
    val kind: String = "",
    val amount: Long = 0,
    /** Kategori dari SKOR TOTAL — label saja, TIDAK menggerakkan uang. */
    val kategoriTotal: String? = null,
    /** Bonus kalau SEMUA indikator BAGUS SEKALI. Pembanding untuk [amount]. */
    val bonusMaksRp: Long? = null,
    /** Absen pada respons lama / snapshot yang belum disegarkan. */
    val alasan: List<KpiBracketAlasanDto> = emptyList()
) {
    /**
     * MODEL MANA yang melahirkan vonis ini — dibaca dari BENTUK payload, bukan
     * dari periode yang sedang dibuka.
     *
     * Alasannya sama dengan yang ditulis web di `KpiHasilBadge.tsx`: komponen
     * yang menerima "periode terkunci" sebagai parameter menuntut TIAP pemanggil
     * ingat mengopernya, dan yang lupa memberi label salah tanpa satu pun error.
     * Bentuknya sudah cukup memutuskan — `bonusMaksRp` HANYA ada di vonis model
     * bonus per indikator; snapshot Juli & Agustus 2026 tak pernah punya field
     * itu.
     */
    val modelBonus: Boolean get() = bonusMaksRp != null
}

/**
 * Judul panel rincian, mengikuti MODEL yang melahirkan vonisnya.
 *
 * Memakai satu judul untuk keduanya membuat arsip Juli terbaca seolah lahir
 * dari model bonus hari ini — padahal satuan angkanya pun berbeda (rupiah vs
 * poin persen). Cerminan `KpiBracketAlasan` di web.
 */
internal fun judulAlasanKpi(modelBonus: Boolean): String =
    if (modelBonus) "Kenapa bonusnya tidak penuh"
    else "Indikator yang menekan vonis (aturan lama)"

/**
 * Satuan dampak satu baris alasan — `null` bila baris itu memang tak menyimpan
 * angkanya (lalu layar tak mencetak apa pun, bukan mencetak nol).
 *
 * **Inilah aturan yang paling mudah dirusak.** Baris model bonus menyimpan
 * `hilangRp`, baris snapshot periode terkunci hanya `dampakPct`. Membaca
 * `hilangRp ?: 0` pada yang kedua mencetak "−Rp 0" untuk SETIAP indikator —
 * daftar yang terbaca "tak ada yang hilang" padahal sebabnya cuma tak terbaca.
 * Membaca `dampakPct` pada yang pertama sama salahnya dari arah sebaliknya.
 *
 * SATU perbedaan yang disengaja dari web: `KpiBracketAlasan.tsx` menulis
 * `hilangRp ?? 0` sehingga baris yang kehilangan angkanya tetap tercetak
 * "−Rp 0". Di sini angkanya `null` → barisnya tak mencetak apa-apa. Nol yang
 * dikarang tak bisa dibedakan dari nol yang benar; ruang kosong bisa.
 *
 * [formatRupiah] & [formatAngka] disuntikkan supaya fungsi ini bisa diuji tanpa
 * Compose/Android — pemformatannya sendiri sudah punya testnya sendiri.
 */
internal fun dampakAlasanKpi(
    baris: KpiBracketAlasanDto,
    modelBonus: Boolean,
    formatRupiah: (Double) -> String,
    formatAngka: (Double) -> String,
): String? = if (modelBonus) {
    baris.hilangRp?.let { "−${formatRupiah(it.toDouble())}" }
} else {
    baris.dampakPct?.let { "${if (it > 0) "+" else ""}${formatAngka(it)} poin" }
}

@Serializable
data class KpiInsentifKomponenDto(
    val sumber: String = "",
    val kind: String = "",
    val label: String = "",
    val pct: Double = 0.0
)

/** Vonis persen posisi sales. */
@Serializable
data class KpiInsentifDto(
    val pct: Double = 0.0,
    val komponen: List<KpiInsentifKomponenDto> = emptyList()
)

@Serializable
data class KpiDetailData(
    val periode: String = "",
    val karyawan: KpiKaryawanDto? = null,
    val position: KpiPositionDto? = null,
    val items: List<KpiItemDto> = emptyList(),
    val totalScore: Double = 0.0,
    val totalPct: Double = 0.0,
    val bracket: KpiBracketDto? = null,
    val insentif: KpiInsentifDto? = null,
    /** Σbobot terisi sudah ≥ 0,5 — di bawah itu server menahan vonis. */
    val filled: Boolean = false
)

@Serializable
data class KpiKaryawanRowDto(
    val karyawanId: String = "",
    val nama: String = "",
    val divisi: String = "",
    val cabangName: String = "",
    val positionJudul: String? = null,
    val bracketMode: String = "",
    val totalPct: Double = 0.0,
    val filled: Boolean = false
)

@Serializable
data class KpiListData(
    val periode: String = "",
    val items: List<KpiKaryawanRowDto> = emptyList()
)

/**
 * Geser periode "YYYY-MM" sejumlah [delta] bulan. Manual, BUKAN `java.time` —
 * `minSdk = 24` tanpa desugaring, `java.time` melempar `NoClassDefFoundError`
 * di API 24/25 (lihat CLAUDE.md). Input tak berbentuk periode dikembalikan apa
 * adanya.
 */
fun shiftPeriode(periode: String, delta: Int): String {
    val parts = periode.split("-")
    if (parts.size != 2) return periode
    val year = parts[0].toIntOrNull() ?: return periode
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return periode
    val total = year * 12 + (month - 1) + delta
    if (total < 0) return periode
    // padStart, bukan String.format: hasilnya dikirim balik ke server sebagai
    // query `periode` — angka non-ASCII dari Locale tertentu akan ditolak
    // `normalize_periode`.
    return (total / 12).toString().padStart(4, '0') + "-" +
        (total % 12 + 1).toString().padStart(2, '0')
}

/**
 * Angka bulat tampil tanpa desimal; sisanya maksimal 2 desimal dengan KOMA
 * (sepadan `formatRupiah` yang memakai titik sebagai pemisah ribuan). Dirakit
 * manual, bukan `String.format("%.2f")` — format itu ikut Locale perangkat,
 * jadi pemisah desimalnya berubah-ubah antar HP dan antar mesin uji.
 */
fun formatKpiNumber(value: Double): String {
    val cents = Math.round(value * 100.0)
    if (cents % 100L == 0L) return (cents / 100L).toString()
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val frac = (abs % 100L).toString().padStart(2, '0').trimEnd('0')
    return "$sign${abs / 100L},$frac"
}

/**
 * "Masih perlu dikejar" — selisih menuju target. `null` bila target sudah
 * tercapai (atau target 0, yang tak bisa dikejar). Actual `null` diperlakukan
 * sebagai 0: indikator yang belum dinilai justru yang paling perlu dikejar.
 */
fun kpiKekurangan(item: KpiItemDto): Double? {
    if (item.target <= 0.0) return null
    val sisa = item.target - (item.actual ?: 0.0)
    return if (sisa > 0.0) sisa else null
}

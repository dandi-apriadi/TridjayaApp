package com.krisoft.tridjayaelektronik.ui.kupongebyar

import com.krisoft.tridjayaelektronik.data.model.KuponGebyarBarisDto

/**
 * Aturan layar "Konsumen Gebyar" sebagai fungsi MURNI — pola sama
 * `AcInstallPlan.kt` / `HomeServicePlan.kt`.
 *
 * Isinya cerminan aturan yang hidup di repo backend
 * (`kinerja-service/src/kupon_gebyar/`) dan tak punya satu pun pemeriksa
 * kompiler lintas repo; `KuponGebyarPlanTest` adalah penggantinya.
 */

/** Halaman daftar. 50 = `DEFAULT_PAGE_SIZE` server; `MAKS_PAGE_SIZE` 200. */
internal const val UKURAN_HALAMAN = 50

/**
 * Boleh menekan tombol "Kirim undangan" untuk baris ini?
 *
 * `sudahDikirim` saja yang menentukan — BUKAN `hp`. Server sudah menyaring
 * baris tanpa nomor dari daftar, jadi memeriksanya lagi di sini hanya akan
 * mematikan tombol untuk baris yang nomornya kebetulan tak lolos parser klien
 * padahal server menganggapnya sah. Batas sesungguhnya tetap di server (409
 * kalau ternyata sudah dikerjakan rekan sedetik lebih dulu).
 */
internal fun bolehKirim(baris: KuponGebyarBarisDto): Boolean = !baris.sudahDikirim

/**
 * Nomor untuk `wa.me` — digit saja, berawalan `62`.
 *
 * Sengaja KONSERVATIF: `null` kalau bentuknya tak meyakinkan, dan tombol WA-nya
 * lalu disembunyikan. Membuka WhatsApp ke nomor yang salah lebih buruk daripada
 * tak punya tombol — undangan mendarat di orang asing, dan karyawan mengira
 * tugasnya beres.
 *
 * Aturan yang ditiru dari `laporan/gebyar/fase0_kandidat_duplikat.py`
 * (`hp_normal`): nomor Indonesia saja. Awalan `+` yang BUKAN `+62` ditolak
 * mentah — `+886902135055` pernah lolos jadi `0886902135055` dan terbaca
 * seperti nomor lokal.
 */
internal fun nomorWa(hp: String?): String? {
    val mentah = hp?.trim().orEmpty()
    if (mentah.isEmpty()) return null
    if (mentah.startsWith("+") && !mentah.startsWith("+62")) return null
    val digit = mentah.filter { it.isDigit() }
    val normal = when {
        digit.startsWith("62") -> digit
        digit.startsWith("0") -> "62" + digit.drop(1)
        // Tanpa awalan apa pun: hanya diterima kalau memang berbentuk nomor
        // seluler tanpa nol di depan (`8123…`). Batas panjangnya ada supaya
        // potongan angka acak tak lolos jadi "nomor".
        digit.startsWith("8") && digit.length in 9..13 -> "62$digit"
        else -> return null
    }
    // 62 + 9..13 digit. Nomor Indonesia terpendek yang sah ~10 digit lokal.
    return normal.takeIf { it.length in 11..15 }
}

/**
 * Teks status kanan kartu.
 *
 * "Sudah dikirim" TANPA nama pengirim tetap ditulis apa adanya: `dikirimOleh`
 * bisa kosong untuk baris yang buktinya diunggah sebelum kolom itu terisi, dan
 * "Sudah dikirim oleh " yang menggantung terbaca seperti bug.
 */
internal fun statusBaris(baris: KuponGebyarBarisDto): String = when {
    !baris.sudahDikirim -> "Belum dikirim"
    baris.dikirimOleh.isBlank() -> "Sudah dikirim"
    else -> "Sudah dikirim · ${baris.dikirimOleh}"
}

/**
 * Rupiah tanpa desimal, pemisah titik — sama dengan format di layar lain app
 * ini. Ditulis manual (bukan `NumberFormat`) supaya tak bergantung locale
 * perangkat: HP yang locale-nya `en-US` akan mencetak koma.
 */
internal fun formatRupiahGebyar(nilai: Long): String {
    val angka = nilai.coerceAtLeast(0).toString()
    val sb = StringBuilder()
    angka.forEachIndexed { i, c ->
        if (i > 0 && (angka.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return "Rp$sb"
}

/**
 * Apakah masih ada halaman berikutnya.
 *
 * Dihitung dari [total] server, BUKAN dari `items.size == pageSize`: halaman
 * terakhir yang kebetulan penuh akan membuat tombol "Muat lagi" tetap tampil,
 * lalu menjawab halaman kosong — dan karyawan menyimpulkan daftarnya rusak.
 */
internal fun adaHalamanLagi(sudahDimuat: Int, total: Int): Boolean = sudahDimuat < total

/**
 * Pesan yang ditempel pada galat unggah yang fotonya SUDAH terkirim.
 *
 * Ada supaya karyawan tak memotret ulang untuk pekerjaan yang cuma kurang satu
 * panggilan — pola sama `AcInstallViewModel.lampirkan`.
 */
internal fun pesanBuktiTertunda(asli: String): String =
    "$asli Fotonya sudah terunggah — pakai \"Simpan ulang\", tak perlu memotret lagi."

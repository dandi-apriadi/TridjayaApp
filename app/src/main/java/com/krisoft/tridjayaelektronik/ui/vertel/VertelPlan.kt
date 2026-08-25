package com.krisoft.tridjayaelektronik.ui.vertel

import com.krisoft.tridjayaelektronik.data.model.BarisVertelDto
import com.krisoft.tridjayaelektronik.data.model.DaftarVertelDto
import com.krisoft.tridjayaelektronik.data.model.VertelHasil
import com.krisoft.tridjayaelektronik.data.model.VertelKanal

/**
 * Aturan MURNI layar verifikasi telepon — cerminan `vertel::validasi_catat`
 * dan `vertel::ringkas` di server. Dipisah dari Compose supaya bisa diuji tanpa
 * perangkat, pola sama [com.krisoft.tridjayaelektronik.ui.acinstall.AcInstallPlan].
 */
object VertelPlan {

    /** Sudah pernah ditelepon? `panggilan == null` = BELUM, dan itu keadaan awal
     *  setiap baris — bukan kegagalan. */
    fun sudahDitelepon(baris: BarisVertelDto): Boolean = baris.panggilan != null

    /**
     * Nomor yang layak ditautkan ke WhatsApp.
     *
     * Sumbernya HANYA [BarisVertelDto.waNumber] — nomor yang sudah dinormalkan
     * server. `null` berarti server sudah menilai nomor mentahnya dan menjawab
     * "tak layak" (kosong, nomor kantor, ngawur). **Jangan** jatuh ke
     * [BarisVertelDto.customerHp] sebagai gantinya: itu membangun tautan yang
     * server sengaja tolak, dan hasilnya chat ke nomor yang salah.
     */
    fun waUrl(baris: BarisVertelDto): String? =
        baris.waNumber?.takeIf { it.isNotBlank() }?.let { "https://wa.me/$it" }

    /** Nomor bisa ditelepon lewat dialer? Berbeda dari [waUrl]: dialer menerima
     *  nomor lokal `08…` apa adanya, jadi patokannya nomor mentah, bukan hasil
     *  normalisasi WA. */
    fun telUrl(baris: BarisVertelDto): String? =
        baris.customerHp?.trim()?.takeIf { it.isNotBlank() }?.let { "tel:$it" }

    /** Kanal & hasil divalidasi terhadap daftar TERTUTUP di server — nilai di
     *  luar ini dijawab 400. Layar hanya menawarkan pilihan dari daftar ini,
     *  jadi fungsi ini menjaga terhadap salah ketik saat menambah pilihan baru. */
    fun kanalSah(kanal: String): Boolean = kanal in VertelKanal.SEMUA

    fun hasilSah(hasil: String): Boolean = hasil in VertelHasil.SEMUA

    /**
     * Boleh menekan "Simpan"? Catatan bebas TIDAK diwajibkan server untuk hasil
     * mana pun — termasuk `ada_komplain`.
     *
     * Sengaja tidak diperketat di klien: komplain yang tercatat tanpa keterangan
     * tetap jauh lebih berguna daripada komplain yang tak jadi dicatat karena
     * verifikator sedang di telepon dan formnya menolak disimpan.
     */
    fun bolehSimpan(kanal: String, hasil: String): Boolean =
        kanalSah(kanal) && hasilSah(hasil)

    /**
     * Persentase pekerjaan hari itu, 0..100.
     *
     * Penyebutnya `total - tanpaNomor`, BUKAN `total`. Transaksi yang nomornya
     * tak bisa dihubungi sama sekali bukan kelalaian verifikator — memasukkannya
     * ke penyebut membuat target mustahil dicapai oleh sebab yang bukan
     * salahnya, dan itu persis alasan server memisahkan `tanpaNomor` sebagai
     * angka tersendiri.
     *
     * Penyebut nol (semua tanpa nomor, atau daftar kosong) → 0, bukan pembagian
     * nol dan bukan 100: tak ada yang bisa dikerjakan belum berarti selesai.
     */
    fun persenSelesai(daftar: DaftarVertelDto): Int {
        val r = daftar.ringkasan
        val bisaDihubungi = r.total - r.tanpaNomor
        if (bisaDihubungi <= 0L) return 0
        val persen = r.sudahDitelepon * 100L / bisaDihubungi
        return persen.coerceIn(0L, 100L).toInt()
    }

    /**
     * Urutan kerja: yang BELUM ditelepon didahulukan, yang tanpa nomor ditaruh
     * paling bawah.
     *
     * Baris tanpa nomor tak bisa dikerjakan sama sekali, jadi menaruhnya di atas
     * berarti verifikator menggulir melewati pekerjaan yang mustahil sebelum
     * sampai ke yang bisa. Selebihnya urutan server dipertahankan — `sortedBy`
     * di Kotlin stabil.
     */
    fun urutKerja(baris: List<BarisVertelDto>): List<BarisVertelDto> =
        baris.sortedBy { b ->
            when {
                waUrl(b) == null && telUrl(b) == null -> 2
                sudahDitelepon(b) -> 1
                else -> 0
            }
        }
}

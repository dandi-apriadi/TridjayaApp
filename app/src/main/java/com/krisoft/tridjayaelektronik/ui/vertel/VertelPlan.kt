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

    /** Apa yang menahan tombol Simpan + kalimat yang menjelaskannya. */
    data class CatatGate(
        val bolehSimpan: Boolean,
        /** `null` saat boleh simpan. */
        val alasan: String?,
    )

    /**
     * Gerbang simpan — cerminan `validasi_catat` di server, urut sama supaya
     * kalimat di app dan kalimat 400 dari server tak pernah berselisih.
     *
     * Dua aturan yang paling mudah dikira berlebihan, dan keduanya milik server:
     * - **Komplain hanya boleh pada panggilan `terhubung`.** Menandai komplain
     *   pada panggilan yang tak pernah tersambung adalah kontradiksi — tak ada
     *   yang bicara, jadi tak ada yang komplain.
     * - **Komplain WAJIB bercatatan.** Tanpa keterangan tak bisa ditindaklanjuti
     *   siapa pun, padahal menindaklanjutinya bagian dari jobdesk verifikator.
     *
     * Menegakkannya di klien bukan duplikasi sia-sia: tanpa ini verifikator
     * menekan Simpan, menunggu round-trip, lalu menerima 400 — untuk isian yang
     * sudah bisa dinilai salah sebelum dikirim.
     */
    fun catatGate(
        kanal: String,
        hasil: String,
        adaKomplain: Boolean,
        catatan: String,
    ): CatatGate {
        val tolak = { alasan: String -> CatatGate(bolehSimpan = false, alasan = alasan) }
        if (!kanalSah(kanal)) return tolak("Pilih kanal dulu: telepon atau WhatsApp.")
        if (!hasilSah(hasil)) return tolak("Pilih hasil panggilannya.")
        if (adaKomplain && hasil != VertelHasil.TERHUBUNG) {
            return tolak("Komplain hanya bisa dicatat pada panggilan yang terhubung.")
        }
        if (adaKomplain && catatan.isBlank()) {
            return tolak("Isi catatan komplainnya supaya bisa ditindaklanjuti.")
        }
        return CatatGate(bolehSimpan = true, alasan = null)
    }

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

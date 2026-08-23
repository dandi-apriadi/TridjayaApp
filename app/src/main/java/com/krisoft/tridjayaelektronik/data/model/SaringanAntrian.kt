package com.krisoft.tridjayaelektronik.data.model

/**
 * Saringan untuk antrian delivery (`GET /api/inventory/delivery`).
 *
 * Ini data murni tanpa Compose — test app ini JVM-unit-only, jadi apa pun yang
 * punya KEPUTUSAN harus hidup di luar `@Composable` atau nol gerbang yang bisa
 * menangkapnya.
 *
 * Sisi server sudah menerima seluruh field ini (`ListQuery`, inventory-service
 * `delivery.rs`) — nol perubahan Rust untuk memakainya.
 *
 * ## Yang WAJIB diketahui sebelum menambah pemakai
 *
 * **[kodeDealer] BOHONG di sebagian layar, tanpa satu pun error.** Penjaganya di
 * server adalah `if filter.kode_dealer.is_none()` — nilai dari klien hanya
 * dipakai kalau rantai peran belum mengisinya:
 *
 *  - **Riwayat PDI**: rantai SUDAH mengisi `kode_dealer` → pilihan klien
 *    diabaikan diam-diam. Kontrolnya jadi tombol mati rasa.
 *  - **Antri Kasir**: lebih buruk. Rantai mengisi `cabang_bayar`, BUKAN
 *    `kode_dealer`, jadi penjaga `is_none()` tidak menahan apa pun dan kedua
 *    klausa di-AND di SQL. Memilih cabang lain menghasilkan daftar KOSONG yang
 *    terbaca sebagai "SPK saya hilang".
 *
 * Karena itu pemilih cabang dirender lewat bendera per-rute
 * ([KontrolSaringan]), **bukan** dari role dan bukan dipasang di semua layar.
 * Kontrol yang berbohong lebih merusak daripada kontrol yang tidak ada.
 */
data class SaringanAntrian(
    /** Cari kode SPK / konsumen / no. transaksi / serial. */
    val q: String? = null,
    /** Kode dealer (mis. `D-01`). Lihat peringatan di KDoc kelas. */
    val kodeDealer: String? = null,
    /** Salah satu [URUT_TERBARU] / [URUT_TERLAMA]. Nilai lain → server 400. */
    val urut: String? = null,
    /** CSV metode kirim — MELEBARKAN daftar, lihat [DELIVERY_METHOD_PILIHAN]. */
    val deliveryMethod: String? = null,
) {
    /** Ada saringan aktif? Dipakai empty-state supaya menyebut sebabnya. */
    val adaYangAktif: Boolean
        get() = listOf(q, kodeDealer, urut, deliveryMethod)
            .any { !it.isNullOrBlank() && it != URUT_TERBARU }

    companion object {
        val KOSONG = SaringanAntrian()

        /**
         * Nilai `urut` yang DITERIMA server. Nilai asing dijawab 400 — ini
         * satu-satunya param saringan delivery yang tidak fail-open, jadi
         * jangan menambah nilai di sini sebelum servernya ter-deploy.
         */
        const val URUT_TERBARU = "terbaru"
        const val URUT_TERLAMA = "terlama"

        /**
         * `deliveryMethod` MEMBALIK default di tahap `pending_scheduling`:
         * kosong = server MEMBUANG `self_pickup` + `sales_delivery`; diisi =
         * tampilkan HANYA metode itu.
         *
         * Jadi memilih "Diambil Sendiri" MENAMBAH baris yang tadinya tak
         * terlihat. Label di layar harus mengatakan itu — pemakai yang mengira
         * semua saringan menyempitkan akan salah membaca daftarnya.
         */
        val DELIVERY_METHOD_PILIHAN = listOf(
            "driver" to "Diantar Driver",
            "self_pickup" to "Diambil Sendiri",
            "sales_delivery" to "Diantar Sales",
        )
    }
}

/**
 * Kontrol saringan mana yang dirender di sebuah layar antrian.
 *
 * Bendera EKSPLISIT per-rute, bukan disimpulkan dari role atau status — lihat
 * peringatan `kodeDealer` di [SaringanAntrian]. Menyimpulkannya berarti
 * memasang kontrol yang diabaikan server di sebagian layar.
 */
data class KontrolSaringan(
    val cari: Boolean = false,
    val cabang: Boolean = false,
    val urut: Boolean = false,
    val metode: Boolean = false,
) {
    val adaKontrol: Boolean get() = cari || cabang || urut || metode

    companion object {
        val NIHIL = KontrolSaringan()
    }
}

/**
 * Teks indikator "daftar terpotong".
 *
 * `null` (= tidak dirender) saat:
 *  - server belum mengirim `total` (APK baru di atas server lama), atau
 *  - `total <= ditampilkan` — daftarnya memang utuh.
 *
 * Sengaja TIDAK memakai `total ?: items.size`: itu mengarang bahwa daftarnya
 * utuh justru pada server yang tak bisa memastikannya.
 */
fun indikatorTerpotong(ditampilkan: Int, total: Int?, satuan: String = "unit"): String? {
    if (total == null || total <= ditampilkan) return null
    return "Menampilkan $ditampilkan dari $total $satuan"
}

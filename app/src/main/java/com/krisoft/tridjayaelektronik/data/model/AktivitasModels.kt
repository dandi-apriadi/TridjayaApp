package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Aktivitas harian (laporan aktivitas harian) — kinerja-service `raport.rs` +
 * `jobdesk.rs` lewat gateway `/api/raport-harian` & `/api/jobdesk-divisions`.
 * Semua field camelCase, sama persis dengan yang dipakai web
 * (`store/picAktivitasStore.ts`).
 *
 * **Istilahnya diganti "jobdesk" → "aktivitas" (2026-08-17), TAPI hanya nama
 * KELAS.** Repo ini nol `@SerialName`, jadi nama PROPERTI di bawah ADALAH nama
 * di kabel: mengganti `jobdesks`/`jobdeskIndex`/`jobdeskText` = field hilang
 * senyap (kotlinx.serialization mengisi nilai default, bukan melempar), dan
 * yang terlihat di lapangan cuma daftar kosong / index 0.
 */

/** Satu posisi/jabatan + daftar aktivitas hariannya (master `app_settings`). */
@Serializable
data class AktivitasPositionDto(
    val id: String = "",
    val posisi: String = "",
    /** NAMA KABEL — jangan ikut di-rename jadi `aktivitas`, lihat KDoc berkas. */
    val jobdesks: List<String> = emptyList(),
    /**
     * Nomor POSISI butir yang sudah dicabut — `KUNCI_NONAKTIF`
     * (`aktivitas_master.rs`). Butir nonaktif **berhenti dihitung sebagai
     * tagihan** di server: ia keluar dari penyebut indikator KPI
     * `LAPORAN AKTIVITAS` (yang bermuara ke slip gaji) DAN dari gerbang absen
     * pulang.
     *
     * Diturunkan sejak 2026-08-28. Sebelumnya app tak mengenal kunci ini sama
     * sekali — konsekuensi yang server sebut DITERIMA (klien lama merender
     * butir nonaktif sebagai pekerjaan biasa, "tak lebih buruk dari hari ini"),
     * bukan terlewat. Yang diperbaiki di sini adalah PENYEBUTNYA: layar riwayat
     * memakai `jumlahButirAktif`, jadi angkanya berhenti berselisih dengan skor
     * yang benar-benar dibayarkan.
     *
     * Nomor posisi, BUKAN array boolean sepanjang [jobdesks] — menambah butir
     * baru di akhir tak menyentuh daftar ini sama sekali.
     */
    val nonaktif: List<Int> = emptyList(),
)

/**
 * Nomor butir nonaktif yang SAH untuk divisi ini — cerminan `nomorButirNonaktif`
 * (web `ownerAktivitasData.ts`) dan `indeks_nonaktif` (Rust).
 *
 * Nomor di luar rentang disaring, dan itu bukan kehati-hatian berlebih: daftar
 * ini dipakai sebagai PENGURANG penyebut, jadi satu nomor liar dari katalog lama
 * membuat penyebut di layar lebih kecil dari yang sebenarnya ditagih — tanpa
 * satu pun error.
 */
internal fun nomorButirNonaktif(posisi: AktivitasPositionDto?): Set<Int> {
    val jumlah = posisi?.jobdesks?.size ?: 0
    return posisi?.nonaktif.orEmpty().filter { it in 0 until jumlah }.toSet()
}

/**
 * Berapa butir divisi ini yang masih DITAGIH.
 *
 * **Cerminan `aktivitas_master::jumlah_butir_aktif` (Rust) dan
 * `jumlahButirAktif` (web) — ketiganya HARUS menjawab angka yang sama.** Layar
 * yang memakai `jobdesks.size` penuh menampilkan pencapaian atas penyebut yang
 * LEBIH BESAR: 10/13 = 77% di layar sementara yang dibayarkan 10/12 = 83%.
 */
internal fun jumlahButirAktif(posisi: AktivitasPositionDto?): Int =
    (posisi?.jobdesks?.size ?: 0) - nomorButirNonaktif(posisi).size

/**
 * Balasan `GET /api/raport-harian/penempatan-saya`.
 *
 * `penempatanId` NULL punya arti sendiri — "orang ini belum punya baris
 * `kpi_assignments`" — dan itu BUKAN sama dengan "permintaannya belum selesai".
 * Pemanggil WAJIB memisahkan keduanya lewat `PenempatanSaya`; lihat KDoc-nya
 * di `ui/raport/RaportPlan.kt` untuk alasan kenapa mencampurnya merusak layar.
 */
@Serializable
data class PenempatanSayaData(
    val penempatanId: String? = null,
    /**
     * Gerbang chat trainee — `null` pada server lama, dan itu ARTI PENTING:
     * "fitur ini tidak berlaku", bukan "belum termuat". Lihat [ChatTraineeDto].
     */
    val chatTrainee: ChatTraineeDto? = null,
)

/**
 * Ambang jumlah chat harian untuk role `trainee` (gelombang 2, vc123).
 *
 * **Server yang memutuskan, klien cuma merender.** Angka di label butir master
 * ("CHAT 200 WA" / "CHAT 100 WA", migrasi 231) SENGAJA tidak boleh di-parse:
 * otoritas ambangnya `app_settings.aktivitas_chat_trainee`.
 *
 * **Sejak 2026-08-31 label dan ambang MEMANG berselisih, dan labelnya sisi yang
 * SALAH.** Ambang non-sales naik 100 -> 150 (sales tetap 200) sementara teks
 * butir sengaja dibiarkan apa adanya — 15 dari 18 divisi masih "CHAT 100 WA".
 * Jadi label kini LEBIH LONGGAR daripada gerbang, kebalikan dari rancangan
 * awal: layar yang memajang angka dari label menjanjikan 100, kirimannya kena
 * 400, dan karena butirnya tak jadi lahir orangnya ikut tertahan absen pulang.
 * Render [ambang] apa adanya.
 *
 * [berlaku] `false` untuk SEMUA orang selain trainee, dan untuk setiap keadaan
 * fail-open di server (saklar mati, setelan rusak, divisi tak ketemu, master
 * tanpa butir ber-prefiks "CHAT "). Klien memperlakukan `berlaku=false` sama
 * dengan blok yang tak ada sama sekali.
 *
 * [aktivitasIndex] = posisi butir CHAT di master divisi orang itu. Dipakai
 * sebagai penambah, bukan penentu: lihat `tampilkanJumlahChat` di
 * `AktivitasBuktiPlan.kt` untuk kenapa arahnya sengaja permisif.
 */
@Serializable
data class ChatTraineeDto(
    val berlaku: Boolean = false,
    val aktivitasIndex: Int = -1,
    val ambang: Int = 0,
    /** `"video"` — satu-satunya nilai hari ini; disimpan apa adanya untuk teks layar. */
    val buktiWajib: String = "video",
)

@Serializable
data class AktivitasDivisionsData(
    val divisions: List<AktivitasPositionDto> = emptyList(),
)

/**
 * Satu baris raport yang SUDAH terkirim hari itu. `evidenceUrl` bisa berisi
 * satu URL atau string JSON array (baris lama web multi-bukti) — layar mobile
 * hanya memakai keberadaannya, tak mem-parsing isinya.
 */
@Serializable
data class AktivitasItemDto(
    val id: String = "",
    /** Pemilik baris — dipakai klien menyaring ulang, lihat `AktivitasRepository`. */
    val employeeId: String = "",
    /** Diisi server dari profil karyawan; kolom PIC memakai ini sebagai judul baris. */
    val employeeName: String = "",
    val cabang: String = "",
    val divisiName: String = "",
    val tanggal: String = "",
    val submittedAt: String? = null,
    /** NAMA KABEL — tetap ejaan lama, lihat KDoc berkas. */
    val jobdeskIndex: Int = 0,
    /** NAMA KABEL — tetap ejaan lama, lihat KDoc berkas. */
    val jobdeskText: String = "",
    val mode: String = "none",
    val evidenceUrl: String? = null,
    val employeeNote: String? = null,
    /**
     * Angka yang diklaim baris ini — hari ini hanya butir CHAT trainee yang
     * mengisinya (`aktivitas_harian.jumlah`, migrasi 314). `null` untuk 154
     * karyawan lain dan untuk server lama, SELAMANYA.
     *
     * NAMA KABEL persis `jumlah`, sama dengan `AktivitasItemPayload` sisi
     * server — repo ini nol `@SerialName`, jadi ejaan lain = field hilang
     * senyap (kotlinx mengisi default, tak melempar).
     *
     * `null` BUKAN `0`: `null` berarti "butir ini tak punya angka", sedangkan
     * `0` adalah KLAIM yang bisa dibaca gerbang. Jangan "merapikannya" jadi
     * `Int = 0` — layar akan menulis "0 chat terkirim" untuk semua orang.
     */
    val jumlah: Int? = null,
    /**
     * Bukti pada baris ini yang ISINYA sama dengan unggahan terdahulu (sidik
     * jari piksel server, migrasi 240).
     *
     * Server hanya mengirimkannya ke pembaca yang boleh melihat raport SEMUA
     * orang — karyawan yang membaca raportnya sendiri sengaja tidak diberi
     * tahu, karena memberitahunya mengajari cara menghindarinya. Absen pada
     * server lama, jadi default `emptyList()` (bukan `null`).
     */
    val buktiDuplikat: List<BuktiDuplikatDto> = emptyList(),
    /**
     * Baris ini jatuh pada tanggal yang punya pengajuan off (izin/sakit/cuti)
     * BERSTATUS approved milik karyawannya. Sama seperti `buktiDuplikat`:
     * server hanya mengirimnya ke pembaca yang boleh melihat raport SEMUA
     * orang — karyawan yang membaca raportnya sendiri tak pernah menerimanya.
     *
     * Penanda, BUKAN pemblokir: baris lama (sebelum gerbang submit
     * `ensure_bukan_off` ada) dan baris auto-isi "Kirim Prospek" (kredit CRM,
     * lewat worker bukan submit manual) tetap bisa muncul di sini.
     */
    val karyawanSedangOff: Boolean = false,
    /** `off_requests.kategori` (izin/sakit/cuti/off) milik penanda di atas. */
    val offKategori: String? = null,
    val reviewStatus: String = "pending",
    val score: Int? = null,
    val reviewerComment: String? = null,
    val reviewedAt: String? = null,
    /**
     * `kpi_assignments.position_id` PENULIS baris ini (server:
     * `a.position_id AS kpi_position_id`) — penempatan KPI yang berlaku saat
     * baris dibuat, bukan tag divisi.
     *
     * Dipakai menentukan PENYEBUT nilai harian: dari sinilah divisi aktivitas
     * yang benar dicari, lalu [jumlahButirAktif]-nya jadi jumlah butir yang
     * ditagih. `null` sah dan berarti "penulisnya belum punya penempatan" —
     * penyebutnya lalu jatuh ke batas bawah data nyata, sama seperti backend.
     */
    val kpiPositionId: String? = null,
)

/** Satu bukti yang isinya sama dengan unggahan terdahulu (server: `DuplikatBukti`). */
@Serializable
data class BuktiDuplikatDto(
    val buktiUrl: String = "",
    /** Berkas yang lebih dulu diunggah. */
    val asliUrl: String = "",
    val asliKaryawanId: String = "",
    val asliKaryawanNama: String = "",
    val asliDiunggahAt: String = "",
    /** Baris raport pemilik berkas asli SUDAH disetujui penilai. */
    val asliDisetujui: Boolean = false,
)

@Serializable
data class AktivitasListData(
    val items: List<AktivitasItemDto> = emptyList(),
    /**
     * Jumlah SELURUH baris yang cocok filter, bukan panjang [items] — server
     * memotong `items` ke `limit` (default 100, maks 2000), jadi badge antrian
     * harus memakai angka ini (pola `DISCOUNT_PENDING`/`CHAT_REVIEW_PENDING`).
     */
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 100,
    val totalPages: Int = 1,
)

/**
 * Putusan PIC atas satu baris raport. Struct server (`ReviewAktivitasPayload`)
 * SENGAJA tanpa `rename_all`, jadi ketiga nama ini apa adanya.
 *
 * `score` boleh `null`: server mengisinya sendiri (`rejected` → 0, selain itu
 * `score ?: 100`, di-clamp 0..100) — lihat `skorReview` yang mencerminkannya
 * supaya angka yang tampil di app sama dengan yang tersimpan.
 */
@Serializable
data class ReviewAktivitasBody(
    val status: String,
    val score: Int? = null,
    val comment: String? = null,
)

@Serializable
data class ReviewAktivitasResult(
    val id: String = "",
    val status: String = "",
    val score: Int? = null,
)

@Serializable
data class AktivitasUploadData(val url: String = "")

@Serializable
data class SubmitAktivitasItem(
    /** NAMA KABEL — tetap ejaan lama, lihat KDoc berkas. */
    val jobdeskIndex: Int,
    /** NAMA KABEL — tetap ejaan lama, lihat KDoc berkas. */
    val jobdeskText: String,
    /** `none` | `image` | `video` — divalidasi ulang server. */
    val mode: String,
    val evidenceUrl: String? = null,
    val employeeNote: String? = null,
    /**
     * Jumlah chat untuk butir CHAT trainee. NAMA KABEL persis `jumlah` — sama
     * dengan field `jumlah` pada `AktivitasItemPayload` (kinerja-service).
     *
     * **SATU ejaan, tanpa alias, dan opsional dengan sengaja.** Struct server
     * tak memakai `deny_unknown_fields` dan `Option` yang hilang jadi `None`,
     * jadi seluruh APK lapangan vc69-vc122 yang tak pernah mengirim field ini
     * tetap dijawab 200 untuk butir NON-chat. Yang berubah hanya butir CHAT
     * milik trainee, dan itu memang sasaran rilis ini.
     *
     * JANGAN menyelipkan angkanya ke [employeeNote]: server menerima catatan di
     * SEMUA mode, jadi angka yang dititipkan di sana KELIHATAN berhasil dan
     * bisa dibohongi dengan mengetik "200" di kalimat apa pun.
     */
    val jumlah: Int? = null,
)

/**
 * `tanggal` & `cabang` SENGAJA tak dikirim: server mengisi tanggal hari ini
 * (zona server) dan cabang dari profil karyawan. Mengirim tanggal dari jam HP
 * yang salah = raport nyasar ke tanggal lain.
 */
@Serializable
data class SubmitAktivitasBody(val items: List<SubmitAktivitasItem>)

@Serializable
data class SubmitAktivitasResult(val saved: Int = 0, val tanggal: String = "")

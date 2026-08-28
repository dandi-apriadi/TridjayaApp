package com.krisoft.tridjayaelektronik.util

/**
 * Kalimat tunggal untuk "app kamera pulang tanpa membawa foto".
 *
 * **Kenapa ada berkas sendiri untuk satu kalimat.** Pola
 * `TakePicture() { ok -> if (ok) { … } }` — TANPA cabang untuk `ok == false` —
 * terpasang di **enam belas** callback kamera di app ini, dan tiap satunya
 * menelan kegagalan simpan tanpa jejak: nol pesan, nol unggahan, nol baris log.
 * Yang dilihat petugas cuma app kamera terbuka lalu tertutup lagi; ia kembali ke
 * layar yang tampak normal dan menyangka buktinya terkirim.
 *
 * Terbukti menggigit 2026-08-28 (Kupon Gebyar, cabang Haurgeulis): "sempat bisa
 * upload tapi hanya 1 yang terupload, sisanya tidak" — tanpa satu pun galat yang
 * bisa ditunjukkan siapa pun. Kegagalan senyap juga merusak penyelidikan:
 * selama kamera bisa gagal tanpa bersuara, laporan "kamera normal" TIDAK bisa
 * dipakai membuktikan penyimpanan HP sehat, dan itu sempat menyesatkan diagnosa
 * insiden yang sama.
 *
 * **`ok == false` menggabungkan dua hal yang tak bisa dipisahkan dari sini**:
 * petugas membatalkan sendiri, ATAU app kamera gagal menulis ke berkas tujuan
 * (direktori cache sudah disapu sistem, penyimpanan penuh). Kontrak
 * `ActivityResultContracts.TakePicture` cuma menyerahkan `Boolean` — tak ada
 * exception, tak ada kode galat — jadi kalimatnya WAJIB menyebut keduanya dan
 * tak boleh memvonis salah satu. Membatalkan itu lumrah dan bukan kesalahan,
 * makanya kalimat ini tidak menyalahkan; ia hanya memastikan orang TAHU bahwa
 * fotonya tidak tersimpan.
 */
const val PESAN_KAMERA_TAK_TERSIMPAN: String =
    "Foto tidak jadi tersimpan. Kalau tadi tidak sengaja membatalkan, " +
        "periksa sisa penyimpanan HP lalu coba lagi."

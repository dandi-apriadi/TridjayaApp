package com.krisoft.tridjayaelektronik.data

/**
 * Saring peta `GET /api/me/capabilities`: **peta KOSONG itu KEGAGALAN, bukan
 * jawaban "kamu tak boleh apa-apa".**
 *
 * **Peta kosong memang tak mungkin sah.** `capabilities_for`
 * (`packages/rust-shared/src/capabilities.rs`, Tridjaya-Web — diperiksa
 * 2026-08-20) memancarkan SELURUH kunci `CAPABILITY_ROLES` lalu menambahkan
 * `spk.pipeline` + `spk.create`; nilainya boleh semuanya `false`, tapi PETANYA
 * tak pernah kosong. Yang bisa menghasilkan `emptyMap()` justru sisi klien:
 * `CapabilitiesDto.capabilities` (`data/model/AuthModels.kt`) ber-default
 * `emptyMap()`, dan converter di `NetworkModule.json` memakai
 * `coerceInputValues = true` + `ignoreUnknownKeys = true` — jadi badan
 * `{"data":{}}` atau `{"data":{"capabilities":null}}` (rename field, canary,
 * proxy yang menelan badan) terdekode 200-sukses berisi peta kosong.
 *
 * `null` adalah nilai yang SUDAH dipahami semua pemanggil: "gagal / offline /
 * server lama". Menyamakan peta kosong dengannya tak menambah cabang baru di
 * mana pun — ia memindahkan satu kelas kegagalan ke jalur yang penanganannya
 * sudah ada.
 *
 * **Apa yang berubah per pembaca — DIPERIKSA SATU PER SATU (2026-08-20),
 * jangan diringkas jadi "keempatnya dapat tombolnya kembali".** Versi
 * sebelumnya dari KDoc ini menulis begitu dan itu KELIRU: efeknya berbeda-beda
 * karena tiap pembaca memperlakukan `null` dengan caranya sendiri.
 *
 *  - `ui/indent/IndentDetailScreen` (`IndentDetailViewModel`) → `gateAllows`.
 *    **SATU-SATUNYA yang benar-benar dipulihkan.** Peta kosong (bukan `null`)
 *    membuat `gateAllows` menjawab `false` — kunci yang absen sengaja
 *    fail-closed — sehingga Setujui/Tolak lenyap. Dengan `null` ia jatuh ke
 *    cadangan `INDENT_APPROVE_ROLES`, jadi tombolnya kembali untuk role yang
 *    memang berhak.
 *  - `ui/opname/OpnameDetailViewModel` → `canPropose`. **Perilakunya BERBALIK,
 *    bukan dipulihkan.** `OpnameDetailUiState.canPropose` ber-default `true`
 *    (sengaja: server lama tak mengirim petanya), jadi peta kosong dulu
 *    menyetelnya `false` (fail-CLOSED, tombol "Usulan SN" hilang) dan kini
 *    `?.let` tak berjalan sama sekali sehingga ia tetap `true` (fail-OPEN,
 *    tombol tampil lalu server menjawab 403). Bukan lubang keamanan — server
 *    tetap gerbangnya — tapi juga bukan "tombolnya kembali dengan benar".
 *  - `ui/homeservice/HomeServiceDetailViewModel` → `bolehDispatch`,
 *    `bolehAturTarik`. **NO-OP.** Keduanya ber-default `false` dan diisi lewat
 *    `caps["x"] == true` di dalam `?.let`, jadi peta kosong dan `null`
 *    menghasilkan hasil yang IDENTIK: tetap `false`.
 *  - `ui/kpi/KpiViewModel` → `canManage`. **NO-OP.**
 *    `capabilities()?.get("kpi.manage") == true` bernilai `false` untuk peta
 *    kosong maupun `null`.
 *
 * Jadi saringan ini menutup satu kegagalan nyata (Indent) dan menggeser satu
 * lagi dari fail-closed ke fail-open (Opname). Yang dua lagi ikut lewat jalur
 * yang sama hanya supaya tak ada pembaca yang memakai aturan berbeda —
 * memperbaikinya betulan menuntut tiap pembaca punya keadaan "tak tahu" yang
 * terpisah dari "boleh"/"tak boleh", dan itu perubahan yang lebih besar dari
 * yang layak ditumpangkan di sini.
 *
 * **Kenapa di sini, di [AuthRepository.capabilities], bukan di tiap pemanggil.**
 * `ui/home/PenyegarKemampuan` sudah menolak peta kosong sejak 2026-08-20, tapi
 * penyegar itu hanya dipakai DUA ViewModel (Home + Activity). Empat pembaca di
 * atas memanggil [AuthRepository.capabilities] mentah. Menaruh saringannya di
 * satu titik masuk membuat aturannya tak bisa menyimpang antar pembaca; daftar
 * tertutup pembacanya dijaga `PembacaPetaKemampuanTest`.
 */
internal fun petaKemampuanSah(peta: Map<String, Boolean>?): Map<String, Boolean>? =
    if (peta.isNullOrEmpty()) null else peta

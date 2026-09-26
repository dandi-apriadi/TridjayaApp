# Tridjaya Elektronik — Android App (repo ini BUKAN lagi sumber APK)

Repo ini fork yang tertinggal. Sumber APK yang beredar sekarang ada di monorepo
`Tridjaya-Web`, direktori `mobile/`. Commit terakhir yang menarik kerja monorepo ke sini
tanggal 2026-09-03; monorepo sudah jauh di depan sejak itu.

**Baca panduannya di `Tridjaya-Web/mobile/CLAUDE.md`, bukan di sini.** Berkas itu kanonik:
keputusan arsitektur, jebakan, kontrak lintas repo, penjaga rilis, dan status fitur semuanya
dirawat di sana.

Sebelum 2026-09-21 berkas ini memuat salinan berkas itu sebanyak 1.324 baris. Salinannya
dihapus karena sudah basi dan berisiko mengajarkan fakta yang tidak lagi benar —
isinya nol baris yang tidak ada di berkas kanonik. Riwayat lengkapnya ada di git
(`git log -p -- CLAUDE.md`).

## Kalau Anda memang harus bekerja di repo ini

- Pastikan dulu ke pemilik proyek bahwa perubahannya memang untuk fork ini, bukan untuk
  APK produksi. Kalau untuk APK produksi, pindah ke `Tridjaya-Web/mobile`.
- Aplikasinya native Android (Kotlin + Jetpack Compose) untuk staf lapangan Tridjaya
  Elektronik: inventory, CRM, KPI, flyer, absen, raport harian, alur SPK → surat jalan →
  serah terima → PDI → kasir, stok opname per serial, indent, mutasi, payroll, dan Pusat
  Notifikasi. Backend-nya microservices Rust di `https://tridjaya.com/api` (repo terpisah).

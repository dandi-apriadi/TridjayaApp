# Tridjaya Elektronik — Android App Master Plan

Native Android app (Kotlin + Jetpack Compose) untuk staf lapangan Tridjaya Elektronik. Awalnya
hanya "browse inventory + CRM + KPI + flyer", kini juga alat kerja harian operasional: absen,
raport harian, alur SPK → surat jalan → serah terima → PDI → kasir, stok opname per serial,
indent, mutasi, payroll, dan Pusat Notifikasi. Talks to an existing Rust microservices backend at
`https://tridjaya.com/api` (separate repo, not part of this project).

Read this file first in any new session. It exists so a future agent doesn't have to
re-derive architecture decisions or repeat mistakes already fixed once.

## Tech stack

- Kotlin, Jetpack Compose (Material3 "Expressive"), no XML layouts
- Hilt for DI (`@HiltViewModel`, constructor injection everywhere)
- Room for local persistence/caching (`data/local/`)
- Retrofit + OkHttp + kotlinx.serialization for networking (`data/remote/`)
- Paging 3 for the Inventory product list
- Encrypted **DataStore** (`TokenStore`, Android Keystore AES-GCM) for JWT tokens + cached user
  profile fields (migrated off the deprecated EncryptedSharedPreferences — see auth section)
- Navigation Compose — one root `NavHost` (login ↔ main) + one nested `NavHost` per bottom-nav
  tab (`ActivityNavHost` — shared route table, mounted twice with a different `startDestination`
  for the Activity and Operasional tabs; `InventoryNavHost`, `LeadsNavHost`)
- minSdk 24, targetSdk/compileSdk 35, Compose BOM 2024.10.01

## Package layout

Daftar di bawah tidak lengkap — ia menyebut yang perlu konteks. `ls` dulu sebelum menyimpulkan
sebuah modul belum ada; app ini sudah jauh lebih luas dari "inventory + CRM + KPI" di paragraf
pembuka (ada alur SPK/pengiriman/PDI, opname, indent, mutasi, payroll, notifikasi, dll).

```
data/
  AuthRepository.kt        auth (login/profile/logout), token refresh race-condition-safe
  InventoryRepository.kt   product/stock sync + paging (Inventory tab only)
  SalesRepository.kt       KPI/target/leaderboard (klasemen) + Home dashboard cache + txn drill-down
  CrmRepository.kt         leads sync/cache + pipeline/CRM actions
  DeliveryFlowRepository.kt  SPK → surat jalan → serah terima → PDI → kasir
  OpnameRepository.kt      stok opname per unit/serial + antrean offline
  RaportRepository.kt      raport harian / Input Aktivitas (BETA) — parseError utamakan `errors[0]`
  NotificationsRepository.kt  Pusat Notifikasi (+ FCM deep-link)
  Indent/Mutasi/Deadstock/Payroll/ErpPriceChanges/SerialInput/Off/Device Repository.kt
  SpkTodayCounter.kt       hitungan SPK hari ini untuk kartu Activity
  ProductImageUrl.kt       resolusi field ERP `Gambar` → URL gambar (dipakai flyer, list, search)
  TokenStore.kt            encrypted DataStore (Keystore AES-GCM): tokens, expiry, profile, mustChangePassword
  SessionCrypto.kt         Android Keystore AES-256/GCM encrypt/decrypt for the session blob
  SessionSerializer.kt     DataStore Serializer<PersistedSession> (encrypts via SessionCrypto)
  ThemePreferences.kt / SearchHistoryPreferences.kt   plain SharedPreferences (bukan terenkripsi)
  local/                   Room entities/DAOs/AppDatabase (branch_stock, leads, dashboard cache,
                           opname_unit, sync meta) — **version 14**
  remote/                  Retrofit API interfaces + NetworkModule (OkHttp client, auth interceptor)
  model/                   @Serializable DTOs mirroring backend JSON
  pricing/                 InstallmentCalculator (cicilan/OTR simulator, ported from TE KOTLINT reference)
  export/                  CSV export, flyer PNG export + WhatsApp/generic share intents
domain/                    use case murni + logika teruji (auth, home, indent, inventory, leads,
                           sales/KlasemenStandings, search) — target utama unit test
di/AppModule.kt            Hilt providers: Room DB, DAOs, TokenStore, repositories
ui/
  activity/     Activity — layar pertama app (tugas harian + antrian ber-gate), ActivityNavHost
                (tabel route dipakai juga oleh tab Operasional, lihat catatan arsitektur di bawah),
                ActivityRegistry/ActivityPlan (gating), PanduanAlurScreen (alur + direktori petugas)
  home/         Dashboard lama (KPI, branch/sales rankings) — kini tab kedua "Operasional";
                QuickAccessMenus.kt = grid Akses Cepat ber-gate (pola sama ActivityRegistry)
  deliveryflow/ SpkHub + layar lapangan (surat jalan, serah terima, PDI, kasir), BranchRegions
  opname/, serials/, indent/, mutasi/, deadstock/, priceerp/, payroll/, notifications/
  raport/       Input Aktivitas (BETA) — lihat "What's implemented"
  inventory/    Product list (search/filter/sort/paging), ProductDetailScreen (flyer generator)
  leads/        CRM: list/search, add, detail (stage move, won/lost/reopen)
  attendance/, search/, sales/, security/, session/, splash/, login/, settings/, update/
  navigation/   AppDestination enum — single source of truth for bottom-nav tabs
  theme/        TridjayaAppTheme, ClayCard, TridjayaBottomNav, TridjayaHeader, RupiahInput, custom icons
MainActivity.kt             hosts every tab's NavHost + the keep-all-tabs-alive bottom nav container
```

## Architecture decisions worth knowing before you touch things

**`java.time` DILARANG di `app/src/main`.** minSdk 24, dan `coreLibraryDesugaring` TIDAK
diaktifkan (cek sendiri: `grep -r desugar` di semua `*.kts` — nihil), sedangkan `java.time`
baru ada di API 26. Kompilasi tetap hijau; yang pecah adalah HP Android 7.0/7.1 di lapangan,
dengan `NoClassDefFoundError` — turunan `Error`, BUKAN `Exception`, jadi gejalanya berbeda-beda
tergantung penangkap terdekat: `runCatching` (menangkap `Throwable`) menelannya jadi **nol
senyap selamanya**, sementara `catch (e: Exception)` tidak menangkapnya sama sekali dan
**app-nya tutup**. Sudah menggigit dua kali dalam sehari (371d0f5 `isGantung` → senyap;
`InventoryRepository.findInTransitHint` → crash saat hasil pencarian kosong). Pakai
`SimpleDateFormat`/`Calendar`, dan pakai ULANG helper yang sudah ada alih-alih menulis util
tanggal ke-sekian: `parseIsoUtcMillis` (`data/model/NotificationModels.kt`) untuk parse ISO,
`KlasemenStandings.todayIso()`/`shiftDays()` (`domain/sales/`) untuk `yyyy-MM-dd` + geser hari.

**Tiap subdirektori `cacheDir`/`filesDir` WAJIB punya entri di
`res/xml/file_paths.xml`.** Kalau tidak, `FileProvider.getUriForFile` melempar
`IllegalArgumentException: Failed to find configured root that contains …`.
Kompilasi hijau, lint hijau, nol test menyentuhnya — pecahnya di HP petugas.
Sudah menggigit **tiga kali**: `event` (foto KTP), `home_service` (foto komplain),
dan `serial` (usulan pendaftaran SN, ditemukan 2026-08-14). Yang ketiga paling
mahal karena `getUriForFile`-nya ada DI DALAM `remember` di `OpnameDetailScreen`
— melempar saat KOMPOSISI, jadi app tutup begitu panel usulan dirender, bukan
saat tombol kamera ditekan. Akibatnya `serial_registration_requests` di produksi
**nol baris sejak fitur itu mendarat 29 Juli**: tak seorang pun pernah berhasil
mengusulkan, dan server tak pernah mencatat error karena request-nya memang tak
pernah terkirim. Sekarang dijaga `FileProviderPathsTest` — ia memindai seluruh
`app/src/main` dan menuntut tiap subdirektori punya deklarasinya. Menambah satu
baris XML ongkosnya nol; melewatkannya ongkosnya fitur mati senyap.

**Region-aware product identity.** The ERP's `kode` (product code) collides across regions —
the same code can be a *different physical product* in a different branch region. Product
identity is always the composite key `kode + kodeCabang`, never `kode` alone. This shows up in
Room primary keys, DAO queries, ViewModel state maps, and nav route args. If you add a new
Inventory feature, key by both fields.

**Cache strategy — uniform 5-hour TTL, Room-backed, no network only.** Inventory
(`branch_stock` table), Home dashboard (`DashboardCacheEntity`, one JSON blob), and Leads
(`LeadEntity`) all sync from the API into Room and are considered fresh for 5 hours
(`SYNC_INTERVAL_MILLIS` / `DASHBOARD_CACHE_TTL_MILLIS` / `LEADS_SYNC_INTERVAL_MILLIS`). Screens
read from Room; a background sync only fires when stale, plus manual pull/refresh buttons.
**Leads are observed reactively**: `LeadDao.observe()/observeAll()` expose Room `Flow`s that
`LeadsListViewModel` collects, so any cache write (create lead, move stage, mark won/lost — all call
`CrmRepository.cacheLead()`) updates the list + KPI strip live with no manual reload. Network sync
still runs on init/refresh; it just writes into the same cache the UI already observes.
This was a deliberate user choice (they were shown a "smarter tiered TTL" option and picked
uniform 5h instead) — don't quietly change it back to tiered without asking.

**Cache Inventory SENGAJA hanya memuat barang berstok > 0 — dan itulah kenapa "stok 0" butuh
jalur sendiri.** `InventoryRepository.sync()` memanggil `stok-cabang` dengan `inStock=true`, jadi
baris berstok nol **tak pernah masuk Room**. Akibatnya layar Inventory tak "menyaring" barang stok
nol; ia memang tak punya datanya, dan orang yang mencarinya menyimpulkan barangnya tak ada di
katalog perusahaan. **Jangan memperbaikinya dengan `inStock=false`**: katalog penuh 66.482 baris
(67 halaman), hanya 5,5%-nya berstok — 28 Juli 2026 SP GS `GetStokCabang` sempat mengembalikannya
dan sinkronisasi HP lapangan tak pernah selesai (834 percobaan berhenti di halaman 4–5, hanya 19
tuntas). `SYNC_MAX_PAGES` (20) juga akan memotongnya, dan snapshot terpotong sengaja TIDAK
memanggil `replaceAll` sehingga baris basi tak pernah dibersihkan. Yang dipakai sejak 2026-08-28
adalah **menambal cache per kata kunci** (`InventoryRepository.lengkapiStokNol` +
`domain/inventory/KatalogStokNol.kt`, diuji `KatalogStokNolTest`): `stok-cabang` dipanggil dengan
`search` + `inStock=false` + `limit` (parameter `search` sudah lama ada — autocomplete Input SPK
memakainya lewat `DeliveryFlowApi`), hasilnya di-`insertAll` ke `branch_stock`, lalu Paging yang
mengamati Room memunculkannya sendiri. Empat hal yang mengikat:
* **`insertAll`, bukan `replaceAll`** — ini tambalan parsial atas satu kata kunci; `replaceAll`
  akan mengosongkan seluruh inventori lalu menyisakan segelintir barang stok nol.
* **`syncMeta` tak disentuh**, supaya `sync()` penuh berikutnya tetap jalan sesuai TTL dan
  `replaceAll`-nya membersihkan baris tambalan ini sendiri. Itu properti sembuh-sendirinya —
  jangan "dioptimalkan" dengan menyegarkan syncMeta di sini.
* **Menambal cache, BUKAN daftar hasil kedua di layar.** Detail produk, rincian per cabang,
  ekspor CSV, dan chip filter semuanya membaca Room; daftar kedua akan menghidupkan baris yang
  tak bisa dibuka.
* **Produk yang masih berstok di salah satu dealer dibuang** walau server mengirimnya — ia sudah
  ada di cache, dan memasukkannya diam-diam menambah baris cabang berstok nol ke produk yang
  sudah tampil. Pengelompokannya `kode + kodeCabang` + `SUM(stok)`, PERSIS seperti DAO; kalau
  tidak, vonis "nol" tak pernah cocok dengan yang dilihat layar.
Chip **"Termasuk stok 0"** sengaja BUKAN kebalikan chip "Ready": "Ready" menyaring apa yang sudah
ada di Room, chip baru ini menambah datanya. Pemicunya (`perluCariStokNol`) menuntut kata kunci
≥ 3 huruf — tanpa itu satu huruf meminta server memindai katalog penuh.

**Migrasi Room ditulis eksplisit begitu tabel memegang data yang belum tersinkron.** `AppDatabase`
kini **version 14** (`branch_stock`, `leads`, dashboard cache, `opname_unit`, sync meta). Bump-bump
awal mengandalkan `fallbackToDestructiveMigration()` dan itu aman selama isi tabel cuma cache yang
bisa di-fetch ulang. Sudah tidak aman lagi: `opname_unit` dan lead `pendingSync` menyimpan **hasil
kerja lapangan yang belum sampai server**, jadi migrasi destruktif = data user hilang diam-diam.
Karena itu `AppModule.kt` (bukan `AppDatabase.kt`) mendaftarkan `MIGRATION_11_12` dan
`MIGRATION_13_14` lewat `.addMigrations(...)`. **Jebakannya:** `.fallbackToDestructiveMigration()`
masih terpasang sebagai jaring pengaman, jadi menaikkan `version` **tanpa** menulis `Migration`
tetap kompilasi hijau dan tetap menghapus tabel di HP user tanpa peringatan (itulah yang terjadi di
12→13). Tiap bump: tanyakan "tabel ini bisa hilang tanpa rugi?" — kalau tidak, tulis `Migration`
sungguhan dan daftarkan.

**Tab switching must not tear down state.** `MainActivity.kt`'s `MainScreen` composes *every
visited tab* once and keeps it alive for the session (visibility-toggled via alpha + a
`blockInputWhen` pointer-input blocker on hidden tabs), instead of disposing/recomposing the
selected tab's NavHost. This was a deliberate fix — a naive `when(selected) { ... }` switch
was previously destroying each tab's ViewModels and forcing a full reload on every tab switch.
Don't revert to that pattern.

**Layar pertama app = `ui/activity/` (Activity), bukan dashboard lama.** Sejak redesain
2026-07-28 (spec `docs/superpowers/specs/2026-07-28-mobile-activity-home-redesign-design.md`
di repo **tridjaya** — backend, bukan repo ini —, branch `feat/activity-home-redesign` di sini),
tab pertama menjawab "hari ini aku harus
ngapain?": tugas harian (absen, prospek), antrian milik role user (PDI/kasir/surat jalan/
approval), dan pintasan "Buat Baru" (`ActivityScreen.kt` + `ActivityViewModel.kt`). Dashboard
lama (KPI/Target/Ranking) pindah utuh ke tab kedua "Operasional" — **satu tabel route**
(`ActivityNavHost.kt`, route anak `home_*` tak berubah) dipakai KEDUA tab lewat parameter
`startDestination` (`ACTIVITY_ROUTE_ROOT` vs `HOME_ROUTE_DASHBOARD`), masing-masing dengan
`NavHostController` sendiri, supaya deep-link push FCM yang sudah ada tetap jalan tanpa
disentuh. Siapa-melihat-apa di Activity diatur **registri ber-gate** `ActivityRegistry.kt`
(`ACTIVITY_ITEMS`, pola sama `ui/home/QuickAccessMenus.kt`) — setiap item WAJIB menyatakan
`capability` (kunci `GET /api/me/capabilities`) + `allowedRoles` cadangan offline +
`backendGuard` (rujukan guard backend asli). `navKey` di registri diterjemahkan jadi route lewat
`routeForNavKey` (fungsi murni, diuji `ActivityNavHostRouteTest`) — kontrak stringly-typed tanpa
pemeriksa kompiler, jangan menambah item baru tanpa menambah kasusnya di sana juga.

**Alur SPK = SATU pencatatan per SPK, kerja fisik tetap per unit.** Pipeline
backend memecah SPK banyak barang jadi satu baris `delivery_jobs` per unit
fisik — itu benar untuk PDI/serial/serah terima, dan baris per unit itulah yang
menghitung statistik kiriman. Tapi sejak 2026-08-05/06 (`b6cbb132`, `b68e2792`,
`c0ee01ac` di repo **tridjaya**) hampir semua endpoint tahap **FAN-OUT se-SPK**:
konfirmasi kasir, klaim PDI (POST+DELETE), surat jalan, penugasan driver,
`dispatch`, `deliver`, dan approve/reject diskon — satu panggilan menyelesaikan
seluruh unit sebatch. Di GS, SPK banyak barang memang SATU transaksi satu nomor.

Konsekuensi yang mengikat app:
- **Jangan pernah memanggil endpoint tahap dalam loop per unit.** Panggilan
  ke-2 dst dijawab 400 "sudah tidak di tahap ini" — pekerjaannya sudah selesai
  di panggilan pertama, tapi layarnya membacanya sebagai kegagalan. Antrian
  (`DeliveryQueueScreen`) mengelompokkan unit lewat `groupJobsBySpk`
  (`SpkBatch.kt`, cerminan `batch_prefix` backend + `utils/spkBatch.ts` web).
- **SATU pengecualian yang disengaja: `setoran-kasir` (2026-08-22).** Endpoint
  ini TIDAK fan-out di server dan tak akan pernah bisa, karena nominal tiap
  barang berbeda — jadi fan-out-nya ada di KLIEN (`setoranKasirSpk`): satu foto
  bukti diunggah SEKALI lalu N `POST` per unit memakai URL yang sama. Yang
  membuatnya aman justru sifat yang bikin endpoint lain berbahaya, dan ketiganya
  terbaca dari `record_kasir_setoran` (`inventory-service delivery/mysql.rs`):
  `UPDATE … WHERE id = ? AND status = 'delivered'` — **status tak berubah**
  (handler-nya menyebut dirinya NON-BLOCKING), **tak ada guard
  `setoran_kasir_at IS NULL`** (mencatat ulang menimpa, bukan ditolak, jadi
  percobaan ulang aman), dan **scope-nya per `id`** (tak ada validasi lintas-unit
  seperti `units[]` milik `confirm_spk`). Sebelum ini kasir memotret slip setor
  yang SAMA sebanyak jumlah barang, padahal antriannya sudah satu kartu per SPK
  sejak 2026-08-06 — kartu yang tetap muncul setelah satu barang dikonfirmasi
  terbaca sebagai gagal-simpan. Aturannya hidup sebagai fungsi murni teruji di
  `SetoranKasirGate.kt` (`unitMenungguSetoran`, `setoranSpkRencana`); kalau salah
  satu dari tiga sifat di atas berubah, yang benar adalah **menuntut endpoint
  se-batch di server**, bukan menambal loop ini. Dua rincian yang mudah dikira
  kelalaian: **nominal TIDAK di-prefill** dari `hargaTotal` (itu cuma sama dengan
  uang yang diterima pada COD `full`; kredit menerima DP-nya saja dan COD `dp`
  menerima sisanya, jadi prefill menaruh angka yang terlihat benar di dua dari
  tiga jenis pembayaran), dan **gerbang render aksinya se-SPK**
  (`unitMenungguSetoran(...).isNotEmpty()`, bukan `job.setoranKasirAt` unit yang
  dibuka) — kalau tidak, kiriman yang separuh berhasil menutup aksi itu sama
  sekali dan sisa barangnya tak bisa disetor dari kartu SPK mana pun.
- **Tiga bentuk antrian, sengaja berbeda:**
  (a) **Kasir** (`pending_spk`) = SATU kartu per SPK (`SpkRingkasCard`), ketuk
  membuka detail; tombol konfirmasinya HANYA di detail. Kasir menyalin satu
  penjualan ke GS sebagai satu transaksi satu nomor — N baris untuk satu
  penjualan membuatnya mengira ada N pekerjaan.
  (b) **PDI & surat jalan** = baris per unit + header grup + tombol di kepala
  grup; kerja fisiknya memang per unit (serial, checklist), keputusannya per
  SPK.
  (c) **Manifest driver** (`reorderable`) = daftar RATA per unit tanpa grup —
  `POST /delivery/driver/reorder` mengurutkan id unit dan panah naik/turun
  bekerja atas indeks daftar itu.
- **Layar detail memuat saudara se-SPK lewat `loadBatchUnits`**, TAPI hanya
  untuk `pending_spk` (satu-satunya tahap yang isiannya butuh daftar itu:
  `units[]` menuntut nominal DP tiap unit COD `dp` sebatch). Sumbernya antrian
  kasir itu sendiri (`GET /delivery?status=pending_spk`) sehingga himpunannya
  sama persis dengan `siblings` yang divalidasi `confirm_spk`. Fail-soft: gagal
  = jatuh balik ke satu unit. Kalau nanti tahap lain butuh daftar saudara,
  perluas fungsi ini — jangan menebak dari `state.items` (isinya antrian mana
  pun yang terakhir dibuka).
- **Ambang barang besar datang dari server**, `GET /delivery/context` field
  `barangBesarThreshold`. Barang besar tetap PDI per unit (checklist + no.
  rangka); barang kecil tuntas sekali klik lewat `POST /delivery/{id}/pdi-kecil`.
  `isBarangBesar` FAIL-CLOSED — harga/ambang tak diketahui = besar, jadi server
  lama otomatis kembali ke perilaku per unit. **Jangan hardcode 1.500.000.**
- **Picker serial SPK MEMPERINGATKAN unit repair/retur, tidak memblokirnya**
  (keputusan user 2026-08-09). `SerialRegistryRow` kini membawa `kondisi` +
  `kondisiKeterangan`; `SpkItemCard` menampilkan peringatan merah saat unit
  terpilih bermasalah, dan `serialUntukDisarankan()` menaikkan unit sehat ke
  atas SEBELUM daftarnya dipotong lima — tanpa itu satu batch unit retur bisa
  mengisi seluruh saran dan menyembunyikan unit layak di posisi keenam. Memblokir
  sengaja TIDAK dipilih: registry bisa telat diperbarui, dan unit repair yang
  sudah selesai diperbaiki masih bertanda repair. Server tidak menegakkan apa pun
  soal ini — murni lapisan klien.
- **Diskon ditolak TIDAK lagi melepas unit.** SPK kembali ke sales dan unitnya
  tetap `pending_discount` sampai dia memilih: revisi diskon (lewat web —
  `POST /discount-requests` tak pernah dipanggil dari app), sunting isi SPK
  (`bolehSuntingSpk`: admin, ATAU sales PEMILIK saat `pending_discount`), atau
  `POST /discount-requests/{id}/lanjut-tanpa-diskon`. Tanpa jalan keluar itu SPK
  mandek permanen dari sisi app **tanpa satu pun pesan error**.
- **Gate serah terima dinilai atas unit yang DIBUKA, bukan se-SPK.** Driver yang
  membuka unit non-COD menuntaskan unit COD sekamar tanpa pernah diminta foto
  uang. App belum bisa memilih anchor sendiri (layar detail tak memuat saudara
  se-SPK) — mitigasinya label "COD · tagih Rp…" di kartu antrian + kalimat
  pengarah di form serah terima. Kalau nanti ada endpoint "unit se-batch",
  inilah tempat pertama yang harus memakainya (`loadBatchUnits` sudah jadi
  polanya — tinggal dilebarkan ke tahap driver).

**Token refresh is synchronized + proactive.** `NetworkModule.kt` has one `TokenRefresher`
(`synchronized`) shared by two callers: `AuthHeaderInterceptor` refreshes **proactively** when the
access token is within ~1 min of its `expires_in`-derived expiry (so most requests skip the 401
round-trip), and `TokenRefreshAuthenticator` is the **reactive** 401 fallback. Both pass the token
they're dissatisfied with to `refresher.refresh(staleToken)`; if the store already holds a
different token (another thread rotated while we waited on the lock), it's reused instead of
refreshing again. This is critical because the refresh token is single-use/rotating: concurrent
refreshes with the same token would have the losers fail and wipe a session the winner just
renewed. Keep the single-refresher + synchronization — removing it reintroduces random forced
logouts.

**Session storage is an encrypted DataStore, not EncryptedSharedPreferences.** `TokenStore` now
persists the whole session (`PersistedSession`: tokens, access-token expiry, cached profile,
`mustChangePassword`) as **one AES-256/GCM blob** in a typed DataStore; the key is a
non-exportable Android Keystore key (`SessionCrypto`), and `SessionSerializer` encrypts/decrypts on
every read/write (returns the empty default on an undecryptable blob so a Keystore loss after a
restore can't crash startup). Jetpack Security's EncryptedSharedPreferences is deprecated — this
replaces it. **Why the API stayed synchronous:** OkHttp's interceptor/authenticator run on
background threads that can't suspend, so `TokenStore` keeps an in-memory `@Volatile` mirror of the
DataStore that those callers read instantly; writes update the mirror synchronously and persist to
the DataStore async (`scope.launch { dataStore.updateData { cache } }` — writing the *latest*
mirror, not a snapshot, so concurrent persists converge idempotently). `warmUp()` (called from
`TridjayaApplication` on `Dispatchers.IO`) seeds the mirror + runs a **one-time migration** from the
legacy `tridjaya_secure_prefs` EncryptedSharedPreferences store before the splash decides
login-vs-main, so existing users don't get logged out on update. Both the DataStore file
(`datastore/tridjaya_session.pb`) and the legacy prefs are excluded from cloud backup/transfer
(`backup_rules.xml` / `data_extraction_rules.xml`) — same "Keystore key isn't backed up" reasoning.

**Password flows + forced-change gate.** `AuthApi`/`AuthRepository` cover `change-password`
(snake_case body), `forgot-password`, `reset-password` (screens in `ui/login/`; voluntary change is
an inline sub-screen in Settings, forgot/reset are root routes off Login). The backend's
`must_change_password` flag is surfaced reactively via `TokenStore.mustChangePasswordState` →
`SessionViewModel.mustChangePassword`; `MainActivity`'s gate `LaunchedEffect` routes a logged-in
user with the flag set to a **blocking** `ROUTE_CHANGE_PW` (no back, system back swallowed) and
releases to Main once `markPasswordChanged()` clears it. The **required-WhatsApp** gate from
`android-api.md` is deliberately **not** implemented yet (was descoped by the user in this pass) —
`updateProfile` + the field plumbing exist, so it's a small follow-up if wanted.

**Floating pill bottom nav (Rhythm `FloatingNavigationBar`), not Material3 `NavigationBar`.**
`TridjayaBottomNav.kt` reproduces Rhythm's actual home-screen nav: a pill-shaped
`FloatingNavigationBar` at the bottom holding the browse tabs (Activity + Operasional — selected
tab expands to icon+label, others are icon-only).

> **Tombol Cari (search FAB) DIHAPUS 2026-07-29** atas permintaan user. Pill kini memenuhi
> lebar layar sendirian dan `TridjayaFloatingNav` tak lagi punya parameter `searchItem`.
> **`AppDestination.INVENTORY` SENGAJA tetap ada** — ia HOST `InventoryNavHost`, jadi
> menghapusnya dari enum akan mematikan seluruh menu Inventory (jelajah barang, detail
> produk, flyer) DAN pencarian gabungan, bukan cuma tombolnya. Ia hanya dilepas dari
> `bottomNavItems` dan kini dijangkau lewat **dua ubin berbeda di seksi PINTASAN layar
> Activity** — satu tab, dua tujuan; jangan digabung jadi satu:
>
> | Ubin | Item registri / `navKey` | Callback | Mendarat di |
> |---|---|---|---|
> | **"Cari Barang"** | `inventory` | `onQuickAccessInventory` → `inventoryOpenListSignal++` | `INVENTORY_ROUTE_LIST` (jelajah barang: filter, urut, paging, stok per cabang) |
> | **"Cari Semua"** | `cari_semua` | `onQuickAccessSearch` → `inventoryOpenSearchSignal++` | `SEARCH_ROUTE_ROOT` (`GlobalSearchScreen`: produk + prospek dalam satu kolom) |
>
> Keduanya di luar `routeForNavKey` (ditangani seperti `"crm"` — tujuannya NavHost lain).
> Tile "Inventory" di grid Akses Cepat Operasional memakai callback yang pertama.
> **Dua sinyal terpisah itu WAJIB**, bukan kemewahan: tab ini tetap ter-compose seumur sesi,
> jadi sesudah sekali "Cari Barang" nav controller-nya duduk di `INVENTORY_ROUTE_LIST` dengan
> `SEARCH_ROUTE_ROOT` sudah di-pop — pindah tab tanpa sinyal akan memunculkan daftar barang
> lagi. `openSearchSignal` memakai `popUpTo(graph.id) { inclusive = true }` (kosongkan seluruh
> tumpukan tab, apa pun isinya) supaya Back dari pencarian keluar ke Activity, bukan mendarat
> di daftar barang. Dijaga `ActivityRegistryTest` (`inventory punya pintu masuk di Activity dan
> tak lagi di bottom nav` + `cari semua adalah pintu kedua yang terpisah dari cari barang`).
>
> Riwayat: antara 41f570d dan perbaikan ini, kedua pintu masuk sama-sama menaikkan
> `inventoryOpenListSignal` sehingga `GlobalSearchScreen` praktis tak terjangkau.

Deskripsi historis tab Cari (masih berlaku untuk isi `InventoryNavHost` itu sendiri): tab ini
membuka **global search** (`GlobalSearchScreen`, `ui/search/`), NOT the
inventory browse screen — one field searches cached products (`InventoryRepository.searchProducts`)
+ leads (`CrmRepository.cachedLeads`) at once, grouped by type; results deep-link to product/lead
detail, and the full filterable inventory browse is still reachable via "Jelajahi semua barang".
The tab's `InventoryNavHost` root is `SEARCH_ROUTE_ROOT`; the browse list (`INVENTORY_ROUTE_LIST`)
is now a pushed sub-screen with its own back button. `GlobalSearchScreen` mirrors Rhythm's
`UniversalSearchScreen`: a top bar (back + "Cari" + a Tune/filter button that reveals
`Semua/Produk/Prospek` type-filter chips), results filling the top, and the **search field docked
at the bottom above the keyboard** — the floating bottom nav is hidden here (`showBottomNav` is
`false` for the whole Cari tab; a back button returns to Home via `onCloseSearch`).
**`MainActivity` MUST keep `android:windowSoftInputMode="adjustResize"`** (in AndroidManifest) —
without it, the bottom-docked search field double-pads on some devices (window auto-resize *plus*
`imePadding()`) and floats mid-screen. The search Column uses `.imePadding()`; adjustResize makes
that report the keyboard correctly. Wired via `TridjayaFloatingNav(pillItems)`, **overlaid** at `BottomCenter`
inside `MainActivity`'s content `Box` (NOT `Scaffold.bottomBar`) so content scrolls *behind* it
like Rhythm — every scrollable tab (Activity/Operasional/Inventory/Leads/RankingList/Settings) adds
~100dp bottom content clearance so nothing hides permanently. Sejak search FAB dihapus, pil
memenuhi lebar sendirian dan tab terpilih diberi **sisa ruang** supaya label panjang
("Operasional") tak terpotong (6b40d08) — jangan kembalikan ke pembagian rata. The nav **hides on
any sub-screen**: `MainScreen` hoists each tab's nested `NavHostController`, watches its current route
via `currentBackStackEntryAsState`, and an `AnimatedVisibility` (slide down + fade) shows the nav
only when the selected tab is on its root route — hidden on pushed details (product/lead/ranking/add)
and on Settings, so those full-screen pages own the frame. Each nested `NavHost` uses Rhythm's
sub-screen transition
(`fadeIn(300) + slideInVertically(offsetY = it/4, tween 350 EaseInOutQuart)`, reversed on pop).
This was chosen over Material3 `NavigationBar` **and** over `NavigationSuiteScaffold` at the user's
explicit request (they compared all three) — don't swap it without asking. The Leads screen's own
add FAB is deliberately a smaller tonal `SmallFloatingActionButton` so it reads as secondary.

**Edge-to-edge is handled in `Theme.kt` via `SideEffect`**, not `enableEdgeToEdge()` in
`MainActivity`. Every screen's own `Scaffold` sets `contentWindowInsets = WindowInsets(0,0,0,0)`
and consumes status-bar/nav-bar insets itself (via `TridjayaHeader` or explicit
`windowInsetsPadding`) — don't let two layers both reserve the same inset or you get a double-padded
gap (this happened twice already, see git history/session logs if it recurs).

**`ClayCard` uses `Surface`, not Material3 `Card`.** Deliberately built with independent
`tonalElevation`/`shadowElevation` (shadow defaults to 0) because `Card`'s bundled shadow forces
a redrawn shadow layer on every visible row during list scroll — multiplied across 15-20 rows
during a fling, this was a measurable scroll-perf cost. Keep list-row usages shadow-free;
only opt into `shadowElevation > 0` for non-scrolling standalone cards if really needed.

**Consistent offline/error UX: `ExpressiveErrorState`.** Every data screen that fetches over the
network must, when it fails **with no cached data to fall back on**, show
`ExpressiveErrorState(message, onRetry)` (`ui/theme/ExpressiveComponents.kt` — cloud-off icon +
"Gagal memuat" + a "Coba lagi" button) wired to the ViewModel's existing reload — **never** a bare
`Text(errorMessage)`, a blank screen, or a stuck spinner. This is applied on Home
(`loadDashboard`), RankingList (`load`, plus an empty-state for zero results), Leads list
(`refresh`), Lead detail (`load`, only when it's a real error vs a genuine "not found"), Product
detail (`load`), and Inventory Paging (inline "Coba lagi" on the sync banner, `pagingItems.retry()`
on append errors, and a full error state when the initial refresh fails with an empty DB). Screens
that read only local Room/cache and stay useful offline (Global search, cached Leads/Inventory
lists, Add-lead's offline queue) deliberately show cached data or an empty state instead — the
error state is specifically for the network-failed-and-nothing-to-show case. Verified live via
airplane-mode (cleared Room DB but kept the session) — each screen showed the error+retry card, and
tapping retry either reloaded or, on an expired session, logged out cleanly to Login.
Use `ExpressiveEmptyState` for "no results" and `ExpressiveErrorState` for "load failed".

## Product flyer generation (Inventory → Product Detail)

`ProductDetailScreen.kt` renders a poster-styled "flyer" (`ProductFlyer` composable) matching a
specific reference design (blue/white poster with promo price, tenor/cicilan grid, frosted-glass
price cards) — colors are intentionally hardcoded in a `FlyerColors` object, not
`MaterialTheme`-driven, so the shared image looks identical regardless of the user's device theme.

Capture works via **`PixelCopy`** on the host Window, cropped to the flyer's
`onGloballyPositioned` bounds, with a `legacyCapture()` `View.draw(Canvas)` fallback when no Window
is reachable — see the performance section for why. **Batasnya API 26, bukan 24** (catatan lama di
berkas ini keliru): overload ber-`srcRect` `PixelCopy.request(Window, Rect, Bitmap, …)` baru ada di
Oreo, sedangkan minSdk 24. Sejak perbaikan lint 2026-08-14 ada penjagaan `Build.VERSION.SDK_INT`
eksplisit yang menurunkan API 24–25 ke `legacyCapture()`; tanpa itu HP Android 7.0/7.1 crash
`NoSuchMethodError` saat menekan "Buat Gambar"/"Kirim ke WA". No newer Compose `GraphicsLayer` capture API is
used (wasn't confirmed available in this project's resolved Compose version, so don't assume it
exists without checking `ui-graphics-android`'s actual jar contents first). Three actions: "Buat
Gambar" (generate + generic Android share sheet), "Kirim ke WA" (generate + `Intent` targeted at
`com.whatsapp`, falls back to generic share if not installed), "Salin" (copies a formatted
"Struktur Kredit" text block to clipboard).

**Product images ARE implemented now** (catatan lama "tidak ada field foto" sudah kedaluwarsa).
Foto datang dari field ERP `Gambar`; `data/ProductImageUrl.resolve()` menormalkan nilainya —
formatnya belum pasti dari backend, bisa path relatif atau URL penuh, jadi ia melewatkan yang
sudah `http(s)://` dan memprefiks sisanya dengan `BuildConfig.API_BASE_URL`. **Semua** pemakai
harus lewat helper itu supaya aturannya seragam (`FlyerLayouts.kt`, `InventoryScreen.kt`,
`GlobalSearchScreen.kt`); render pakai Coil (`io.coil-kt:coil-compose`). Placeholder tetap dipakai
saat `Gambar` kosong/null.

## Installment/cicilan simulator (`data/pricing/InstallmentCalculator.kt`)

Ported line-for-line from a separate reference project (`C:\laragon\www\TE KOTLINT`, an older
Fragment/XML version of similar functionality) — OTR/DP/tenor math is copied exactly, including
its quirks (two different calculation paths depending on product category — "ADV" categories
like Sepeda Listrik/Laptop/Handphone/TV use one bracket-lookup table, everything else derives a
final OTR via a two-step 12-month lookup). Price-bracket lookup tables are bundled CSVs in
`app/src/main/assets/pricing/`. If the reference project's business logic ever changes, this
needs to be re-ported by hand — there's no shared library between the two projects.

## Absen (Kehadiran) — mobile, WIRED ke backend nyata

Fitur absen **check-in + selfie + lokasi (geofence)** di `ui/attendance/`, tersambung ke backend
**`kinerja-service` modul absensi** (`tridjaya` repo, branch `feat/absensi-karyawan`,
`src/absensi.rs` + `absensi_upload.rs`) via gateway `/api/absensi/*`. **Bukan dummy** — awalnya
dibangun dummy (backend belum ada), lalu user membuat backend-nya dan modul di-refactor ke wiring
nyata. Kontrak lengkap: `docs/absen-api-contract.md`.

- **Alur punch**: ambil GPS + selfie → **upload selfie dulu** (`POST /api/absensi/upload-photo`
  multipart field `file`, ≤5 MB) → dapat URL relatif → `POST /api/absensi/check-in|check-out`
  `{lat,lng,photoUrl}`. Server menghitung jarak geofence (Haversine), telat/pulang-cepat, dan
  `status` (`valid` bila dalam radius, `pending_review` bila di luar → butuh approve reviewer).
  App **tidak** menghitung geofence sendiri (config cabang admin-only) — verdict tampil dari record.
- **Layer app**: `data/model/AttendanceModels.kt` (`AbsensiRecordDto` 1:1 camelCase),
  `data/remote/AbsensiApi.kt` (Retrofit, `today`/`list`/`check-in`/`check-out`/`upload-photo`),
  `data/AbsensiRepository.kt` (no cache — absen harus real-time), `AttendanceViewModel`
  (today+history paralel, kompres selfie ≤2 MB/1600px + EXIF, punch), `AttendanceScreen`.
  DI: `NetworkModule.createAbsensiApi` + `AppModule.provideAbsensiApi`.
- **Selfie**: full-res `ActivityResultContracts.TakePicture()` + FileProvider (cache-path `absensi/`
  di `file_paths.xml`, authority `${applicationId}.fileprovider`). **Tanpa izin `CAMERA`** (delegasi
  ke app kamera; kalau CAMERA dideklarasi wajib request runtime — jangan tambah tanpa alasan).
- **Lokasi**: framework `LocationManager` (`ui/attendance/LocationProvider.kt`, suspend, tanpa
  play-services) — izin `ACCESS_FINE/COARSE_LOCATION` di manifest + request runtime.
- **Menu**: absen adalah tugas harian pertama di layar **Activity**, route nested `home_absen` di
  `ActivityNavHost` (nama route `home_*` sengaja tak berubah saat `HomeNavHost` di-rename — lihat
  catatan arsitektur Activity). Role gate di **backend** (STAFF_ROLES self-service); gate di app
  kini dinyatakan lewat `ActivityRegistry`/`QuickAccessMenus`, bukan lagi "semua user lihat menu".
- **Prasyarat data**: tiap cabang perlu di-set geofence via `PUT /api/absensi/config/{cabangId}`
  (admin). Tanpa config → jarak null, absen tak pernah di-flag telat/luar-area (fail-open).

## Signing / release builds

- `release-keystore.jks` + `keystore.properties` (git-ignored, **not** committed) hold the real
  release signing key. **Back these up outside the repo** — if lost, this app can never be
  updated again under the same signature on a real device/Play Store.
- `app/build.gradle.kts` reads `keystore.properties` at build time and wires
  `signingConfigs.release` only if the file exists — a machine without `keystore.properties`
  still builds an *unsigned* release APK (won't `adb install`), which is intentional (never
  hardcode credentials in the build script).
- `isMinifyEnabled = true` + `isShrinkResources = true` for release, with proguard rules for
  kotlinx.serialization, Retrofit, and Google Tink (`security-crypto`'s transitive dep — needs
  `-dontwarn com.google.errorprone.annotations.**` or R8 fails on missing classes).
- **R8 is legitimately slow in this dev environment** (observed 5-15+ min for `minifyReleaseWithR8`
  alone) — this is environment-specific, not a sign the build is stuck. Don't kill it prematurely;
  check CPU usage via `ps aux | grep java` or `top` if in doubt (climbing CPU = still working).
- A signed release APK and a debug APK have **different signatures** — installing one over the
  other on the same device requires `adb uninstall` first (wipes local app data/login). Always
  ask before doing this; it's destructive to the user's test session.
- Observed R8 wall-clock on a full release build here: **~44 min** once (cold-ish). Build via
  `run_in_background`, watch the Gradle daemon's `java` CPU/RAM climbing to confirm progress,
  and only trust `BUILD SUCCESSFUL` in the output — `--console=plain` buffers, so an empty output
  file mid-build is normal, not a hang.
- **Peta deobfuskasi R8 diarsipkan OTOMATIS** — `assembleRelease` di-`finalizedBy`
  tugas `arsipkanMappingRilis`, yang menyalin
  `app/build/outputs/mapping/release/mapping.txt` ke
  `../cadangan-lokal/mapping-<versionName>-vc<versionCode>.txt`. **Jangan cabut
  penautannya, dan jangan kembalikan jadi langkah manual di runbook.** Manual
  sudah dicoba dan gagal LIMA KALI berturut-turut: audit 2026-08-16 menemukan
  `cadangan-lokal/` cuma memuat `mapping-2.71-vc82.txt`, artinya peta untuk 2.76
  s/d 2.80 hilang permanen — ditimpa `assembleRelease` berikutnya, nol error,
  nol gejala, dan baru ketahuan karena ada yang kebetulan mendaftar isi
  direktori. Tanpa peta itu, stack trace crash dari HP pengguna tak terbaca lagi
  selamanya. Arsipnya sengaja hidup DI LUAR pohon kerja git (sejajar repo, sama
  seperti keystore): arsip di dalam repo adalah arsip yang lenyap pada
  `git clean -xdf` berikutnya. Tugasnya **berhenti dengan galat** kalau arsip
  untuk versionCode yang sama sudah ada dengan isi BERBEDA — itu berarti dua
  biner memakai satu nomor versi, dan menimpanya membuang peta milik biner yang
  mungkin sudah beredar.
- **`versionCode` must be bumped** in `app/build.gradle.kts` for every release (per 2026-07-29:
  `versionCode = 49`, `versionName = "2.38"`) — the update system's Remote Config comparison and
  Play/side-load upgrades both depend on it. Pola commit yang dipakai: satu commit
  `chore(release): bump versi X.YY (<ringkasan>)` di akhir tiap batch fitur/fix.

## Release hardening (production-readiness pass)

Done in a dedicated "is this ready to ship?" pass; don't regress these:

- **Launcher icon works on API 24/25.** minSdk is 24 but the icon was previously *only*
  `mipmap-anydpi-v26/ic_launcher.xml` (adaptive icons are API 26+), so on Android 7.0/7.1 the
  launcher had no raster to fall back to. Fixed by generating PNG mipmaps for **all five
  densities** (`mipmap-mdpi…xxxhdpi`, both `ic_launcher` + `ic_launcher_round`) from the exact
  `ic_launcher_foreground.xml` geometry (white building glyph on `#0D47A1`), plus an
  `ic_launcher_round.xml` adaptive icon for v26+ and `android:roundIcon` in the manifest. The PNGs
  were rasterized with a one-off Pillow script (no Android Studio Image Asset tool in this env) —
  if the icon design changes, regenerate all 10 PNGs, don't hand-edit them.
- **Backup/transfer disaring isinya, `allowBackup` tetap `true`.** `res/xml/backup_rules.xml`
  (`fullBackupContent`, API<31) dan `res/xml/data_extraction_rules.xml` (`dataExtractionRules`,
  API 31+) memuat daftar `<exclude>` yang **harus sejajar**, dan di berkas kedua daftar itu wajib
  ditulis DUA KALI — `<cloud-backup>` dan `<device-transfer>` adalah jalur terpisah, mengecualikan
  di satu blok saja meninggalkan yang lain terbuka. Dua alasan berbeda hidup di daftar yang sama:
  * **Tak bisa dipulihkan** — `tridjaya_secure_prefs` + `datastore/tridjaya_session.pb`: kunci AES-nya
    ada di Android Keystore yang tak pernah ikut backup, jadi blob yang dipulihkan tak bisa
    didekripsi dan bisa crash saat baca pertama. User tinggal login ulang.
  * **Tak boleh keluar dari perangkat** (ditambahkan 2026-08-14, temuan audit #8) — Room
    **`tridjaya.db`** beserta `-wal`/`-shm`/`-journal`, plus sharedpref `search_history`. Isinya
    PII pelanggan: tabel `leads` menyimpan nama/phone/lokasi/catatan calon pembeli, `dashboard_cache`
    menyimpan direktori petugas, dan riwayat pencarian global memuat nama prospek yang diketik.
    minSdk 24 berarti di Android 7–11 `adb backup` menariknya **tanpa root**. Semuanya cache yang
    bisa di-fetch ulang sesudah login, jadi tak ada yang hilang permanen — kecuali baris
    `pendingSync`/`opname_units` yang belum tersinkron, yang sengaja tidak ikut pindah perangkat.
  * `filesDir/update/` (APK pembaruan in-app) juga dikecualikan — bukan privasi melainkan **kuota**:
    Auto Backup cuma 25 MB per app, satu APK puluhan MB di situ menggagalkan seluruh backup senyap.
  * Direktori cache (`getCacheDir()`, `code_cache`, `no_backup`) sudah dikecualikan Android sendiri
    — selfie absen, foto bukti, dan cache gambar Coil tidak perlu disebut.
  * `allowBackup="false"` SENGAJA tidak dipilih: app ini side-load enterprise dan perpindahan
    perangkat karyawan masih diinginkan. Yang disaring isinya, bukan mekanismenya.
- **Dev artifacts removed from the release surface.** The stale LAN IP `10.132.14.53` was dropped
  from `network_security_config.xml` (only emulator loopback `10.0.2.2`/`localhost` keep cleartext;
  prod is HTTPS-only), and the root `serve.log` was deleted.
- **Two loading-spinner hangs fixed.** `ProductDetailViewModel` and `GlobalSearchViewModel` wrapped
  their `viewModelScope.launch` load in try/catch so a thrown read never leaves `isLoading`/
  `isSearching` stuck forever (they fall through to the existing "not found"/empty states;
  `GlobalSearch` rethrows `CancellationException` so a superseding search still cancels cleanly).

## Build & deploy workflow (this dev environment specifically)

**Dipindah dari Windows/laragon ke Kali Linux pada 2026-08-08 — semua path di bawah sudah
diverifikasi ulang di mesin baru; jangan pakai catatan `C:\...` versi lama.**

- No Android Studio GUI available in this environment — everything via Gradle CLI.
- Sistem cuma punya `openjdk-25-jre` (JRE, **tanpa `javac`**) — `javac`/`java` di PATH tidak cukup
  untuk build. JDK 17 yang benar ada di `~/.jdks/jdk-17.0.20+8` (Temurin), diekspor sebagai
  `$JAVA_HOME_17` lewat `~/.zshrc`. **Selalu override eksplisit:**
  `JAVA_HOME=$JAVA_HOME_17 ./gradlew <task>` (gradlew fallback ke `java` biasa di PATH kalau
  `JAVA_HOME` kosong, yang salah versi).
- Tidak ada lagi isu quoting PowerShell untuk flag `-P` — di bash/zsh
  `-Pkotlin.compiler.execution.strategy=in-process` jalan normal tanpa perlu tanda kutip khusus.
- Builds are slow (1-3+ min for debug, much longer for release/R8) — always run via
  `run_in_background: true` and poll with `ScheduleWakeup`, don't block synchronously.
  Patokan nyata: `:app:installDebug` dingin (daemon baru) memakan **~18 menit** di mesin ini.
  (Angka ini dari mesin Windows lama — belum ada patokan baru di Kali, catat ulang kalau sudah ada.)
- Android SDK: `~/Android/Sdk`, dipasok lewat `local.properties` (`sdk.dir`) **dan** `$ANDROID_HOME`
  (diekspor di `~/.zshrc`).
- `adb` ada di `$ANDROID_HOME/platform-tools/adb` dan **sudah masuk `$PATH`** (lewat `~/.zshrc`) —
  panggil `adb` langsung, tidak perlu path lengkap.
- Test device: physical phone, serial `30531702210004R`. `adb devices -l` sometimes shows it
  disconnected if the USB cable/authorization dropped — ask the user to reconnect rather than
  assuming the device is gone.

## Performance hardening (perf/UI/responsiveness pass)

Three fixes from a dedicated performance audit — don't regress these:

- **Flyer capture is off the main thread.** `ProductDetailScreen.kt`'s `captureBitmap()` now uses
  `PixelCopy` (API 26+ untuk overload ber-`srcRect`; API 24–25 turun ke `legacyCapture()`)
  to copy the already-rendered window pixels on the render thread and deliver
  the result via callback, instead of the old `View.draw(Canvas)` path that allocated a full-screen
  `ARGB_8888` bitmap and rasterised the whole view tree synchronously on the UI thread (a visible
  freeze on tap). A `legacyCapture()` software fallback remains for the rare case where no host
  Window is reachable. It's a `suspend` fn — callers already invoke it from a coroutine.
- **Home dashboard fires its 4 endpoints concurrently.** `SalesRepository.homeDashboard()`
  wraps the KPI / monthly-target / branch-performance / sales-performance calls in
  `coroutineScope { async { … } }` so cold-load latency is the slowest single round-trip, not the
  sum of four. Keep them independent — don't serialise them back.
- **Baseline Profile.** A `:baselineprofile` module (`com.android.test` +
  `androidx.baselineprofile` plugin, `useConnectedDevices = true`) generates an AOT-compilation
  profile from the app's startup path; `androidx.profileinstaller` (added to `:app`) installs it on
  first run, removing cold-start / first-scroll JIT jank. The committed profile lives at
  `app/src/release/generated/baselineProfiles/`. **Regenerate** after meaningful startup/UI changes:
  `:app:generateBaselineProfile` (needs the physical device connected + unlocked). Gotchas seen
  once: (1) the cold generation build is very slow in this env (~2h the first time, ~8min cached);
  (2) `INSTALL_FAILED_UPDATE_INCOMPATIBLE` if a differently-signed build of the app is already on
  the device — the generator's `nonMinifiedRelease` is release-signed, so `adb uninstall` any debug
  build first (UTP also clears it on teardown). The deeper journeys (product-list scroll, CRM) sit
  behind the login gate and aren't automated, so the profile is startup-focused by design.

**Deferred from the same audit (not done — check with the user before starting):** no
`WindowSizeClass`/adaptive layouts (single-column `fillMaxWidth` everywhere — fine for phones, not
optimised for tablet/landscape/foldable); the shared flyer is captured at device width, so the
exported image's aspect ratio varies by screen (a fixed render width would make it consistent).

## App update system + Firebase (Remote Config)

Force-update / optional-update / "Cek Pembaruan" (Settings) driven by **Firebase Remote Config**:

- `UpdateManager` (`data/update/`) reads Remote Config keys `min_version_code`, `latest_version_code`,
  `latest_version_name`, `update_url`, `release_notes` and compares to `BuildConfig.VERSION_CODE`:
  below `min` → **force** (blocking, non-dismissible `UpdateDialog`, back/outside ignored); below
  `latest` → optional dismissible prompt; else up-to-date. Awaits Play-services `Task`s via a tiny
  local `awaitResult()` (no `kotlinx-coroutines-play-services` dep).
- Startup gate: `TridjayaNavHost` (MainActivity) hosts `UpdateViewModel`, checks once, overlays the
  force dialog over the whole app (incl. login). Settings → **Aplikasi**: shows `BuildConfig`
  version + a "Cek Pembaruan" item (manual check → dialog or "sudah terbaru" toast).
- **Firebase is optional at build time — gated on `app/google-services.json` like the release
  keystore.** The `com.google.gms.google-services` plugin is applied only if that file exists
  (`app/build.gradle.kts` tail); `firebase-bom` + `firebase-config-ktx` are always present but inert
  without a default `FirebaseApp` — `UpdateManager` checks `FirebaseApp.getApps().isEmpty()` and
  returns `Unknown` (never forces). So the app builds & runs today; force-update stays off.
- **To activate:** (1) drop your Firebase project's `google-services.json` into `app/` (plugin
  auto-applies on next build), (2) set the 5 Remote Config keys in the Firebase console. No code
  change needed. Bump `versionCode` in `app/build.gradle.kts` for each release so the comparison works.

## What's implemented

- Login (NIK/WhatsApp + password), JWT session in encrypted DataStore, proactive + reactive
  auto-refresh, forced `must_change_password` gate, and change/forgot/reset-password flows
- Home (tab Operasional): KPI summary (today/MTD + growth badges vs yesterday/last month),
  branch + sales rankings (top 5 + "lihat semua"). Dashboard
  sections (KPI / Target / Ranking Cabang / Ranking Sales) are **user-reorderable + show/hide**
  via a "Tune" button → `HomeCustomizeSheet` (up/down arrows, not drag). Order+visibility persist
  in plain (non-encrypted) SharedPreferences via `HomeLayoutPreferences` (Hilt constructor-injected).
  **Kartu sapaan sudah TIDAK di sini** — pindah ke Activity (lihat baris berikut); slot teratas
  Home kini murni `EventCarousel`, dan tak dirender sama sekali kalau tak ada event aktif.
- Kartu sapaan (`ui/activity/GreetingCard.kt`): gradien + ikon berubah per waktu
  (pagi/siang/sore/malam) dengan override musiman (`seasonalGreeting`, mis. Agustus =
  Kemerdekaan). Tampil paling atas di **Activity** (layar pertama app), menggantikan
  `GreetingRow` teks polos lama; baris tanggal ikut membawa nama cabang (param `cabang`)
  supaya info yang dulu ditampilkan `GreetingRow` tak hilang. Kartunya fixed, tak ikut
  pengaturan urutan `HomeCustomizeSheet`.
- Inventory: search (Material3 `SearchBar`), filter chips (ready-only, region, category, brand),
  sort, Paging3 list with expandable per-branch stock breakdown, product detail with flyer
  generator + WhatsApp share + installment simulator
- CRM/Leads: list with search + summary stats, add lead, detail screen (WhatsApp chat deep link,
  pipeline stage picker, won/lost/reopen actions)
- **Alur SPK → pengiriman → PDI → kasir** (`ui/deliveryflow/`): SpkHub + daftar SPK, input item
  (autocomplete barang ber-stok+harga, picker serial, No PO, kolom nominal berformat rupiah lewat
  `ui/theme/RupiahInput.kt`), terbit surat jalan sekali ketuk, serah terima ber-GPS, klaim PDI
  ("Ambil PDI" + label "diproses oleh X"), konfirmasi pembayaran kasir. Syarat kirim disatukan
  (link Maps wajib untuk Sales Antar Sendiri); alasan tolak diskon wajib.
- **Stok opname per unit/serial** (`ui/opname/`, `data/OpnameRepository.kt`): hitung fisik per unit
  ber-serial — **bukan** angka jumlah per SKU — layar scan per unit, laporan PDF berisi daftar
  serial, antrean offline. Normalisasi serial dijaga sejajar implementasi Rust lewat unit test.
  **Kondisi unit = 4 nilai** sejak 2026-08-09 (`KONDISI_PILIHAN`: layak / tidak_layak / repair /
  retur, + kolom keterangan bebas), menggantikan checkbox biner "TIDAK layak". Daftarnya cerminan
  `opname::KONDISI_VALID` Rust (migrasi 194) dan dijaga `OpnameKondisiTest` — kontrak
  stringly-typed lintas repo tanpa pemeriksa kompiler. **Server kini MENOLAK nilai asing** per
  baris (`kondisi_tidak_dikenal`); dulu dipaksa jadi `layak` diam-diam, jadi nilai yang meleset
  bukan lagi "tersimpan salah" melainkan unitnya tak terhitung sama sekali. Kondisi & keterangan
  sengaja BERTAHAN antar scan dalam satu sheet (satu rak rusak ditandai berturut-turut);
  meresetnya tiap unit membuat petugas diam-diam mencatat sisanya sebagai layak.
  **Selisih registry vs lapangan** (`kondisiRegistry`/`kondisiSelisih` dari server) tampil
  sebagai kartu di layar detail sesi. Nilainya SENGAJA tidak disimpan ke Room: vonis registry
  berubah kapan saja admin-stok mengubahnya, jadi menyalinnya ke buffer lokal = menampilkan
  vonis basi di layar yang justru dipakai memverifikasi. Konsekuensinya kartu itu kosong saat
  offline — itu jujur, pembandingnya memang tak terbaca. `refreshValidationStatuses` karena
  itu MENGEMBALIKAN daftar unit versi server, bukan cuma menulis ke Room.
  **Gate tampilan kartu Activity: akun uji + PELAKSANA NYATA (2026-08-14, dua
  babak — baca keduanya).** Pagi harinya gate dipasang AKUN UJI SAJA; sorenya
  dipersempit karena terbukti terlalu lebar. Keadaan sekarang:
  `ITEM_KHUSUS_AKUN_UJI` tetap memuat `opname_cabang` + `opname_validasi`, TAPI
  `TEMBUS_AKUN_UJI` memberi jalan tembus untuk `OPNAME_PELAKSANA_NYATA`
  (`admin-stok`, `admin`, `superadmin`, `kepala-cabang`). Jadi karyawan biasa
  tetap tak melihat apa pun; pelaksana yang memang menjalankan opname melihatnya.
  **Sebabnya konkret:** admin-stok sudah memakai opname di produksi pada hari
  yang sama (sesi `OPN-20260814-0001`, scan SN 17:46), dan hanya merekalah yang
  boleh mendaftarkan SN (`SERIAL_INPUT_ROLES = ["admin-stok"]`) — gate versi pagi
  mencabut alat kerja yang sedang dipakai. `raport` SENGAJA tidak diberi jalan
  tembus. Dijaga `ActivityRegistryTest` (tiga tes: karyawan biasa tertutup,
  pelaksana nyata tembus termasuk kasus role utama `karyawan` + `extra_roles`
  `admin-stok`, dan raport tak ikut bocor).
  Latar aslinya tetap berlaku: sebabnya
  `opname.hitung` memuat role `karyawan` (sengaja, lihat paragraf berikut), dan
  sejak migrasi 144 itu berarti hampir seluruh pegawai; kartunya mendarat di HP
  semua orang begitu 2.69 terpasang. **Yang menyembunyikan adalah URUTAN di
  `visibleActivityItems` — saringan akun-uji berjalan SEBELUM `gateAllows`,
  jadi ia menang atas peta kemampuan server yang tetap menjawab `true`.** Jangan
  perbaiki dengan mencabut `karyawan` dari `OPNAME_HITUNG_ROLES` di rust-shared:
  itu mematikan izin MENGHITUNG untuk akun uji juga, sehingga fiturnya tak bisa
  diuji sama sekali. **TIDAK ikut ditutup** (keputusan user yang sama): tile
  "Opname" (`opname.view`) + "Input SN" (`serial.input`) di Akses Cepat, jadi
  layar sesi opname MASIH terjangkau pemegang `opname.view` — itu memang yang
  diminta. Tak ada cerminan web yang perlu disamakan: nav web memakai
  `opname.view` (`navCatalog.ts`), yang tak pernah memuat `karyawan`.
  **Sisa celah yang diketahui:** deep-link notifikasi (`opname_sesi_dibuka`,
  `opname_manual_submitted`) masih mendarat di layar opname — `routeForNavKey`
  sengaja tak disentuh supaya akun uji tetap bisa menembusnya.

  **Multi-petugas + kartu "Opname Cabang" (2026-08-09)**: server memisahkan izin
  MENGHITUNG (`opname.hitung`, dikunci ke cabang akun) dari izin MEMILIKI SESI,
  jadi petugas cabang kini bisa ikut scan di sesi yang dibuat orang lain. Kartu
  Activity `opname_cabang` memakai kunci `opname.hitung` — BUKAN `opname.view`,
  yang menyetir menu Stock Opname di web dan cuma dipegang pengelola/pemantau.
  **Tile "Opname" di grid Akses Cepat Operasional SENGAJA tidak dihapus** walau
  konvensi repo bilang menu yang naik ke Activity dilepas dari grid: dua kunci
  itu beda audiens (manager/owner punya `opname.view` tapi TIDAK punya
  `opname.hitung`), jadi menghapusnya akan membuang satu-satunya pintu pemantau.
  `canManage`/`canHitung` kini datang dari server (`OpnameDetailDto`), bukan lagi
  disimpulkan `isOwner && draft` — kesimpulan itu menyembunyikan tombol scan dari
  petugas yang justru berhak. Keduanya **`Boolean?`, bukan `Boolean = false`**:
  `null` berarti "server belum mengenal field ini" dan app jatuh balik ke aturan
  lama. Default `false` akan mencabut tombol scan bahkan dari pemilik sesi begitu
  APK baru beredar di atas server lama — mengurangi fungsi yang sudah jalan,
  bukan sekadar konservatif. Tombol hapus per baris memakai `serialMilikSaya`
  (dari daftar unit versi server): petugas boleh mengoreksi salah scan sendiri,
  tak boleh membongkar klaim orang lain.
  **Penolakan server kini dibagi dua (2026-08-09, bersama jendela waktu migrasi
  196)**: PERMANEN (`duplikat_dalam_sesi`, `jendela_sudah_tutup`, dst) barisnya
  dibuang dari Room seperti dulu; SEMENTARA (`jendela_belum_mulai`) barisnya
  **BERTAHAN** dan dikirim ulang otomatis begitu jendela terbuka, dan hasilnya
  dilaporkan `ScanResult.Queued` bukan `Rejected`. Aturan lama (buang SETIAP
  penolakan) benar selama semua penolakan permanen; begitu server bisa menjawab
  "sesi belum dibuka", ia membuang hasil scan petugas yang cuma kepagian —
  tanpa error, tanpa tanda di layar, baru ketahuan saat hitungan akhir kurang.
  **Kode tak dikenal diperlakukan SEMENTARA** (`tolakPermanen` daftar-putih):
  APK yang tertinggal versi tak boleh membuang data karena kode barunya belum
  dikenal.
  **Input jendela waktu** ada di sheet buat-sesi (`OpnameListScreen`), MATI
  secara default — sesi tanpa batas adalah perilaku lama dan tetap sah.
  **Tombol "Nihil"** ada di baris barang yang belum dihitung (`StockSearchRow`),
  hanya untuk yang boleh menghitung DAN yang belum punya unit — menandai nihil
  barang yang sudah dihitung akan membuang hasil kerja orang lain, dan server
  pun menolaknya. Dikonfirmasi dulu karena tercatat SELISIH PENUH (barang
  dilaporkan hilang), bukan "lewati saja". `tandaiNihil` SENGAJA tidak diantre
  offline seperti scan: nihil adalah PERNYATAAN yang bisa diulang kapan saja,
  dan pernyataan yang "tersimpan" menurut layar tapi belum sampai server jauh
  lebih menyesatkan daripada penolakan yang jelas.
  Validasinya di `ui/opname/OpnameJendela.kt` (fungsi murni, diuji): cerminan
  `parse_jendela` Rust, memeriksa rentang angka juga karena regex saja
  meloloskan bulan 13 / jam 25. Perbandingan urutan mulai-selesai **leksikografis
  atas string wall-clock**, sengaja tanpa aritmetika tanggal — itu sekaligus
  menjauhkannya dari `java.time` yang haram di `app/src/main`.
- **Pusat Notifikasi** (`ui/notifications/`) + deep-link FCM; notifikasi terbaca bisa dihapus.
- Indent, mutasi histori, deadstock, perubahan harga ERP, payroll, input serial — masing-masing
  satu layar + ViewModel, semuanya ber-gate (lihat `ActivityRegistry`/`QuickAccessMenus`).
- **Input Serial Number (`ui/serials/`) = SATU tile, DUA pekerjaan.** Layar pertamanya
  pilihan mode (`SerialInputMode`), bukan langsung daftar produk: `TETAPKAN` mendaftarkan
  SN pabrik yang sudah tertempel di unit (`POST /inventory/serial-numbers`), `BUAT_BARU`
  membuat kode pengganti `GEN-…` untuk barang yang memang tak pernah punya nomor pabrik
  (`POST /inventory/serial-numbers/generate`). Keduanya dulu ditumpuk dalam satu form dan
  yang kedua tersembunyi di kaki halaman — di web keduanya memang menu terpisah
  (`AdminStokSerialInputPage.tsx` + `SerialGeneratePage.tsx`).
  **Tetap satu tile** (`serial_input`, kunci `serial.input`) walau web punya dua menu:
  `SERIAL_INPUT_ROLES` dan `SERIAL_GENERATE_ROLES` sama-sama `["admin-stok"]`, jadi tile
  kedua tak menyaring siapa pun — ia cuma menambah kunci yang bisa melenceng.
  **Daftar produknya membawa badge cakupan + filter Semua/Belum lengkap/Lengkap** dari
  `GET /inventory/serial-numbers/summary` (`SerialCoverage.kt`, fungsi murni + diuji).
  Alur kerjanya menetapkan SN ke SELURUH produk, jadi tanpa filter itu satu-satunya cara
  tahu mana yang belum tergarap adalah membuka produk satu per satu. **`TAK_DIKETAHUI`
  BUKAN sinonim `BELUM`**: saat cakupan gagal dimuat atau dipotong di batas server (8.000
  kode, field `truncated`), produk yang absen dari peta bisa saja sudah lengkap —
  memvonisnya `BELUM` memicu pendaftaran ulang. Cakupan gagal SENGAJA tidak mengisi
  `contextError`: daftar produk yang sudah terbaca tak boleh ditutup layar error, karena
  pendaftaran SN tetap sah tanpa peta kelengkapan.
  Registry inilah yang jadi bahan verifikasi lapangan — petugas cabang men-scan barcode
  tiap unit saat opname dan server menolak serial yang sama dua kali dalam satu sesi
  (`duplikat_dalam_sesi`), jadi produk yang SN-nya belum ditetapkan di sini **tak bisa
  diverifikasi sama sekali** di sana.
- **Panduan Alur + Direktori Petugas** (`ui/activity/PanduanAlurScreen.kt`) dari tombol PINTASAN.
- Settings: profile display, nomor WA bisa diubah, semua role terlihat, logout dikonfirmasi,
  cabang, cek pembaruan (`ui/settings/SettingsFormat.kt` memformat nilai tampilan)
- Input Aktivitas / raport harian (`ui/raport/`, **BETA** — kartu di Activity berlabel BETA):
  daftar aktivitas posisi karyawan dari `GET /api/jobdesk-divisions` (dicocokkan ke `divisi`
  profil lewat `matchAktivitasPosition`, port 1:1 `getPositionMatch` web — **tak boleh** jatuh ke
  posisi pertama saat tak cocok, itu bikin orang dinilai atas aktivitas divisi lain), kirim per
  baris ke `POST /api/raport-harian`. Bukti = foto kamera, **sampai 10 gambar dari galeri** (dinaikkan dari 6 pada
  2026-08-16 — katalog aktivitas produksi penuh target berjumlah sepuluh; angkanya
  hidup di `MAX_GAMBAR` dan WAJIB sama dengan `MAX_IMAGE_FILES` web),
  **satu video**, atau `mode=none` + alasan ≥10 karakter.
  **Istilah "jobdesk" diganti "aktivitas" pada 2026-08-17** — di layar DAN di nama simbol
  Kotlin. Yang SENGAJA tetap mengeja "jobdesk" dan jangan "dirapikan": nama properti DTO
  (`AktivitasPositionDto.jobdesks`, `RaportItemDto.jobdeskIndex`/`.jobdeskText`,
  `SubmitRaportItem.jobdeskIndex`/`.jobdeskText`) — repo ini nol `@SerialName`, jadi nama
  properti Kotlin ADALAH nama di kabel dan menggantinya bikin field hilang senyap (kotlinx
  mengisi default, tak melempar); rute `api/jobdesk-divisions` + kunci `app_settings`
  `jobdesk_divisions`; dan nama berkas cache `raport/jobdesk_<i>_g<n>.jpg` / `_k<n>.jpg`
  (path FileProvider, `res/xml/file_paths.xml`).
  **Unggah galeri + video ditambahkan 2026-08-14** (sebelumnya kamera saja). Aturannya
  di `ui/raport/RaportBuktiPlan.kt` (fungsi murni, diuji `RaportBuktiPlanTest`):
  * **Bentuk `evidenceUrl`: 1 gambar → string POLOS, ≥2 → JSON array; video selalu polos.**
    Ini BUKAN gaya penulisan — server menyajikan bukti lewat guard yang mencocokkan
    `WHERE bukti_url = '/uploads/raport/<berkas>'` **PERSIS**
    (`kinerja-service/src/raport/mysql.rs` `karyawan_id_for_evidence`). Baris yang menyimpan
    JSON array tak pernah cocok → **404** untuk karyawan yang membuka buktinya SENDIRI dan
    untuk `kepala-cabang` (peninjau berbatas cabang); `pic_raport`/`hrd`/`admin`/`owner`/
    `manager` aman karena lewat jalur lihat-semua. Web SELALU membungkus array, jadi baris
    multi-gambar dari web **sudah lama** terkena. Kalau app ikut membungkus SEMUA kasus,
    jalur paling ramai (satu foto kamera) ikut rusak dan gejalanya "bukti saya hilang
    setelah update aplikasi" tanpa satu pun error server. Membalik aturan ini baru aman
    setelah `karyawan_id_for_evidence` diperbaiki (JSON_CONTAINS/LIKE/tabel anak).
    **SUDAH DIPERBAIKI 2026-08-15** (repo tridjaya, commit `c3f05a77`): guard-nya kini
    `bukti_url = ? OR JSON_CONTAINS(IF(JSON_VALID(bukti_url), bukti_url, '[]'), JSON_QUOTE(?))`,
    jadi baris JSON array dikenali pemiliknya. Aturan "1 gambar → string polos" karena itu
    tak lagi WAJIB secara teknis — tapi JANGAN diubah sampai binary itu benar-benar
    ter-deploy di VPS; sampai saat itu perilaku produksi masih yang lama. Kalau kelak
    disederhanakan, `RaportBuktiPlanTest` adalah tempat aturannya dikunci.
  * **Foto galeri tetap di-watermark** tapi judulnya `TRIDJAYA · AKTIVITAS (GALERI)`,
    berbeda dari kamera — stempel jam di bar watermark itu jam PROSES, bukan jam foto
    diambil, jadi tanpa label ini foto tahun lalu terlihat seperti foto hari ini.
  * **Alur pilih → staging → tombol "Kirim bukti"**, bukan auto-kirim seperti versi
    kamera-saja: server upsert dan MENIMPA `bukti_url` seluruhnya, jadi menambah satu foto
    berarti mengirim ULANG daftar lengkapnya. Bukti lama di-seed ke staging saat baris
    disentuh (`pilihanUntuk`) — tanpa itu menambah foto = menghapus bukti lama tanpa error.
  * **Unggah gambar bisa dilanjutkan**: URL disimpan per berkas, jadi gagal di gambar ke-4
    dari 6 tak mengulang tiga yang sudah naik. `POST /raport-harian/upload` memanggil
    `ensure_window_open()` di SETIAP request, jadi jendela jam yang tertutup di tengah loop
    itu skenario nyata. `send()` tak pernah dipanggil dengan daftar parsial.
  * **Video ditolak lokal** kalau >30 MB (`MAX_EVIDENCE_BYTES` server) — tanpa transcoding.
    Ekstensi ditentukan `when` Kotlin murni (mp4/webm/mov), **bukan `MimeTypeMap`**: kelas
    itu melempar `RuntimeException("Stub!")` di unit test JVM dan tabelnya milik ROM. Nama
    berkas didahulukan atas MIME karena penyedia galeri kadang menjawab `video/mp4` untuk
    berkas `.mov`, dan server memvalidasi ekstensi × MIME × magic bytes SERENTAK — pasangan
    yang meleset ditolak 400 SETELAH 30 MB terkirim.
  * Unggahnya lewat `RaportUploadApi` + client sendiri (write 300s/read 120s, tanpa
    `HttpLoggingInterceptor`) — client bersama timeout-nya 20 detik dan level `BODY` di debug
    mem-buffer seluruh video ke heap. Video streaming lewat `UriRequestBody` (dipakai bersama
    bukti chat), **tidak pernah** `readBytes()`.
  * Picker = Photo Picker (`PickMultipleVisualMedia`/`PickVisualMedia`), **tanpa izin apa
    pun**; di bawah Android 11/13 ia turun sendiri ke SAF — jangan ditambal `READ_MEDIA_*`.
  * Bukti yang SUDAH terkirim dirender sebagai kotak "Terkirim", **bukan** `AsyncImage` ke
    server: baris multi-gambar akan menjawab 404 ke pemiliknya sendiri (lihat butir pertama).
  Jendela jam pelaporan (default 08:00–18:00) & larangan hari Minggu ditegakkan server;
  pesan detailnya ada di `errors[0]`, bukan `message` — `RaportRepository.parseError`
  sengaja mengutamakan `errors[0]` (repository lain di app ini belum).
  **Gate tampilan: AKUN UJI SAJA** (`ITEM_KHUSUS_AKUN_UJI = setOf("raport")`). Riwayatnya
  bolak-balik dan itu sengaja dicatat: ditutup 2026-07-31 → DIBUKA untuk semua 2026-08-12 →
  DITUTUP LAGI 2026-08-14, semuanya atas permintaan user. Cerminan web-nya
  `raportInputVisible` (`DashboardLayout.tsx`, `isAkunUji`) — keduanya harus sepakat.
  **`RAPORT_INPUT_ROLES` TETAP `ALL_LOGGED_IN`, jangan dikunci ulang ke `setOf("karyawan")`:**
  keluarga akun uji ber-role macam-macam (UJI Sales/PDI/Kasir/Driver), jadi mengunci role
  justru menghilangkan kartu dari akun uji sendiri dan fiturnya tak bisa diuji sama sekali.
  Yang menyembunyikan = set akun-uji, bukan gate role.
  Sisi server: `POST /raport-harian` + `/raport-harian/upload` **LOGIN-ONLY sejak 2026-08-14**
  (`KARYAWAN_ROLES` dibuang; endpoint-nya self-scoped — payload tak membawa id karyawan).
  Mismatch lama "role lain dijawab 403" sudah TIDAK ada. Gate ini murni TAMPILAN: endpoint
  sengaja dibiarkan terbuka supaya baris raport berjalan + auto-feed KPI `LAPORAN AKTIVITAS`
  tak putus.
- **Komplain / Home Service** (`ui/homeservice/`, `data/HomeServiceRepository.kt`) — alur purna-jual
  penuh di mobile: lapor → triase CS → kunjungan teknisi → penarikan unit. Lima kartu di Activity
  (`lapor_komplain`, `komplain_masuk`, `tugas_home_service`, `tarik_unit`, `tugas_tarik_unit`),
  route `home_hs_*`. **Plus DUA ubin Akses Cepat sejak 2026-08-15** (`komplain_lapor`,
  `komplain_tugas`) — dilaporkan user bahwa sales & PDI tak menemukan menunya: registri
  beranda tak punya satu pun entri home service, sementara kartu `lapor_komplain` memang
  sengaja ber-`hiddenFromActivity` (commit 2344c71, keputusan user yang TIDAK dibatalkan),
  jadi layar laporannya hidup tanpa satu pun pintu menuju ke sana. Yang mengikat:
  * **`status` server cuma menerima SATU nilai** (`"baru,ditugaskan"` → 400), sementara tiap antrian
    butuh beberapa — jadi daftar dimuat TANPA filter status lalu disaring klien (`saringStatus` +
    `HsMode`, cerminan cara web). Angka badge Activity juga dihitung dari hasil saringan itu, BUKAN
    `total` (yang berarti "semua tiket terambil", bukan "yang menunggu kamu").
  * **`mine=true` memilih KOLOM berdasarkan `jenis`**: `tarik_unit` → `tarik_driver_id`, selain itu
    `assigned_teknisi_id`. Layar driver yang lupa mengirim `jenis=tarik_unit` selalu kosong TANPA
    error — dijaga `HomeServicePlanTest`.
  * **`jadwalAt` hanya `YYYY-MM-DD` / `YYYY-MM-DD HH:MM:SS`** (ISO8601 ber-`Z` → 400), dan jamnya
    **WIB apa adanya** — server menyimpan yang dikirim tanpa konversi zona. Disaring
    `jadwalUntukServer` sebelum dialog tertutup.
  * **Foto di-serve terautentikasi** (`api/home-service/photo/{berkas}` + bearer) — `/uploads/…`
    mentah selalu gagal (`fotoHsUrl`).
  * `umurJam`/`melewatiSla` dipakai APA ADANYA dari server. Jangan hitung ulang: `created_at`
    ditulis WIB tapi dibandingkan dengan `Utc::now()`, jadi angkanya sudah punya bias ~7 jam yang
    diketahui — menghitung sendiri cuma menghasilkan angka KEDUA yang beda dari yang dilihat CS.
  * **`HS_LAPOR_ROLES` = `ALL_LOGGED_IN` sejak 2026-08-15**, dan ubin/kartu lapor ber-`capability
    = null`. Jalur pelaporan kinerja-service kini LOGIN-ONLY (repo tridjaya `57166a31`), jadi kunci
    apa pun di klien lebih SEMPIT dari servernya — `spk.pipeline` yang dipakai sebelumnya pun
    menutup `ai-engineer`. Daftar 13 role yang lama menutup 24 karyawan aktif di produksi
    (admin-penjualan 9, pic-raport 6, crm-manager 3, it-programmer 2, digital-team 2, hrd 1,
    ai-engineer 1); daftar role selalu tertinggal dari daftar pegawai. Jangan "merapikannya"
    kembali jadi daftar role.
  * **Role `cs` SENGAJA tak ditulis** di `HS_DISPATCH_ROLES` walau ada di daftar server:
    rust-shared menyatakan sendiri role literal `cs` belum ada di sistem, jadi ejaan itu tak
    akan pernah cocok (baris mati). CS sungguhan lolos lewat `homeservice.dispatch`.
  * **Teknisi lintas cabang** — server memberi scope `CabangAtauDitugaskan` sejak 2026-08-15
    (`57166a31`). Sebelumnya teknisi hanya melihat tiket CABANGNYA, padahal CS pusat menugaskan
    lintas 13 cabang: penugasan diterima, daftar kosong, nol galat. Sisi app tak perlu diubah —
    tapi kalau daftar teknisi terlihat kosong di lapangan, periksa versi BACKEND dulu.
  * **Belum ada di app** (sengaja): sparepart berbiaya saat menutup kunjungan (nominal + bukti bayar
    + setoran kasir — alur uang yang belum diuji lewat HP), dan tak ada endpoint edit/komentar tiket
    sama sekali di backend (salah input = batalkan lalu buat ulang).
  * Dropdown teknisi memakai `GET /api/users?role=pdi`, yang gate-nya (`USERS_READ_ROLES`) TIDAK
    memuat `cs` — kegagalannya ditampilkan sebagai keterangan "tugaskan lewat web", sama seperti web.
- **Nilai Aktivitas (PIC raport)** — `ui/raport/RaportReviewScreen.kt` + `RaportReviewViewModel`
  + logika murni `RaportReviewPlan.kt`, kartu ANTRIAN `raport_review` di Activity (route
  `home_raport_review`). Memuat `GET /api/raport-harian?tanggal=…&status=pending` (SELURUH
  karyawan — `antrianReview`, bukan `raportOfDay` yang menyaring ke diri sendiri) dan memutus
  lewat `PATCH /api/raport-harian/{id}/review` `{status, score, comment}`. Yang perlu diketahui:
  * Gate `raport.review` / `RAPORT_REVIEW_ROLES` — **`owner` tidak termasuk** (ia boleh membaca
    lewat `RAPORT_VIEW_ALL_ROLES`, tapi ditolak `review_raport`).
  * Skor ditentukan SERVER: `rejected` → 0, `approved` → `score ?: 100` di-clamp 0..100.
    `skorReview` mencerminkannya supaya angka di layar sama dengan yang tersimpan; tolak wajib
    berkomentar (`bolehSimpanReview`, dijaga di ViewModel juga, bukan cuma di tombol).
  * Bukti TIDAK bisa dirender dari `/uploads/raport/…` mentah (upload privat, S-02 web) —
    `evidenceImageUrl` memetakannya ke `api/raport-harian/evidence/{berkas}` + header
    `Authorization` (pola `AuthedImage`). Sengaja BUKAN alias gateway `api/raport-files/*`
    yang dipakai web: alias itu menolak role `hrd`, padahal `hrd` termasuk penilai.
    `evidenceUrl` bisa berupa string JSON array (baris lama multi-bukti) → `parseEvidenceUrls`.
  * `mode=video` tak diputar di app (alasan sama dengan `ChatReviewScreen`) — barisnya diberi
    penanda dan pemeriksaannya diserahkan ke web.
  * Server TIDAK men-scope penilai↔karyawan sama sekali (`service.review()` tak menerima
    identity): siapa pun yang lolos role boleh menilai baris cabang mana pun.
- **Konsumen Gebyar (kupon doorprize)** — `ui/kupongebyar/` (`KuponGebyarScreen`
  + `KuponGebyarViewModel` + logika murni `KuponGebyarPlan.kt`), kartu ANTRIAN
  `kupon_gebyar` di Activity (route `home_kupon_gebyar`). Konsumen dengan belanja
  ≥ Rp1,5 juta (1 Jan–30 Agu 2026) berhak SATU kupon; karyawan mengirimi undangan
  lalu memotret buktinya. Yang perlu diketahui:
  * **Gerbangnya DUA lapis.** Kunci `kupon_gebyar.lihat` cuma tahu ROLE; yang
    menentukan adalah CABANG (semua kecuali Manado = D-06 **dan** D-07), yang
    hidup di `auth_users.cabang_id` dan tak bisa dinyatakan di katalog kemampuan
    sama sekali. Lapis keduanya `kuponGebyarCardVisible(bolehLihat)` — vonis dari
    `GET /kupon-gebyar/meta`. Pola identik `bolehIsi` di modul Event.
  * **Vonis `null` (offline) TETAP MENAMPILKAN kartunya**, bertanda "gagal muat".
    Arahnya SENGAJA kebalikan `gateAllows` yang fail-closed: di sana server
    MENJAWAB dan kuncinya absen (= sengaja dicabut), di sini server tak menjawab.
    Menyembunyikannya = keluhan "menunya hilang" tiap sinyal jelek.
  * **Rute gateway `/api/kupon-gebyar` BARU** — tak menumpang wildcard yang sudah
    ada seperti pemasangan AC. APK ini MENGIKAT urutan deploy: migrasi 277+278 →
    kinerja-service DAN **gateway** → baru APK. `check-mobile-contract.sh`
    menangkapnya kalau dijalankan sebelum rilis.
  * **Tanpa cache Room, disengaja.** Isinya nama + nomor + nilai belanja konsumen;
    dan baris yang sudah dikerjakan rekan secabang HILANG dari daftar, jadi
    salinan basi = dua orang mengirimi konsumen yang sama.
  * **409 = sudah dikerjakan rekan secabang**, nama pemegangnya ada di `message`
    (bukan `errors`, yang kosong untuk konflik) — `parseError` membaca kedua jalur.
    Bukti yang unggahannya sudah berhasil tapi pencatatannya gagal disimpan di
    `buktiTertunda` supaya tombolnya jadi "Simpan ulang", bukan memotret lagi
    (pola `AcInstallViewModel`). Pada 409 justru DIBUANG — tak ada yang bisa
    diselamatkan, pekerjaannya memang sudah selesai.
  * **`perluNomorPengganti` = nomor yang tercatat nomor KARYAWAN**, bukan nomor
    konsumennya (27 baris di produksi). Dirender sebagai peringatan merah.
  * Foto bukti TIDAK ditampilkan di app — `KuponGebyarModels.kt` sengaja
    tak menurunkan field itu ke `KuponGebyarBarisDto`. **BUKAN lagi karena
    server tak mengirimnya**: `BarisKuponPublic` mendapat `buktiUrl` 2026-08-26
    untuk kebutuhan web (rincian per cabang di Papan Gebyar). Kalau app kelak
    perlu menampilkannya, tinggal tambah field + `AuthedImage`/Coil dengan
    header `Authorization`, pola yang sama dengan foto delivery. Papan
    `/monitoring` juga tetap web-only.
- All three tabs' data is Room-cached with a uniform 5-hour TTL and survives tab switches

## Official Android/Material guideline compliance

Audited against developer.android.com guidance (architecture was already largely compliant from
earlier work — Hilt DI, Repository pattern, StateFlow-exposed ViewModels, Room caching, Paging3,
edge-to-edge, R8/shrinking, synchronized token refresh). This pass covered the remaining gaps:

- **Predictive back gesture** (Android 13+ guideline): `android:enableOnBackInvokedCallback="true"`
  set in `AndroidManifest.xml`. Navigation Compose 2.8.4 wires into the system back dispatcher
  automatically once this flag is on — no extra `BackHandler` code needed for the standard
  push/pop nav flows already in use.
- **Accessibility touch targets**: `TridjayaBottomNav.kt`'s `PillNavItem` now has an explicit
  `.heightIn(min = 48.dp)` on the clickable region, guaranteeing Material's minimum touch target
  regardless of font-scale settings (previously relied on content-wrap height, which happened to
  clear 48dp at default scale but wasn't guaranteed).
- **Accessibility content descriptions**: audited every `contentDescription = null` usage — all
  current instances are on decorative icons sitting directly next to a text label (e.g. the Star
  icon beside "Ranking Cabang", the Call icon beside "Chat WhatsApp" button text). Per Material's
  own accessibility guidance, `null` is *correct* there — a real description would cause
  screen readers to double-announce the same information. No bugs found.

**Deliberately not applied — with reasoning, don't "fix" these without checking with the user first:**

- **String resource extraction**: the whole UI hardcodes Indonesian strings directly in `Text(...)`
  calls rather than `stringResource(R.string.xxx)` (`strings.xml` only has `app_name`). This is a
  real localization/testability gap per official guidance, but migrating 30+ call sites across
  every screen is a large mechanical refactor with real regression risk (easy to typo a key or
  break a format-string argument) — out of scope for a guideline *pass*, worth a dedicated task.
- **Gradle version catalog** (`libs.versions.toml`): dependency versions are still inline in
  `app/build.gradle.kts` rather than centralized in a version catalog, which is the current
  official Gradle/AGP recommendation. Low runtime impact, pure tooling/maintainability — worth
  doing but isn't urgent, and touching every dependency line in one pass is unnecessary risk for
  a build that's currently working.

## Lint (`:app:lintDebug`) — baseline 0 error, jangan diturunkan

`lintDebug` menggagalkan build kalau ada error (`abortOnError` default, tanpa `lint-baseline.xml`),
dan sejak 2026-08-14 baselinenya **0 error, 30 warning**. Dua keputusan di dalamnya jangan
"dirapikan" tanpa bertanya:

- **`QUERY_ALL_PACKAGES` DITEKAN dengan `tools:ignore="QueryAllPackagesPermission"`, BUKAN dicabut.**
  Lint menyarankan `<queries>`, tapi `<queries>` hanya bisa menyebut paket yang **sudah diketahui
  namanya**, sedangkan `SecurityGuard.detect()` justru mengenumerasi seluruh paket terpasang untuk
  menemukan yang menyatakan `ACCESS_MOCK_LOCATION` — termasuk app fake-GPS yang belum pernah kita
  lihat. Mencabutnya menyisakan daftar spoofer hardcoded saja = melemahkan gerbang integritas titik
  absen demi memuaskan pemeriksa kebijakan Play Store yang tidak berlaku untuk side-load enterprise.
- **`ProduceStateDoesNotAssignValue` adalah FALSE POSITIVE di compose-runtime 1.7.5** (BOM
  2024.10.01) dan itu sebabnya `SecurityGate` di `MainActivity.kt` memakai
  `remember { mutableStateOf(...) }` + `LaunchedEffect(key)`, bukan `produceState`. Pemicunya
  **bukan bentuk penugasan** melainkan **adanya argumen key**: dibuktikan dengan menjalankan lint
  atas tujuh varian sekaligus — `produceState<T?>(null) { value = f() }` (tanpa key) LOLOS,
  sedangkan keenam varian ber-key gagal semua, termasuk `this.value = h` eksplisit, versi dua
  langkah `val h = ...; value = h`, key positional, key bernama, dan versi tanpa delegasi `by`.
  Jadi jangan buang waktu mengutak-atik bentuk `value = ...`; itu jalan buntu. Kalau Compose naik
  versi, uji ulang `produceState` sebelum menganggap catatan ini masih berlaku.

## Tema & warna

Bagian ini pernah terpecah dua catatan yang saling bertabrakan (satu bilang default `Biru
Tridjaya` `#1E63E9` lewat `blueDefaultScheme()`, satu bilang tema app = ungu Rhythm `#6750A4`).
**Keduanya sudah tidak cocok dengan kode** — `blueDefaultScheme()` tak ada lagi, dan ungu Rhythm
bukan warna app. Yang berlaku sekarang, dari `ui/theme/ThemeSchemes.kt`:

- **Default = `AppColorScheme.DEFAULT`, label "Tridjaya Web", primary `#465FFF`**, dibangun
  `tridjayaWebScheme(dark)` — mengikuti palet web Tridjaya, bukan M3 baseline. Ia mendefinisikan
  **seluruh** role sendiri termasuk netral (light: background/surface putih `#FFFFFF`, dark:
  `#101828` dengan tangga `surfaceContainer*` sendiri), jadi jangan berasumsi ia memakai netral
  bersama seperti preset lain.
- **8 preset lain** — Lavender/Rose/Warm/Amber/Forest/Mint/Cool/Ocean — cuma memasok triad
  primary/secondary/tertiary lewat helper `lightTriad()`/`darkTriad()`, dan **meminjam netral +
  error dari `Color.kt`**. Di situlah warisan Rhythm sesungguhnya masih hidup.
- **`Color.kt` = sisa port Rhythm** (github.com/cromaguy/Rhythm), bersama `Type.kt` dan
  `Shape.kt` (8/12/16/24/32 dp) — di-port persis atas permintaan user, menggantikan branding
  violet lama (`#5C4AD5` + amber tertiary); jangan pulihkan yang lama. Tapi seed ungu `#6750A4`
  kini **praktis mati**: `PrimaryLight` tidak dirujuk di mana pun (cek `grep -rn "PrimaryLight\b"`
  — hanya deklarasinya sendiri). Yang benar-benar dipakai dari file itu adalah netral, error, dan
  `InversePrimaryLight`. Jadi: Rhythm = substrat netral + tipografi + bentuk, **bukan** warna
  utama app.
- Hanya *visual token* Rhythm yang di-port, BUKAN mesin temanya (dynamic color dari album art,
  font "Geom" unduhan, preset festive) — itu mesin app musik, di luar lingkup.
- **Material You** (`dynamicLight/DarkColorScheme`) tersedia sebagai pilihan di Android 12+;
  mode gelap ikut sistem/terang/gelap. Pilihan disimpan `ThemePreferences` (`data/`, Hilt
  singleton + StateFlow) yang di-observe `MainActivity.setContent`, jadi seluruh app berganti
  warna live; diatur dari Settings → Tema (`ThemeSettingsScreen`).
- **Flyer sengaja kebal tema** (`FlyerColors` hardcoded) supaya gambar yang dibagikan identik di
  HP mana pun — lihat bagian flyer.
- Ikon se-app pakai `Icons.Rounded.*`; interaksi utama memicu haptic `CONTEXT_CLICK` ringan lewat
  `rememberHapticClick`; `ExpressiveShapes` menambah token squircle/asimetris.

Catatan historis: "dynamic color deliberately not applied" sudah **superseded** — user memang
meminta theming penuh.

## Known gaps / natural next steps

- No product photos (see flyer section above) — needs a backend image URL field first
- **Automated tests exist but are JVM-unit-only.** `app/src/test/` has a real suite (pure-logic
  tests for delivery flow models, branch regions, indent decisions, menu access gates, the
  Activity registry/plan/nav-key mapping — run via `:app:testDebugUnitTest`); `app/src/androidTest/`
  still has no instrumented Compose UI tests. That remains the next investment.
- No CI/CD pipeline — builds and releases are manual
- Debug builds have no signing story beyond the Android SDK default debug key; only one release
  keystore exists and it's local-only (not backed up anywhere but the user's own storage)
- `arr.csv` is bundled as a pricing asset in the TE KOTLINT reference but was confirmed unused in
  its actual calculation logic — not ported here; if a 5th product-category bracket table is
  ever needed, check the reference project's newest logic first, don't assume `arr.csv`'s old
  intent is still correct
- **Offline create (add lead) is supported** via an optimistic local-first write + sync queue:
  `CrmRepository.createLead()` inserts the lead into Room immediately (temp **negative** `id`,
  `LeadEntity.pendingSync = true`) so it shows in the list at once marked **"Antre"** (amber cloud
  badge in `LeadCard`), then `appScope.launch { syncPendingLeads() }` pushes it. `syncPendingLeads()`
  (Mutex-guarded queue) POSTs each pending lead oldest-first and, on success, **replaces** the temp
  row with the authoritative server row; failures stay pending and are retried on create / manual
  refresh / list-VM init (`GetLeadsUseCase.syncPending()`). `syncLeads()` flushes pending first and
  re-appends any still-pending rows after `replaceAll` so a refresh never drops an unsynced lead.
  `@AppScope CoroutineScope` (in `AppModule`) keeps the push alive past the Add-Lead screen. DB
  was bumped to v5 for the `pendingSync` column at the time (destructive migration — cache
  re-syncs); skema sekarang **v14**, lihat catatan migrasi Room di bawah.
  **Still online-only:** move-stage / mark won/lost (they act on a server `id`, so a pending lead
  can't be mutated until it syncs).
- No string resources (see guideline section above) and no Gradle version catalog — both real
  gaps, both deliberately deferred rather than rushed

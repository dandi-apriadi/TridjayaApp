import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.baselineprofile")
}

// Release signing credentials live outside version control in keystore.properties
// (see .gitignore) — never hardcode a keystore password in this build file.
val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.krisoft.tridjayaelektronik"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.krisoft.tridjayaelektronik"
        minSdk = 24
        targetSdk = 35
        // Runner instrumentasi — dibutuhkan `connectedAndroidTest`, yang
        // satu-satunya cara menguji jalur yang cuma pecah di runtime Android
        // lama (mis. `java.time` di API < 26).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 129
        versionName = "3.18"

        // Gateway Rust tridjaya, deployed at tridjaya.com (HTTPS, no emulator/LAN
        // workaround needed since it's a public domain). Migrated 2026-07-13 from
        // tridjayaelektronik.tech, which now only serves an HTML redirect page.
        buildConfigField("String", "API_BASE_URL", "\"https://tridjaya.com/\"")

        // Penanda "ini bukan build produksi", dibaca UI untuk menampilkan badge BETA
        // di header. DUA jalur menyalakannya, karena "versi uji coba" di repo ini
        // ada dua bentuk yang berbeda:
        //
        //   1. build DEBUG  — otomatis (blok `debug` di bawah). Ini yang dipasang
        //      lewat kabel saat mengembangkan.
        //   2. `-Pbeta`     — RELEASE yang sengaja ditandai uji coba. Perlu karena
        //      APK yang dibagikan ke karyawan lewat APK_UPLOAD_DIR adalah build
        //      release bertanda tangan asli (debug tak bisa dipakai: kuncinya beda,
        //      Android menolak memasangnya di atas app terpasang) — jadi tanpa flag
        //      ini rilis percobaan tak punya cara membedakan diri dari rilis biasa.
        //
        // Default `false`: rilis normal tak boleh diam-diam mengaku beta.
        buildConfigField("boolean", "IS_BETA", if (project.hasProperty("beta")) "true" else "false")
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")

                // v1 (JAR signing) WAJIB DINYALAKAN EKSPLISIT — insiden 2026-08-28.
                //
                // AGP mematikan v1 sendiri begitu `minSdk >= 24`, karena untuk
                // MEMASANG APK v2 memang sudah cukup di Android 7+. Alasan itu
                // benar dan tetap benar; yang salah adalah menganggapnya cukup
                // untuk semua hal.
                //
                // `UpdateManager.ditandatanganiKitaSendiri()` (audit keamanan
                // 2026-08, temuan 3.5) memeriksa sertifikat APK yang BARU
                // DIUNDUH lewat `PackageManager.getPackageArchiveInfo()`. Untuk
                // berkas APK LEPAS — bukan paket terpasang — panggilan itu di
                // banyak versi/ROM Android hanya membaca **JAR signature (v1)**.
                // Tanpa v1, `signingInfo`/`signatures` kosong, pemeriksanya
                // fail-closed, dan SETIAP update ditolak dengan kalimat
                // "tanda tangannya bukan milik aplikasi ini" — tuduhan yang
                // salah terhadap APK kita sendiri, plus berkasnya dihapus.
                //
                // Rusak DIAM-DIAM sejak pemeriksa itu mendarat (`06009bef`,
                // 24 Agu 23:38) sampai 28 Agu: `mandatory=false` membuat orang
                // sekadar mengabaikan update yang gagal, jadi tak ada yang
                // melapor. Rilis 3.06 menyalakan `mandatory=true` dan seluruh
                // fleet menabraknya serentak.
                //
                // JANGAN matikan lagi dengan alasan "minSdk 24 tak butuh v1".
                // Ongkosnya cuma ukuran APK; yang dibeli adalah satu-satunya
                // jalur update yang dipunyai app ini.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Build debug SELALU beta — ia tak pernah sampai ke tangan karyawan
            // sebagai app resmi, jadi tak ada kasus di mana penandanya salah.
            buildConfigField("boolean", "IS_BETA", "true")

            // Debug memakai API_BASE_URL default (https://tridjaya.com/) sama seperti
            // release. Untuk uji ke gateway lokal:
            //
            //   ./scripts/adb-reverse-watch.sh      (biarkan jalan di terminal lain)
            //   ./gradlew installDebug -PlocalApi
            //
            // Pakai skrip penjaga itu, JANGAN `adb reverse` sekali jalan: tunnelnya
            // tidak bertahan melewati kabel tersenggol, USB di-suspend saat HP sleep,
            // atau daemon adb restart — dan hilangnya cuma terlihat sebagai "app tidak
            // bisa terhubung", tanpa petunjuk bahwa penyebabnya ada di laptop.
            //
            // localhost sudah diizinkan cleartext di network_security_config.xml.
            //
            // Flag opt-in, BUKAN edit tangan di defaultConfig: cara lama menyuruh
            // mengubah baris yang dipakai release lalu mengandalkan orang untuk
            // ingat mengembalikannya sebelum commit. Rilis yang menembak
            // localhost tidak menimbulkan error apa pun saat di-build — cuma app
            // di lapangan yang tak bisa memuat apa-apa.
            //
            // `applicationIdSuffix` ikut DI DALAM flag supaya varian uji terpasang
            // BERDAMPINGAN dengan app produksi di HP yang sama. Tanpa itu, debug
            // (keystore SDK bawaan) bentrok tanda tangan dengan rilis terpasang →
            // INSTALL_FAILED_UPDATE_INCOMPATIBLE, dan satu-satunya jalan keluar
            // adalah uninstall app produksi: sesi login karyawan ikut hilang.
            // Debug build biasa (tanpa flag) TIDAK berubah sama sekali.
            if (project.hasProperty("localApi")) {
                // -PapiBaseUrl override: HP nyata di luar jangkauan USB/adb reverse
                // (mis. lewat tunnel Cloudflare) butuh URL publik, bukan localhost.
                val url = project.findProperty("apiBaseUrl")?.toString() ?: "http://localhost:4100/"
                buildConfigField("String", "API_BASE_URL", "\"$url\"")
                applicationIdSuffix = ".localapi"
                versionNameSuffix = "-localapi"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        // WAJIB, bukan kenyamanan. minSdk 24 berarti HP Android 7 masih dilayani,
        // dan di sana `java.time.*` TIDAK ADA di runtime — pemakaiannya melempar
        // `NoClassDefFoundError` saat kelasnya pertama disentuh. Yang membuat ini
        // berbahaya: (1) `NoClassDefFoundError` adalah `Error`, jadi `catch (e:
        // Exception)` di layar TIDAK menangkapnya dan app langsung tertutup;
        // (2) unit test JVM tak pernah bisa menangkapnya karena jalan di JDK 17
        // yang punya `java.time` lengkap — gerbangnya hijau, HP-nya mati.
        // Korban nyata: `org.dhatim:fastexcel` memanggil `java.time.Instant.now()`
        // tanpa syarat di `Workbook.finish()`, jadi ekspor XLSX Inventaris
        // menutup app di setiap HP Android 7/7.1.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Menyediakan java.time/java.util.stream di API < 26 — lihat alasan di compileOptions.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation("androidx.core:core-ktx:1.15.0")
    // EXIF orientation saat kompres foto bukti indent (IndentCreateViewModel)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Secure token storage: encrypted DataStore (Android Keystore AES-GCM). security-crypto is
    // kept only for the one-time migration reading the legacy EncryptedSharedPreferences store.
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Dependency injection
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Local cache
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Paging
    implementation("androidx.paging:paging-runtime:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")

    // Penjadwal pengingat prospek mandek (push/ProspekReminder.kt) — periodik harian, selamat
    // dari reboot & force-stop tanpa receiver BOOT_COMPLETED atau izin exact-alarm Android 12+
    // yang dituntut AlarmManager. SENGAJA tanpa androidx.hilt:hilt-work: worker ini cuma butuh
    // dua singleton, jadi EntryPointAccessors sudah cukup dan tak menambah KSP processor
    // maupun memaksa TridjayaApplication mengimplementasi Configuration.Provider.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // XLSX export (Inventory "Export ke Excel") — lightweight pure-Java writer, no POI/reflection
    // baggage, small enough for Android; supports styled cells + embedded row images.
    implementation("org.dhatim:fastexcel:0.20.2")

    // Product photo thumbnails (Inventory list + detail flyer) — Coil 2.x (not 3.x: avoids the
    // multi-artifact network-engine split for a project this size). Disk+memory caching built in,
    // so scrolling the Inventory list doesn't refetch images on every recomposition.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Firebase Cloud Messaging (push approval izin/absen). Active only when a real
    // google-services.json is present (the plugin below is applied conditionally); otherwise the
    // dependency is inert (no default FirebaseApp). Remote Config dropped — update-check /
    // force-update is now driven by our own backend (`UpdateManager` → `/api/users/app-apk/meta`),
    // not Firebase.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Scan barcode serial number (PDI + Input SPK) — Google code scanner:
    // TANPA izin kamera (UI scanner disediakan Play Services, model di-download
    // on-demand), jauh lebih ringan daripada bundling CameraX+ML Kit.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Transcode video bukti Input Aktivitas >30MB on-device (H.264/MediaCodec hardware) sebelum
    // upload (util/VideoTranscoder.kt) — pustaka androidx RESMI untuk transcode via MediaCodec,
    // satu-satunya jalan realistis selain menulis ulang MediaCodec/MediaMuxer manual sendiri.
    // Versi diverifikasi LANGSUNG dari maven-metadata.xml dl.google.com/android/maven2
    // (latest/release=1.11.0 utk transformer, common, DAN effect), bukan diketik dari ingatan.
    implementation("androidx.media3:media3-transformer:1.9.4")
    // Tipe bersama Transformer (EditedMediaItem, MediaItem via androidx.media3.common, dll) —
    // versi HARUS sama persis dengan media3-transformer, media3 tak menjamin kompat lintas versi.
    implementation("androidx.media3:media3-common:1.9.4")
    // Presentation.createForWidthAndHeight (scale target lebar/tinggi) — Transformer sendiri
    // tak punya parameter dimensi, efeknya hidup di modul terpisah ini.
    implementation("androidx.media3:media3-effect:1.9.4")

    // Installs the bundled baseline profile on first run (removes cold-start/first-scroll JIT jank).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Consumes the profile produced by the :baselineprofile module and bundles it into the APK.
    baselineProfile(project(":baselineprofile"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Apply the Google Services plugin only when a real Firebase config file has been added (mirrors how
// release signing is gated on keystore.properties) — the app still builds & runs without it, and the
// update system simply stays inert until Firebase is configured.
// `-PlocalApi` mengubah applicationId jadi ...localapi, dan google-services.json
// cuma memuat klien untuk package aslinya — plugin ini MENGGAGALKAN build kalau
// tak menemukan klien yang cocok ("No matching client found for package name").
// Dilewati saat flag aktif; FCM lalu inert (DeviceRepository.fetchFcmToken sudah
// menangani Firebase tak ter-inisialisasi), yang memang benar untuk varian uji:
// ia tak boleh ikut menerima push yang ditujukan ke app produksi.
val berkasGoogleServices = rootProject.file("app/google-services.json")
val firebaseSengajaDilewati = project.hasProperty("localApi")
if (berkasGoogleServices.exists() && !firebaseSengajaDilewati) {
    apply(plugin = "com.google.gms.google-services")
}

// ─────────────────────────────────────────────────────────────────────────────
// GERBANG KERAS — build RILIS MENOLAK jalan tanpa google-services.json
// ─────────────────────────────────────────────────────────────────────────────
//
// "Dilewati diam-diam" di atas BENAR untuk build debug dan SALAH TOTAL untuk
// rilis. Tanpa berkas itu plugin tak pernah diterapkan, jadi APK terbit tanpa
// satu pun string konfigurasi Firebase — sementara pustaka `firebase-messaging`
// tetap ikut ter-bundle. Hasilnya app yang KELIHATAN utuh:
// `DeviceRepository.fetchFcmToken` menangkap kegagalan init lalu mengembalikan
// null, `registerCurrentToken` menulis satu `Log.w` ke logcat HP, dan
// `POST /absensi/register-device` TIDAK PERNAH LAHIR. Server tak menyimpan jejak
// percobaan gagal karena memang tak ada percobaan — nol error di kedua sisi.
//
// INSIDEN YANG DITUTUPNYA (forensik 2026-09-01, diukur di APK produksi + DB):
// laptop di-instal ulang 2026-08-08. `keystore.properties` dan
// `release-keystore.jks` dipulihkan manual dari arsip (keduanya bertanggal
// 2026-08-08 22:02:59); `google-services.json` TIDAK — ia gitignored persis
// seperti keystore, tapi tak masuk daftar pemulihan. Sejak rilis berikutnya
// (vc72/73, 9–10 Agustus) SETIAP APK release terbit tanpa Firebase, ~52 versi
// berturut-turut. Yang terukur 26 hari kemudian:
//   • APK vc123/3.12 yang BEREDAR: `google_app_id`, `gcm_defaultSenderId`,
//     `project_id`, `google_api_key` = NOL semuanya dari 93 string resource
//     (APK vc10 yang sehat masih memuat project_id `tridjayaelektronik-35068`).
//   • Token FCM terakhir yang pernah terdaftar: 2026-08-06 21:46:37. Nol baris
//     baru sesudahnya.
//   • 51 dari 122 pengguna APK aktif (41,8%) tanpa device token; log
//     kinerja-service mencatat 3.395 "push DIBUANG: akun aktif belum punya
//     device token" dalam 7 hari, menyentuh 80 nama unik.
// Empat dari sepuluh karyawan berhenti menerima notifikasi approval/pengiriman
// selama 26 hari dan tak satu pun sinyal menyebutkannya. Build sukses, app
// jalan, notifikasi diam — itulah kenapa penjaganya harus di sini, bukan di
// runbook yang bergantung pada ingatan orang setelah instal ulang berikutnya.
//
// Debug SENGAJA tidak ikut diwajibkan: mesin pengembangan harus tetap bisa
// membangun tanpa Firebase.
//
// **`-PlocalApi` TIDAK mengecualikan gerbang ini** (koreksi review PR — draf
// pertama menyamakannya dengan pengecualian debug di atas, dan itu salah).
// `-PlocalApi` hanya mengubah `applicationIdSuffix` di build type `debug`
// (lihat blok `debug {}` di bawah); ia TIDAK PUNYA efek apa pun pada build
// type `release`. Jadi `./gradlew assembleRelease -PlocalApi` sebelumnya
// menghasilkan APK PRODUKSI asli (applicationId `com.krisoft.tridjayaelektronik`,
// release-signed) tanpa Firebase — mereproduksi PERSIS insiden yang gerbang
// ini dibuat untuk mencegah, lewat kombinasi flag yang tak ada yang menahannya.
//
// Daftar tugasnya EKSAK, bukan pola "berakhiran Release": `:app:generateBaselineProfile`
// membangun varian `nonMinifiedRelease` yang tak pernah dibagikan ke siapa pun,
// dan pola longgar akan ikut menghentikannya.
//
// **Diperiksa lewat `gradle.taskGraph.whenReady`, BUKAN daftar nama task CLI
// literal (`startParameter.taskNames`)** — koreksi review PR kedua. Bentuk itu
// cuma berisi nama task yang DIKETIK di CLI secara literal — `./gradlew build`
// atau `./gradlew assemble` menjalankan
// `assembleRelease` secara TRANSITIF (task itu mendepend padanya) tanpa string
// "assembleRelease" pernah muncul di `taskNames`, jadi gerbang lama tak pernah
// menyala untuk keduanya. Task graph yang SUDAH DIRESOLVE (`allTasks`) memuat
// task transitif ini. `whenReady` tetap menyala SEBELUM eksekusi task mana pun
// (bukan `doFirst`), jadi gagalnya tetap dalam hitungan detik, bukan setelah R8
// selesai ~44 menit.
val tugasWajibFirebase = setOf("assembleRelease", "bundleRelease", "installRelease")
gradle.taskGraph.whenReady {
    val mintaBuildRilis = allTasks.any { it.name in tugasWajibFirebase }
    if (mintaBuildRilis && !berkasGoogleServices.exists()) {
        throw GradleException(
            "google-services.json TIDAK ADA — build RILIS dihentikan.\n" +
                "\n" +
                "Taruh berkasnya PERSIS di: ${berkasGoogleServices.path}\n" +
                "\n" +
                "Ambilnya: Firebase console → proyek `tridjayaelektronik-35068` → Project settings → " +
                "Your apps → Android app `com.krisoft.tridjayaelektronik` → Download " +
                "google-services.json. Hanya PEMILIK akun Firebase yang bisa mengunduhnya, jadi ini " +
                "tak bisa diselesaikan sendiri oleh sesi mana pun tanpa dia.\n" +
                "\n" +
                "Berkas ini ter-gitignore (mobile/.gitignore `app/google-services.json`), jadi ia TIDAK " +
                "ikut clone/checkout dan TIDAK ikut dipulihkan bersama keystore setelah instal ulang. " +
                "Tanpa dia APK terbit tanpa konfigurasi Firebase dan pendaftaran token FCM mati total " +
                "di semua HP: terukur 2026-09-01 → 41,8% pengguna APK aktif tanpa device token, nol " +
                "pendaftaran baru selama 26 hari, ~52 versi rusak berturut-turut sejak vc72 (9 Agt 2026).\n" +
                "\n" +
                "Build variant DEBUG (dengan atau tanpa `-PlocalApi`) tidak menuntut berkas ini — " +
                "pakai itu untuk pengembangan lokal. Build RILIS TIDAK PUNYA jalan keluar lewat " +
                "flag apa pun, termasuk `-PlocalApi`.",
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ARSIP PETA DEOBFUSKASI R8 — menempel di `assembleRelease`, bukan langkah manual
// ─────────────────────────────────────────────────────────────────────────────
//
// `app/build/outputs/mapping/release/mapping.txt` adalah SATU-SATUNYA cara
// membaca stack trace crash dari HP pengguna setelah R8 mengaburkan namanya.
// Ia ditimpa setiap `assembleRelease` berikutnya, dan direktori `build/` memang
// pantas dihapus kapan saja — jadi tanpa arsip, peta itu hidup persis sampai
// build berikutnya.
//
// KENAPA INI TUGAS GRADLE, BUKAN LANGKAH DI RUNBOOK: langkah manual sudah
// DICOBA dan GAGAL. Audit 2026-08-16 menemukan `cadangan-lokal/` cuma memuat
// `mapping-2.71-vc82.txt` — artinya peta untuk 2.76, 2.77, 2.78, 2.79, dan 2.80
// hilang permanen, ditimpa satu per satu tanpa seorang pun menyadarinya. Nol
// error, nol gejala; ketahuannya cuma karena ada yang kebetulan mendaftar isi
// direktori. Aturan yang bergantung pada ingatan manusia lima kali berturut-turut
// gagal, jadi ia dipindah ke tempat yang tak bisa lupa: `finalizedBy`.
//
// Nilainya diambil saat KONFIGURASI (bukan di dalam `doLast`) supaya tugas ini
// tidak mematikan configuration cache Gradle.
val versiKodeRilis = android.defaultConfig.versionCode
val versiNamaRilis = android.defaultConfig.versionName
val berkasMappingRilis = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
// `cadangan-lokal/` hidup DI LUAR repo (sejajar dengannya) — sengaja, sama
// seperti keystore: arsip yang tinggal di dalam pohon kerja git adalah arsip
// yang lenyap pada `git clean -xdf` berikutnya.
//
// **Letaknya DUA kandidat, bukan satu path mati.** `../cadangan-lokal` benar
// untuk repo mobile berdiri sendiri (`Tridjaya/TridjayaApp` + `Tridjaya/
// cadangan-lokal`), tapi sejak subtree 2026-08-21 `rootProject` bisa juga
// `<monorepo>/mobile`, dan dari sana arsipnya ada di `../../cadangan-lokal`.
// Terbukti menggigit saat rilis 3.03 (2026-08-28): build SUKSES, APK terunggah,
// dan peta R8-nya TIDAK terarsip — satu baris `warn` di antara 58 task, yang
// baru ketahuan karena kebetulan dibaca. Peta yang hilang tak menimbulkan
// keluhan sampai ada crash report yang harus dibaca berbulan-bulan kemudian,
// dan saat itu ia tak bisa dibuat ulang.
// `ARSIP_RILIS_DIR` (env) menang atas keduanya, untuk mesin dengan layout lain.
val kandidatArsipRilis = listOfNotNull(
    System.getenv("ARSIP_RILIS_DIR")?.let { File(it) },
    rootProject.file("../cadangan-lokal"),    // repo mobile berdiri sendiri
    rootProject.file("../../cadangan-lokal"), // mobile/ di dalam monorepo
)
val direktoriArsipRilis = kandidatArsipRilis.firstOrNull { it.isDirectory }
    ?: kandidatArsipRilis.last()

val arsipkanMappingRilis = tasks.register("arsipkanMappingRilis") {
    description = "Salin mapping.txt R8 ke ../cadangan-lokal/ dengan nama ber-versi."
    group = "release"
    doLast {
        val sumber = berkasMappingRilis.get().asFile
        if (!sumber.exists()) {
            // Bukan kegagalan: build tanpa minify (atau varian lain) memang tak
            // menghasilkan peta. Tetap dicetak supaya "tak ada arsip" tak pernah
            // jadi hal yang cuma bisa ditemukan lewat `ls`.
            logger.lifecycle("arsip mapping DILEWATI: ${sumber.path} tak ada (build tanpa R8?)")
            return@doLast
        }
        if (!direktoriArsipRilis.isDirectory) {
            // Pesannya menyebut SEMUA kandidat yang dicoba: "direktori X tak
            // ada" pada layout yang salah membuat orang membuat direktori di
            // tempat yang juga salah.
            logger.warn(
                "arsip mapping DILEWATI: tak ada direktori arsip — peta untuk " +
                    "$versiNamaRilis vc$versiKodeRilis TIDAK terarsip. Yang dicoba: " +
                    kandidatArsipRilis.joinToString(", ") { it.path } +
                    ". Set ARSIP_RILIS_DIR=/path/ke/cadangan-lokal lalu jalankan " +
                    "ulang `gradlew arsipkanMappingRilis` — mapping.txt hasil build " +
                    "tadi masih ada, jadi arsipnya tak perlu build ulang.",
            )
            return@doLast
        }
        val tujuan = File(direktoriArsipRilis, "mapping-$versiNamaRilis-vc$versiKodeRilis.txt")
        if (tujuan.exists()) {
            // Ukuran sama = build ulang versi yang sama; itu wajar (malam ini
            // 2.81 dibangun dua kali) dan tak perlu diributkan.
            if (tujuan.length() == sumber.length()) {
                logger.lifecycle("arsip mapping: $versiNamaRilis-vc$versiKodeRilis sudah ada, isinya sama")
                return@doLast
            }
            // Ukuran BEDA untuk versionCode yang sama = dua biner berbeda memakai
            // satu nomor versi. Menimpanya diam-diam akan membuang peta milik
            // biner yang mungkin sudah beredar. Berhenti, jangan tebak mana yang benar.
            throw GradleException(
                "Arsip mapping untuk vc$versiKodeRilis SUDAH ADA dengan isi BERBEDA " +
                    "(${tujuan.length()} byte vs ${sumber.length()} byte). Dua build berbeda memakai " +
                    "satu versionCode — naikkan versionCode dulu, atau arsipkan yang lama dengan nama lain. " +
                    "JANGAN timpa: peta yang tertimpa tak bisa dipulihkan."
            )
        }
        sumber.copyTo(tujuan)
        logger.lifecycle("arsip mapping: ${tujuan.path} (${tujuan.length()} byte)")
    }
}

// `matching` + `configureEach`, bukan `named`: tugas `assembleRelease` baru lahir
// setelah plugin Android selesai memasang variannya.
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(arsipkanMappingRilis) }

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
        versionCode = 98
        versionName = "2.87"

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
if (rootProject.file("app/google-services.json").exists() && !project.hasProperty("localApi")) {
    apply(plugin = "com.google.gms.google-services")
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
val direktoriArsipRilis = rootProject.file("../cadangan-lokal")

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
            logger.warn("arsip mapping DILEWATI: ${direktoriArsipRilis.path} tak ada — peta untuk $versiNamaRilis TIDAK terarsip")
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

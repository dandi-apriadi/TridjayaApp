package com.krisoft.tridjayaelektronik.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.local.AppDatabase
import com.krisoft.tridjayaelektronik.data.local.BranchStockDao
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheDao
import com.krisoft.tridjayaelektronik.data.local.LeadDao
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.remote.ApkApi
import com.krisoft.tridjayaelektronik.data.remote.AuthApi
import com.krisoft.tridjayaelektronik.data.remote.EksekutifApi
import com.krisoft.tridjayaelektronik.data.remote.AbsensiApi
import com.krisoft.tridjayaelektronik.data.remote.DeadstockApi
import com.krisoft.tridjayaelektronik.data.remote.KuponGebyarApi
import com.krisoft.tridjayaelektronik.data.remote.DeliveryFlowApi
import com.krisoft.tridjayaelektronik.data.remote.CrmApi
import com.krisoft.tridjayaelektronik.data.remote.BirthdayApi
import com.krisoft.tridjayaelektronik.data.remote.DeviceApi
import com.krisoft.tridjayaelektronik.data.remote.ErpPriceChangesApi
import com.krisoft.tridjayaelektronik.data.remote.GodaApi
import com.krisoft.tridjayaelektronik.data.remote.EventApi
import com.krisoft.tridjayaelektronik.data.remote.OffApi
import com.krisoft.tridjayaelektronik.data.remote.AcInstallApi
import com.krisoft.tridjayaelektronik.data.remote.VertelApi
import com.krisoft.tridjayaelektronik.data.remote.HomeServiceApi
import com.krisoft.tridjayaelektronik.data.remote.AktivitasApi
import com.krisoft.tridjayaelektronik.data.remote.AktivitasUploadApi
import com.krisoft.tridjayaelektronik.data.remote.ProspekUploadApi
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import com.krisoft.tridjayaelektronik.data.remote.NetworkModule
import com.krisoft.tridjayaelektronik.data.remote.NotificationsApi
import com.krisoft.tridjayaelektronik.data.remote.KpiApi
import com.krisoft.tridjayaelektronik.data.remote.PayrollApi
import com.krisoft.tridjayaelektronik.data.remote.SalesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the application-lifetime coroutine scope used for fire-and-forget background work (e.g. the
 *  offline lead-sync queue) that must outlive any single ViewModel/screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun provideAuthApi(tokenStore: TokenStore): AuthApi = NetworkModule.createAuthApi(tokenStore)

    @Provides
    @Singleton
    fun provideInventoryApi(tokenStore: TokenStore): InventoryApi =
        NetworkModule.createInventoryApi(tokenStore)

    @Provides
    @Singleton
    fun provideSalesApi(tokenStore: TokenStore): SalesApi =
        NetworkModule.createSalesApi(tokenStore)

    @Provides
    @Singleton
    fun provideCrmApi(tokenStore: TokenStore): CrmApi = NetworkModule.createCrmApi(tokenStore)

    @Provides
    @Singleton
    fun provideAbsensiApi(tokenStore: TokenStore): AbsensiApi =
        NetworkModule.createAbsensiApi(tokenStore)

    @Provides
    @Singleton
    fun provideEksekutifApi(tokenStore: TokenStore): EksekutifApi =
        NetworkModule.createEksekutifApi(tokenStore)

    @Provides
    @Singleton
    fun provideEventApi(tokenStore: TokenStore): EventApi =
        NetworkModule.createEventApi(tokenStore)

    @Provides
    @Singleton
    fun provideOffApi(tokenStore: TokenStore): OffApi =
        NetworkModule.createOffApi(tokenStore)

    @Provides
    @Singleton
    fun provideAktivitasApi(tokenStore: TokenStore): AktivitasApi =
        NetworkModule.createAktivitasApi(tokenStore)

    @Provides
    @Singleton
    fun provideAktivitasUploadApi(tokenStore: TokenStore): AktivitasUploadApi =
        NetworkModule.createAktivitasUploadApi(tokenStore)

    @Provides
    @Singleton
    fun provideProspekUploadApi(tokenStore: TokenStore): ProspekUploadApi =
        NetworkModule.createProspekUploadApi(tokenStore)

    @Provides
    @Singleton
    fun provideHomeServiceApi(tokenStore: TokenStore): HomeServiceApi =
        NetworkModule.createHomeServiceApi(tokenStore)

    @Provides
    @Singleton
    fun provideAcInstallApi(tokenStore: TokenStore): AcInstallApi =
        NetworkModule.createAcInstallApi(tokenStore)

    @Provides
    @Singleton
    fun provideVertelApi(tokenStore: TokenStore): VertelApi =
        NetworkModule.createVertelApi(tokenStore)

    @Provides
    @Singleton
    fun provideDeviceApi(tokenStore: TokenStore): DeviceApi =
        NetworkModule.createDeviceApi(tokenStore)

    @Provides
    @Singleton
    fun provideBirthdayApi(tokenStore: TokenStore): BirthdayApi =
        NetworkModule.createBirthdayApi(tokenStore)

    @Provides
    @Singleton
    fun provideDeliveryFlowApi(tokenStore: TokenStore): DeliveryFlowApi =
        NetworkModule.createDeliveryFlowApi(tokenStore)

    @Provides
    @Singleton
    fun provideNotificationsApi(tokenStore: TokenStore): NotificationsApi =
        NetworkModule.createNotificationsApi(tokenStore)

    @Provides
    @Singleton
    fun providePayrollApi(tokenStore: TokenStore): PayrollApi =
        NetworkModule.createPayrollApi(tokenStore)

    @Provides
    @Singleton
    fun provideKpiApi(tokenStore: TokenStore): KpiApi =
        NetworkModule.createKpiApi(tokenStore)

    @Provides
    @Singleton
    fun provideErpPriceChangesApi(tokenStore: TokenStore): ErpPriceChangesApi =
        NetworkModule.createErpPriceChangesApi(tokenStore)

    @Provides
    @Singleton
    fun provideDeadstockApi(tokenStore: TokenStore): DeadstockApi =
        NetworkModule.createDeadstockApi(tokenStore)

    @Provides
    @Singleton
    fun provideGodaApi(tokenStore: TokenStore): GodaApi =
        NetworkModule.createGodaApi(tokenStore)

    @Provides
    @Singleton
    fun provideKuponGebyarApi(tokenStore: TokenStore): KuponGebyarApi =
        NetworkModule.createKuponGebyarApi(tokenStore)

    @Provides
    @Singleton
    fun provideApkApi(tokenStore: TokenStore): ApkApi =
        NetworkModule.createApkApi(tokenStore)

    /** v11 → v12: kolom aging stok (umurHari + kondisi) di branch_stock. Migrasi ADDITIVE —
     *  jangan destruktif, supaya antrean offline (pending leads, hitungan opname) tidak terhapus
     *  saat update aplikasi. */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE branch_stock ADD COLUMN umurHari INTEGER")
            db.execSQL("ALTER TABLE branch_stock ADD COLUMN kondisi TEXT")
        }
    }

    /** v13 → v14: opname pindah dari JUMLAH per SKU (`opname_counts`) ke satu baris per UNIT
     *  fisik (`opname_units`). Ditulis eksplisit dan BUKAN dibiarkan jatuh ke
     *  `fallbackToDestructiveMigration()`, karena wipe DB ikut membuang antrean prospek offline
     *  (`LeadEntity.pendingSync`) yang belum pernah sampai ke server — alasan yang sama dengan
     *  MIGRATION_11_12 di atas. Isi `opname_counts` sendiri sengaja dibuang: endpoint qty
     *  (`/opname/{id}/items`) sudah dihapus backend, jadi angka yang tersisa di sana tak punya
     *  tujuan kirim lagi. */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `opname_counts`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `opname_units` (" +
                    "`sessionId` TEXT NOT NULL, `serialNumber` TEXT NOT NULL, " +
                    "`kodeBarang` TEXT NOT NULL, `namaBarang` TEXT, `kondisi` TEXT NOT NULL, " +
                    "`keterangan` TEXT, `temuan` TEXT, `updatedAtMillis` INTEGER NOT NULL, " +
                    "`syncedAtMillis` INTEGER, PRIMARY KEY(`sessionId`, `serialNumber`))"
            )
        }
    }

    /** v14 → v15: unit ketik-manual membawa metadata validasi admin-stok
     *  (migrasi backend 193). Eksplisit, bukan destructive — alasan sama
     *  MIGRATION_13_14: wipe DB ikut membuang antrean offline yang belum
     *  terkirim. Baris lama otomatis `inputMethod='scan'`. */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `opname_units` ADD COLUMN `inputMethod` TEXT NOT NULL DEFAULT 'scan'")
            db.execSQL("ALTER TABLE `opname_units` ADD COLUMN `validationStatus` TEXT")
            db.execSQL("ALTER TABLE `opname_units` ADD COLUMN `rejectReason` TEXT")
        }
    }

    /** v15 → v16: antrean create prospek menyimpan vonis permanen server
     *  (`leads.syncRejectReason`). Eksplisit, bukan destructive — alasan sama
     *  MIGRATION_13_14: wipe DB ikut membuang antrean offline yang belum
     *  terkirim, dan justru baris antrean itulah yang kolom ini jelaskan.
     *  Baris lama otomatis NULL = masih benar-benar mengantre. */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `leads` ADD COLUMN `syncRejectReason` TEXT")
        }
    }

    /**
     * Bukti prospek pada baris antrean. ALTER eksplisit, BUKAN dibiarkan jatuh
     * ke `fallbackToDestructiveMigration()` — lihat komentar di builder.
     */
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `leads` ADD COLUMN `buktiUrl` TEXT")
        }
    }

    /**
     * Peringatan WA penugasan tak terkirim (`leads.assignmentWarning`). ALTER
     * eksplisit dengan alasan yang sama seperti empat migrasi di atas: tabel
     * `leads` memegang antrean offline yang BELUM sampai server, jadi jatuh ke
     * `fallbackToDestructiveMigration()` berarti menghapus prospek yang orangnya
     * sudah ketik dan kira tersimpan.
     */
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `leads` ADD COLUMN `assignmentWarning` TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tridjaya.db")
            .addMigrations(
                MIGRATION_11_12, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                MIGRATION_16_17, MIGRATION_17_18,
            )
            // KOREKSI 2026-08-18 atas komentar lama ("local cache only — safe to
            // wipe"): itu SUDAH TIDAK BENAR sejak `leads` mendapat antrean
            // offline. Tabel itu memegang baris ber-`pendingSync = 1` yang
            // BELUM pernah sampai ke server, jadi jatuh ke destructive berarti
            // menghapus prospek yang orangnya sudah ketik dan kira tersimpan —
            // tanpa pesan apa pun, saat memasang APK baru.
            //
            // Entity LAIN memang cache murni. Jadi aturannya sekarang: setiap
            // perubahan skema yang menyentuh `leads` WAJIB punya Migration
            // eksplisit di atas. Fallback dipertahankan untuk sisanya.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBranchStockDao(database: AppDatabase): BranchStockDao = database.branchStockDao()

    @Provides
    fun provideSyncMetaDao(database: AppDatabase): SyncMetaDao = database.syncMetaDao()

    @Provides
    fun provideDashboardCacheDao(database: AppDatabase): DashboardCacheDao = database.dashboardCacheDao()

    @Provides
    fun provideLeadDao(database: AppDatabase): LeadDao = database.leadDao()

    @Provides
    fun provideOpnameUnitDao(database: AppDatabase): OpnameUnitDao = database.opnameUnitDao()
}

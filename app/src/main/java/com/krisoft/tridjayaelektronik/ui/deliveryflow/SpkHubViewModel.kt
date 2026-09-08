package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.lifecycle.ViewModel
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.model.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Kebijakan akses alur SPK per-user — MIRROR gate backend (delivery.rs /
 * discounts.rs / aki.rs) supaya tiap divisi hanya lihat tahap/aksi yang jadi
 * tanggung jawabnya. Backend tetap otoritatif (menolak aksi lintas-scope);
 * ini murni menyaring menu & tombol. Dipakai [SpkHubViewModel] (menu hub) DAN
 * [DeliveryFlowViewModel] (aksi layar detail + tombol approval aki).
 */
object SpkAccessPolicy {

    /** Divisi bernilai-akses operasional — nama divisi identik dgn nama role.
     *  MIRROR `divisi_access_slugs` backend (pasca remediasi 2026-07-22):
     *  divisi label lain ("admin"/"sales"/"support" dst) BUKAN pembawa akses —
     *  jangan disuntik jadi role, bikin menu bohong (backend tetap 403). */
    val OPERATIONAL_DIVISI = setOf("pdi", "kasir", "driver", "delivery-control", "admin-penjualan")

    /** Role efektif viewer: role utama ∪ roles[] backend ∪ divisi OPERASIONAL.
     *  Cache lama pra-update: `roles` kosong → fallback role+divisi tetap jalan. */
    fun rolesOf(user: UserDto?): Set<String> = buildSet {
        user?.role?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        user?.roles?.forEach { it.trim().lowercase().takeIf { s -> s.isNotEmpty() }?.let { s -> add(s) } }
        user?.divisi?.split(",")?.forEach { raw ->
            raw.trim().lowercase().takeIf { it in OPERATIONAL_DIVISI }?.let { add(it) }
        }
    }

    fun grantPrefixesOf(user: UserDto?): List<String> =
        user?.pageGrants.orEmpty().map { it.prefix.trim().lowercase() }.filter { it.isNotEmpty() }

    fun isAdmin(roles: Set<String>): Boolean = "admin" in roles || "superadmin" in roles

    /** Paritas backend `is_manager` (delivery.rs) = manager ATAU owner. */
    fun isManager(roles: Set<String>): Boolean = "manager" in roles || "owner" in roles

    /** Role yang TIDAK boleh jadi aktor tulis SPK — mirror
     *  `capabilities::SPK_CREATE_BLOCKED_ROLES` (rust-shared). */
    private val SPK_CREATE_BLOCKED = setOf("manager", "owner", "ai-engineer")

    /** Paritas `capabilities::can_create_spk`: semua role KECUALI manager/owner/
     *  ai-engineer, dan bukan aktor tanpa role sama sekali. */
    fun canCreateSpk(roles: Set<String>): Boolean =
        roles.isNotEmpty() && roles.none { it in SPK_CREATE_BLOCKED }

    private fun hasGrant(grants: List<String>, prefix: String) = grants.any { it.contains(prefix) }

    /** Boleh menyetujui/menolak form aki (aki.rs `approve_form`/`reject_form`) —
     *  approval TUNGGAL, redesain 2026-07-24 (dulu 3-pihak/089): HANYA approver
     *  PUSAT (page-grant `/dashboard/aki-approval`, implied role `aki-approver`)
     *  atau admin/manager. kepala-cabang/admin-penjualan/kasir DICABUT — dulu
     *  ikut approve cabang sendiri, kini 403 di backend. */
    fun canApproveAki(roles: Set<String>, grants: List<String>): Boolean =
        isAdmin(roles) || isManager(roles) ||
            roles.contains("aki-approver") ||
            hasGrant(grants, "/dashboard/aki-approval")

    fun accessOf(user: UserDto?): SpkHubAccess {
        val roles = rolesOf(user)
        val grants = grantPrefixesOf(user)
        val admin = isAdmin(roles)
        fun hasRole(vararg r: String) = r.any { roles.contains(it) }
        return SpkHubAccess(
            // Buat SPK & riwayat: kebijakan create-SPK terbuka (semua staf lapangan).
            input = true,
            history = true,
            diskon = admin || hasGrant(grants, "/dashboard/discount-approval"),
            pdi = admin || hasRole("pdi"),
            // Menu Pengambilan Aki: PDI (pembuat form, lihat status form sendiri)
            // + approver pusat/manager/admin. Redesain 2026-07-24 (dulu juga
            // kasir/admin-penjualan/kepala-cabang — 3-pihak/089) — role itu
            // dicabut, backend sudah 403-kan mereka di endpoint ini.
            aki = admin || isManager(roles) || hasRole("pdi", "aki-approver") ||
                hasGrant(grants, "/dashboard/aki-approval"),
            kasir = admin || hasRole("kasir"),
            note = admin || hasRole("delivery-control"),
            jadwal = admin || hasRole("delivery-control"),
            // Sales antar sendiri (2026-07-24, backend `authorize_driver` SALES_ROLES
            // ∪ driver): sales/admin-sales bisa di-assign jadi driver job SPK
            // miliknya sendiri (`delivery_method='sales_delivery'`) — tanpa ini menu
            // "Tugas Antar" tak pernah kelihatan buat mereka walau job-nya ada.
            // Ownership tetap dicek backend per-job (assignedDriverId==actor).
            driver = admin || hasRole("driver", "sales", "admin-sales"),
            // Tombol aksi driver PER-JOB (berangkat/chat/serah terima). Gate
            // backend `authorize_driver` = (role driver ATAU `can_create_spk`)
            // DAN job di-assign ke aktor — jadi patokannya kepemilikan, bukan
            // nama role. [driver] di atas terlalu sempit untuk ini: di produksi
            // SEMUA staf lapangan ber-role `karyawan` (nol akun ber-role
            // "sales"), sehingga staf yang mengantar SPK-nya sendiri
            // (`sales_delivery`) tak pernah dapat tombolnya walau backend
            // mengizinkan. Aman dilebarkan karena pemanggilnya SELALU
            // memasangkan ini dengan `assignedDriverId == currentUserId`.
            driverAction = admin || hasRole("driver") || canCreateSpk(roles),
            riwayatDiskon = true,
        )
    }
}

/** Akses menu hub SPK per-role — murni menyaring menu, backend otoritatif. */
@HiltViewModel
class SpkHubViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val access = SpkAccessPolicy.accessOf(authRepository.cachedUser)
}

data class SpkHubAccess(
    val input: Boolean,
    val history: Boolean,
    val diskon: Boolean,
    val riwayatDiskon: Boolean,
    val pdi: Boolean,
    val aki: Boolean,
    val kasir: Boolean,
    val note: Boolean,
    val jadwal: Boolean,
    val driver: Boolean,
    val driverAction: Boolean,
)

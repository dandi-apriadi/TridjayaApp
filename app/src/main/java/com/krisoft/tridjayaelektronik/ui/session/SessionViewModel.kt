package com.krisoft.tridjayaelektronik.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.DeviceRepository
import com.krisoft.tridjayaelektronik.domain.auth.ObserveMustChangePasswordUseCase
import com.krisoft.tridjayaelektronik.domain.auth.ObserveSessionStateUseCase
import com.krisoft.tridjayaelektronik.domain.auth.ValidateSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single source of truth for whether the app should show Login or Main content. [sessionState]
 * starts from whatever's cached locally (instant, no network wait) and is kept live thereafter —
 * it flips to false on explicit logout *or* if the silent background [validateSessionUseCase]
 * call below discovers the session is no longer valid (its underlying token refresh failed).
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    observeSessionStateUseCase: ObserveSessionStateUseCase,
    observeMustChangePasswordUseCase: ObserveMustChangePasswordUseCase,
    private val validateSessionUseCase: ValidateSessionUseCase,
    private val deviceRepository: DeviceRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val sessionState: StateFlow<Boolean> = observeSessionStateUseCase()

    /** When true (and logged in), the app must show the forced change-password gate. */
    val mustChangePassword: StateFlow<Boolean> = observeMustChangePasswordUseCase()

    /** Profil dari cache sesi (sinkron, tanpa network) — dipakai `MainScreen` memilih tab
     *  awal (Activity vs Operasional) dari role efektif saat komposisi pertama. */
    val cachedUser get() = authRepository.cachedUser

    /**
     * Peta kemampuan untuk gerbang TAB (`AppDestination.visibleBottomNavItems`).
     *
     * Sengaja hanya MENERUSKAN cermin milik [AuthRepository] — ViewModel ini
     * di-scope ke root `TridjayaNavHost` dan hidup seumur proses, jadi kalau ia
     * mengambil sendiri, petanya beku sampai app dimatikan. Yang mengisi cermin
     * itu `ActivityViewModel`/`HomeViewModel` lewat `PenyegarKemampuan`, yang
     * memang mengambil ulang tiap sidik akses atau identitas token berubah.
     *
     * `null` = belum pernah berhasil diambil. Di keadaan itu gerbangnya jatuh ke
     * daftar role lokal (cadangan offline), BUKAN fail-closed — lihat
     * `AuthRepository.petaKemampuanTerakhir`.
     */
    val petaKemampuan: StateFlow<Map<String, Boolean>?> = authRepository.petaKemampuanTerakhir

    init {
        if (sessionState.value) {
            viewModelScope.launch { validateSessionUseCase() }
        }
        // Daftarkan token FCM ke backend tiap kali sesi login aktif — titik pusat, BUKAN digantung
        // di AttendanceViewModel seperti sebelumnya (kebetulan historis: user yang tak pernah buka
        // layar Absensi jadi TAK PERNAH ke-daftar tokennya, jadi tak pernah dapat push delivery/CRM
        // sama sekali — ketemu nyata dari laporan "notifikasi delivery flow tidak jalan"). Reaktif
        // (bukan cuma cek sekali di init) supaya login BARU dalam proses yang sama (bukan cold-start
        // sudah login) ikut ke-daftar, bukan cuma app yang dibuka dalam keadaan sudah login.
        viewModelScope.launch {
            sessionState.collect { loggedIn -> if (loggedIn) deviceRepository.registerCurrentToken() }
        }
    }
}

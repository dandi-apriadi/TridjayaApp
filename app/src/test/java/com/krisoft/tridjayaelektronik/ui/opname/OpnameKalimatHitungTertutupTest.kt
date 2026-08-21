package com.krisoft.tridjayaelektronik.ui.opname

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fungsi murni — dites tanpa Compose, pola sama `filterOpnameStock`.
 *
 * Cerminan `frontend/src/utils/opnameIzinCatat.ts` beserta testnya
 * (`opnameIzinCatat.spec.ts`). Sampai 2026-08-21 layar ini hanya punya SATU
 * kalimat dan kalimat itu mengarahkan SEMUA orang ke admin stok — arahan yang
 * BUNTU bagi pemantau lintas cabang, karena tak ada satu pun izin yang bisa
 * diberikan admin stok kepada mereka.
 */
class OpnameKalimatHitungTertutupTest {

    @Test
    fun `manager owner dapat kalimat pemantau, bukan disuruh menghubungi admin stok`() {
        val kalimat = kalimatHitungTertutup(isManager = true, lingkup = "semua")
        assertTrue(kalimat, kalimat.contains("memantau opname lintas cabang"))
        assertFalse("pemantau tak boleh disuruh menagih izin: $kalimat", kalimat.contains("Hubungi"))
    }

    @Test
    fun `administrator platform dapat kalimatnya sendiri`() {
        // admin/superadmin: `isManager` false (OPNAME_MONITOR_ROLES cuma
        // manager/owner) tapi lingkupnya `semua` sejak `has_admin_platform`
        // membuka baca lintas cabang 2026-08-21.
        val kalimat = kalimatHitungTertutup(isManager = false, lingkup = "semua")
        assertTrue(kalimat, kalimat.contains("administrator platform"))
        assertFalse("arahan buntu: $kalimat", kalimat.contains("Hubungi"))
    }

    @Test
    fun `pemantau diperiksa LEBIH DULU daripada lingkup`() {
        // Urutannya bermakna: manager/owner juga ber-lingkup `semua`, jadi
        // membalik urutan membuat mereka disebut administrator platform.
        assertTrue(
            kalimatHitungTertutup(isManager = true, lingkup = "semua")
                .contains("memantau opname lintas cabang"),
        )
    }

    @Test
    fun `petugas cabang tetap diarahkan ke admin stok — di sana arahannya benar`() {
        val kalimat = kalimatHitungTertutup(isManager = false, lingkup = "cabang")
        assertTrue(kalimat, kalimat.contains("Hubungi admin stok"))
        assertTrue(kalimat, kalimat.contains("menunjuk petugas opname"))
    }

    @Test
    fun `konteks belum tiba atau gagal jatuh ke kalimat LAMA, bukan menuduh peran`() {
        // `null` = "belum tahu". Konteks diambil di coroutine terpisah dan
        // permintaannya boleh gagal tanpa mematikan apa pun.
        val kalimat = kalimatHitungTertutup(isManager = null, lingkup = null)
        assertEquals(kalimatHitungTertutup(isManager = false, lingkup = "cabang"), kalimat)
    }

    @Test
    fun `server lama yang tak mengirim lingkup tetap dapat kalimat lama`() {
        // `OpnameContextDto.lingkup` default `""`, `isManager` default `false`.
        val kalimat = kalimatHitungTertutup(isManager = false, lingkup = "")
        assertTrue(kalimat, kalimat.contains("Hubungi admin stok"))
    }
}

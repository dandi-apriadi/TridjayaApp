package com.krisoft.tridjayaelektronik.data.model

import com.krisoft.tridjayaelektronik.data.remote.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Kontrak KABEL KPI — penjaga atas kelas bug yang GAGAL SENYAP.**
 *
 * Converter Retrofit app ini ber-`ignoreUnknownKeys = true`
 * (`NetworkModule.json`), jadi field yang salah nama / belum dideklarasikan
 * **tidak** melempar apa pun: nilainya diam-diam jatuh ke default dan layarnya
 * cuma kehilangan informasi. Itu persis yang terjadi pada model bonus per
 * indikator: server mengirim `bonusMaksRp`/`kategoriTotal`/`alasan[]` sejak
 * 2026-08-19, `KpiBracketDto` tak mendeklarasikan satu pun, dan selama sembilan
 * hari layar KPI di HP kehilangan seluruh rincian "kenapa bonusku tidak penuh"
 * tanpa satu pun error di mana pun.
 *
 * Karena itu test ini **memakai instans converter yang SUNGGUHAN**
 * ([NetworkModule.json], `internal`), bukan `Json {}` bikinan sendiri —
 * menyalin konfigurasinya berarti menguji salinan yang bisa melenceng dari yang
 * benar-benar dipakai Retrofit. Alasan yang sama ditulis di berkas itu.
 *
 * JSON di bawah disalin dari bentuk yang benar-benar dipancarkan
 * `kinerja-service`: `kpi/scoring.rs::payload` (`bracket`) + `ScoredItem`
 * (`items[]`), keduanya `#[serde(rename_all = "camelCase")]`.
 */
class KpiKontrakKabelTest {

    private val json = NetworkModule.json

    // ── Model BONUS per indikator (Excel Sept 2026, periode 2026-09 ke atas) ──

    /**
     * Bentuk yang dipancarkan `payload()` untuk vonis model baru. Kalau salah
     * satu nama field di DTO meleset, assert di bawah memerah — BUKAN diam.
     */
    private val bonusBaru = """
        {
          "periode": "2026-09",
          "totalScore": 0.87,
          "totalPct": 87.0,
          "filled": true,
          "items": [
            {
              "indicatorId": 1,
              "indikator": "KEHADIRAN",
              "target": 100.0,
              "bobot": 0.35,
              "actual": 95.0,
              "achievement": 0.95,
              "hasilBobot": 0.3325,
              "kategori": "BAGUS",
              "bonusRp": 262500,
              "source": "auto"
            }
          ],
          "bracket": {
            "kind": "reward",
            "amount": 787500,
            "kategoriTotal": "SEDANG",
            "bonusMaksRp": 1500000,
            "alasan": [
              {
                "indikator": "PENJUALAN",
                "achievement": 0.4,
                "bobot": 0.3,
                "kategori": "KURANG",
                "bonusRp": 0,
                "bonusMaksRp": 450000,
                "hilangRp": 450000,
                "dinilai": true
              },
              {
                "indikator": "LAPORAN AKTIVITAS",
                "achievement": 0.0,
                "bobot": 0.35,
                "kategori": "KURANG",
                "bonusRp": 0,
                "bonusMaksRp": 525000,
                "hilangRp": 525000,
                "dinilai": false
              }
            ]
          },
          "insentif": null
        }
    """.trimIndent()

    @Test
    fun `bracket model bonus terdekode utuh — bukan jatuh ke default`() {
        val d = json.decodeFromString<KpiDetailData>(bonusBaru)
        val b = assertNotNull("bracket wajib terdekode", d.bracket).let { d.bracket!! }

        assertEquals("reward", b.kind)
        assertEquals(787_500L, b.amount)
        // Ketiga field inilah yang dulu hilang senyap.
        assertEquals("SEDANG", b.kategoriTotal)
        assertEquals(1_500_000L, b.bonusMaksRp)
        assertEquals(2, b.alasan.size)
    }

    @Test
    fun `alasan membawa rupiah dan penanda belum-dinilai`() {
        val b = json.decodeFromString<KpiDetailData>(bonusBaru).bracket!!
        val pertama = b.alasan[0]
        assertEquals("PENJUALAN", pertama.indikator)
        assertEquals(450_000L, pertama.hilangRp)
        assertEquals(0L, pertama.bonusRp)
        assertEquals("KURANG", pertama.kategori)
        assertTrue(pertama.dinilai)

        // Bedanya penting: capaian rendah itu kinerja, indikator kosong itu
        // penilaian yang belum dikerjakan HR. Keduanya membayar Rp 0, jadi
        // hanya flag inilah yang membedakannya di layar.
        assertFalse("indikator belum dinilai wajib tertandai", b.alasan[1].dinilai)
    }

    @Test
    fun `item KPI membawa kategori dan bonusRp`() {
        val item = json.decodeFromString<KpiDetailData>(bonusBaru).items.single()
        assertEquals("BAGUS", item.kategori)
        assertEquals(262_500L, item.bonusRp)
    }

    @Test
    fun `vonis model bonus dikenali dari BENTUK payload`() {
        val b = json.decodeFromString<KpiDetailData>(bonusBaru).bracket!!
        assertTrue("bonusMaksRp ada -> model bonus", b.modelBonus)
    }

    // ── Snapshot periode TERKUNCI (Juli & Agustus 2026, aturan LAMA) ──────────

    /**
     * Vonis aturan lama yang benar-benar tersimpan di `kpi_snapshot` periode
     * terkunci: `kind: "punishment"` (nilai yang server TAK PERNAH terbitkan
     * lagi), `insentif` persen untuk posisi sales, dan `alasan[]` berbentuk
     * `dampakPct` — bukan `hilangRp`.
     *
     * App WAJIB tetap bisa membacanya. Periode 2026-07/08 masih dibuka orang
     * dari HP, dan snapshot-nya sengaja diawetkan (migrasi 250).
     */
    private val snapshotLama = """
        {
          "periode": "2026-07",
          "totalScore": 1.12,
          "totalPct": 111.87,
          "filled": true,
          "items": [
            {
              "indicatorId": 9,
              "indikator": "KEHADIRAN",
              "target": 100.0,
              "bobot": 0.35,
              "actual": 90.0,
              "achievement": 0.9,
              "hasilBobot": 0.315
            }
          ],
          "bracket": {
            "kind": "punishment",
            "amount": 250000,
            "alasan": [
              { "indikator": "PENJUALAN", "achievement": 0.5, "bobot": 0.3, "dampakPct": -15.0, "dinilai": true }
            ]
          },
          "insentif": { "pct": -5.0, "komponen": [ { "sumber": "kpi", "kind": "punishment", "label": "KPI TOTAL", "pct": -5.0 } ] }
        }
    """.trimIndent()

    @Test
    fun `snapshot aturan lama tetap terbaca dan TIDAK mengaku model bonus`() {
        val d = json.decodeFromString<KpiDetailData>(snapshotLama)
        val b = d.bracket!!
        assertEquals("punishment", b.kind)
        assertEquals(250_000L, b.amount)
        assertFalse("tanpa bonusMaksRp = BUKAN model bonus", b.modelBonus)
        assertNull("model lama tak punya kategoriTotal", b.kategoriTotal)
        assertNull(b.bonusMaksRp)

        // Insentif persen HANYA ada di snapshot lama; server model baru selalu null.
        assertEquals(-5.0, d.insentif!!.pct, 1e-9)
    }

    /**
     * Inti kenapa [KpiBracketAlasanDto.hilangRp] nullable, bukan `0L` ber-default.
     *
     * Baris alasan lama cuma punya `dampakPct`. Kalau `hilangRp` dibuat
     * non-null ber-default 0, layar akan mencetak "−Rp 0" untuk SETIAP
     * indikator — daftar yang terbaca "tak ada yang hilang" padahal sebabnya
     * cuma tak terbaca. `null` bisa dibedakan; `0` tidak.
     */
    @Test
    fun `alasan bentuk lama menyisakan hilangRp null, bukan nol`() {
        val a = json.decodeFromString<KpiDetailData>(snapshotLama).bracket!!.alasan.single()
        assertNull("hilangRp WAJIB null di bentuk lama", a.hilangRp)
        assertNull(a.bonusRp)
        assertNull(a.kategori)
        assertEquals(-15.0, a.dampakPct!!, 1e-9)
    }

    @Test
    fun `item snapshot lama menyisakan kategori dan bonusRp null`() {
        val item = json.decodeFromString<KpiDetailData>(snapshotLama).items.single()
        assertNull(item.kategori)
        assertNull(item.bonusRp)
        // Yang lama tetap terbaca.
        assertEquals(0.315, item.hasilBobot, 1e-9)
    }

    // ── Bentuk pinggiran ─────────────────────────────────────────────────────

    @Test
    fun `bracket netral tanpa alasan terdekode sebagai daftar kosong`() {
        val d = json.decodeFromString<KpiDetailData>(
            """{"periode":"2026-09","filled":true,
               "bracket":{"kind":"netral","amount":0,"bonusMaksRp":1500000,"alasan":[]}}"""
        )
        val b = d.bracket!!
        assertEquals("netral", b.kind)
        assertEquals(0L, b.amount)
        assertTrue("alasan kosong = tiap indikator BAGUS SEKALI", b.alasan.isEmpty())
        assertTrue(b.modelBonus)
    }

    /**
     * Respons LAMA (server yang belum mengirim `alasan` sama sekali) tak boleh
     * membuat dekode gagal — APK baru bisa saja jalan di atas server lama.
     */
    @Test
    fun `bracket tanpa field alasan tetap terdekode`() {
        val b = json.decodeFromString<KpiDetailData>(
            """{"periode":"2026-09","filled":true,"bracket":{"kind":"reward","amount":500000}}"""
        ).bracket!!
        assertTrue(b.alasan.isEmpty())
        assertFalse(b.modelBonus)
    }

    /**
     * `filled=false` = server MENAHAN vonis (Σbobot terisi < 0,5). `bracket`
     * dan `insentif` sama-sama null — jangan menghitung sendiri, uangnya nyata.
     */
    @Test
    fun `vonis ditahan server terdekode sebagai null, bukan nol`() {
        val d = json.decodeFromString<KpiDetailData>(
            """{"periode":"2026-09","filled":false,"bracket":null,"insentif":null}"""
        )
        assertFalse(d.filled)
        assertNull(d.bracket)
        assertNull(d.insentif)
    }
}

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

    /**
     * `alasan` kosong ⟹ bonus PENUH, jadi bentuk yang benar untuk kasus ini
     * adalah `reward` ber-`amount == bonusMaksRp` — BUKAN `netral`.
     * `bracket_alasan` (`scoring.rs:174-195`) hanya membuang baris ber-`hilang
     * == 0`, dan `payload` (`:403`) menulis `netral` hanya saat `amount == 0`;
     * keduanya bersamaan mustahil (Σbobot = 0 sudah tercegat `filled`).
     *
     * Draf pertama test ini memakai `netral` + `alasan:[]` dan pesan assert-nya
     * berbunyi "alasan kosong = tiap indikator BAGUS SEKALI" — membacakan
     * kebalikan dari `amount:0` yang ia assert sendiri. Ditemukan review
     * adversarial 2026-08-28. Fixture yang mustahil membuat test tetap hijau
     * sambil menjelaskan dunia yang tak ada.
     */
    @Test
    fun `bonus penuh — alasan kosong datang bersama kind reward`() {
        val b = json.decodeFromString<KpiDetailData>(
            """{"periode":"2026-09","filled":true,
               "bracket":{"kind":"reward","amount":1500000,"kategoriTotal":"BAGUS SEKALI",
                          "bonusMaksRp":1500000,"alasan":[]}}"""
        ).bracket!!
        assertEquals("reward", b.kind)
        assertTrue("alasan kosong = tiap indikator BAGUS SEKALI", b.alasan.isEmpty())
        assertEquals("bonus penuh: amount == bonusMaksRp", b.bonusMaksRp, b.amount)
        assertTrue(b.modelBonus)
    }

    /** `netral` yang NYATA: Rp 0, dan alasannya justru berisi. */
    @Test
    fun `bracket netral membawa alasan yang menjelaskan Rp 0`() {
        val b = json.decodeFromString<KpiDetailData>(
            """{"periode":"2026-09","filled":true,
               "bracket":{"kind":"netral","amount":0,"kategoriTotal":"KURANG","bonusMaksRp":1500000,
                 "alasan":[{"indikator":"KEHADIRAN","achievement":0.5,"bobot":1.0,"kategori":"KURANG",
                            "bonusRp":0,"bonusMaksRp":1500000,"hilangRp":1500000,"dinilai":true}]}}"""
        ).bracket!!
        assertEquals("netral", b.kind)
        assertEquals(0L, b.amount)
        assertEquals(1, b.alasan.size)
        assertEquals(1_500_000L, b.alasan.single().hilangRp)
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
    // ── Render alasan: SATUAN & JUDUL ikut model ─────────────────────────────
    //
    // Paritas dengan `KpiBracketAlasan` web. Versi pertama panel mobile
    // `return` lebih awal untuk snapshot periode terkunci, sehingga karyawan
    // yang membuka Juli/Agustus dari HP melihat "Punishment Rp 250.000" TANPA
    // satu baris sebab — sementara rekannya di web melihat daftar penyebabnya.
    // Denda rupiah yang tak bisa dibantah dari kanal lapangan.

    private val fRupiah: (Double) -> String = { "Rp ${it.toLong()}" }
    private val fAngka: (Double) -> String = { if (it == it.toLong().toDouble()) "${it.toLong()}" else "$it" }

    @Test
    fun `judul panel membedakan model bonus dari aturan lama`() {
        // Kata per kata sama dengan `KpiBracketAlasan.tsx` — dua kanal yang
        // menamai hal yang sama dengan kalimat berbeda terbaca sebagai dua hal.
        assertEquals("Kenapa bonusnya tidak penuh", judulAlasanKpi(true))
        // Aturan lama menamainya vonis, bukan bonus — satu judul untuk keduanya
        // membuat arsip Juli terbaca seolah lahir dari model hari ini.
        assertTrue(judulAlasanKpi(false).contains("aturan lama"))
    }

    @Test
    fun `model bonus memakai satuan RUPIAH dari hilangRp`() {
        val baris = KpiBracketAlasanDto(indikator = "PENJUALAN", hilangRp = 450_000, dinilai = true)
        assertEquals("−Rp 450000", dampakAlasanKpi(baris, true, fRupiah, fAngka))
    }

    @Test
    fun `snapshot lama memakai satuan POIN dari dampakPct`() {
        val baris = KpiBracketAlasanDto(indikator = "PENJUALAN", dampakPct = -15.0, dinilai = true)
        assertEquals("-15 poin", dampakAlasanKpi(baris, false, fRupiah, fAngka))
        // Dampak POSITIF diberi tanda + supaya arahnya terbaca.
        assertEquals("+3 poin", dampakAlasanKpi(baris.copy(dampakPct = 3.0), false, fRupiah, fAngka))
    }

    /**
     * INTI aturannya, dan yang paling mudah dirusak: membaca field milik model
     * SEBERANG menghasilkan angka yang terlihat sah tapi bohong.
     *
     * `hilangRp ?: 0` pada baris lama mencetak "−Rp 0" untuk SETIAP indikator —
     * daftar yang terbaca "tak ada yang hilang" padahal sebabnya cuma tak
     * terbaca. `null` bisa dibedakan; `0` tidak.
     */
    @Test
    fun `field model seberang tak pernah dibaca — null, bukan nol`() {
        // Baris LAMA dinilai sebagai model bonus: `hilangRp` memang tak ada.
        val lama = KpiBracketAlasanDto(indikator = "X", dampakPct = -15.0)
        assertNull("jangan mencetak −Rp 0", dampakAlasanKpi(lama, true, fRupiah, fAngka))

        // Baris BARU dinilai sebagai aturan lama: `dampakPct` memang tak ada.
        val baru = KpiBracketAlasanDto(indikator = "X", hilangRp = 450_000)
        assertNull("jangan mencetak 0 poin", dampakAlasanKpi(baru, false, fRupiah, fAngka))
    }

    /**
     * Vonis punishment yang DIBATALKAN owner (migrasi 311, lima driver Agustus
     * 2026). Dua field baru — `amountAsli` & `dibatalkanSebab` — harus
     * benar-benar sampai ke DTO.
     *
     * Kalau salah satu tak terdeklarasi, `ignoreUnknownKeys` membuangnya TANPA
     * ERROR dan layar mencetak "Punishment Rp 0 — dibatalkan" tanpa nominal
     * maupun sebab. Itu persis kelas kegagalan senyap yang melahirkan berkas
     * test ini.
     */
    @Test
    fun `vonis dibatalkan membawa nominal asli dan sebabnya`() {
        val d = json.decodeFromString<KpiDetailData>(
            """{"periode":"2026-08","filled":true,"bracket":{"kind":"dibatalkan","amount":0,
               "amountAsli":500000,"dibatalkanSebab":"Aturan punishment dicabut 2026-08-19.",
               "alasan":[{"indikator":"SETORAN COD TEPAT WAKTU","dampakPct":-45.45,"dinilai":true}]},
               "insentif":null}"""
        )
        val b = d.bracket!!
        assertEquals("dibatalkan", b.kind)
        assertEquals(0L, b.amount)
        assertEquals(500_000L, b.amountAsli)
        assertEquals("Aturan punishment dicabut 2026-08-19.", b.dibatalkanSebab)
        // Snapshot lama: tanpa `bonusMaksRp`, jadi rincian alasannya WAJIB
        // dibaca sebagai model LAMA (satuan poin), bukan rupiah.
        assertFalse("vonis batal berasal dari snapshot aturan lama", b.modelBonus)
        assertEquals(1, b.alasan.size)
    }

    /**
     * ARAH BALIK: vonis yang BUKAN `dibatalkan` tak boleh mendadak membawa
     * `amountAsli`. Kalau ia terisi di luar konteks itu, layar akan mencetak
     * nominal denda untuk orang yang tak pernah didenda.
     */
    @Test
    fun `vonis biasa tidak membawa nominal batal`() {
        val d = json.decodeFromString<KpiDetailData>(
            """{"periode":"2026-09","filled":true,"bracket":{"kind":"reward","amount":662500,"bonusMaksRp":1500000},"insentif":null}"""
        )
        val b = d.bracket!!
        assertNull(b.amountAsli)
        assertNull(b.dibatalkanSebab)
        assertTrue(b.modelBonus)
    }

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

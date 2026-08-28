package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.kondisiLabel
import com.krisoft.tridjayaelektronik.data.model.BrokerOption
import com.krisoft.tridjayaelektronik.data.model.SerialRegistryRow
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.MoneyTextField
import java.io.File
import kotlinx.coroutines.launch

/**
 * Saran serial yang ditampilkan di kartu barang SPK, urut unit sehat dulu.
 *
 * Daftarnya dipotong lima di UI, jadi pengurutan ini yang menentukan APA yang
 * terlihat: tanpa itu satu batch unit retur bisa mengisi seluruh saran dan
 * menyembunyikan unit layak yang kebetulan ada di posisi keenam — sales tak
 * akan pernah tahu ia ada. Unit bermasalah tetap DITAMPILKAN (keputusan user
 * 2026-08-09: peringatkan, jangan blokir), cuma turun ke bawah.
 *
 * `sortedBy` stabil, jadi urutan dari server (kode barang, lalu serial) tetap
 * terjaga di dalam masing-masing kelompok.
 */
internal fun serialUntukDisarankan(
    opsi: List<SerialRegistryRow>,
    serialTerpilih: String
): List<SerialRegistryRow> =
    opsi.filter { it.serialNumber != serialTerpilih }.sortedBy { it.bermasalah }

/**
 * Kartu satu barang SPK multi-unit — tiap barang bawa pembayaran/komisi/order
 * sendiri (mirror kartu item web SalesDeliveryFlowPage). Collapsible utk layar
 * sempit.
 *
 * **Progressive disclosure (2026-08-12).** Mayoritas SPK = cash, tanpa diskon,
 * tanpa COD, tanpa KBK. Dulu keempat blok itu selalu terpampang di TIAP kartu
 * (dikali maksimal [MAX_SPK_BARIS] barang), jadi jalur yang paling sering
 * dipakai justru jalur dengan paling banyak isian untuk dilewati. Sekarang
 * kartu terbuka hanya memuat yang selalu perlu — barang, jumlah, serial, harga,
 * siapa yang PDI — dan empat blok sisanya baru muncul setelah PEMICUNYA
 * diketuk di baris "Cara bayar & tambahan".
 *
 * **Invarian yang membuat penyembunyian ini aman: tak ada field tersembunyi
 * yang bisa melahirkan pesan di [SpkItemDraft.issues].** Mematikan pemicu
 * SELALU mengosongkan isian bloknya (kredit → fincoy/PO, COD → metode+DP,
 * diskon → [SpkItemDraft.tanpaDiskon], KBK → broker), dan blok diskon membuka
 * dirinya sendiri begitu ada isian ([SpkItemDraft.blokDiskonTerlihat]). Tanpa
 * itu tombol Simpan bisa mati sambil menyembunyikan justru field yang harus
 * diperbaiki — kegagalan yang tak memunculkan error apa pun.
 *
 * Pemicu COD juga tetap dirender selama [SpkItemDraft.driverTerimaUang] masih
 * menyala walau metode pengirimannya bukan lagi "driver", supaya keadaan
 * nyangkut (kalau ada) bisa dimatikan sales, bukan cuma hilang dari layar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpkItemCard(
    index: Int,
    item: SpkItemDraft,
    issues: List<String>,
    serialOptions: List<SerialRegistryRow>,
    brokerResults: List<BrokerOption>,
    brokerSearch: String,
    onBrokerSearch: (String) -> Unit,
    onUpdate: (SpkItemDraft) -> Unit,
    onRemove: () -> Unit,
    onSerialFocus: () -> Unit,
    /** Watermark+upload foto PO barang ini, return URL (null = gagal). */
    uploadPoPhoto: suspend (File) -> String?,
    /** Watermark+upload foto bukti acc diskon barang ini, return URL (null = gagal). */
    uploadBuktiAcc: suspend (File, Boolean) -> AuthResult<String>,
    /** Metode pengiriman SPK (header, bukan per-barang): "driver" | "self_pickup" |
     *  "sales_delivery". COD (uang diambil driver) cuma relevan "driver" (2026-07-26). */
    deliveryMethod: String = "driver",
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            // Header: Barang #N + ringkasan + hapus + expand
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onUpdate(item.copy(expanded = !item.expanded)) }) {
                Column(Modifier.weight(1f)) {
                    Text("Barang #${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(if (item.expanded) item.namaBarang else item.summaryLine(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.expanded) Text("${item.kodeBarang} · ${item.kategori} · ${item.merk}" + (item.stokTersedia?.let { " · stok $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Daftar issue hanya dirender saat kartu terbuka, dan cuma
                    // SATU kartu boleh terbuka sekaligus — jadi tanpa baris ini
                    // pesan "Ada barang belum lengkap — cek tanda merah di
                    // kartu" menunjuk tanda merah yang tak ada di layar.
                    if (!item.expanded && issues.isNotEmpty()) {
                        Text(
                            "${issues.size} hal belum lengkap — ketuk untuk memperbaiki",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = "Hapus barang #${index + 1}", tint = MaterialTheme.colorScheme.error) }
                Icon(if (item.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (item.expanded) {
                Spacer(Modifier.height(12.dp))
                BlokLabel("Unit yang dijual")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveTextField(item.warna, { onUpdate(item.copy(warna = it)) }, label = "Warna", modifier = Modifier.weight(1f))
                    ExpressiveTextField(item.qty, { onUpdate(item.copy(qty = it.filter { c -> c.isDigit() })) }, label = "Qty" + (item.stokTersedia?.let { " (stok $it)" } ?: ""), keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                // Satu serial menandai SATU unit fisik, jadi field ini mati saat
                // qty>1. Dikunci di sini supaya sales tahu sebelum menekan Simpan;
                // server tetap penegaknya (create_delivery menolak 400).
                val qtyLebihSatu = (item.qtyInt ?: 1) > 1
                ExpressiveTextField(
                    item.serialNumber, { onUpdate(item.copy(serialNumber = it)) },
                    label = if (qtyLebihSatu) "No. Rangka/Serial — isi saat PDI" else "No. Rangka/Serial (opsional)",
                    enabled = !qtyLebihSatu,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = if (qtyLebihSatu) null else ({ BarcodeScanButton { sn -> onUpdate(item.copy(serialNumber = sn)) } })
                )
                if (qtyLebihSatu) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Qty ${item.qtyInt} = ${item.qtyInt} unit fisik dengan serial berbeda. " +
                            "Pecah jadi baris sendiri kalau serialnya mau dicatat sekarang.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // MEMPERINGATKAN, bukan memblokir (keputusan user 2026-08-09):
                // registry bisa telat diperbarui, dan unit repair yang sudah
                // selesai diperbaiki masih bertanda repair. Yang ditutup cuma
                // jalur diam-diam — lihat pengurutan di bawah.
                val terpilih = serialOptions.firstOrNull { it.serialNumber == item.serialNumber }
                if (!qtyLebihSatu && terpilih?.bermasalah == true) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Admin stok menandai unit ini ${kondisiLabel(terpilih.kondisi!!).uppercase()}" +
                            (terpilih.kondisiKeterangan?.let { " - $it" } ?: "") +
                            ". Pastikan barangnya memang siap dikirim.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                val availSerial =
                    if (qtyLebihSatu) emptyList() else serialUntukDisarankan(serialOptions, item.serialNumber)
                if (availSerial.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Serial tersedia (ketuk):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availSerial.take(5).forEach { row ->
                            Surface(onClick = { onUpdate(item.copy(serialNumber = row.serialNumber)) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    row.serialNumber + (row.kondisi?.takeIf { row.bermasalah }?.let { " \u00b7 ${kondisiLabel(it)}" } ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (row.bermasalah) MaterialTheme.colorScheme.error else Color.Unspecified,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                } else if (!qtyLebihSatu) {
                    // Trigger fetch lazy saat kartu dibuka (fail-soft, cache di VM).
                    // Dilewati saat qty>1: serialnya toh tak bisa dipakai di baris ini,
                    // jadi menariknya cuma memanggil endpoint tanpa pemakai.
                    onSerialFocus()
                }
                Spacer(Modifier.height(14.dp))
                BlokLabel("Harga yang dibayar konsumen")
                MoneyTextField(item.hargaOtr, { onUpdate(item.copy(hargaOtr = it)) }, modifier = Modifier.fillMaxWidth(), label = if (item.isCredit) "Harga OTR *" else "Harga Jual *")

                // Metode PDI (backend 2026-07-27): TAK ADA opsi melewati PDI. Toggle ini
                // cuma menentukan SIAPA yang mengerjakan — tim PDI cabang atau sales
                // sendiri. Checklist + foto wajib di dua-duanya, apa pun metode
                // pengirimannya. Sales pemilik SPK langsung diarahkan ke form PDI begitu
                // SPK selesai dibuat kalau memilih PDI Mandiri.
                //
                // Judulnya menyebut KEPUTUSANNYA ("siapa yang mengecek"), bukan istilah
                // internal "Metode PDI" — dua opsinya sudah lama bukan "PDI vs tanpa PDI".
                Spacer(Modifier.height(14.dp))
                BlokLabel("Siapa yang mengecek unit (PDI)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to "Tim PDI cabang", false to "Saya sendiri").forEach { (v, l) ->
                        val sel = item.pdiRequired == v
                        Surface(onClick = { onUpdate(item.copy(pdiRequired = v)) }, shape = RoundedCornerShape(50), color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                            Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                    }
                }
                if (!item.pdiRequired) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sales isi checklist PDI + foto unit sendiri — langsung diarahkan ke form-nya begitu SPK ini selesai dibuat. Kasir baru bisa proses setelah PDI mandiri ini lengkap.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (deliveryMethod == "self_pickup" || deliveryMethod == "sales_delivery") {
                    // Peringatan ini BUKAN mengubah keputusan (backend `is_self_pdi`
                    // tetap mengizinkan sales lanjut sendiri terlepas dari pilihan ini
                    // untuk kedua metode ini), tapi mencegah ekspektasi salah SAAT
                    // memilih: sales/admin yang menekan "Tim PDI cabang" di sini
                    // mengira ada tim lain yang akan dinotifikasi & mengerjakan —
                    // padahal untuk unit yang diambil/diantar sendiri, sales pemilik
                    // SPK-nya tetap bisa (dan kalau cabangnya tak punya staf PDI,
                    // SATU-SATUNYA yang bisa) langsung lanjut isi PDI sendiri.
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Karena metode diambil/diantar sendiri, Anda (sales) tetap bisa langsung isi PDI sendiri kapan saja — tak perlu menunggu tim PDI cabang, terutama kalau cabang ini belum punya staf PDI.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Pemicu blok opsional ─────────────────────────────────────
                // Empat blok di bawah dulu selalu terpampang. Sekarang mereka
                // menunggu diketuk di sini; keadaan mati = keadaan yang paling
                // sering benar (cash, tanpa diskon, tanpa COD, order sales).
                Spacer(Modifier.height(14.dp))
                BlokLabel("Cara bayar & tambahan")
                Text(
                    "Ketuk yang berlaku untuk barang ini. Cash, tanpa diskon, order sales sendiri? Tak perlu diketuk apa pun.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                // COD = uang diambil DRIVER — tak relevan tanpa driver (diambil
                // sendiri / sales antar sendiri). Pemicunya TETAP dirender selama
                // benderanya masih menyala: kalau suatu keadaan menyisakannya
                // menyala tanpa driver, sales masih bisa mematikannya sendiri
                // alih-alih kehilangan kendalinya dari layar.
                val codRelevan = item.driverTerimaUang || (!item.isCredit && deliveryMethod == "driver")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PemicuChip("Kredit / leasing", item.isCredit) { aktif ->
                        onUpdate(
                            if (aktif) item.copy(paymentType = "credit", driverTerimaUang = false, codPaymentMode = "", codDpAmount = "")
                            else item.copy(paymentType = "cash", fincoy = "", fincoyLain = "", preOrderId = "", poPhotoUrl = "")
                        )
                    }
                    if (codRelevan) {
                        PemicuChip("COD (uang ke driver)", item.driverTerimaUang) { aktif ->
                            onUpdate(
                                item.copy(
                                    driverTerimaUang = aktif,
                                    codPaymentMode = if (aktif) item.codPaymentMode else "",
                                    codDpAmount = if (aktif) item.codDpAmount else "",
                                )
                            )
                        }
                    }
                    PemicuChip("Diskon", item.blokDiskonTerlihat) { aktif ->
                        onUpdate(if (aktif) item.copy(diskonDibuka = true) else item.tanpaDiskon())
                    }
                    PemicuChip("Dari broker KBK", item.isKbk) { aktif ->
                        onUpdate(
                            if (aktif) item.copy(orderSource = "kbk")
                            // Komisi & no. HP ikut dikosongkan. `toItemBody` memang
                            // tak mengirimnya saat bukan KBK, tapi nilai yang
                            // tertinggal di balik pemicu mati akan muncul lagi tanpa
                            // diminta begitu pemicunya dinyalakan lagi.
                            else item.copy(orderSource = "sales", kbkBrokerKode = "", kbkBrokerNama = "", komisiKbk = "", noHpKbk = "")
                        )
                    }
                }

                // ── 1. Pembiayaan (kredit) ───────────────────────────────────
                if (item.isCredit) {
                    Spacer(Modifier.height(14.dp))
                    BlokLabel("Kredit — leasing, DP & angsuran")
                    // Leasing dulu (satu-satunya yang WAJIB di blok ini), baru nominal,
                    // baru berkas opsional — sales berhenti di baris pertama yang bisa
                    // menahan SPK-nya, bukan di baris kelima.
                    ItemFincoyDropdown(item.fincoy) { onUpdate(item.copy(fincoy = it)) }
                    if (item.fincoy == FINCOY_LAINNYA) {
                        Spacer(Modifier.height(8.dp))
                        ExpressiveTextField(item.fincoyLain, { onUpdate(item.copy(fincoyLain = it)) }, label = "Nama fincoy/leasing lain *", modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoneyTextField(item.dpNet, { onUpdate(item.copy(dpNet = it)) }, modifier = Modifier.weight(1f), label = "DP Net")
                        MoneyTextField(item.pembayaran1, { onUpdate(item.copy(pembayaran1 = it)) }, modifier = Modifier.weight(1f), label = "Pembayaran 1")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoneyTextField(item.angsuran, { onUpdate(item.copy(angsuran = it)) }, modifier = Modifier.weight(1f), label = "Angsuran")
                        ExpressiveTextField(item.tenor, { onUpdate(item.copy(tenor = it.filter { c -> c.isDigit() })) }, label = "Tenor (bln)", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    }
                    // Pre Order ID + foto PO cuma relevan buat kredit (leasing/fincoy
                    // butuh bukti PO), tetap opsional (koreksi 2026-07-26 — sebelumnya
                    // tampil unconditional buat cash & kredit).
                    Spacer(Modifier.height(10.dp))
                    ExpressiveTextField(item.preOrderId, { onUpdate(item.copy(preOrderId = it)) }, label = "No PO (opsional)", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    PoPhotoField(poPhotoUrl = item.poPhotoUrl, onUploaded = { url -> onUpdate(item.copy(poPhotoUrl = url)) }, uploadPoPhoto = uploadPoPhoto)
                }

                // ── 2. COD (2026-07-25, cash-only) ───────────────────────────
                // Mirror web SalesDeliveryFlowPage. driverTerimaNominal DIHITUNG backend
                // dari hargaOtr+codPaymentMode+codDpAmount, bukan lagi input manual
                // (cegah mismatch DP vs sisa). Checkbox lamanya digantikan pemicu chip
                // di atas — dua saklar untuk satu bendera cuma bikin ragu.
                if (item.driverTerimaUang) {
                    Spacer(Modifier.height(14.dp))
                    BlokLabel("COD — uang diambil driver saat kirim")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("full" to "Full Payment", "dp" to "DP").forEach { (k, l) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onUpdate(item.copy(codPaymentMode = k)) }
                            ) {
                                RadioButton(selected = item.codPaymentMode == k, onClick = { onUpdate(item.copy(codPaymentMode = k)) })
                                Text(l, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (item.codPaymentMode == "dp") {
                        Spacer(Modifier.height(6.dp))
                        MoneyTextField(item.codDpAmount, { onUpdate(item.copy(codDpAmount = it)) }, modifier = Modifier.fillMaxWidth(), label = "Jumlah DP *")
                        Spacer(Modifier.height(4.dp))
                        val otr = item.hargaOtr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                        val dp = item.codDpAmount.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                        Text(
                            "Sisa diambil driver: ${formatRupiahSimple((otr - dp).coerceAtLeast(0.0))}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (item.codPaymentMode == "full") {
                        Spacer(Modifier.height(4.dp))
                        val otr = item.hargaOtr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                        Text(
                            "Sisa diambil driver: ${formatRupiahSimple(otr)} (penuh)",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── 3. Diskon ────────────────────────────────────────────────
                // Terbuka lewat pemicu, TAPI juga terbuka sendiri selama ada isian
                // ([SpkItemDraft.blokDiskonTerlihat]) — "Alasan diskon wajib" tak boleh
                // pernah menunjuk field yang tak ada di layar.
                if (item.blokDiskonTerlihat) {
                    Spacer(Modifier.height(14.dp))
                    BlokLabel("Diskon barang ini")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoneyTextField(item.diskon, { onUpdate(item.copy(diskon = it)) }, modifier = Modifier.weight(1f), label = "Diskon")
                        ExpressiveTextField(item.alasanDiskon, { onUpdate(item.copy(alasanDiskon = it)) }, label = if ((item.diskon.toLongOrNull() ?: 0L) > 0) "Alasan diskon *" else "Alasan diskon", modifier = Modifier.weight(1f))
                    }
                    if ((item.diskon.toLongOrNull() ?: 0L) > 0) {
                        Spacer(Modifier.height(8.dp))
                        AccDiskonField(
                            accDiskon = item.accDiskon,
                            onAccChange = { onUpdate(item.copy(accDiskon = it)) },
                        )
                        Spacer(Modifier.height(8.dp))
                        BuktiAccField(
                            buktiUrl = item.buktiDiskonUrl,
                            // Wajib begitu nama pemberi acc disebut — lihat
                            // `SpkItemDraft.issues()` (cerminan guard server).
                            wajib = item.accDiskon.isNotBlank(),
                            onUploaded = { onUpdate(item.copy(buktiDiskonUrl = it)) },
                            uploadBukti = uploadBuktiAcc,
                        )
                    }
                }

                // ── 4. KBK / broker ──────────────────────────────────────────
                // Isian "Komisi Sales" dibuang 2026-08-03 (permintaan user);
                // blok ini tinggal milik broker KBK.
                if (item.isKbk) {
                    Spacer(Modifier.height(14.dp))
                    BlokLabel("Broker KBK & komisinya")
                    if (item.kbkBrokerKode.isBlank()) {
                        ExpressiveTextField(brokerSearch, onBrokerSearch, label = "Cari broker KBK (min. 2 karakter) *", modifier = Modifier.fillMaxWidth())
                        if (brokerResults.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                brokerResults.forEach { b ->
                                    Surface(onClick = { onUpdate(item.copy(kbkBrokerKode = b.kode, kbkBrokerNama = b.nama)); onBrokerSearch("") }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text(b.nama, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(b.kode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.kbkBrokerNama.ifBlank { item.kbkBrokerKode }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text(item.kbkBrokerKode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onUpdate(item.copy(kbkBrokerKode = "", kbkBrokerNama = "")) }) { Text("Ganti") }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoneyTextField(item.komisiKbk, { onUpdate(item.copy(komisiKbk = it)) }, modifier = Modifier.weight(1f), label = "Komisi KBK")
                        ExpressiveTextField(item.noHpKbk, { onUpdate(item.copy(noHpKbk = it)) }, label = "No. HP KBK", keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f))
                    }
                }

                if (issues.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        issues.forEach { Text("• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                }
            }
        }
    }
}

private fun formatRupiahSimple(value: Double): String =
    "Rp" + value.toLong().toString().reversed().chunked(3).joinToString(".").reversed()

/**
 * Judul kelompok di dalam kartu barang.
 *
 * Sengaja SATU composable, bukan `Text(...)` yang ditulis ulang tiap kelompok:
 * kartunya kini punya enam kelompok dan gaya yang menyimpang di salah satunya
 * membuat kelompok itu terbaca sebagai jenis isian yang berbeda.
 */
@Composable
private fun BlokLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * Pemicu satu blok opsional (kredit / COD / diskon / KBK).
 *
 * [onToggle] menerima keadaan BARU, bukan sekadar "diketuk" — pemanggilnya
 * selalu punya dua cabang yang berbeda (menyalakan vs mengosongkan isian blok),
 * dan menyerahkan pembalikan bendera ke pemanggil adalah cara paling mudah
 * menulis chip yang menyala tapi tak pernah membersihkan apa pun.
 */
@Composable
private fun PemicuChip(label: String, aktif: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = aktif,
        onClick = { onToggle(!aktif) },
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/** Foto PO per-barang: capture kamera → watermark → upload langsung (bukan
 *  slot review terpisah spt PDI/deliver — pola sama web `uploadDeliveryPhoto`
 *  on-file-select, cukup 1x aksi). File cache per-composable-instance (aman
 *  walau ada beberapa kartu barang sekaligus di layar). */
@Composable
private fun PoPhotoField(
    poPhotoUrl: String,
    onUploaded: (String) -> Unit,
    uploadPoPhoto: suspend (File) -> String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val file = remember { File(context.cacheDir, "delivery/po_item_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        uploading = true
        error = null
        scope.launch {
            val url = uploadPoPhoto(file)
            uploading = false
            if (url != null) onUploaded(url) else error = "Gagal unggah foto PO"
        }
    }

    Text("Foto PO (opsional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    if (poPhotoUrl.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Foto PO terunggah", style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = { onUploaded("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus foto PO") }
        }
    } else {
        Surface(
            onClick = { if (!uploading) cam.launch(uri) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (uploading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Mengunggah…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ambil / unggah foto PO", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (error != null) {
        Spacer(Modifier.height(4.dp))
        Text(error!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Saran nama pemberi acc diskon — cerminan `SARAN_ACC_DISKON` di web
 *  (`SalesDeliveryFlowPage.tsx`). Dua nama, teks tetap bebas diketik. */
private val SARAN_ACC_DISKON = listOf("Setiawan Widjaya", "Feby")

/** "Acc oleh" — teks bebas + chip saran. Compose tak punya padanan
 *  `<datalist>`; chip menekankan pilihan umum tanpa mengunci pilihan. */
@Composable
private fun AccDiskonField(accDiskon: String, onAccChange: (String) -> Unit) {
    ExpressiveTextField(
        accDiskon,
        onAccChange,
        label = "Acc oleh (opsional)",
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SARAN_ACC_DISKON.forEach { nama ->
            AssistChip(onClick = { onAccChange(nama) }, label = { Text(nama) })
        }
    }
}

/** Bukti acc: kamera ATAU galeri. Keduanya bermuara ke berkas cache yang
 *  SAMA supaya jalur unggahnya (watermark + POST) cuma satu, bukan dua yang
 *  bisa menyimpang diam-diam.
 *
 *  Picker-nya `PickVisualMedia` (Photo Picker) dan **tidak butuh izin apa
 *  pun** — jangan ditambal `READ_MEDIA_*`. Sampai 2026-08-28 baris ini
 *  memakai `GetContent()` sambil mengklaim dirinya Photo Picker; itu keliru
 *  (`GetContent()` = `ACTION_GET_CONTENT`, pemilih dokumen lama) dan
 *  menghasilkan URI yang tak selalu bisa dibuka. Lihat catatan lengkap di
 *  `KuponGebyarScreen.kt`. */
@Composable
private fun BuktiAccField(
    buktiUrl: String,
    wajib: Boolean,
    onUploaded: (String) -> Unit,
    uploadBukti: suspend (File, Boolean) -> AuthResult<String>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val file = remember {
        File(context.cacheDir, "delivery/acc_diskon_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
    }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }

    // `dariGaleri` menentukan KALIMAT saat fotonya gagal didekode — bukan
    // sekadar label. Foto galeri yang tak terdekode di HP Android 7/8 (HEIC
    // yang masuk dari luar) akan gagal lagi setiap kali, jadi "jepret ulang"
    // menyuruh mengulang hal yang mustahil; jalur kamera justru sebaliknya.
    fun unggah(dariGaleri: Boolean) {
        uploading = true
        error = null
        scope.launch {
            when (val hasil = uploadBukti(file, dariGaleri)) {
                is AuthResult.Success -> {
                    uploading = false
                    onUploaded(hasil.data)
                }
                is AuthResult.Failure -> {
                    uploading = false
                    error = hasil.message
                }
            }
        }
    }

    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) unggah(dariGaleri = false)
    }
    val galeri = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        // Sebab kegagalan dibawa sampai ke layar — `.isSuccess` dulu
        // membuangnya, sehingga laporan lapangan tak bisa didiagnosa.
        // `openInputStream` sendiri bisa melempar — WAJIB di dalam `runCatching`.
        runCatching {
            val masuk = context.contentResolver.openInputStream(picked)
                ?: error("galeri tak memberi isi berkas")
            masuk.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }.fold(
            onSuccess = { unggah(dariGaleri = true) },
            onFailure = { e ->
                error = "Foto itu tidak bisa dibaca (${e.javaClass.simpleName}). " +
                    "Kalau masih di cloud dan belum terunduh, buka dulu di galeri atau pakai Kamera."
            },
        )
    }

    Text(
        if (wajib) "Bukti acc *" else "Bukti acc (opsional)",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    if (buktiUrl.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bukti acc terunggah", style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = { onUploaded("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus bukti acc") }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveOutlinedButton(onClick = { if (!uploading) cam.launch(uri) }, enabled = !uploading, modifier = Modifier.weight(1f)) {
                Text(if (uploading) "Mengunggah…" else "Kamera")
            }
            ExpressiveOutlinedButton(onClick = { if (!uploading) galeri.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !uploading, modifier = Modifier.weight(1f)) {
                Text("Galeri")
            }
        }
    }
    error?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Dropdown fincoy per-item (pola CabangSelector). */
@Composable
private fun ItemFincoyDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) { "" -> "Pilih leasing…"; FINCOY_LAINNYA -> "Lainnya…"; else -> selected }
    Column {
        Text("Fincoy / Leasing *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)).clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                FINCOY_PARTNERS.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { onSelect(p); expanded = false }) }
                DropdownMenuItem(text = { Text("Lainnya…") }, onClick = { onSelect(FINCOY_LAINNYA); expanded = false })
            }
        }
    }
}

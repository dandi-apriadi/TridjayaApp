package com.krisoft.tridjayaelektronik.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.HomeRepairService
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.ElectricBike
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.PriceChange
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.LeadSummary
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.ui.activity.akunUji
import com.krisoft.tridjayaelektronik.ui.event.EventCarousel
import com.krisoft.tridjayaelektronik.ui.event.EventViewModel
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenEntity
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.ui.sales.KlasemenRowCard
import com.krisoft.tridjayaelektronik.ui.sales.KlasemenViewModel
import com.krisoft.tridjayaelektronik.ui.notifications.NotificationCenterViewModel
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonBox
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonLine
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

@Composable
fun HomeScreen(
    onViewMoreBranches: () -> Unit = {},
    onViewMoreSales: () -> Unit = {},
    onBranchClick: (LeaderboardBranchItemDto) -> Unit = {},
    onSalesClick: (LeaderboardSalesItemDto) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onQuickAccessInventory: () -> Unit = {},
    onQuickAccessSearch: () -> Unit = {},
    onQuickAccessLeads: () -> Unit = {},
    onQuickAccessIndent: () -> Unit = {},
    onQuickAccessSales: () -> Unit = {},
    onQuickAccessOpname: () -> Unit = {},
    onQuickAccessAbsen: () -> Unit = {},
    onQuickAccessGaji: () -> Unit = {},
    onQuickAccessKpi: () -> Unit = {},
    onQuickAccessHargaGs: () -> Unit = {},
    onQuickAccessSerialInput: () -> Unit = {},
    onQuickAccessGodaSerial: () -> Unit = {},
    onQuickAccessDeadstock: () -> Unit = {},
    onQuickAccessMutasiHistori: () -> Unit = {},
    onKomplainLapor: () -> Unit = {},
    onKomplainSaya: () -> Unit = {},
    onKomplainTugas: () -> Unit = {},
    onPemasanganAcKontrol: () -> Unit = {},
    onVertel: () -> Unit = {},
    onKlasemenLapangan: () -> Unit = {},
    /** Buka satu menu alur SPK berdasarkan key: input/diskon/kasir/pdi/kontrol/driver. */
    onSpkMenu: (String) -> Unit = {},
    /** Buka layar isi prospek untuk satu event (id-nya). */
    onOpenEvent: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    // Instance terpisah dari layar isi prospek (pola sama NotificationCenterViewModel di
    // bawah) — di sini cuma dibaca daftar event-nya, form-nya tak pernah tersentuh.
    val eventViewModel: EventViewModel = hiltViewModel()
    val eventState by eventViewModel.uiState.collectAsState()
    // Ambil ULANG tiap kali layar ini masuk komposisi (kembali dari route anak, atau pindah
    // tab ke Operasional lagi) — bukan sekali di `init` VM. VM-nya hidup selama back stack
    // tab ini hidup, jadi `init` = sekali seumur proses: sekali gagal saat wifi bazar putus,
    // kartunya tak akan pernah muncul lagi, dan tak ada tile Akses Cepat sebagai pintu kedua.
    LaunchedEffect(Unit) { eventViewModel.muat() }
    // Content scrolls behind the floating nav; clear it (pill ≈ 88dp) plus the system nav-bar inset.
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

    // Badge unread — instance terpisah dari layar Notifikasi; fetch on-entry (bukan polling), dan
    // lagi begitu Home kembali tampil setelah pop dari layar itu (LaunchedEffect re-run tiap kali
    // composable ini masuk komposisi baru, termasuk saat kembali dari navigasi).
    val notifViewModel: NotificationCenterViewModel = hiltViewModel()
    val notifState by notifViewModel.state.collectAsState()
    LaunchedEffect(Unit) { notifViewModel.refreshUnreadCount() }

    // Instance yang SAMA dengan yang dipakai `HomeKlasemenCard` (hiltViewModel() tanpa key →
    // satu instance per ViewModelStoreOwner), cuma di-resolve di sini supaya tarik-turun bisa
    // ikut menyegarkan widget Klasemen — tanpa itu, refresh cuma menyegarkan separuh layar.
    val klasemenViewModel: KlasemenViewModel = hiltViewModel()

    TridjayaCollapsibleHeader(
        title = "Tridjaya App",
        actions = {
            ExpressiveFilledIconButton(onClick = onOpenNotifications) {
                BadgedBox(badge = {
                    if (notifState.unreadCount > 0) {
                        Badge { Text(if (notifState.unreadCount > 99) "99+" else "${notifState.unreadCount}") }
                    }
                }) {
                    Icon(Icons.Rounded.Notifications, contentDescription = "Notifikasi")
                }
            }
        }
    ) { contentModifier ->
        Box(modifier = contentModifier) {
            TridjayaPullRefresh(
                isRefreshing = state.isLoading && state.user != null,
                // Layar ini menampung EMPAT sumber data dari ViewModel berbeda; `loadDashboard`
                // saja cuma menyegarkan seksi KPI/target/CRM, sisanya tetap basi.
                onRefresh = {
                    viewModel.loadDashboard(forceRefresh = true)
                    eventViewModel.muat()
                    notifViewModel.refreshUnreadCount()
                    klasemenViewModel.load(forceRefresh = true)
                }
            ) {
                when {
                    state.isLoading && state.user == null -> HomeLoadingSkeleton()
                    state.errorMessage != null && state.kpi == null && state.target == null -> {
                        ScrollableCenter {
                            ExpressiveErrorState(
                                message = state.errorMessage ?: "Tidak bisa memuat dashboard.",
                                onRetry = { viewModel.loadDashboard(forceRefresh = true) }
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomClearance),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Banner izin notifikasi kini di ActivityScreen (layar pertama app,
                            // Task B6) — dicabut dari sini supaya tak dobel di tab Operasional.
                            // Kartu sapaan PINDAH ke `ui/activity/GreetingCard.kt` (layar
                            // pertama app) — slot ini kini murni milik kartu event, dan cuma
                            // muncul kalau server bilang user ini sales (`bolehIsi`) DAN ada
                            // event aktif. Daftar kosong menampung SEMUA keadaan lain
                            // sekaligus: jaringan mati, respons tak terbaca, bukan sales, tak
                            // ada event — tak ada yang dirender, dashboard langsung mulai dari
                            // seksinya. Layar utama tak boleh mati karena fitur ini.
                            val kartuEvent = eventState.kartuEvent
                            if (kartuEvent.isNotEmpty()) {
                                item {
                                    EventCarousel(events = kartuEvent, onOpen = { onOpenEvent(it.id) })
                                }
                            }
                            // Tiap bagian dirender sebagai kartu selebar layar berjudul (gaya sama
                            // dengan daftar klasemen), dalam urutan tetap [HomeSection.DEFAULT_ORDER].
                            HomeSection.DEFAULT_ORDER.forEach { section ->
                                homeSection(
                                    section, state, onViewMoreBranches, onViewMoreSales, onBranchClick, onSalesClick,
                                    onQuickAccessInventory, onQuickAccessSearch, onQuickAccessLeads, onQuickAccessIndent, onQuickAccessSales,
                                    onQuickAccessOpname, onQuickAccessAbsen, onQuickAccessGaji, onQuickAccessKpi,
                                    onQuickAccessHargaGs,
                                    onQuickAccessSerialInput, onQuickAccessGodaSerial,
                                    onQuickAccessDeadstock, onQuickAccessMutasiHistori,
                                    onKomplainLapor, onKomplainSaya, onKomplainTugas,
                                    onPemasanganAcKontrol, onVertel, onKlasemenLapangan,
                                    onSpkMenu
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Emits one dashboard section: a titled header + its full-width content card. */
private fun LazyListScope.homeSection(
    section: HomeSection,
    state: HomeUiState,
    onViewMoreBranches: () -> Unit,
    onViewMoreSales: () -> Unit,
    onBranchClick: (LeaderboardBranchItemDto) -> Unit,
    onSalesClick: (LeaderboardSalesItemDto) -> Unit,
    onQuickAccessInventory: () -> Unit,
    onQuickAccessSearch: () -> Unit,
    onQuickAccessLeads: () -> Unit,
    onQuickAccessIndent: () -> Unit,
    onQuickAccessSales: () -> Unit,
    onQuickAccessOpname: () -> Unit,
    onQuickAccessAbsen: () -> Unit,
    onQuickAccessGaji: () -> Unit,
    onQuickAccessKpi: () -> Unit,
    onQuickAccessHargaGs: () -> Unit,
    onQuickAccessSerialInput: () -> Unit,
    onQuickAccessGodaSerial: () -> Unit,
    onQuickAccessDeadstock: () -> Unit,
    onQuickAccessMutasiHistori: () -> Unit,
    onKomplainLapor: () -> Unit,
    onKomplainSaya: () -> Unit,
    onKomplainTugas: () -> Unit,
    onPemasanganAcKontrol: () -> Unit,
    onVertel: () -> Unit,
    onKlasemenLapangan: () -> Unit,
    onSpkMenu: (String) -> Unit
) {
    when (section) {
        HomeSection.QUICK_ACCESS -> {
            item { SectionHeader(title = "Akses Cepat", icon = Icons.Rounded.Bolt) }
            item {
                // Hak tiap menu dinyatakan di REGISTRI (`QuickAccessMenus.kt`),
                // dinilai dari role EFEKTIF (role utama + roles + divisi) — sama
                // dengan yang dipakai backend saat memutuskan 200/403.
                QuickAccessRow(
                    effectiveRoles = effectiveRoles(state.user),
                    capabilities = state.capabilities,
                    // Vonis akun uji dihitung dari profil yang SAMA dengan yang
                    // memasok `effectiveRoles` di atas (`state.user`), jadi
                    // keduanya tak bisa saling mendahului: profil belum termuat
                    // = `null` = role kosong DAN bukan akun uji. Predikatnya
                    // `akunUji` dari `ActivityRegistry.kt` — DIPAKAI ULANG, bukan
                    // ditulis tandingannya (2026-08-15, jalan masuk KPI).
                    akunUji = akunUji(state.user?.name, state.user?.nik),
                    onInventory = onQuickAccessInventory,
                    onCariSemua = onQuickAccessSearch,
                    onLeads = onQuickAccessLeads,
                    onIndent = onQuickAccessIndent,
                    onSales = onQuickAccessSales,
                    onOpname = onQuickAccessOpname,
                    onAbsen = onQuickAccessAbsen,
                    onGaji = onQuickAccessGaji,
                    onKpi = onQuickAccessKpi,
                    onHargaGs = onQuickAccessHargaGs,
                    onSerialInput = onQuickAccessSerialInput,
                    onGodaSerial = onQuickAccessGodaSerial,
                    onDeadstock = onQuickAccessDeadstock,
                    onMutasiHistori = onQuickAccessMutasiHistori,
                    onKomplainLapor = onKomplainLapor,
                    onKomplainSaya = onKomplainSaya,
                    onKomplainTugas = onKomplainTugas,
                    onPemasanganAcKontrol = onPemasanganAcKontrol,
                    onVertel = onVertel,
                    onKlasemenLapangan = onKlasemenLapangan,
                    onSpkMenu = onSpkMenu,
                )
            }
        }
        HomeSection.CRM_SUMMARY -> {
            // Angkanya dihitung dari cache lead lokal, yang diisi `GET /crm/leads`.
            // Role tanpa akses CRM tak pernah punya isi cache itu → kartu selalu
            // nol dan menyesatkan. Sembunyikan, sejalan dgn tile CRM di atas.
            //
            // AWAS — syarat ini sekarang SELALU `false`: tak ada lagi entri
            // ber-`id = "crm"` di [QUICK_ACCESS_MENUS]. CRM dilepas dari grid
            // 2026-07-28 saat ia naik jadi kartu di layar Activity (lihat
            // komentar di atas `QUICK_ACCESS_MENUS`), dan syarat ini ikut mati
            // diam-diam bersamanya, jadi seksi "Ringkasan CRM" tak pernah
            // dirender walau masih terdaftar di `HomeLayout.DEFAULT_ORDER`.
            // Itu keadaan yang SUDAH ADA sebelum perubahan akun-uji 2026-08-15
            // dan sengaja tidak diperbaiki di sini — "apakah Ringkasan CRM masih
            // diinginkan?" itu keputusan produk, bukan rapi-rapi kode.
            // `akunUji` tetap dioper supaya kedua pemanggil
            // `visibleQuickAccessMenus` di berkas ini menilai dengan masukan yang
            // sama; hari ini efeknya nol, karena `"crm"` juga tak ada di
            // [MENU_TAMBAHAN_AKUN_UJI].
            if (
                visibleQuickAccessMenus(
                    effectiveRoles(state.user),
                    state.capabilities,
                    akunUji(state.user?.name, state.user?.nik),
                ).any { it.id == "crm" }
            ) {
                item { SectionHeader(title = "Ringkasan CRM", icon = Icons.Rounded.Groups) }
                item { CrmCard(summary = state.crmSummary) }
            }
        }
        HomeSection.LEADERBOARD -> {
            item { SectionHeader(title = "Klasemen", icon = Icons.Rounded.EmojiEvents) }
            item { HomeKlasemenCard(onOpenSales = onQuickAccessSales) }
        }
    }
}

/**
 * Widget Klasemen di Home — memakai data & gaya yang sama persis dengan layar Sales
 * ([KlasemenViewModel] + [KlasemenRowCard]): kartu-per-baris, medali 🥇🥈🥉, dan
 * MovementBadge (naik/turun/BARU). Default periode = **kemarin**; metrik otomatis
 * mengikuti entity (Sales → unit, Cabang → omset), sama seperti web /dashboard/klasemen.
 * Top 5 saja; "Lihat semua" membuka layar Sales lengkap.
 */
@Composable
private fun HomeKlasemenCard(onOpenSales: () -> Unit) {
    val vm: KlasemenViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()

    // Default klasemen di Home = hari kemarin (layar Sales tetap default hari ini — VM terpisah per entry).
    LaunchedEffect(Unit) {
        val kemarin = KlasemenStandings.shiftDays(KlasemenStandings.todayIso(), -1)
        if (state.cutoffIso != kemarin) vm.setCutoff(kemarin)
    }

    val isSales = state.entity == KlasemenEntity.SALES
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LeaderboardTab("Sales", isSales, Modifier.weight(1f)) { vm.setEntity(KlasemenEntity.SALES) }
            LeaderboardTab("Cabang", !isSales, Modifier.weight(1f)) { vm.setEntity(KlasemenEntity.CABANG) }
        }

        Text(
            text = if (isSales) "Peringkat sales (unit) · kemarin" else "Peringkat cabang (omset) · kemarin",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        when {
            state.isLoading && state.standings.isEmpty() -> repeat(3) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp)
                )
            }
            state.errorMessage != null && state.standings.isEmpty() ->
                EmptyRankRow(state.errorMessage ?: "Gagal memuat klasemen")
            state.standings.isEmpty() -> EmptyRankRow("Belum ada data klasemen kemarin")
            else -> state.standings.take(5).forEach { row -> KlasemenRowCard(row, state.metric) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lihat semua",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenSales() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun LeaderboardTab(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyRankRow(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
        textAlign = TextAlign.Center
    )
}

/**
 * Role gates for the quick-access menus — mirrors the backend gateway's route guards so the
 * user never sees a menu that would only answer 403:
 * - Indent (`require_indent_submitter` GET): admin, owner, indent-approver, manager, kepala-cabang.
 * - Opname (service `authorize_view`): admin, admin-stok, kepala-cabang, manager, owner.
 *   `has_admin` TIDAK pernah dipakai `authorize_view`; dan sejak 2026-08-21 guard itu juga
 *   meloloskan admin/superadmin LINTAS CABANG lewat `has_admin_platform` — sebelumnya ubin ini
 *   tampil untuk superadmin (capability `opname.view` memuatnya) lalu server menolak tiap sesi
 *   cabang lain, yaitu properti "never sees a menu that would only answer 403" yang dijanjikan
 *   KDoc ini justru bohong untuk role itu.
 * Inventory/Prospek/Sales are open to every logged-in role. A null role (profile not loaded
 * yet) hides the gated tiles — they appear as soon as the cached profile lands.
 */
internal val INDENT_MENU_ROLES = setOf("admin", "superadmin", "kepala-cabang", "manager")
internal val OPNAME_MENU_ROLES = setOf("admin", "admin-stok", "kepala-cabang", "manager", "owner")

/** `require_price_changes_reader` gateway guard (baca tanpa `force`) — lihat gateway/src/lib.rs. */
internal val HARGA_GS_MENU_ROLES = setOf("admin", "manager", "owner", "kepala-cabang", "karyawan")

/** `is_admin_stok_role` di `serials.rs` — POST /inventory/serial-numbers hanya role ini. */
internal val SERIAL_INPUT_MENU_ROLES = setOf("admin-stok")

/**
 * Cerminan `GODA_SERIAL_ADD_ROLES` (rust-shared `capabilities.rs`, dipisah dari
 * `GODA_SERIAL_EDIT_ROLES` 2026-09-03) — PENAMBAH registry SN GODA, bukan
 * pembacanya maupun penulis-ganti.
 *
 * Sengaja BUKAN `GODA_VIEW_ROLES` yang lebih luas (manager/owner ikut di
 * sana, tapi TIDAK boleh menambah/mengganti). Menu mobile ini satu-satunya
 * isinya adalah MENAMBAH SN (`POST`, bukan `PUT` — lihat `ui/goda/`), jadi
 * daftar pembaca akan membuka layar yang tombol simpannya dijawab 403 — persis
 * "menu mati" yang CLAUDE.md repo ini ingin cegah. Gateway sendiri tetap
 * meloloskan pembaca (`require_goda_access` = `GODA_VIEW_ROLES`); penyempitan
 * untuk penulisan terjadi di service, dan cadangan offline ini mencerminkan
 * lapis yang menolak, bukan lapis yang meloloskan.
 *
 * `kepala-cabang`/`admin-penjualan`/`kasir` ikut sejak 2026-09-03 (permintaan
 * user membuka akses TAMBAH SN, web DAN mobile — dua sisi menu yang sama):
 * ketiganya boleh menambah SN yang belum terdaftar dari HP saat unit diterima
 * cabang, TAPI TIDAK ikut `GODA_SERIAL_EDIT_ROLES` (mengganti SN yang sudah
 * ada tetap admin-stok/admin/superadmin/staf-gudang saja — registry tanpa
 * tabel riwayat, keputusan eksplisit user saat ditanya).
 */
internal val GODA_SERIAL_MENU_ROLES = setOf(
    "admin-stok",
    "admin",
    "superadmin",
    "staf-gudang",
    "kepala-cabang",
    "admin-penjualan",
    "kasir",
)

/** `is_cabang_role` di `deadstock/mod.rs` (dealer dipaksa backend, anti-IDOR) — manager
 *  punya mode terpisah (monitoring+audit, web-only) jadi tidak termasuk di sini. */
internal val DEADSTOCK_MENU_ROLES = setOf("karyawan", "kepala-cabang", "admin-stok")

/** Endpoint mutasi-histori TIDAK di-gate role server-side — RoleGuard halaman web
 *  (`InventoryMutasiPage.tsx`, roles=["admin","admin-stok"]) direplikasi di sini. */
internal val MUTASI_HISTORI_MENU_ROLES = setOf("admin", "admin-stok")

/** Semua gate menu memakai role EFEKTIF (role utama + roles + divisi), BUKAN
 *  role utama saja — backend juga menilai dari daftar itu. Dulu role utama saja:
 *  pemegang `indent-approver` (implied dari page-grant) dan karyawan ber-divisi
 *  `admin-stok` kehilangan menu yang sebenarnya boleh mereka pakai. */
internal fun canAccessIndent(roles: Set<String>): Boolean =
    roles.any { it in INDENT_MENU_ROLES }

internal fun canAccessOpname(roles: Set<String>): Boolean =
    roles.any { it in OPNAME_MENU_ROLES }

internal fun canAccessHargaGs(roles: Set<String>): Boolean =
    roles.any { it in HARGA_GS_MENU_ROLES }

internal fun canAccessSerialInput(roles: Set<String>): Boolean =
    roles.any { it in SERIAL_INPUT_MENU_ROLES }

internal fun canAccessDeadstock(roles: Set<String>): Boolean =
    roles.any { it in DEADSTOCK_MENU_ROLES }

internal fun canAccessMutasiHistori(roles: Set<String>): Boolean =
    roles.any { it in MUTASI_HISTORI_MENU_ROLES }

/** `STAFF_ROLES` di kinerja-service (`absensi.rs`) — dipakai absensi DAN slip
 *  gaji (`VIEW_OWN_ROLES = STAFF_ROLES`). `crm-manager`/`ai-engineer` TIDAK ada
 *  di sana, jadi dua menu itu 403 untuk mereka.
 *
 *  Cerminan `STAFF_SELF_SERVICE_ROLES` (rust-shared `capabilities.rs`).
 *  **`trainee` ditambahkan 2026-08-28** — ia sudah ada di sisi Rust sejak role
 *  itu lahir (17 Agt, paket 3.18d) dengan alasan yang ditulis eksplisit di sana:
 *  trainee WAJIB bisa absen di hari pertamanya, dan `attendance/report.rs`
 *  menyaring `LOWER(u.role) IN (...)` dari daftar yang sama. Cerminan di sini
 *  tertinggal, jadi trainee yang membuka app saat peta kemampuan belum termuat
 *  (sinyal lemah, bukan cuma mode pesawat) kehilangan kartu Absen & Slip Gaji —
 *  padahal server mengizinkannya. */
internal val STAFF_MENU_ROLES = setOf(
    "karyawan", "trainee", "kepala-cabang", "admin-sales", "sales", "pdi", "driver", "kasir",
    "delivery-control", "admin-stok", "operator", "agent", "hrd", "manager", "admin",
    "superadmin", "owner",
)

internal fun canAccessStaffSelfService(roles: Set<String>): Boolean =
    roles.any { it in STAFF_MENU_ROLES }

/** `MOBILE_LEADERBOARD_ROLES` gateway — lihat gateway/src/lib.rs. */
internal val KLASEMEN_MENU_ROLES = setOf(
    "manager", "sales-manager", "kepala-cabang", "admin", "superadmin", "owner",
    "karyawan", "agent", "operator", "admin-sales",
)

internal fun canAccessKlasemen(roles: Set<String>): Boolean =
    roles.any { it in KLASEMEN_MENU_ROLES }

/** `is_pipeline_actor` di `delivery.rs`: semua role KECUALI ai-engineer murni
 *  (dan aktor tanpa role sama sekali). */
internal fun canAccessSpk(roles: Set<String>): Boolean =
    roles.any { it in setOf("admin", "superadmin", "manager") } ||
        (roles.isNotEmpty() && roles != setOf("ai-engineer"))

/** Siapa yang benar-benar dilayani `crm-service`: `karyawan` & `kepala-cabang`
 *  (input + lead miliknya sendiri, lewat `karyawan_scope`) dan
 *  `crm-manager`/admin (`CRM_FULL`). Manager, owner, ai-engineer dapat 403 di
 *  `/crm/leads`.
 *
 *  `kepala-cabang` masuk 2026-07-29 mengikuti `CRM_INPUT_ROLES` di rust-shared
 *  (laporan user: kepala cabang ber-`is_sales` punya target prospek harian tapi
 *  tak punya menu untuk mengisinya). Daftar ini cuma CADANGAN saat peta
 *  `/api/me/capabilities` belum termuat — lihat `gateAllows`; saat online
 *  server yang memutuskan, jadi perbaikan backend berlaku tanpa APK baru.
 *
 *  **`trainee` DICABUT 2026-08-31 (keputusan user), setelah sempat ditambahkan
 *  2026-08-28.** Alasan penambahan dulu — "prospek harian adalah SATU-SATUNYA
 *  pekerjaan trainee yang menghasilkan angka" — sudah tidak berlaku: masa
 *  training dipersempit ke Data Inventory + Aktivitas Harian + Pengaturan Akun,
 *  dan `CRM_INPUT_ROLES` di rust-shared ikut mencabutnya di paket yang sama.
 *  Konsekuensi yang diukur, bukan diasumsikan: scorecard training kehilangan
 *  sumber `prospek_rata_rata`/`closing_terverifikasi`, jadi keduanya dilaporkan
 *  "tidak diukur" — BUKAN angka 0 — supaya vonis kelulusan tidak melawan sinyal
 *  yang memang tak punya sumber lagi.
 *
 *  **Kenapa cadangan offline ini WAJIB ikut dicabut, bukan cukup sisi server.**
 *  Kartu "Input prospek" (`ActivityRegistry.kt`) ber-`capability = "crm.input"`,
 *  jadi saat ONLINE ia hilang sendiri begitu peta kemampuan datang. Yang tersisa
 *  adalah jendela sebelum peta itu termuat (sinyal lemah, bukan cuma mode
 *  pesawat): trainee melihat kartunya beberapa detik, mengetuknya, lalu dijawab
 *  403 — bentuk "menu mati" yang justru ingin dicegah CLAUDE.md repo ini.
 *  `CadanganRoleCerminRustTest` membaca `capabilities.rs` LANGSUNG, jadi
 *  pencabutan di satu sisi saja langsung merah. */
internal val CRM_MENU_ROLES =
    setOf("karyawan", "kepala-cabang", "crm-manager", "admin", "superadmin")

/** Role EFEKTIF: role utama + `roles` (multi-role) + `divisi` (folding
 *  divisi-driven access), semuanya lowercase. Backend menilai hak dari daftar
 *  yang sama — gate menu harus ikut, bukan cuma role utama. */
internal fun effectiveRoles(user: com.krisoft.tridjayaelektronik.data.model.UserDto?): Set<String> = buildSet {
    user?.role?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    user?.roles?.forEach { it.trim().lowercase().takeIf { s -> s.isNotEmpty() }?.let { s -> add(s) } }
    user?.divisi?.split(",")?.forEach { it.trim().lowercase().takeIf { s -> s.isNotEmpty() }?.let { s -> add(s) } }
}

internal fun canAccessCrm(effectiveRoles: Set<String>): Boolean =
    effectiveRoles.any { it in CRM_MENU_ROLES }

/**
 * Shortcut row to the app's most-used destinations. Five tiles no longer fit a fixed-width
 * phone row, so this scrolls horizontally with fixed-width tiles instead of weight-splitting.
 */
@Composable
private fun QuickAccessRow(
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>?,
    /** Tanpa nilai default — SENGAJA, dan sengaja BERBEDA dari
     *  `visibleQuickAccessMenus` yang default-nya `false`. Di sana default itu
     *  melindungi pemanggil luar yang tak tahu-menahu soal akun uji; di sini
     *  hanya ada satu pemanggil dan ia memegang `state.user`, jadi default
     *  hanya akan menyembunyikan kelalaian: menu [MENU_TAMBAHAN_AKUN_UJI] tak
     *  pernah muncul untuk akun uji, tanpa error, tanpa test yang berteriak.
     *  Biarkan kompiler yang menagih. */
    akunUji: Boolean,
    onInventory: () -> Unit,
    onCariSemua: () -> Unit,
    onLeads: () -> Unit,
    onIndent: () -> Unit,
    onSales: () -> Unit,
    onOpname: () -> Unit,
    onAbsen: () -> Unit,
    onGaji: () -> Unit,
    onKpi: () -> Unit,
    onHargaGs: () -> Unit,
    onSerialInput: () -> Unit,
    onGodaSerial: () -> Unit,
    onDeadstock: () -> Unit,
    onMutasiHistori: () -> Unit,
    onKomplainLapor: () -> Unit,
    onKomplainSaya: () -> Unit,
    onKomplainTugas: () -> Unit,
    onPemasanganAcKontrol: () -> Unit,
    onVertel: () -> Unit,
    onKlasemenLapangan: () -> Unit,
    onSpkMenu: (String) -> Unit,
) {
    // Tile dirender dari REGISTRI (`QuickAccessMenus.kt`) — hak akses tiap menu
    // dinyatakan di sana, sekali, di sebelah guard backend yang dicerminkannya.
    // Menambah tile langsung di sini (tanpa entri registri) tidak akan tampil.
    val menus = visibleQuickAccessMenus(effectiveRoles, capabilities, akunUji)
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(224.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(menus, key = { it.id }) { menu ->
            val (icon, tint) = quickAccessVisual(menu.id)
            QuickAccessTile(
                icon = icon,
                label = menu.label,
                tint = tint,
                onClick = {
                    when (menu.id) {
                        "absen" -> onAbsen()
                        "gaji" -> onGaji()
                        "kpi" -> onKpi()
                        "spk" -> onSpkMenu("hub")
                        "pdi_queue" -> onSpkMenu("pdi")
                        "inventory" -> onInventory()
                        "cari_semua" -> onCariSemua()
                        "crm" -> onLeads()
                        "indent" -> onIndent()
                        "klasemen" -> onSales()
                        "klasemen_lapangan" -> onKlasemenLapangan()
                        "opname" -> onOpname()
                        "harga_gs" -> onHargaGs()
                        "serial_input" -> onSerialInput()
                        "goda_serial" -> onGodaSerial()
                        "deadstock" -> onDeadstock()
                        "mutasi_histori" -> onMutasiHistori()
                        "komplain_lapor" -> onKomplainLapor()
                        "komplain_saya" -> onKomplainSaya()
                        "komplain_tugas" -> onKomplainTugas()
                        "pemasangan_ac_kontrol" -> onPemasanganAcKontrol()
                        "vertel" -> onVertel()
                    }
                },
                modifier = Modifier.width(86.dp)
            )
        }
    }
}

/** Ikon + warna per menu. Dipisah dari registri supaya registri tetap murni data
 *  (bisa diuji tanpa Compose). */
@Composable
private fun quickAccessVisual(id: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when (id) {
    "absen" -> Pair(Icons.Rounded.Fingerprint, Color(0xFF0E9384))
    "gaji" -> Pair(Icons.Rounded.Payments, Color(0xFF7A5AF8))
    "kpi" -> Pair(Icons.Rounded.Insights, Color(0xFF0BA5EC))
    "spk" -> Pair(Icons.Rounded.LocalShipping, Color(0xFF1E63E9))
    "pdi_queue" -> Pair(Icons.Rounded.FactCheck, Color(0xFF6941C6))
    "inventory" -> Pair(Icons.Rounded.Inventory2, MaterialTheme.colorScheme.primary)
    "cari_semua" -> Pair(Icons.Rounded.Search, Color(0xFF0086C9))
    "crm" -> Pair(Icons.Rounded.Groups, MaterialTheme.colorScheme.tertiary)
    "indent" -> Pair(Icons.Rounded.PlaylistAddCheck, MaterialTheme.colorScheme.secondary)
    "klasemen" -> Pair(Icons.Rounded.BarChart, Color(0xFF12B76A))
    "klasemen_lapangan" -> Pair(Icons.Rounded.EmojiEvents, Color(0xFFF79009))
    "opname" -> Pair(Icons.Rounded.FactCheck, Color(0xFF0BA5EC))
    "harga_gs" -> Pair(Icons.Rounded.PriceChange, Color(0xFFF79009))
    "serial_input" -> Pair(Icons.Rounded.Numbers, Color(0xFF667085))
    "goda_serial" -> Pair(Icons.Rounded.ElectricBike, Color(0xFF12B76A))
    "deadstock" -> Pair(Icons.Rounded.Inventory2, Color(0xFFB54708))
    "mutasi_histori" -> Pair(Icons.Rounded.SwapHoriz, Color(0xFF7A5AF8))
    // Warna merah yang sama dipakai kartu komplain di layar Activity
    // (`ActivityScreen.kt`), supaya modul yang sama tak berganti rupa
    // tergantung dari mana ia dibuka.
    "komplain_lapor" -> Pair(Icons.Rounded.Build, Color(0xFFD92D20))
    "komplain_saya" -> Pair(Icons.Rounded.Build, Color(0xFFB5670C))
    "komplain_tugas" -> Pair(Icons.Rounded.HomeRepairService, Color(0xFFD92D20))
    // Ikon yang sama dengan kartu "Tugas Pemasangan AC" di layar Activity:
    // dua sisi modul yang sama tak boleh berganti rupa tergantung dari mana
    // ia dibuka (alasan yang sama dipakai dua ubin komplain di atas).
    "pemasangan_ac_kontrol" -> Pair(Icons.Rounded.AcUnit, Color(0xFF0BA5EC))
    "vertel" -> Pair(Icons.Rounded.PhoneInTalk, Color(0xFF0E9384))
    else -> Pair(Icons.Rounded.Bolt, MaterialTheme.colorScheme.primary)
}

@Composable
private fun QuickAccessTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Sales KPI — MTD hero (vs last month) + a 2×2 grid of today's metrics, each with growth. */
@Composable
internal fun KpiCard(kpi: ExecutiveKpiDto?) {
    if (kpi == null) {
        PlaceholderCard("Belum ada data KPI")
        return
    }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Hero: MTD amount in a tinted panel — label up top, growth badge pinned top-right,
            // big value below.
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Amount Bulan Ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GrowthBadge(kpi.mtd.growthPct)
                    }
                    Text(
                        text = formatRupiah(kpi.mtd.current),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Bulan lalu ${formatRupiah(kpi.mtd.lastMonth)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Text(
                text = "Performa Hari Ini",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(Icons.Rounded.AccountBalanceWallet, "Amount", formatRupiah(kpi.revenue.today), kpi.revenue.growthPct)
                MiniMetric(Icons.Rounded.Receipt, "Transaksi", "${kpi.transaction.today.toInt()}", kpi.transaction.growthPct)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(Icons.Rounded.Inventory2, "Unit Terjual", "${kpi.unit.today.toInt()}", kpi.unit.growthPct)
                MiniMetric(Icons.Rounded.Calculate, "Rata²/Transaksi", formatRupiah(kpi.avgTransaction), null)
            }
        }
    }
}

/** Small circular tinted icon badge — the "icon chip" used on KPI/target tile headers. */
@Composable
internal fun IconChip(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(26.dp), shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}

/** A small metric tile inside a card: icon chip + label + value + optional growth arrow. */
@Composable
internal fun RowScope.MiniMetric(icon: ImageVector, label: String, value: String, growthPct: Double?) {
    Surface(
        modifier = Modifier.weight(1f).heightIn(min = 84.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                IconChip(icon = icon, tint = MaterialTheme.colorScheme.primary)
            }
            // Full (un-abbreviated) amounts can run long — allow a second line instead of
            // ellipsis-truncating a currency figure, which would hide real digits.
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (growthPct != null) {
                val positive = growthPct >= 0
                Text(
                    text = "${if (positive) "▲" else "▼"} %.1f%%".format(kotlin.math.abs(growthPct)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (positive) Color(0xFF12B76A) else Color(0xFFF04438),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun TargetCard(target: MonthlyTargetDto?) {
    if (target == null) {
        PlaceholderCard("Belum ada data target")
        return
    }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pencapaian Target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TargetStatusChip(willAchieve = target.projection.willAchieve)
                    }
                    Text(
                        text = "%.1f%%".format(target.achievementPct),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    LinearProgressIndicator(
                        progress = { (target.achievementPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 8.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = formatRupiah(target.actual), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(text = "dari ${formatRupiah(target.target)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "Seharusnya %.1f%%  •  hari ke-%d dari %d".format(target.expectedPct, target.dayPassed, target.daysInMonth),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(Icons.Rounded.AccountBalanceWallet, "Sisa Target", formatRupiahShort(target.remainingRevenue), null)
                MiniMetric(Icons.Rounded.CalendarToday, "Sisa Hari", "${target.remainingDays} hari", null)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(Icons.Rounded.TrendingUp, "Butuh / Hari", formatRupiahShort(target.neededPerDay), null)
                MiniMetric(Icons.Rounded.Flag, "Target / Hari", formatRupiahShort(target.targetPerDay), null)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Proyeksi Akhir Bulan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatRupiah(target.projection.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Estimasi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "%.1f%%".format(target.projection.achievementPct), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (target.projection.gap > 0) {
                Text(
                    text = "Kurang ${formatRupiahShort(target.projection.gap)} lagi untuk capai target",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB5670C),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun TargetStatusChip(willAchieve: Boolean) {
    val (label, color) = if (willAchieve) "On Track" to Color(0xFF2E7D32) else "Perlu Usaha" to Color(0xFFB5670C)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CrmCard(summary: LeadSummary?) {
    if (summary == null) {
        PlaceholderCard("Belum ada data prospek")
        return
    }
    // Desain tenang & rapi: satu angka utama (nilai pipeline) + baris statistik seragam.
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Nilai Pipeline Aktif",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatRupiah(summary.openEstimatedValue),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CrmStat("Hari Ini", summary.todayCount, MaterialTheme.colorScheme.onSurface)
                CrmStatDivider()
                CrmStat("Total", summary.totalCount, MaterialTheme.colorScheme.onSurface)
                CrmStatDivider()
                CrmStat("Deal", summary.wonThisMonth, Color(0xFF2E7D32))
                CrmStatDivider()
                CrmStat("Gagal", summary.lostThisMonth, Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun RowScope.CrmStat(label: String, value: Int, valueColor: Color) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CrmStatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

@Composable
internal fun PlaceholderCard(text: String) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    icon: ImageVector,
    onViewMore: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (onViewMore != null) {
            IconButton(onClick = onViewMore) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Lihat semua $title",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun GrowthBadge(pct: Double) {
    val positive = pct >= 0
    val color = if (positive) Color(0xFF12B76A) else Color(0xFFF04438)
    val arrow = if (positive) "▲" else "▼"
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = "$arrow %.1f%%".format(kotlin.math.abs(pct)),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun RankingCard(content: @Composable () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val color = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = CircleShape, modifier = Modifier.size(28.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = "$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun BranchRankingRow(rank: Int, branch: LeaderboardBranchItemDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = branch.cabang, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "${branch.totalTransaksi} transaksi", style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatRupiah(branch.omset.toDouble()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SalesRankingRow(rank: Int, sales: LeaderboardSalesItemDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = sales.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "${sales.totalQty} unit · ${sales.totalTransaksi} transaksi", style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatRupiah(sales.revenue.toDouble()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun HomeLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(110.dp), shape = MaterialTheme.shapes.extraLarge)
        repeat(3) {
            SkeletonLine(widthFraction = 0.45f, height = 22.dp)
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(24.dp))
        }
    }
}

internal fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = kotlin.math.abs(rounded).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (rounded < 0) "-Rp $text" else "Rp $text"
}

/** Compact currency for stat cards, e.g. Rp 1,8M / Rp 77,9Jt. */
internal fun formatRupiahShort(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000_000 -> "%sRp %.1fM".format(sign, abs / 1_000_000_000)
        abs >= 1_000_000 -> "%sRp %.1fJt".format(sign, abs / 1_000_000)
        abs >= 1_000 -> "%sRp %.0fRb".format(sign, abs / 1_000)
        else -> "%sRp %.0f".format(sign, abs)
    }
}

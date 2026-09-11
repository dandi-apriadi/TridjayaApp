package com.krisoft.tridjayaelektronik.ui.cekharga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.model.ProductPriceSearchItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_QUERY_LENGTH = 2
private const val DEBOUNCE_MS = 300L

data class CekHargaUiState(
    val query: String = "",
    val items: List<ProductPriceSearchItemDto> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

/**
 * Layar "Cek Harga" (menu Akses Cepat terpisah dari Inventory) — pencarian harga LIVE ke
 * server ([InventoryRepository.cekHarga]), bukan cache Room. Debounce sama seperti
 * [com.krisoft.tridjayaelektronik.ui.search.GlobalSearchViewModel] (`GlobalSearchScreen`
 * yang menyaring cache offline) — dua layar dengan sumber data berbeda, jangan digabung.
 */
@HiltViewModel
class CekHargaViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CekHargaUiState())
    val state: StateFlow<CekHargaUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(items = emptyList(), isSearching = false, hasSearched = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            runSearch(trimmed)
        }
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(isSearching = true, error = null) }
        try {
            when (val res = repository.cekHarga(query)) {
                is AuthResult.Success -> _state.update {
                    it.copy(isSearching = false, hasSearched = true, items = res.data)
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(isSearching = false, hasSearched = true, items = emptyList(), error = res.message)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(isSearching = false, hasSearched = true, items = emptyList(), error = e.message)
            }
        }
    }
}

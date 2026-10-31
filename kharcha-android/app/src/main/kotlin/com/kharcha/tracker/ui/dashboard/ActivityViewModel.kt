package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.ActivityLogDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.repository.ActivityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Render state for the activity screen's group picker + date filter.
 *
 * [Ready.groups] is the user's groups (`GET /groups`); [Ready.selectedGroupId]
 * is the group whose feed is shown (the first group is auto-selected, null
 * when the user has no group at all - the screen shows a "no groups" hint).
 */
sealed class ActivityUiState {
    object Loading : ActivityUiState()
    data class Error(val message: String) : ActivityUiState()
    data class Ready(
        val groups: List<GroupDto>,
        val selectedGroupId: String? = null
    ) : ActivityUiState()
}

/**
 * Render state of the selected group's audit-log feed.
 *
 * [Idle] means no group is selected (the screen shows a pick-a-group hint).
 * [Ready.members] is the group's member list for actor-name resolution
 * (degraded to empty when `/members` failed - the rows fall back to the
 * 8-char id prefix, exactly like the web UI).
 */
sealed class ActivityFeedState {
    object Idle : ActivityFeedState()
    object Loading : ActivityFeedState()
    data class Error(val message: String) : ActivityFeedState()
    data class Ready(
        val logs: List<ActivityLogDto>,
        val total: Int,
        val page: Int,
        val members: List<GroupMemberDto>
    ) : ActivityFeedState()
}

/**
 * Loads the group picker, then the selected group's audit-log feed with the
 * web UI's conventions: the first group is auto-selected, the date filter
 * resets on group change, and the feed is paginated 20 per page (the
 * server's `X-Total-Count` header reports the unbounded total).
 *
 * No-arg constructor (default [ViewModel] factory): the network stack is a
 * process-wide singleton initialised in [com.kharcha.tracker.MainActivity].
 */
class ActivityViewModel : ViewModel() {

    private val repository = ActivityRepository(NetworkModule.api)

    private val _uiState = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private val _feedState = MutableStateFlow<ActivityFeedState>(ActivityFeedState.Idle)
    val feedState: StateFlow<ActivityFeedState> = _feedState.asStateFlow()

    /** The applied date-range filter as ISO instants (nulls = unbounded). */
    var dateFrom: String? = null
        private set
    var dateTo: String? = null
        private set

    /** The 1-based feed page currently loaded (or being loaded). */
    var page: Int = 1
        private set

    /** Set once [load] has completed, so [refresh] cannot race the first load. */
    private var firstLoadComplete = false

    init {
        load()
    }

    /** (Re)loads the group picker - also the retry action after an error. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = ActivityUiState.Loading
            when (val result = repository.groups()) {
                is NetworkResult.Success -> {
                    // Auto-select the first group (web behaviour).
                    val first = result.data.firstOrNull { !it.id.isNullOrBlank() }?.id
                    _uiState.value = ActivityUiState.Ready(
                        groups = result.data,
                        selectedGroupId = first
                    )
                    if (first != null) {
                        loadFeed(first)
                    } else {
                        _feedState.value = ActivityFeedState.Idle
                    }
                    firstLoadComplete = true
                }

                is NetworkResult.Error -> {
                    _uiState.value = ActivityUiState.Error(result.message)
                    firstLoadComplete = true
                }

                else -> {
                    _uiState.value = ActivityUiState.Error("Unexpected error")
                    firstLoadComplete = true
                }
            }
        }
    }

    /**
     * Refetches the picker list and (when a group is selected) its feed
     * without a full-screen spinner, called on screen resume. A transient
     * failure keeps the last good data.
     */
    fun refresh() {
        if (!firstLoadComplete) return
        viewModelScope.launch {
            when (val result = repository.groups()) {
                is NetworkResult.Success -> _uiState.update { current ->
                    if (current is ActivityUiState.Ready) {
                        val stillThere = result.data.any { it.id == current.selectedGroupId }
                        val selection = if (stillThere) {
                            current.selectedGroupId
                        } else {
                            result.data.firstOrNull { !it.id.isNullOrBlank() }?.id
                        }
                        current.copy(groups = result.data, selectedGroupId = selection)
                    } else {
                        current
                    }
                }

                else -> Unit
            }
            selectedGroup()?.let { groupId ->
                val next = fetchFeed(groupId)
                if (next is ActivityFeedState.Ready) {
                    _feedState.value = next
                }
            }
        }
    }

    /** Selects a group: resets the date filter and page, then loads page 1. */
    fun selectGroup(groupId: String) {
        val ready = _uiState.value as? ActivityUiState.Ready ?: return
        if (ready.selectedGroupId == groupId) return
        _uiState.value = ready.copy(selectedGroupId = groupId)
        dateFrom = null
        dateTo = null
        page = 1
        loadFeed(groupId)
    }

    /**
     * Applies (or clears, when both bounds are null) the date filter and
     * reloads the first page. [dateFrom] / [dateTo] are the validated ISO
     * instants from [ActivityTexts.dateParams].
     */
    fun applyDateRange(dateFrom: String?, dateTo: String?) {
        this.dateFrom = dateFrom
        this.dateTo = dateTo
        page = 1
        loadFeed()
    }

    /** Moves the feed by [delta] pages, clamped to the known page count. */
    fun goToPage(delta: Int) {
        val ready = _feedState.value as? ActivityFeedState.Ready ?: return
        val maxPage = ActivityTexts.pageCount(ready.total)
        val target = (ready.page + delta).coerceIn(1, maxPage)
        if (target == ready.page) return
        page = target
        loadFeed()
    }

    /** Retries the current page after a feed error. */
    fun retryFeed() {
        loadFeed()
    }

    /** The currently selected group id, or null (nothing to load). */
    private fun selectedGroup(): String? =
        (_uiState.value as? ActivityUiState.Ready)?.selectedGroupId

    /** Loads [page] of the selected group's feed (spinner shown). */
    private fun loadFeed() {
        val groupId = selectedGroup() ?: return
        loadFeed(groupId)
    }

    private fun loadFeed(groupId: String) {
        _feedState.value = ActivityFeedState.Loading
        viewModelScope.launch {
            _feedState.value = fetchFeed(groupId)
        }
    }

    /**
     * Fetches one feed page (the current [page] + filter). Member names are
     * fetched best-effort for actor resolution: a failed `/members` degrades
     * to an empty list, exactly like the web's members-cache fallback.
     */
    private suspend fun fetchFeed(groupId: String): ActivityFeedState {
        // The repository takes a 0-based page; the UI states are 1-based.
        when (
            val result = repository.activity(
                groupId = groupId,
                page = (page - 1).coerceAtLeast(0),
                pageSize = ActivityRepository.PAGE_SIZE,
                dateFrom = dateFrom,
                dateTo = dateTo
            )
        ) {
            is NetworkResult.Success -> {
                val members = (repository.members(groupId) as? NetworkResult.Success)?.data
                    ?: emptyList()
                return ActivityFeedState.Ready(
                    logs = result.data.logs,
                    total = result.data.total,
                    page = page,
                    members = members
                )
            }

            is NetworkResult.Error ->
                return ActivityFeedState.Error(result.message)

            else ->
                return ActivityFeedState.Error("Unexpected error")
        }
    }
}

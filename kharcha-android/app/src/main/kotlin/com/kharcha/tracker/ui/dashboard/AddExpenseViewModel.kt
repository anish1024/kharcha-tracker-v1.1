package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.AddExpenseRequest
import com.kharcha.tracker.data.remote.dto.ExpenseShareDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.repository.DashboardRepository
import com.kharcha.tracker.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How the amount is split across members. */
enum class SplitMode {
    /** Server splits equally across all current group members (gateway default). */
    EQUAL,

    /** The form's [ExpenseFormState.percentages] must sum to 100. */
    PERCENTAGE
}

/**
 * The add-expense form, held in the VM so validation is pure and testable
 * and the input survives recomposition.
 */
data class ExpenseFormState(
    val description: String = "",
    val amountText: String = "",
    val category: String = "",
    /** The selected payer's user id; null = the signed-in user pays. */
    val payerId: String? = null,
    val splitMode: SplitMode = SplitMode.EQUAL,
    /**
     * [SplitMode.PERCENTAGE] only: member id -> the percentage as typed
     * (raw text, kept verbatim so the fields stay freely editable). Parsed
     * with `toDoubleOrNull()` by [validateExpenseForm] and
     * [buildAddExpenseRequest] when the form is valid.
     */
    val percentages: Map<String, String> = emptyMap()
)

/**
 * Render state for the add-expense screen: the member list (for the payer
 * and percentage pickers) plus the signed-in profile (for the "(You)"
 * label and the default payer).
 *
 * [Ready.members] may be empty when the members fetch failed - the screen
 * still allows an EQUAL split with the default payer (both are
 * server-side defaults), so this degrades instead of erroring unless the
 * profile fetch also failed (in which case there is nothing to render).
 */
sealed class AddExpenseUiState {
    object Loading : AddExpenseUiState()
    data class Error(val message: String) : AddExpenseUiState()
    data class Ready(
        val members: List<GroupMemberDto>,
        val myUserId: String?,
        val myName: String?
    ) : AddExpenseUiState()
}

/**
 * Progress of the submit action. [Success] is the one-shot signal the
 * screen uses to snackbar + pop back; [Failed] is surfaced as a snackbar
 * and cleared via [AddExpenseViewModel.clearSubmitError].
 */
sealed class SubmitUiState {
    object Idle : SubmitUiState()
    object Busy : SubmitUiState()
    object Success : SubmitUiState()
    data class Failed(val message: String) : SubmitUiState()
}

/** Tolerance for the percentage-sum check (float rounding). */
private const val PERCENTAGE_TOLERANCE = 0.01

/**
 * Splits 100 across [memberIds] to 2-decimal precision such that the
 * values sum to exactly 100 (e.g. 3 members -> "33.34" + "33.33" +
 * "33.33"). Values are 2-decimal strings so the prefilled fields read and
 * edit like plain text; each parses to the exact Double the server expects.
 * Used to prefill the percentage fields so the seeded form always passes
 * [validateExpenseForm].
 */
fun seedPercentages(memberIds: List<String>): Map<String, String> {
    if (memberIds.isEmpty()) return emptyMap()
    val totalCents = 10_000L
    val baseCents = totalCents / memberIds.size
    val remainderCents = totalCents % memberIds.size
    return memberIds.mapIndexedNotNull { index, id ->
        id?.let {
            val cents = baseCents + if (index < remainderCents) 1L else 0L
            it to "%.2f".format(cents / 100.0)
        }
    }.toMap()
}

/**
 * Validates the add-expense form. Returns a user-facing message when
 * invalid, or null when the form is ready to submit.
 *
 * [members] is the group's member list (may be empty when the members
 * fetch failed - a percentage split is unavailable then, so only the
 * amount rules apply).
 */
fun validateExpenseForm(
    members: List<GroupMemberDto>,
    form: ExpenseFormState
): String? {
    val amount = form.amountText.trim().toDoubleOrNull()
    if (amount == null) return "Enter a valid amount"
    if (amount <= 0.0) return "The amount must be greater than zero"

    if (form.splitMode == SplitMode.PERCENTAGE) {
        if (members.isEmpty()) return "Member list unavailable - use an equal split"
        if (form.percentages.size < members.size) {
            return "Enter a percentage for each member"
        }
        val parsed = form.percentages.values.map { it.trim().toDoubleOrNull() }
        if (parsed.any { it == null }) {
            return "Enter a valid percentage for each member"
        }
        val sum = parsed.sumOf { it ?: 0.0 }
        if (abs(sum - 100.0) > PERCENTAGE_TOLERANCE) {
            return "Percentages must add up to 100% (currently ${"%.2f".format(sum)}%)"
        }
    }
    return null
}

/**
 * Builds the wire request for a valid form.
 *
 * Split mapping (verified against the gateway):
 * - [SplitMode.EQUAL] -> `expn_splt` omitted (null) - the gateway splits
 *   the amount equally across all current members (its default).
 * - [SplitMode.PERCENTAGE] -> one [ExpenseShareDto] per entry in
 *   [ExpenseFormState.percentages].
 *
 * [ExpenseFormState.payerId] null -> the signed-in actor pays the full
 * amount (gateway default); otherwise `paid_by_user_id` is set.
 */
fun buildAddExpenseRequest(groupId: String, form: ExpenseFormState): AddExpenseRequest {
    val shares = if (form.splitMode == SplitMode.PERCENTAGE) {
        form.percentages.map { (userId, raw) ->
            ExpenseShareDto(userId = userId, percentage = raw.trim().toDouble())
        }
    } else {
        null
    }
    return AddExpenseRequest(
        groupId = groupId,
        amount = form.amountText.trim().toDouble(),
        description = form.description.trim().takeIf { it.isNotEmpty() },
        category = form.category.trim().takeIf { it.isNotEmpty() },
        paidByUserId = form.payerId,
        shares = shares
    )
}

/**
 * Collects and submits an expense for one group.
 *
 * Constructor takes the route's [groupId]; navigation creates one VM per
 * back-stack entry. Holds the [DashboardRepository] only for `me()` (the
 * default payer / "(You)" label); everything group-scoped goes through
 * [GroupRepository].
 */
class AddExpenseViewModel(
    private val groupId: String
) : ViewModel() {

    private val groupRepository = GroupRepository(NetworkModule.api)
    private val dashboardRepository = DashboardRepository(NetworkModule.api)

    private val _uiState = MutableStateFlow<AddExpenseUiState>(AddExpenseUiState.Loading)
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(ExpenseFormState())
    val form: StateFlow<ExpenseFormState> = _form.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitUiState>(SubmitUiState.Idle)
    val submitState: StateFlow<SubmitUiState> = _submitState.asStateFlow()

    /** Live validation message, or null when the form is valid. */
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    init {
        load()
    }

    /** (Re)loads members + profile - also the retry action after an error. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = AddExpenseUiState.Loading

            // Best-effort both ways: with either piece the screen can still
            // submit a sensible EQUAL-split expense (gateway defaults).
            val me = (dashboardRepository.me() as? NetworkResult.Success)?.data
            val members = (groupRepository.members(groupId) as? NetworkResult.Success)?.data

            if (me == null && members == null) {
                _uiState.value =
                    AddExpenseUiState.Error("Could not load the group members")
            } else {
                _uiState.value = AddExpenseUiState.Ready(
                    members = members.orEmpty(),
                    myUserId = me?.id,
                    myName = me?.name
                )
                // Default payer: the signed-in user (server does the same,
                // but the chip should read selected).
                me?.id?.let { myId ->
                    _form.update { f ->
                        if (f.payerId == null) f.copy(payerId = myId) else f
                    }
                }
            }

            revalidate()
        }
    }

    fun setDescription(value: String) {
        _form.update { it.copy(description = value) }
        revalidate()
    }

    fun setAmount(value: String) {
        _form.update { it.copy(amountText = value) }
        revalidate()
    }

    fun setCategory(value: String) {
        _form.update { it.copy(category = value) }
        revalidate()
    }

    fun setPayer(userId: String) {
        _form.update { it.copy(payerId = userId) }
    }

    fun setSplitMode(mode: SplitMode) {
        _form.update { it.copy(splitMode = mode) }
        if (mode == SplitMode.PERCENTAGE) {
            seedPercentages()
        }
        revalidate()
    }

    fun setPercentage(userId: String, value: String) {
        _form.update {
            val map = it.percentages.toMutableMap()
            if (value.isBlank()) {
                map.remove(userId)
            } else {
                map[userId] = value
            }
            it.copy(percentages = map)
        }
        revalidate()
    }

    /**
     * Validates the current form. Returns true when valid (and clears the
     * live error), false otherwise (message in [validationError]).
     */
    fun revalidate(): Boolean {
        val state = _uiState.value as? AddExpenseUiState.Ready ?: return false
        val error = validateExpenseForm(state.members, _form.value)
        _validationError.value = error
        return error == null
    }

    /** Submits the form; [SubmitUiState.Success] signals the screen to pop back. */
    fun submit() {
        if (_submitState.value is SubmitUiState.Busy) return
        if (!revalidate()) return

        val form = _form.value
        viewModelScope.launch {
            _submitState.value = SubmitUiState.Busy
            val request = runCatching { buildAddExpenseRequest(groupId, form) }.getOrElse {
                _submitState.value = SubmitUiState.Failed("Invalid amount")
                return@launch
            }
            when (val result = groupRepository.addExpense(request)) {
                is NetworkResult.Success ->
                    _submitState.value = SubmitUiState.Success

                is NetworkResult.Error ->
                    _submitState.value = SubmitUiState.Failed(result.message)

                else ->
                    _submitState.value = SubmitUiState.Failed("Unexpected error")
            }
        }
    }

    /** Clears a shown submit error (the screen calls this after snackbar). */
    fun clearSubmitError() {
        if (_submitState.value is SubmitUiState.Failed) {
            _submitState.value = SubmitUiState.Idle
        }
    }

    /** Prefills the percentage fields so they sum to exactly 100. */
    private fun seedPercentages() {
        val ready = _uiState.value as? AddExpenseUiState.Ready ?: return
        val ids = ready.members.mapNotNull { it.id }
        if (ids.isEmpty()) return
        _form.update { it.copy(percentages = seedPercentages(ids)) }
    }
}

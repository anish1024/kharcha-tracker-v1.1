package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.ExpensePaymentDto
import com.kharcha.tracker.data.remote.dto.ExpenseUpdateRequest
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.repository.DashboardRepository
import com.kharcha.tracker.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A strict `yyyy-MM-dd` date (the gateway parses it as a datetime). */
private val DATE_PATTERN = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

/**
 * The edit-expense form: the expense's original values (seeded once on
 * load) plus the values the user is currently typing. Comparing the two
 * decides which keys go on the wire - the gateway treats **absent keys as
 * "no change"**, so an unedited field must stay out of the request.
 *
 * [amount] is displayed read-only: the gateway's `ExpenseUpdate` has no
 * amount field, so the amount can't be changed here.
 */
data class EditExpenseFormState(
    val initialDate: String? = null,
    val initialCategory: String? = null,
    val initialDescription: String = "",
    val initialPayerId: String? = null,
    val amount: Double = 0.0,
    /** The date as typed, `yyyy-MM-dd`; blank = "keep the original". */
    val date: String = "",
    /** The category as typed; blank with a non-blank [initialCategory] is invalid. */
    val category: String = "",
    /** The description as typed; an empty string clears it. */
    val description: String = "",
    /** The selected payer's user id; [initialPayerId] = "keep the original". */
    val payerId: String? = null
)

/**
 * Render state for the edit-expense screen: the expense itself plus the
 * group's member list (for the payer picker) and the signed-in profile.
 *
 * [Ready.members] / [Ready.myUserId] may be empty/null when those fetches
 * failed - the form still works (the original payer stays selected), only
 * the "change payer" option degrades.
 */
sealed class EditExpenseUiState {
    object Loading : EditExpenseUiState()
    data class Error(val message: String) : EditExpenseUiState()
    data class Ready(
        val expense: ExpenseDto,
        val members: List<GroupMemberDto>,
        val myUserId: String?
    ) : EditExpenseUiState()
}

/**
 * Progress of the save action. [Success] is the one-shot signal the screen
 * uses to snackbar + pop back; [Failed] is surfaced inline and cleared via
 * [EditExpenseViewModel.clearSubmitError].
 */
sealed class EditSubmitUiState {
    object Idle : EditSubmitUiState()
    object Busy : EditSubmitUiState()
    object Success : EditSubmitUiState()
    data class Failed(val message: String) : EditSubmitUiState()
}

/**
 * Progress of the delete action. [Deleted] is the one-shot signal the
 * screen uses to snackbar + pop back; [Failed] is surfaced in the confirm
 * dialog and cleared via [EditExpenseViewModel.clearDeleteError].
 */
sealed class EditDeleteUiState {
    object Idle : EditDeleteUiState()
    object Busy : EditDeleteUiState()
    object Deleted : EditDeleteUiState()
    data class Failed(val message: String) : EditDeleteUiState()
}

/**
 * The `yyyy-MM-dd` prefix of [dateTime], or null when it doesn't start
 * with a well-formed date (the field then starts blank = "keep the
 * original").
 */
fun initialDateOf(dateTime: String?): String? {
    val candidate = dateTime?.take(10) ?: return null
    return if (DATE_PATTERN.matches(candidate)) candidate else null
}

/**
 * Validates a `yyyy-MM-dd` date string (leap-year aware). Returns a
 * user-facing message when invalid, or null when valid. A blank input is
 * treated as "no change" and passes - the caller decides whether blank is
 * allowed for the field.
 */
fun validateEditDate(input: String): String? {
    val value = input.trim()
    if (value.isEmpty()) return null
    val match = DATE_PATTERN.find(value) ?: return "Use the format YYYY-MM-DD"
    val year = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    if (year < 1990 || year > 2100) return "Year out of range"
    if (month < 1 || month > 12) return "Month must be 01-12"
    val daysInMonth = when (month) {
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 31
    }
    if (day < 1 || day > daysInMonth) return "Day must be 01-${daysInMonth} for that month"
    return null
}

/** Gregorian leap-year rule (no `java.time` on minSdk 24). */
private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

/**
 * Validates the whole edit form. Returns a user-facing message when
 * invalid, or null when ready to submit.
 *
 * The category can't be blanked out: the gateway only accepts a valid
 * category value when the key is present, and there is no API to remove a
 * category - so blanking it is a form error rather than "no change".
 */
fun validateEditExpenseForm(state: EditExpenseFormState): String? {
    validateEditDate(state.date)?.let { return it }
    if (!state.initialCategory.isNullOrBlank() && state.category.trim().isEmpty()) {
        return "The category can't be removed - keep it or enter a new one"
    }
    return null
}

/**
 * True when the form differs from the loaded expense in at least one
 * field. The screen disables Save when this is false (sending `{}` is a
 * server-side no-op anyway).
 */
fun hasChanges(state: EditExpenseFormState): Boolean {
    val dateChanged = state.date.trim().isNotEmpty() && state.date.trim() != state.initialDate
    val categoryChanged =
        state.category.trim().isNotEmpty() && state.category.trim() != state.initialCategory
    val descriptionChanged = state.description != state.initialDescription
    val payerChanged = state.payerId != null && state.payerId != state.initialPayerId
    return dateChanged || categoryChanged || descriptionChanged || payerChanged
}

/**
 * Builds the wire request for a valid form, carrying **only** the fields
 * that actually changed (the gateway sets each key it receives, so an
 * unchanged field must be omitted, not sent with its old value):
 *
 * - date: non-blank and different from the original -> `date_time`
 * - category: non-blank and different -> `expn_ctgry`
 * - description: different (an empty string clears it) -> `expn_desc`
 * - payer: different -> `paid_by` = the full amount on that one payer
 *   (the gateway requires the paid total to match the expense amount)
 */
fun buildExpenseUpdateRequest(state: EditExpenseFormState): ExpenseUpdateRequest {
    val date = state.date.trim()
    val category = state.category.trim()
    val description = state.description
    val payerId = state.payerId

    return ExpenseUpdateRequest(
        dateTime = if (date.isNotEmpty() && date != state.initialDate) date else null,
        category = if (category.isNotEmpty() && category != state.initialCategory) category else null,
        description = if (description != state.initialDescription) description else null,
        paidBy = if (payerId != null && payerId != state.initialPayerId) {
            listOf(ExpensePaymentDto(userId = payerId, amount = state.amount))
        } else {
            null
        }
    )
}

/**
 * Loads one expense and holds its edit form (Stage 7a).
 *
 * Constructor takes the route's [groupId] (members for the payer picker)
 * and [transId] (the expense); navigation creates one VM per back-stack
 * entry. Save and delete are one-shot actions with their own progress
 * states, mirroring [AddExpenseViewModel].
 */
class EditExpenseViewModel(
    private val groupId: String,
    private val transId: Int
) : ViewModel() {

    private val groupRepository = GroupRepository(NetworkModule.api)
    private val dashboardRepository = DashboardRepository(NetworkModule.api)

    private val _uiState = MutableStateFlow<EditExpenseUiState>(EditExpenseUiState.Loading)
    val uiState: StateFlow<EditExpenseUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(EditExpenseFormState())
    val form: StateFlow<EditExpenseFormState> = _form.asStateFlow()

    private val _submitState = MutableStateFlow<EditSubmitUiState>(EditSubmitUiState.Idle)
    val submitState: StateFlow<EditSubmitUiState> = _submitState.asStateFlow()

    private val _deleteState = MutableStateFlow<EditDeleteUiState>(EditDeleteUiState.Idle)
    val deleteState: StateFlow<EditDeleteUiState> = _deleteState.asStateFlow()

    /** Live validation message, or null when the form is valid. */
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    init {
        load()
    }

    /** Loads the expense, then seeds the form; also the retry action. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = EditExpenseUiState.Loading
            when (val result = groupRepository.getExpense(transId)) {
                is NetworkResult.Success -> {
                    val expense = result.data
                    // Best-effort: the form still works with the original
                    // payer selected when either fetch fails.
                    val me = (dashboardRepository.me() as? NetworkResult.Success)?.data
                    val members =
                        (groupRepository.members(groupId) as? NetworkResult.Success)?.data
                    _uiState.value = EditExpenseUiState.Ready(
                        expense = expense,
                        members = members.orEmpty(),
                        myUserId = me?.id
                    )
                    seedForm(expense)
                }

                is NetworkResult.Error ->
                    _uiState.value = EditExpenseUiState.Error(result.message)

                else ->
                    _uiState.value = EditExpenseUiState.Error("Unexpected error")
            }
        }
    }

    private fun seedForm(expense: ExpenseDto) {
        val initialPayerId = expense.payments.firstOrNull { it.userId != null }?.userId
        _form.value = EditExpenseFormState(
            initialDate = initialDateOf(expense.dateTime),
            initialCategory = expense.category?.takeIf { it.isNotBlank() },
            initialDescription = expense.description.orEmpty(),
            initialPayerId = initialPayerId,
            amount = expense.amount,
            date = initialDateOf(expense.dateTime).orEmpty(),
            category = expense.category.orEmpty(),
            description = expense.description.orEmpty(),
            payerId = initialPayerId
        )
        _validationError.value = null
    }

    fun setDate(value: String) {
        _form.update { it.copy(date = value) }
    }

    fun setCategory(value: String) {
        _form.update { it.copy(category = value) }
    }

    fun setDescription(value: String) {
        _form.update { it.copy(description = value) }
    }

    fun setPayer(userId: String) {
        _form.update { it.copy(payerId = userId) }
    }

    /**
     * Validates the current form. Returns true when valid (and clears the
     * live error), false otherwise (message in [validationError]).
     */
    fun revalidate(): Boolean {
        val error = validateEditExpenseForm(_form.value)
        _validationError.value = error
        return error == null
    }

    /** True when at least one field differs from the loaded expense. */
    fun hasChanges(): Boolean = hasChanges(_form.value)

    /** Saves the changes; [EditSubmitUiState.Success] signals the screen to pop back. */
    fun submit() {
        if (_submitState.value is EditSubmitUiState.Busy) return
        if (!revalidate()) return
        if (!hasChanges(_form.value)) return

        viewModelScope.launch {
            _submitState.value = EditSubmitUiState.Busy
            val request = buildExpenseUpdateRequest(_form.value)
            when (val result = groupRepository.updateExpense(transId, request)) {
                is NetworkResult.Success ->
                    _submitState.value = EditSubmitUiState.Success

                is NetworkResult.Error ->
                    _submitState.value = EditSubmitUiState.Failed(result.message)

                else ->
                    _submitState.value = EditSubmitUiState.Failed("Unexpected error")
            }
        }
    }

    /** Deletes the expense; [EditDeleteUiState.Deleted] signals the screen to pop back. */
    fun delete() {
        if (_deleteState.value is EditDeleteUiState.Busy) return
        viewModelScope.launch {
            _deleteState.value = EditDeleteUiState.Busy
            when (val result = groupRepository.deleteExpense(transId)) {
                is NetworkResult.Success ->
                    _deleteState.value = EditDeleteUiState.Deleted

                is NetworkResult.Error ->
                    _deleteState.value = EditDeleteUiState.Failed(result.message)

                else ->
                    _deleteState.value = EditDeleteUiState.Failed("Unexpected error")
            }
        }
    }

    /** Clears a shown submit error (the screen calls this after surfacing it). */
    fun clearSubmitError() {
        if (_submitState.value is EditSubmitUiState.Failed) {
            _submitState.value = EditSubmitUiState.Idle
        }
    }

    /** Clears a shown delete error (the screen calls this when the dialog is dismissed). */
    fun clearDeleteError() {
        if (_deleteState.value is EditDeleteUiState.Failed) {
            _deleteState.value = EditDeleteUiState.Idle
        }
    }
}

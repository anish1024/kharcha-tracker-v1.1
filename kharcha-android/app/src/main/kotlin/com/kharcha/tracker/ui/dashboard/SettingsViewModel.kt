package com.kharcha.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.remote.dto.NotificationDto
import com.kharcha.tracker.data.remote.dto.SubscriptionStatusDto
import com.kharcha.tracker.data.remote.dto.UserDto
import com.kharcha.tracker.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Render state for the settings screen.
 *
 * [Ready.profile] is the signed-in user (`GET /users/me`) - the whole screen
 * is behind a session, so a failure here is the only [Error]. [Ready.subscription]
 * degrades to null (the plan section shows nothing) and [Ready.notifications]
 * to an empty list when those fetches fail.
 */
sealed class SettingsUiState {
    object Loading : SettingsUiState()
    data class Error(val message: String) : SettingsUiState()
    data class Ready(
        val profile: UserDto,
        val subscription: SubscriptionStatusDto?,
        val notifications: List<NotificationDto>
    ) : SettingsUiState()
}

/**
 * Progress of "save profile". [Saved] is snackbar'd by the screen (which
 * then resets the state); [Failed] is snackbar'd the same way.
 */
sealed class ProfileSaveState {
    object Idle : ProfileSaveState()
    object Busy : ProfileSaveState()
    data class Saved(val profile: UserDto) : ProfileSaveState()
    data class Failed(val message: String) : ProfileSaveState()
}

/**
 * Progress of a plan activation. [Activated.status] is the fresh status the
 * screen swaps into the subscription banner; [Failed.message] is shown
 * inline in the confirm dialog (the dialog stays open until dismissed, the
 * [com.kharcha.tracker.GroupDetailScreen] leave-group pattern).
 */
sealed class ActivationState {
    object Idle : ActivationState()
    object Busy : ActivationState()
    data class Activated(val status: SubscriptionStatusDto) : ActivationState()
    data class Failed(val message: String) : ActivationState()
}

/** Progress of "mark all notifications read" (the server returns the count). */
sealed class MarkReadState {
    object Idle : MarkReadState()
    object Busy : MarkReadState()
    data class Done(val count: Int) : MarkReadState()
    data class Failed(val message: String) : MarkReadState()
}

/**
 * Loads the profile + subscription status + notification list, and drives
 * the profile-save, plan-activation and mark-all-read actions. Sign-out is
 * the caller's job ([com.kharcha.tracker.ui.auth.AuthViewModel.signOut]) -
 * the screen receives it as a callback.
 *
 * No-arg constructor (default [ViewModel] factory): the network stack is a
 * process-wide singleton initialised in [com.kharcha.tracker.MainActivity].
 */
class SettingsViewModel : ViewModel() {

    private val repository = SettingsRepository(NetworkModule.api)

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _profileState = MutableStateFlow<ProfileSaveState>(ProfileSaveState.Idle)
    val profileState: StateFlow<ProfileSaveState> = _profileState.asStateFlow()

    private val _activationState = MutableStateFlow<ActivationState>(ActivationState.Idle)
    val activationState: StateFlow<ActivationState> = _activationState.asStateFlow()

    private val _markReadState = MutableStateFlow<MarkReadState>(MarkReadState.Idle)
    val markReadState: StateFlow<MarkReadState> = _markReadState.asStateFlow()

    /** Set once [load] has completed, so [refresh] cannot race the first load. */
    private var firstLoadComplete = false

    init {
        load()
    }

    /** (Re)loads profile + subscription + notifications - also the retry action. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            // The profile is the one piece the screen cannot render without;
            // subscription + notifications degrade independently.
            when (val profile = repository.me()) {
                is NetworkResult.Success ->
                    _uiState.value = SettingsUiState.Ready(
                        profile = profile.data,
                        subscription = (repository.subscription() as? NetworkResult.Success)?.data,
                        notifications = (repository.notifications() as? NetworkResult.Success)?.data
                            ?: emptyList()
                    )

                is NetworkResult.Error ->
                    _uiState.value = SettingsUiState.Error(profile.message)

                else ->
                    _uiState.value = SettingsUiState.Error("Unexpected error")
            }
            firstLoadComplete = true
        }
    }

    /**
     * Refetches profile + subscription + notifications without a
     * full-screen spinner, called on screen resume. A failed refresh keeps
     * the last good data (a hard session failure force-signs-out via the
     * auth interceptor anyway).
     */
    fun refresh() {
        if (!firstLoadComplete) return
        viewModelScope.launch {
            val current = _uiState.value as? SettingsUiState.Ready ?: return@launch
            when (val profile = repository.me()) {
                is NetworkResult.Success ->
                    _uiState.value = current.copy(
                        profile = profile.data,
                        subscription = (repository.subscription() as? NetworkResult.Success)?.data,
                        notifications = (repository.notifications() as? NetworkResult.Success)?.data
                            ?: current.notifications
                    )

                else -> Unit
            }
        }
    }

    /**
     * Saves the profile ([name] required; [avatarUrl] optional - null leaves
     * it unchanged). The updated profile is swapped into the state on
     * success; [ProfileSaveState.Failed] carries the server's 422 message.
     */
    fun saveProfile(name: String, avatarUrl: String?) {
        if (_profileState.value is ProfileSaveState.Busy) return
        viewModelScope.launch {
            _profileState.value = ProfileSaveState.Busy
            when (val result = repository.updateProfile(name, avatarUrl)) {
                is NetworkResult.Success -> {
                    _uiState.update { current ->
                        (current as? SettingsUiState.Ready)
                            ?.copy(profile = result.data) ?: current
                    }
                    _profileState.value = ProfileSaveState.Saved(result.data)
                }

                is NetworkResult.Error ->
                    _profileState.value = ProfileSaveState.Failed(result.message)

                else ->
                    _profileState.value = ProfileSaveState.Failed("Unexpected error")
            }
        }
    }

    /** Resets the profile state (the screen calls this after snackbar'ing). */
    fun resetProfileState() {
        _profileState.value = ProfileSaveState.Idle
    }

    /**
     * Mock payment: activates [plan] and swaps the fresh status into the
     * subscription banner ([ActivationState.Activated]). [ActivationState.Failed]
     * (the server's 422 "Invalid plan; ...") stays inline in the confirm
     * dialog until it is dismissed.
     */
    fun activatePlan(plan: String) {
        if (_activationState.value is ActivationState.Busy) return
        viewModelScope.launch {
            _activationState.value = ActivationState.Busy
            when (val result = repository.activatePlan(plan)) {
                is NetworkResult.Success -> {
                    _uiState.update { current ->
                        (current as? SettingsUiState.Ready)
                            ?.copy(subscription = result.data) ?: current
                    }
                    _activationState.value = ActivationState.Activated(result.data)
                }

                is NetworkResult.Error ->
                    _activationState.value = ActivationState.Failed(result.message)

                else ->
                    _activationState.value = ActivationState.Failed("Unexpected error")
            }
        }
    }

    /** Resets the activation state (the confirm dialog calls it on dismiss). */
    fun resetActivationState() {
        _activationState.value = ActivationState.Idle
    }

    /**
     * Marks all notifications read (the server returns the count marked);
     * the list's rows are flipped to read in place on success.
     */
    fun markAllNotificationsRead() {
        if (_markReadState.value is MarkReadState.Busy) return
        viewModelScope.launch {
            _markReadState.value = MarkReadState.Busy
            when (val result = repository.markAllNotificationsRead()) {
                is NetworkResult.Success -> {
                    _uiState.update { current ->
                        (current as? SettingsUiState.Ready)
                            ?.copy(notifications = current.notifications.map { it.copy(isRead = true) })
                            ?: current
                    }
                    _markReadState.value = MarkReadState.Done(result.data)
                }

                is NetworkResult.Error ->
                    _markReadState.value = MarkReadState.Failed(result.message)

                else ->
                    _markReadState.value = MarkReadState.Failed("Unexpected error")
            }
        }
    }

    /** Resets the mark-read state (the screen calls this after snackbar'ing). */
    fun resetMarkReadState() {
        _markReadState.value = MarkReadState.Idle
    }
}

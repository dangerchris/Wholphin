package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.TekSavvyTuner
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TekSavvyTuneViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val tekSavvyTuner: TekSavvyTuner,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val state = MutableStateFlow(TekSavvyTuneState())

        fun init(channelNumber: String) {
            savedStateHandle["channelNumber"] = channelNumber
            tune(channelNumber)
        }

        fun tune(channelNumber: String) {
            state.update { it.copy(loading = LoadingState.Loading) }
            viewModelScope.launchDefault {
                val result = tekSavvyTuner.tune(channelNumber)
                result.fold(
                    onSuccess = {
                        state.update { it.copy(loading = LoadingState.Success) }
                    },
                    onFailure = { ex ->
                        Timber.e(ex, "Failed to tune channel $channelNumber")
                        state.update { it.copy(loading = LoadingState.Error(ex)) }
                    },
                )
            }
        }

        fun goBack() {
            navigationManager.goBack()
        }
    }

data class TekSavvyTuneState(
    val loading: LoadingState = LoadingState.Pending,
)

@Composable
fun TekSavvyTunePage(
    channelNumber: String,
    modifier: Modifier = Modifier,
    viewModel: TekSavvyTuneViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(channelNumber) {
        viewModel.init(channelNumber)
    }

    LifecycleStartEffect(Unit) {
        onStopOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val l = state.loading) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                CircularProgress(Modifier.size(48.dp))
                androidx.tv.material3.Text(
                    text = "Tuning to channel $channelNumber...",
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            LoadingState.Success -> {
                androidx.tv.material3.Text(
                    text = "Tuned to channel $channelNumber",
                    color = Color.Green,
                    modifier = Modifier.padding(16.dp),
                )
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.goBack()
                }
            }

            is LoadingState.Error -> {
                androidx.tv.material3.Text(
                    text = "Failed to tune channel",
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                androidx.tv.material3.Text(
                    text = l.exception?.message ?: "Unknown error",
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                TextButton(onClick = { viewModel.tune(channelNumber) }) {
                    androidx.tv.material3.Text("Retry")
                }
                TextButton(onClick = { viewModel.goBack() }, modifier = Modifier.padding(top = 8.dp)) {
                    androidx.tv.material3.Text("Back")
                }
            }
        }
    }
}

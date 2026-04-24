package com.messark.dgchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _greetingMessage = MutableStateFlow(application.getString(R.string.hello_message))
    val greetingMessage: StateFlow<String> = _greetingMessage.asStateFlow()
}

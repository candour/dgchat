package com.example.app

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelTest {
    @Test
    fun `greetingMessage is initially the string from resources`() = runBlocking {
        val application = mockk<Application>()
        every { application.getString(R.string.hello_message) } returns "Hello"

        val viewModel = MainViewModel(application)
        assertEquals("Hello", viewModel.greetingMessage.first())
    }
}

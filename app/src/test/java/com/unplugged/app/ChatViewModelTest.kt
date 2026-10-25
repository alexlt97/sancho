package com.unplugged.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Test
    fun sendAddsMessageAndClearsDraft() {
        val viewModel = ChatViewModel()

        viewModel.updateDraft("hello")
        viewModel.send()

        assertEquals("", viewModel.uiState.value.draft)
        assertEquals(listOf(ChatMessage("You", "hello")), viewModel.uiState.value.messages)
    }

    @Test
    fun blankMessageIsIgnored() {
        val viewModel = ChatViewModel()

        viewModel.updateDraft("   ")
        viewModel.send()

        assertEquals(emptyList<ChatMessage>(), viewModel.uiState.value.messages)
    }

    @Test
    fun loadedFakeModelAnswersPrompt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val fakeLlm = FakeLocalLlm()
            val viewModel = ChatViewModel(localLlm = fakeLlm)

            viewModel.loadModel(
                ImportedModel("fake.litertlm", 1L, File("fake.litertlm")),
            )
            advanceUntilIdle()

            viewModel.updateDraft("hello")
            viewModel.send()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isModelReady)
            assertEquals(
                "Assistant: Fake answer to: hello",
                viewModel.uiState.value.messages.last().let { "${it.author}: ${it.text}" },
            )
            assertTrue(viewModel.uiState.value.messages.last().responseTimeMillis != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun mockPreviewStartsReadyAndStreamsAResponse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = ChatViewModel(
                localLlm = MockLocalLlm(),
                initialModelReady = true,
            )

            assertTrue(viewModel.uiState.value.isModelReady)
            assertEquals("Sancho mock", viewModel.uiState.value.modelName)

            viewModel.updateDraft("hello")
            viewModel.send()
            advanceUntilIdle()

            val assistant = viewModel.uiState.value.messages.last()
            assertEquals("Assistant", assistant.author)
            assertTrue(assistant.text.contains("Mock response"))
            assertTrue(assistant.responseTimeMillis != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun attachedImageIsIncludedInTheModelRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val fakeLlm = FakeLocalLlm()
            val viewModel = ChatViewModel(localLlm = fakeLlm)
            val image = File("photo.jpg")

            viewModel.loadModel(ImportedModel("fake.litertlm", 1L, File("fake.litertlm")))
            advanceUntilIdle()
            viewModel.attachImage(ImageAttachment(image, "photo.jpg"))
            viewModel.send()
            advanceUntilIdle()

            assertEquals(image, fakeLlm.lastRequest?.imageFile)
            assertEquals("Describe this image.", fakeLlm.lastRequest?.prompt)
            assertEquals(null, viewModel.uiState.value.imageAttachment)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

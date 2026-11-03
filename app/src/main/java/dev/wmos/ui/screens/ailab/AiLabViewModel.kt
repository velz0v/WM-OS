package dev.wmos.ui.screens.ailab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.ai.AiClient
import dev.wmos.ai.AiError
import dev.wmos.ai.AiResult
import dev.wmos.ai.ChatRole
import dev.wmos.ai.ChatTurn
import dev.wmos.ai.ProviderPreset
import dev.wmos.ai.ProviderPresets
import dev.wmos.data.db.entity.AiProviderEntity
import dev.wmos.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TestState(
    val running: Boolean = false,
    val success: Boolean? = null,
    val message: String = "",
)

class AiLabViewModel(private val container: AppContainer) : ViewModel() {
    private val client = AiClient()

    val providers: StateFlow<List<AiProviderEntity>> = container.providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _test = MutableStateFlow<TestState>(TestState())
    val test: StateFlow<TestState> = _test

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError

    val presets: List<ProviderPreset> = ProviderPresets.all

    fun maskedKey(provider: AiProviderEntity): String = container.providers.maskedKey(provider)

    fun save(
        id: String?,
        name: String,
        kind: String,
        baseUrl: String,
        model: String,
        apiKey: String?,
        makeDefault: Boolean,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            container.providers.save(id, name, kind, baseUrl, model, apiKey, makeDefault)
                .onSuccess {
                    _saveError.value = null
                    onDone()
                }
                .onFailure { error ->
                    _saveError.value = error.message ?: "Не удалось сохранить провайдера"
                }
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { container.providers.setDefault(id) }
    }

    fun toggleEnabled(provider: AiProviderEntity) {
        viewModelScope.launch { container.providers.setEnabled(provider.id, !provider.enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.providers.delete(id) }
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun testConnection(provider: AiProviderEntity) {
        viewModelScope.launch {
            _test.value = TestState(running = true)
            val key = container.providers.apiKey(provider)
            if (key.isNullOrBlank()) {
                _test.value = TestState(success = false, message = "Ключ не сохранён")
                return@launch
            }
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.chat(
                    provider = provider,
                    apiKey = key,
                    turns = listOf(
                        ChatTurn(ChatRole.SYSTEM, "Отвечай предельно коротко."),
                        ChatTurn(ChatRole.USER, "Ответь одним словом: ок"),
                    ),
                    maxTokens = 16,
                )
            }
            _test.value = when (result) {
                is AiResult.Success -> TestState(
                    success = true,
                    message = "Подключение работает. Ответ: «${result.text.take(40)}»",
                )

                is AiResult.Failure -> TestState(
                    success = false,
                    message = "${result.kind.human()}: ${result.message}",
                )
            }
            container.actionLog.log(
                actor = "user",
                tool = "test_provider",
                status = if (_test.value.success == true) "SUCCESS" else "ERROR",
                summary = "Проверка ${provider.name}: ${_test.value.message.take(120)}",
            )
        }
    }

    fun resetTest() {
        _test.value = TestState()
    }

    companion object {
        val Factory = viewModelFactory { initializer { AiLabViewModel(container()) } }
    }
}

fun AiError.human(): String = when (this) {
    AiError.INVALID_KEY -> "Неверный ключ"
    AiError.RATE_LIMITED -> "Лимит запросов"
    AiError.QUOTA_EXCEEDED -> "Квота исчерпана"
    AiError.UNSUPPORTED_MODEL -> "Модель недоступна"
    AiError.CONTEXT_TOO_LARGE -> "Слишком большой контекст"
    AiError.TIMEOUT -> "Тайм-аут"
    AiError.NETWORK -> "Нет сети"
    AiError.SERVER -> "Ошибка провайдера"
    AiError.INVALID_RESPONSE -> "Некорректный ответ"
    AiError.NO_PROVIDER -> "Провайдер не выбран"
    AiError.UNKNOWN -> "Ошибка"
}

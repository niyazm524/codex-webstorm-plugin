package com.github.niyazm524.codexwebstormplugin.toolWindow

enum class MessageKind {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
    DIFF,
}

data class MessageAction(val label: String, val onClick: () -> Unit)

data class ChatMessage(
    val id: String,
    val kind: MessageKind,
    var content: String,
    val actions: List<MessageAction> = emptyList(),
)

data class ChatSession(val id: String, val title: String)

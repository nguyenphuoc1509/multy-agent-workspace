package com.workspace.conversation.published;

import java.util.UUID;

/** Appends messages. Must be called inside the caller's transaction (the conversation row is locked to assign seq). */
public interface ConversationWriter {

    record Appended(MessageInfo message, boolean duplicate) {}

    /** Idempotent on (conversation, clientRequestId): a repeat returns the stored message with duplicate = true. */
    Appended appendUserMessage(UUID conversationId, String content, UUID clientRequestId);

    MessageInfo appendAgentMessage(UUID conversationId, String content, UUID agentRunId);
}

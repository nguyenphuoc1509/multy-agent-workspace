package com.workspace.conversation.web;

import com.workspace.conversation.published.MessageInfo;
import com.workspace.conversation.published.MessageRole;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, UUID conversationId, int seq, MessageRole role, String content,
                              UUID agentRunId, Instant createdAt) {
    public static MessageResponse from(MessageInfo m) {
        return new MessageResponse(m.id(), m.conversationId(), m.seq(), m.role(), m.content(), m.agentRunId(),
                m.createdAt());
    }
}

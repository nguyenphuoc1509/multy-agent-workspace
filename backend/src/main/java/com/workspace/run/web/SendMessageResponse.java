package com.workspace.run.web;

import com.workspace.conversation.published.MessageInfo;
import com.workspace.conversation.published.MessageRole;
import java.time.Instant;
import java.util.UUID;

public record SendMessageResponse(Message message, RunResponse run) {

    public record Message(UUID id, UUID conversationId, int seq, MessageRole role, String content, Instant createdAt) {
        static Message from(MessageInfo m) {
            return new Message(m.id(), m.conversationId(), m.seq(), m.role(), m.content(), m.createdAt());
        }
    }

    static SendMessageResponse from(MessageInfo m, RunResponse run) {
        return new SendMessageResponse(Message.from(m), run);
    }
}

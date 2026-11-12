package com.workspace.conversation.published;

import java.time.Instant;
import java.util.UUID;

public record MessageInfo(UUID id, UUID conversationId, int seq, MessageRole role, String content, UUID agentRunId,
                          Instant createdAt) {}

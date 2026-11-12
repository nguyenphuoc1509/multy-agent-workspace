package com.workspace.conversation.published;

import java.time.Instant;
import java.util.UUID;

public record ConversationInfo(UUID id, UUID projectId, UUID agentId, UUID taskId, String title, int lastSeq,
                               Instant archivedAt, Instant createdAt) {}

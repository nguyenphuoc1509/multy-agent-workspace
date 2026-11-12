package com.workspace.conversation.web;

import com.workspace.conversation.application.ConversationService.ConversationView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(UUID id, UUID agentId, UUID taskId, String title, int lastSeq, boolean stale,
                                   List<String> staleDocumentTypes, UUID activeRunId, Instant archivedAt,
                                   Instant createdAt) {
    static ConversationResponse from(ConversationView v) {
        var c = v.conversation();
        var x = v.extras();
        return new ConversationResponse(c.id(), c.agentId(), c.taskId(), c.title(), c.lastSeq(), x.stale(),
                x.staleDocumentTypes(), x.activeRunId(), c.archivedAt(), c.createdAt());
    }
}

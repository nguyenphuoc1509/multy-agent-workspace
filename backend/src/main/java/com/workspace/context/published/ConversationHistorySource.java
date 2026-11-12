package com.workspace.context.published;

import java.util.List;
import java.util.UUID;

/** Port implemented by the conversation module so context never depends on it. */
public interface ConversationHistorySource {

    /** role is USER, AGENT or SYSTEM. */
    record HistoryMessage(int seq, String role, String content) {}

    /** True when the conversation exists and belongs to this agent (used to authorise previews). */
    boolean belongsToAgent(UUID conversationId, UUID agentId);

    /** The last {@code count} messages with seq <= upToSeq, oldest first. */
    List<HistoryMessage> recent(UUID conversationId, int upToSeq, int count);
}

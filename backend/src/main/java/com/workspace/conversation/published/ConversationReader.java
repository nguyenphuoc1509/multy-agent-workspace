package com.workspace.conversation.published;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationReader {

    Optional<ConversationInfo> find(UUID conversationId);

    Optional<MessageInfo> findMessage(UUID messageId);

    /** The AGENT message a run produced, if it succeeded. */
    Optional<MessageInfo> findByAgentRun(UUID agentRunId);

    /** The last {@code count} messages with seq <= upToSeq, oldest first. */
    List<MessageInfo> recentMessages(UUID conversationId, int upToSeq, int count);
}

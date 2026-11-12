package com.workspace.conversation.application;

import com.workspace.context.published.ConversationHistorySource;
import com.workspace.conversation.published.ConversationReader;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Lets the context module read history without depending on this module (brief section 4, ports). */
@Component
class ConversationHistoryAdapter implements ConversationHistorySource {

    private final ConversationReader reader;

    ConversationHistoryAdapter(ConversationReader reader) {
        this.reader = reader;
    }

    @Override
    public boolean belongsToAgent(UUID conversationId, UUID agentId) {
        return reader.find(conversationId).filter(c -> c.agentId().equals(agentId)).isPresent();
    }

    @Override
    public List<HistoryMessage> recent(UUID conversationId, int upToSeq, int count) {
        return reader.recentMessages(conversationId, upToSeq, count).stream()
                .map(m -> new HistoryMessage(m.seq(), m.role().name(), m.content())).toList();
    }
}

package com.workspace.conversation.published;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Port implemented by the run module: adds computed fields (active run, staleness) to conversations. */
public interface ConversationDecorator {

    record Extras(UUID activeRunId, boolean stale, List<String> staleDocumentTypes) {
        public static Extras none() {
            return new Extras(null, false, List.of());
        }
    }

    Map<UUID, Extras> extras(Collection<UUID> conversationIds);
}

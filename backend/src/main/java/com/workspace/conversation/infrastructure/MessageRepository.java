package com.workspace.conversation.infrastructure;

import com.workspace.conversation.domain.Message;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByConversationIdAndClientRequestId(UUID conversationId, UUID clientRequestId);

    @Query("select m from Message m where m.conversationId = :cid and m.seq > :afterSeq order by m.seq asc")
    List<Message> after(@Param("cid") UUID conversationId, @Param("afterSeq") int afterSeq, Pageable page);

    @Query("select m from Message m where m.conversationId = :cid and m.seq <= :upTo order by m.seq desc")
    List<Message> latestUpTo(@Param("cid") UUID conversationId, @Param("upTo") int upToSeq, Pageable page);
}

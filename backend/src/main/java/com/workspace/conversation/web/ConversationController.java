package com.workspace.conversation.web;

import com.workspace.conversation.application.ConversationService;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.shared.published.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ConversationController {

    private final ConversationService conversations;
    private final CurrentUserProvider currentUser;

    public ConversationController(ConversationService conversations, CurrentUserProvider currentUser) {
        this.conversations = conversations;
        this.currentUser = currentUser;
    }

    @PostMapping("/agents/{agentId}/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@PathVariable UUID agentId, @Valid @RequestBody(required = false) CreateConversationRequest body) {
        return ConversationResponse.from(conversations.create(currentUser.require().id(), agentId,
                body == null ? null : body.title()));
    }

    @GetMapping("/projects/{projectId}/conversations")
    public PageResponse<ConversationResponse> list(@PathVariable UUID projectId,
                                                   @RequestParam(required = false) UUID agentId,
                                                   @RequestParam(required = false) String cursor,
                                                   @RequestParam(required = false) Integer limit) {
        PageResponse<ConversationService.ConversationView> page =
                conversations.list(currentUser.require().id(), projectId, agentId, cursor, limit);
        return new PageResponse<>(page.items().stream().map(ConversationResponse::from).toList(), page.nextCursor());
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationResponse get(@PathVariable UUID conversationId) {
        return ConversationResponse.from(conversations.get(currentUser.require().id(), conversationId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public PageResponse<MessageResponse> messages(@PathVariable UUID conversationId,
                                                  @RequestParam(required = false) Integer afterSeq,
                                                  @RequestParam(required = false) Integer limit) {
        PageResponse<com.workspace.conversation.published.MessageInfo> page =
                conversations.messages(currentUser.require().id(), conversationId, afterSeq, limit);
        return new PageResponse<>(page.items().stream().map(MessageResponse::from).toList(), page.nextCursor());
    }
}

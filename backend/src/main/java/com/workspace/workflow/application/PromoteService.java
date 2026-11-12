package com.workspace.workflow.application;

import com.workspace.agent.published.AgentRuntime;
import com.workspace.agent.published.PermissionCode;
import com.workspace.audit.published.AuditLog;
import com.workspace.conversation.published.ConversationInfo;
import com.workspace.conversation.published.ConversationReader;
import com.workspace.conversation.published.MessageInfo;
import com.workspace.conversation.published.MessageRole;
import com.workspace.document.published.DocumentDrafts;
import com.workspace.document.published.DocumentType;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Promote: an AGENT message becomes a DRAFT version (never an approved one). OWNER only. */
@Service
public class PromoteService {

    private static final Set<ProjectRole> OWNER_ONLY = Set.of(ProjectRole.OWNER);

    private final ConversationReader conversations;
    private final AgentRuntime agents;
    private final DocumentDrafts drafts;
    private final ProjectAccess access;
    private final AuditLog audit;

    public PromoteService(ConversationReader conversations, AgentRuntime agents, DocumentDrafts drafts,
                          ProjectAccess access, AuditLog audit) {
        this.conversations = conversations;
        this.agents = agents;
        this.drafts = drafts;
        this.access = access;
        this.audit = audit;
    }

    /** Exactly one of documentId / newDocumentType must be given (validated by the caller's DTO). */
    @Transactional
    public DocumentDrafts.Draft promote(UUID userId, UUID messageId, UUID documentId, DocumentType newType,
                                        String newTitle, String changeSummary) {
        MessageInfo message = conversations.findMessage(messageId).orElseThrow(ApiException::notFound);
        ConversationInfo conv = conversations.find(message.conversationId()).orElseThrow(ApiException::notFound);
        access.requireRole(conv.projectId(), userId, OWNER_ONLY);
        if (message.role() != MessageRole.AGENT) throw ApiException.validation("Only AGENT messages can be promoted");

        DocumentType type;
        if (documentId != null) {
            DocumentDrafts.DocumentRef ref = drafts.find(documentId).orElseThrow(ApiException::notFound);
            if (!ref.projectId().equals(conv.projectId())) throw ApiException.notFound();
            type = ref.type();
        } else {
            type = newType;
        }
        AgentRuntime.Runtime rt = agents.load(conv.agentId()).orElseThrow(ApiException::notFound);
        boolean allowed = rt.config().permissions().stream().anyMatch(p -> p.code() == PermissionCode.PROPOSE_DOCUMENT
                && (p.resourceType().equals("*") || p.resourceType().equals(type.name())));
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_CANNOT_PROPOSE",
                    "The role of this agent may not propose " + type + " documents");
        }
        DocumentDrafts.Draft draft = documentId != null
                ? drafts.draftOnto(documentId, message.content(), conv.agentId(), messageId, changeSummary, userId)
                : drafts.newDocument(conv.projectId(), type, newTitle, message.content(), conv.agentId(), messageId,
                        changeSummary, userId);
        audit.record(conv.projectId(), AuditLog.ACTOR_USER, userId.toString(), "MESSAGE_PROMOTED", "MESSAGE", messageId,
                null, Map.of("documentId", draft.documentId().toString(), "versionId", draft.versionId().toString()));
        return draft;
    }
}

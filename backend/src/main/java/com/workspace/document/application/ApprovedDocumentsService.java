package com.workspace.document.application;

import com.workspace.document.domain.Document;
import com.workspace.document.domain.DocumentVersion;
import com.workspace.document.infrastructure.DocumentRepository;
import com.workspace.document.infrastructure.DocumentVersionRepository;
import com.workspace.document.published.ApprovedDocuments;
import com.workspace.document.published.DocumentType;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ApprovedDocumentsService implements ApprovedDocuments {

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;

    ApprovedDocumentsService(DocumentRepository documents, DocumentVersionRepository versions) {
        this.documents = documents;
        this.versions = versions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Version> currentApproved(UUID projectId, Collection<DocumentType> types) {
        if (types.isEmpty()) return List.of();
        List<Document> docs = documents
                .findByProjectIdAndTypeInAndCurrentApprovedVersionIdIsNotNullOrderByCreatedAtAscIdAsc(projectId, types);
        Map<UUID, DocumentVersion> byId = versions.findAllById(docs.stream()
                .map(Document::getCurrentApprovedVersionId).toList()).stream()
                .collect(Collectors.toMap(DocumentVersion::getId, Function.identity()));
        return docs.stream()
                .sorted(Comparator.comparing((Document d) -> d.getType().ordinal()).thenComparing(Document::getCreatedAt)
                        .thenComparing(Document::getId))
                .map(d -> toVersion(d, byId.get(d.getCurrentApprovedVersionId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Version> versions(Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) return List.of();
        List<DocumentVersion> vs = versions.findAllById(versionIds);
        Map<UUID, Document> docs = documents.findAllById(vs.stream().map(DocumentVersion::getDocumentId).distinct().toList())
                .stream().collect(Collectors.toMap(Document::getId, Function.identity()));
        return vs.stream().map(v -> toVersion(docs.get(v.getDocumentId()), v)).toList();
    }

    private static Version toVersion(Document d, DocumentVersion v) {
        // integrity: the stored hash must still describe the stored content (a corrupt row must never reach a prompt)
        if (!com.workspace.document.domain.ContentHash.of(v.getContent()).equals(v.getContentHash())) {
            throw new IllegalStateException("CONTENT_HASH_CORRUPT");
        }
        return new Version(v.getId(), d.getId(), d.getType(), d.getTitle(), v.getVersionNo(), v.getContentHash(),
                v.getContent(), d.getCreatedAt());
    }
}

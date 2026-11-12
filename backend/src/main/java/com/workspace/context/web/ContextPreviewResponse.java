package com.workspace.context.web;

import com.workspace.context.published.ContextAssembler.Assembled;
import com.workspace.document.published.DocumentType;
import java.util.List;
import java.util.UUID;

public record ContextPreviewResponse(List<PreviewItem> items, int tokenEstimate, List<PreviewBlocker> blockers,
                                     List<String> missingDocumentTypes) {

    public record PreviewItem(DocumentType documentType, UUID versionId, int versionNo, String contentHash, String mode) {}

    public record PreviewBlocker(String reason, String documentType) {}

    static ContextPreviewResponse from(Assembled a) {
        return new ContextPreviewResponse(
                a.items().stream().map(i -> new PreviewItem(i.documentType(), i.documentVersionId(), i.versionNo(),
                        i.contentHash(), i.inclusionMode())).toList(),
                a.tokenEstimate(),
                a.blockers().stream().map(b -> new PreviewBlocker(b.reason(), b.documentType())).toList(),
                a.missingDocumentTypes());
    }
}

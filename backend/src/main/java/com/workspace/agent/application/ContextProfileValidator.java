package com.workspace.agent.application;

import com.workspace.agent.published.ContextDocument;
import com.workspace.agent.published.ContextProfile;
import com.workspace.agent.published.Permission;
import com.workspace.agent.published.PermissionCode;
import com.workspace.document.published.DocumentType;
import com.workspace.shared.published.ApiException;
import com.workspace.shared.published.FieldIssue;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Brief section 7: every listed type must be covered by READ_DOCUMENT of the same config version. */
final class ContextProfileValidator {

    private ContextProfileValidator() {}

    static void validate(ContextProfile profile, List<Permission> permissions) {
        if (profile.includeUnapproved()) {
            throw ApiException.validation("includeUnapproved must be false in this version",
                    List.of(new FieldIssue("contextProfile.includeUnapproved", "must be false")));
        }
        Set<DocumentType> seen = EnumSet.noneOf(DocumentType.class);
        for (ContextDocument d : profile.documents()) {
            if (!seen.add(d.type())) {
                throw ApiException.validation("Document type listed twice: " + d.type(),
                        List.of(new FieldIssue("contextProfile.documents", "duplicate type " + d.type())));
            }
        }

        Set<String> seenPerms = new HashSet<>();
        Set<String> readable = new HashSet<>();
        for (Permission p : permissions) {
            String rt = p.resourceType();
            if (!rt.equals("*")) {
                try {
                    DocumentType.valueOf(rt);
                } catch (IllegalArgumentException e) {
                    throw ApiException.validation("Unknown resourceType '" + rt + "'",
                            List.of(new FieldIssue("permissions", "resourceType must be * or a document type")));
                }
            }
            if (!seenPerms.add(p.code() + ":" + rt)) {
                throw ApiException.validation("Permission listed twice: " + p.code() + " " + rt,
                        List.of(new FieldIssue("permissions", "duplicate permission")));
            }
            if (p.code() == PermissionCode.READ_DOCUMENT) readable.add(rt);
        }
        for (ContextDocument d : profile.documents()) {
            if (!readable.contains("*") && !readable.contains(d.type().name())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONTEXT_PROFILE_EXCEEDS_PERMISSION",
                        "The context profile lists " + d.type() + " but READ_DOCUMENT does not cover it",
                        Map.of("documentType", d.type().name()));
            }
        }
    }
}

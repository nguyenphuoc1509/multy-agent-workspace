package com.workspace.document.published;

import java.util.Optional;

public enum DocumentType {
    REQUIREMENTS, ARCHITECTURE, API_CONTRACT, ADR;

    /** Prerequisite gates are COMPUTED, never stored (brief section 6). */
    public Optional<DocumentType> prerequisite() {
        return switch (this) {
            case ARCHITECTURE -> Optional.of(REQUIREMENTS);
            case API_CONTRACT -> Optional.of(ARCHITECTURE);
            case REQUIREMENTS, ADR -> Optional.empty();
        };
    }

    public String gateCode() {
        return name() + "_APPROVED";
    }

    public String contentFormat() {
        return this == API_CONTRACT ? "application/yaml" : "text/markdown";
    }
}
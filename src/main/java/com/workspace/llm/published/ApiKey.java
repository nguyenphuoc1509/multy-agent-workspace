package com.workspace.llm.published;

/** A provider API key as a transient value. Never logged: toString is redacted. */
public record ApiKey(String value) {
    @Override
    public String toString() {
        return "ApiKey[redacted]";
    }
}

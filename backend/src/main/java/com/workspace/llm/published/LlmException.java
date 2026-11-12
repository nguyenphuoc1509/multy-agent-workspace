package com.workspace.llm.published;

/** A provider failure. The message never contains the API key or request content. */
public class LlmException extends RuntimeException {

    public enum Kind { TRANSIENT, PERMANENT }

    private final Kind kind;
    private final String code;

    public LlmException(Kind kind, String code) {
        super(code);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() { return kind; }
    public String code() { return code; }
}

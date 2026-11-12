package com.workspace.llm.published;

import java.util.function.BooleanSupplier;

/** Port to one LLM provider. The key is a transient value: never stored in a field, never logged. */
public interface LlmProvider {

    /** Provider code as stored in the database, e.g. ANTHROPIC. */
    String code();

    /** Cheap call that proves the key works. Must not log or retain the key. */
    KeyValidation validate(ApiKey key, String modelOrNull);

    /**
     * Streams a completion, calling the listener for every text delta. Checks shouldAbort between chunks and then
     * throws LlmAbortedException (the connection is closed). Transient errors (429, 5xx, network) are reported as
     * LlmException.Kind.TRANSIENT so the caller may retry with the same prompt.
     */
    StreamResult stream(StreamRequest request, ApiKey key, StreamListener listener, BooleanSupplier shouldAbort);
}

package com.workspace.llm.published;

/** Port to one LLM provider. Streaming is added in M3; M2 needs key validation only. */
public interface LlmProvider {

    /** Provider code as stored in the database, e.g. ANTHROPIC. */
    String code();

    /** Cheap call that proves the key works. Must not log or retain the key. */
    KeyValidation validate(ApiKey key, String modelOrNull);
}

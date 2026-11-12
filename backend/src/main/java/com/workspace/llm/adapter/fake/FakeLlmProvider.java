package com.workspace.llm.adapter.fake;

import com.workspace.llm.published.ApiKey;
import com.workspace.llm.published.KeyValidation;
import com.workspace.llm.published.LlmProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Scripted provider for tests and local runs without a real key (workspace.llm.fake=true). No network.
 * Keys starting with "sk-bad" are rejected, keys starting with "sk-down" are "unavailable", all others are valid.
 */
@Component
@ConditionalOnProperty(name = "workspace.llm.fake", havingValue = "true")
public class FakeLlmProvider implements LlmProvider {

    @Override
    public String code() {
        return "ANTHROPIC";
    }

    @Override
    public KeyValidation validate(ApiKey key, String modelOrNull) {
        if (key.value().startsWith("sk-bad")) return KeyValidation.REJECTED;
        if (key.value().startsWith("sk-down")) return KeyValidation.UNAVAILABLE;
        return KeyValidation.VALID;
    }
}

package com.workspace.llm.published;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Looks up the adapter for a provider code. */
@Component
public class LlmProviders {

    private final List<LlmProvider> providers;

    public LlmProviders(List<LlmProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public Optional<LlmProvider> find(String code) {
        return providers.stream().filter(p -> p.code().equals(code)).findFirst();
    }
}

package com.workspace.llm.adapter.anthropic;

import com.workspace.llm.published.ApiKey;
import com.workspace.llm.published.KeyValidation;
import com.workspace.llm.published.LlmProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real Anthropic adapter. Validation lists models (GET /v1/models): no tokens are spent and no model is needed.
 * Only this package talks to Anthropic over HTTP. Disabled when workspace.llm.fake=true.
 */
@Component
@ConditionalOnProperty(name = "workspace.llm.fake", havingValue = "false", matchIfMissing = true)
public class AnthropicProvider implements LlmProvider {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;

    public AnthropicProvider(@Value("${workspace.llm.anthropic.base-url:https://api.anthropic.com}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String code() {
        return "ANTHROPIC";
    }

    @Override
    public KeyValidation validate(ApiKey key, String modelOrNull) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/models?limit=1"))
                .timeout(Duration.ofSeconds(10))
                .header("x-api-key", key.value())
                .header("anthropic-version", "2023-06-01")
                .GET().build();
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) return KeyValidation.VALID;
            if (status == 401 || status == 403) return KeyValidation.REJECTED;
            return KeyValidation.UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return KeyValidation.UNAVAILABLE;
        } catch (IOException | RuntimeException e) {
            return KeyValidation.UNAVAILABLE; // never include the exception text: it could echo request details
        }
    }
}

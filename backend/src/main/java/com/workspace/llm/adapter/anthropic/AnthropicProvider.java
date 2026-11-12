package com.workspace.llm.adapter.anthropic;

import com.workspace.llm.published.ApiKey;
import com.workspace.llm.published.KeyValidation;
import com.workspace.llm.published.LlmException;
import com.workspace.llm.published.LlmProvider;
import com.workspace.llm.published.LlmAbortedException;
import com.workspace.llm.published.LlmTurn;
import com.workspace.llm.published.StreamListener;
import com.workspace.llm.published.Usage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.workspace.llm.published.StreamRequest;
import com.workspace.llm.published.StreamResult;
import java.util.function.BooleanSupplier;
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
    private final JsonMapper json;

    public AnthropicProvider(@Value("${workspace.llm.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
                             JsonMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
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

    @Override
    public StreamResult stream(StreamRequest request, ApiKey key, StreamListener listener, BooleanSupplier shouldAbort) {
        ObjectNode body = json.createObjectNode();
        body.put("model", request.model());
        body.put("max_tokens", request.maxOutputTokens());
        body.put("stream", true);
        if (request.system() != null && !request.system().isBlank()) body.put("system", request.system());
        ArrayNode messages = body.putArray("messages");
        for (LlmTurn t : request.turns()) messages.addObject().put("role", t.role()).put("content", t.content());

        HttpRequest http = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(120))
                .header("x-api-key", key.value())
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<InputStream> response;
        try {
            response = this.http.send(http, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmAbortedException();
        } catch (IOException | RuntimeException e) {
            throw new LlmException(LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE"); // no exception text: may echo the request
        }
        int status = response.statusCode();
        if (status != 200) {
            closeQuietly(response.body());
            if (status == 401 || status == 403) throw new LlmException(LlmException.Kind.PERMANENT, "PROVIDER_REJECTED_KEY");
            if (status == 429 || status >= 500) throw new LlmException(LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE");
            throw new LlmException(LlmException.Kind.PERMANENT, "PROVIDER_BAD_REQUEST");
        }

        StringBuilder text = new StringBuilder();
        int inputTokens = 0;
        int outputTokens = 0;
        String event = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (shouldAbort.getAsBoolean()) throw new LlmAbortedException();
                if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                    continue;
                }
                if (!line.startsWith("data:") || event == null) continue;
                JsonNode data = json.readTree(line.substring(5).trim());
                switch (event) {
                    case "message_start" -> {
                        JsonNode u = data.path("message").path("usage");
                        inputTokens = u.path("input_tokens").asInt(inputTokens);
                        outputTokens = u.path("output_tokens").asInt(outputTokens);
                    }
                    case "content_block_delta" -> {
                        JsonNode d = data.path("delta");
                        if ("text_delta".equals(d.path("type").asString())) {
                            String piece = d.path("text").asString("");
                            if (!piece.isEmpty()) {
                                text.append(piece);
                                listener.onDelta(piece);
                            }
                        }
                    }
                    case "message_delta" -> {
                        JsonNode u = data.path("usage");
                        outputTokens = u.path("output_tokens").asInt(outputTokens);
                        inputTokens = u.path("input_tokens").asInt(inputTokens);
                    }
                    case "error" -> {
                        String type = data.path("error").path("type").asString("");
                        boolean transientError = type.equals("overloaded_error") || type.equals("api_error")
                                || type.equals("rate_limit_error");
                        throw new LlmException(transientError ? LlmException.Kind.TRANSIENT : LlmException.Kind.PERMANENT,
                                transientError ? "PROVIDER_UNAVAILABLE" : "PROVIDER_BAD_REQUEST");
                    }
                    case "message_stop" -> {
                        return new StreamResult(text.toString(), new Usage(inputTokens, outputTokens));
                    }
                    default -> { }
                }
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof LlmException le) throw le;
            if (e instanceof LlmAbortedException ae) throw ae;
            throw new LlmException(LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE"); // dropped connection
        }
        throw new LlmException(LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE"); // ended before message_stop
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}

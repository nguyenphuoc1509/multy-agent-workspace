package com.workspace.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.workspace.llm.adapter.anthropic.AnthropicProvider;
import com.workspace.llm.published.ApiKey;
import com.workspace.llm.published.LlmAbortedException;
import com.workspace.llm.published.LlmException;
import com.workspace.llm.published.LlmTurn;
import com.workspace.llm.published.StreamRequest;
import com.workspace.llm.published.StreamResult;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The streaming adapter against a local HTTP server that speaks Anthropic's SSE format. No network, no real key. */
class AnthropicProviderTest {

    private HttpServer server;
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private final AtomicReference<String> seenKey = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private AnthropicProvider serve(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            seenBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            seenKey.set(ex.getRequestHeaders().getFirst("x-api-key"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", "text/event-stream");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return new AnthropicProvider("http://127.0.0.1:" + server.getAddress().getPort(), JsonMapper.builder().build());
    }

    private static final String OK = """
            event: message_start
            data: {"type":"message_start","message":{"usage":{"input_tokens":25,"output_tokens":1}}}

            event: ping
            data: {"type":"ping"}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hel"}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"lo"}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":15}}

            event: message_stop
            data: {"type":"message_stop"}

            """;

    private static StreamRequest req() {
        return new StreamRequest("claude-x", "be nice", List.of(new LlmTurn("user", "hi")), 100);
    }

    @Test
    void streamsDeltasAndReportsUsage() throws Exception {
        AnthropicProvider p = serve(200, OK);
        List<String> deltas = new ArrayList<>();
        StreamResult r = p.stream(req(), new ApiKey("sk-ant-secret"), deltas::add, () -> false);
        assertThat(deltas).containsExactly("Hel", "lo");
        assertThat(r.text()).isEqualTo("Hello");
        assertThat(r.usage().inputTokens()).isEqualTo(25);
        assertThat(r.usage().outputTokens()).isEqualTo(15);
        assertThat(seenKey.get()).isEqualTo("sk-ant-secret");
        assertThat(seenBody.get()).contains("\"stream\":true").contains("\"model\":\"claude-x\"").contains("\"system\":\"be nice\"")
                .contains("\"max_tokens\":100");
    }

    @Test
    void statusCodesMapToTransientOrPermanent() throws Exception {
        assertFailure(401, LlmException.Kind.PERMANENT, "PROVIDER_REJECTED_KEY");
        assertFailure(400, LlmException.Kind.PERMANENT, "PROVIDER_BAD_REQUEST");
        assertFailure(429, LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE");
        assertFailure(529, LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE");
    }

    private void assertFailure(int status, LlmException.Kind kind, String code) throws Exception {
        AnthropicProvider p = serve(status, "{\"error\":{\"message\":\"sk-ant-secret leaked\"}}");
        assertThatThrownBy(() -> p.stream(req(), new ApiKey("sk-ant-secret"), d -> { }, () -> false))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.kind()).isEqualTo(kind);
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.getMessage()).doesNotContain("sk-ant-secret"); // never echo provider bodies
                });
        server.stop(0);
    }

    @Test
    void midStreamErrorAndTruncationAreHandled() throws Exception {
        AnthropicProvider overloaded = serve(200, "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}\n\n");
        assertThatThrownBy(() -> overloaded.stream(req(), new ApiKey("k"), d -> { }, () -> false))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(LlmException.Kind.TRANSIENT));
        server.stop(0);
        AnthropicProvider truncated = serve(200, OK.substring(0, OK.indexOf("event: message_delta")));
        assertThatThrownBy(() -> truncated.stream(req(), new ApiKey("k"), d -> { }, () -> false))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(LlmException.Kind.TRANSIENT));
    }

    @Test
    void abortStopsStreaming() throws Exception {
        AnthropicProvider p = serve(200, OK);
        List<String> deltas = new ArrayList<>();
        assertThatThrownBy(() -> p.stream(req(), new ApiKey("k"), deltas::add, () -> !deltas.isEmpty()))
                .isInstanceOf(LlmAbortedException.class);
        assertThat(deltas).containsExactly("Hel");
    }
}

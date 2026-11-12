package com.workspace.llm.adapter.fake;

import com.workspace.llm.published.ApiKey;
import com.workspace.llm.published.KeyValidation;
import com.workspace.llm.published.LlmAbortedException;
import com.workspace.llm.published.LlmException;
import com.workspace.llm.published.LlmProvider;
import com.workspace.llm.published.LlmTurn;
import com.workspace.llm.published.StreamListener;
import com.workspace.llm.published.StreamRequest;
import com.workspace.llm.published.StreamResult;
import com.workspace.llm.published.Usage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Scripted provider for tests and for trying the app without a real key (workspace.llm.fake=true). No network.
 * Key: "sk-bad..." rejected, "sk-down..." unavailable, anything else valid.
 * Reply: "[fake] You said: <last user message>" in 8-character chunks. Markers in the last user message:
 *   [slow]            about 5 s of output, one chunk every 50 ms (to test cancellation and the time limit)
 *   [fail-permanent]  PERMANENT error PROVIDER_BAD_REQUEST
 *   [fail-transient]  TRANSIENT error on every attempt
 *   [fail-once]       TRANSIENT error on the first attempt only (per message text), then succeeds
 */
@Component
@ConditionalOnProperty(name = "workspace.llm.fake", havingValue = "true")
public class FakeLlmProvider implements LlmProvider {

    private final AtomicInteger failOnceCalls = new AtomicInteger();
    private final long chunkDelayMs;

    public FakeLlmProvider(@Value("${workspace.llm.fake-chunk-delay-ms:0}") long chunkDelayMs) {
        this.chunkDelayMs = chunkDelayMs;
    }

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

    @Override
    public StreamResult stream(StreamRequest request, ApiKey key, StreamListener listener, BooleanSupplier shouldAbort) {
        String last = request.turns().isEmpty() ? "" : request.turns().get(request.turns().size() - 1).content();
        if (last.contains("[fail-permanent]")) throw new LlmException(LlmException.Kind.PERMANENT, "PROVIDER_BAD_REQUEST");
        if (last.contains("[fail-transient]")) throw new LlmException(LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE");
        if (last.contains("[fail-once]") && failOnceCalls.getAndIncrement() == 0) {
            throw new LlmException(LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE");
        }
        boolean slow = last.contains("[slow]");
        String reply = slow ? "slow reply ".repeat(40) : "[fake] You said: " + last;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < reply.length(); i += 8) {
            if (shouldAbort.getAsBoolean()) throw new LlmAbortedException();
            String chunk = reply.substring(i, Math.min(reply.length(), i + 8));
            out.append(chunk);
            listener.onDelta(chunk);
            long delay = slow ? 50 : chunkDelayMs;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LlmAbortedException();
                }
            }
        }
        int in = (request.system().length() + request.turns().stream().mapToInt(t -> t.content().length()).sum() + 3) / 4;
        return new StreamResult(out.toString(), new Usage(in, (out.length() + 3) / 4));
    }
}

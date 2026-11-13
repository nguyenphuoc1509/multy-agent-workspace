package com.workspace.llm.adapter.claudecode;

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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the official Claude Code CLI ({@code claude -p}) on the machine where the backend runs, under whatever
 * login that user has already done (no API key stored here). Off unless workspace.llm.claude-code.enabled=true.
 *
 * Safety: all tools are disabled, slash commands, MCP servers and settings are not loaded, the working directory is a
 * fresh empty temp directory, nothing is persisted by the CLI. The agent can only produce text.
 * The prompt goes through stdin (command lines have length limits); stderr is discarded and never put in errors.
 */
@Component
@ConditionalOnProperty(name = "workspace.llm.claude-code.enabled", havingValue = "true")
public class ClaudeCodeProvider implements LlmProvider {

    private final String command;
    private final JsonMapper json;

    public ClaudeCodeProvider(@Value("${workspace.llm.claude-code.command:claude}") String command, JsonMapper json) {
        this.command = command;
        this.json = json;
    }

    @Override
    public String code() {
        return "CLAUDE_CODE";
    }

    @Override
    public boolean requiresCredential() {
        return false;
    }

    @Override
    public KeyValidation validate(ApiKey key, String modelOrNull) {
        return KeyValidation.VALID; // there is no key: the CLI's own login is used
    }

    @Override
    public StreamResult stream(StreamRequest request, ApiKey key, StreamListener listener, BooleanSupplier shouldAbort) {
        Path workDir = null;
        Process process = null;
        try {
            workDir = Files.createTempDirectory("workspace-claude-");
            process = new ProcessBuilder(commandLine(request.model())).directory(workDir.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException | RuntimeException e) {
            cleanup(workDir);
            throw new LlmException(LlmException.Kind.PERMANENT, "CLI_NOT_FOUND");
        }
        final Process proc = process;
        AtomicBoolean aborted = new AtomicBoolean();
        Thread watchdog = Thread.ofVirtual().start(() -> {
            try {
                while (proc.isAlive()) {
                    if (shouldAbort.getAsBoolean()) {
                        aborted.set(true);
                        kill(proc);
                        return;
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            try (OutputStream in = proc.getOutputStream()) {
                in.write(prompt(request).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (aborted.get()) throw new LlmAbortedException();
                throw new LlmException(LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE");
            }
            return read(proc, listener, aborted);
        } finally {
            watchdog.interrupt();
            kill(proc);
            cleanup(workDir);
        }
    }

    private StreamResult read(Process proc, StreamListener listener, AtomicBoolean aborted) {
        StringBuilder text = new StringBuilder();
        int inputTokens = 0;
        int outputTokens = 0;
        try (BufferedReader out = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
                JsonNode ev;
                try {
                    ev = json.readTree(line);
                } catch (RuntimeException notJson) {
                    continue;
                }
                String type = ev.path("type").asString("");
                if (type.equals("stream_event")) {
                    JsonNode e = ev.path("event");
                    if (e.path("type").asString("").equals("content_block_delta")
                            && e.path("delta").path("type").asString("").equals("text_delta")) {
                        String piece = e.path("delta").path("text").asString("");
                        if (!piece.isEmpty()) {
                            text.append(piece);
                            listener.onDelta(piece);
                        }
                    }
                } else if (type.equals("result")) {
                    JsonNode u = ev.path("usage");
                    inputTokens = u.path("input_tokens").asInt(0) + u.path("cache_creation_input_tokens").asInt(0)
                            + u.path("cache_read_input_tokens").asInt(0);
                    outputTokens = u.path("output_tokens").asInt(0);
                    if (ev.path("is_error").asBoolean(false)) {
                        throw new LlmException(LlmException.Kind.PERMANENT, errorCode(ev.path("result").asString("")));
                    }
                    if (text.isEmpty()) text.append(ev.path("result").asString(""));
                    return new StreamResult(text.toString(), new Usage(inputTokens, outputTokens));
                }
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof LlmException le) throw le;
            if (aborted.get()) throw new LlmAbortedException();
            throw new LlmException(LlmException.Kind.TRANSIENT, "PROVIDER_UNAVAILABLE");
        }
        if (aborted.get()) throw new LlmAbortedException();
        throw new LlmException(LlmException.Kind.PERMANENT, "CLI_ERROR"); // exited without a result (not logged in, crashed...)
    }

    /** Only a coarse category: the CLI text can contain prompt fragments, so it is never copied into errors. */
    private static String errorCode(String message) {
        String m = message.toLowerCase();
        return m.contains("login") || m.contains("authenticat") || m.contains("api key") ? "CLI_NOT_LOGGED_IN" : "CLI_ERROR";
    }

    private List<String> commandLine(String model) {
        List<String> c = new ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            c.add("cmd");
            c.add("/c");
        }
        c.add(command);
        c.addAll(List.of("-p", "--output-format", "stream-json", "--verbose", "--include-partial-messages",
                "--tools", "", "--no-session-persistence", "--disable-slash-commands", "--strict-mcp-config",
                "--setting-sources", "", "--system-prompt",
                "You are the assistant described in the <instructions> block of the user message. Follow them. Answer with text only."));
        if (model != null && !model.isBlank()) {
            c.add("--model");
            c.add(model);
        }
        return c;
    }

    /** One stdin document: the role's instructions (with the approved documents) and the conversation so far. */
    static String prompt(StreamRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<instructions>\n").append(r.system() == null ? "" : r.system()).append("\n</instructions>\n\n<conversation>\n");
        for (LlmTurn t : r.turns()) {
            sb.append('[').append(t.role()).append("]\n").append(t.content()).append("\n\n");
        }
        sb.append("</conversation>\n\nWrite the assistant's next reply to the last [user] message. Output only the reply.\n");
        return sb.toString();
    }

    /** The npm shim / cmd wrapper starts the real CLI as a child: kill the whole tree. */
    private static void kill(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    private static void cleanup(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp dir; the OS cleans it eventually
        }
    }
}

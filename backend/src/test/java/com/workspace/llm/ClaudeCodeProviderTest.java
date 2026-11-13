package com.workspace.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.workspace.llm.adapter.claudecode.ClaudeCodeProvider;
import com.workspace.llm.published.ApiKey;
import com.workspace.llm.published.LlmAbortedException;
import com.workspace.llm.published.LlmException;
import com.workspace.llm.published.LlmTurn;
import com.workspace.llm.published.StreamRequest;
import com.workspace.llm.published.StreamResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** The CLI adapter against a stand-in script that prints what the real `claude -p --output-format stream-json` prints. */
class ClaudeCodeProviderTest {

    @TempDir
    Path dir;

    @BeforeEach
    void posixOnly() {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
    }

    private ClaudeCodeProvider scripted(String body) throws Exception {
        Path script = dir.resolve("claude");
        Files.writeString(script, "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(script, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        return new ClaudeCodeProvider(script.toString(), JsonMapper.builder().build());
    }

    private static StreamRequest req() {
        return new StreamRequest("sonnet", "be brief", List.of(new LlmTurn("user", "hello there")), 100);
    }

    private String ok(String stdinFile, String argsFile) {
        return "cat > " + stdinFile + "\n"
                + "printf '%s\\n' \"$@\" > " + argsFile + "\n"
                + "echo '{\"type\":\"system\",\"subtype\":\"init\"}'\n"
                + "echo '{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hel\"}}}'\n"
                + "echo '{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"lo\"}}}'\n"
                + "echo '{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"result\":\"Hello\",\"usage\":{\"input_tokens\":2,\"cache_creation_input_tokens\":10,\"cache_read_input_tokens\":3,\"output_tokens\":4}}'\n";
    }

    @Test
    void streamsDeltasSumsInputTokens_andLocksTheCliDown() throws Exception {
        Path stdin = dir.resolve("stdin.txt");
        Path args = dir.resolve("args.txt");
        ClaudeCodeProvider p = scripted(ok(stdin.toString(), args.toString()));
        List<String> deltas = new ArrayList<>();
        StreamResult r = p.stream(req(), new ApiKey(""), deltas::add, () -> false);
        assertThat(deltas).containsExactly("Hel", "lo");
        assertThat(r.text()).isEqualTo("Hello");
        assertThat(r.usage().inputTokens()).isEqualTo(15);
        assertThat(r.usage().outputTokens()).isEqualTo(4);
        String sentArgs = Files.readString(args);
        assertThat(sentArgs).contains("-p", "stream-json", "--no-session-persistence", "--strict-mcp-config", "--disable-slash-commands")
                .contains("--model\nsonnet");
        assertThat(List.of(sentArgs.split("\n"))).containsSubsequence("--tools", ""); // tools disabled
        String sentStdin = Files.readString(stdin);
        assertThat(sentStdin).contains("be brief").contains("[user]\nhello there");
    }

    @Test
    void errorsAreCategorisedAndNeverEchoCliText() throws Exception {
        ClaudeCodeProvider loggedOut = scripted("cat > /dev/null\necho '{\"type\":\"result\",\"is_error\":true,\"result\":\"Please login: secret-prompt-text\"}'\n");
        assertThatThrownBy(() -> loggedOut.stream(req(), new ApiKey(""), d -> { }, () -> false))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.code()).isEqualTo("CLI_NOT_LOGGED_IN");
                    assertThat(e.kind()).isEqualTo(LlmException.Kind.PERMANENT);
                    assertThat(e.getMessage()).doesNotContain("secret-prompt-text");
                });
        ClaudeCodeProvider crash = scripted("cat > /dev/null\nexit 3\n");
        assertThatThrownBy(() -> crash.stream(req(), new ApiKey(""), d -> { }, () -> false))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.code()).isEqualTo("CLI_ERROR"));
        ClaudeCodeProvider missing = new ClaudeCodeProvider(dir.resolve("nope").toString(), JsonMapper.builder().build());
        assertThatThrownBy(() -> missing.stream(req(), new ApiKey(""), d -> { }, () -> false))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.code()).isEqualTo("CLI_NOT_FOUND"));
    }

    @Test
    void abortKillsTheProcess() throws Exception {
        ClaudeCodeProvider p = scripted("cat > /dev/null\nexec sleep 30\n");
        long t0 = System.nanoTime();
        assertThatThrownBy(() -> p.stream(req(), new ApiKey(""), d -> { }, () -> System.nanoTime() - t0 > 400_000_000L))
                .isInstanceOf(LlmAbortedException.class);
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(10_000);
    }

    @Test
    void needsNoCredential() throws Exception {
        assertThat(scripted("exit 0\n").requiresCredential()).isFalse();
    }
}

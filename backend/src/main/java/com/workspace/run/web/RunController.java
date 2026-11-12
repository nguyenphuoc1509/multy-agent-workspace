package com.workspace.run.web;

import com.workspace.context.published.ContextAssembler.SnapshotView;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.run.application.RunService;
import com.workspace.shared.published.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class RunController {

    private final RunService runs;
    private final CurrentUserProvider currentUser;

    public RunController(RunService runs, CurrentUserProvider currentUser) {
        this.runs = runs;
        this.currentUser = currentUser;
    }

    /** 202 for a new message + queued run; 200 when clientRequestId was already used (idempotent repeat). */
    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<SendMessageResponse> send(@PathVariable UUID conversationId,
                                                    @Valid @RequestBody SendMessageRequest body) {
        RunService.SendResult r = runs.send(currentUser.require().id(), conversationId, body.content(), body.clientRequestId());
        RunResponse run = r.run() == null ? null : RunResponse.from(r.run());
        return ResponseEntity.status(r.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(SendMessageResponse.from(r.message(), run));
    }

    @GetMapping("/runs/{runId}")
    public RunResponse get(@PathVariable UUID runId) {
        return RunResponse.from(runs.get(currentUser.require().id(), runId));
    }

    @GetMapping(value = "/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID runId) {
        return runs.stream(currentUser.require().id(), runId);
    }

    @GetMapping("/runs/{runId}/snapshot")
    public SnapshotView snapshot(@PathVariable UUID runId) {
        return runs.snapshot(currentUser.require().id(), runId);
    }

    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<RunResponse> cancel(@PathVariable UUID runId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(RunResponse.from(runs.cancel(currentUser.require().id(), runId)));
    }

    @PostMapping("/runs/{runId}/retry")
    public ResponseEntity<RunResponse> retry(@PathVariable UUID runId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(RunResponse.from(runs.retry(currentUser.require().id(), runId)));
    }

    @GetMapping("/projects/{projectId}/runs")
    public PageResponse<RunResponse> list(@PathVariable UUID projectId, @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) Integer limit) {
        PageResponse<RunService.RunView> page = runs.list(currentUser.require().id(), projectId, status, cursor, limit);
        return new PageResponse<>(page.items().stream().map(RunResponse::from).toList(), page.nextCursor());
    }

    @GetMapping("/projects/{projectId}/usage")
    public RunService.Usage usage(@PathVariable UUID projectId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return runs.usage(currentUser.require().id(), projectId, date);
    }
}

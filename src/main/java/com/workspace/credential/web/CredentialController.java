package com.workspace.credential.web;

import com.workspace.credential.application.CredentialService;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.shared.published.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CredentialController {

    private final CredentialService credentials;
    private final CurrentUserProvider currentUser;

    public CredentialController(CredentialService credentials, CurrentUserProvider currentUser) {
        this.credentials = credentials;
        this.currentUser = currentUser;
    }

    @PostMapping("/projects/{projectId}/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialResponse add(@PathVariable UUID projectId, @Valid @RequestBody AddCredentialRequest body) {
        return CredentialResponse.from(credentials.add(currentUser.require().id(), projectId, body.label(),
                body.provider(), body.apiKey()));
    }

    @GetMapping("/projects/{projectId}/credentials")
    public PageResponse<CredentialResponse> list(@PathVariable UUID projectId) {
        return new PageResponse<>(credentials.list(currentUser.require().id(), projectId).stream()
                .map(CredentialResponse::from).toList(), null);
    }

    @PostMapping("/credentials/{credentialId}/revoke")
    public CredentialResponse revoke(@PathVariable UUID credentialId) {
        return CredentialResponse.from(credentials.revoke(currentUser.require().id(), credentialId));
    }
}

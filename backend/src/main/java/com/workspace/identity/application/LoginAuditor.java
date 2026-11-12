package com.workspace.identity.application;

import com.workspace.audit.published.AuditLog;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes LOGIN_SUCCEEDED / LOGIN_FAILED in its own transaction, so a failed login (which throws) still leaves its
 * audit row. Throttled attempts (429) are not audited: an attacker could otherwise fill the audit table.
 * Never records the password; for an unknown email only the (trimmed) attempted email is kept.
 */
@Component
class LoginAuditor {

    private static final String ACTOR_ANONYMOUS = "ANONYMOUS";
    private static final int MAX_EMAIL_CHARS = 254;

    private final AuditLog audit;

    LoginAuditor(AuditLog audit) {
        this.audit = audit;
    }

    @Transactional
    public void succeeded(UUID userId) {
        audit.record(null, AuditLog.ACTOR_USER, userId.toString(), "LOGIN_SUCCEEDED", "USER", userId, null, null);
    }

    /** userId is null when the email is unknown; the reason is deliberately the same in both cases. */
    @Transactional
    public void failed(UUID userId, String normalisedEmail) {
        String attempted = normalisedEmail.length() > MAX_EMAIL_CHARS
                ? normalisedEmail.substring(0, MAX_EMAIL_CHARS) : normalisedEmail;
        audit.record(null, userId != null ? AuditLog.ACTOR_USER : ACTOR_ANONYMOUS,
                userId != null ? userId.toString() : attempted,
                "LOGIN_FAILED", "USER", userId, null, Map.of("reason", "INVALID_CREDENTIALS"));
    }
}

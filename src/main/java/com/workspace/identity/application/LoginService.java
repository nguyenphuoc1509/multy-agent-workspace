package com.workspace.identity.application;

import com.workspace.identity.domain.AppUser;
import com.workspace.identity.infrastructure.AppUserRepository;
import com.workspace.identity.published.CurrentUser;
import com.workspace.shared.published.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    /** bcrypt only uses the first 72 bytes; longer inputs are rejected instead of silently truncated. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final String dummyHash;

    public LoginService(AppUserRepository users, PasswordEncoder encoder, LoginThrottle throttle) {
        this.users = users;
        this.encoder = encoder;
        this.throttle = throttle;
        // used to equalise timing when the email is unknown
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public CurrentUser login(String rawEmail, String password) {
        String key = normaliseEmail(rawEmail);
        throttle.checkAllowed(key);

        boolean passwordUsable = password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
        Optional<AppUser> user = users.findByEmail(key);

        boolean ok;
        if (user.isPresent() && passwordUsable) {
            ok = encoder.matches(password, user.get().getPasswordHash());
        } else {
            encoder.matches(passwordUsable ? password : "x", dummyHash);
            ok = false;
        }

        if (!ok) {
            throttle.recordFailure(key);
            throw ApiException.unauthenticated("Invalid email or password");
        }
        throttle.reset(key);
        AppUser u = user.get();
        return new CurrentUser(u.getId(), u.getEmail(), u.getDisplayName());
    }
}

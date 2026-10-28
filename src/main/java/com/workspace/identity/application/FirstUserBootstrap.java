package com.workspace.identity.application;

import com.workspace.identity.domain.AppUser;
import com.workspace.identity.infrastructure.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** No public registration: the first user is created from environment variables when app_user is empty. */
@Component
class FirstUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstUserBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final BootstrapProperties props;
    private final Clock clock;

    FirstUserBootstrap(AppUserRepository users, PasswordEncoder encoder, BootstrapProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;

        if (props.email().isBlank() || props.password().isBlank()) {
            log.warn("No users exist and WORKSPACE_BOOTSTRAP_EMAIL / WORKSPACE_BOOTSTRAP_PASSWORD are not set: "
                    + "nobody will be able to log in.");
            return;
        }
        if (props.password().length() < MIN_PASSWORD_LENGTH
                || props.password().getBytes(StandardCharsets.UTF_8).length > LoginService.MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("WORKSPACE_BOOTSTRAP_PASSWORD must be " + MIN_PASSWORD_LENGTH
                    + " characters or more and at most " + LoginService.MAX_PASSWORD_BYTES + " bytes in UTF-8.");
        }
        String email = LoginService.normaliseEmail(props.email());
        users.save(AppUser.create(email, encoder.encode(props.password()), props.displayName().trim(),
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        log.info("Created first user {}", email); // never log the password
    }
}

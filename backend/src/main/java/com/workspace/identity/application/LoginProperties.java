package com.workspace.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "workspace.login")
public record LoginProperties(@DefaultValue("5") int maxFailures, @DefaultValue("15m") Duration window) {}

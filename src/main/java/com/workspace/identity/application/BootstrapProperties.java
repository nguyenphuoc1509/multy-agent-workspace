package com.workspace.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "workspace.bootstrap")
public record BootstrapProperties(
        @DefaultValue("") String email,
        @DefaultValue("") String password,
        @DefaultValue("Owner") String displayName) {}

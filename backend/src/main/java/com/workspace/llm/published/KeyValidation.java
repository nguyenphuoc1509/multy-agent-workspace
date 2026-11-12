package com.workspace.llm.published;

/** Outcome of checking a key with the provider. UNAVAILABLE means we could not tell (network, 5xx, rate limit). */
public enum KeyValidation { VALID, REJECTED, UNAVAILABLE }

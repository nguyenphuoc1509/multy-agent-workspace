package com.workspace.conversation.web;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(@Size(max = 200) String title) {}

package com.workspace.shared.published;

import java.util.List;

/** Standard list envelope: {"items":[...],"nextCursor":"..."|null}. */
public record PageResponse<T>(List<T> items, String nextCursor) {}

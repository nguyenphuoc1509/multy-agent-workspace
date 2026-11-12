package com.workspace.shared.published;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem documents with the extra "code" member required by the API conventions. */
public final class ProblemJson {

    private ProblemJson() {}

    public static ProblemDetail of(HttpStatus status, String code, String detail, Map<String, Object> extras) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create("urn:workspace:problem:" + code));
        pd.setProperty("code", code);
        extras.forEach(pd::setProperty);
        return pd;
    }

    /** Hand-written JSON for servlet-filter-level errors (no ObjectMapper dependency). */
    public static String toJson(HttpStatus status, String code, String detail) {
        return "{\"type\":\"urn:workspace:problem:" + code + "\""
                + ",\"title\":\"" + escape(status.getReasonPhrase()) + "\""
                + ",\"status\":" + status.value()
                + ",\"code\":\"" + escape(code) + "\""
                + ",\"detail\":\"" + escape(detail) + "\"}";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}

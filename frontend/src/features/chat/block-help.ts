/** Where to go to fix a blocked run. */
export function blockHelp(reason: string, projectId: string): { text: string; href: string } {
  const base = `/projects/${projectId}`;
  switch (reason) {
    case "CREDENTIAL_REVOKED":
      return { text: "The agent has no usable credential (missing or revoked). Add one and assign it to the agent.", href: `${base}/settings/credentials` };
    case "AGENT_DISABLED":
      return { text: "This agent is disabled. Enable it to continue.", href: `${base}/agents` };
    case "BUDGET_EXCEEDED":
      return { text: "Today's token budget would be exceeded. Raise the project budget or wait until tomorrow (UTC).", href: base };
    case "MISSING_APPROVED_DOC":
      return { text: "A required document has no approved version yet.", href: `${base}/documents` };
    case "CONTEXT_TOO_LARGE":
      return { text: "The context does not fit the agent's token limit. Raise maxContextTokens or trim the role's context profile.", href: `${base}/agents` };
    case "RUN_LIMIT_EXCEEDED":
      return { text: "The run exceeded its time limit. Raise maxRunSeconds on the agent.", href: `${base}/agents` };
    default:
      return { text: reason, href: base };
  }
}

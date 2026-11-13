-- Agents may use the local Claude Code CLI provider (no credential). Credentials stay ANTHROPIC-only.
ALTER TABLE agent DROP CONSTRAINT agent_provider_check;
ALTER TABLE agent ADD CONSTRAINT agent_provider_check CHECK (provider IN ('ANTHROPIC', 'CLAUDE_CODE'));

import type { components } from "./api";

type S = components["schemas"];

export type Project = S["ProjectResponse"];
export type User = S["UserResponse"];
export type Doc = S["DocumentResponse"];
export type Version = S["VersionResponse"];
export type VersionSummary = S["VersionSummaryResponse"];
export type Comment = S["CommentResponse"];
export type Decision = S["DecisionResponse"];
export type Credential = S["CredentialResponse"];
export type Role = S["RoleResponse"];
export type ConfigVersion = S["ConfigVersionResponse"];
export type ContextProfile = S["ContextProfile"];
export type Permission = S["Permission"];
export type Agent = S["AgentResponse"];
export type AgentParams = S["AgentParams"];
export type ContextPreview = S["ContextPreviewResponse"];
export type Conversation = S["ConversationResponse"];
export type Message = S["MessageResponse"];
export type Run = S["RunResponse"];
export type Snapshot = S["SnapshotView"];
export type Usage = S["Usage"];
export type SendMessageResponse = S["SendMessageResponse"];
export type PromoteResponse = S["PromoteResponse"];

export type DocType = Doc["type"];
export const DOC_TYPES = ["REQUIREMENTS", "ARCHITECTURE", "API_CONTRACT", "ADR"] as const satisfies readonly NonNullable<DocType>[];

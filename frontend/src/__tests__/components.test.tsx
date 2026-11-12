import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { StatusBadge } from "@/components/ui";
import { ApproveDialog } from "@/features/document/approve-dialog";
import { AddCredentialForm } from "@/features/credential/add-credential-form";

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

afterEach(() => vi.restoreAllMocks());

describe("StatusBadge", () => {
  it("labels unofficial states clearly and APPROVED as official", () => {
    render(
      <>
        <StatusBadge status="DRAFT" />
        <StatusBadge status="IN_REVIEW" />
        <StatusBadge status="APPROVED" />
      </>,
    );
    expect(screen.getByText("Draft - not official")).toBeInTheDocument();
    expect(screen.getByText("In review - not official")).toBeInTheDocument();
    expect(screen.getByText("Approved (official)")).toBeInTheDocument();
  });
});

describe("ApproveDialog", () => {
  const props = {
    projectId: "p1",
    versionId: "v1",
    documentTitle: "Reqs",
    documentType: "REQUIREMENTS",
    versionNo: 3,
    displayedHash: "sha256:9a1f0000deadbeef",
    onClose: () => {},
    onStale: () => {},
  };

  it("names the entity and sends the displayed hash (R1)", async () => {
    document.cookie = "XSRF-TOKEN=t";
    const f = vi.spyOn(globalThis, "fetch").mockResolvedValue(json(200, { version: { id: "v1", documentId: "d1" } }));
    wrap(<ApproveDialog {...props} />);
    expect(screen.getByText(/Reqs/)).toBeInTheDocument();
    expect(screen.getByText("9a1f0000")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Approve v3" }));
    await waitFor(() => expect(f).toHaveBeenCalled());
    const call = f.mock.calls.find((c) => String(c[0]).includes("/approve"))!;
    expect(JSON.parse(String(call[1]!.body))).toMatchObject({ expectedHash: "sha256:9a1f0000deadbeef" });
  });

  it("lists missing prerequisites with links on GATE_NOT_SATISFIED", async () => {
    document.cookie = "XSRF-TOKEN=t";
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json(422, { code: "GATE_NOT_SATISFIED", detail: "x", missing: [{ documentType: "ARCHITECTURE", reason: "NO_APPROVED_VERSION" }] }),
    );
    wrap(<ApproveDialog {...props} />);
    fireEvent.click(screen.getByRole("button", { name: "Approve v3" }));
    const link = await screen.findByRole("link", { name: "ARCHITECTURE" });
    expect(link).toHaveAttribute("href", "/projects/p1/documents?type=ARCHITECTURE");
  });

  it("calls onStale on STALE_CONTENT", async () => {
    document.cookie = "XSRF-TOKEN=t";
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json(409, { code: "STALE_CONTENT", detail: "changed", currentHash: "sha256:new" }));
    const onStale = vi.fn();
    wrap(<ApproveDialog {...props} onStale={onStale} />);
    fireEvent.click(screen.getByRole("button", { name: "Approve v3" }));
    await waitFor(() => expect(onStale).toHaveBeenCalled());
    expect(screen.getByText(/content changed/i)).toBeInTheDocument();
  });
});

describe("AddCredentialForm (R4)", () => {
  async function fill() {
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "main" } });
    fireEvent.change(screen.getByLabelText(/API key/), { target: { value: "sk-ant-SECRET-1234" } });
    fireEvent.click(screen.getByRole("button", { name: "Add credential" }));
  }

  it("clears the key from the DOM immediately, on success", async () => {
    document.cookie = "XSRF-TOKEN=t";
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json(201, { id: "c1", label: "main", keyLast4: "1234" }));
    wrap(<AddCredentialForm projectId="p1" />);
    await fill();
    expect((screen.getByLabelText(/API key/) as HTMLInputElement).value).toBe("");
    await waitFor(() => expect((screen.getByLabelText("Label") as HTMLInputElement).value).toBe(""));
    expect(document.body.innerHTML).not.toContain("SECRET");
  });

  it("clears the key on failure too, and shows the provider error", async () => {
    document.cookie = "XSRF-TOKEN=t";
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json(502, { code: "PROVIDER_REJECTED_KEY", detail: "The provider rejected this key" }));
    wrap(<AddCredentialForm projectId="p1" />);
    await fill();
    expect((screen.getByLabelText(/API key/) as HTMLInputElement).value).toBe("");
    expect(await screen.findByRole("alert")).toHaveTextContent("The provider rejected this key");
    expect(document.body.innerHTML).not.toContain("SECRET");
  });
});

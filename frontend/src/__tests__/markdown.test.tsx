import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Markdown } from "@/components/markdown";

describe("Markdown (untrusted text)", () => {
  it("drops raw HTML and scripts", () => {
    const { container } = render(<Markdown>{'hello <script>alert(1)</script><img src="x" onerror="alert(1)"> <b>b</b>'}</Markdown>);
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("b")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
  });

  it("never loads remote images", () => {
    const { container } = render(<Markdown>{"![x](https://evil.example/a.png?d=secret)"}</Markdown>);
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByTestId("blocked-image")).toBeInTheDocument();
  });

  it("allows only http, https and mailto links, opened safely", () => {
    const { container } = render(<Markdown>{"[ok](https://example.com) [bad](javascript:alert(1)) [mail](mailto:a@b.c)"}</Markdown>);
    const links = [...container.querySelectorAll("a")];
    expect(links.map((a) => a.getAttribute("href"))).toEqual(["https://example.com", "mailto:a@b.c"]);
    for (const a of links) {
      expect(a.getAttribute("target")).toBe("_blank");
      expect(a.getAttribute("rel")).toBe("noopener noreferrer");
    }
    expect(screen.getByText("bad")).toBeInTheDocument();
  });
});

import ReactMarkdown from "react-markdown";

const SAFE_LINK = /^(https?:|mailto:)/i;

/**
 * Untrusted text (agent output, documents, comments): no raw HTML (react-markdown ignores it by default),
 * links limited to http/https/mailto, remote images never loaded.
 */
export function Markdown({ children }: { children: string }) {
  return (
    <div className="prose-sm max-w-none space-y-2 break-words [&_code]:rounded [&_code]:bg-slate-100 [&_code]:px-1 [&_h1]:text-xl [&_h1]:font-bold [&_h2]:text-lg [&_h2]:font-semibold [&_li]:ml-5 [&_ol]:list-decimal [&_pre]:overflow-auto [&_pre]:rounded [&_pre]:bg-slate-100 [&_pre]:p-2 [&_ul]:list-disc">
      <ReactMarkdown
        skipHtml
        urlTransform={(url) => (SAFE_LINK.test(url) ? url : "")}
        components={{
          a: ({ href, children: c }) =>
            href && SAFE_LINK.test(href) ? (
              <a href={href} target="_blank" rel="noopener noreferrer" className="text-indigo-700 underline">
                {c}
              </a>
            ) : (
              <span>{c}</span>
            ),
          img: ({ src, alt }) => (
            <span className="rounded border border-dashed border-slate-300 px-1 text-xs text-slate-500" data-testid="blocked-image">
              [blocked remote image{alt ? `: ${alt}` : ""}
              {typeof src === "string" && SAFE_LINK.test(src) ? ` - ${src}` : ""}]
            </span>
          ),
        }}
      >
        {children}
      </ReactMarkdown>
    </div>
  );
}

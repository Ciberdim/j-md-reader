package org.ciberdim.mdreader.parser;

import org.ciberdim.mdreader.model.HeadingInfo;
import org.ciberdim.mdreader.model.MarkdownDocument;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.AttributeProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles parsing of Markdown files into HTML and extracting
 * document metadata, such as headings for the Table of Contents.
 */
public class MarkdownParser {

    private final Parser parser;
    private final List<org.commonmark.Extension> extensions;

    /**
     * Constructs a new MarkdownParser and configures its extensions.
     */
    public MarkdownParser() {
        this.extensions = List.of(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create()
        );

        this.parser = Parser.builder()
            .extensions(extensions)
            .build();
    }

    /**
     * Parses the given Markdown file into a MarkdownDocument.
     * Reads the file contents, generates heading IDs, parses headings,
     * renders HTML, and wraps it in a styled HTML template.
     * 
     * @param file the Markdown file to parse
     * @return the parsed MarkdownDocument
     * @throws IOException if there is an error reading the file
     */
    public MarkdownDocument parse(File file) throws IOException {
        String rawContent = Files.readString(file.toPath());
        return parseRaw(rawContent, file);
    }

    /**
     * Parses the given raw Markdown string into a MarkdownDocument.
     * 
     * @param rawContent the raw markdown text
     * @param file the associated file (may be null if unsaved)
     * @return the parsed MarkdownDocument
     */
    public MarkdownDocument parseRaw(String rawContent, File file) {
        Node documentNode = parser.parse(rawContent);

        // First pass: extract headings and generate unique IDs
        List<HeadingInfo> headings = new ArrayList<>();
        Map<Node, String> headingIdMap = new IdentityHashMap<>();
        Map<String, Integer> idCounts = new HashMap<>();

        documentNode.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                String headingText = extractText(heading);
                String baseId = generateBaseId(headingText);
                
                // Deduplicate IDs
                String finalId = baseId;
                if (idCounts.containsKey(baseId)) {
                    int count = idCounts.get(baseId) + 1;
                    idCounts.put(baseId, count);
                    finalId = baseId + "-" + count;
                } else {
                    idCounts.put(baseId, 0);
                }

                HeadingInfo info = new HeadingInfo(heading.getLevel(), headingText, finalId);
                headings.add(info);
                headingIdMap.put(heading, finalId);

                super.visit(heading);
            }
        });

        // Second pass: render HTML using the unique IDs
        HtmlRenderer htmlRenderer = HtmlRenderer.builder()
            .extensions(extensions)
            .attributeProviderFactory(context -> new AttributeProvider() {
                @Override
                public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
                    if (node instanceof Heading && headingIdMap.containsKey(node)) {
                        attributes.put("id", headingIdMap.get(node));
                    }
                }
            })
            .build();

        String bodyHtml = htmlRenderer.render(documentNode);
        String fullHtml = wrapInTemplate(bodyHtml);

        return new MarkdownDocument(file, rawContent, fullHtml, headings);
    }

    /**
     * Recursively extracts plain text from all children of a Node.
     */
    private String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                sb.append(text.getLiteral());
            }

            @Override
            public void visit(Code code) {
                sb.append(code.getLiteral());
            }
        });
        return sb.toString().trim();
    }

    /**
     * Generates a clean URL-friendly HTML id attribute based on text content.
     */
    private String generateBaseId(String text) {
        if (text == null || text.isBlank()) {
            return "heading";
        }
        String clean = text.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "") // Remove special characters
            .replaceAll("\\s+", "-")         // Replace spaces with hyphens
            .replaceAll("-+", "-");          // Collapse multiple hyphens
        
        if (clean.startsWith("-")) {
            clean = clean.substring(1);
        }
        if (clean.endsWith("-")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.isBlank() ? "heading" : clean;
    }

    /**
     * Wraps the generated HTML snippet in a full HTML layout template,
     * including light/dark theme CSS and external JS dependencies.
     */
    private String wrapInTemplate(String bodyHtml) {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Markdown Reader</title>
            <style>
                :root {
                    --bg-color: #ffffff;
                    --text-color: #24292e;
                    --border-color: #d0d7de;
                    --link-color: #0969da;
                    --code-bg: #f6f8fa;
                    --quote-bg: #f6f8fa;
                    --quote-border: #d0d7de;
                    --table-zebra: #f6f8fa;
                }
                
                body.dark-theme {
                    --bg-color: #0d1117;
                    --text-color: #c9d1d9;
                    --border-color: #30363d;
                    --link-color: #58a6ff;
                    --code-bg: #161b22;
                    --quote-bg: #161b22;
                    --quote-border: #30363d;
                    --table-zebra: #161b22;
                }

                body {
                    margin: 0;
                    padding: 40px;
                    background-color: var(--bg-color);
                    color: var(--text-color);
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                    font-size: 16px;
                    line-height: 1.6;
                    word-wrap: break-word;
                    transition: background-color 0.2s ease, color 0.2s ease;
                }

                .markdown-body {
                    max-width: 900px;
                    margin: 0 auto;
                }

                h1, h2, h3, h4, h5, h6 {
                    margin-top: 24px;
                    margin-bottom: 16px;
                    font-weight: 600;
                    line-height: 1.25;
                }

                h1 { font-size: 2em; border-bottom: 1px solid var(--border-color); padding-bottom: 0.3em; }
                h2 { font-size: 1.5em; border-bottom: 1px solid var(--border-color); padding-bottom: 0.3em; }
                h3 { font-size: 1.25em; }
                h4 { font-size: 1em; }

                p {
                    margin-top: 0;
                    margin-bottom: 16px;
                }

                a {
                    color: var(--link-color);
                    text-decoration: none;
                }
                a:hover {
                    text-decoration: underline;
                }

                ul, ol {
                    margin-top: 0;
                    margin-bottom: 16px;
                    padding-left: 2em;
                }

                li {
                    margin-top: 0.25em;
                }

                code {
                    padding: 0.2em 0.4em;
                    margin: 0;
                    font-size: 85%%;
                    background-color: var(--code-bg);
                    border-radius: 6px;
                    font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, Liberation Mono, monospace;
                }

                pre {
                    padding: 16px;
                    overflow: auto;
                    font-size: 85%%;
                    line-height: 1.45;
                    background-color: var(--code-bg);
                    border-radius: 6px;
                    margin-top: 0;
                    margin-bottom: 16px;
                }

                pre code {
                    padding: 0;
                    margin: 0;
                    font-size: 100%%;
                    background-color: transparent;
                    border-radius: 0;
                    word-break: normal;
                    white-space: pre;
                }

                blockquote {
                    padding: 0 1em;
                    color: #57606a;
                    border-left: 0.25em solid var(--quote-border);
                    background-color: var(--quote-bg);
                    margin: 0 0 16px 0;
                }
                
                body.dark-theme blockquote {
                    color: #8b949e;
                }

                table {
                    display: block;
                    width: 100%%;
                    width: max-content;
                    max-width: 100%%;
                    overflow: auto;
                    margin-top: 0;
                    margin-bottom: 16px;
                    border-spacing: 0;
                    border-collapse: collapse;
                }

                table th, table td {
                    padding: 6px 13px;
                    border: 1px solid var(--border-color);
                }

                table tr {
                    background-color: var(--bg-color);
                    border-top: 1px solid var(--border-color);
                }

                table tr:nth-child(even) {
                    background-color: var(--table-zebra);
                }

                img {
                    max-width: 100%%;
                    box-sizing: border-box;
                    margin-bottom: 16px;
                }

                hr {
                    height: 0.25em;
                    padding: 0;
                    margin: 24px 0;
                    background-color: var(--border-color);
                    border: 0;
                }

                /* Mermaid Diagram Styling */
                .mermaid {
                    background: white !important;
                    color: black !important;
                    padding: 15px;
                    border-radius: 6px;
                    border: 1px solid #d0d7de;
                    margin: 15px 0;
                    display: inline-block;
                    text-align: center;
                }
            </style>
            <!-- Highlight.js syntax highlighting -->
            <link id="highlight-style" rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css">
            <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
            
            <!-- Mermaid.js for diagrams -->
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
        </head>
        <body class="light-theme">
            <div class="markdown-body">
                %s
            </div>
            <script>
                document.addEventListener("DOMContentLoaded", function() {
                    // Find all <code class="language-mermaid"> or <code class="language-sequence"> blocks
                    const codeBlocks = document.querySelectorAll("pre code");
                    codeBlocks.forEach(block => {
                        const isMermaid = block.classList.contains("language-mermaid");
                        const isSequence = block.classList.contains("language-sequence");
                        if (isMermaid || isSequence) {
                            const pre = block.parentElement;
                            const div = document.createElement("div");
                            div.className = "mermaid";
                            let content = block.textContent.trim();
                            if (isSequence && !content.startsWith("sequenceDiagram")) {
                                content = "sequenceDiagram\\n" + content;
                            }
                            div.textContent = content;
                            pre.replaceWith(div);
                        }
                    });

                    // Initialize Mermaid
                    mermaid.initialize({
                        startOnLoad: true,
                        theme: 'default',
                        securityLevel: 'loose'
                    });

                    // Perform code highlighting
                    hljs.highlightAll();

                    // Speed up mouse wheel scrolling in JavaFX WebView (default is too slow)
                    window.addEventListener('wheel', function(event) {
                        let container = event.target;
                        let hasScrollableParent = false;
                        while (container && container !== document.body && container !== document.documentElement) {
                            const style = window.getComputedStyle(container);
                            const isScrollableY = (style.overflowY === 'auto' || style.overflowY === 'scroll') 
                                && container.scrollHeight > container.clientHeight;
                            const isScrollableX = (style.overflowX === 'auto' || style.overflowX === 'scroll') 
                                && container.scrollWidth > container.clientWidth;
                            
                            if (isScrollableY || isScrollableX) {
                                hasScrollableParent = true;
                                break;
                            }
                            container = container.parentElement;
                        }

                        if (!hasScrollableParent) {
                            event.preventDefault();
                            const multiplier = 2.5; // Custom scroll speed multiplier
                            window.scrollBy({
                                top: event.deltaY * multiplier,
                                left: event.deltaX * multiplier,
                                behavior: 'auto'
                            });
                        }
                    }, { passive: false });
                });
            </script>
        </body>
        </html>
        """.formatted(bodyHtml);
    }
}

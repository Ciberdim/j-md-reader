package org.ciberdim.mdreader.parser;

import org.ciberdim.mdreader.model.HeadingInfo;
import org.ciberdim.mdreader.model.MarkdownDocument;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.heading.anchor.IdGenerator;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.AttributeProviderContext;
import org.commonmark.renderer.html.AttributeProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles parsing of Markdown files into HTML and extracting
 * document metadata, such as headings for the Table of Contents.
 */
public class MarkdownParser {
    private static final Logger logger = LoggerFactory.getLogger(MarkdownParser.class);

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;
    private String htmlTemplate;

    /**
     * Constructs a new MarkdownParser and configures its extensions.
     */
    public MarkdownParser() {
        List<org.commonmark.Extension> extensions = List.of(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            HeadingAnchorExtension.create()
        );

        this.parser = Parser.builder()
            .extensions(extensions)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

        this.htmlRenderer = HtmlRenderer.builder()
            .extensions(extensions)
            .attributeProviderFactory(new AttributeProviderFactory() {
                @Override
                public AttributeProvider create(AttributeProviderContext context) {
                    return new AttributeProvider() {
                        @Override
                        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
                            if (node.getSourceSpans() != null && !node.getSourceSpans().isEmpty()) {
                                // getLineIndex is 0-based, we want 1-based to match MainWindow loop
                                attributes.put("data-source-line", String.valueOf(node.getSourceSpans().get(0).getLineIndex() + 1));
                            }
                        }
                    };
                }
            })
            .build();
            
        loadTemplate();
    }

    private void loadTemplate() {
        try (InputStream is = getClass().getResourceAsStream("/templates/layout.html")) {
            if (is != null) {
                htmlTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                logger.error("Template /templates/layout.html not found. Using fallback.");
                htmlTemplate = "<!DOCTYPE html><html><body>%s</body></html>";
            }
        } catch (IOException e) {
            logger.error("Failed to load HTML template", e);
            htmlTemplate = "<!DOCTYPE html><html><body>%s</body></html>";
        }
    }

    /**
     * Parses the given Markdown file into a MarkdownDocument.
     * 
     * @param file the Markdown file to parse
     * @return the parsed MarkdownDocument
     * @throws IOException if there is an error reading the file
     */
    public MarkdownDocument parse(File file) throws IOException {
        String rawContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
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

        List<HeadingInfo> headings = new ArrayList<>();
        IdGenerator idGenerator = IdGenerator.builder().build();

        documentNode.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                String headingText = extractText(heading);
                String finalId = idGenerator.generateId(headingText);
                
                HeadingInfo info = new HeadingInfo(heading.getLevel(), headingText, finalId);
                headings.add(info);

                super.visit(heading);
            }
        });

        String bodyHtml = htmlRenderer.render(documentNode);
        String fullHtml = htmlTemplate.replace("%s", bodyHtml);

        return new MarkdownDocument(file, rawContent, fullHtml, bodyHtml, headings);
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
}

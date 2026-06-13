package org.ciberdim.mdreader.model;

import java.io.File;
import java.util.List;

/**
 * Encapsulates the state of a loaded Markdown document.
 * Contains references to the source file, the raw markdown string,
 * the generated HTML representation, and the parsed headings (TOC).
 */
public class MarkdownDocument {
    private final File file;
    private final String rawContent;
    private final String htmlContent;
    private final List<HeadingInfo> headings;

    /**
     * Constructs a new MarkdownDocument.
     * 
     * @param file the source file on disk
     * @param rawContent the raw markdown text content
     * @param htmlContent the generated HTML content
     * @param headings the list of headings extracted from the document
     */
    public MarkdownDocument(File file, String rawContent, String htmlContent, List<HeadingInfo> headings) {
        this.file = file;
        this.rawContent = rawContent;
        this.htmlContent = htmlContent;
        this.headings = List.copyOf(headings);
    }

    /**
     * Gets the source file of this document.
     * 
     * @return the source file
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets the raw markdown text content.
     * 
     * @return the raw content string
     */
    public String getRawContent() {
        return rawContent;
    }

    /**
     * Gets the generated HTML representation of the document.
     * 
     * @return the HTML content string
     */
    public String getHtmlContent() {
        return htmlContent;
    }

    /**
     * Gets the list of headings extracted from the document for the Table of Contents.
     * 
     * @return an unmodifiable list of HeadingInfo objects
     */
    public List<HeadingInfo> getHeadings() {
        return headings;
    }
}

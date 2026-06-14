package org.ciberdim.mdreader.model;

/**
 * Represents a heading in a Markdown document.
 * Used for building the Table of Contents navigation.
 * 
 * @param level the heading level (e.g., 1 for H1, 2 for H2)
 * @param text the text content of the heading
 * @param anchorId the HTML anchor ID used for navigating to the heading
 */
public record HeadingInfo(int level, String text, String anchorId) {
}

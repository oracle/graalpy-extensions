package org.graalvm.python.pyinterfacegen

import com.sun.source.doctree.*
import com.sun.source.util.DocTrees
import javax.lang.model.element.Element

private val preRegex = Regex("(?is)<pre[^>]*>(.*?)</pre>")
private val codeWrapped = Regex("(?s)^\\s*\\{@code\\s*(.*?)\\s*}\\s*$")
private val pEndTag = Regex("(?i)</?p\\s*>")
private val brTag = Regex("(?i)<br\\s*/?>")
private val javadocTag = Regex("\\{@[^}]*}")
private val openTag = Regex("<[^>]+>")
private val spaceRun = Regex(" {2,}")
private val leadingHorizontalWhitespace = Regex("(?m)^[ \\t\\u00A0]+")
private val excessiveBlankLines = Regex("\\n{3,}")
private val codeBlockStarterPrecededByNewline = Regex("(?m)([^\\n])\\n(>>> )")
private val whitespaceRun = Regex("\\s+")
private val codeJavaDocTag = Regex("\\{@code\\s+([^}]*)}")
private val literalJavaDocTag = Regex("\\{@literal\\s+([^}]*)}")
private val linkJavaDocTag = Regex("\\{@link\\s+([^}]*)}")

// Convert full Javadoc (summary, body and common block tags) to a multi-line Python docstring.
fun DocTrees.javadocFull(el: Element): String? {
    val dc = getDocCommentTree(el) ?: return null

    fun renderInline(parts: List<DocTree>?): String {
        if (parts == null || parts.isEmpty()) return ""
        var text = parts.joinToString("") { p ->
            when (p.kind) {
                DocTree.Kind.TEXT -> (p as TextTree).body
                else -> p.toString()
            }
        }
        text = normalizeCommonInlineTags(text)

        // Extract and protect <pre>...</pre> blocks so we don't collapse whitespace inside them.
        data class PreBlock(val marker: String, val content: String)

        val preBlocks = mutableListOf<PreBlock>()
        fun htmlUnescape(s: String): String =
            s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")

        fun dedentBlock(s: String): String {
            val lines = s.replace("\r\n", "\n").split('\n')
            // Trim leading/trailing blank lines
            val first = lines.indexOfFirst { it.trim().isNotEmpty() }
            val last = lines.indexOfLast { it.trim().isNotEmpty() }
            if (first == -1 || last == -1) return ""
            val chunk = lines.subList(first, last + 1)
            val minIndent = chunk.filterNot(String::isBlank).minOfOrNull { line ->
                line.takeWhile { it == ' ' || it == '\t' }.length
            } ?: 0
            return chunk.joinToString("\n") { it.drop(minIndent) }
        }
        text = text.replace(preRegex) { m ->
            val inner = m.groupValues[1]
            var cleaned = dedentBlock(htmlUnescape(inner))
            // If the block is wrapped in {@code ...}, strip the wrapper and keep only its content.
            val mcode = codeWrapped.find(cleaned)
            if (mcode != null) {
                cleaned = mcode.groupValues[1]
                // Re-dedent in case inner content had additional indentation.
                cleaned = dedentBlock(cleaned)
            }
            // Use a safe placeholder that won't be removed by HTML tag stripping.
            val marker = "§§J2PYI_PRE_${preBlocks.size}§§"
            preBlocks += PreBlock(marker, cleaned)
            marker
        }

        // Preserve some HTML structure outside <pre>
        text = text.replace(pEndTag, "\n\n")
        text = text.replace(brTag, "\n")
        // Remove other inline tags
        text = text.replace(javadocTag, "")
        // Strip remaining HTML elements (but not our markers)
        text = text.replace(openTag, "")
        // Normalize whitespace but keep newlines; do NOT strip indentation across newlines
        text = text.replace("\r\n", "\n")
        // Collapse runs of spaces in non-code text, but avoid touching indentation: do not cross newlines
        text = text.replace(spaceRun, " ")
        // Trim both leading and trailing spaces on each non-<pre> line (placeholders still in place)
        text = text.lines().joinToString("\n") { it.trim() }.trim()
        // Aggressively strip any leading horizontal whitespace (incl. NBSP) at line starts, non-code only
        text = text.replace(leadingHorizontalWhitespace, "")
        // Collapse excessive blank lines to at most one
        text = text.replace(excessiveBlankLines, "\n\n")

        // Restore protected <pre> blocks, formatting code blocks:
        // - Trim leading/trailing blank lines
        // - Prefix first line with '>>> '
        // - Indent subsequent lines by 4 spaces (relative to start of the block)
        for (pb in preBlocks) {
            val lines = pb.content.replace("\r\n", "\n").lines()
            // Trim leading/trailing blank lines inside the code block
            val trimmed = lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            val formatted = if (trimmed.isEmpty()) {
                ""
            } else {
                buildString {
                    var firstDone = false
                    for (lnRaw in trimmed) {
                        val ln = lnRaw.replace(Regex("\\s+$"), "")
                        // Skip entirely blank interior lines to match expected formatting
                        if (ln.isBlank()) continue
                        if (!firstDone) {
                            append(">>> ").append(ln)
                            firstDone = true
                        } else {
                            append('\n').append("    ").append(ln)
                        }
                    }
                }
            }
            text = text.replace(pb.marker, formatted)
        }
        // Ensure a blank line separates prose from the start of a code block.
        // When a '>>> ' code-start line is preceded by only a single newline, make it two.
        text = text.replace(codeBlockStarterPrecededByNewline, "$1\n\n$2")

        return text
    }

    val out = mutableListOf<String>()
    val summary = renderInline(dc.firstSentence)
    if (summary.isNotBlank()) out += summary
    val body = renderInline(dc.body)
    if (body.isNotBlank()) {
        if (out.isNotEmpty()) out += ""
        out += body
    }

    // Block tags -> Google-style sections
    val params = mutableListOf<Pair<String, String>>()
    var returns: String? = null
    val raises = mutableListOf<Pair<String, String>>()
    for (bt in dc.blockTags) {
        when (bt.kind) {
            DocTree.Kind.PARAM -> {
                val pt = bt as ParamTree
                if (!pt.isTypeParameter) {
                    val name = pt.name.toString()
                    val desc = renderInline(pt.description)
                    params += name to desc
                }
            }

            DocTree.Kind.RETURN -> {
                val rt = bt as ReturnTree
                val desc = renderInline(rt.description)
                if (desc.isNotBlank()) returns = desc
            }

            DocTree.Kind.THROWS, DocTree.Kind.EXCEPTION -> {
                val tt = bt as ThrowsTree
                val name = tt.exceptionName?.toString() ?: "Exception"
                val desc = renderInline(tt.description)
                raises += name to desc
            }

            else -> {} // ignore others for now
        }
    }
    if (params.isNotEmpty()) {
        if (out.isNotEmpty()) out += ""
        out += "Args:"
        for ((n, d) in params) {
            out += "  $n: ${d.ifBlank { "" }}".trimEnd()
        }
    }
    if (!returns.isNullOrBlank()) {
        if (out.isNotEmpty()) out += ""
        out += "Returns:"
        out += "  $returns"
    }
    if (raises.isNotEmpty()) {
        if (out.isNotEmpty()) out += ""
        out += "Raises:"
        for ((n, d) in raises) {
            out += "  $n: ${d.ifBlank { "" }}".trimEnd()
        }
    }
    val result = out.joinToString("\n").trim()
    return result.ifBlank { null }
}

// Extract first-sentence Javadoc summary as plain text (conservative).
fun DocTrees.javadocSummary(el: Element): String? {
    val dc = getDocCommentTree(el) ?: return null
    val parts = dc.firstSentence
    if (parts == null || parts.isEmpty()) return null
    var text = parts.joinToString("") { p ->
        if (p.kind == DocTree.Kind.TEXT) {
            (p as TextTree).body
        } else {
            p.toString()
        }
    }
    text = normalizeCommonInlineTags(text)
    // Remove any other inline tags and HTML
    text = text.replace(javadocTag, "")
    text = text.replace(openTag, "")
    // Collapse whitespace
    text = text.replace(whitespaceRun, " ").trim()
    return text.ifBlank { null }
}

private fun normalizeCommonInlineTags(text: String): String {
    return text.replace(codeJavaDocTag, "$1")
        .replace(literalJavaDocTag, "$1")
        .replace(linkJavaDocTag) { m ->
            val body = m.groupValues[1].trim()
            val parts = body.split(whitespaceRun, limit = 2)
            if (parts.size == 2) parts[1] else parts[0]
        }
}
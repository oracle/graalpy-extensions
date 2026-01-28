package org.graalvm.python.pyinterfacegen.build

import org.gradle.api.Project
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

data class LicenseInfo(val name: String, val url: String)
data class DeveloperInfo(
    val name: String,
    val email: String,
    val organization: String?,
    val organizationUrl: String?
)
data class ScmInfo(
    val connection: String,
    val developerConnection: String,
    val url: String,
    val tag: String?
)
data class RootPomMetadata(
    val version: String,
    val url: String,
    val licenses: List<LicenseInfo>,
    val developers: List<DeveloperInfo>,
    val scm: ScmInfo
)

private fun findPomUpwards(start: File): File? {
    var cur: File? = start
    while (cur != null) {
        val pom = File(cur, "pom.xml")
        if (pom.exists()) return pom
        cur = cur.parentFile
    }
    return null
}

private val xp by lazy { XPathFactory.newInstance().newXPath() }

private fun textOf(elem: Element?, expr: String): String? {
    if (elem == null) return null
    val node = xp.evaluate(expr, elem, XPathConstants.NODE) as? org.w3c.dom.Node ?: return null
    return node.textContent?.trim()?.ifBlank { null }
}

private fun properties(elem: Element): Map<String, String> {
    val propsElem = xp.evaluate("/*[local-name()='project']/*[local-name()='properties']",
        elem, XPathConstants.NODE) as? Element ?: return emptyMap()
    val map = linkedMapOf<String, String>()
    val children = propsElem.childNodes
    for (i in 0 until children.length) {
        val n = children.item(i)
        if (n is Element) {
            val key = n.localName ?: n.tagName
            val value = n.textContent?.trim().orEmpty()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                map[key] = value
            }
        }
    }
    return map
}

private val PLACEHOLDER = Regex("\\$\\{([^}]+)}")
private fun resolvePlaceholders(input: String, props: Map<String, String>): String {
    // Resolve recursively but with a reasonable cap to avoid loops.
    var prev = input
    repeat(8) {
        val next = PLACEHOLDER.replace(prev) { m ->
            val key = m.groupValues[1]
            props[key] ?: m.value
        }
        if (next == prev) return next
        prev = next
    }
    return prev
}

fun readRootPomMetadata(project: Project): RootPomMetadata {
    val pomFile = findPomUpwards(project.rootDir)
        ?: error("Cannot locate root pom.xml starting at ${project.rootDir}")
    val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val doc = dbf.newDocumentBuilder().parse(pomFile)
    val root = doc.documentElement

    val props = properties(root)
    val revision = props["revision"]
        ?: error("Root pom.xml is missing <properties><revision>...</revision></properties>")

    // Prefer explicit <url> first, then fall back to project.url.root property.
    val urlRaw = xp.evaluate("/*[local-name()='project']/*[local-name()='url']/text()",
        root, XPathConstants.STRING) as String?
    val url = (urlRaw?.trim()?.takeIf { it.isNotEmpty() } ?: props["project.url.root"])
        ?.let { resolvePlaceholders(it, props) }
        ?: error("Root pom.xml is missing <url> or properties.project.url.root")

    // Licenses
    val licenseNodes = xp.evaluate(
        "/*[local-name()='project']/*[local-name()='licenses']/*[local-name()='license']",
        root, XPathConstants.NODESET
    ) as org.w3c.dom.NodeList
    val licenses = buildList {
        for (i in 0 until licenseNodes.length) {
            val e = licenseNodes.item(i) as Element
            val name = textOf(e, "./*[local-name()='name']") ?: error("License name missing")
            val lurl = textOf(e, "./*[local-name()='url']") ?: error("License url missing")
            add(LicenseInfo(name, lurl))
        }
    }
    require(licenses.isNotEmpty()) { "No <licenses> found in root pom.xml" }

    // Developers
    val devNodes = xp.evaluate(
        "/*[local-name()='project']/*[local-name()='developers']/*[local-name()='developer']",
        root, XPathConstants.NODESET
    ) as org.w3c.dom.NodeList
    val developers = buildList {
        for (i in 0 until devNodes.length) {
            val e = devNodes.item(i) as Element
            val name = textOf(e, "./*[local-name()='name']") ?: error("Developer name missing")
            val email = textOf(e, "./*[local-name()='email']") ?: error("Developer email missing")
            val org = textOf(e, "./*[local-name()='organization']")
            val orgUrl = textOf(e, "./*[local-name()='organizationUrl']")
            add(DeveloperInfo(name, email, org, orgUrl))
        }
    }
    require(developers.isNotEmpty()) { "No <developers> found in root pom.xml" }

    // SCM
    val scmElem = xp.evaluate(
        "/*[local-name()='project']/*[local-name()='scm']",
        root, XPathConstants.NODE
    ) as? Element ?: error("No <scm> section in root pom.xml")
    val scm = ScmInfo(
        connection = resolvePlaceholders(
            textOf(scmElem, "./*[local-name()='connection']")
                ?: error("SCM connection missing"), props
        ),
        developerConnection = resolvePlaceholders(
            textOf(scmElem, "./*[local-name()='developerConnection']")
                ?: error("SCM developerConnection missing"), props
        ),
        url = resolvePlaceholders(
            textOf(scmElem, "./*[local-name()='url']")
                ?: error("SCM url missing"), props
        ),
        tag = textOf(scmElem, "./*[local-name()='tag']")?.let { resolvePlaceholders(it, props) }
    )

    return RootPomMetadata(
        version = revision,
        url = url,
        licenses = licenses,
        developers = developers,
        scm = scm
    )
}

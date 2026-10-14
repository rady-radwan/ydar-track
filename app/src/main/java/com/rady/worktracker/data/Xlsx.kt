package com.rady.worktracker.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/*
 * A small, dependency-free reader and writer for Excel .xlsx files.
 * (.xlsx is a zip of XML files.) It supports what this app needs:
 * text, numbers, dates/times, simple formulas, bold headers, column widths,
 * a frozen header row and filters.
 */

// ---------------------------------------------------------------- writing

sealed class XCell {
    data class Text(val value: String, val bold: Boolean = false) : XCell()
    /** [style]: one of the XStyle constants. */
    data class Num(val value: Double, val style: Int = XStyle.GENERAL) : XCell()
    /** A formula (without "=") plus the value Excel should show before recalculating. */
    data class Formula(val formula: String, val cached: Double, val style: Int = XStyle.GENERAL) : XCell()
    data object Empty : XCell()
}

object XStyle {
    const val GENERAL = 0
    const val HEADER = 1
    const val DATE = 2
    const val TIME = 3
    const val DECIMAL2 = 4
    const val INTEGER = 5
    const val BOLD = 6
    const val BOLD_DECIMAL2 = 7
}

class XSheet(
    val name: String,
    val rows: List<List<XCell>>,
    /** Column widths in Excel "characters". */
    val widths: List<Double> = emptyList(),
    val freezeHeader: Boolean = true,
    val autoFilter: Boolean = false,
)

object XlsxWriter {

    fun write(sheets: List<XSheet>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return bos.toByteArray()
    }

    fun colName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> if (ch < ' ' && ch != '\n' && ch != '\t' && ch != '\r') Unit else sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun num(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite() && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()

    private fun sheetXml(s: XSheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (s.freezeHeader && s.rows.isNotEmpty()) {
            sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft" activeCell="A2" sqref="A2"/></sheetView></sheetViews>""")
        }
        sb.append("""<sheetFormatPr defaultRowHeight="15"/>""")
        if (s.widths.isNotEmpty()) {
            sb.append("<cols>")
            s.widths.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        s.rows.forEachIndexed { ri, row ->
            val r = ri + 1
            sb.append("""<row r="$r">""")
            row.forEachIndexed { ci, cell ->
                val ref = colName(ci) + r
                when (cell) {
                    is XCell.Text -> {
                        val style = if (cell.bold) XStyle.BOLD else if (ri == 0 && s.freezeHeader) XStyle.HEADER else 0
                        val st = if (style != 0) """ s="$style"""" else ""
                        sb.append("""<c r="$ref" t="inlineStr"$st><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>""")
                    }
                    is XCell.Num -> sb.append("""<c r="$ref" s="${cell.style}"><v>${num(cell.value)}</v></c>""")
                    is XCell.Formula -> sb.append("""<c r="$ref" s="${cell.style}"><f>${esc(cell.formula)}</f><v>${num(cell.cached)}</v></c>""")
                    XCell.Empty -> Unit
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        if (s.autoFilter && s.rows.isNotEmpty()) {
            val cols = s.rows.maxOf { it.size }.coerceAtLeast(1)
            sb.append("""<autoFilter ref="A1:${colName(cols - 1)}${s.rows.size}"/>""")
        }
        sb.append("""<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun contentTypes(n: Int): String {
        val sheets = (1..n).joinToString("") {
            """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>$sheets</Types>"""
    }

    private fun workbook(sheets: List<XSheet>): String {
        val list = sheets.mapIndexed { i, s ->
            """<sheet name="${esc(s.name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("")
        val filters = sheets.mapIndexedNotNull { i, s ->
            if (s.autoFilter && s.rows.isNotEmpty()) {
                val cols = s.rows.maxOf { it.size }.coerceAtLeast(1)
                """<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">'${esc(s.name.take(31))}'!${'$'}A${'$'}1:${'$'}${colName(cols - 1)}${'$'}${s.rows.size}</definedName>"""
            } else null
        }.joinToString("")
        val defined = if (filters.isNotEmpty()) "<definedNames>$filters</definedNames>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView/></bookViews><sheets>$list</sheets>$defined<calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>"""
    }

    private fun workbookRels(n: Int): String {
        val sheets = (1..n).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$sheets<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    // Order of <xf> entries must match the XStyle constants.
    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy\-mm\-dd"/><numFmt numFmtId="165" formatCode="hh:mm"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font><font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFDE3D3"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left/><right/><top/><bottom style="thin"><color rgb="FFE8590C"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="2" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}

// ---------------------------------------------------------------- reading

/** A cell read from a file: its text, and its number if it holds one. */
data class RCell(val text: String, val number: Double? = null) {
    val isBlank: Boolean get() = text.isBlank() && number == null
}

class RSheet(val name: String, val rows: List<List<RCell>>)

object XlsxReader {

    fun read(input: InputStream): List<RSheet> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) {
                    val name = e.name.trimStart('/')
                    // Only the parts we need; skip images and other large parts.
                    if (name.endsWith(".xml") || name.endsWith(".rels")) files[name] = zip.readBytes()
                }
            }
        }
        val wb = files["xl/workbook.xml"] ?: throw IllegalArgumentException("This file isn't an Excel workbook (.xlsx).")
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { parse(it) }
        val relTargets = HashMap<String, String>()
        rels?.let { doc ->
            elements(doc.documentElement, "Relationship").forEach { r ->
                relTargets[r.getAttribute("Id")] = r.getAttribute("Target")
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { readShared(it) } ?: emptyList()

        val out = mutableListOf<RSheet>()
        val wbDoc = parse(wb)
        elements(wbDoc.documentElement, "sheet").forEachIndexed { index, sh ->
            val name = sh.getAttribute("name")
            val rid = attrLocal(sh, "id")
            var target = relTargets[rid] ?: "worksheets/sheet${index + 1}.xml"
            target = when {
                target.startsWith("/") -> target.trimStart('/')
                target.startsWith("xl/") -> target
                else -> "xl/$target"
            }
            val bytes = files[target] ?: return@forEachIndexed
            out += RSheet(name, readSheet(bytes, shared))
        }
        return out
    }

    private fun parse(bytes: ByteArray): org.w3c.dom.Document {
        val f = DocumentBuilderFactory.newInstance()
        runCatching { f.isNamespaceAware = true }
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun localName(e: Element): String = e.localName ?: e.tagName.substringAfter(':')

    /** All descendants named [local] (ignoring any namespace prefix), in document order. */
    private fun elements(root: Element, local: String): List<Element> {
        val list = ArrayList<Element>()
        fun walk(e: Element) {
            var n = e.firstChild
            while (n != null) {
                if (n is Element) {
                    if (localName(n) == local) list += n
                    walk(n)
                }
                n = n.nextSibling
            }
        }
        if (localName(root) == local) list += root
        walk(root)
        return list
    }

    private fun children(e: Element, local: String): List<Element> {
        val list = ArrayList<Element>()
        var n = e.firstChild
        while (n != null) {
            if (n is Element && localName(n) == local) list += n
            n = n.nextSibling
        }
        return list
    }

    private fun attrLocal(e: Element, local: String): String {
        val attrs = e.attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            val ln = a.localName ?: a.nodeName.substringAfter(':')
            if (ln == local && a.nodeName != local) return a.nodeValue
        }
        return e.getAttribute(local)
    }

    /** Text of <t> elements inside an <si> or <is>, skipping phonetic runs. */
    private fun richText(e: Element): String {
        val sb = StringBuilder()
        fun walk(n: Element) {
            when (localName(n)) {
                "rPh", "phoneticPr" -> return
                "t" -> { sb.append(n.textContent); return }
            }
            var c = n.firstChild
            while (c != null) {
                if (c is Element) walk(c)
                c = c.nextSibling
            }
        }
        walk(e)
        return sb.toString()
    }

    private fun readShared(bytes: ByteArray): List<String> =
        children(parse(bytes).documentElement, "si").map { richText(it) }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1)
            else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1)
            else break
        }
        return n - 1
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>): List<List<RCell>> {
        val doc = parse(bytes)
        val rows = mutableListOf<List<RCell>>()
        var expectedRow = 1
        for (rowEl in elements(doc.documentElement, "row")) {
            val r = rowEl.getAttribute("r").toIntOrNull() ?: expectedRow
            while (rows.size < r - 1) rows.add(emptyList())   // keep row numbers aligned
            expectedRow = r + 1
            val cells = ArrayList<RCell>()
            var nextCol = 0
            for (c in children(rowEl, "c")) {
                val ref = c.getAttribute("r")
                val col = if (ref.isNotEmpty()) colIndex(ref) else nextCol
                nextCol = col + 1
                val type = c.getAttribute("t")
                val v = children(c, "v").firstOrNull()?.textContent
                val cell = when (type) {
                    "s" -> RCell(v?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) } ?: "")
                    "inlineStr" -> RCell(children(c, "is").firstOrNull()?.let { richText(it) } ?: "")
                    "str", "e" -> RCell(v ?: "")
                    "b" -> RCell(if (v == "1") "TRUE" else "FALSE")
                    "d" -> RCell(v ?: "")
                    else -> {
                        val d = v?.trim()?.toDoubleOrNull()
                        RCell(v?.trim() ?: "", d)
                    }
                }
                while (cells.size < col) cells += RCell("")
                if (col >= 0) cells += cell
            }
            rows.add(cells)
        }
        return rows
    }
}

/** Minimal CSV reader (handles quotes, commas, semicolons and line breaks in quotes). */
object CsvReader {
    fun read(text: String): List<List<RCell>> {
        val t = text.removePrefix("﻿")
        val firstLine = t.lineSequence().firstOrNull() ?: ""
        val sep = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<RCell>>()
        var row = mutableListOf<RCell>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endField() {
            val s = field.toString()
            row += RCell(s, s.trim().toDoubleOrNull())
            field.setLength(0)
        }
        while (i < t.length) {
            val ch = t[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(ch)
            } else {
                when (ch) {
                    '"' -> quoted = true
                    sep -> endField()
                    '\r' -> {}
                    '\n' -> { endField(); rows.add(row); row = mutableListOf() }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { endField(); rows.add(row) }
        return rows
    }
}

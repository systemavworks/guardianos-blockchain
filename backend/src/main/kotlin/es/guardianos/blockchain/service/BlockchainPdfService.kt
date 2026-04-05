package es.guardianos.blockchain.service

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import es.guardianos.blockchain.model.BlockchainFinding
import es.guardianos.blockchain.model.BlockchainReport
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File

private val log = LoggerFactory.getLogger("BlockchainPdfService")

object BlockchainPdfService {

    // ── Paleta de colores ─────────────────────────────────────────────────────
    private val C_DARK        = DeviceRgb(14, 14, 25)
    private val C_ACCENT      = DeviceRgb(0, 180, 216)
    private val C_LIGHT       = DeviceRgb(248, 249, 250)
    private val C_BORDER      = DeviceRgb(220, 220, 230)
    private val C_TEXT        = DeviceRgb(30, 30, 50)
    private val C_MUTED       = DeviceRgb(100, 100, 120)
    private val C_EVID_BG     = DeviceRgb(245, 245, 252)
    private val C_REC_BG      = DeviceRgb(240, 249, 255)
    private val C_FOOTER_LINE = DeviceRgb(200, 200, 220)
    private val C_FOOTER_TEXT = DeviceRgb(150, 150, 170)
    private val C_PAGE_NUM    = DeviceRgb(100, 100, 120)

    private val C_CRITICAL    = DeviceRgb(220,  38,  38)
    private val C_HIGH        = DeviceRgb(234,  88,  12)
    private val C_MEDIUM      = DeviceRgb(202, 138,   4)
    private val C_LOW         = DeviceRgb( 37,  99, 235)
    private val C_INFO        = DeviceRgb(  5, 150, 105)

    private val C_CRITICAL_BG = DeviceRgb(254, 226, 226)
    private val C_HIGH_BG     = DeviceRgb(255, 237, 213)
    private val C_MEDIUM_BG   = DeviceRgb(254, 249, 195)
    private val C_LOW_BG      = DeviceRgb(219, 234, 254)
    private val C_INFO_BG     = DeviceRgb(209, 250, 229)

    private val RISK_ORDER = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO")

    private fun riskFg(risk: String) = when (risk) {
        "CRITICAL" -> C_CRITICAL; "HIGH" -> C_HIGH; "MEDIUM" -> C_MEDIUM
        "LOW"      -> C_LOW;      else   -> C_INFO
    }

    private fun riskBg(risk: String) = when (risk) {
        "CRITICAL" -> C_CRITICAL_BG; "HIGH" -> C_HIGH_BG; "MEDIUM" -> C_MEDIUM_BG
        "LOW"      -> C_LOW_BG;      else   -> C_INFO_BG
    }

    // ── Cadenas i18n ──────────────────────────────────────────────────────────
    private data class L(
        val title: String,        val subtitle: String,
        val chain: String,        val address: String,   val token: String,
        val supply: String,       val holders: String,   val analyzedOn: String,
        val secScore: String,     val riskLvl: String,
        val execSummary: String,  val contractInfo: String,
        val findingsSummary: String,
        val severity: String,     val count: String,
        val findingsDetail: String, val finding: String,
        val category: String,     val description: String,
        val evidence: String,     val recommendation: String,
        val noFindings: String,   val noFindingsBody: String,
        val riskMap: Map<String, String>,
        val disclaimer: String,   val generatedBy: String,
        val confidential: String
    )

    private val ES = L(
        title           = "INFORME DE AUDITORÍA DE SEGURIDAD BLOCKCHAIN",
        subtitle        = "Análisis Automatizado de Contrato Inteligente · GuardianOS Blockchain",
        chain           = "Red",
        address         = "Dirección del contrato",
        token           = "Token",
        supply          = "Supply total",
        holders         = "Holders",
        analyzedOn      = "Analizado el",
        secScore        = "Puntuación de Seguridad",
        riskLvl         = "Nivel de Riesgo",
        execSummary     = "Resumen Ejecutivo",
        contractInfo    = "Información del Contrato",
        findingsSummary = "Resumen de Hallazgos por Severidad",
        severity        = "Severidad",
        count           = "Nº hallazgos",
        findingsDetail  = "Hallazgos Detallados",
        finding         = "Hallazgo",
        category        = "Categoría",
        description     = "Descripción",
        evidence        = "Evidencia técnica",
        recommendation  = "Recomendación",
        noFindings      = "Sin hallazgos de seguridad",
        noFindingsBody  = "Este contrato ha superado todos los controles de seguridad analizados por GuardianOS Blockchain.",
        riskMap         = mapOf(
            "CRITICAL" to "CRÍTICO", "HIGH" to "ALTO",
            "MEDIUM" to "MEDIO",     "LOW" to "BAJO", "INFO" to "INFO"
        ),
        disclaimer      = "Este informe fue generado automáticamente por GuardianOS Blockchain Security. " +
                "Los resultados reflejan el estado del contrato en el momento del análisis y no constituyen " +
                "una auditoría de seguridad completa. Se recomienda complementar con una revisión manual del código fuente.",
        generatedBy     = "Generado por GuardianOS Blockchain Security  ·  guardianos.es",
        confidential    = "CONFIDENCIAL"
    )

    private val EN = L(
        title           = "BLOCKCHAIN SECURITY AUDIT REPORT",
        subtitle        = "Automated Smart Contract Analysis · GuardianOS Blockchain",
        chain           = "Network",
        address         = "Contract address",
        token           = "Token",
        supply          = "Total supply",
        holders         = "Holders",
        analyzedOn      = "Analyzed on",
        secScore        = "Security Score",
        riskLvl         = "Risk Level",
        execSummary     = "Executive Summary",
        contractInfo    = "Contract Information",
        findingsSummary = "Findings Summary by Severity",
        severity        = "Severity",
        count           = "Findings",
        findingsDetail  = "Detailed Findings",
        finding         = "Finding",
        category        = "Category",
        description     = "Description",
        evidence        = "Technical evidence",
        recommendation  = "Recommendation",
        noFindings      = "No security findings",
        noFindingsBody  = "This contract passed all security checks analyzed by GuardianOS Blockchain.",
        riskMap         = mapOf(
            "CRITICAL" to "CRITICAL", "HIGH" to "HIGH",
            "MEDIUM" to "MEDIUM",     "LOW" to "LOW", "INFO" to "INFO"
        ),
        disclaimer      = "This report was automatically generated by GuardianOS Blockchain Security. " +
                "Results reflect the state of the contract at the time of analysis and do not constitute " +
                "a complete security audit. A manual source code review is recommended.",
        generatedBy     = "Generated by GuardianOS Blockchain Security  ·  guardianos.es",
        confidential    = "CONFIDENTIAL"
    )

    // ── Directorio de caché ───────────────────────────────────────────────────
    private val CACHE_DIR = File("./reports/blockchain").also { it.mkdirs() }

    // ── Punto de entrada ──────────────────────────────────────────────────────
    fun generate(report: BlockchainReport, lang: String = "es"): ByteArray {
        // Servir desde caché si el reporte está completado y el PDF ya existe
        val cacheFile = File(CACHE_DIR, "${report.id}-$lang.pdf")
        if (report.status == "completed" && cacheFile.exists() && cacheFile.length() > 1024) {
            return cacheFile.readBytes()
        }

        val l   = if (lang == "en") EN else ES
        val baos = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(baos))
        val doc    = Document(pdfDoc, PageSize.A4).apply {
            setMargins(50f, 44f, 60f, 44f)
        }

        val fR = PdfFontFactory.createFont(StandardFonts.HELVETICA)
        val fB = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
        val fM = PdfFontFactory.createFont(StandardFonts.COURIER)

        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, FooterHandler(fR, fB, l))

        // ── Páginas ────────────────────────────────────────────────────────────
        coverPage(doc, report, l, fR, fB, fM)

        doc.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        summaryPage(doc, report, l, fR, fB)

        if (report.findings.isNotEmpty()) {
            doc.add(AreaBreak(AreaBreakType.NEXT_PAGE))
            findingsPages(doc, report, l, fR, fB, fM)
        }

        doc.close()

        val bytes = baos.toByteArray()

        // Guardar en caché
        if (report.status == "completed") {
            runCatching { cacheFile.writeBytes(bytes) }
                .onFailure { log.warn("No se pudo guardar PDF en caché: ${it.message}") }
        }
        log.info("[PDF] Generado para report=${report.id} lang=$lang size=${bytes.size}B")
        return bytes
    }

    // ── Portada ───────────────────────────────────────────────────────────────
    private fun coverPage(doc: Document, report: BlockchainReport, l: L, fR: PdfFont, fB: PdfFont, fM: PdfFont) {

        // Bloque de cabecera oscuro
        val hdrTable = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        hdrTable.addCell(
            Cell().setBackgroundColor(C_DARK).setPadding(32f).setBorder(Border.NO_BORDER)
                .add(Paragraph(l.title).setFont(fB).setFontSize(14f).setFontColor(ColorConstants.WHITE).setMarginBottom(10f))
                .add(Paragraph(l.subtitle).setFont(fR).setFontSize(10f).setFontColor(C_ACCENT).setMarginBottom(0f))
        )
        doc.add(hdrTable)

        // Línea de acento cyan
        val accentBar = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        accentBar.addCell(
            Cell().setBackgroundColor(C_ACCENT).setHeight(3f).setBorder(Border.NO_BORDER)
                .add(Paragraph(""))
        )
        doc.add(accentBar)

        doc.add(Paragraph("").setMarginBottom(18f))

        // ── Información del contrato ───────────────────────────────────────────
        doc.add(sectionLabel(l.contractInfo, fB))

        val infoT = Table(UnitValue.createPercentArray(floatArrayOf(36f, 64f))).useAllAvailableWidth().setMarginBottom(22f)

        infoRow(infoT, l.address, report.address, fR, fB, mono = true, fM = fM)
        infoRow(infoT, l.chain, report.chainId.replaceFirstChar { it.uppercase() }, fR, fB)

        if (!report.tokenName.isNullOrBlank()) {
            val tok = buildString {
                append(report.tokenName)
                if (!report.tokenSymbol.isNullOrBlank()) append(" (${report.tokenSymbol})")
            }
            infoRow(infoT, l.token, tok, fR, fB)
        }
        if (!report.totalSupply.isNullOrBlank()) {
            infoRow(infoT, l.supply, fmtNum(report.totalSupply!!), fR, fB)
        }
        if (!report.holderCount.isNullOrBlank()) {
            infoRow(infoT, l.holders, report.holderCount!!, fR, fB)
        }
        val dateStr = (report.completedAt ?: report.createdAt).substringBefore("T")
        infoRow(infoT, l.analyzedOn, dateStr, fR, fB)
        doc.add(infoT)

        // ── Tarjetas de puntuación y riesgo ────────────────────────────────────
        val cardT = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth().setMarginBottom(24f)

        // Score
        cardT.addCell(
            Cell().setBorder(Border.NO_BORDER).setBackgroundColor(C_LIGHT).setPadding(18f)
                .add(Paragraph(l.secScore).setFont(fB).setFontSize(8f).setFontColor(C_MUTED).setMarginBottom(6f))
                .add(Paragraph("${report.overallScore}").setFont(fB).setFontSize(34f).setFontColor(riskFg(report.riskLevel)).setMarginBottom(0f))
                .add(Paragraph("/ 100").setFont(fR).setFontSize(9f).setFontColor(C_MUTED))
        )
        // Nivel de riesgo
        cardT.addCell(
            Cell().setBorder(Border.NO_BORDER).setBackgroundColor(riskBg(report.riskLevel)).setPadding(18f)
                .add(Paragraph(l.riskLvl).setFont(fB).setFontSize(8f).setFontColor(C_MUTED).setMarginBottom(6f))
                .add(
                    Paragraph(l.riskMap[report.riskLevel] ?: report.riskLevel)
                        .setFont(fB).setFontSize(24f).setFontColor(riskFg(report.riskLevel))
                )
        )
        doc.add(cardT)

        // Marca CONFIDENCIAL
        doc.add(
            Paragraph(l.confidential)
                .setFont(fB).setFontSize(8f).setFontColor(C_MUTED)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(10f)
        )
    }

    // ── Resumen ejecutivo ─────────────────────────────────────────────────────
    private fun summaryPage(doc: Document, report: BlockchainReport, l: L, fR: PdfFont, fB: PdfFont) {
        doc.add(sectionLabel(l.execSummary, fB))

        if (report.findings.isEmpty()) {
            doc.add(Paragraph(l.noFindings).setFont(fB).setFontSize(13f).setFontColor(C_INFO).setMarginBottom(8f))
            doc.add(Paragraph(l.noFindingsBody).setFont(fR).setFontSize(10f).setFontColor(C_TEXT))
        } else {
            doc.add(Paragraph(l.findingsSummary).setFont(fB).setFontSize(10f).setFontColor(C_TEXT).setMarginBottom(10f))

            val sumT = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth().setMarginBottom(28f)

            // Cabecera de tabla
            sumT.addCell(
                Cell().setBackgroundColor(C_DARK).setBorder(Border.NO_BORDER).setPadding(9f)
                    .add(Paragraph(l.severity).setFont(fB).setFontSize(9f).setFontColor(ColorConstants.WHITE))
            )
            sumT.addCell(
                Cell().setBackgroundColor(C_DARK).setBorder(Border.NO_BORDER).setPadding(9f)
                    .add(Paragraph(l.count).setFont(fB).setFontSize(9f).setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER))
            )

            for (risk in RISK_ORDER) {
                val cnt = report.findings.count { it.risk == risk }
                if (cnt == 0) continue
                sumT.addCell(
                    Cell().setBorder(Border.NO_BORDER).setBackgroundColor(riskBg(risk)).setPadding(9f)
                        .setBorderBottom(SolidBorder(C_BORDER, 0.5f))
                        .add(Paragraph(l.riskMap[risk] ?: risk).setFont(fB).setFontSize(10f).setFontColor(riskFg(risk)))
                )
                sumT.addCell(
                    Cell().setBorder(Border.NO_BORDER).setBackgroundColor(riskBg(risk)).setPadding(9f)
                        .setBorderBottom(SolidBorder(C_BORDER, 0.5f))
                        .add(Paragraph(cnt.toString()).setFont(fB).setFontSize(10f).setFontColor(riskFg(risk))
                            .setTextAlignment(TextAlignment.CENTER))
                )
            }

            // Fila total
            sumT.addCell(
                Cell().setBorder(Border.NO_BORDER).setBackgroundColor(C_LIGHT).setPadding(9f)
                    .add(Paragraph("TOTAL").setFont(fB).setFontSize(10f).setFontColor(C_TEXT))
            )
            sumT.addCell(
                Cell().setBorder(Border.NO_BORDER).setBackgroundColor(C_LIGHT).setPadding(9f)
                    .add(Paragraph(report.findings.size.toString()).setFont(fB).setFontSize(10f).setFontColor(C_TEXT)
                        .setTextAlignment(TextAlignment.CENTER))
            )
            doc.add(sumT)
        }

        // Disclaimer al pie del resumen
        doc.add(
            Paragraph(l.disclaimer)
                .setFont(fR).setFontSize(8f).setFontColor(C_MUTED)
                .setMarginTop(30f)
                .setBorderTop(SolidBorder(C_BORDER, 0.5f))
                .setPaddingTop(10f)
        )
    }

    // ── Hallazgos detallados ──────────────────────────────────────────────────
    private fun findingsPages(doc: Document, report: BlockchainReport, l: L, fR: PdfFont, fB: PdfFont, fM: PdfFont) {
        doc.add(sectionLabel(l.findingsDetail, fB))

        var idx = 1
        for (risk in RISK_ORDER) {
            val items = report.findings.filter { it.risk == risk }
            if (items.isEmpty()) continue

            // Cabecera de sección por severidad
            doc.add(
                Paragraph(l.riskMap[risk] ?: risk)
                    .setFont(fB).setFontSize(11f).setFontColor(riskFg(risk))
                    .setBackgroundColor(riskBg(risk)).setPadding(8f)
                    .setMarginTop(14f).setMarginBottom(8f)
            )

            for (f in items) {
                doc.add(findingCard(f, idx, l, fR, fB, fM))
                idx++
            }
        }
    }

    private fun findingCard(f: BlockchainFinding, idx: Int, l: L, fR: PdfFont, fB: PdfFont, fM: PdfFont): Table {
        val t = Table(UnitValue.createPercentArray(floatArrayOf(100f)))
            .useAllAvailableWidth().setMarginBottom(12f)
            .setBorder(SolidBorder(C_BORDER, 0.5f))

        // Cabecera del hallazgo
        t.addCell(
            Cell().setBorder(Border.NO_BORDER).setBackgroundColor(C_LIGHT).setPadding(10f)
                .add(
                    Paragraph()
                        .add(Text("${l.finding} #$idx").setFont(fB).setFontSize(9f).setFontColor(C_MUTED))
                        .add(Text("   ${f.id}").setFont(fM).setFontSize(8f).setFontColor(C_MUTED))
                        .add(Text("   " + (l.riskMap[f.risk] ?: f.risk)).setFont(fB).setFontSize(9f).setFontColor(riskFg(f.risk)))
                        .setMarginBottom(5f)
                )
                .add(Paragraph(f.title).setFont(fB).setFontSize(11f).setFontColor(C_TEXT).setMarginBottom(0f))
        )

        // Categoría
        t.addCell(
            Cell().setBorder(Border.NO_BORDER).setPadding(8f).setBorderTop(SolidBorder(C_BORDER, 0.5f))
                .add(
                    Paragraph()
                        .add(Text("${l.category}:  ").setFont(fB).setFontSize(9f).setFontColor(C_MUTED))
                        .add(Text(f.category).setFont(fR).setFontSize(9f).setFontColor(C_TEXT))
                )
        )

        // Descripción
        t.addCell(
            Cell().setBorder(Border.NO_BORDER).setPadding(10f).setBorderTop(SolidBorder(C_BORDER, 0.5f))
                .add(Paragraph(l.description).setFont(fB).setFontSize(8f).setFontColor(C_MUTED).setMarginBottom(3f))
                .add(Paragraph(f.description).setFont(fR).setFontSize(10f).setFontColor(C_TEXT))
        )

        // Evidencia técnica (opcional)
        if (!f.evidence.isNullOrBlank()) {
            t.addCell(
                Cell().setBorder(Border.NO_BORDER).setBackgroundColor(C_EVID_BG).setPadding(10f)
                    .setBorderTop(SolidBorder(C_BORDER, 0.5f))
                    .add(Paragraph(l.evidence).setFont(fB).setFontSize(8f).setFontColor(C_MUTED).setMarginBottom(3f))
                    .add(Paragraph(f.evidence).setFont(fM).setFontSize(8f).setFontColor(C_TEXT))
            )
        }

        // Recomendación
        t.addCell(
            Cell().setBorder(Border.NO_BORDER).setBackgroundColor(C_REC_BG).setPadding(10f)
                .setBorderTop(SolidBorder(C_BORDER, 0.5f))
                .add(Paragraph(l.recommendation).setFont(fB).setFontSize(8f).setFontColor(C_MUTED).setMarginBottom(3f))
                .add(Paragraph(f.recommendation).setFont(fR).setFontSize(10f).setFontColor(C_TEXT))
        )

        return t
    }

    // ── Helpers de construcción ───────────────────────────────────────────────

    private fun sectionLabel(text: String, fB: PdfFont) =
        Paragraph(text)
            .setFont(fB).setFontSize(14f).setFontColor(C_DARK)
            .setBorderBottom(SolidBorder(C_ACCENT, 2f))
            .setPaddingBottom(6f).setMarginBottom(14f).setMarginTop(4f)

    private fun infoRow(
        t: Table, label: String, value: String,
        fR: PdfFont, fB: PdfFont,
        mono: Boolean = false, fM: PdfFont? = null
    ) {
        t.addCell(
            Cell().setBorder(Border.NO_BORDER).setBackgroundColor(C_LIGHT).setPadding(7f)
                .setBorderBottom(SolidBorder(C_BORDER, 0.5f))
                .add(Paragraph(label).setFont(fB).setFontSize(9f).setFontColor(C_MUTED))
        )
        t.addCell(
            Cell().setBorder(Border.NO_BORDER).setPadding(7f)
                .setBorderBottom(SolidBorder(C_BORDER, 0.5f))
                .add(
                    Paragraph(value)
                        .setFont(if (mono && fM != null) fM else fR)
                        .setFontSize(if (mono) 8f else 10f)
                        .setFontColor(C_TEXT)
                )
        )
    }

    private fun fmtNum(s: String) = runCatching { "%,.0f".format(s.toBigDecimal()) }.getOrDefault(s)

    // ── Footer: separador + marca + número de página ──────────────────────────
    private class FooterHandler(
        private val fR: PdfFont,
        private val fB: PdfFont,
        private val l: L
    ) : IEventHandler {
        override fun handleEvent(event: Event) {
            val e      = event as PdfDocumentEvent
            val page   = e.page
            val ps     = page.pageSize
            val pageNum = e.document.getPageNumber(page)
            val canvas = PdfCanvas(page)

            // Línea separadora
            canvas.setStrokeColor(C_FOOTER_LINE)
                .setLineWidth(0.5f)
                .moveTo(44.0, 46.0)
                .lineTo((ps.width - 44).toDouble(), 46.0)
                .stroke()

            // Marca GuardianOS (izquierda)
            canvas.beginText()
                .setFontAndSize(fR, 7f)
                .setFillColor(C_FOOTER_TEXT)
                .moveText(44.0, 32.0)
                .showText(l.generatedBy)
                .endText()

            // Número de página (derecha)
            canvas.beginText()
                .setFontAndSize(fB, 7f)
                .setFillColor(C_PAGE_NUM)
                .moveText((ps.width - 68).toDouble(), 32.0)
                .showText("$pageNum")
                .endText()

            canvas.release()
        }
    }
}

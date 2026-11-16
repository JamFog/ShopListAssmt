package com.mmgpt.shoplistgpt.spreadsheet

import com.mmgpt.shoplistgpt.ShoppingCategory
import com.mmgpt.shoplistgpt.ShoppingItem
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Writes shopping lists as XLSX in the layout [SpreadsheetImporter] reads back:
 *  - one sheet, named after the plan date (yyyy-MM-dd);
 *  - one column per category, its heading in row 0 in bold, its items in the rows below;
 *  - items that are checked or struck are struck through, flagged items have a yellow fill,
 *    and items that are both get both.
 *
 * Note that the importer does not read the yellow fill, so flags don't survive a round trip.
 */
object SpreadsheetExporter {

    /**
     * Fixed column width, in 1/256ths of a character.
     *
     * Android quirk: do NOT replace this with Sheet.autoSizeColumn(). Auto-sizing measures
     * text with java.awt classes, which don't exist on Android, so it crashes the app at
     * runtime. JVM unit tests run with java.awt available, so they won't catch it.
     */
    const val COLUMN_WIDTH = 6000

    /** Suggested file name for an export made on [date]. */
    fun defaultFileName(date: LocalDate): String =
        "Shop-${date.format(DateTimeFormatter.ISO_DATE)}.xlsx"

    fun export(categories: List<ShoppingCategory>, planDate: LocalDate, output: OutputStream) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(planDate.format(DateTimeFormatter.ISO_DATE))
            val styles = Styles(workbook)

            categories.forEachIndexed { col, category ->
                val headerRow = sheet.getRow(0) ?: sheet.createRow(0)
                headerRow.createCell(col).apply {
                    setCellValue(category.heading)
                    setCellStyle(styles.heading)
                }
                category.items.forEachIndexed { index, item ->
                    val row = sheet.getRow(index + 1) ?: sheet.createRow(index + 1)
                    row.createCell(col).apply {
                        setCellValue(item.name)
                        styles.forItem(item)?.let { setCellStyle(it) }
                    }
                }
                sheet.setColumnWidth(col, COLUMN_WIDTH)
            }
            workbook.write(output)
        }
    }

    /** The four cell styles an export uses, created once per workbook. */
    private class Styles(workbook: XSSFWorkbook) {
        private val boldFont = workbook.createFont().apply { bold = true }
        private val strikeFont = workbook.createFont().apply { strikeout = true }

        val heading: CellStyle = workbook.createCellStyle().apply { setFont(boldFont) }
        private val struck: CellStyle = workbook.createCellStyle().apply { setFont(strikeFont) }
        private val flagged: CellStyle = workbook.createCellStyle().apply { fillYellow() }
        private val struckFlagged: CellStyle = workbook.createCellStyle().apply {
            setFont(strikeFont)
            fillYellow()
        }

        /** Style for [item], or null to leave the default (plain) style. */
        fun forItem(item: ShoppingItem): CellStyle? {
            val isStruck = item.isChecked || item.isStruck
            return when {
                item.isFlagged && isStruck -> struckFlagged
                item.isFlagged -> flagged
                isStruck -> struck
                else -> null
            }
        }

        private fun CellStyle.fillYellow() {
            fillForegroundColor = IndexedColors.YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
    }
}

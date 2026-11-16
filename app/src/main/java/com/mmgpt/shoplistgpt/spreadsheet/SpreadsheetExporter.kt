package com.mmgpt.shoplistgpt.spreadsheet

import com.mmgpt.shoplistgpt.ShoppingCategory
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Writes shopping lists as XLSX in the same layout [SpreadsheetImporter] reads: one column per
 * category with a bold heading in the first row, checked/struck items struck through and
 * flagged items highlighted yellow. The sheet is named after the plan date.
 */
object SpreadsheetExporter {

    /** Suggested file name for an export made on [date]. */
    fun defaultFileName(date: LocalDate): String =
        "Shop-${date.format(DateTimeFormatter.ISO_DATE)}.xlsx"

    fun export(categories: List<ShoppingCategory>, planDate: LocalDate, output: OutputStream) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(planDate.format(DateTimeFormatter.ISO_DATE))

            val headerFont = workbook.createFont().apply { bold = true }
            val headerStyle = workbook.createCellStyle().apply { setFont(headerFont) }

            val strikeFont = workbook.createFont().apply { strikeout = true }
            val strikeStyle = workbook.createCellStyle().apply { setFont(strikeFont) }

            val flaggedStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.YELLOW.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            val strikeFlaggedStyle = workbook.createCellStyle().apply {
                setFont(strikeFont)
                fillForegroundColor = IndexedColors.YELLOW.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            categories.forEachIndexed { col, category ->
                val headerRow = sheet.getRow(0) ?: sheet.createRow(0)
                headerRow.createCell(col).apply {
                    setCellValue(category.heading)
                    cellStyle = headerStyle
                }

                category.items.forEachIndexed { index, item ->
                    val row = sheet.getRow(index + 1) ?: sheet.createRow(index + 1)
                    val cell = row.createCell(col)
                    cell.setCellValue(item.name)
                    val struck = item.isChecked || item.isStruck
                    when {
                        item.isFlagged && struck -> cell.cellStyle = strikeFlaggedStyle
                        item.isFlagged -> cell.cellStyle = flaggedStyle
                        struck -> cell.cellStyle = strikeStyle
                    }
                }
                // Auto-sizing columns relies on java.awt classes which are not
                // available on Android and cause a crash. Use a fixed width instead.
                sheet.setColumnWidth(col, COLUMN_WIDTH)
            }

            workbook.write(output)
        }
    }

    private const val COLUMN_WIDTH = 6000
}

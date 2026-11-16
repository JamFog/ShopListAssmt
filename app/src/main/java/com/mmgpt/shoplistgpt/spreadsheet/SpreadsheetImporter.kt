package com.mmgpt.shoplistgpt.spreadsheet

import com.mmgpt.shoplistgpt.ShoppingCategory
import com.mmgpt.shoplistgpt.ShoppingItem
import com.opencsv.CSVReader
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses shopping lists from CSV and XLSX files.
 *
 * IDs are handed out by the caller through [nextItemId] and [nextCategoryId] so that
 * imported entries continue the caller's ID sequence.
 */
class SpreadsheetImporter(
    private val nextItemId: () -> Long,
    private val nextCategoryId: () -> Long
) {

    /** Result of an XLSX import: the categories plus the plan date taken from the sheet name. */
    data class XlsxResult(val categories: List<ShoppingCategory>, val planDate: LocalDate?)

    /**
     * Reads a CSV file. The first row is a header and is skipped; every other non-blank
     * cell becomes an item. All items go into a single "Items" category.
     */
    fun importCsv(input: InputStream): List<ShoppingCategory> {
        val reader = CSVReader(InputStreamReader(input))
        val items = mutableListOf<ShoppingItem>()
        reader.forEachIndexed { index, line ->
            if (index == 0) return@forEachIndexed // skip header
            line.forEach { cell ->
                val name = cell.trim()
                if (name.isNotEmpty()) {
                    items.add(ShoppingItem(id = nextItemId(), name = name))
                }
            }
        }
        return listOf(ShoppingCategory(nextCategoryId(), "Items", items))
    }

    /**
     * Reads the first sheet of an XLSX workbook. Columns are read left to right, top to
     * bottom; a bold cell starts a new category and a struck-through cell is an item that
     * is already checked. A sheet named with an ISO date (yyyy-MM-dd) gives the plan date.
     */
    fun importXlsx(input: InputStream): XlsxResult {
        XSSFWorkbook(input).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val planDate = try {
                LocalDate.parse(sheet.sheetName, DateTimeFormatter.ISO_DATE)
            } catch (e: Exception) {
                null
            }
            val categories = mutableListOf<ShoppingCategory>()
            val numColumns = sheet.getRow(0).lastCellNum.toInt()
            for (col in 0 until numColumns) {
                var heading: String? = null
                var items = mutableListOf<ShoppingItem>()
                for (rowIndex in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    val cell = row.getCell(col) ?: continue
                    val value = cell.toString().trim()
                    if (value.isEmpty()) continue
                    val font = workbook.getFontAt(cell.cellStyle.fontIndexAsInt)
                    if (font.bold) {
                        if (heading != null) {
                            categories.add(ShoppingCategory(nextCategoryId(), heading, items))
                            items = mutableListOf()
                        }
                        heading = value
                    } else {
                        items.add(
                            ShoppingItem(
                                id = nextItemId(),
                                name = value,
                                isChecked = font.strikeout,
                                isStruck = font.strikeout
                            )
                        )
                    }
                }
                if (heading != null) {
                    categories.add(ShoppingCategory(nextCategoryId(), heading, items))
                }
            }
            return XlsxResult(categories, planDate)
        }
    }

    companion object {
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        /** MIME types offered in the file picker: CSV or Excel, falling back to anything. */
        val IMPORT_MIME_TYPES = arrayOf(
            "text/csv",
            "application/vnd.ms-excel",
            "text/comma-separated-values",
            XLSX_MIME,
            "*/*"
        )

        /** True if a file with this MIME type / name should be read as XLSX rather than CSV. */
        fun isXlsx(mimeType: String?, fileName: String): Boolean =
            mimeType == XLSX_MIME || fileName.endsWith(".xlsx", ignoreCase = true)
    }
}

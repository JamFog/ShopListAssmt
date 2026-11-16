package com.mmgpt.shoplistgpt.spreadsheet

import com.mmgpt.shoplistgpt.ShoppingCategory
import com.mmgpt.shoplistgpt.ShoppingItem
import com.opencsv.CSVReader
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Turns CSV and XLSX shopping lists into [ShoppingCategory]s.
 *
 * Pure parsing: it reads from a stream and returns data, without touching Android, the
 * ViewModel or the UI, so the rules below can be unit-tested directly. IDs are drawn from
 * the [IdSequence]s the caller passes in.
 *
 * The import rules are deliberately pinned by tests (SpreadsheetImporterTest and
 * SpreadsheetImportExportTest); change them only on purpose.
 */
object SpreadsheetImporter {

    /** Heading given to the single category that a CSV import produces. */
    const val CSV_CATEGORY_HEADING = "Items"

    /** An XLSX import: the categories plus the plan date read from the sheet name, if any. */
    data class XlsxImport(val categories: List<ShoppingCategory>, val planDate: LocalDate?)

    /**
     * CSV rules:
     *  - the first row is a header and is skipped;
     *  - every other cell, trimmed, becomes an item if it isn't blank (row by row, left to right);
     *  - all items go into one category headed [CSV_CATEGORY_HEADING];
     *  - nothing is checked, struck or flagged.
     */
    fun importCsv(input: InputStream, itemIds: IdSequence, categoryIds: IdSequence): List<ShoppingCategory> {
        val items = mutableListOf<ShoppingItem>()
        CSVReader(InputStreamReader(input)).forEachIndexed { rowIndex, row ->
            if (rowIndex == 0) return@forEachIndexed
            for (cell in row) {
                val name = cell.trim()
                if (name.isNotEmpty()) items += ShoppingItem(id = itemIds.take(), name = name)
            }
        }
        return listOf(ShoppingCategory(categoryIds.take(), CSV_CATEGORY_HEADING, items))
    }

    /**
     * XLSX rules (first sheet only):
     *  - the sheet name, if it is an ISO date (yyyy-MM-dd), is the plan date; otherwise null;
     *  - columns are read left to right, each from top to bottom; the number of columns is
     *    taken from the first row;
     *  - cell text is trimmed and blank cells are skipped;
     *  - a **bold** cell starts a new category, so one column can hold several categories;
     *  - any other cell is an item of the current category; items above a column's first
     *    heading are kept and end up in that first heading's category;
     *  - a ~~struck-through~~ item comes in as both checked and struck;
     *  - a column with no bold cell produces no category.
     */
    fun importXlsx(input: InputStream, itemIds: IdSequence, categoryIds: IdSequence): XlsxImport =
        XSSFWorkbook(input).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val categories = mutableListOf<ShoppingCategory>()
            val columnCount = sheet.getRow(0).lastCellNum.toInt()

            for (col in 0 until columnCount) {
                var heading: String? = null
                var items = mutableListOf<ShoppingItem>()
                for (rowIndex in 0..sheet.lastRowNum) {
                    val cell = sheet.getRow(rowIndex)?.getCell(col) ?: continue
                    val value = cell.toString().trim()
                    if (value.isEmpty()) continue
                    val font = workbook.fontOf(cell)
                    if (font.bold) {
                        if (heading != null) {
                            categories += ShoppingCategory(categoryIds.take(), heading, items)
                            items = mutableListOf()
                        }
                        heading = value
                    } else {
                        items += ShoppingItem(
                            id = itemIds.take(),
                            name = value,
                            isChecked = font.strikeout,
                            isStruck = font.strikeout
                        )
                    }
                }
                if (heading != null) {
                    categories += ShoppingCategory(categoryIds.take(), heading, items)
                }
            }
            XlsxImport(categories, parsePlanDate(sheet.sheetName))
        }

    private fun parsePlanDate(sheetName: String): LocalDate? =
        try {
            LocalDate.parse(sheetName, DateTimeFormatter.ISO_DATE)
        } catch (e: Exception) {
            null
        }

    private fun Workbook.fontOf(cell: Cell) = getFontAt(cell.cellStyle.fontIndexAsInt)
}

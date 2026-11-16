package com.mmgpt.shoplistgpt.spreadsheet

import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Plain JVM tests for the import rules, calling [SpreadsheetImporter] directly — no activity,
 * ViewModel or Robolectric needed. The activity-level behaviour is covered separately by
 * SpreadsheetImportExportTest.
 */
class SpreadsheetImporterTest {

    // ---------------------------------------------------------------------- XLSX

    @Test
    fun boldCellsSplitOneColumnIntoSeveralCategories() {
        val result = xlsx {
            cell(0, 0, "Fruit", bold = true); cell(1, 0, "Apple")
            cell(2, 0, "Veg", bold = true); cell(3, 0, "Carrot"); cell(4, 0, "Leek")
        }

        assertEquals(listOf("Fruit", "Veg"), result.categories.map { it.heading })
        assertEquals(listOf("Apple"), result.categories[0].items.map { it.name })
        assertEquals(listOf("Carrot", "Leek"), result.categories[1].items.map { it.name })
    }

    @Test
    fun columnsAreReadLeftToRight() {
        val result = xlsx {
            cell(0, 0, "A", bold = true); cell(0, 1, "B", bold = true); cell(0, 2, "C", bold = true)
        }

        assertEquals(listOf("A", "B", "C"), result.categories.map { it.heading })
    }

    @Test
    fun struckCellsAreBothCheckedAndStruck() {
        val items = xlsx {
            cell(0, 0, "Fruit", bold = true)
            cell(1, 0, "Apple")
            cell(2, 0, "Banana", struck = true)
        }.categories.single().items

        assertFalse(items[0].isChecked || items[0].isStruck)
        assertTrue(items[1].isChecked && items[1].isStruck)
        assertTrue(items.none { it.isFlagged })
    }

    @Test
    fun boldAndStruckCellIsAHeadingNotAnItem() {
        val result = xlsx { cell(0, 0, "Fruit", bold = true, struck = true); cell(1, 0, "Apple") }

        assertEquals("Fruit", result.categories.single().heading)
        assertEquals(listOf("Apple"), result.categories.single().items.map { it.name })
    }

    @Test
    fun itemsAboveTheFirstHeadingJoinThatHeading() {
        val result = xlsx { cell(0, 0, "Orphan"); cell(1, 0, "Fruit", bold = true); cell(2, 0, "Apple") }

        assertEquals(listOf("Orphan", "Apple"), result.categories.single().items.map { it.name })
    }

    @Test
    fun columnWithoutAnyHeadingProducesNoCategory() {
        val result = xlsx {
            cell(0, 0, "Fruit", bold = true); cell(1, 0, "Apple")
            cell(0, 1, "loose"); cell(1, 1, "items")
        }

        assertEquals(listOf("Fruit"), result.categories.map { it.heading })
    }

    @Test
    fun headingWithNoItemsIsStillACategory() {
        val result = xlsx { cell(0, 0, "Empty", bold = true); cell(1, 0, "Next", bold = true) }

        assertEquals(listOf("Empty", "Next"), result.categories.map { it.heading })
        assertTrue(result.categories.all { it.items.isEmpty() })
    }

    @Test
    fun valuesAreTrimmedAndBlankCellsSkipped() {
        val result = xlsx { cell(0, 0, " Fruit ", bold = true); cell(1, 0, "   "); cell(3, 0, "  Apple ") }

        assertEquals("Fruit", result.categories.single().heading)
        assertEquals(listOf("Apple"), result.categories.single().items.map { it.name })
    }

    @Test
    fun isoDateSheetNameIsThePlanDate() {
        assertEquals(LocalDate.of(2026, 3, 14), xlsx("2026-03-14") { cell(0, 0, "F", bold = true) }.planDate)
    }

    @Test
    fun otherSheetNamesGiveNoPlanDate() {
        assertNull(xlsx("Groceries") { cell(0, 0, "F", bold = true) }.planDate)
        assertNull(xlsx("14-03-2026") { cell(0, 0, "F", bold = true) }.planDate)
    }

    @Test
    fun onlyTheFirstSheetIsRead() {
        val bytes = XSSFWorkbook().use { wb ->
            wb.createSheet("2026-03-14").put(wb, 0, 0, "First", bold = true)
            wb.createSheet("other").put(wb, 0, 0, "Second", bold = true)
            wb.bytes()
        }

        val result = SpreadsheetImporter.importXlsx(ByteArrayInputStream(bytes), IdSequence(), IdSequence())

        assertEquals(listOf("First"), result.categories.map { it.heading })
    }

    @Test
    fun xlsxIdsComeFromTheCallersSequences() {
        val itemIds = IdSequence(100)
        val categoryIds = IdSequence(10)
        val result = xlsx(itemIds = itemIds, categoryIds = categoryIds) {
            cell(0, 0, "Fruit", bold = true); cell(1, 0, "Apple"); cell(2, 0, "Pear")
            cell(0, 1, "Dairy", bold = true); cell(1, 1, "Milk")
        }

        assertEquals(listOf(10L, 11L), result.categories.map { it.id })
        assertEquals(listOf(100L, 101L, 102L), result.categories.flatMap { it.items }.map { it.id })
        assertEquals(103L, itemIds.next)
        assertEquals(12L, categoryIds.next)
    }

    // ----------------------------------------------------------------------- CSV

    @Test
    fun csvSkipsHeaderAndPutsEveryNonBlankCellInOneItemsCategory() {
        val cats = csv("Col A,Col B\nApple, Milk \n,Bread\n  ,\nEggs\n")

        assertEquals(SpreadsheetImporter.CSV_CATEGORY_HEADING, cats.single().heading)
        assertEquals(listOf("Apple", "Milk", "Bread", "Eggs"), cats.single().items.map { it.name })
        assertTrue(cats.single().items.none { it.isChecked || it.isStruck || it.isFlagged })
    }

    @Test
    fun csvWithOnlyAHeaderGivesOneEmptyCategory() {
        assertTrue(csv("Col A\n").single().items.isEmpty())
    }

    @Test
    fun csvIdsComeFromTheCallersSequences() {
        val itemIds = IdSequence(5)
        val categoryIds = IdSequence(2)
        val cats = SpreadsheetImporter.importCsv(
            ByteArrayInputStream("h\nA,B\n".toByteArray()), itemIds, categoryIds
        )

        assertEquals(2L, cats.single().id)
        assertEquals(listOf(5L, 6L), cats.single().items.map { it.id })
        assertEquals(7L, itemIds.next)
        assertEquals(3L, categoryIds.next)
    }

    // -------------------------------------------------------------------- format

    @Test
    fun xlsxIsDetectedByMimeTypeOrExtension_everythingElseIsCsv() {
        assertTrue(SpreadsheetFormat.isXlsx(SpreadsheetFormat.XLSX_MIME, "content://x/1234"))
        assertTrue(SpreadsheetFormat.isXlsx(null, "content://x/list.XLSX"))
        assertFalse(SpreadsheetFormat.isXlsx("text/csv", "content://x/list.csv"))
        assertFalse(SpreadsheetFormat.isXlsx(null, "content://x/download"))
    }

    // ------------------------------------------------------------------- helpers

    private class SheetBuilder(val wb: Workbook, val sheet: Sheet) {
        fun cell(row: Int, col: Int, value: String, bold: Boolean = false, struck: Boolean = false) =
            sheet.put(wb, row, col, value, bold, struck)
    }

    private fun xlsx(
        sheetName: String = "2026-03-14",
        itemIds: IdSequence = IdSequence(),
        categoryIds: IdSequence = IdSequence(),
        build: SheetBuilder.() -> Unit
    ): SpreadsheetImporter.XlsxImport {
        val bytes = XSSFWorkbook().use { wb ->
            SheetBuilder(wb, wb.createSheet(sheetName)).build()
            wb.bytes()
        }
        return SpreadsheetImporter.importXlsx(ByteArrayInputStream(bytes), itemIds, categoryIds)
    }

    private fun csv(text: String) =
        SpreadsheetImporter.importCsv(ByteArrayInputStream(text.toByteArray()), IdSequence(), IdSequence())
}

internal fun Sheet.put(
    wb: Workbook, row: Int, col: Int, value: String, bold: Boolean = false, struck: Boolean = false
) {
    val cell = (getRow(row) ?: createRow(row)).createCell(col)
    cell.setCellValue(value)
    if (bold || struck) {
        val font = wb.createFont().apply {
            this.bold = bold
            strikeout = struck
        }
        cell.cellStyle = wb.createCellStyle().apply { setFont(font) }
    }
}

internal fun Workbook.bytes(): ByteArray = ByteArrayOutputStream().also { write(it) }.toByteArray()

package com.mmgpt.shoplistgpt.spreadsheet

import com.mmgpt.shoplistgpt.ShoppingCategory
import com.mmgpt.shoplistgpt.ShoppingItem
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
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

/** Plain JVM tests for [SpreadsheetExporter], reading the written bytes back with POI. */
class SpreadsheetExporterTest {

    private val date = LocalDate.of(2026, 3, 14)

    @Test
    fun defaultFileNameIsShopDashIsoDate() {
        assertEquals("Shop-2026-03-14.xlsx", SpreadsheetExporter.defaultFileName(date))
    }

    @Test
    fun oneSheetNamedAfterPlanDate_oneColumnPerCategory() {
        val wb = export(
            ShoppingCategory(0, "Fruit", listOf(ShoppingItem(1, "Apple"), ShoppingItem(2, "Pear"))),
            ShoppingCategory(1, "Dairy", listOf(ShoppingItem(3, "Milk")))
        )

        assertEquals(1, wb.numberOfSheets)
        val sheet = wb.getSheetAt(0)
        assertEquals("2026-03-14", sheet.sheetName)
        assertEquals(listOf("Fruit", "Dairy"), (0..1).map { sheet.getRow(0).getCell(it).stringCellValue })
        assertEquals("Apple", sheet.getRow(1).getCell(0).stringCellValue)
        assertEquals("Pear", sheet.getRow(2).getCell(0).stringCellValue)
        assertEquals("Milk", sheet.getRow(1).getCell(1).stringCellValue)
        assertNull(sheet.getRow(2).getCell(1))
    }

    @Test
    fun columnsUseTheFixedWidth_notAutoSize() {
        val sheet = export(ShoppingCategory(0, "A very long heading indeed", emptyList())).getSheetAt(0)

        assertEquals(SpreadsheetExporter.COLUMN_WIDTH, sheet.getColumnWidth(0))
    }

    @Test
    fun fourStyles_headingBold_struckStrikethrough_flaggedYellow_bothCombined() {
        val sheet = export(
            ShoppingCategory(
                0, "Fruit", listOf(
                    ShoppingItem(1, "Plain"),
                    ShoppingItem(2, "Checked", isChecked = true),
                    ShoppingItem(3, "Struck", isStruck = true),
                    ShoppingItem(4, "Flagged", isFlagged = true),
                    ShoppingItem(5, "FlaggedStruck", isFlagged = true, isStruck = true)
                )
            )
        ).let { wb -> wb to wb.getSheetAt(0) }
        val (wb, s) = sheet
        fun cell(row: Int) = s.getRow(row).getCell(0)

        assertTrue(wb.fontOf(cell(0)).bold)
        assertLooks(wb, cell(1), struck = false, yellow = false)
        assertLooks(wb, cell(2), struck = true, yellow = false)
        assertLooks(wb, cell(3), struck = true, yellow = false)
        assertLooks(wb, cell(4), struck = false, yellow = true)
        assertLooks(wb, cell(5), struck = true, yellow = true)
    }

    @Test
    fun exportedFileImportsBackToTheSameHeadingsItemsAndDate() {
        val original = listOf(
            ShoppingCategory(0, "Fruit", listOf(ShoppingItem(1, "Apple"), ShoppingItem(2, "Banana", isChecked = true))),
            ShoppingCategory(1, "Dairy", listOf(ShoppingItem(3, "Milk", isFlagged = true)))
        )
        val out = ByteArrayOutputStream()
        SpreadsheetExporter.export(original, date, out)

        val back = SpreadsheetImporter.importXlsx(ByteArrayInputStream(out.toByteArray()), IdSequence(), IdSequence())

        assertEquals(date, back.planDate)
        assertEquals(listOf("Fruit", "Dairy"), back.categories.map { it.heading })
        assertEquals(listOf("Apple", "Banana"), back.categories[0].items.map { it.name })
        assertEquals(listOf(false, true), back.categories[0].items.map { it.isChecked })
        // Flags are not read back by the importer.
        assertFalse(back.categories[1].items.single().isFlagged)
    }

    private fun export(vararg categories: ShoppingCategory): Workbook {
        val out = ByteArrayOutputStream()
        SpreadsheetExporter.export(categories.toList(), date, out)
        return XSSFWorkbook(ByteArrayInputStream(out.toByteArray()))
    }

    private fun Workbook.fontOf(cell: Cell) = getFontAt(cell.cellStyle.fontIndexAsInt)

    private fun assertLooks(wb: Workbook, cell: Cell, struck: Boolean, yellow: Boolean) {
        val name = cell.stringCellValue
        assertEquals("strikeout of $name", struck, wb.fontOf(cell).strikeout)
        assertFalse("bold of $name", wb.fontOf(cell).bold)
        val isYellow = cell.cellStyle.fillPattern == FillPatternType.SOLID_FOREGROUND &&
            cell.cellStyle.fillForegroundColor == IndexedColors.YELLOW.index
        assertEquals("yellow fill of $name", yellow, isYellow)
    }
}

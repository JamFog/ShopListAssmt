package com.mmgpt.shoplistgpt

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Task 4 safety net: characterisation tests for spreadsheet import/export.
 *
 * These pin down the CURRENT behaviour of MainActivity's CSV/XLSX import and XLSX export
 * before that code is refactored out of the activity. They only go through the public
 * surface (the start-page buttons, the system file pickers' results, the ViewModel and the
 * bytes written to the output URI), so they don't care where the parsing code lives and
 * should pass unchanged before and after the refactor.
 */
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SpreadsheetImportExportTest {

    private lateinit var activity: MainActivity
    private lateinit var viewModel: ShoppingListViewModel

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        viewModel = ViewModelProvider(activity)[ShoppingListViewModel::class.java]
    }

    // ------------------------------------------------------------------ XLSX import

    @Test
    fun importXlsx_boldCellsStartCategories_columnsReadLeftToRight() {
        val bytes = workbook("2026-03-14") { wb ->
            val sheet = wb.getSheetAt(0)
            // Column 0 holds two categories; column 1 holds one.
            put(wb, sheet, 0, 0, "Fruit", bold = true)
            put(wb, sheet, 1, 0, "Apple")
            put(wb, sheet, 2, 0, "Banana", struck = true)
            put(wb, sheet, 3, 0, "Veg", bold = true)
            put(wb, sheet, 4, 0, "Carrot")
            put(wb, sheet, 0, 1, "Dairy", bold = true)
            put(wb, sheet, 1, 1, "Milk")
        }

        importFile("content://test/shop.xlsx", bytes)

        val cats = viewModel.categories.value!!
        assertEquals(listOf("Fruit", "Veg", "Dairy"), cats.map { it.heading })
        assertEquals(listOf("Apple", "Banana"), cats[0].items.map { it.name })
        assertEquals(listOf("Carrot"), cats[1].items.map { it.name })
        assertEquals(listOf("Milk"), cats[2].items.map { it.name })
    }

    @Test
    fun importXlsx_struckCellsAreCheckedAndStruck_plainCellsAreNeither() {
        val bytes = workbook("2026-03-14") { wb ->
            val sheet = wb.getSheetAt(0)
            put(wb, sheet, 0, 0, "Fruit", bold = true)
            put(wb, sheet, 1, 0, "Apple")
            put(wb, sheet, 2, 0, "Banana", struck = true)
        }

        importFile("content://test/shop.xlsx", bytes)

        val (apple, banana) = viewModel.categories.value!!.single().items
        assertFalse(apple.isChecked)
        assertFalse(apple.isStruck)
        assertFalse(apple.isFlagged)
        assertTrue(banana.isChecked)
        assertTrue(banana.isStruck)
        assertFalse(banana.isFlagged)
    }

    @Test
    fun importXlsx_trimsValues_skipsBlankCellsAndGapRows() {
        val bytes = workbook("2026-03-14") { wb ->
            val sheet = wb.getSheetAt(0)
            put(wb, sheet, 0, 0, "  Fruit ", bold = true)
            put(wb, sheet, 1, 0, "   ")
            // row 2 left entirely empty
            put(wb, sheet, 3, 0, " Apple  ")
        }

        importFile("content://test/shop.xlsx", bytes)

        val cat = viewModel.categories.value!!.single()
        assertEquals("Fruit", cat.heading)
        assertEquals(listOf("Apple"), cat.items.map { it.name })
    }

    @Test
    fun importXlsx_itemsAboveFirstHeadingInAColumnJoinThatHeading() {
        val bytes = workbook("2026-03-14") { wb ->
            val sheet = wb.getSheetAt(0)
            put(wb, sheet, 0, 0, "Orphan")
            put(wb, sheet, 1, 0, "Fruit", bold = true)
            put(wb, sheet, 2, 0, "Apple")
        }

        importFile("content://test/shop.xlsx", bytes)

        // Orphan is collected into the pending item list, then that list is attached to the
        // first heading because no earlier heading flushed it.
        val cat = viewModel.categories.value!!.single()
        assertEquals("Fruit", cat.heading)
        assertEquals(listOf("Orphan", "Apple"), cat.items.map { it.name })
    }

    @Test
    fun importXlsx_isoDateSheetNameBecomesPlanDate() {
        val bytes = workbook("2026-03-14") { wb ->
            put(wb, wb.getSheetAt(0), 0, 0, "Fruit", bold = true)
        }

        importFile("content://test/shop.xlsx", bytes)

        assertEquals(LocalDate.of(2026, 3, 14), viewModel.planDate.value)
    }

    @Test
    fun importXlsx_nonDateSheetNameClearsPlanDate() {
        viewModel.setPlanDate(LocalDate.of(2020, 1, 1))
        val bytes = workbook("Groceries") { wb ->
            put(wb, wb.getSheetAt(0), 0, 0, "Fruit", bold = true)
        }

        importFile("content://test/shop.xlsx", bytes)

        assertNull(viewModel.planDate.value)
    }

    @Test
    fun importXlsx_replacesExistingCategories_andShowsShoppingPager() {
        viewModel.setCategories(listOf(ShoppingCategory(0, "Old", listOf(ShoppingItem(0, "x")))))
        val bytes = workbook("2026-03-14") { wb ->
            put(wb, wb.getSheetAt(0), 0, 0, "Fruit", bold = true)
        }

        importFile("content://test/shop.xlsx", bytes)

        assertEquals(listOf("Fruit"), viewModel.categories.value!!.map { it.heading })
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.viewPager).visibility)
        assertEquals("Shop (Planned 14 Mar)", activity.supportActionBar?.title?.toString())
    }

    @Test
    fun importXlsx_givesEveryCategoryAndItemAUniqueId() {
        val bytes = workbook("2026-03-14") { wb ->
            val sheet = wb.getSheetAt(0)
            put(wb, sheet, 0, 0, "Fruit", bold = true)
            put(wb, sheet, 1, 0, "Apple")
            put(wb, sheet, 2, 0, "Pear")
            put(wb, sheet, 0, 1, "Dairy", bold = true)
            put(wb, sheet, 1, 1, "Milk")
        }

        importFile("content://test/shop.xlsx", bytes)

        val cats = viewModel.categories.value!!
        assertEquals(cats.size, cats.map { it.id }.toSet().size)
        val items = cats.flatMap { it.items }
        assertEquals(items.size, items.map { it.id }.toSet().size)
    }

    // ------------------------------------------------------------------- CSV import

    @Test
    fun importCsv_skipsHeaderRow_everyNonBlankCellBecomesAnItemInOneCategory() {
        val csv = "Heading A,Heading B\nApple, Milk \n,Bread\n  ,\nEggs\n"

        importFile("content://test/list.csv", csv.toByteArray())

        val cat = viewModel.categories.value!!.single()
        assertEquals("Items", cat.heading)
        assertEquals(listOf("Apple", "Milk", "Bread", "Eggs"), cat.items.map { it.name })
        assertTrue(cat.items.none { it.isChecked || it.isStruck || it.isFlagged })
        assertEquals(cat.items.size, cat.items.map { it.id }.toSet().size)
    }

    @Test
    fun importCsv_leavesPlanDateUnchanged_andShowsShoppingPager() {
        viewModel.setPlanDate(LocalDate.of(2026, 5, 1))

        importFile("content://test/list.csv", "h\nApple\n".toByteArray())

        assertEquals(LocalDate.of(2026, 5, 1), viewModel.planDate.value)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.viewPager).visibility)
    }

    @Test
    fun importFile_unknownExtensionIsTreatedAsCsv() {
        importFile("content://test/download", "h\nApple\n".toByteArray())

        assertEquals("Items", viewModel.categories.value!!.single().heading)
    }

    // ------------------------------------------------------------------ XLSX export

    @Test
    fun export_suggestsDatedFileName() {
        clickAndSettle(R.id.btnExport)

        val started = shadowOf(activity).nextStartedActivityForResult.intent
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        assertEquals("Shop-$today.xlsx", started.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun export_writesOneColumnPerCategory_headingInRow0_itemsBelow() {
        viewModel.setPlanDate(LocalDate.of(2026, 3, 14))
        viewModel.setCategories(
            listOf(
                ShoppingCategory(0, "Fruit", listOf(ShoppingItem(1, "Apple"), ShoppingItem(2, "Pear"))),
                ShoppingCategory(1, "Dairy", listOf(ShoppingItem(3, "Milk")))
            )
        )

        exportAndRead().use { wb ->
            assertEquals(1, wb.numberOfSheets)
            val sheet = wb.getSheetAt(0)
            assertEquals("2026-03-14", sheet.sheetName)
            assertEquals("Fruit", sheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("Dairy", sheet.getRow(0).getCell(1).stringCellValue)
            assertEquals("Apple", sheet.getRow(1).getCell(0).stringCellValue)
            assertEquals("Milk", sheet.getRow(1).getCell(1).stringCellValue)
            assertEquals("Pear", sheet.getRow(2).getCell(0).stringCellValue)
            assertNull(sheet.getRow(2).getCell(1))
            assertEquals(6000, sheet.getColumnWidth(0))
            assertEquals(6000, sheet.getColumnWidth(1))
        }
        assertEquals("Exported", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun export_withNoPlanDate_namesSheetAfterToday() {
        viewModel.setPlanDate(null)
        viewModel.setCategories(listOf(ShoppingCategory(0, "Fruit", emptyList())))

        exportAndRead().use { wb ->
            assertEquals(LocalDate.now().format(DateTimeFormatter.ISO_DATE), wb.getSheetAt(0).sheetName)
        }
    }

    @Test
    fun export_stylesHeadingsBold_checkedOrStruckStruckThrough_flaggedYellow() {
        viewModel.setCategories(
            listOf(
                ShoppingCategory(
                    0, "Fruit", listOf(
                        ShoppingItem(1, "Plain"),
                        ShoppingItem(2, "Checked", isChecked = true),
                        ShoppingItem(3, "Struck", isStruck = true),
                        ShoppingItem(4, "Flagged", isFlagged = true),
                        ShoppingItem(5, "FlaggedChecked", isFlagged = true, isChecked = true)
                    )
                )
            )
        )

        exportAndRead().use { wb ->
            val sheet = wb.getSheetAt(0)
            fun cell(row: Int) = sheet.getRow(row).getCell(0)

            assertTrue(isBold(wb, cell(0)))

            assertStyle(wb, cell(1), struck = false, yellow = false)
            assertStyle(wb, cell(2), struck = true, yellow = false)
            assertStyle(wb, cell(3), struck = true, yellow = false)
            assertStyle(wb, cell(4), struck = false, yellow = true)
            assertStyle(wb, cell(5), struck = true, yellow = true)
        }
    }

    // ------------------------------------------------------------------- round trip

    @Test
    fun exportThenImport_keepsHeadingsNamesOrderAndDate_butLosesFlags() {
        viewModel.setPlanDate(LocalDate.of(2026, 3, 14))
        val original = listOf(
            ShoppingCategory(
                0, "Fruit", listOf(
                    ShoppingItem(1, "Apple"),
                    ShoppingItem(2, "Banana", isChecked = true),
                    ShoppingItem(3, "Cherry", isFlagged = true)
                )
            ),
            ShoppingCategory(1, "Dairy", listOf(ShoppingItem(4, "Milk", isStruck = true)))
        )
        viewModel.setCategories(original)

        val bytes = exportBytes()
        viewModel.setCategories(emptyList())
        viewModel.setPlanDate(null)
        importFile("content://test/roundtrip.xlsx", bytes)

        val cats = viewModel.categories.value!!
        assertEquals(listOf("Fruit", "Dairy"), cats.map { it.heading })
        assertEquals(listOf("Apple", "Banana", "Cherry"), cats[0].items.map { it.name })
        assertEquals(listOf("Milk"), cats[1].items.map { it.name })
        assertEquals(LocalDate.of(2026, 3, 14), viewModel.planDate.value)

        // Checked and struck both export as strikethrough, which imports as checked + struck.
        assertEquals(listOf(false, true, false), cats[0].items.map { it.isChecked })
        assertEquals(listOf(false, true, false), cats[0].items.map { it.isStruck })
        assertTrue(cats[1].items.single().isChecked)
        // Import does not read the yellow fill, so flags are not round-tripped.
        assertTrue(cats.flatMap { it.items }.none { it.isFlagged })
    }

    // ---------------------------------------------------------------------- helpers

    /** Taps Import, then answers the system file picker with [uri], whose content is [bytes]. */
    private fun importFile(uri: String, bytes: ByteArray) {
        val parsed = Uri.parse(uri)
        shadowOf(activity.contentResolver).registerInputStreamSupplier(parsed) {
            ByteArrayInputStream(bytes)
        }
        clickAndSettle(R.id.btnImport)
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
        shadowOf(activity).receiveResult(
            request.intent,
            Activity.RESULT_OK,
            Intent().setData(parsed).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        settle()
    }

    /** Taps Export, answers the "create document" picker, and returns the bytes written. */
    private fun exportBytes(): ByteArray {
        val uri = Uri.parse("content://test/export.xlsx")
        val out = ByteArrayOutputStream()
        shadowOf(activity.contentResolver).registerOutputStream(uri, out)
        clickAndSettle(R.id.btnExport)
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        shadowOf(activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        settle()
        return out.toByteArray()
    }

    private fun exportAndRead(): Workbook = XSSFWorkbook(ByteArrayInputStream(exportBytes()))

    private fun clickAndSettle(id: Int) {
        activity.findViewById<View>(id).performClick()
        settle()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun workbook(sheetName: String, fill: (XSSFWorkbook) -> Unit): ByteArray {
        XSSFWorkbook().use { wb ->
            wb.createSheet(sheetName)
            fill(wb)
            return ByteArrayOutputStream().also { wb.write(it) }.toByteArray()
        }
    }

    private fun put(
        wb: Workbook,
        sheet: org.apache.poi.ss.usermodel.Sheet,
        row: Int,
        col: Int,
        value: String,
        bold: Boolean = false,
        struck: Boolean = false
    ) {
        val r = sheet.getRow(row) ?: sheet.createRow(row)
        val cell = r.createCell(col)
        cell.setCellValue(value)
        if (bold || struck) {
            val font = wb.createFont().apply {
                this.bold = bold
                strikeout = struck
            }
            cell.cellStyle = wb.createCellStyle().apply { setFont(font) }
        }
    }

    private fun isBold(wb: Workbook, cell: Cell) = wb.getFontAt(cell.cellStyle.fontIndexAsInt).bold

    private fun assertStyle(wb: Workbook, cell: Cell, struck: Boolean, yellow: Boolean) {
        val font = wb.getFontAt(cell.cellStyle.fontIndexAsInt)
        assertEquals("strikeout of '${cell.stringCellValue}'", struck, font.strikeout)
        assertFalse("bold of '${cell.stringCellValue}'", font.bold)
        val isYellow = cell.cellStyle.fillPattern == FillPatternType.SOLID_FOREGROUND &&
            cell.cellStyle.fillForegroundColor == IndexedColors.YELLOW.index
        assertEquals("yellow fill of '${cell.stringCellValue}'", yellow, isYellow)
    }
}

package com.mmgpt.shoplistgpt

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/**
 * Task 2 – double-tapping a category name should open that category's page.
 *
 * Plan mode: double-tap a row on the "Categories" page (pager page 0).
 * Shop mode: double-tap a row on the "Summary" page (last pager page).
 *
 * Each test drives the real MainActivity / ViewPager2 / fragments under Robolectric and
 * delivers real touch events through the window, so the production GestureDetector code runs.
 * "Opened the right page" is checked by reading the heading of the page now showing, which
 * makes the assertions independent of how the eventual fix is implemented.
 *
 * Tests marked BASELINE are expected to pass before the fix (the simple path works).
 * Tests marked BUG are expected to FAIL before the fix and pass after it.
 */
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(qualifiers = "w411dp-h891dp")
class DoubleTapNavigationTest {

    private lateinit var activity: MainActivity
    private lateinit var viewModel: ShoppingListViewModel
    private val pager: ViewPager2 get() = activity.findViewById(R.id.viewPager)

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        viewModel = ViewModelProvider(activity)[ShoppingListViewModel::class.java]
    }

    // ---------------------------------------------------------------- Shop mode

    /** BASELINE: summary double-tap works when the category list hasn't changed size. */
    @Test
    fun shopSummary_doubleTap_opensTappedCategory() {
        val names = listOf("Fruit", "Dairy", "Bakery", "Frozen")
        viewModel.setCategories(categories(names))
        click(R.id.btnShop)
        val summaryPage = names.size

        names.forEachIndexed { i, name ->
            goToPage(summaryPage)
            assertEquals(activity.getString(R.string.summary_heading), currentHeading())
            doubleTap(rowNamed(currentList(), name))
            assertEquals("pager position after double-tapping '$name'", i, pager.currentItem)
            assertEquals("page shown after double-tapping '$name'", name, currentHeading())
        }
    }

    /**
     * BUG (root cause 2): after the number of categories grows, the page that used to be the
     * Summary page must now show a category. FragmentStateAdapter keys pages by position, so
     * it reuses the stale ShopCategoriesFragment at the old summary position.
     */
    @Test
    fun shopPages_matchCategories_afterCategoryCountChanges() {
        growCategoriesWhileSummaryWasShowing(from = 3, to = 5)

        // Pager is still on position 3, which is now category C3, not the summary.
        assertEquals(3, pager.currentItem)
        assertEquals("page at old summary position", "C3", currentHeading())
    }

    /** BUG (root cause 2): the user-visible symptom — summary double-tap lands on the wrong page. */
    @Test
    fun shopSummary_doubleTap_opensTappedCategory_afterCategoryCountChanges() {
        growCategoriesWhileSummaryWasShowing(from = 3, to = 5)

        goToPage(5)
        assertEquals(activity.getString(R.string.summary_heading), currentHeading())
        doubleTap(rowNamed(currentList(), "C3"))

        assertEquals(3, pager.currentItem)
        assertEquals("page shown after double-tapping 'C3'", "C3", currentHeading())
    }

    // ---------------------------------------------------------------- Plan mode

    /** BASELINE: Plan categories double-tap works when nothing has been reordered. */
    @Test
    fun planCategories_doubleTap_opensTappedCategory() {
        val names = listOf("Fruit", "Dairy", "Bakery", "Frozen")
        viewModel.setCategories(categories(names))
        click(R.id.btnPlan)

        names.forEachIndexed { i, name ->
            goToPage(0)
            assertEquals(activity.getString(R.string.categories_heading), currentHeading())
            doubleTap(rowNamed(currentList(), name))
            assertEquals("pager position after double-tapping '$name'", i + 1, pager.currentItem)
            assertEquals("page shown after double-tapping '$name'", name, currentHeading())
        }
    }

    /**
     * BUG (root cause 1): after a drag-reorder, the order shown on the Categories page must
     * equal the order in the ViewModel (which is what the category pages are built from).
     */
    @Test
    fun planCategories_dragReorder_keepsDisplayedOrderInSyncWithViewModel() {
        viewModel.setCategories(categories(listOf("C0", "C1", "C2", "C3")))
        click(R.id.btnPlan)

        dragFirstCategoryDownOneSlot()

        val list = currentList()
        val displayed = (list.adapter as ItemAdapter).currentList
            .filter { it.isHeading }.map { it.name }
        val inViewModel = viewModel.categories.value!!.map { it.heading }
        assertEquals("Categories page order vs ViewModel order", inViewModel, displayed)
    }

    /** BUG (root cause 1): the user-visible symptom — after a drag, double-tap opens the wrong page. */
    @Test
    fun planCategories_doubleTap_opensTappedCategory_afterDragReorder() {
        viewModel.setCategories(categories(listOf("C0", "C1", "C2", "C3")))
        click(R.id.btnPlan)

        dragFirstCategoryDownOneSlot()

        val shownOrder = (currentList().adapter as ItemAdapter).currentList
            .filter { it.isHeading }.map { it.name }
        shownOrder.forEachIndexed { i, name ->
            goToPage(0)
            doubleTap(rowNamed(currentList(), name))
            assertEquals("pager position after double-tapping '$name'", i + 1, pager.currentItem)
            assertEquals("page shown after double-tapping '$name'", name, currentHeading())
        }
    }

    // ---------------------------------------------------------------- scenario helpers

    /**
     * Shop mode with [from] categories, user views the Summary page, goes Home, then the
     * category list grows to [to] (as an Import would do) and the user taps Shop again.
     */
    private fun growCategoriesWhileSummaryWasShowing(from: Int, to: Int) {
        viewModel.setCategories(categories((0 until from).map { "C$it" }))
        click(R.id.btnShop)
        goToPage(from) // Summary page
        assertEquals(activity.getString(R.string.summary_heading), currentHeading())

        @Suppress("DEPRECATION")
        activity.onBackPressed() // back to the start page
        settle()

        viewModel.setCategories(categories((0 until to).map { "C$it" }))
        click(R.id.btnShop)
    }

    /**
     * Drags "C0" (adapter row 1; row 0 is the "Add" placeholder) onto "C1" (row 2) and drops it.
     *
     * The drag callback is invoked the way ItemTouchHelper does on a device: onMove fires on
     * each MOVE event while the dragged view still overlaps the target. Here it fires twice
     * before the main looper runs, i.e. before ListAdapter's background diff has been applied
     * to the RecyclerView — the window in which root cause 1 occurs. Then clearView = drop.
     */
    private fun dragFirstCategoryDownOneSlot() {
        val list = currentList()
        val callback = dragCallback(list)
        val dragged = list.findViewHolderForAdapterPosition(1)!!
        val target = list.findViewHolderForAdapterPosition(2)!!

        callback.onMove(list, dragged, target)
        callback.onMove(list, dragged, target)
        callback.clearView(list, dragged)
        settle()
    }

    // ---------------------------------------------------------------- UI helpers

    private fun categories(names: List<String>) = names.mapIndexed { i, name ->
        ShoppingCategory(i.toLong(), name, listOf(ShoppingItem(100L + i, "$name item")))
    }

    private fun click(id: Int) {
        activity.findViewById<View>(id).performClick()
        settle()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
    }

    private fun goToPage(position: Int) {
        pager.setCurrentItem(position, false)
        settle()
    }

    /** The fragment ViewPager2 is currently displaying (FragmentStateAdapter tags it "f<itemId>"). */
    private fun currentPage(): Fragment {
        val adapter = pager.adapter!!
        val tag = "f" + adapter.getItemId(pager.currentItem)
        return activity.supportFragmentManager.findFragmentByTag(tag)
            ?: error("No fragment for pager position ${pager.currentItem}")
    }

    private fun currentHeading(): String =
        currentPage().requireView().findViewById<TextView>(R.id.tvHeading).text.toString()

    private fun currentList(): RecyclerView =
        currentPage().requireView().findViewById(R.id.recyclerView)

    private fun rowNamed(list: RecyclerView, name: String): View =
        (0 until list.childCount).map { list.getChildAt(it) }
            .firstOrNull { it.findViewById<TextView>(R.id.tvName)?.text?.toString() == name }
            ?: error("No row named '$name' in list")

    /** Two quick taps on the centre of [view], dispatched through the window like real input. */
    private fun doubleTap(view: View) {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val x = loc[0] + view.width / 2f
        val y = loc[1] + view.height / 2f
        val root = activity.window.decorView
        val t = SystemClock.uptimeMillis()

        fun send(action: Int, downTime: Long, eventTime: Long) {
            val ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            root.dispatchTouchEvent(ev)
            ev.recycle()
        }
        send(MotionEvent.ACTION_DOWN, t, t)
        send(MotionEvent.ACTION_UP, t, t + 50)
        send(MotionEvent.ACTION_DOWN, t + 150, t + 150)
        send(MotionEvent.ACTION_UP, t + 150, t + 200)
        settle()
    }

    /** The drag-and-drop callback installed by PlanCategoriesFragment on its RecyclerView. */
    private fun dragCallback(list: RecyclerView): ItemTouchHelper.Callback {
        val helper = (0 until list.itemDecorationCount)
            .map { list.getItemDecorationAt(it) }
            .filterIsInstance<ItemTouchHelper>()
            .single()
        val field = ItemTouchHelper::class.java.getDeclaredField("mCallback")
        field.isAccessible = true
        return field.get(helper) as ItemTouchHelper.Callback
    }
}

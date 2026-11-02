package com.mmgpt.shoplistgpt

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

import com.mmgpt.shoplistgpt.PlanCategoryFragment
import com.mmgpt.shoplistgpt.PlanCategoriesFragment

/**
 * Pages: one per category, plus one "overview" page that lists all categories
 * (first page in Plan mode, last page — the Summary — in Shop mode).
 */
class CategoryPagerAdapter(
    activity: FragmentActivity,
    private val viewModel: ShoppingListViewModel,
    private val planMode: Boolean = false
) : FragmentStateAdapter(activity) {

    private val categoryCount: Int
        get() = viewModel.categories.value?.size ?: 0

    private val overviewPosition: Int
        get() = if (planMode) 0 else categoryCount

    override fun getItemCount(): Int = categoryCount + 1

    override fun createFragment(position: Int): Fragment =
        if (planMode) {
            if (position == overviewPosition) {
                PlanCategoriesFragment()
            } else {
                PlanCategoryFragment.newInstance(position - 1)
            }
        } else {
            if (position == overviewPosition) {
                ShopCategoriesFragment()
            } else {
                CategoryFragment.newInstance(position)
            }
        }

    /*
     * FragmentStateAdapter caches fragments by item id, and the default id is the position.
     * In Shop mode the Summary page's position moves when the category count changes, so a
     * stale ShopCategoriesFragment used to be reused at the position where a category page
     * now belongs. Giving the overview page its own fixed id means a page's id changes
     * whenever its fragment type would, so the adapter creates the right fragment.
     * Category pages keep position-based ids because their fragments look categories up by
     * index, which is exactly what the position represents.
     */
    override fun getItemId(position: Int): Long =
        if (position == overviewPosition) OVERVIEW_PAGE_ID else position.toLong()

    override fun containsItem(itemId: Long): Boolean {
        if (itemId == OVERVIEW_PAGE_ID) return true
        val firstCategoryPosition = if (planMode) 1 else 0
        return itemId in firstCategoryPosition.toLong() until (firstCategoryPosition + categoryCount).toLong()
    }

    companion object {
        private const val OVERVIEW_PAGE_ID = -1L
    }
}

package com.mmgpt.shoplistgpt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.Toast
import android.view.MotionEvent

import com.mmgpt.shoplistgpt.databinding.ActivityMainBinding
import com.mmgpt.shoplistgpt.databinding.StartPageBinding
import com.mmgpt.shoplistgpt.CategoryPagerAdapter
import com.mmgpt.shoplistgpt.ShoppingCategory
import android.view.Menu
import android.view.MenuItem

import com.mmgpt.shoplistgpt.spreadsheet.SpreadsheetExporter
import com.mmgpt.shoplistgpt.spreadsheet.SpreadsheetImporter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var startBinding: StartPageBinding
    private lateinit var viewModel: ShoppingListViewModel
    private lateinit var pagerAdapter: CategoryPagerAdapter

    private var nextItemId = 0L
    private var nextCategoryId = 0L
    private var inPlanMode = false
    private var touchStartX = 0f

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                // grant yourself read permission for this URI
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                importFile(it)
            }
        }

    private val createFileLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(SpreadsheetImporter.XLSX_MIME)
        ) { uri ->
            uri?.let { exportXlsx(it) }
        }

    override fun onStop() {
        super.onStop()
        viewModel.saveState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // `startPage` is already generated as a StartPageBinding, so we can
        // reference it directly without rebinding it to a View.
        startBinding = binding.startPage

        // Use the toolbar defined in the layout as the Activity's ActionBar
        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(ShoppingListViewModel::class.java)

        // Update next ID counters from any persisted data
        nextItemId = viewModel.categories.value
            ?.flatMap { it.items }
            ?.maxOfOrNull { it.id }?.plus(1) ?: 0L
        nextCategoryId = viewModel.categories.value
            ?.maxOfOrNull { it.id }?.plus(1) ?: 0L

        pagerAdapter = CategoryPagerAdapter(this, viewModel, false)
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.adapter = pagerAdapter

        val pagerRecyclerView = binding.viewPager.getChildAt(0) as RecyclerView
        pagerRecyclerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - touchStartX
                    val threshold = 100
                    val lastIndex = pagerAdapter.itemCount - 1
                    when {
                        deltaX > threshold && binding.viewPager.currentItem == 0 -> {
                            binding.viewPager.currentItem = lastIndex
                            true
                        }
                        deltaX < -threshold && binding.viewPager.currentItem == lastIndex -> {
                            binding.viewPager.currentItem = 0
                            true
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }

        var lastSize = viewModel.categories.value?.size ?: 0
        viewModel.categories.observe(this) { categories ->
            val newSize = categories.size
            if (newSize != lastSize) {
                pagerAdapter.notifyDataSetChanged()
                lastSize = newSize
            }
        }

        startBinding.btnShop.setOnClickListener { showShopping() }
        startBinding.btnImport.setOnClickListener { pickCsvFile() }
        startBinding.btnPlan.setOnClickListener { showPlanning() }
        startBinding.btnExport.setOnClickListener { promptForExport() }

        showStartPage()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_home -> {
                showStartPage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (binding.viewPager.visibility == View.VISIBLE) {
            showStartPage()
        } else {
            super.onBackPressed()
        }
    }

    private fun pickCsvFile() {
        // allow CSV or Excel files
        filePickerLauncher.launch(SpreadsheetImporter.IMPORT_MIME_TYPES)
    }

    private fun importFile(uri: Uri) {
        val importer = SpreadsheetImporter(
            nextItemId = { nextItemId++ },
            nextCategoryId = { nextCategoryId++ }
        )
        val isXlsx = SpreadsheetImporter.isXlsx(contentResolver.getType(uri), uri.toString())
        contentResolver.openInputStream(uri)?.use { input ->
            if (isXlsx) {
                val result = importer.importXlsx(input)
                viewModel.setPlanDate(result.planDate)
                viewModel.setCategories(result.categories)
            } else {
                viewModel.setCategories(importer.importCsv(input))
            }
            showShopping()
        }
    }

    private fun promptForExport() {
        createFileLauncher.launch(SpreadsheetExporter.defaultFileName(LocalDate.now()))
    }

    private fun exportXlsx(uri: Uri) {
        val categories = viewModel.categories.value ?: emptyList()
        val plan = viewModel.planDate.value ?: LocalDate.now()
        contentResolver.openOutputStream(uri)?.use { output ->
            SpreadsheetExporter.export(categories, plan, output)
        }
        Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show()
    }

    private fun showStartPage() {
        // `startPage` is an included layout so we need its root view to
        // control visibility.
        binding.startPage.root.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
        supportActionBar?.title = getString(R.string.app_name)
        if (inPlanMode) {
            val updated = viewModel.categories.value?.map { cat ->
                cat.copy(items = cat.items.map { it.copy(isStruck = it.isChecked) })
            } ?: emptyList()
            viewModel.setCategories(updated)
            pagerAdapter = CategoryPagerAdapter(this, viewModel, false)
            binding.viewPager.adapter = pagerAdapter
            inPlanMode = false
        }
    }

    private fun showShopping() {
        binding.startPage.root.visibility = View.GONE
        binding.viewPager.visibility = View.VISIBLE
        val date = viewModel.planDate.value
        val title = if (date != null) {
            val fmt = DateTimeFormatter.ofPattern("d MMM")
            getString(R.string.shop_with_date, date.format(fmt))
        } else {
            getString(R.string.shop)
        }
        supportActionBar?.title = title
    }

    private fun showPlanning() {
        val planned = viewModel.categories.value?.map { cat ->
            cat.copy(items = cat.items.map { it.copy(isStruck = true) })
        } ?: return
        viewModel.setCategories(planned)
        val today = LocalDate.now()
        viewModel.setPlanDate(today)
        pagerAdapter = CategoryPagerAdapter(this, viewModel, true)
        binding.viewPager.adapter = pagerAdapter
        binding.startPage.root.visibility = View.GONE
        binding.viewPager.visibility = View.VISIBLE
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        supportActionBar?.title = getString(R.string.plan_with_date, today.format(fmt))
        inPlanMode = true
    }
}

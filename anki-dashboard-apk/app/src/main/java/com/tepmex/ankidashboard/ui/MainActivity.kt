package com.tepmex.ankidashboard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.tepmex.ankidashboard.R
import com.tepmex.ankidashboard.data.AnkiContract
import com.tepmex.ankidashboard.data.DashboardData
import com.tepmex.ankidashboard.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DashboardViewModel by viewModels()
    private val deckAdapter = DeckChipsAdapter { viewModel.setSelectedDecks(it) }
    private val leechesAdapter = LeechesAdapter()
    private var leechFieldByDeck: Map<String, String> = emptyMap()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.reload()
    }

    private val openCollectionLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, flags)
        viewModel.setCollectionUri(uri.toString())
    }

    private val syncSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.reloadAfterAnkiWebSync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.deckRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.deckRecycler.adapter = deckAdapter
        binding.leechesRecycler.layoutManager = LinearLayoutManager(this)
        binding.leechesRecycler.adapter = leechesAdapter

        binding.retryButton.setOnClickListener { tryLoadWithPermission() }
        binding.grantPermissionButton.setOnClickListener {
            permissionLauncher.launch(AnkiContract.READ_WRITE_PERMISSION)
        }
        binding.pickCollectionButton.setOnClickListener {
            openCollectionLauncher.launch(arrayOf("*/*"))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as com.tepmex.ankidashboard.AnkiDashboardApp)
                    .preferences.leechFieldByDeck.collect { saved ->
                        if (saved.isNotEmpty()) {
                            leechFieldByDeck = saved
                            state@ run {
                                viewModel.uiState.value.data?.let { data ->
                                    leechesAdapter.submit(data.leeches, leechFieldByDeck)
                                }
                            }
                        }
                    }
            }
        }

        tryLoadWithPermission()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_RELOAD, 0, R.string.action_reload)
        menu.add(0, MENU_ANKIWEB, 1, R.string.action_ankiweb_sync)
        menu.add(0, MENU_COLLECTION, 2, R.string.action_pick_collection)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_RELOAD -> {
                viewModel.reload()
                return true
            }
            MENU_ANKIWEB -> {
                syncSettingsLauncher.launch(
                    android.content.Intent(this, SyncSettingsActivity::class.java),
                )
                return true
            }
            MENU_COLLECTION -> {
                openCollectionLauncher.launch(arrayOf("*/*"))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun tryLoadWithPermission() {
        val repo = viewModel.ankiRepository
        when {
            !repo.hasAnkiInstalled() -> viewModel.reload()
            repo.hasAnkiPermission() -> viewModel.reload()
            else -> permissionLauncher.launch(AnkiContract.READ_WRITE_PERMISSION)
        }
    }

    private fun render(state: DashboardUiState) {
        binding.loadingOverlay.isVisible = state.loading
        binding.errorPanel.isVisible = state.errorCode != null
        binding.mainScroll.isVisible = state.errorCode == null && !state.loading

        when (state.errorCode) {
            "anki_missing" -> {
                binding.errorTitle.text = getString(R.string.error_anki_missing_title)
                binding.errorMessage.text = state.errorMessage
                binding.grantPermissionButton.isVisible = false
            }
            "anki_permission" -> {
                binding.errorTitle.text = getString(R.string.error_permission_title)
                binding.errorMessage.text = state.errorMessage
                binding.grantPermissionButton.isVisible = true
            }
            else -> {
                binding.errorTitle.text = getString(R.string.error_generic_title)
                binding.errorMessage.text = state.errorMessage ?: ""
                binding.grantPermissionButton.isVisible = false
            }
        }

        val data = state.data ?: return
        val deckNames = data.deckNamesAndIds.keys.toList()
        deckAdapter.submitDecks(deckNames, state.selectedDecks)

        binding.statusBanner.isVisible = data.statusMessage != null
        binding.statusBanner.text = data.statusMessage

        val hasDecks = state.selectedDecks.isNotEmpty()
        binding.statsSection.isVisible = hasDecks
        binding.chartsSection.isVisible = hasDecks
        binding.emptyDecksHint.isVisible = !hasDecks

        if (!hasDecks) return

        val memorized = data.intervals.count { it >= 7 }
        binding.reviewScoreValue.text = String.format("%.2f", data.reviewScore)
        binding.wordsValue.text = getString(R.string.words_fraction, memorized, data.totalCards)
        binding.hoursValue.text = String.format("%.1f", data.totalHoursSpent)
        binding.longMemoryValue.text = data.longMemory.toString()
        val pct = if (data.totalCards > 0) memorized * 100f / data.totalCards else 0f
        binding.progressPercent.text = String.format("%.1f%%", pct)
        binding.progressTrack.post {
            val w = (binding.progressTrack.width * pct / 100f).toInt().coerceAtLeast(0)
            binding.progressFill.layoutParams.width = w
            binding.progressFill.requestLayout()
        }

        if (data.historyAvailable && data.plotData.isNotEmpty()) {
            bindVocabChart(data)
            binding.reviewHeatmap.setData(data.reviewsStats, CalendarHeatmapView.ColorScheme.ANKI)
            binding.mistakesHeatmap.setData(data.mistakesData, CalendarHeatmapView.ColorScheme.MISTAKE)
            bindMonthlyChart(data.newVocabPerMonthData)
            binding.chartPlaceholder.isVisible = false
            binding.vocabChart.isVisible = true
            binding.reviewHeatmap.isVisible = true
            binding.mistakesHeatmap.isVisible = true
            binding.monthlyChart.isVisible = true
        } else {
            binding.chartPlaceholder.isVisible = true
            binding.vocabChart.isVisible = false
            binding.reviewHeatmap.isVisible = false
            binding.mistakesHeatmap.isVisible = false
            binding.monthlyChart.isVisible = false
        }

        leechFieldByDeck = buildLeechFieldMap(state.selectedDecks, data.deckFieldOptions)
        leechesAdapter.submit(data.leeches, leechFieldByDeck)
        binding.leechesSettingsButton.setOnClickListener { showLeechFieldDialog(state.selectedDecks, data) }
    }

    private fun buildLeechFieldMap(
        selectedDecks: Set<String>,
        options: Map<String, List<String>>,
    ): Map<String, String> {
        val out = linkedMapOf<String, String>()
        selectedDecks.forEach { deck ->
            val fields = options[deck].orEmpty()
            out[deck] = fields.firstOrNull().orEmpty()
        }
        return out
    }

    private fun showLeechFieldDialog(selectedDecks: Set<String>, data: DashboardData) {
        val decks = selectedDecks.toList()
        if (decks.isEmpty()) return
        val labels = decks.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.leech_field_title)
            .setItems(labels) { _, which ->
                val deck = decks[which]
                val fieldOptions = data.deckFieldOptions[deck].orEmpty()
                if (fieldOptions.isEmpty()) return@setItems
                AlertDialog.Builder(this)
                    .setTitle(deck)
                    .setItems(fieldOptions.toTypedArray()) { _, fieldIdx ->
                        val field = fieldOptions[fieldIdx]
                        leechFieldByDeck = leechFieldByDeck.toMutableMap().apply { put(deck, field) }
                        lifecycleScope.launch {
                            (application as com.tepmex.ankidashboard.AnkiDashboardApp)
                                .preferences.setLeechField(deck, field)
                        }
                        leechesAdapter.submit(data.leeches, leechFieldByDeck)
                    }
                    .show()
            }
            .show()
    }

    private fun bindVocabChart(data: DashboardData) {
        val labels = data.plotData.map { it.first }
        val labelIdx = labels.mapIndexed { idx, _ -> idx.toFloat() }

        val learnedEntries = data.plotData.mapIndexed { idx, pair ->
            Entry(idx.toFloat(), pair.second.toFloat())
        }
        val learnedSet = LineDataSet(learnedEntries, getString(R.string.chart_words_learned)).apply {
            color = getColor(R.color.chart_words)
            setCircleColor(getColor(R.color.chart_words))
            lineWidth = 2.5f
            setDrawCircles(false)
            setDrawValues(false)
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
        }

        val mistakeEntries = data.mistakesData.mapIndexed { idx, pair ->
            BarEntry(idx.toFloat(), pair.second.toFloat())
        }
        val mistakeSet = BarDataSet(mistakeEntries, getString(R.string.chart_mistakes)).apply {
            color = getColor(R.color.chart_mistakes)
            setDrawValues(false)
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
        }

        val reviewEntries = data.reviewsData.mapIndexed { idx, pair ->
            BarEntry(idx.toFloat(), pair.second.toFloat())
        }
        val reviewSet = BarDataSet(reviewEntries, getString(R.string.chart_reviews)).apply {
            color = getColor(R.color.chart_reviews)
            setDrawValues(false)
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
        }

        val combined = CombinedData()
        combined.setData(LineData(learnedSet))
        combined.setData(BarData(mistakeSet, reviewSet))

        binding.vocabChart.data = combined
        binding.vocabChart.drawOrder = arrayOf(
            com.github.mikephil.charting.charts.CombinedChart.DrawOrder.BAR,
            com.github.mikephil.charting.charts.CombinedChart.DrawOrder.LINE,
        )
        binding.vocabChart.xAxis.valueFormatter = IndexAxisValueFormatter(sparseLabels(labels))
        binding.vocabChart.xAxis.labelRotationAngle = -45f
        binding.vocabChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.vocabChart.axisRight.isEnabled = true
        binding.vocabChart.axisLeft.isEnabled = true
        binding.vocabChart.description.isEnabled = false
        binding.vocabChart.legend.isEnabled = true
        binding.vocabChart.invalidate()
    }

    private fun bindMonthlyChart(monthly: List<Pair<String, Int>>) {
        val entries = monthly.mapIndexed { idx, pair -> BarEntry(idx.toFloat(), pair.second.toFloat()) }
        val set = BarDataSet(entries, getString(R.string.chart_new_vocab)).apply {
            color = getColor(R.color.chart_words)
            setDrawValues(false)
        }
        binding.monthlyChart.data = BarData(set)
        binding.monthlyChart.xAxis.valueFormatter = IndexAxisValueFormatter(
            monthly.map { formatMonthLabel(it.first) },
        )
        binding.monthlyChart.xAxis.labelRotationAngle = -45f
        binding.monthlyChart.description.isEnabled = false
        binding.monthlyChart.invalidate()
    }

    private fun sparseLabels(labels: List<String>): Array<String> {
        if (labels.size <= 12) return labels.toTypedArray()
        val step = labels.size / 10
        return labels.mapIndexed { idx, value ->
            if (idx % step == 0) value.takeLast(5) else ""
        }.toTypedArray()
    }

    private fun formatMonthLabel(key: String): String = try {
        LocalDate.parse("$key-01", DateTimeFormatter.ISO_LOCAL_DATE)
            .format(DateTimeFormatter.ofPattern("MMM yy"))
    } catch (_: DateTimeParseException) {
        key
    }

    companion object {
        private const val MENU_RELOAD = 1001
        private const val MENU_ANKIWEB = 1002
        private const val MENU_COLLECTION = 1003
    }
}

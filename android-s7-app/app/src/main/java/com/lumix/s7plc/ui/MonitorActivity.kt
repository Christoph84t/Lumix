package com.lumix.s7plc.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lumix.s7plc.R
import com.lumix.s7plc.databinding.ActivityMonitorBinding
import com.lumix.s7plc.databinding.DialogAddTagBinding
import com.lumix.s7plc.model.PlcDataType
import com.lumix.s7plc.model.PlcTag
import com.lumix.s7plc.protocol.S7Area
import com.lumix.s7plc.ui.adapter.TagAdapter
import com.lumix.s7plc.viewmodel.ConnectionViewModel
import com.lumix.s7plc.viewmodel.ConnectionViewModelFactory
import com.lumix.s7plc.viewmodel.MonitorViewModel
import com.lumix.s7plc.viewmodel.MonitorViewModelFactory
import java.util.UUID

class MonitorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitorBinding
    private val connectionVm: ConnectionViewModel by viewModels { ConnectionViewModelFactory() }
    private lateinit var monitorVm: MonitorViewModel
    private lateinit var adapter: TagAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        monitorVm = ViewModelProvider(
            this, MonitorViewModelFactory(connectionVm)
        )[MonitorViewModel::class.java]

        setupRecyclerView()
        setupFab()
        setupPollingControls()
        observeViewModel()

        // Demo-Tags hinzufügen (können entfernt werden)
        addDemoTags()
    }

    private fun setupRecyclerView() {
        adapter = TagAdapter(
            onRemove = { tag -> monitorVm.removeTag(tag.id) },
            onWrite  = { tag -> showWriteDialog(tag) }
        )
        binding.rvTags.layoutManager = LinearLayoutManager(this)
        binding.rvTags.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddTag.setOnClickListener { showAddTagDialog() }
    }

    private fun setupPollingControls() {
        binding.togglePolling.setOnCheckedChangeListener { _, checked ->
            if (checked) monitorVm.startPolling() else monitorVm.stopPolling()
        }
        binding.btnReadOnce.setOnClickListener { monitorVm.readOnce() }

        binding.sliderInterval.addOnChangeListener { _, value, _ ->
            monitorVm.pollingIntervalMs = value.toLong()
            binding.tvInterval.text = "${value.toInt()} ms"
        }
        binding.sliderInterval.value = 1000f
    }

    private fun observeViewModel() {
        monitorVm.tags.observe(this) { states ->
            adapter.submitList(states)
            binding.tvEmpty.visibility = if (states.isEmpty()) View.VISIBLE else View.GONE
        }
        monitorVm.statusMessage.observe(this) { msg ->
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun addDemoTags() {
        // Beispiel-Tags – zeigen, wie die App genutzt wird
        monitorVm.addTag(PlcTag(
            id = UUID.randomUUID().toString(),
            name = "DB1.DBW0 (Word)",
            area = S7Area.DB, dbNumber = 1,
            byteOffset = 0, dataType = PlcDataType.WORD,
            description = "Beispiel-Wort"
        ))
        monitorVm.addTag(PlcTag(
            id = UUID.randomUUID().toString(),
            name = "DB1.DBD4 (Real)",
            area = S7Area.DB, dbNumber = 1,
            byteOffset = 4, dataType = PlcDataType.REAL,
            unit = "°C", description = "Temperatur"
        ))
        monitorVm.addTag(PlcTag(
            id = UUID.randomUUID().toString(),
            name = "M0.0 (Bit)",
            area = S7Area.MERKER, dbNumber = 0,
            byteOffset = 0, bitOffset = 0,
            dataType = PlcDataType.BOOL,
            description = "Merkerbit M0.0"
        ))
    }

    private fun showAddTagDialog() {
        val dialogBinding = DialogAddTagBinding.inflate(layoutInflater)

        // Bereich-Spinner befüllen
        val areas = S7Area.entries.map { it.label }
        dialogBinding.spinnerArea.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_item, areas
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Datentyp-Spinner
        val types = PlcDataType.entries.map { it.label }
        dialogBinding.spinnerDataType.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_item, types
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        AlertDialog.Builder(this)
            .setTitle("Tag hinzufügen")
            .setView(dialogBinding.root)
            .setPositiveButton("Hinzufügen") { _, _ ->
                val selectedArea = S7Area.entries[dialogBinding.spinnerArea.selectedItemPosition]
                val selectedType = PlcDataType.entries[dialogBinding.spinnerDataType.selectedItemPosition]
                val tag = PlcTag(
                    id          = UUID.randomUUID().toString(),
                    name        = dialogBinding.etTagName.text.toString().ifBlank { "Tag" },
                    area        = selectedArea,
                    dbNumber    = dialogBinding.etDbNumber.text.toString().toIntOrNull() ?: 0,
                    byteOffset  = dialogBinding.etByteOffset.text.toString().toIntOrNull() ?: 0,
                    bitOffset   = dialogBinding.etBitOffset.text.toString().toIntOrNull() ?: 0,
                    dataType    = selectedType,
                    unit        = dialogBinding.etUnit.text.toString(),
                    description = dialogBinding.etDescription.text.toString()
                )
                monitorVm.addTag(tag)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showWriteDialog(tag: PlcTag) {
        val input = android.widget.EditText(this)
        input.hint = "Wert eingeben"

        AlertDialog.Builder(this)
            .setTitle("${tag.name} schreiben")
            .setView(input)
            .setPositiveButton("Schreiben") { _, _ ->
                val text = input.text.toString()
                val bytes = valueToBytes(tag, text)
                if (bytes != null) {
                    monitorVm.writeTagValue(tag, bytes)
                } else {
                    Snackbar.make(binding.root, "Ungültiger Wert", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun valueToBytes(tag: PlcTag, text: String): ByteArray? = try {
        when (tag.dataType) {
            PlcDataType.BOOL  -> byteArrayOf(if (text.trim().lowercase() in listOf("1","true","ein","on")) 1 else 0)
            PlcDataType.BYTE  -> byteArrayOf(text.trim().toInt(16).toByte())
            PlcDataType.WORD,
            PlcDataType.INT   -> {
                val v = text.trim().toInt()
                byteArrayOf((v shr 8).toByte(), (v and 0xFF).toByte())
            }
            PlcDataType.DWORD,
            PlcDataType.DINT  -> {
                val v = text.trim().toLong()
                byteArrayOf(
                    ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
                    ((v shr 8)  and 0xFF).toByte(),  (v          and 0xFF).toByte()
                )
            }
            PlcDataType.REAL  -> {
                val v = text.trim().toFloat()
                val bits = java.lang.Float.floatToIntBits(v)
                byteArrayOf(
                    (bits shr 24).toByte(), (bits shr 16).toByte(),
                    (bits shr 8).toByte(),   bits.toByte()
                )
            }
        }
    } catch (_: Exception) { null }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_monitor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_clear_tags -> { monitorVm.clearTags(); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}

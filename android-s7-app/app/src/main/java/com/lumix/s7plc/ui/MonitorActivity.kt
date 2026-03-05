package com.lumix.s7plc.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.lumix.s7plc.R
import com.lumix.s7plc.S7PlcApp
import com.lumix.s7plc.model.PlcDataType
import com.lumix.s7plc.model.PlcTag
import com.lumix.s7plc.protocol.S7Area
import com.lumix.s7plc.ui.adapter.TagAdapter
import com.lumix.s7plc.viewmodel.ConnectionViewModel
import com.lumix.s7plc.viewmodel.MonitorViewModel
import com.lumix.s7plc.viewmodel.TagState
import java.util.UUID

@Suppress("DEPRECATION")
class MonitorActivity : Activity() {

    private lateinit var connectionVm: ConnectionViewModel
    private lateinit var monitorVm: MonitorViewModel
    private lateinit var adapter: TagAdapter

    private lateinit var switchPolling: Switch
    private lateinit var btnReadOnce: Button
    private lateinit var btnAddTag: Button
    private lateinit var seekBarInterval: SeekBar
    private lateinit var tvInterval: TextView
    private lateinit var lvTags: ListView
    private lateinit var tvEmpty: TextView

    private val tagsObserver: (List<TagState>) -> Unit = { states ->
        adapter.setData(states)
        tvEmpty.visibility = if (states.isEmpty()) View.VISIBLE else View.GONE
    }
    private val statusObserver: (String) -> Unit = { msg ->
        if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)

        actionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_monitor)
        }

        connectionVm = (application as S7PlcApp).connectionViewModel
        monitorVm = MonitorViewModel(connectionVm.repository)

        switchPolling   = findViewById(R.id.switchPolling) as Switch
        btnReadOnce     = findViewById(R.id.btnReadOnce) as Button
        btnAddTag       = findViewById(R.id.btnAddTag) as Button
        seekBarInterval = findViewById(R.id.seekBarInterval) as SeekBar
        tvInterval      = findViewById(R.id.tvInterval) as TextView
        lvTags          = findViewById(R.id.lvTags) as ListView
        tvEmpty         = findViewById(R.id.tvEmpty) as TextView

        adapter = TagAdapter(this,
            onRemove = { tag -> monitorVm.removeTag(tag.id) },
            onWrite  = { tag -> showWriteDialog(tag) }
        )
        lvTags.adapter = adapter

        setupControls()
        addDemoTags()

        monitorVm.tags.observe(tagsObserver)
        monitorVm.statusMessage.observe(statusObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorVm.tags.removeObserver(tagsObserver)
        monitorVm.statusMessage.removeObserver(statusObserver)
        monitorVm.onDestroy()
    }

    private fun setupControls() {
        switchPolling.setOnCheckedChangeListener { _, checked ->
            if (checked) monitorVm.startPolling() else monitorVm.stopPolling()
        }
        btnReadOnce.setOnClickListener { monitorVm.readOnce() }
        btnAddTag.setOnClickListener { showAddTagDialog() }

        seekBarInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val ms = 200L + progress * 100L
                monitorVm.pollingIntervalMs = ms
                tvInterval.text = "$ms ms"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        seekBarInterval.progress = 8 // default ~1000ms
    }

    private fun addDemoTags() {
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_tag, null)

        val etTagName     = dialogView.findViewById(R.id.etTagName) as android.widget.EditText
        val spinnerArea   = dialogView.findViewById(R.id.spinnerArea) as Spinner
        val etDbNumber    = dialogView.findViewById(R.id.etDbNumber) as android.widget.EditText
        val etByteOffset  = dialogView.findViewById(R.id.etByteOffset) as android.widget.EditText
        val etBitOffset   = dialogView.findViewById(R.id.etBitOffset) as android.widget.EditText
        val spinnerType   = dialogView.findViewById(R.id.spinnerDataType) as Spinner
        val etUnit        = dialogView.findViewById(R.id.etUnit) as android.widget.EditText
        val etDescription = dialogView.findViewById(R.id.etDescription) as android.widget.EditText

        spinnerArea.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            S7Area.values().map { it.label })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            PlcDataType.values().map { it.label })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        AlertDialog.Builder(this)
            .setTitle("Tag hinzufügen")
            .setView(dialogView)
            .setPositiveButton("Hinzufügen") { _, _ ->
                val selectedArea = S7Area.values()[spinnerArea.selectedItemPosition]
                val selectedType = PlcDataType.values()[spinnerType.selectedItemPosition]
                monitorVm.addTag(PlcTag(
                    id          = UUID.randomUUID().toString(),
                    name        = etTagName.text.toString().ifBlank { "Tag" },
                    area        = selectedArea,
                    dbNumber    = etDbNumber.text.toString().toIntOrNull() ?: 0,
                    byteOffset  = etByteOffset.text.toString().toIntOrNull() ?: 0,
                    bitOffset   = etBitOffset.text.toString().toIntOrNull() ?: 0,
                    dataType    = selectedType,
                    unit        = etUnit.text.toString(),
                    description = etDescription.text.toString()
                ))
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
                    Toast.makeText(this, "Ungültiger Wert", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun valueToBytes(tag: PlcTag, text: String): ByteArray? = try {
        when (tag.dataType) {
            PlcDataType.BOOL  -> byteArrayOf(if (text.trim().toLowerCase() in listOf("1","true","ein","on")) 1 else 0)
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
        menu.add(0, 1, 0, "Tags löschen")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        1 -> { monitorVm.clearTags(); true }
        else -> super.onOptionsItemSelected(item)
    }
}

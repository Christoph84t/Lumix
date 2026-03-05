package com.lumix.s7plc.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.lumix.s7plc.R
import com.lumix.s7plc.S7PlcApp
import com.lumix.s7plc.model.ByteRow
import com.lumix.s7plc.protocol.S7Area
import com.lumix.s7plc.ui.adapter.ByteRowAdapter
import com.lumix.s7plc.viewmodel.ConnectionViewModel
import com.lumix.s7plc.viewmodel.DataBlockViewModel
import com.lumix.s7plc.viewmodel.DbReadState

class DataBlockActivity : Activity() {

    private lateinit var connectionVm: ConnectionViewModel
    private lateinit var dbVm: DataBlockViewModel
    private lateinit var adapter: ByteRowAdapter

    private lateinit var radioGroupArea: RadioGroup
    private lateinit var layoutDbNumber: View
    private lateinit var etDbNumber: EditText
    private lateinit var etStartByte: EditText
    private lateinit var etSizeBytes: EditText
    private lateinit var btnRead: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSummary: TextView
    private lateinit var tvError: TextView
    private lateinit var lvBytes: ListView
    private lateinit var tvEmpty: TextView

    private val stateObserver: (DbReadState) -> Unit = { state -> onStateChanged(state) }
    private val writeObserver: (String) -> Unit = { msg ->
        if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_block)

        actionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_datablock)
        }

        connectionVm = (application as S7PlcApp).connectionViewModel
        dbVm = DataBlockViewModel(connectionVm.repository)

        radioGroupArea = findViewById(R.id.radioGroupArea) as RadioGroup
        layoutDbNumber = findViewById(R.id.layoutDbNumber)
        etDbNumber     = findViewById(R.id.etDbNumber) as EditText
        etStartByte    = findViewById(R.id.etStartByte) as EditText
        etSizeBytes    = findViewById(R.id.etSizeBytes) as EditText
        btnRead        = findViewById(R.id.btnRead) as Button
        progressBar    = findViewById(R.id.progressBar) as ProgressBar
        tvSummary      = findViewById(R.id.tvSummary) as TextView
        tvError        = findViewById(R.id.tvError) as TextView
        lvBytes        = findViewById(R.id.lvBytes) as ListView
        tvEmpty        = findViewById(R.id.tvEmpty) as TextView

        adapter = ByteRowAdapter(this) { row -> showWriteDialog(row) }
        lvBytes.adapter = adapter

        etDbNumber.setText("1")
        etStartByte.setText("0")
        etSizeBytes.setText("64")

        setupReadButton()
        dbVm.state.observe(stateObserver)
        dbVm.writeStatus.observe(writeObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        dbVm.state.removeObserver(stateObserver)
        dbVm.writeStatus.removeObserver(writeObserver)
        dbVm.onDestroy()
    }

    private fun setupReadButton() {
        btnRead.setOnClickListener {
            val dbNum = etDbNumber.text.toString().toIntOrNull()
            val start = etStartByte.text.toString().toIntOrNull()
            val size  = etSizeBytes.text.toString().toIntOrNull()

            if (dbNum == null || start == null || size == null || size <= 0) {
                Toast.makeText(this, "Ungültige Parameter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (size > 1024) {
                Toast.makeText(this, "Maximal 1024 Bytes pro Abfrage", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when (radioGroupArea.checkedRadioButtonId) {
                R.id.radioDb      -> dbVm.readDB(dbNum, start, size)
                R.id.radioMerker  -> dbVm.readArea(S7Area.MERKER, start, size)
                R.id.radioInputs  -> dbVm.readArea(S7Area.INPUTS, start, size)
                R.id.radioOutputs -> dbVm.readArea(S7Area.OUTPUTS, start, size)
                else              -> dbVm.readDB(dbNum, start, size)
            }
        }

        radioGroupArea.setOnCheckedChangeListener { _, checkedId ->
            layoutDbNumber.visibility =
                if (checkedId == R.id.radioDb) View.VISIBLE else View.GONE
        }
    }

    private fun onStateChanged(state: DbReadState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        tvError.visibility = if (state.errorMessage != null) View.VISIBLE else View.GONE
        tvError.text = state.errorMessage ?: ""

        adapter.setData(state.rows)
        tvEmpty.visibility =
            if (!state.isLoading && state.rows.isEmpty() && state.errorMessage == null)
                View.VISIBLE else View.GONE

        if (state.rows.isNotEmpty()) {
            tvSummary.text = "${state.rows.size} Bytes gelesen ab Adresse ${state.startByte}"
            tvSummary.visibility = View.VISIBLE
        } else {
            tvSummary.visibility = View.GONE
        }
    }

    private fun showWriteDialog(row: ByteRow) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_write_byte, null)
        val tvCurrentValue = dialogView.findViewById(R.id.tvCurrentValue) as TextView
        val etNewValue     = dialogView.findViewById(R.id.etNewValue) as EditText

        tvCurrentValue.text = "Adresse: ${row.address}\nAktueller Wert: 0x${row.hex} (${row.decimal})"
        etNewValue.setText(row.hex)

        AlertDialog.Builder(this)
            .setTitle("Byte schreiben")
            .setView(dialogView)
            .setPositiveButton("Schreiben") { _, _ ->
                val text = etNewValue.text.toString().trim()
                val value = try {
                    when {
                        text.startsWith("0x") || text.startsWith("0X") -> text.substring(2).toInt(16)
                        text.all { c -> c.isDigit() } -> text.toInt()
                        else -> text.toInt(16)
                    }
                } catch (_: Exception) { -1 }

                if (value !in 0..255) {
                    Toast.makeText(this, "Ungültiger Wert (0–255)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                dbVm.writeSingleByte(dbVm.state.value.dbNumber, row.address, value)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}

package com.lumix.s7plc.ui

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lumix.s7plc.databinding.ActivityDataBlockBinding
import com.lumix.s7plc.databinding.DialogWriteByteBinding
import com.lumix.s7plc.model.ByteRow
import com.lumix.s7plc.protocol.S7Area
import com.lumix.s7plc.ui.adapter.ByteRowAdapter
import com.lumix.s7plc.viewmodel.ConnectionViewModel
import com.lumix.s7plc.viewmodel.ConnectionViewModelFactory
import com.lumix.s7plc.viewmodel.DataBlockViewModel
import com.lumix.s7plc.viewmodel.DataBlockViewModelFactory

class DataBlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataBlockBinding
    private val connectionVm: ConnectionViewModel by viewModels { ConnectionViewModelFactory() }
    private lateinit var dbVm: DataBlockViewModel
    private lateinit var adapter: ByteRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        dbVm = ViewModelProvider(
            this, DataBlockViewModelFactory(connectionVm)
        )[DataBlockViewModel::class.java]

        setupRecyclerView()
        setupReadButton()
        observeViewModel()

        // Standard-Werte
        binding.etDbNumber.setText("1")
        binding.etStartByte.setText("0")
        binding.etSizeBytes.setText("64")
    }

    private fun setupRecyclerView() {
        adapter = ByteRowAdapter { row -> showWriteDialog(row) }
        binding.rvBytes.layoutManager = LinearLayoutManager(this)
        binding.rvBytes.adapter = adapter
    }

    private fun setupReadButton() {
        binding.btnRead.setOnClickListener {
            val dbNum = binding.etDbNumber.text.toString().toIntOrNull()
            val start = binding.etStartByte.text.toString().toIntOrNull()
            val size  = binding.etSizeBytes.text.toString().toIntOrNull()

            if (dbNum == null || start == null || size == null || size <= 0) {
                Snackbar.make(binding.root, "Ungültige Parameter", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (size > 1024) {
                Snackbar.make(binding.root, "Maximal 1024 Bytes pro Abfrage", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when (binding.radioGroupArea.checkedRadioButtonId) {
                binding.radioDb.id      -> dbVm.readDB(dbNum, start, size)
                binding.radioMerker.id  -> dbVm.readArea(S7Area.MERKER, start, size)
                binding.radioInputs.id  -> dbVm.readArea(S7Area.INPUTS, start, size)
                binding.radioOutputs.id -> dbVm.readArea(S7Area.OUTPUTS, start, size)
                else                    -> dbVm.readDB(dbNum, start, size)
            }
        }

        // DB-Nummer nur anzeigen wenn DB ausgewählt
        binding.radioGroupArea.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutDbNumber.visibility =
                if (checkedId == binding.radioDb.id) View.VISIBLE else View.GONE
        }
    }

    private fun observeViewModel() {
        dbVm.state.observe(this) { state ->
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.tvError.visibility = if (state.errorMessage != null) View.VISIBLE else View.GONE
            binding.tvError.text = state.errorMessage

            adapter.submitList(state.rows)
            binding.tvEmpty.visibility =
                if (!state.isLoading && state.rows.isEmpty() && state.errorMessage == null)
                    View.VISIBLE else View.GONE

            if (state.rows.isNotEmpty()) {
                binding.tvSummary.text =
                    "${state.rows.size} Bytes gelesen ab Adresse ${state.startByte}"
                binding.tvSummary.visibility = View.VISIBLE
            } else {
                binding.tvSummary.visibility = View.GONE
            }
        }

        dbVm.writeStatus.observe(this) { msg ->
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showWriteDialog(row: ByteRow) {
        val currentState = dbVm.state.value ?: return
        val dialogBinding = DialogWriteByteBinding.inflate(layoutInflater)
        dialogBinding.tvCurrentValue.text =
            "Adresse: ${row.address}\nAktueller Wert: 0x${row.hex} (${row.decimal})"
        dialogBinding.etNewValue.setText(row.hex)
        dialogBinding.etNewValue.hint = "Hex (z.B. FF) oder Dezimal"

        AlertDialog.Builder(this)
            .setTitle("Byte schreiben")
            .setView(dialogBinding.root)
            .setPositiveButton("Schreiben") { _, _ ->
                val text = dialogBinding.etNewValue.text.toString().trim()
                val value = try {
                    if (text.startsWith("0x") || text.startsWith("0X"))
                        text.substring(2).toInt(16)
                    else if (text.all { it.isDigit() })
                        text.toInt()
                    else
                        text.toInt(16)
                } catch (_: Exception) { -1 }

                if (value !in 0..255) {
                    Snackbar.make(binding.root, "Ungültiger Wert (0–255)", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                dbVm.writeSingleByte(currentState.dbNumber, row.address, value)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

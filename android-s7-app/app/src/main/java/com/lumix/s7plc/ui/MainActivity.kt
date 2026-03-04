package com.lumix.s7plc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.lumix.s7plc.R
import com.lumix.s7plc.databinding.ActivityMainBinding
import com.lumix.s7plc.viewmodel.ConnectionState
import com.lumix.s7plc.viewmodel.ConnectionViewModel
import com.lumix.s7plc.viewmodel.ConnectionViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ConnectionViewModel by viewModels { ConnectionViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupSlotPresets()
        setupButtons()
        observeViewModel()

        // Standard-Werte vorausfüllen
        binding.etHost.setText(viewModel.lastHost)
        binding.etRack.setText(viewModel.lastRack.toString())
        binding.etSlot.setText(viewModel.lastSlot.toString())
    }

    private fun setupSlotPresets() {
        val presets = arrayOf("S7-300/400 (Slot 2)", "S7-1200/1500 (Slot 1)", "LOGO! 0BA8 (Slot 1)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presets)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPreset.adapter = adapter
        binding.spinnerPreset.setSelection(1) // S7-1200 als Standard

        binding.btnApplyPreset.setOnClickListener {
            when (binding.spinnerPreset.selectedItemPosition) {
                0 -> { binding.etRack.setText("0"); binding.etSlot.setText("2") }
                1 -> { binding.etRack.setText("0"); binding.etSlot.setText("1") }
                2 -> { binding.etRack.setText("0"); binding.etSlot.setText("1") }
            }
        }
    }

    private fun setupButtons() {
        binding.btnConnect.setOnClickListener {
            val host  = binding.etHost.text.toString().trim()
            val rack  = binding.etRack.text.toString().toIntOrNull() ?: 0
            val slot  = binding.etSlot.text.toString().toIntOrNull() ?: 1
            val alias = binding.etAlias.text.toString().trim()

            if (host.isEmpty()) {
                binding.etHost.error = "IP-Adresse eingeben"
                return@setOnClickListener
            }
            viewModel.connect(host, rack, slot, alias)
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        binding.btnOpenMonitor.setOnClickListener {
            startActivity(Intent(this, MonitorActivity::class.java))
        }

        binding.btnOpenDbBrowser.setOnClickListener {
            startActivity(Intent(this, DataBlockActivity::class.java))
        }

        binding.btnHelp.setOnClickListener { showHelpDialog() }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is ConnectionState.Disconnected -> {
                    setConnectedUi(false)
                    binding.tvStatus.text = "Nicht verbunden"
                    binding.tvStatus.setTextColor(getColor(R.color.status_disconnected))
                }
                is ConnectionState.Connecting -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnConnect.isEnabled = false
                    binding.tvStatus.text = "Verbinde..."
                    binding.tvStatus.setTextColor(getColor(R.color.status_connecting))
                }
                is ConnectionState.Connected -> {
                    binding.progressBar.visibility = View.GONE
                    setConnectedUi(true)
                    binding.tvStatus.text = "Verbunden  |  PDU: ${state.pduSize} Bytes"
                    binding.tvStatus.setTextColor(getColor(R.color.status_connected))
                    Snackbar.make(binding.root, "Verbindung hergestellt", Snackbar.LENGTH_SHORT).show()
                }
                is ConnectionState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnConnect.isEnabled = true
                    binding.tvStatus.text = "Fehler: ${state.message}"
                    binding.tvStatus.setTextColor(getColor(R.color.status_error))
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setConnectedUi(connected: Boolean) {
        binding.progressBar.visibility = View.GONE
        binding.btnConnect.isEnabled = !connected
        binding.btnDisconnect.isEnabled = connected
        binding.btnOpenMonitor.isEnabled = connected
        binding.btnOpenDbBrowser.isEnabled = connected
        binding.cardConnectionParams.alpha = if (connected) 0.7f else 1.0f
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("S7 PLC Verbindung")
            .setMessage("""
                |Verbindungsparameter:
                |
                |• IP-Adresse: Netzwerkadresse der SPS
                |• Rack: Baugruppenträger (meist 0)
                |• Slot: Steckplatz der CPU
                |  - S7-300: Slot 2
                |  - S7-400: variiert (1–4)
                |  - S7-1200/1500: Slot 1
                |  - LOGO! 0BA8+: Slot 1
                |
                |Port 102 (ISO-TCP) muss erreichbar sein.
                |Bei S7-1200/1500 PUT/GET in den Verbindungseigenschaften aktivieren.
            """.trimMargin())
            .setPositiveButton("OK", null)
            .show()
    }
}

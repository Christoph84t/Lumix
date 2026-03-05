package com.lumix.s7plc.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.lumix.s7plc.R
import com.lumix.s7plc.S7PlcApp
import com.lumix.s7plc.viewmodel.ConnectionState
import com.lumix.s7plc.viewmodel.ConnectionViewModel

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private lateinit var viewModel: ConnectionViewModel

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerPreset: Spinner
    private lateinit var btnApplyPreset: Button
    private lateinit var etHost: EditText
    private lateinit var etRack: EditText
    private lateinit var etSlot: EditText
    private lateinit var etAlias: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnOpenMonitor: Button
    private lateinit var btnOpenDbBrowser: Button
    private lateinit var btnHelp: Button
    private lateinit var cardConnectionParams: View

    private val stateObserver: (ConnectionState) -> Unit = { state -> onStateChanged(state) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = (application as S7PlcApp).connectionViewModel

        @Suppress("UNCHECKED_CAST")
        tvStatus           = findViewById(R.id.tvStatus) as TextView
        progressBar        = findViewById(R.id.progressBar) as ProgressBar
        spinnerPreset      = findViewById(R.id.spinnerPreset) as Spinner
        btnApplyPreset     = findViewById(R.id.btnApplyPreset) as Button
        etHost             = findViewById(R.id.etHost) as EditText
        etRack             = findViewById(R.id.etRack) as EditText
        etSlot             = findViewById(R.id.etSlot) as EditText
        etAlias            = findViewById(R.id.etAlias) as EditText
        btnConnect         = findViewById(R.id.btnConnect) as Button
        btnDisconnect      = findViewById(R.id.btnDisconnect) as Button
        btnOpenMonitor     = findViewById(R.id.btnOpenMonitor) as Button
        btnOpenDbBrowser   = findViewById(R.id.btnOpenDbBrowser) as Button
        btnHelp            = findViewById(R.id.btnHelp) as Button
        cardConnectionParams = findViewById(R.id.cardConnectionParams)

        setupSlotPresets()
        setupButtons()

        etHost.setText(viewModel.lastHost)
        etRack.setText(viewModel.lastRack.toString())
        etSlot.setText(viewModel.lastSlot.toString())

        viewModel.state.observe(stateObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.state.removeObserver(stateObserver)
    }

    private fun setupSlotPresets() {
        val presets = arrayOf("S7-300/400 (Slot 2)", "S7-1200/1500 (Slot 1)", "LOGO! 0BA8 (Slot 1)")
        spinnerPreset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presets)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerPreset.setSelection(1)

        btnApplyPreset.setOnClickListener {
            when (spinnerPreset.selectedItemPosition) {
                0 -> { etRack.setText("0"); etSlot.setText("2") }
                1 -> { etRack.setText("0"); etSlot.setText("1") }
                2 -> { etRack.setText("0"); etSlot.setText("1") }
            }
        }
    }

    private fun setupButtons() {
        btnConnect.setOnClickListener {
            val host = etHost.text.toString().trim()
            val rack = etRack.text.toString().toIntOrNull() ?: 0
            val slot = etSlot.text.toString().toIntOrNull() ?: 1
            val alias = etAlias.text.toString().trim()
            if (host.isEmpty()) {
                etHost.error = "IP-Adresse eingeben"
                return@setOnClickListener
            }
            viewModel.connect(host, rack, slot, alias)
        }

        btnDisconnect.setOnClickListener { viewModel.disconnect() }

        btnOpenMonitor.setOnClickListener {
            startActivity(Intent(this, MonitorActivity::class.java))
        }

        btnOpenDbBrowser.setOnClickListener {
            startActivity(Intent(this, DataBlockActivity::class.java))
        }

        btnHelp.setOnClickListener { showHelpDialog() }
    }

    private fun onStateChanged(state: ConnectionState) {
        when (state) {
            is ConnectionState.Disconnected -> {
                setConnectedUi(false)
                tvStatus.text = "Nicht verbunden"
                tvStatus.setTextColor(getColor(R.color.status_disconnected))
            }
            is ConnectionState.Connecting -> {
                progressBar.visibility = View.VISIBLE
                btnConnect.isEnabled = false
                tvStatus.text = "Verbinde..."
                tvStatus.setTextColor(getColor(R.color.status_connecting))
            }
            is ConnectionState.Connected -> {
                progressBar.visibility = View.GONE
                setConnectedUi(true)
                tvStatus.text = "Verbunden  |  PDU: ${state.pduSize} Bytes"
                tvStatus.setTextColor(getColor(R.color.status_connected))
                Toast.makeText(this, "Verbindung hergestellt", Toast.LENGTH_SHORT).show()
            }
            is ConnectionState.Error -> {
                progressBar.visibility = View.GONE
                btnConnect.isEnabled = true
                tvStatus.text = "Fehler: ${state.message}"
                tvStatus.setTextColor(getColor(R.color.status_error))
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setConnectedUi(connected: Boolean) {
        progressBar.visibility = View.GONE
        btnConnect.isEnabled = !connected
        btnDisconnect.isEnabled = connected
        btnOpenMonitor.isEnabled = connected
        btnOpenDbBrowser.isEnabled = connected
        cardConnectionParams.alpha = if (connected) 0.7f else 1.0f
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("S7 PLC Verbindung")
            .setMessage("""
                Verbindungsparameter:

                • IP-Adresse: Netzwerkadresse der SPS
                • Rack: Baugruppenträger (meist 0)
                • Slot: Steckplatz der CPU
                  - S7-300: Slot 2
                  - S7-400: variiert (1–4)
                  - S7-1200/1500: Slot 1
                  - LOGO! 0BA8+: Slot 1

                Port 102 (ISO-TCP) muss erreichbar sein.
                Bei S7-1200/1500 PUT/GET in den Verbindungseigenschaften aktivieren.
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
}

package com.lumix.s7plc.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lumix.s7plc.databinding.ItemByteRowBinding
import com.lumix.s7plc.model.ByteRow

class ByteRowAdapter(
    private val onRowClick: (ByteRow) -> Unit
) : ListAdapter<ByteRow, ByteRowAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemByteRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: ByteRow) {
            b.tvAddress.text  = "DBB%d".format(row.address)
            b.tvHex.text      = "0x${row.hex}"
            b.tvDecimal.text  = row.decimal
            b.tvBinary.text   = row.binary
            b.root.setOnClickListener { onRowClick(row) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemByteRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ByteRow>() {
            override fun areItemsTheSame(a: ByteRow, b: ByteRow) = a.address == b.address
            override fun areContentsTheSame(a: ByteRow, b: ByteRow) = a == b
        }
    }
}

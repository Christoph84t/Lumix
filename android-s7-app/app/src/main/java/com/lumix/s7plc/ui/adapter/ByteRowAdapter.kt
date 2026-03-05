package com.lumix.s7plc.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.lumix.s7plc.R
import com.lumix.s7plc.model.ByteRow

class ByteRowAdapter(
    private val context: Context,
    private val onRowClick: (ByteRow) -> Unit
) : BaseAdapter() {

    private var items: List<ByteRow> = emptyList()

    fun setData(data: List<ByteRow>) {
        items = data
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_byte_row, parent, false)
        val row = items[position]
        (view.findViewById(R.id.tvAddress) as TextView).text = "DBB%d".format(row.address)
        (view.findViewById(R.id.tvHex) as TextView).text     = "0x${row.hex}"
        (view.findViewById(R.id.tvDecimal) as TextView).text = row.decimal
        (view.findViewById(R.id.tvBinary) as TextView).text  = row.binary
        view.setOnClickListener { onRowClick(row) }
        return view
    }
}

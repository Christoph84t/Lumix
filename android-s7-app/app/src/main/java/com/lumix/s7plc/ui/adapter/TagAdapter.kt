package com.lumix.s7plc.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import com.lumix.s7plc.R
import com.lumix.s7plc.model.PlcTag
import com.lumix.s7plc.model.TagValue
import com.lumix.s7plc.viewmodel.TagState

class TagAdapter(
    private val context: Context,
    private val onRemove: (PlcTag) -> Unit,
    private val onWrite: (PlcTag) -> Unit
) : BaseAdapter() {

    private var items: List<TagState> = emptyList()

    fun setData(data: List<TagState>) {
        items = data
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_tag, parent, false)
        val state = items[position]
        val tag = state.tag

        (view.findViewById(R.id.tvTagName) as TextView).text = tag.name
        (view.findViewById(R.id.tvTagDescription) as TextView).text = buildString {
            append(tag.area.shortName)
            if (tag.area.code == 0x84.toByte()) append(tag.dbNumber)
            append(".DB${tag.dataType.label}${tag.byteOffset}")
            if (tag.dataType.name == "BOOL") append(".${tag.bitOffset}")
            if (tag.unit.isNotBlank()) append("  [${tag.unit}]")
        }
        val tvValue = view.findViewById(R.id.tvTagValue) as TextView
        tvValue.text = state.value.formatted()
        tvValue.setTextColor(
            if (state.value is TagValue.ErrorValue)
                context.getColor(android.R.color.holo_red_light)
            else
                context.getColor(android.R.color.holo_green_dark)
        )

        (view.findViewById(R.id.btnRemove) as Button).setOnClickListener { onRemove(tag) }
        (view.findViewById(R.id.btnWrite) as Button).setOnClickListener  { onWrite(tag) }
        return view
    }
}

package com.lumix.s7plc.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lumix.s7plc.databinding.ItemTagBinding
import com.lumix.s7plc.model.PlcTag
import com.lumix.s7plc.model.TagValue
import com.lumix.s7plc.viewmodel.TagState

class TagAdapter(
    private val onRemove: (PlcTag) -> Unit,
    private val onWrite: (PlcTag) -> Unit
) : ListAdapter<TagState, TagAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemTagBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(state: TagState) {
            val tag = state.tag
            b.tvTagName.text = tag.name
            b.tvTagDescription.text = buildString {
                append(tag.area.shortName)
                if (tag.area.code == 0x84.toByte()) append(tag.dbNumber)
                append(".DB${tag.dataType.label}${tag.byteOffset}")
                if (tag.dataType.name == "BOOL") append(".${tag.bitOffset}")
                if (tag.unit.isNotBlank()) append("  [${tag.unit}]")
            }
            b.tvTagValue.text = state.value.formatted()
            b.tvTagValue.setTextColor(
                itemView.context.getColor(
                    if (state.value is TagValue.ErrorValue)
                        android.R.color.holo_red_light
                    else
                        android.R.color.holo_green_dark
                )
            )
            b.btnRemove.setOnClickListener { onRemove(tag) }
            b.btnWrite.setOnClickListener  { onWrite(tag) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TagState>() {
            override fun areItemsTheSame(a: TagState, b: TagState) = a.tag.id == b.tag.id
            override fun areContentsTheSame(a: TagState, b: TagState) = a == b
        }
    }
}

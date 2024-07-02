package com.ingenico.pclfilesharingsample

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import java.io.File

/**
 * Created by jpeltier on 11/01/2018.
 */

open class FileAdapter(var mInflator: LayoutInflater, var list: ArrayList<File>): BaseAdapter() {

    var selectedIds = ArrayList<Int>()

    fun toggleSelected(position: Int) {
        if (selectedIds.contains(position)) {
            selectedIds.remove(position)
        } else {
            selectedIds.add(position)
        }
        this.notifyDataSetChanged()
    }

    fun getSelected() : ArrayList<Int>{
        return selectedIds
    }

    fun clearSelected(){
        selectedIds.clear()
    }

    override fun getCount(): Int {
        return list.count()
    }

    override fun getItem(position: Int): Any {
        return list.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: FileListHolder

        if (convertView == null) {
            view = this.mInflator.inflate(R.layout.layout_file_item, parent, false)
            holder = FileListHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as FileListHolder
        }

        holder.label.text = list.get(position).name

        if (selectedIds.contains(position)) {
            view.isSelected = true
            view.isPressed = true
            view.setBackgroundColor(Color.GRAY)
        }
        else
        {
            view.isSelected = false
            view.isPressed = false
            view.setBackgroundColor(Color.WHITE)
        }

        return view
    }

}
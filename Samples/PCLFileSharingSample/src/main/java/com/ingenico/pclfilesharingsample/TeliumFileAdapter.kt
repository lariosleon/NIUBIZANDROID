package com.ingenico.pclfilesharingsample

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.ingenico.pclservice.TeliumFile

/**
 * Created by jpeltier on 23/01/2018.
 */
open class TeliumFileAdapter(var mInflator: LayoutInflater, var list: ArrayList<TeliumFile>): BaseAdapter() {

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

        if (list.get(position).isDirectory) {
            holder.icon.setImageResource(R.drawable.folder_icon)
        } else {
            holder.icon.setImageResource(R.drawable.file_icon)
        }

        return view
    }

}
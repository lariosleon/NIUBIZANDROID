package com.ingenico.pclfilesharingsample

import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * Created by jpeltier on 11/01/2018.
 */
class FileListHolder(row: View?) {

    val icon: ImageView = row?.findViewById(R.id.icon) as ImageView
    val label: TextView = row?.findViewById(R.id.filename) as TextView

}
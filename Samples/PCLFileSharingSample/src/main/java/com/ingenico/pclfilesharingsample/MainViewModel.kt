package com.ingenico.pclfilesharingsample

import android.view.LayoutInflater
import androidx.lifecycle.ViewModel
import com.ingenico.pclservice.TeliumFile
import java.io.File

class MainViewModel: ViewModel() {

    var teliumFiles: ArrayList<TeliumFile> = ArrayList()
    var androidFiles: ArrayList<File> = ArrayList()
    var androidFileAdapter: FileAdapter? = null
    var teliumListAdapter: TeliumFileAdapter? = null

    fun updateTeliumListAdapter(inflater: LayoutInflater){
        if(teliumListAdapter == null)
        {
            teliumListAdapter = TeliumFileAdapter(inflater, teliumFiles)
        }
    }

    fun updateAndroidFileAdapter(inflater: LayoutInflater){
        if(androidFileAdapter == null)
        {
            androidFileAdapter = FileAdapter(inflater, androidFiles)
        }
    }
}
package com.ingenico.pclfilesharingsample

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ingenico.pclservice.PCLFileSharing
import com.ingenico.pclservice.PCLFileSharingResult
import com.ingenico.pclservice.TeliumFile

/**
 * Created by jpeltier on 24/01/2018.
 */
class TeliumFileFragment: Fragment() {
    private var tvFileName: TextView? = null
    private var tvFileSize: TextView? = null
    private var btDownload: Button? = null
    private var btDelete: Button? = null
    private lateinit var teliumFile: TeliumFile
    private var alert: AlertDialog? = null

    companion object {
        private const val TAG = "PCL_FILE_SHARING_SAMPLE"
        private var layout : FrameLayout? = null
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if(layout == null )layout = inflater.inflate(R.layout.fragment_telium_file,container) as FrameLayout?
        tvFileName = layout?.findViewById(R.id.filename)
        tvFileSize = layout?.findViewById(R.id.filesize)
        btDownload = layout?.findViewById(R.id.download)
        btDelete = layout?.findViewById(R.id.delete)

        teliumFile = arguments?.getSerializable("FILE") as TeliumFile
        tvFileName?.text = teliumFile.name
        tvFileSize?.text = teliumFile.size.toString() + " bytes"
        Log.d(TAG, teliumFile.toString())

        btDownload?.setOnClickListener { _ ->
            // Download the file from Telium to Android
            alert = AlertDialog.Builder(requireActivity()).create()
            alert!!.setTitle(R.string.downloading_title)
            alert!!.setMessage(this.getString(R.string.downloading_message))
            alert!!.setCancelable(false)
            alert!!.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "Done") { _, _ ->
                alert!!.hide()
            }
            alert!!.show()

            val button = alert!!.getButton(AlertDialog.BUTTON_POSITIVE)
            button.visibility = View.INVISIBLE

            Log.d(TAG, requireActivity().application.applicationInfo.dataDir)

            requireActivity().getExternalFilesDir("import")?.absolutePath?.let {
                fileSharingInterface?.onDownload(teliumFile.path, it, PCLFileSharing.PCLFileSharingOnDownload { result ->
                    if (result == PCLFileSharingResult.PCLFileSharingResultOk) {
                        alert?.setTitle(R.string.downloaded_title)
                        alert?.setMessage(this.getString(R.string.downloaded_message))
                        button?.visibility = View.VISIBLE
                        alert?.setCancelable(true)
                    }
                })
            }
        }
        btDelete?.setOnClickListener(){ _ ->
            alert = AlertDialog.Builder(requireActivity()).create()
            alert!!.setTitle(R.string.deleting_title)
            alert!!.setMessage(this.getString(R.string.deleting_message))
            alert!!.setCancelable(false)
            alert!!.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "Done") { _, _ ->
                alert!!.hide()
            }
            alert!!.show()

            val button = alert!!.getButton(AlertDialog.BUTTON_POSITIVE)
            button.visibility = View.INVISIBLE

            Log.d(TAG, requireActivity().application.applicationInfo.dataDir)

            fileSharingInterface?.onDelete(teliumFile.path,PCLFileSharing.PCLFileSharingOnDelete{ result ->
                if (result == PCLFileSharingResult.PCLFileSharingResultOk) {
                    alert?.setTitle(R.string.deleted_title)
                    alert?.setMessage(this.getString(R.string.deleted_message))
                    button?.visibility = View.VISIBLE
                    alert?.setCancelable(true)
                }
            })
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        if(alert != null)
        {
            if(alert!!.isShowing) alert!!.cancel()
            alert = null
        }
    }

    private var fileSharingInterface : FileSharingInterface? = null

    fun setFileSharingInterface(fileSharingInterface: FileSharingInterface){
        this.fileSharingInterface = fileSharingInterface
    }

    interface FileSharingInterface{
        fun onDownload(filepath:String, toDirectory:String, callback:PCLFileSharing.PCLFileSharingOnDownload)
        fun onDelete(filepath:String, callback:PCLFileSharing.PCLFileSharingOnDelete)
    }
}
package com.ingenico.pclfilesharingsample

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.view.MenuInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.Switch
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ingenico.pclservice.PCLFileSharing
import com.ingenico.pclservice.PCLFileSharingResult
import com.ingenico.pclservice.TeliumFile
import kotlinx.android.synthetic.main.fragment_main.tvState
import kotlinx.android.synthetic.main.fragment_main.toolbar
import kotlinx.android.synthetic.main.fragment_main.connection_fab
import kotlinx.android.synthetic.main.fragment_main.suspend_fab
import kotlinx.android.synthetic.main.fragment_main.cancel_fab
import java.io.File

class MainFragment : Fragment(){
    private var lltMode: Boolean = false
    private lateinit var lltModeSwitch: Switch
    private lateinit var btnMultiSelect: Button
    private var connectAlert: AlertDialog? = null
    private var isMultiSelectMode = false
    private lateinit var mViewModel: MainViewModel
    private lateinit var androidList: ListView
    private lateinit var compressionCheckBox:CheckBox
    private lateinit var installSpinner:Spinner
    private lateinit var teliumList: ListView
    private var isSuspended = false

    private var currentPath: String = "/"
    private var pathHistory: ArrayList<String> = ArrayList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val layout = inflater.inflate(R.layout.fragment_main, container, false) as CoordinatorLayout

        lltModeSwitch = layout.findViewById(R.id.lltModeSwitch)
        btnMultiSelect = layout.findViewById(R.id.buttonMultiSelect)
        androidList = layout.findViewById(R.id.androidList)
        compressionCheckBox = layout.findViewById(R.id.compressionCheckBox)
        installSpinner = layout.findViewById(R.id.installSpinner)
        teliumList = layout.findViewById(R.id.teliumList)
        this.context?.let {
            ArrayAdapter.createFromResource(
                it,
                R.array.install_array,
                android.R.layout.simple_spinner_item
            ).also { adapter ->
                // Specify the layout to use when the list of choices appears
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                // Apply the adapter to the spinner
                installSpinner.adapter = adapter
            }
        }

        mViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        mViewModel.updateAndroidFileAdapter(inflater)
        mViewModel.updateTeliumListAdapter(inflater)

        return layout
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.menu_main,menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        if (item?.itemId == R.id.action_settings) {
            fileSharingInterface?.onOpenDevicesSettings()
        }

        return when (item?.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)

        fileSharingInterface?.let{
            if(it.isPclConnected()) {
                tvState.setText(R.string.str_connected)
                tvState.setBackgroundColor(Color.GREEN)
            } else {
                tvState.setText(R.string.str_not_connected)
                tvState.setBackgroundColor(Color.RED)
            }
        }
        tvState.setTextColor(Color.BLACK)
        if(fileSharingInterface?.isFileSharingStarted() == true){
            connection_fab.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            (suspend_fab as View).visibility = View.VISIBLE
            (cancel_fab as View).visibility = View.VISIBLE
        }
        connection_fab.setOnClickListener {
            fileSharingInterface?.let {
                if (it.isFileSharingStarted()) {
                    resetSharing(false)
                } else {
                    displayConnectingAlert()
                }
            }
        }
        suspend_fab.setOnClickListener{
            if (isSuspended) {
                fileSharingInterface?.let{ fsi ->
                    fsi.onRestart(PCLFileSharing.PCLFileSharingOnResult {
                        if(it == PCLFileSharingResult.PCLFileSharingResultOk) {
                            isSuspended = false
                            suspend_fab.setImageResource(android.R.drawable.ic_media_pause)
                        }
                    })
                }
            } else {
                fileSharingInterface?.let{ fsi ->
                    fsi.onSuspend(PCLFileSharing.PCLFileSharingOnResult {
                        if(it == PCLFileSharingResult.PCLFileSharingResultOk) {
                            isSuspended = true
                            suspend_fab.setImageResource(android.R.drawable.ic_media_next)
                        }
                    })
                }
            }
        }
        cancel_fab.setOnClickListener{
            fileSharingInterface?.let{ fsi ->
                fsi.onCancel(PCLFileSharing.PCLFileSharingOnResult {
                    isSuspended = true
                    suspend_fab.setImageResource(android.R.drawable.ic_media_next)
                })
            }
        }

        lltModeSwitch.setOnClickListener {
            if (lltModeSwitch.isChecked) {
                fileSharingInterface?.let {
                    if (it.isFileSharingStarted()) {
                        resetSharing()
                    }
                }
                this.lltMode = true
                displayConnectingAlert()
            } else {
                fileSharingInterface?.let {
                    if (it.isFileSharingStarted()) {
                        resetSharing()
                    }
                }
                this.lltMode = false
            }
        }

        connectAlert = AlertDialog.Builder(activity as MainActivity).create()
        connectAlert!!.setTitle(R.string.waiting_terminal_title)
        connectAlert!!.setMessage(this.getString(R.string.waiting_terminal_message))
        connectAlert!!.setCancelable(false)

        connectAlert!!.setButton(AlertDialog.BUTTON_POSITIVE, "Cancel") { _, _ ->
            resetSharing()
        }

        btnMultiSelect.setOnClickListener {
            fileSharingInterface?.let{
                if (it.isFileSharingStarted()) {
                    if (isMultiSelectMode) {
                        btnMultiSelect.text = getString(R.string.multiselect_btn_message)

                        //send list of file
                        if(mViewModel.androidFileAdapter!!.getSelected().size > 0){
                            val alert = AlertDialog.Builder(activity as MainActivity).create()
                            alert.setTitle(R.string.uploading_title)
                            alert.setMessage(this.getString(R.string.uploading_files_message, 0, mViewModel.androidFileAdapter!!.getSelected().size))
                            alert.setCancelable(false)
                            alert.setButton(AlertDialog.BUTTON_POSITIVE, "Done") { _, _ ->
                                alert.hide()
                            }
                            alert.show()

                            val button = alert.getButton(AlertDialog.BUTTON_POSITIVE)
                            button.visibility = View.INVISIBLE

                            //create file list to send
                            val androidSendFiles: ArrayList<File> = ArrayList()
                            for(i in mViewModel.androidFileAdapter!!.getSelected()){
                                androidSendFiles.add(mViewModel.androidFiles[i])
                            }

                            it.onUpload(androidSendFiles, "/import",compressionCheckBox.isChecked, PCLFileSharing.PCLFileSharingOnUploads { current, total, result ->
                                if (result == PCLFileSharingResult.PCLFileSharingResultOk && current == total) {
                                    alert.setTitle(R.string.uploaded_title)
                                    alert.setMessage(this.getString(R.string.uploaded_files_message, total))
                                    alert.setCancelable(true)
                                    button.visibility = View.VISIBLE
                                } else if (result != PCLFileSharingResult.PCLFileSharingResultOk) {
                                    alert.setTitle(R.string.uploaded_error_title)
                                    alert.setMessage(this.getString(R.string.uploaded_error_files_message, current, total, result.value))
                                    alert.setCancelable(true)
                                    button.visibility = View.VISIBLE
                                } else {
                                    alert.setTitle(R.string.uploading_title)
                                    alert.setMessage(this.getString(R.string.uploading_files_message, current, total))
                                }
                            })
                        }
                        mViewModel.androidFileAdapter?.clearSelected()
                        mViewModel.androidFileAdapter?.notifyDataSetChanged()
                    } else {
                        btnMultiSelect.text = getString(R.string.send_btn_message)
                    }
                    isMultiSelectMode = !isMultiSelectMode


                } else {
                    showNotConnectedAlert()
                }
            }
        }

        androidList.adapter = mViewModel.androidFileAdapter
        androidList.setOnItemClickListener { _, _, position, _ ->
            fileSharingInterface?.let{
                if (it.isFileSharingStarted()) {
                    if(isMultiSelectMode){
                        Log.d(TAG, "Click on file " + mViewModel.androidFiles[position].path)
                        mViewModel.androidFileAdapter?.toggleSelected(position)
                    }else {
                        Log.d(TAG, "Sending file " + mViewModel.androidFiles[position].path + " to /import")
                        val alert = AlertDialog.Builder(activity as MainActivity).create()
                        alert.setTitle(R.string.uploading_title)
                        alert.setMessage(this.getString(R.string.uploading_message))
                        alert.setCancelable(false)
                        alert.setButton(AlertDialog.BUTTON_POSITIVE, "Done") { _, _ ->
                            alert.hide()
                        }
                        alert.show()

                        val button = alert.getButton(AlertDialog.BUTTON_POSITIVE)
                        button.visibility = View.INVISIBLE

                        it?.onUpload(mViewModel.androidFiles.get(position), "/import",compressionCheckBox.isChecked, PCLFileSharing.PCLFileSharingOnUpload { result ->
                            if (result == PCLFileSharingResult.PCLFileSharingResultOk) {
                                alert.setTitle(R.string.uploaded_title)
                                alert.setMessage(this.getString(R.string.uploaded_message))
                                alert.setCancelable(true)
                                button.visibility = View.VISIBLE
                            } else {
                                alert.setTitle(R.string.uploaded_error_title)
                                alert.setMessage(this.getString(R.string.uploaded_error_file_message,  result.value))
                                alert.setCancelable(true)
                                button.visibility = View.VISIBLE
                            }
                        })
                    }
                } else {
                    showNotConnectedAlert()
                }
            }
        }

        teliumList.adapter =  mViewModel.teliumListAdapter
        teliumList.setOnItemClickListener { _, _, position, _ ->
            fileSharingInterface?.let{
                if (it.isFileSharingStarted()) {
                    if (position == 0) {
                        if (pathHistory.size > 0) {
                            Log.d(TAG, "Move back to " + pathHistory.last())

                            it.onList(pathHistory.last(), PCLFileSharing.PCLFileSharingOnList { files ->
                                val back = TeliumFile("..", "/", 0, false)

                                mViewModel.teliumFiles.clear()
                                mViewModel.teliumFiles.add(back)

                                files?.forEachIndexed { _, teliumFile ->
                                    mViewModel.teliumFiles.add(teliumFile)
                                }

                                mViewModel.teliumListAdapter?.notifyDataSetChanged()
                                if(pathHistory.size > 0) {
                                    this.currentPath = pathHistory.last()
                                    this.pathHistory.removeAt(pathHistory.size - 1)
                                }
                            })
                        }
                    } else {
                        val teliumFile = mViewModel.teliumFiles[position]

                        if (teliumFile.isDirectory) {
                            Log.d(TAG, "Move to " + teliumFile.path)

                            it.onList(teliumFile.path, PCLFileSharing.PCLFileSharingOnList { files ->
                                val back = TeliumFile("..", "/", 0, false)

                                mViewModel.teliumFiles.clear()
                                mViewModel.teliumFiles.add(back)

                                files?.forEachIndexed { _, teliumFile ->
                                    mViewModel.teliumFiles.add(teliumFile)
                                }

                                mViewModel.teliumListAdapter?.notifyDataSetChanged()

                                pathHistory.add(currentPath)
                                currentPath = teliumFile.path
                            })
                        } else {
                            Log.d(TAG, "Open Telium file " + teliumFile.name)
                            it.onSetTeliumFileView(teliumFile)
                        }
                    }
                }
            }

        }
    }

    override fun onResume() {
        super.onResume()

        mViewModel.androidFiles.clear()

        val directory = requireActivity().getExternalFilesDir("import")
        directory?.listFiles()?.forEach {
            Log.d(TAG, it.name)
            mViewModel.androidFiles.add(it)
        }

        fileSharingInterface?.let{
            if(it.isPclConnected()) {
                tvState.setText(R.string.str_connected)
                tvState.setBackgroundColor(Color.GREEN)

                if (it.isFileSharingStarted()) {
                    it.onList(currentPath, PCLFileSharing.PCLFileSharingOnList { files ->
                        val back = TeliumFile("..", "/", 0, false)
                        mViewModel.teliumFiles.clear()
                        mViewModel.teliumFiles.add(back)

                        files?.forEachIndexed { _, teliumFile ->
                            mViewModel.teliumFiles.add(teliumFile)
                        }

                        mViewModel.teliumListAdapter?.notifyDataSetChanged()
                    })
                } else {
                    mViewModel.teliumFiles.clear()
                    mViewModel.teliumListAdapter?.notifyDataSetChanged()
                    this.connection_fab.setImageResource(android.R.drawable.ic_media_play)
                    (this.suspend_fab as View).visibility = View.GONE
                    (this.cancel_fab as View).visibility = View.GONE
                    this.lltMode = false
                    lltModeSwitch.isChecked = false
                }
            } else {
                tvState.setText(R.string.str_not_connected)
                tvState.setBackgroundColor(Color.RED)

                if (it.isFileSharingStarted()) {
                    resetSharing()
                }else {
                    mViewModel.teliumFiles.clear()
                    mViewModel.teliumListAdapter?.notifyDataSetChanged()
                    this.connection_fab.setImageResource(android.R.drawable.ic_media_play)
                    (this.suspend_fab as View).visibility = View.GONE
                    (this.cancel_fab as View).visibility = View.GONE
                    this.lltMode = false
                    lltModeSwitch.isChecked = false
                }
            }
        }
    }

    private fun showNotConnectedAlert() {
        val alert = AlertDialog.Builder(requireActivity()).create()
        alert.setTitle(R.string.terminal_not_connected_title)
        alert.setMessage(this.getString(R.string.terminal_not_connected_message))
        alert.setCancelable(true)
        alert.show()
    }

    private fun displayConnectingAlert() {
        if (lltMode) {
            fileSharingInterface?.onStartAsLLT("pairingfile.txt", PCLFileSharing.PCLFileSharingOnStart { result ->
                this.hideConnectingAlert()
                if (result == PCLFileSharingResult.PCLFileSharingResultOk) {
                    connection_fab.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    (this.suspend_fab as View).visibility = View.VISIBLE
                    (this.cancel_fab as View).visibility = View.VISIBLE
                    fileSharingInterface?.onList("/", PCLFileSharing.PCLFileSharingOnList { files ->
                        val back = TeliumFile("..", "/", 0, false)

                        mViewModel.teliumFiles.clear()
                        mViewModel.teliumFiles.add(back)

                        files?.forEachIndexed { _, teliumFile ->
                            mViewModel.teliumFiles.add(teliumFile)
                        }

                        mViewModel.teliumListAdapter?.notifyDataSetChanged()
                    })
                }
                else
                    resetSharing()
            })
        } else {
            fileSharingInterface?.onStartAndDoUpdate("pairingfile.txt", 8000, PCLFileSharing.PCLFileSharingOnStart { result ->
                this.hideConnectingAlert()
                if (result == PCLFileSharingResult.PCLFileSharingResultOk) {
                    connection_fab.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    (this.suspend_fab as View).visibility = View.VISIBLE
                    (this.cancel_fab as View).visibility = View.VISIBLE
                    fileSharingInterface?.onList("/", PCLFileSharing.PCLFileSharingOnList { files ->
                        val back = TeliumFile("..", "/", 0, false)

                        mViewModel.teliumFiles.clear()
                        mViewModel.teliumFiles.add(back)

                        files?.forEachIndexed { _, teliumFile ->
                            mViewModel.teliumFiles.add(teliumFile)
                        }

                        mViewModel?.teliumListAdapter?.notifyDataSetChanged()
                    })
                }
                else
                    resetSharing()
            })
        }
        connectAlert?.show()
    }

    private fun hideConnectingAlert() {
        connectAlert?.hide()
    }

    override fun onPause(){
        connectAlert?.let{
            if(it.isShowing) it.dismiss()
            connectAlert = null
        }
        super.onPause()
    }

    fun onStateChanged(state: String) {
        if (state == "CONNECTED") {
            tvState.setText(R.string.str_connected)
            tvState.setBackgroundColor(Color.GREEN)
            tvState.setTextColor(Color.BLACK)
        } else {
            tvState.setText(R.string.str_not_connected)
            tvState.setBackgroundColor(Color.RED)
            tvState.setTextColor(Color.BLACK)

            fileSharingInterface?.let{
                if (it.isFileSharingStarted()) {
                    if(it.isPclConnected()){
                        it.onList(currentPath, PCLFileSharing.PCLFileSharingOnList { files ->
                            val back = TeliumFile("..", "/", 0, false)
                            mViewModel.teliumFiles.clear()
                            mViewModel.teliumFiles.add(back)

                            files?.forEachIndexed { _, teliumFile ->
                                mViewModel.teliumFiles.add(teliumFile)
                            }

                            mViewModel.teliumListAdapter?.notifyDataSetChanged()
                        })
                    } else {
                        resetSharing()
                    }
                } else {
                    mViewModel.teliumFiles.clear()
                    mViewModel.teliumListAdapter?.notifyDataSetChanged()
                    this.connection_fab.setImageResource(android.R.drawable.ic_media_play)
                    (this.suspend_fab as View).visibility = View.GONE
                    (this.cancel_fab as View).visibility = View.GONE
                    this.lltMode = false
                    lltModeSwitch.isChecked = false
                }
            }
        }
    }

    fun resetSharing(cancel: Boolean = true){
        fileSharingInterface?.let {
            PCLFileSharing.PCLFileSharingOnStop { result ->
                it.onResetPcl()
                if (result == PCLFileSharingResult.PCLFileSharingResultOk) {
                    mViewModel.teliumFiles.clear()
                    mViewModel.teliumListAdapter?.notifyDataSetChanged()
                    this.connection_fab.setImageResource(android.R.drawable.ic_media_play)
                    this.suspend_fab.setImageResource(android.R.drawable.ic_media_pause)
                    (this.suspend_fab as View).visibility = View.GONE
                    (this.cancel_fab as View).visibility = View.GONE
                    this.isSuspended = false
                    this.lltMode = false
                    lltModeSwitch.isChecked = false
                }
            }.let { callback ->
                if(cancel) {
                    fileSharingInterface?.onShutdown()
                    callback.onStop(PCLFileSharingResult.PCLFileSharingResultAbortedError)
                }else{
                    when(installSpinner.selectedItem.toString()){
                        resources.getStringArray(R.array.install_array)[0] -> {
                            fileSharingInterface?.onSuspend(PCLFileSharing.PCLFileSharingOnResult {
                                if(it == PCLFileSharingResult.PCLFileSharingResultOk)
                                    fileSharingInterface?.onStop(false, callback)
                                else {
                                    fileSharingInterface?.onShutdown()
                                    callback.onStop(PCLFileSharingResult.PCLFileSharingResultAbortedError)
                                }
                            })
                        }
                        resources.getStringArray(R.array.install_array)[1] -> {
                            fileSharingInterface?.onCommit(PCLFileSharing.PCLFileSharingOnResult {
                                if (it == PCLFileSharingResult.PCLFileSharingResultOk)
                                    fileSharingInterface?.onStop(false, callback)
                                else {
                                    fileSharingInterface?.onShutdown()
                                    callback.onStop(PCLFileSharingResult.PCLFileSharingResultAbortedError)
                                }
                            })
                        }
                        resources.getStringArray(R.array.install_array)[2] ->{
                            fileSharingInterface?.onInstallOffline(PCLFileSharing.PCLFileSharingOnResult {
                                if (it == PCLFileSharingResult.PCLFileSharingResultOk)
                                    fileSharingInterface?.onStop(false, callback)
                                else {
                                    fileSharingInterface?.onShutdown()
                                    callback.onStop(PCLFileSharingResult.PCLFileSharingResultAbortedError)
                                }
                            })
                        }
                        else -> {
                            fileSharingInterface?.onShutdown()
                            callback.onStop(PCLFileSharingResult.PCLFileSharingResultAbortedError)
                        }
                    }
                }
            }
        }
    }

    interface FileSharingInterface{
        fun onOpenDevicesSettings()
        fun isFileSharingStarted():Boolean
        fun onStartAsLLT(pairingFileName: String, callback: PCLFileSharing.PCLFileSharingOnStart)
        fun onStartAndDoUpdate(pairingFileName: String, port: Int, callback: PCLFileSharing.PCLFileSharingOnStart)
        fun onRestart(callback: PCLFileSharing.PCLFileSharingOnResult)
        fun onList(path: String, callback: PCLFileSharing.PCLFileSharingOnList)
        fun onUpload(file: File, toDirectory: String, compressed: Boolean, callback: PCLFileSharing.PCLFileSharingOnUpload)
        fun onUpload(files: List<File>, toDirectory: String,compressed: Boolean, callback: PCLFileSharing.PCLFileSharingOnUploads)
        fun onCommit(callback: PCLFileSharing.PCLFileSharingOnResult)
        fun onInstallOffline(callback: PCLFileSharing.PCLFileSharingOnResult)
        fun onSuspend(callback: PCLFileSharing.PCLFileSharingOnResult)
        fun onCancel(callback: PCLFileSharing.PCLFileSharingOnResult)
        fun onStop(install: Boolean,callback: PCLFileSharing.PCLFileSharingOnStop)
        fun onShutdown()
        fun onSetTeliumFileView(file: TeliumFile)
        fun isPclConnected():Boolean
        fun onResetPcl()
    }

    private var fileSharingInterface: FileSharingInterface? = null
    fun setFileSharingInterface(fileSharingInterface: FileSharingInterface)
    {
        this.fileSharingInterface = fileSharingInterface
    }

    companion object{
        const val TAG = CommonActivity.TAG
    }
}

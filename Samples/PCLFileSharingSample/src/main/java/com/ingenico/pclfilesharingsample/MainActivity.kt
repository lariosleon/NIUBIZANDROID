package com.ingenico.pclfilesharingsample


import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import com.ingenico.pclservice.PCLFileSharing
import com.ingenico.pclservice.PCLFileSharing.NTPT3_EVENT_INTENT
import com.ingenico.pclservice.PclService
import com.ingenico.pclservice.TeliumFile
import com.ingenico.pclutilities.PclLog
import java.io.File
import java.util.*

class MainActivity : CommonActivity(), FragmentOnAttachListener, MainFragment.FileSharingInterface, TeliumFileFragment.FileSharingInterface {
    val TELIUM_FILE_FRAGMENT_TAG = "TeliumFile"
    val MAIN_FRAGMENT_TAG = "Main"
    val FILE_SHARING_TIMEOUT_TRY = 0

    val mFileSharingReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            when (intent?.action) {
                PCLFileSharing.NTPT3_EVENT_INTENT -> handleNtpt3Event(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.isTestVersion && BuildConfig.LimitTimeUsed < System.currentTimeMillis()) {
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle("PCL Test Version")
            alertDialogBuilder
                    .setMessage("You try to use a PCL test version after the deadline!")
                    .setCancelable(false)
                    .setPositiveButton("Close") { dialog, id ->
                        // if this button is clicked, close
                        // current activity
                        this.finish()
                    }

            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
            return
        }

        if(checkAndRequestPermissions()) {
            startPclService()
            initService()
        }

        // Disable low power mode during NTPT3
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initStateReceiver()

        registerReceiver(mFileSharingReceiver, IntentFilter(NTPT3_EVENT_INTENT))

        supportFragmentManager.addFragmentOnAttachListener(this)

        if(savedInstanceState == null) {
            val fragment = MainFragment()
            var ft = supportFragmentManager.beginTransaction()
            ft.add(R.id.fragment_container, fragment, MAIN_FRAGMENT_TAG)
            ft.commit()
        }
    }

    override fun onStateChanged(state: String) {
        supportFragmentManager.findFragmentByTag(TELIUM_FILE_FRAGMENT_TAG)?.let{
            if((it is TeliumFileFragment)&&(it.isResumed)&&(state == "DISCONNECTED"))
                supportFragmentManager.popBackStack()
        }
        supportFragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG)?.let{
            if((it is MainFragment)&&(it.isResumed))
                it.onStateChanged(state)
        }
    }


    override fun onPclServiceConnected() {
        Log.d(MainFragment.TAG, "onPclServiceConnected")
        if(isCompanionConnected)
            onStateChanged("CONNECTED")
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity: onDestroy")
        PCLFileSharing.getSharedInstance().shutdown()
        releaseService()
        stopPclService()
        unregisterReceiver(mFileSharingReceiver)
        super.onDestroy()
    }

    private fun handleNtpt3Event(intent: Intent?){
        val extra = intent?.getSerializableExtra(PCLFileSharing.NTPT3_EVENT_END_EXTRA)
        var stop = true
        extra?.let{Log.d(TAG, "handle event ${PCLFileSharing.NTPT3_EVENT_END_EXTRA} : $it")}?: run {
            val extra = intent?.getSerializableExtra(PCLFileSharing.NTPT3_EVENT_ERROR_EXTRA)
            Log.d(TAG, "handle event ${PCLFileSharing.NTPT3_EVENT_ERROR_EXTRA} : $extra")
            extra?.let{
                stop = it == PCLFileSharing.Ntpt3Error.NTPT3_ERROR_TIMEOUT
            } ?: run{stop = false}
        }
        if(stop) {
            supportFragmentManager.findFragmentByTag(TELIUM_FILE_FRAGMENT_TAG)?.let {
                if ((it is TeliumFileFragment) && (it.isResumed))
                    supportFragmentManager.popBackStack()
            }
            supportFragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG)?.let {
                if ((it is MainFragment) && (it.isResumed))
                    it.resetSharing()
            }
        }
    }

    private fun startPclService() {
        if (!mServiceStarted) {
            val settings = getSharedPreferences("PCLSERVICE", Context.MODE_PRIVATE)
            val enableLog = settings.getBoolean("ENABLE_LOG", true)
            val i = Intent(this, PclService::class.java)
            i.putExtra("PACKAGE_NAME", BuildConfig.APPLICATION_ID)
            i.putExtra("FILE_NAME", "pairingfile.txt")
            i.putExtra("ENABLE_LOG", enableLog)
            if (applicationContext.startService(i) != null)
                mServiceStarted = true
        }
    }

    private fun stopPclService() {
        if (mServiceStarted) {
            Log.d(TAG, "MainActivity: stopPclService")
            val i = Intent(this, PclService::class.java)
            if (applicationContext.stopService(i))
                mServiceStarted = false
        }
    }

    override fun onSetTeliumFileView(file : TeliumFile) {
        var ft = supportFragmentManager.beginTransaction()
        ft.addToBackStack(null)
        var fragment = TeliumFileFragment()
        var savedInstance = Bundle()
        savedInstance.putSerializable("FILE",file)
        fragment.arguments = savedInstance
        ft.replace(R.id.fragment_container,fragment,TELIUM_FILE_FRAGMENT_TAG)
        ft.commit()
    }

    override fun onAttachFragment(fragmentManager: FragmentManager, fragment:Fragment) {
        if(fragment is MainFragment)
            fragment.setFileSharingInterface(this)
        else if (fragment is TeliumFileFragment)
            fragment.setFileSharingInterface(this)
    }

    override fun onOpenDevicesSettings() {
        val intent = Intent(this, DevicesActivity::class.java)
        startActivity(intent)
    }
    override fun isFileSharingStarted(): Boolean {
        return PCLFileSharing.getSharedInstance().isStarted
    }

    override fun onStartAsLLT(pairingFileName:String, callback: PCLFileSharing.PCLFileSharingOnStart){
        PCLFileSharing.getSharedInstance().startAsLLTMode(this,pairingFileName,false,callback)
    }

    override fun onStartAndDoUpdate(pairingFileName: String, port:Int, callback: PCLFileSharing.PCLFileSharingOnStart){
        PCLFileSharing.getSharedInstance().startAndLaunchDoUpdate(this,pairingFileName,port,false,callback)
    }

    override fun onRestart(callback: PCLFileSharing.PCLFileSharingOnResult) {
        PCLFileSharing.getSharedInstance().restartSession(callback)
    }

    override fun onList(path:String, callback: PCLFileSharing.PCLFileSharingOnList){
        PCLFileSharing.getSharedInstance().list(path,callback)
    }

    override fun onUpload(file: File, toDirectory: String, compressed:Boolean, callback: PCLFileSharing.PCLFileSharingOnUpload) {
        PCLFileSharing.getSharedInstance().upload(file,toDirectory,compressed,callback)
    }

    override fun onUpload(files: List<File>, toDirectory: String, compressed:Boolean, callback: PCLFileSharing.PCLFileSharingOnUploads) {
        PCLFileSharing.getSharedInstance().upload(files,toDirectory,compressed,callback)
    }

    override fun onCommit(callback: PCLFileSharing.PCLFileSharingOnResult) {
        PCLFileSharing.getSharedInstance().commitSession(callback)
    }

    override fun onInstallOffline(callback: PCLFileSharing.PCLFileSharingOnResult) {
        PCLFileSharing.getSharedInstance().installOffline(callback)
    }

    override fun onSuspend(callback: PCLFileSharing.PCLFileSharingOnResult) {
        PCLFileSharing.getSharedInstance().suspendSession(callback)
    }

    override fun onStop(install:Boolean, callback:PCLFileSharing.PCLFileSharingOnStop){
        PCLFileSharing.getSharedInstance().stop(install,callback)
    }

    override fun onShutdown() {
        PCLFileSharing.getSharedInstance().shutdown()
    }

    override fun onCancel(callback: PCLFileSharing.PCLFileSharingOnResult) {
        PCLFileSharing.getSharedInstance().cancelSession(callback)
    }

    override fun isPclConnected():Boolean{
        return isCompanionConnected
    }

    override fun onResetPcl(){
        mPclService?.let{
            it.disconnectPcl()
            it.connectPcl()
        }
    }

    override fun onDownload(filepath: String, toDirectory: String, callback: PCLFileSharing.PCLFileSharingOnDownload) {
        PCLFileSharing.getSharedInstance().download(filepath,toDirectory,callback)
    }

    override fun onDelete(filepath: String, callback: PCLFileSharing.PCLFileSharingOnDelete) {
        PCLFileSharing.getSharedInstance().delete(filepath,callback)
    }

    fun showDeniedPermissionAlert(permissions: ArrayList<String>) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setMessage("ERROR: Can't start PCL service\nMissing permissions :\n$permissions")
        builder.setCancelable(true)
        builder.create().show()
    }

    protected val mPclServicePermissions =
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

    fun checkAndRequestPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PclLog.v(TAG, "checkAndRequestPermissions")
            val permissionsToGrant = ArrayList<String>()
            for (permission in mPclServicePermissions) {
                if (checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) permissionsToGrant.add(
                    permission
                )
            }
            if (permissionsToGrant.size > 0) {
                val requestMultiplePermissions =
                    registerForActivityResult<Array<String>, Map<String, Boolean>>(
                        RequestMultiplePermissions()
                    ) { result ->
                        val permissionsDenied = ArrayList<String>()
                        for ((key, value) in result) {
                            if (!value) permissionsDenied.add(key)
                        }
                        if (permissionsDenied.size > 0) {
                            showDeniedPermissionAlert(permissionsDenied)
                        } else {
                            startPclService()
                            initService()
                        }
                    }
                requestMultiplePermissions.launch(mPclServicePermissions)
                return false
            }
        }
        return true
    }
}

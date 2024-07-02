package com.ingenico.pclfilesharingsample

import android.annotation.SuppressLint
import android.content.*
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.ingenico.pclservice.PclService
import com.ingenico.pclservice.PclBinder

abstract class CommonActivity : AppCompatActivity(), CommonActivityInterface {
    protected var mPclService: PclService? = null
    private var mStateReceiver: StateReceiver? = null
    protected var mServiceStarted: Boolean = false
    private var mServiceConnection: PclServiceConnection = PclServiceConnection()
    private var mBound = false

    val isCompanionConnected: Boolean
        get() {
            var bRet = false
            if (mPclService != null) {
                val result = ByteArray(1)
                run {
                    if (mPclService!!.serverStatus(result)) {
                        if (result[0].toInt() == 0x10)
                            bRet = true
                    }
                }
            }
            return bRet
        }


    internal inner class PclServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, boundService: IBinder) {
            //mPclService = IPclService.Stub.asInterface((IBinder)boundService);
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = boundService as PclBinder
            mPclService = binder.service as PclService
            Log.d(TAG, "onServiceConnected")
            onPclServiceConnected()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mPclService = null
            Log.d(TAG, "onServiceDisconnected")
        }
    }

    override fun onResume() {
        Log.d(TAG, "CommonActivity: onResume")
        super.onResume()
        initStateReceiver()
    }

    override fun onPause() {
        Log.d(TAG, "CommonActivity: onPause")
        super.onPause()
        releaseStateReceiver()
    }


    internal abstract fun onPclServiceConnected()

    protected fun initService() {
        if (!mBound) {
            Log.d(TAG, "initService")
            val settings = getSharedPreferences("PCLSERVICE", Context.MODE_PRIVATE)
            val enableLog = settings.getBoolean("ENABLE_LOG", true)
            mServiceConnection = PclServiceConnection()
            val intent = Intent(this, PclService::class.java)
            intent.putExtra("PACKAGE_NAME", "com.ingenico.pcltestappwithlib")
            intent.putExtra("FILE_NAME", "pairingfile.txt")
            intent.putExtra("ENABLE_LOG", enableLog)
            mBound = bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    protected fun releaseService() {
        if (mBound) {
            Log.d(TAG, "releaseService")
            unbindService(mServiceConnection)
            mBound = false
        }
    }

    protected fun initStateReceiver() {
        if (mStateReceiver == null) {
            Log.d(TAG, "initStateReceiver")
            mStateReceiver = StateReceiver(this)
            val intentfilter = IntentFilter("com.ingenico.pclservice.intent.action.STATE_CHANGED")
            registerReceiver(mStateReceiver, intentfilter)
        }
    }

    protected fun releaseStateReceiver() {
        if (mStateReceiver != null) {
            Log.d(TAG, "releaseStateReceiver")
            unregisterReceiver(mStateReceiver)
            mStateReceiver = null
        }
    }

    private inner class StateReceiver internal constructor(receiver: CommonActivity) : BroadcastReceiver() {
        private var viewOwner: CommonActivity? = null
        @SuppressLint("UseValueOf")
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra("state")
            Log.d(TAG, String.format("receiver: State %s", state))
            state?.let{viewOwner!!.onStateChanged(it)}
        }

        init {
            viewOwner = receiver
        }
    }

    companion object {
        const val TAG = "PCL_FILE_SHARING_SAMPLE"
    }

}

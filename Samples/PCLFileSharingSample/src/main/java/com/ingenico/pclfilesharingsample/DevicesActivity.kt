package com.ingenico.pclfilesharingsample

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import com.ingenico.pclservice.PclService
import com.ingenico.pclutilities.PclUtilities
import com.ingenico.pclutilities.PclUtilities.IpTerminal
import java.util.*

/**
 * Created by jpeltier on 27/12/2017.
 */

class DevicesActivity : CommonActivity(), View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mRadioGroup: RadioGroup
    private lateinit var mPclUtil: PclUtilities
    private var sslActivated = false
    private var terminalCounter = 0
    private val terminals = HashSet<IpTerminal>()
    internal var mCurrentDevice: CharSequence? = null
    private var mUsbManager: UsbManager? = null
    private var mPermissionIntent: PendingIntent? = null
    private var mPermissionRequested = false
    private var mPermissionGranted = false

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devices)

        Log.d(TAG, "DevicesActivity onCreate")

        val data = lastNonConfigurationInstance as CharSequence?
        mCurrentDevice = data

        mPclUtil = PclUtilities(this, BuildConfig.APPLICATION_ID, "pairingfile.txt")

        initService()

        mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        if (mUsbManager != null) {
            mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);

            val usbFilter = IntentFilter(ACTION_USB_PERMISSION)
            usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            registerReceiver(mUsbReceiver, usbFilter)
        }

        mProgressBar = findViewById(R.id.lookingCompanionsProgress)
        mRadioGroup = findViewById(R.id.optionRadioGroup)
        refreshTerminalsList("onCreate");
        mRadioGroup.setOnCheckedChangeListener(this)
    }

    override fun onDestroy() {
        Log.d(TAG, "DevicesActivity: onDestroy")
        super.onDestroy()
        releaseService()
        unregisterReceiver(mUsbReceiver)
    }

    override fun onResume() {
        super.onResume()
        refreshTerminalsList("onResume")
    }

    override fun onClick(view: View) {
    }

    override fun onStateChanged(state: String) {
    }

    override fun onPclServiceConnected() {
        Log.d(TAG, "onPclServiceConnected")
    }

    internal fun refreshTerminalsList(string: String) {
        Log.d(TAG, "refreshTerminalsList: " + string)
        mProgressBar.visibility = View.VISIBLE
        var bFound = false
        terminalCounter = 0

        mRadioGroup.removeAllViews()

        val btCompanions = mPclUtil.GetPairedCompanions()
        if (btCompanions != null && btCompanions.size > 0) {
            for (companion in btCompanions) {
                Log.d(TAG, "refreshTerminalsList: "+companion.bluetoothDevice.address)
                val radioButton = RadioButton(this)
                radioButton.text = companion.bluetoothDevice.address + " - " + companion.bluetoothDevice.name
                radioButton.id = terminalCounter

                if (companion.isActivated) {
                    bFound = true
                    radioButton.isChecked = true
                    mCurrentDevice = companion.bluetoothDevice.address + " - " + companion.bluetoothDevice.name
                } else {
                    radioButton.isChecked = false
                }
                mRadioGroup.addView(radioButton)
                terminalCounter++
            }
        }

        val usbDevices = mPclUtil.usbDevices
        if (usbDevices != null && usbDevices.size > 0) {
            for (device in usbDevices) {
                Log.d(TAG, "refreshTerminalsList: "+device.toString())
                if (!mUsbManager!!.hasPermission(device) && !mPermissionRequested && !mPermissionGranted) {
                    Log.d(TAG, "refreshTerminalsList: requestPermission")
                    mPermissionRequested = true
                    mUsbManager?.requestPermission(device, mPermissionIntent)
                } else {
                    val companion = mPclUtil.getUsbCompanion(device)

                    if (companion != null) {
                        val radioButton = RadioButton(this)
                        radioButton.text = companion.name
                        radioButton.id = terminalCounter

                        if (companion.isActivated) {
                            bFound = true
                            radioButton.isChecked = true
                            mCurrentDevice = companion.name
                        } else {
                            radioButton.isChecked = false
                        }
                        mRadioGroup.addView(radioButton)
                        terminalCounter++
                    } else {
                        Log.d(TAG, "refreshTerminalsList: getUsbCompanion returns null")
                    }
                }
            }
        }

        val serialDevices = mPclUtil.serialPortDevices
        if (serialDevices != null && serialDevices.size > 0) {
            val serialPortAutho = arrayOf("/dev/ttyMT0", "/dev/ttyHSL1", "/dev/ttyS3")
            for (device in serialDevices) {
                // Test if our serial port terminal
                for(serialPort in serialPortAutho) {
                    if (device == serialPort) {
                        var tmpStr = ""

                        if (mCurrentDevice == null) {
                            tmpStr = mPclUtil.activatedCompanion
                        } else {
                            tmpStr = mCurrentDevice.toString()
                        }

                        if (tmpStr.startsWith(serialPort)) {
                            val radioButton = RadioButton(this)
                            radioButton.text = tmpStr
                            radioButton.id = terminalCounter
                            bFound = true
                            radioButton.isChecked = true
                            mRadioGroup.addView(radioButton)
                            terminalCounter++
                        } else {
                            val companion = mPclUtil.getSerialPortCompanion(device)

                            if (companion != null) {
                                val radioButton = RadioButton(this)
                                radioButton.text = companion.toString()
                                radioButton.id = terminalCounter

                                if (companion.isActivated) {
                                    bFound = true
                                    radioButton.isChecked = true
                                    mCurrentDevice = companion.toString()
                                } else {
                                    radioButton.isChecked = false
                                }
                                mRadioGroup.addView(radioButton)
                                terminalCounter++
                                companion.serialPortDevice.close()
                            }
                        }
                        break
                    }
                }
            }
        } else {
            Log.d(TAG, "refreshTerminalsList: getSerialPortCompanions returns null or empty list")
        }

        terminals.clear()

        if (!bFound) {
            val activatedCompanion = mPclUtil.activatedCompanion

            if (activatedCompanion != null && !activatedCompanion.isEmpty() && activatedCompanion[2] != ':' && !activatedCompanion.contains("_")) {
                val radioButton = RadioButton(this)
                radioButton.text = activatedCompanion
                radioButton.id = terminalCounter
                radioButton.isChecked = true
                mCurrentDevice = activatedCompanion
                mRadioGroup.addView(radioButton)
                terminalCounter++
            } else if (activatedCompanion != null && !activatedCompanion.isEmpty() && activatedCompanion.contains("_")) {
                val terminalIp = activatedCompanion.split("_")

                if (!terminalIp[1].equals("255.255.255.255")) {
                    val radioButton = RadioButton(this)
                    radioButton.text = terminalIp[2] + " - " + terminalIp[0]
                    radioButton.id = terminalCounter
                    radioButton.tag = "ip"
                    radioButton.isChecked = true

                    sslActivated = terminalIp[3].equals("1")
                    mCurrentDevice = activatedCompanion
                    mRadioGroup.addView(radioButton)

                    val terminal = mPclUtil.IpTerminal(terminalIp[0], terminalIp[2], terminalIp[1], sslActivated.toInt())
                    terminal.activate()
                    terminals.add(terminal)
                    terminalCounter++
                }
            }
        }

        val task = GetIpTerminalsTask(this)
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)

        //mProgressBar.visibility = View.INVISIBLE
        Log.d(TAG, "END refreshTerminalsList: "+string)
    }

    internal inner class RestartServiceTask : AsyncTask<Void, Void, Boolean>() {
        override fun doInBackground(vararg tmp: Void): Boolean? {
            mPclService?.disconnectPcl()
            mPclService?.connectPcl()
            return true
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
            Log.d(CommonActivity.TAG, "WelcomeActivity: stopPclService")
            val i = Intent(this, PclService::class.java)
            if (applicationContext.stopService(i))
                mServiceStarted = false
        }
    }

    private inner class GetIpTerminalsTask(internal var context: Context) : AsyncTask<Any, IpTerminal, String>() {

        override fun doInBackground(vararg params: Any): String? {
            var bExist: Boolean
            for (i in 0..3) {
                val terminalsRetrived = mPclUtil.ipTerminals
                if (terminalsRetrived != null) {
                    for (term1 in terminalsRetrived) {
                        bExist = false
                        for (term2 in terminals) {
                            if (term1.mac == term2.mac) {
                                bExist = true
                                break
                            }
                        }
                        if (!bExist) {
                            terminals.add(term1)
                            publishProgress(term1)
                        }
                    }
                }

            }
            return null
        }

        override fun onProgressUpdate(vararg values: IpTerminal) {

            var activated = ""
            val term = values[0]

            if (mCurrentDevice != null) {
                val activatedtab = mCurrentDevice.toString().split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (activatedtab.size > 2)
                    activated = activatedtab[2] + " - " + activatedtab[0]
            }
            Log.d(TAG, "GetIpTerminals: activated=" + activated)


            Log.d(TAG, "GetIpTerminals: term=" + term.mac + " - " + term.name)
            if (activated != term.mac + " - " + term.name) {
                val radioButton = RadioButton(context)
                radioButton.text = term.mac + " - " + term.name
                radioButton.id = terminalCounter
                radioButton.tag = "ip"
                if (term.isActivated) {
                    radioButton.isChecked = true
                    mCurrentDevice = term.mac + " - " + term.name
                    val terminalIp = mPclUtil!!.getActivatedCompanion().split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    sslActivated = if (terminalIp[3] === "1") true else false
                    //mcbActivateSsl.setChecked(sslActivated)
                }
                mRadioGroup.addView(radioButton)
                terminalCounter++
            }
        }
    }

    private val mUsbReceiver = object : BroadcastReceiver() {

        @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice
                    if (PclUtilities.isIngenicoUsbDevice(device)) {
                        refreshTerminalsList("deviceAttached")
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice
                    if (device != null) {
                        if (PclUtilities.isIngenicoUsbDevice(device)) {
                            refreshTerminalsList("deviceDetached")
                        }
                    }
                }
            } else if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice
                    if (PclUtilities.isIngenicoUsbDevice(device)) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.i(TAG, "Permission granted for device\n" + device)
                            refreshTerminalsList("deviceGranted")
                            mPermissionGranted = true
                        } else {
                            Log.w(TAG, "Permission refused for device\n" + device)
                        }
                        mPermissionRequested = false
                    }

                }
            }
        }
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? {
        val cs: CharSequence
        val id = mRadioGroup.checkedRadioButtonId
        cs = if (id == -1) "" else (mRadioGroup.getChildAt(id) as RadioButton).text
        return cs
    }

    override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
//        findViewById(R.id.button_unitary_test).setEnabled(true)
//        findViewById(R.id.button_loop_test).setEnabled(true)
            Log.d(TAG, String.format("onCheckedChanged id=%d", checkedId))
            if (checkedId != -1) {
                var rb = group.getChildAt(checkedId)
                if (rb != null) {
                    rb as RadioButton
                    Log.d(TAG, String.format("onCheckedChanged id=%d text=%s tmpName=%s", checkedId, rb.text, mCurrentDevice))
                    if (rb.text != mCurrentDevice) {
                        Log.d(TAG, String.format("current:%s saved:%s", rb.text, mCurrentDevice))

                        mCurrentDevice = rb.text
//                    if (mCurrentDevice.toString() == getString(R.string.use_direct_connect)) {
//                        val terminal = mPclUtil.IpTerminal("", "", "255.255.255.255", 0)
//
//                        if (mcbActivateSsl.isChecked()) {
//                            terminal.ssl = 1
//                        } else {
//                            terminal.ssl = 0
//                        }
//
//                        mPclUtil.activateIpTerminal(terminal)
//                    } else
                        if (rb.tag != null && rb.tag.toString().equals("ip", ignoreCase = true)) {
                            val terminalNameTab = mCurrentDevice.toString().split(" - ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                            if (terminalNameTab.size > 0) {

                                for (terminal in terminals) {
                                    if (terminal.name.equals(terminalNameTab[1].trim { it <= ' ' }, ignoreCase = true)) {
//                                    if (mcbActivateSsl.isChecked()) {
//                                        terminal.ssl = 1
//                                    }
                                        val result = mPclUtil.activateIpTerminal(terminal)
                                        Log.d(TAG, "activateIpTerminal: " + result.toString())
                                    } else {
                                        terminal.isActivated = false
                                    }
                                }

                            }
                        } else if (mCurrentDevice?.get(2) == ':') {
                            mPclUtil.ActivateCompanion((mCurrentDevice as String).substring(0, 17))
                        } else if ((mCurrentDevice as String).startsWith("/dev/")) {
                            val terminalNameTab = mCurrentDevice.toString().split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                            if (terminalNameTab.size == 2) {
                                Log.d(TAG, "Activate SerialPortCompanion" + mCurrentDevice)
                                mPclUtil.activateSerialPortCompanion(terminalNameTab[0], terminalNameTab[1])
                            }
                        } else {
                            mPclUtil.activateUsbCompanion(mCurrentDevice as String)
                        }

                        // Restart the service
                        Log.d(TAG, "Restart PCL Service")
                        val restartTask = RestartServiceTask()
                        restartTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                    } else {
                        Log.d(TAG, "onCheckedChanged Allready selected")
                    }
                }
            }
            Log.d(TAG, "onCheckedChanged EXIT")
    }

    companion object {

        private val TAG = "PCL_FILE_SHARING_SAMPLE"
        private val ACTION_USB_PERMISSION = "com.ingenico.pclfilesharingsample.USB_PERMISSION"
    }

    fun Boolean.toInt() = if (this) 1 else 0

}

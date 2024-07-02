package com.ingenico.pcltestappwithlib;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.ingenico.pclservice.PclService;
import com.ingenico.pclutilities.PclLog;
import com.ingenico.pclutilities.PclUtilities;
import com.ingenico.pclutilities.PclUtilities.BluetoothCompanion;
import com.ingenico.pclutilities.PclUtilities.IpTerminal;
import com.ingenico.pclutilities.PclUtilities.SerialPortCompanion;
import com.ingenico.pclutilities.PclUtilities.UsbCompanion;
import com.ingenico.pclutilities.SslObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WelcomeActivity extends CommonActivity implements OnClickListener, OnCheckedChangeListener {

	private static final String TAG = "PCLTESTAPP";
	private RadioGroup mRadioGroup;
	private PclUtilities mPclUtil;
	private int terminalCounter = 0;
	private Set<IpTerminal> terminals = new HashSet<IpTerminal>();

    TextView mtvState;
    TextView mtvSsl;
	TextView mtvSerialNumber;
	CharSequence mCurrentDevice;
	TextView mtvAddonVersion;
	CheckBox mcbEnableLog;
	CheckBox mcbActivateSsl;
	CheckBox mcbDisableBtFilter;
	EditText filterEt;
	Button filterBtn;
	String filter = null;
	
	UsbManager mUsbManager = null;
	PendingIntent mPermissionIntent = null;
	private boolean mPermissionRequested = false;
	private boolean mPermissionGranted = false;
	private static final String ACTION_USB_PERMISSION = "com.ingenico.pcltestappwithlib.USB_PERMISSION";
	Handler mIpTerminalHandler;
	
	private boolean mRestart;
	
	static class PclObject {
		PclServiceConnection serviceConnection;
		PclService service;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		TextView tvAppVersion;
		TextView tvAppDemoVersion;
		TextView tvBuildDate;
		super.onCreate(savedInstanceState);
		Log.d(TAG, "WelcomeActivity start");
		setContentView(R.layout.welcome);
		
		findViewById(R.id.button_unitary_test).setOnClickListener(this);
		findViewById(R.id.button_easy_pairing).setOnClickListener(this);
		findViewById(R.id.button_loop_test).setOnClickListener(this);
		findViewById(R.id.button_unitary_test).setEnabled(false);
		findViewById(R.id.button_loop_test).setEnabled(true);
		mRadioGroup=findViewById(R.id.optionRadioGroup);
		mtvState = findViewById(R.id.tvState);
		mtvSsl = findViewById(R.id.tvSsl);
		mtvSerialNumber = findViewById(R.id.tvSerialNumber);
		tvAppVersion = findViewById(R.id.tvAppVersion);
		try {
			tvAppVersion.setText(getString(R.string.app_version) + getPackageManager().getPackageInfo(getPackageName(), 0 ).versionName);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		tvBuildDate = findViewById(R.id.tvBuildDate);
		tvBuildDate.setText(getString(R.string.build_date) + BuildConfig.BUILD_TIME);

		tvAppDemoVersion = findViewById(R.id.tvAppDemoVersion);
		tvAppDemoVersion.setText(getString(R.string.app_is_test_version) + BuildConfig.isTestVersion.toString());

		mtvAddonVersion = (TextView)findViewById(R.id.tvAddonVersion);
		
		mcbEnableLog = (CheckBox)findViewById(R.id.cbEnableLog);
		SharedPreferences settings = getSharedPreferences("PCLSERVICE", MODE_PRIVATE);
    	boolean enableLog = settings.getBoolean("ENABLE_LOG", true);
		mcbEnableLog.setChecked(enableLog);
		mcbEnableLog.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				SharedPreferences settings = getSharedPreferences("PCLSERVICE", MODE_PRIVATE);
				Editor editor = settings.edit();
				boolean isChecked = mcbEnableLog.isChecked();

				editor.putBoolean("ENABLE_LOG", isChecked);
				editor.commit();
				if (mPclService != null) {
					mPclService.enableDebugLog(isChecked);
				}
			}
		});

		sslActivated = settings.getBoolean("ENABLE_SSL", false);
		mcbActivateSsl = (CheckBox)findViewById(R.id.cbActivateSsl);
    	mcbActivateSsl.setChecked(sslActivated);
    	mcbActivateSsl.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				SharedPreferences settings = getSharedPreferences("PCLSERVICE", MODE_PRIVATE);
				Editor editor = settings.edit();

				mtvSsl.setText("");
				mtvSsl.setBackgroundColor(Color.TRANSPARENT);
				mtvSsl.setTextColor(Color.TRANSPARENT);

				sslActivated = mcbActivateSsl.isChecked();
				editor.putBoolean("ENABLE_SSL", sslActivated);
				editor.commit();
				
				// Update IP terminal
				for(IpTerminal terminal: terminals){
					if (terminal.isActivated())
					{
						if(mcbActivateSsl.isChecked()){
							terminal.setSsl(1);
						}
						else{
							terminal.setSsl(0);
						}
						// Disconnect and connect service
						disconnectPcl();
						mPclUtil.activateIpTerminal(terminal);
						connectPcl();
						Log.d(TAG, "onSSLChanged => INIT");
						break;
					}
				}
			}
		});

		btFilterDisabled = settings.getBoolean("DISABLE_BT_FILTER", false);
		mcbDisableBtFilter = (CheckBox)findViewById(R.id.cbDisableBtFilter);
		mcbDisableBtFilter.setChecked(btFilterDisabled);
		mcbDisableBtFilter.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				SharedPreferences settings = getSharedPreferences("PCLSERVICE", MODE_PRIVATE);
				Editor editor = settings.edit();
				btFilterDisabled = mcbDisableBtFilter.isChecked();

				editor.putBoolean("DISABLE_BT_FILTER", btFilterDisabled);
				editor.commit();
			}
		});
					
		filterEt = (EditText) findViewById(R.id.filterText);

		filterBtn = (Button) findViewById(R.id.filterBtn);
		filterBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				filter = filterEt.getText().toString();
				if (filter.isEmpty()) filter = null;
				refreshTerminalsList("Filtering", filter);
			}
		});
		
		mPclUtil = new PclUtilities(this, BuildConfig.APPLICATION_ID, "pairing_addr.txt");
		mPclUtil.AddAuthorizedVidPid(0x0001, 0x0001); // AddAuthorizedVidPid sample

		BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		if((mPclUtil != null)&&(btAdapter != null)) {
			Button button = (Button) findViewById(R.id.button_easy_pairing);
			button.setVisibility(View.VISIBLE);
		}

		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		if (mUsbManager != null) {
			mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
			IntentFilter usbFilter = new IntentFilter(ACTION_USB_PERMISSION);
			usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
			usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
			registerReceiver(mUsbReceiver, usbFilter);
		}

		final Context context = this;
		mIpTerminalHandler = new Handler(getMainLooper(), new Handler.Callback() {
			@Override
			public boolean handleMessage(Message msg) {
				if(msg == null) return false;
				Bundle data = msg.getData();
				if(data == null) return false;
				IpTerminal device = (IpTerminal)data.getSerializable(PclUtilities.IPTERMINAL_MSG_KEY);
				boolean bExist = false;
				for (IpTerminal term: terminals)
				{
					if(device.getMac().equals(term.getMac()))
					{
						bExist = true;
						break;
					}
				}
				if (!bExist)
				{
					terminals.add(device);
					String activated = "";

					if(mCurrentDevice != null){
						String[] activatedtab = mCurrentDevice.toString().split("_");
						if(activatedtab.length > 2)
							activated = activatedtab[2] + " - " + activatedtab[0];
					}
					Log.d(TAG, "GetIpTerminals: activated=" + activated);

					String termString = device.getMac() + " - " + device.getName();
					Log.d(TAG, "GetIpTerminals: term=" + termString);
					if(!activated.equals(termString)){
						if (device.isActivated()) {
							mcbActivateSsl.setChecked(device.getSsl() == 1);
							mCurrentDevice = termString;
						}
						if(shouldCompanionBeDisplayed(termString, filter)) {
							RadioButton radioButton = new RadioButton(context);
							radioButton.setText(termString);
							radioButton.setId(terminalCounter);
							radioButton.setTag("ip");
							radioButton.setChecked(device.isActivated());
							mRadioGroup.addView(radioButton);
							terminalCounter++;
						}
					}
				}
				return false;
			}
		});
	}

	@Override
	protected void onResume() {
		refreshTerminalsList("onResume", filter);
		
		if (mRadioGroup.getCheckedRadioButtonId() == -1)
		{
			// Do not restart PclService when no companion is activated
			mRestart = false;
		}
		else
		{
			mRestart = true;
		}
		
		// Start PclService even if no companion is selected - to test TPCL-750
		mRadioGroup.setOnCheckedChangeListener(this);
		findViewById(R.id.button_unitary_test).setEnabled(true);
		findViewById(R.id.button_loop_test).setEnabled(true);

		if(checkAndRequestPermissions()) {
			startPclService();
			initService();
		}
		
		mReleaseService = 1;
		
		if (isCompanionConnected())
        {
			new GetTermInfoTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        	mtvState.setText(R.string.str_connected);
        	mtvState.setBackgroundColor(Color.GREEN);
        	mtvState.setTextColor(Color.BLACK);
        }
        else
        {
        	mtvState.setText(R.string.str_not_connected);
        	mtvState.setBackgroundColor(Color.RED);
        	mtvState.setTextColor(Color.BLACK);
        }

		if(mtvSsl != null)
		{
			if (isSslCertificateKo()&&sslActivated)
			{
				mtvSsl.setText(R.string.str_ssl_invalid);
				mtvSsl.setBackgroundColor(Color.RED);
				mtvSsl.setTextColor(Color.BLACK);
			}
			else
			{
				mtvSsl.setText("");
				mtvSsl.setBackgroundColor(Color.TRANSPARENT);
				mtvSsl.setTextColor(Color.TRANSPARENT);
			}
		}
		
		if( mPclService != null ) {
			mtvAddonVersion.setText(getString(R.string.addon_version) + mPclService.getAddonVersion());
		}
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		Log.d(TAG, "WelcomeActivity: onDestroy" );
		releaseService();
		if (mReleaseService == 1)
    	{
			stopPclService();
    	}
		if (mUsbReceiver != null) {
			unregisterReceiver(mUsbReceiver);
		}
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		Intent i;
		
		switch (v.getId()) {
		case R.id.button_unitary_test:
			i = new Intent(WelcomeActivity.this, TestListActivity.class);
			startActivity(i);
			break;
		case R.id.button_loop_test:
			i = new Intent(WelcomeActivity.this, PclLoopTestActivity.class);
			startActivity(i);
			break;
		case R.id.button_easy_pairing:
			BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
			if((mPclUtil != null)&&(btAdapter != null)) {
				Intent intent = new Intent(this, EasyPairingActivity.class);
				startActivity(intent);
			}
			else
			{
				Button button = (Button) findViewById(R.id.button_easy_pairing);
				button.setVisibility(View.INVISIBLE);
			}
		}
		
	}

	class GetTermInfoTask extends AsyncTask<Void, Void, Boolean> {
		protected Boolean doInBackground(Void... tmp) {
			Boolean bRet = getTermInfo();
			return bRet;
		}

		protected void onPostExecute(Boolean result) {
			if (result == true)
			{
				mtvSerialNumber.setText(String.format("SN: %08x / PN: %08x", SN, PN));
			}
		}
	}

	class RestartServiceTask extends AsyncTask<Void, Void, Boolean> {
		protected Boolean doInBackground(Void... tmp) {
			disconnectPcl();
			connectPcl();
			return true;
		}
	}

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		findViewById(R.id.button_unitary_test).setEnabled(true);
		findViewById(R.id.button_loop_test).setEnabled(true);

		mtvSsl.setText("");
		mtvSsl.setBackgroundColor(Color.TRANSPARENT);
		mtvSsl.setTextColor(Color.TRANSPARENT);

		Log.d(TAG, String.format("onCheckedChanged id=%d", checkedId));
		if (checkedId != -1) {
			RadioButton rb = (RadioButton) group.getChildAt(checkedId);
			if (rb != null) {
				Log.d(TAG, String.format("onCheckedChanged id=%d text=%s tmpName=%s", checkedId, rb.getText(), mCurrentDevice));
				if (!rb.getText().equals(mCurrentDevice)) {
					Log.d(TAG, String.format("current:%s saved:%s", rb.getText(), mCurrentDevice));
					mRadioGroup.check(rb.getId());
					mCurrentDevice = rb.getText();
					if (mCurrentDevice.toString().equals(getString(R.string.use_direct_connect))) {
						IpTerminal terminal = mPclUtil.new IpTerminal("", "", "255.255.255.255", 0);

						if (mcbActivateSsl.isChecked()) {
							terminal.setSsl(1);
						} else {
							terminal.setSsl(0);
						}

						mPclUtil.activateIpTerminal(terminal);
					} else if (rb.getTag() != null && rb.getTag().toString().equalsIgnoreCase("ip")) {
						String[] terminalNameTab = mCurrentDevice.toString().split(" - ");
						if (terminalNameTab.length > 0) {

							for (IpTerminal terminal : terminals) {

								if (terminal.getName().equalsIgnoreCase(terminalNameTab[1].trim())) {
									if (mcbActivateSsl.isChecked()) {
										terminal.setSsl(1);
									}
									mPclUtil.activateIpTerminal(terminal);
								} else {
									terminal.setActivated(false);
								}
							}

						}
					} else if (mCurrentDevice.charAt(2) == ':') {
						mPclUtil.ActivateCompanion(((String) mCurrentDevice).substring(0, 17),!btFilterDisabled);
					}
					else if (((String) mCurrentDevice).startsWith("/dev/")) {
						String[] terminalNameTab = mCurrentDevice.toString().split(" ");
						if (terminalNameTab.length == 2) {
							Log.d(TAG, "Activate SerialPortCompanion" + mCurrentDevice);
							mPclUtil.activateSerialPortCompanion(terminalNameTab[0], terminalNameTab[1]);
						}
					} else {
						mPclUtil.activateUsbCompanion((String) mCurrentDevice);
					}

					// Restart the service
					if (mRestart) {
						Log.d(TAG,"Restart PCL Service");
						RestartServiceTask restartTask = new RestartServiceTask();
						restartTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
				} else {
					Log.d(TAG, "onCheckedChanged Allready selected");
				}
			}
			mRestart = true;
		}
		Log.d(TAG, "onCheckedChanged EXIT");
	}
	
	@Override
	public void onStateChanged(String state) {
		if (state.equals("CONNECTED"))
		{
			new GetTermInfoTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			mtvState.setText(R.string.str_connected);
			mtvState.setBackgroundColor(Color.GREEN);
			mtvState.setTextColor(Color.BLACK);
		}
		else
		{
			mtvState.setText(R.string.str_not_connected);
			mtvState.setBackgroundColor(Color.RED);
			mtvState.setTextColor(Color.BLACK);
			mtvSerialNumber.setText("");
		}
	}

	@Override
	public void onSslStateChanged(boolean state) {
		if(mtvSsl != null)
		{
			if (!state&&sslActivated)
			{
				mtvSsl.setText(R.string.str_ssl_invalid);
				mtvSsl.setBackgroundColor(Color.RED);
				mtvSsl.setTextColor(Color.BLACK);
			}
			else
			{
				mtvSsl.setText("");
				mtvSsl.setBackgroundColor(Color.TRANSPARENT);
				mtvSsl.setTextColor(Color.TRANSPARENT);
			}
		}
	}

	@Override
	void onPclServiceConnected() {
		Log.d(TAG, "onPclServiceConnected");
		mPclService.addDynamicBridgeLocal(6000, 0);
		mPclService.setexchangeWaitTime1us(1000);
		
		if (isCompanionConnected())
        {
			new GetTermInfoTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			if (mtvState != null) {
	        	mtvState.setText(R.string.str_connected);
	        	mtvState.setBackgroundColor(Color.GREEN);
	        	mtvState.setTextColor(Color.BLACK);
			}
        }
        else
        {
        	if (mtvState != null) {
	        	mtvState.setText(R.string.str_not_connected);
	        	mtvState.setBackgroundColor(Color.RED);
	        	mtvState.setTextColor(Color.BLACK);
        	}
        }
		
		if( mPclService != null ) {
			if (mtvAddonVersion != null) {
				mtvAddonVersion.setText(getString(R.string.addon_version) + mPclService.getAddonVersion());
			}
		}
	}

	private void startPclService() {
		
		if (!mServiceStarted)
		{
			Log.d(TAG,"startPclService");
			SharedPreferences settings = getSharedPreferences("PCLSERVICE", MODE_PRIVATE);
	    	boolean enableLog = settings.getBoolean("ENABLE_LOG", true);
			Intent i = new Intent(this, PclService.class);
			i.putExtra("PACKAGE_NAME", BuildConfig.APPLICATION_ID);
			i.putExtra("FILE_NAME", "pairing_addr.txt");
			i.putExtra("ENABLE_LOG", enableLog);

			if (checkStoragePermissions()) {
				i.putExtra("LOGGER_HOME_DIR", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
			}

			SslObject sslKeyStore = new SslObject(getPackageName(),KEYSTORE_NAME, "ingenico");
			i.putExtra("SSL_KEYSTORE", sslKeyStore);

			if (getApplicationContext().startService(i) != null)
				mServiceStarted = true;
		}
	}
    
    private void stopPclService() {
		if (mServiceStarted)
		{
			Log.d(TAG, "WelcomeActivity: stopPclService" );
			Intent i = new Intent(this, PclService.class);
			if (getApplicationContext().stopService(i))
				mServiceStarted = false;
		}
	}

	void showDeniedPermissionAlert(ArrayList<String> permissions){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("ERROR: Can't start PCL service\nMissing permissions :\n" + permissions.toString());
		builder.setCancelable(true);
		builder.create().show();
	}

	protected static final String[] mPclServicePermissions =  {Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN};
	boolean checkAndRequestPermissions(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			PclLog.v(TAG,"checkAndRequestPermissions");
			final ArrayList<String> permissionsToGrant = new ArrayList<>();
			for(String permission : mPclServicePermissions){
				if(checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED)
					permissionsToGrant.add(permission);
			}
			if(permissionsToGrant.size() > 0) {
				PclLog.v(TAG,"checkAndRequestPermissions request");
				ActivityResultLauncher<String[]> requestMultiplePermissions = registerForActivityResult(
						new ActivityResultContracts.RequestMultiplePermissions(),
						new ActivityResultCallback<Map<String, Boolean>>() {
							@Override
							public void onActivityResult(java.util.Map<String, Boolean> result) {
								ArrayList<String> permissionsDenied = new ArrayList<>();
								for(Map.Entry<String, Boolean> entry : result.entrySet()){
									if(!entry.getValue())
										permissionsDenied.add(entry.getKey());
								}
								if(permissionsDenied.size() > 0){
									showDeniedPermissionAlert(permissionsDenied);
								}else{
									startPclService();
									initService();
								}
							}
						});
				requestMultiplePermissions.launch(mPclServicePermissions);
				return false;
			}
			PclLog.v(TAG,"checkAndRequestPermissions granted");
		}
		return true;
	}

	@Override
	public void onBarCodeReceived(String barCodeValue, int symbology) {
		// Do nothing		
	}

	@Override
	public void onBarCodeClosed() {
		// Do nothing		
	}
	
	void refreshTerminalsList(String string, String filterString) {
		synchronized (mRadioGroup) {
			Log.d(TAG, "refreshTerminalsList: " + string);
			mRadioGroup.removeAllViewsInLayout();
			boolean bFound = false;

			terminalCounter = 0;

			// Add button for IP direct connect
			RadioButton directIpRadioButton = new RadioButton(this);
			directIpRadioButton.setText(getString(R.string.use_direct_connect));
			directIpRadioButton.setId(terminalCounter);
			if (mCurrentDevice != null && mCurrentDevice.toString().equals(getString(R.string.use_direct_connect)))
			{
				bFound = true;
				directIpRadioButton.setChecked(true);
			}
			mRadioGroup.addView(directIpRadioButton);
			terminalCounter++;

			Set<BluetoothCompanion> btComps = mPclUtil.GetPairedCompanions(!btFilterDisabled);
			if (btComps != null && (btComps.size() > 0)) {
				// Loop through paired devices
				for (BluetoothCompanion comp : btComps) {
					String btDevice = comp.getBluetoothDevice().getAddress() + " - " + comp.getBluetoothDevice().getName();
					Log.d(TAG, comp.getBluetoothDevice().getAddress());
					if (comp.isActivated()) {
						bFound = true;
						mCurrentDevice = btDevice;
					}
					if(shouldCompanionBeDisplayed(btDevice, filterString)) {
						RadioButton radioButton = new RadioButton(this);
						radioButton.setText(btDevice);
						radioButton.setId(terminalCounter);
						radioButton.setChecked(comp.isActivated());
						mRadioGroup.addView(radioButton);
						terminalCounter++;
					}
				}
			}

			Set<UsbDevice> usbDevs = mPclUtil.getUsbDevices();
			if (usbDevs != null && (usbDevs.size() > 0)) {
				for (UsbDevice dev : usbDevs) {
					Log.d(TAG, "refreshTerminalsList:" + dev.toString());
					if (!mUsbManager.hasPermission(dev) && !mPermissionRequested && !mPermissionGranted) {
						Log.d(TAG, "refreshTerminalsList: requestPermission");
						mPermissionRequested = true;
						mUsbManager.requestPermission(dev, mPermissionIntent);
					} else {
						UsbCompanion comp = mPclUtil.getUsbCompanion(dev);
						if (comp != null && !comp.getName().isEmpty()) {
							if (comp.isActivated()) {
								bFound = true;
								mCurrentDevice = comp.getName();
							}
							if(shouldCompanionBeDisplayed(comp.getName(), filterString)) {
								RadioButton radioButton = new RadioButton(this);
								radioButton.setText(comp.getName());
								radioButton.setId(terminalCounter);
								radioButton.setChecked(comp.isActivated());
								mRadioGroup.addView(radioButton);
								terminalCounter++;
							}
						} else {
							Log.d(TAG, "refreshTerminalsList: getUsbCompanion returns null");
						}
					}
				}
			}

			Set<String> serialDevices = mPclUtil.getSerialPortDevices();
			if (serialDevices != null && (serialDevices.size() > 0)) {
				// Caspit => ttyS3, ttyHSL1; famoco => ttyMT0
				String serialPortAutho[] = {"/dev/ttyMT0", "/dev/ttyHSL1", "/dev/ttyS3" };
				for (String fileDev : serialDevices) {
					// Test if our serial port terminal
					for(String serialPort : serialPortAutho) {
						if (fileDev.equals(serialPort)) {
							// Test if already selected companion
							if (mCurrentDevice != null && mCurrentDevice.toString().startsWith(serialPort)) {
								bFound = true;
								if (shouldCompanionBeDisplayed(mCurrentDevice.toString(), filterString)) {
									RadioButton radioButton = new RadioButton(this);
									radioButton.setText(mCurrentDevice);
									radioButton.setId(terminalCounter);
									radioButton.setChecked(true);
									mRadioGroup.addView(radioButton);
									terminalCounter++;
								}
							} else {
								SerialPortCompanion comp = mPclUtil.getSerialPortCompanion(fileDev);
								if (comp != null && !comp.getName().isEmpty()) {
									if (comp.isActivated()) {
										bFound = true;
										mCurrentDevice = comp.toString();
									}
									if (shouldCompanionBeDisplayed(comp.toString(), filterString)) {
										RadioButton radioButton = new RadioButton(this);
										radioButton.setText(comp.toString());
										radioButton.setId(terminalCounter);
										radioButton.setChecked(comp.isActivated());
										mRadioGroup.addView(radioButton);
										terminalCounter++;
									}
									comp.getSerialPortDevice().close();
								}
							}
							break;
						}
					}
				}
			} else {
				Log.d(TAG, "refreshTerminalsList: getSerialPortCompanions returns null or empty list");
			}

			terminals.clear();

			if (!bFound) {
				String act = mPclUtil.getActivatedCompanion();
				if(act != null && !act.isEmpty()) {
					if (act.charAt(2) != ':' && !act.contains("_")) {
						mCurrentDevice = act;
						if(shouldCompanionBeDisplayed(act,filterString)) {
							RadioButton radioButton = new RadioButton(this);
							radioButton.setText(act);
							radioButton.setId(terminalCounter);
							radioButton.setChecked(true);
							mRadioGroup.addView(radioButton);
							terminalCounter++;
						}
					} else if (act.contains("_")) {
						String[] terminalIp = act.split("_");
						if (!terminalIp[1].equals("255.255.255.255")) {
							sslActivated = terminalIp[3].equals("1");
							mcbActivateSsl.setChecked(sslActivated);
							mCurrentDevice = act;
							IpTerminal term = mPclUtil.new IpTerminal(terminalIp[0], terminalIp[2], terminalIp[1], sslActivated ? 1 : 0);
							term.activate();
							terminals.add(term);
							String ipText = terminalIp[2] + " - " + terminalIp[0];
							if(shouldCompanionBeDisplayed(ipText,filterString)) {
								RadioButton radioButton = new RadioButton(this);
								radioButton.setText(ipText);
								radioButton.setId(terminalCounter);
								radioButton.setTag("ip");
								radioButton.setChecked(true);
								mRadioGroup.addView(radioButton);
								terminalCounter++;
							}
						} else {
							((RadioButton) mRadioGroup.getChildAt(0)).setChecked(true);
							mCurrentDevice = getString(R.string.use_direct_connect);
						}
					}
				}
			}

			new GetIpTerminalsTask().start();

			Log.d(TAG, "END refreshTerminalsList: " + string);
		}
	}

	private class GetIpTerminalsTask extends Thread {

		@Override
		public void run() {
        	try {
				for (int i = 0; i < 4; i++) {
					if(mPclUtil != null)
						mPclUtil.getIPTerminals(mIpTerminalHandler);
				}
			}catch (Exception e){
        		if(e instanceof InterruptedException)
        			interrupt();
			}
        }
    }
	
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

	    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
		public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
	        	synchronized (this) {
		        	UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		        	if (device != null) {			        	
		        		if (PclUtilities.isIngenicoUsbDevice(device)) {
				        	refreshTerminalsList("deviceAttached", filter);
			        	}
		        	}
	        	}
	        }
	        else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
	        	synchronized (this) {
		        	UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		        	if (device != null) {
			        	if (PclUtilities.isIngenicoUsbDevice(device)) {
				        	refreshTerminalsList("deviceDetached", filter);
			        	}
		        	}
	        	}
	        }
	        else if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) == true) {
						Log.i(TAG, "Permission granted");
						refreshTerminalsList("deviceGranted", filter);
						mPermissionGranted = true;
					}
					else {
						Log.w(TAG, "Permission refused");
					}
					mPermissionRequested = false;
				}
			}
	    }
	};

	/**
	 *  Check whether the input String contains any Strings contained in filters.
	 *  The filters' Strings should be seperated by semicolons.
	 *  If filters is null or empty, true will be returned.
	 */
	private boolean shouldCompanionBeDisplayed(String companionString, String filters) {
		// If there are no filters, show everything
		if (filters == null || filters.trim().isEmpty()) return true;
		// Else, check if filters contains a substring of companionString
		String[] filterArray = filters.split(";");
		for (String item : filterArray) {
			if (!item.trim().isEmpty() && companionString.contains(item.trim())) return true;
		}
		return false;
	}
}

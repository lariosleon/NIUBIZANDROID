package com.ingenico.pcltestappwithlib;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.ingenico.pclservice.PclService;
import com.ingenico.pclutilities.PclLog;
import com.ingenico.pclutilities.SslObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Map;

public class SplashActivity extends CommonActivity {

    /** Duration of wait **/
    private final int SPLASH_DISPLAY_LENGTH = 3000;
    /**
     * The thread to process splash screen events
     */
    private Thread mSplashThread; 

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.splash);

        Log.d(TAG, "SplashActivity onCreate");
        if(BuildConfig.isTestVersion && (BuildConfig.LimitTimeUsed < System.currentTimeMillis())){
            Log.d(TAG, "Test version out of date");
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_LIGHT);
            alertDialogBuilder.setTitle("PCL Test Version");
            alertDialogBuilder
                    .setMessage("You try to use a PCL test version after the deadline!")
                    .setCancelable(false)
                    .setPositiveButton("Close",new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            // if this button is clicked, close
                            // current activity
                            SplashActivity.this.finish();
                        }
                    });

            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return;
        }

        if(checkAndRequestPermissions()) {
            startPclService();
            initService();
        }
        // The thread to wait for splash screen events
        mSplashThread =  new SplashThread();
        mSplashThread.start();
    }

    private class SplashThread extends Thread{
        @Override
        public void run(){
            try {
                synchronized(this){
                    // Wait given period of time or exit on touch
                    this.wait(SPLASH_DISPLAY_LENGTH);
                    Log.d(TAG, "SplashActivity start");
                }
            }
            catch(InterruptedException ex){
            }

            finish();

            // Run next activity
            Intent intent = new Intent();
            intent.setClass(SplashActivity.this, WelcomeActivity.class);
            startActivity(intent);

        }
    }

	@Override
	protected void onDestroy() {
		releaseService();
		super.onDestroy();
	}


    private boolean getFolderSSLcertificate(String fileName, String fileInitName) {
        File sslFolder = new File(getExternalFilesDir(""),"SSL");
        if(sslFolder.exists())
        {
            File sslFile = new File(sslFolder,fileInitName);
            if(sslFile.exists() && sslFile.canRead())
            {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(sslFile);
                    copyToPackageContext(fileName,fis);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    if(fis != null)
                    {
                        try {
                            fis.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Log.d(TAG,"getFolderSSLcertificate "+fileInitName+" to "+fileName);
                return true;
            }
        }
        return false;
    }

    private void getAssetSSLcertificate(String fileName, String fileInitName) {
        InputStream is = null;
        try{
            is = getAssets().open(fileInitName);
            copyToPackageContext(fileName,is);
            Log.d(TAG,"getAssetSSLcertificate "+fileInitName+" to "+fileName);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void copyToPackageContext(String fileName, InputStream content){
        FileOutputStream fos = null;
        long result = 0;
        try {
            ReadableByteChannel rbc = Channels.newChannel(content);
            fos = createPackageContext(getPackageName(), 0).openFileOutput(fileName, Context.MODE_PRIVATE);

            result = fos.getChannel().transferFrom(rbc,0, content.available());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } finally {
            if(fos != null)
            {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.d(TAG, String.format("getAssetSSLcertificate %s - size : %d",fileName,result));
    }

    private void startPclService() {

        if (!mServiceStarted)
        {
            SharedPreferences settings = getSharedPreferences("PCLSERVICE", MODE_PRIVATE);
            boolean enableLog = settings.getBoolean("ENABLE_LOG", true);
            Intent i = new Intent(this, PclService.class);
            i.putExtra("PACKAGE_NAME", BuildConfig.APPLICATION_ID);
            i.putExtra("FILE_NAME", "pairing_addr.txt");
            i.putExtra("ENABLE_LOG", enableLog);

            if(checkStoragePermissions())
            {
                i.putExtra("LOGGER_HOME_DIR", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
            }

            if(!getFolderSSLcertificate(KEYSTORE_NAME, INIT_KEYSTORE_NAME)) {
                getAssetSSLcertificate(KEYSTORE_NAME, INIT_KEYSTORE_NAME);
            }
            SslObject sslKeyStore = new SslObject(getPackageName(),KEYSTORE_NAME, "ingenico");
            i.putExtra("SSL_KEYSTORE", sslKeyStore);
            if (getApplicationContext().startService(i) != null)
                mServiceStarted = true;
        }
    }



    /**
     * Processes splash screen touch events
     */
    @Override
    public boolean onTouchEvent(MotionEvent evt)
    {
        if(evt.getAction() == MotionEvent.ACTION_DOWN)
        {
            synchronized(mSplashThread){
            	mSplashThread.notifyAll();
            }
        }
        return true;
    }

	@Override
	public void onBarCodeReceived(String barCodeValue, int symbology) {
		
	}

	@Override
	public void onBarCodeClosed() {
		
	}

	@Override
	public void onStateChanged(String state) {
		synchronized(mSplashThread){
            mSplashThread.notifyAll();
        }
	}

    @Override
    public void onSslStateChanged(boolean state) {
        synchronized(mSplashThread){
            mSplashThread.notifyAll();
        }
    }

	@Override
	void onPclServiceConnected() {
		Log.d(TAG, "onPclServiceConnected");
		mPclService.addDynamicBridgeLocal(6000, 0);
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
                ActivityResultLauncher<String[]> requestMultiplePermissions = registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        new ActivityResultCallback<Map<String, Boolean>>() {
                            @Override
                            public void onActivityResult(java.util.Map<String, Boolean> result) {
                                ArrayList<String> permissionsDenied = new ArrayList<>();
                                for(Map.Entry<String, Boolean> entry : result.entrySet()){
                                    if(entry.getValue())
                                        permissionsDenied.add(entry.getKey());
                                }
                                if(permissionsDenied.size() == 0){
                                    startPclService();
                                    initService();
                                }
                            }
                        });
                requestMultiplePermissions.launch(mPclServicePermissions);

                return false;
            }
        }
        return true;
    }
}
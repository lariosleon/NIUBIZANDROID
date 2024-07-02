package com.ingenico.pcltestappwithlib;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.activity.ComponentActivity;

import com.ingenico.pclservice.API_ID;
import com.ingenico.pclservice.PclBinder;
import com.ingenico.pclservice.PclService;
import com.ingenico.pclservice.TmsParam;
import com.ingenico.pclservice.TransactionIn;
import com.ingenico.pclservice.TransactionOut;
import com.ingenico.pclutilities.SslObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

public abstract class CommonActivity extends ComponentActivity implements CommonActivityInterface
{
	public static final String TAG = "PCLTESTAPP";
	public static final String KEYSTORE_NAME = "serverb.p12";
	public static final String INIT_KEYSTORE_NAME = "serverbInit.p12";
	public static final String TRUSTSTORE_NAME = "PCLTESTAPP_CA.CRT";
	public static final String INIT_TRUSTSTORE_NAME = "PCLTESTAPP_CA_INIT.CRT";
	protected PclService mPclService = null;
	private static Boolean m_BarCodeActivated = false;
	private static Boolean m_PrinterActivated = false;
    private BarCodeReceiver m_BarCodeReceiver = null;
    private StateReceiver m_StateReceiver = null;
    private SslReceiver m_SslReceiver = null;
    protected PclServiceConnection mServiceConnection;
	protected int mReleaseService;
	protected boolean mBound = false;
	protected boolean mServiceStarted = false;
	protected static boolean sslActivated = false;
	protected static boolean btFilterDisabled = false;
	
	
	
	class PclServiceConnection implements ServiceConnection
	{
		public void onServiceConnected(ComponentName className, IBinder boundService )
		{
			// We've bound to LocalService, cast the IBinder and get LocalService instance             
			PclBinder binder = (PclBinder) boundService;
    		mPclService = (PclService) binder.getService();
			Log.d(TAG, "onServiceConnected" );
			onPclServiceConnected();
		}

		public void onServiceDisconnected(ComponentName className)
		{
			mPclService = null;
			Log.d(TAG, "onServiceDisconnected" );
		}
	};
    
    int SN, PN;
    
    public class _SYSTEMTIME
	{
		// WORD = UInt16
		public short wYear;
		public short wMonth;
		public short wDayOfWeek;
		public short wDay;
		public short wHour;
		public short wMinute;
		public short wSecond;
		public short wMilliseconds;
	}
	protected _SYSTEMTIME sysTime;

	public class _TimeZone
	{
		public byte[] sName = new byte[10];
		public int	 dDST;
	}
	protected _TimeZone sysTimeZone;

	public CommonActivity() 
	{
		
	}

	@Override
	protected void onResume()
	{
		Log.d(TAG, "CommonActivity: onResume" );
		super.onResume();
        //initService();
		//openBarCode();
		initSslReceiver();
		initBarCodeReceiver();
		initStateReceiver();
	}
	@Override
	protected void onPause()
	{
		Log.d(TAG, "CommonActivity: onPause" );
		super.onPause();
		releaseSslReceiver();
		releaseBarCodeReceiver();
		releaseStateReceiver();
		//closeBarCode();
	}
	
	
	abstract void onPclServiceConnected();

	public boolean checkStoragePermissions()
	{
		boolean ret = true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			List<String> permissions = Arrays.asList(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			Context context = getApplicationContext();
			for(String permission : permissions){
				if(context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
				{
					ret = false;
					break;
				}
			}
		}
		return ret;
	}

    protected void initService() 
	{
		if (!mBound)
    	{
    	    Log.d(TAG, "initService" );
    	    SharedPreferences settings = getSharedPreferences("PCLSERVICE", MODE_PRIVATE);
	    	boolean enableLog = settings.getBoolean("ENABLE_LOG", true);
    	    mServiceConnection = new PclServiceConnection();
		    Intent intent = new Intent(this, PclService.class);
		    intent.putExtra("PACKAGE_NAME", "com.ingenico.pcltestappwithlib");
		    intent.putExtra("FILE_NAME", "pairing_addr.txt");
		    intent.putExtra("ENABLE_LOG", enableLog);

			if(checkStoragePermissions())
			{
				intent.putExtra("LOGGER_HOME_DIR", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
			}

			SslObject sslKeyStore = new SslObject(BuildConfig.APPLICATION_ID,KEYSTORE_NAME, "ingenico");
			intent.putExtra("SSL_KEYSTORE", sslKeyStore);

    	    mBound = bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
	}

	protected void releaseService()
	{
		if (mBound) {
			Log.d(TAG, "releaseService" );
			unbindService(mServiceConnection );
			mBound = false;
		}
	}

	protected boolean connectPcl()
	{
		boolean bRet = false;
		if(mPclService != null)
		{
			bRet = mPclService.connectPcl();
		}

		return bRet;
	}

	protected boolean disconnectPcl()
	{
		boolean bRet = false;
		if(mPclService != null)
		{
			bRet = mPclService.disconnectPcl();
		}
		return bRet;
	}
	
    /** SSL */
	abstract void onSslStateChanged(boolean state);
	
	private void initSslReceiver()
	{
		if(m_SslReceiver == null)
		{
			m_SslReceiver = new SslReceiver(this);
			IntentFilter intentfilter = new IntentFilter(SslObject.ACTION_SSL_NOTIFY);
			registerReceiver(m_SslReceiver, intentfilter);
		}
	}
	private void releaseSslReceiver()
	{
		if(m_SslReceiver != null)
		{
			unregisterReceiver(m_SslReceiver);
			m_SslReceiver = null;
		}
	}
	
    /** BarCode */
    private void initBarCodeReceiver()
    {
    	if(m_BarCodeReceiver == null)
    	{
    		m_BarCodeReceiver = new BarCodeReceiver(this);
	    	IntentFilter intentfilter = new IntentFilter("com.ingenico.pclservice.action.BARCODE_EVENT");
	    	intentfilter.addAction("com.ingenico.pclservice.action.BARCODE_CLOSED");
			registerReceiver(m_BarCodeReceiver, intentfilter);
    	}
    }
    private void releaseBarCodeReceiver()
    {
    	if(m_BarCodeReceiver != null)
    	{
    		unregisterReceiver(m_BarCodeReceiver);
    		m_BarCodeReceiver = null;
    	}
    }
    public Boolean openBarCode()
	{
    	Log.d(TAG, "openBarCode" );
		if((mPclService != null) && !m_BarCodeActivated)
			m_BarCodeActivated = setBarCodeActivation(true);
		
		return m_BarCodeActivated;
	}
	public Boolean closeBarCode()
	{
		Log.d(TAG, "closeBarCode" );
		if((mPclService != null) && m_BarCodeActivated)
			m_BarCodeActivated = !setBarCodeActivation(false);
		
		return m_BarCodeActivated;
	}
	public Boolean reopenBarCode()
	{
		if(m_BarCodeActivated)
			closeBarCode();
		return openBarCode();
	}
	private Boolean setBarCodeActivation(boolean activateBarCode)
	{
		boolean result = false;
		byte array [] = null;
		
		if(mPclService != null)
		{
			array = new byte[1];
			{
				if(activateBarCode)
				{
					result = mPclService.openBarcode(array);
					if (result == true)
					{
						if (array[0] != 0)
							result = false;
					}
				}
				else
				{
					mPclService.closeBarcode(array);
					result = true;
				}
			}
			
		}
		
		return result;
	}
	public boolean openPrinter()
	{
		if((mPclService != null) && !m_PrinterActivated)
			m_PrinterActivated = setPrinterActivation(true);
		
		return m_PrinterActivated;
	}
	public boolean closePrinter()
	{
		if(m_PrinterActivated)
			m_PrinterActivated = !setPrinterActivation(false);
		
		return m_PrinterActivated;
	}
	private boolean setPrinterActivation(boolean activatePrinter)
	{
		boolean result = false;
		byte array [] = null;
		
		if(mPclService != null)
		{
			array = new byte[1];
			{
				if(activatePrinter)
					result = mPclService.openPrinter(array);
				else
					result = mPclService.closePrinter(array);
			}
		}
		
		return result;
	}
	public boolean printText( String strText ) 
	{
        boolean Result = false;
        
        if(openPrinter())
        {
        	byte[] PrintResult = new byte[1];
        	{
				Result = mPclService.printText(strText, PrintResult);
			}
        	Log.d(TAG, String.format("TO PRINT : %s",strText));
    		Log.d(TAG, String.format("printText result=%d", PrintResult[0]));
    		
    		closePrinter();
        }
 
  	  return Result;
  	}
	public boolean printBitmap( byte[] bmpBuf, int bmpSize )
	{
        boolean result = false;
        
        if(bmpBuf != null)
        {
	        if(openPrinter())
	        {
	        	byte[] printResult = new byte[1];
	        	{
					result = mPclService.printBitmap(bmpBuf, bmpSize, printResult);
				}
	    		Log.d(TAG, String.format("printBitMap result=%d", printResult[0]));
	    		
	    		closePrinter();
	        }
        }
 
  	  return result;
  	}
	boolean storeLogo( String name, int type, byte[] bmpBuf, int bmpSize, byte[] result ) {
		boolean ret = false;
		if( mPclService != null ) {
			{
				ret = mPclService.storeLogo(name, type, bmpBuf, bmpSize, result);
			}
		}
		return ret;
	}
	
	boolean printLogo( String name, byte[] result ) {
		boolean ret = false;
		if( mPclService != null ) {
			{
				ret = mPclService.printLogo(name, result);
			}
		}
		return ret;
	}
	
	boolean getPrinterStatus(byte[] result) {
		boolean ret = false;
		if( mPclService != null ) {
			{
				ret = mPclService.getPrinterStatus(result);
			}
		}
		return ret;
	}

	boolean getTime() {
		boolean ret = false;
		byte[] time = new byte[16];
		if( mPclService != null ) {
			{
			sysTime = new _SYSTEMTIME();
			ret = mPclService.getTerminalTime(time);
			ByteBuffer bbTime = ByteBuffer.wrap(time);
			bbTime.order(ByteOrder.LITTLE_ENDIAN);
			sysTime.wYear = bbTime.getShort();
			sysTime.wMonth = bbTime.getShort();
			sysTime.wDayOfWeek = bbTime.getShort();
			sysTime.wDay = bbTime.getShort();
			sysTime.wHour = bbTime.getShort();
			sysTime.wMinute = bbTime.getShort();
			sysTime.wSecond = bbTime.getShort();
			sysTime.wMilliseconds = bbTime.getShort();

			}
		}
		return ret;
	}

	boolean getTimeZone(){
		boolean ret = false;
		byte[] timezone = new byte[16];
		if( mPclService != null ) {
			sysTimeZone = new _TimeZone();
			ret = mPclService.getTerminalTimeZone(timezone);
			ByteBuffer bbTime = ByteBuffer.wrap(timezone);
			bbTime.order(ByteOrder.LITTLE_ENDIAN);
			sysTimeZone.dDST = bbTime.getInt();
			bbTime.get(sysTimeZone.sName);
		}
		return ret;
	}	

	boolean setTime(byte[] result) {
		boolean ret = false;
		
		if( mPclService != null ) {
			{
				ret = mPclService.setTerminalTime(result);
			}
		}
		return ret;

	}

	boolean setTimeZone(byte[] result) {
		boolean ret = false;
		if( mPclService != null )
			ret = mPclService.setTerminalTimeZone(result);
		return ret;
	}

	boolean getTermInfo() {
		boolean ret = false;
		byte[] serialNbr = new byte[4];
		byte[] productNbr = new byte[4];
		if( mPclService != null ) {
			{
				ret = mPclService.getTerminalInfo(serialNbr, productNbr);
				ByteBuffer bbSN = ByteBuffer.wrap(serialNbr);
				ByteBuffer bbPN = ByteBuffer.wrap(productNbr);
				bbSN.order(ByteOrder.LITTLE_ENDIAN);
				bbPN.order(ByteOrder.LITTLE_ENDIAN);
				SN = bbSN.getInt();
				PN = bbPN.getInt();
			}
		}
		return ret;

	}

	boolean getFullSerialNumber(byte[] serialNbr) {
		boolean ret = false;
		
		if( mPclService != null ) {
			{
				ret = mPclService.getFullSerialNumber(serialNbr);
				ByteBuffer bbSN = ByteBuffer.wrap(serialNbr);
				bbSN.order(ByteOrder.LITTLE_ENDIAN);
				SN = bbSN.getInt();
			}
		}
		return ret;

	}
	
	boolean getComponentsInfo() {
		boolean ret = false;

		if( mPclService != null ) {
			{
				ret = mPclService.getTerminalComponents("Running.lst");
			}
		}
		return ret;

	}

	boolean doTransaction(TransactionIn transIn, TransactionOut transOut) {
		boolean ret = false;

		if( mPclService != null ) {
			{
				ret = mPclService.doTransaction(transIn, transOut);
			}
		}
		return ret;

	}
	
	boolean doTransactionEx(TransactionIn transIn, TransactionOut transOut, int appNumber, byte[] inBuffer, int inBufferSize, byte[] outBuffer, long[] outBufferSize) {
		boolean ret = false;

		if( mPclService != null ) {
			try {
				ret = mPclService.doTransactionEx(transIn, transOut, appNumber, inBuffer, inBufferSize, outBuffer, outBufferSize);
			} catch( IllegalArgumentException iae) {
				iae.printStackTrace();
			}
		}
		return ret;

	}

	boolean doUpdate(byte[] result) {
		boolean ret = false;
		if( mPclService != null ) {
			ret = mPclService.doUpdate(result);
		}
		return ret;

	}

	boolean doUpdate(TmsParam params, long mTimeout, byte[] result) {
		boolean ret = false;
		if (mPclService != null) {
			ret = mPclService.doUpdate(params, mTimeout, result);
		}
		return ret;
	}

	boolean getLastInfo(API_ID api,byte[] result)
	{
		boolean ret = false;
		if(mPclService != null)
		{
			ret = mPclService.getLastInfo(api,result);
		}
		return ret;
	}

	boolean doTmsInstall(byte[] result) {
		boolean ret = false;
		if( mPclService != null ) {
			ret = mPclService.doTmsInstall(result);
		}
		return ret;

	}

	boolean doTmsCancel(byte[] result) {
		boolean ret = false;
		if( mPclService != null ) {
			ret = mPclService.doTmsCancel(result);
		}
		return ret;

	}
	
	boolean setTmsParameters(TmsParam param, byte[] result)
	{
		boolean ret = false;
		if( mPclService != null ) {
			ret = mPclService.tmsWriteParam(param, result);
		}
		return ret;
	}
	
	boolean getTmsParameters(TmsParam param, byte[] result)
	{
		boolean ret = false;
		if( mPclService != null ) {
			ret = mPclService.tmsReadParam(param, result);
		}
		return ret;
	}


	boolean resetTerminal(int resetInfo) {
		boolean ret = false;
		if( mPclService != null ) {
			{
				ret = mPclService.resetTerminal(resetInfo);
			}
		}
		return ret;

	}

	boolean powerOffTerminal(int powerOffInfo) {
    	boolean ret = false;

    	if (mPclService != null) {
    		ret = mPclService.powerOffTerminal(powerOffInfo);
		}

		return ret;
	}

	int stopPcl() {
    	int ret = 0;

    	if (mPclService != null) {
    		ret = mPclService.stopPcl();
		}

		return ret;
	}

	boolean sendMsg(byte[] msg, int[] byteSent) {
		boolean ret = false;
		if( mPclService != null ) {
			{
				ret = mPclService.sendMessage(msg, byteSent);
			}
		}
		return ret;

	}

	boolean recvMsg(byte[] msg, int[] byteReceived) {
		boolean ret = false;
		if( mPclService != null ) {
			{
				ret = mPclService.receiveMessage(msg, byteReceived);
				Log.d(TAG, String.format("recvMsg Len=%d Msg=%s Hex=%02x%02x%02x", byteReceived[0], msg, msg[0], msg[1], msg[2]));
			}
		}
		return ret;

	}

	boolean flushMsg() {
		boolean ret = false;
		if( mPclService != null ) {
			{
				ret = mPclService.flushMessages();
			}
		}
		return ret;

	}

	boolean launchM2OSShortcut(byte[] shortcut) {
		boolean ret = false;
		if( mPclService != null ) {
			{
				ret = mPclService.launchM2OSShortcut(shortcut);
			}
		}
		return ret;

	}
	
	public boolean isCompanionConnected()
	{
		boolean bRet = false;
		if (mPclService != null)
		{
			byte result[] = new byte[1];
			{
				if (mPclService.serverStatus(result) == true)
				{
					if (result[0] == 0x10)
						bRet = true;
				}
			}
		}
		return bRet;
	}
	
	private void initStateReceiver()
    {
    	if(m_StateReceiver == null)
    	{
    		m_StateReceiver = new StateReceiver(this);
	    	IntentFilter intentfilter = new IntentFilter("com.ingenico.pclservice.intent.action.STATE_CHANGED");
			registerReceiver(m_StateReceiver, intentfilter);
    	}
    }
    private void releaseStateReceiver()
    {
    	if(m_StateReceiver != null)
    	{
    		unregisterReceiver(m_StateReceiver);
    		m_StateReceiver = null;
    	}
    }
	
	public boolean isSslCertificateKo()
	{
		boolean bRet = false;
		if (mPclService != null)
		{
			bRet = mPclService.isSslCertificateKo();
		}
		return bRet;
	}

	private class BarCodeReceiver extends BroadcastReceiver
	{
		private CommonActivity ViewOwner = null;
		@SuppressLint("UseValueOf")
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			if (action.equals("com.ingenico.pclservice.action.BARCODE_CLOSED"))
			{
				ViewOwner.onBarCodeClosed();
			}
			else
			{
				byte abyte0[] = intent.getByteArrayExtra("barcode");
				String BarCodeStr = new String(abyte0);
				int symbology = intent.getIntExtra("barcode_symbology", -2);
			
			
				ViewOwner.onBarCodeReceived(BarCodeStr, symbology);
			}
		}

		BarCodeReceiver(CommonActivity receiver)
        {
            super();
            ViewOwner = receiver;
        }
	}
	
	private class SslReceiver extends BroadcastReceiver
	{
		private CommonActivity ViewOwner = null;
		@SuppressLint("UseValueOf")
		public void onReceive(Context context, Intent intent)
		{
			boolean state = intent.getBooleanExtra(SslObject.ACTION_SSL_NOTIFY,false);
			Log.d(TAG, String.format("receiver: ssl certificate %s", state));
			ViewOwner.onSslStateChanged(state);
		}

		SslReceiver(CommonActivity receiver)
		{
			super();
			ViewOwner = receiver;
		}
	}
	
	private class StateReceiver extends BroadcastReceiver
	{
		private CommonActivity ViewOwner = null;
		@SuppressLint("UseValueOf")
		public void onReceive(Context context, Intent intent)
		{
			String state = intent.getStringExtra("state");
			Log.d(TAG, String.format("receiver: State %s", state));
			ViewOwner.onStateChanged(state);
		}

		StateReceiver(CommonActivity receiver)
        {
            super();
            ViewOwner = receiver;
        }
	}
	
}

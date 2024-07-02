package com.ingenico.pcltestappwithlib;

import android.Manifest;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ingenico.pclutilities.PclUtilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EasyPairingActivity extends ListActivity {
    static final UUID SPP_UUID =UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    final String BT_PAIR_RESULT = "com.ingenico.pclutilities.intent.action.BT_PAIR_RESULT";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int BT_PERMISSIONS_REQUEST = 10;
    private static final String TAG = "PCLUTIL_" + BuildConfig.VERSION_NAME;
    private ArrayList<PclBtDevice> mDevicesToPair = null;
    private PclBtDevice currentBtPairingDevice = null;
    private BroadcastReceiver mReceiver = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private static PclBtAdapter listDataAdapter = null;
    private ConnectThread mConnectThread = null;
    private volatile boolean pairing = false;

    public class PclBtDevice{
        public String mName;
        public String mAddress;

        public PclBtDevice(String name, String address) {
            this.mName = name;
            this.mAddress = address;
        }
        //retrieve user's name
        public String getName(){
            return mName;
        }

        //retrieve users' hometown
        public String getAddress(){
            return mAddress;
        }
    }

    public class PclBtAdapter extends ArrayAdapter<PclBtDevice>{

        private ViewHolder viewHolder = null;
        private class ViewHolder {
            TextView name;
            TextView address;
        }

        public PclBtAdapter(Context context, ArrayList<PclBtDevice> pclBtDevices){
            super(context,R.layout.easypairing_listrow, pclBtDevices);
        }

        public View getView(int position, View convertView, ViewGroup parent){
            // Get the data item for this position
            PclBtDevice pclBtDevice = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                // If there's no view to re-use, inflate a brand new view for row
                viewHolder = new ViewHolder();
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.easypairing_listrow, parent, false);
                viewHolder.name = (TextView) convertView.findViewById(R.id.listRowName);
                viewHolder.address = (TextView) convertView.findViewById(R.id.listRowAddress);
                // Cache the viewHolder object inside the fresh view
                convertView.setTag(viewHolder);
            } else {
                // View is being recycled, retrieve the viewHolder object from tag
                viewHolder = (ViewHolder) convertView.getTag();
            }
            // Populate the data from the data object via the viewHolder object
            // into the template view.
            viewHolder.name.setText(pclBtDevice.mName);
            viewHolder.address.setText(pclBtDevice.mAddress);
            // Return the completed view to render on screen
            return convertView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.easypairing);
        Button button = (Button) findViewById(R.id.discoverButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startDiscovery();
            }
        });

        if(mReceiver == null)
        {
            mReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                        // Discovery has found a device. Get the BluetoothDevice
                        // object and its info from the Intent.
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

                        if(!pairedDevices.contains(device))
                        {
                            String address = device.getAddress();

                            if(PclUtilities.isIngenicoBtDevice(address))
                            {
                                boolean addDevice = true;
                                for(PclBtDevice pclBtDevice : mDevicesToPair)
                                {
                                    if(pclBtDevice.getAddress().equals(address))
                                    {
                                        addDevice = false;
                                    }
                                }
                                if(addDevice)
                                {
                                    PclBtDevice pclBtDevice = new PclBtDevice(device.getName(),address);
                                    mDevicesToPair.add(pclBtDevice);
                                    listDataAdapter.notifyDataSetChanged();
                                }
                            }
                        }
                    }
                    if(BT_PAIR_RESULT.equals(action))
                    {
                        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                        BluetoothDevice btDevice = intent.getParcelableExtra("device");
                        if (mDevicesToPair.contains(currentBtPairingDevice)) {
                            if(pairedDevices.contains(btDevice)) {
                                mDevicesToPair.remove(currentBtPairingDevice);
                                listDataAdapter.notifyDataSetChanged();
                            }
                        }

                        currentBtPairingDevice = null;

                        Button button = (Button) findViewById(R.id.discoverButton);
                        button.setVisibility(View.VISIBLE);
                        TextView textView = (TextView) findViewById(R.id.listTitle);
                        textView.setText(R.string.ingenico_discovering_stopped);
                        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
                        progressBar.setVisibility(View.INVISIBLE);
                    }
                    if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                        if(!pairing)
                        {
                            ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
                            progressBar.setVisibility(View.INVISIBLE);
                            TextView textView = (TextView) findViewById(R.id.listTitle);
                            textView.setText(R.string.ingenico_discovering_stopped);
                            Button button = (Button) findViewById(R.id.discoverButton);
                            button.setVisibility(View.VISIBLE);
                        }
                    }
                }
            };

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
            intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            intentFilter.addAction(BT_PAIR_RESULT);
            registerReceiver(mReceiver, intentFilter);
        }

        mDevicesToPair = new ArrayList<PclBtDevice>();
        listDataAdapter = new PclBtAdapter(this, mDevicesToPair);

        this.setListAdapter(listDataAdapter);

        if(manageBtPermisssions())
        {
            startDiscovery();
        }
    }


    void startDiscovery(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
        }

        if(mBluetoothAdapter.startDiscovery())
        {
            Button button = (Button) findViewById(R.id.discoverButton);
            button.setVisibility(View.INVISIBLE);
            TextView textView = (TextView) findViewById(R.id.listTitle);
            textView.setText(R.string.ingenico_discovering);
            ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
            progressBar.setVisibility(View.VISIBLE);
        }
        else
        {
            Button button = (Button) findViewById(R.id.discoverButton);
            button.setVisibility(View.VISIBLE);
            TextView textView = (TextView) findViewById(R.id.listTitle);
            textView.setText(R.string.ingenico_discovering_stopped);
            ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                startDiscovery();
            }
        }
        else
        {
            Button button = (Button) findViewById(R.id.discoverButton);
            button.setVisibility(View.VISIBLE);
            TextView textView = (TextView) findViewById(R.id.listTitle);
            textView.setText(R.string.ingenico_discovering_stopped);
            ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onListItemClick(ListView listView, View v, int position, long id) {

        pairing = true;

        Button button = (Button) findViewById(R.id.discoverButton);
        button.setVisibility(View.INVISIBLE);
        TextView textView = (TextView) findViewById(R.id.listTitle);
        textView.setText(R.string.ingenico_pairing);
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        // Get the list data adapter.
        PclBtAdapter listAdapter = (PclBtAdapter) listView.getAdapter();
        currentBtPairingDevice = listAdapter.getItem(position);

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(currentBtPairingDevice.mAddress);

        if(mBluetoothAdapter.isDiscovering())
        {
            mBluetoothAdapter.cancelDiscovery();
        }

        if(mConnectThread != null)
        {
            if(mConnectThread.isAlive()) {
                mConnectThread.cancel();
            }
        }
        mConnectThread = new ConnectThread(device, this);

        mConnectThread.start();
    }

    protected void onDestroy() {
        if(mBluetoothAdapter != null)
        {
            if(mBluetoothAdapter.isDiscovering())
            {
                mBluetoothAdapter.cancelDiscovery();
            }
        }

        if(mReceiver != null)
        {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        if(mConnectThread != null)
        {
            if(mConnectThread.isAlive())
            {
                mConnectThread.cancel();
            }
        }

        super.onDestroy();
    }

    public boolean manageBtPermisssions()
    {
        boolean bRet = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissions = Arrays.asList(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_COARSE_LOCATION);
            List<String> permissionToRequest = new ArrayList<String>();
            Context context = getApplicationContext();
            for(String permission : permissions){
                if(context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                {
                    permissionToRequest.add(permission);
                }
            }
            if(!permissionToRequest.isEmpty())
            {
                bRet = false;
                String[] perms = new String[permissionToRequest.size()];
                int i = 0;
                for(String perm : permissionToRequest)
                {
                    perms[i++] = perm;
                }
                requestActivityPermissions(perms,BT_PERMISSIONS_REQUEST);
            }
        }

        return bRet;
    }


    public void requestActivityPermissions(String[] permissions, int id)
    {
        if(permissions != null)
        {
            ActivityCompat.requestPermissions(this, permissions,id);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case BT_PERMISSIONS_REQUEST:
            {
                for(int result : grantResults)
                {
                    if(result != PackageManager.PERMISSION_GRANTED)
                    {
                        onPause();
                        finish();
                        return;
                    }
                }

                startDiscovery();

                return;
            }
        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final Context mContext;

        private void notifyEnd() {
            Intent i = new Intent();
            i.setAction(BT_PAIR_RESULT);
            i.putExtra("device",mmDevice);
            mContext.sendBroadcast(i);
            pairing = false;
        }

        public ConnectThread(BluetoothDevice device,Context context) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            mContext = context;
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                tmp = mmDevice.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                notifyEnd();
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }
            notifyEnd();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
                pairing = false;
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }
}

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final int REQ_CODE = 1337;

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private EditText editTextMessage;
    private Button buttonSend;
    private Button buttonEXIT;

    private String receiveBuffer = "";

    //OPENCV========================================================================================

    private Button buttonCalibrate;

    public int hueMin = 10;
    public int hueMax = 130;
    public int satMin = 10;
    public int satMax = 200;
    public int valMin = 10;
    public int valMax = 200;
    public int setRadius = 0;


    public int countFrames = 0;
    public String messageArduino;

    private static String OCV = "[OPEN CV]";

    JavaCameraView javaCameraView;
    Mat mask = new Mat();
    Mat circlesFrame = new Mat();
    BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case BaseLoaderCallback.SUCCESS: {
                    javaCameraView.enableView();
                    break;
                }
                default:{
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    static {
        if (OpenCVLoader.initDebug()){
            Log.i(OCV, "OpenCV loaded success");
        } else {
            Log.i(OCV, "OpenCV not loaded");
        }
    }

    //OPENCV========================================================================================

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private void messageHandler() {
        if (receiveBuffer != null) {
            mDataField.setText(receiveBuffer);
        }
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                receiveBuffer += intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                if(receiveBuffer.contains("\n")) {
                    receiveBuffer = receiveBuffer.substring(0, receiveBuffer.length() - 1);
                    messageHandler();
                    receiveBuffer = "";
                }
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        //OPENCV VARIABLES==========================================================================

        javaCameraView = (JavaCameraView)findViewById(R.id.java_camera_view);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);

        //OPENCV VARIABLES==========================================================================

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(editTextMessage.getText().toString().length()>0) {
                    mBluetoothLeService.writeCharacteristic(editTextMessage.getText().toString());
                    editTextMessage.setText("");
                }
            }
        });

        buttonCalibrate = findViewById(R.id.buttonCalibrate);
        buttonCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentCalib = new Intent(DeviceControlActivity.this, CalibrationActivity.class);
                intentCalib.putExtra("hueMin", hueMin);
                intentCalib.putExtra("hueMax", hueMax);
                intentCalib.putExtra("satMin", satMin);
                intentCalib.putExtra("satMax", satMax);
                intentCalib.putExtra("valMin", valMin);
                intentCalib.putExtra("valMax", valMax);
                startActivityForResult(intentCalib, REQ_CODE);
            }
        });

        buttonEXIT = findViewById(R.id.buttonEXIT);
        buttonEXIT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    //OPENCV========================================================================================

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat frame = inputFrame.rgba();
        if (setRadius == 0) {
            return inputFrame.rgba();
        } else {
            mBluetoothLeService.writeCharacteristic(messageArduino);
            System.out.println("SENDING DATA: "+ messageArduino);
            Mat hsvImage = new Mat();
            Mat circles = new Mat();
            frame.copyTo(circlesFrame);
            Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
            Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(4, 4));

            Imgproc.blur(frame, frame, new Size(7, 7));
            Imgproc.cvtColor(frame, hsvImage, Imgproc.COLOR_BGR2HSV);

            Scalar minValues = new Scalar(hueMin, satMin, valMin);
            Scalar maxValues = new Scalar(hueMax, satMax, valMax);

            Core.inRange(hsvImage, minValues, maxValues, mask);

            Imgproc.erode(mask, mask, erodeElement);
            Imgproc.erode(mask, mask, erodeElement);
            Imgproc.erode(mask, mask, erodeElement);

            Imgproc.dilate(mask, mask, dilateElement);
            Imgproc.dilate(mask, mask, dilateElement);
            Imgproc.dilate(mask, mask, dilateElement);

            Imgproc.HoughCircles(mask, circles, Imgproc.CV_HOUGH_GRADIENT, 1, 1000, 50, 10, 20, 300);
            drawCircles(circles, circlesFrame);
            frame.release();
            hsvImage.release();
            return circlesFrame;
        }

    }

    public void drawCircles(Mat circles, Mat surface){
        if (circles.cols() > 0) {
            for (int x=0; x < Math.min(circles.cols(), 5); x++ ) {
                double[] circleVec = circles.get(0, x);

                if (circleVec == null) {
                    break;
                }

                Point center = new Point((int) circleVec[0], (int) circleVec[1]);
                int radius = (int) circleVec[2];

                Imgproc.circle(surface, center, 3, new Scalar(0, 255, 255), 5);
                Imgproc.circle(surface, center, radius, new Scalar(255, 0, 255), 2);
                writeCoordinates((int) circleVec[0], radius);
                System.out.println("POINT X: " + (int) circleVec[0]);
            }
        }
    }

    public void writeCoordinates(int x, int resize){
        messageArduino = "";
        int gear = 0;
        if(setRadius-resize <= 0){
            if(setRadius-resize >= 0.3 * -setRadius) {
                gear = 0;
            } else {
                gear = -1;
            }
        } else if (setRadius-resize > 0) {
            if(setRadius-resize <= 0.05 * setRadius) {
                gear = 0;
            } else if(setRadius-resize > 0.05 * setRadius && setRadius-resize <= 0.2 * setRadius){
                gear = 1;
            } else if(setRadius-resize > 0.2 * setRadius && setRadius-resize <= 0.5 * setRadius){
                gear = 2;
            } else {
                gear = 3;
            }
        }

        if(x >= 0 && x <= 120){
            messageArduino = "-5 0 " + gear;
        } else if(x > 120 && x <= 240){
            messageArduino = "-4 0 " + gear;
        } else if(x > 240 && x <= 360){
            messageArduino = "-3 0 " + gear;
        } else if(x > 360 && x <= 480){
            messageArduino = "-2 0 " + gear;
        } else if(x > 480 && x < 600){
            messageArduino = "-1 0 " + gear;
        } else if(x >= 600 && x <= 680){
            messageArduino = "0 0 " + gear;
        } else if(x > 680 && x <= 800){
            messageArduino = "1 0 " + gear;
        } else if(x > 800 && x <= 920){
            messageArduino = "2 0 " + gear;
        } else if(x > 920 && x <= 1040){
            messageArduino = "3 0 " + gear;
        } else if(x > 1040 && x <= 1160){
            messageArduino = "4 0 " + gear;
        } else if(x > 1160 && x <= 1280){
            messageArduino = "5 0 " + gear;
        }
        messageArduino = "<" + messageArduino + ">";
        System.out.println("KUTAS GENERATED: " + messageArduino);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK && requestCode == REQ_CODE) {
            hueMin = data.getExtras().getInt("hueMin");
            hueMax = data.getExtras().getInt("hueMax");
            satMin = data.getExtras().getInt("satMin");
            satMax = data.getExtras().getInt("satMax");
            valMin = data.getExtras().getInt("valMin");
            valMax = data.getExtras().getInt("valMax");
            setRadius = data.getExtras().getInt("radius");
            System.out.println("[KUTAS]: " + hueMin + " " + hueMax + " " + satMin + " " + satMax + " " + valMin + " " + valMax );

        }
    }

    //OPENCV========================================================================================

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        if (OpenCVLoader.initDebug()){
            Log.i(OCV, "OpenCV loaded success");
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            Log.i(OCV, "OpenCV not loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallBack);
        }

        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if(javaCameraView!=null)
            javaCameraView.disableView();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(javaCameraView!=null)
            javaCameraView.disableView();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
                //custom code
                if(uuid.equals("0000ffe1-0000-1000-8000-00805f9b34fb") && mNotifyCharacteristic == null) {
                    mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                    mNotifyCharacteristic = gattCharacteristic;
                }
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}

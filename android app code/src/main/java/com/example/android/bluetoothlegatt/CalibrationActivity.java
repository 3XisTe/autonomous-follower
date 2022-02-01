package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import org.florescu.android.rangeseekbar.RangeSeekBar;
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

public class CalibrationActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    Button buttonConfirm;
    Button buttonMask;
    Button buttonColor;
    RangeSeekBar hueRangeBar;
    RangeSeekBar satRangeBar;
    RangeSeekBar valRangeBar;

    public int minHue;
    public int minSat;
    public int minVal;
    public int maxHue;
    public int maxSat;
    public int maxVal;
    public int circleRadius;
    public int setCircleRadius;
    //opencv

    public boolean flag = false;

    private String camMode = "normal";

    private static final String OCV = "[OPEN CV]";

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
    //opencv

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);
//openCV
        javaCameraView = (JavaCameraView)findViewById(R.id.java_camera_view);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);


        hueRangeBar = (RangeSeekBar)findViewById(R.id.hueRangeBar);
        satRangeBar = (RangeSeekBar)findViewById(R.id.satRangeBar);
        valRangeBar = (RangeSeekBar)findViewById(R.id.valRangeBar);

        Intent intent = getIntent();

        minHue = intent.getExtras().getInt("hueMin");
        maxHue = intent.getExtras().getInt("hueMax");
        minSat = intent.getExtras().getInt("satMin");
        maxSat = intent.getExtras().getInt("satMax");
        minVal = intent.getExtras().getInt("valMin");
        maxVal = intent.getExtras().getInt("valMax");

        hueRangeBar.setSelectedMaxValue(maxHue);
        hueRangeBar.setSelectedMinValue(minHue);
        satRangeBar.setSelectedMaxValue(maxSat);
        satRangeBar.setSelectedMinValue(minSat);
        valRangeBar.setSelectedMaxValue(maxVal);
        valRangeBar.setSelectedMinValue(minVal);

        hueRangeBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener() {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
                Number min_value = bar.getSelectedMinValue();
                Number max_value = bar.getSelectedMaxValue();

                minHue = (int)min_value;
                maxHue = (int)max_value;
              //  Toast.makeText(getApplicationContext(), "Min: " + minHue + "\n" + "Max: " + maxHue, Toast.LENGTH_LONG).show();
            }
        });

        satRangeBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener() {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
                Number min_value = bar.getSelectedMinValue();
                Number max_value = bar.getSelectedMaxValue();

                minSat = (int)min_value;
                maxSat = (int)max_value;
              //  Toast.makeText(getApplicationContext(), "Min: " + minSat + "\n" + "Max: " + maxSat, Toast.LENGTH_LONG).show();
            }
        });

        valRangeBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener() {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
                Number min_value = bar.getSelectedMinValue();
                Number max_value = bar.getSelectedMaxValue();

                minVal = (int)min_value;
                maxVal = (int)max_value;
               // Toast.makeText(getApplicationContext(), "Min: " + minVal + "\n" + "Max: " + maxVal, Toast.LENGTH_LONG).show();
            }
        });

        buttonConfirm = (Button) findViewById(R.id.buttonConfirm);

        buttonConfirm.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Intent data = new Intent();
                data.putExtra("hueMin", minHue);
                data.putExtra("hueMax", maxHue);
                data.putExtra("satMin", minSat);
                data.putExtra("satMax", maxSat);
                data.putExtra("valMin", minVal);
                data.putExtra("valMax", maxVal);
                data.putExtra("radius", setCircleRadius);
                setResult(RESULT_OK, data);
                finish();
            }
        });

        buttonMask = (Button) findViewById(R.id.buttonMask);

        buttonMask.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (flag) {
                    flag = false;
                    camMode = "mask";
                } else {
                    flag = true;
                    camMode = "normal";
                }
            }
        });

        buttonColor = (Button) findViewById(R.id.buttonSize);

        buttonColor.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                setCircleRadius = circleRadius;
                Log.i(OCV, "SET RADIUS: " + setCircleRadius);
            }
        });
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
        mask.release();
        circlesFrame.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat frame = inputFrame.rgba();
        Mat hsvImage = new Mat();
        Mat circles = new Mat();
        frame.copyTo(circlesFrame);
        Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
        Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(4, 4));

        Imgproc.blur(frame, frame, new Size(7, 7));
        Imgproc.cvtColor(frame, hsvImage, Imgproc.COLOR_BGR2HSV);

        Scalar minValues = new Scalar(minHue, minSat, minVal);
        Scalar maxValues = new Scalar(maxHue, maxSat, maxVal);

        Core.inRange(hsvImage, minValues, maxValues, mask);

        Imgproc.erode(mask, mask, erodeElement);
        Imgproc.erode(mask, mask, erodeElement);
        Imgproc.erode(mask, mask, erodeElement);

        Imgproc.dilate(mask, mask, dilateElement);
        Imgproc.dilate(mask, mask, dilateElement);
        Imgproc.dilate(mask, mask, dilateElement);

        Imgproc.HoughCircles(mask, circles, Imgproc.CV_HOUGH_GRADIENT, 1, 1000, 50, 10, 20, 300);
        if(camMode.equals("mask")){
            drawCircles(circles, mask);
            frame.release();
            hsvImage.release();
            return mask;
        } else {
            drawCircles(circles, circlesFrame);
            frame.release();
            hsvImage.release();
            return circlesFrame;
        }
    }

    public void drawCircles(Mat circles, Mat surface){
        if (circles.cols() > 0) {
            for (int x=0; x < Math.min(circles.cols(), 5); x++ ) {
                double circleVec[] = circles.get(0, x);

                if (circleVec == null) {
                    break;
                }

                Point center = new Point((int) circleVec[0], (int) circleVec[1]);
                int radius = (int) circleVec[2];

                Imgproc.circle(surface, center, 3, new Scalar(0, 0, 255), 5);
                Imgproc.circle(surface, center, radius, new Scalar(0, 0, 255), 2);
                circleRadius = radius;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()){
            Log.i(OCV, "OpenCV loaded success");
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            Log.i(OCV, "OpenCV not loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallBack);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if(javaCameraView!=null)
            javaCameraView.disableView();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(javaCameraView!=null)
            javaCameraView.disableView();
    }
}
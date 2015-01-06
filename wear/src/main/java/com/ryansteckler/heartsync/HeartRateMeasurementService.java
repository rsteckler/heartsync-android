package com.ryansteckler.heartsync;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

/**
 * Created by rsteckler on 1/3/15.
 */
public class HeartRateMeasurementService extends Service implements SensorEventListener {

    private final static int TYPE_HEARTRATE = 0;
    private final static int TYPE_ACCURACY = 1;

    private GoogleApiClient mGoogleApiClient;

    SensorManager mSensorManager;
    private Sensor mHeartRateSensor;

    PowerManager.WakeLock mWakelock;

    public static final int MODE_NONE = 0;
    public static final int MODE_ONE = 1;
    public static final int MODE_CONTINUAL = 2;

    private int mCurrentMode = MODE_NONE;

    long mStartTime = 0;
    private static boolean mMeasuring = false;

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {

            Log.d("HeartSync", "Starting new measurement.");
            setMeasuring(true);
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            mWakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartSyncMeasureWakelock");
            mWakelock.acquire();

            Log.d("HeartSync", "Registering for sensor updates.");
            mSensorManager.registerListener(HeartRateMeasurementService.this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mStartTime = new Date().getTime();

            //Block until the measurement is complete to stop the system from destroying the service.
            Log.d("HeartSync", "Blocking until measurement is complete.");
            while (mMeasuring) {
                try {
                    Thread.sleep(5000, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.d("HeartSync", "Finished blocking.  Android may now destroy the service.");

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("HeartSync", "Creating HeartRateMeasurementService.");

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments");
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        mSensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);


        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d("HeartSync", "HeartRateMeasurementService connected to phone: " + connectionHint);
                        // Now you can use the Data Layer API
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d("HeartSync", "HeartRateMeasurementService connection to phone suspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d("HeartSync", "HeartRateMeasurementService connection to phone failed: " + result);
                    }
                })
                        // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("HeartSync", "OnStartCommand HeartRateMeasurementService.");

        //Get the mode
        int requestedMode = intent.getIntExtra("mode", MODE_NONE);

        //Switch modes if needed.
        //Table of mode switching:
        // Currently    Requested   Result
        //  NONE        NONE         NONE
        //  NONE        ONE         ONE
        //  NONE        CONT        CONT
        //  ONE         NONE        NONE (though the measurement still happens)
        //  ONE         ONE         ONE (ignore)
        //  ONE         CONT        CONT
        //  CONT         NONE        NONE (though the next one measurement still happens)
        //  CONT        ONE         CONT (ignore)
        //  CONT        CONT        CONT (ignore)
        // So the only switches are from none->one, one->cont, and none->cont.  Also one->none and cont->none.
        //This logic could be collapsed, but leaving it like this for clarity.
        Log.d("HeartSync", "MeasurementService current mode: " + mCurrentMode);

        if (mCurrentMode == MODE_NONE && requestedMode == MODE_ONE) {
            mCurrentMode = MODE_ONE;
        } else if (mCurrentMode == MODE_ONE && requestedMode == MODE_CONTINUAL) {
            mCurrentMode = MODE_CONTINUAL;
        } else if (mCurrentMode == MODE_NONE && requestedMode == MODE_CONTINUAL) {
            mCurrentMode = MODE_CONTINUAL;
        } else if (mCurrentMode == MODE_ONE && requestedMode == MODE_NONE) {
            mCurrentMode = MODE_NONE;
        } else if (mCurrentMode == MODE_CONTINUAL && requestedMode == MODE_NONE) {
            mCurrentMode = MODE_NONE;
        }

        Log.d("HeartSync", "MeasurementService new mode: " + mCurrentMode);

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        if (!mMeasuring && requestedMode != MODE_NONE) {
            Message msg = mServiceHandler.obtainMessage();
            msg.arg1 = startId;
            mServiceHandler.sendMessage(msg);
        } else {
            Log.d("HeartSync", "We're already measuring.");
        }


        // If we get killed, after returning from here, restart
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    public static boolean getMeasuring() {
        return mMeasuring;
    }

    private void setMeasuring(boolean nowMeasuring) {
        mMeasuring = nowMeasuring;

        //Update the phone.
        PutDataMapRequest dataMap = PutDataMapRequest.create("/monitoring");
        dataMap.getDataMap().putBoolean("monitoring", mMeasuring);
        dataMap.getDataMap().putLong("timestamp", new Date().getTime());
        PutDataRequest request = dataMap.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, request);

    }

    @Override
    public void onDestroy() {
        Log.d("HeartSync", "Destroying HeartRateMeasurementService.");

        if (mSensorManager!=null) {
            Log.d("HeartSync", "onDestroy: Unregistering sensor manager.");
            mSensorManager.unregisterListener(this);
        }

        if(mGoogleApiClient.isConnected()) {
            Log.d("HeartSync", "onDestroy: Disconnecting from phone.");
            mGoogleApiClient.disconnect();
        }

        if (mWakelock != null) {
            if (mWakelock.isHeld()) {
                Log.d("HeartSync", "onDestroy: Releasing wakelock.");
                mWakelock.release();
            } else {
                Log.d("HeartSync", "onDestroy: Wakelock wasn't held.");
            }

        }

        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int heartRate = (int) sensorEvent.values[0];

            Log.d("HeartSync", "Sensor measurement: " + heartRate);

            //Update UI
            Log.d("HeartSync", "Updating UI with heartrate: " + heartRate);
            sendHeartRateToUi(heartRate);

            if (heartRate != 0) {

                final byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(heartRate).array();

                //Send to the phone
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("HeartSync", "Updating nodes with measurement.");

                        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                        for (Node node : nodes.getNodes()) {
                            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), "/heartrate", bytes).await();
                            if (!result.getStatus().isSuccess()) {
                                Log.e("HeartSync", "Failed to update node: " + node.getDisplayName() + " with error: " + result.getStatus());
                            } else {
                                Log.d("HeartSync", "Successfully updated node: " + node.getDisplayName());
                            }
                        }

                        if (mCurrentMode == MODE_NONE || mCurrentMode == MODE_ONE) {
                            //Stop the measurement.
                            Log.d("HeartSync", "Unregistering for sensor updates.");
                            if (mSensorManager != null) {
                                mSensorManager.unregisterListener(HeartRateMeasurementService.this);
                            }

                            Log.d("HeartSync", "Releasing the wakelock.");
                            if (mWakelock != null) {
                                if (mWakelock.isHeld()) {
                                    Log.d("HeartSync", "Released the wakelock.");
                                    mWakelock.release();
                                } else {
                                    Log.d("HeartSync", "Wakelock wasn't held.");
                                }
                            }

                            Log.d("HeartSync", "Finished measurement.");
                            setMeasuring(false);
                        }
                    }
                }).start();
            } else {
                //Zero reading.  Check if it's time to give up yet. (not in continual mode)
                long now = new Date().getTime();
                boolean giveUpOnTime = now - mStartTime > 60000;
                if (mCurrentMode == MODE_NONE || (mCurrentMode == MODE_ONE) && giveUpOnTime) {

                    //Give up.
                    Log.d("HeartSync", "Timing out on measurement.");

                    Log.d("HeartSync", "Unregistering for sensor updates.");
                    if (mSensorManager != null) {
                        mSensorManager.unregisterListener(HeartRateMeasurementService.this);
                    }

                    Log.d("HeartSync", "Releasing the wakelock.");
                    if (mWakelock != null) {
                        if (mWakelock.isHeld()) {
                            Log.d("HeartSync", "Released the wakelock.");
                            mWakelock.release();
                        } else {
                            Log.d("HeartSync", "Wakelock wasn't held.");
                        }
                    }

                    Log.d("HeartSync", "Finished measurement.");
                    setMeasuring(false);
                }
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        if (sensor.getType() == Sensor.TYPE_HEART_RATE) {
            Log.d("HeartSync", "Updating the UI with accuracy.");
            sendAccuracyToUi(i);

            final byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array();
            //Send to the phone
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d("HeartSync", "Updating nodes with accuracy.");

                    NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                    for (Node node : nodes.getNodes()) {
                        MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), "/accuracy", bytes).await();
                        if (!result.getStatus().isSuccess()) {
                            Log.e("HeartSync", "Failed to update node: " + node.getDisplayName() + " with error: " + result.getStatus());
                        } else {
                            Log.d("HeartSync", "Successfully updated node: " + node.getDisplayName());
                        }
                    }
                }
            }).start();
        }
    }

    private void sendHeartRateToUi(int heartRate) {
        sendToUi(TYPE_HEARTRATE, heartRate);
    }

    private void sendAccuracyToUi(int accuracy) {
        sendToUi(TYPE_ACCURACY, accuracy);
    }

    private void sendToUi(int type, int value) {
        Intent intent = new Intent("heartRateUpdate");
        // You can also include some extra data.
        if (type == TYPE_HEARTRATE) {
            intent.putExtra("heartRate", value);
        } else if (type == TYPE_ACCURACY) {
            intent.putExtra("accuracy", value);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}

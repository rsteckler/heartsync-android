package com.ryansteckler.heartsync;

import android.app.IntentService;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

/**
 * Created by rsteckler on 1/3/15.
 */
public class HeartRateMeasurementService extends IntentService implements SensorEventListener {

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
    boolean mMeasuring = false;

    public HeartRateMeasurementService() {
        super("HeartRateMeasurementService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("HeartSync", "Creating HeartRateMeasurementService.");

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
    protected void onHandleIntent(Intent intent) {
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

        if (!mMeasuring && requestedMode != MODE_NONE) {
            Log.d("HeartSync", "Starting new measurement.");
            mMeasuring = true;
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            mWakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartSyncMeasureWakelock");
            mWakelock.acquire();

            Log.d("HeartSync", "Registering for sensor updates.");
            mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
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

        } else {
            Log.d("HeartSync", "We're already measuring.");
        }

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
                            mMeasuring = false;
                        }
                    }
                }).start();
            } else {
                //Zero reading.  Check if it's time to give up yet. (not in continual mode)
                if (mCurrentMode == MODE_NONE || mCurrentMode == MODE_ONE) {

                    long now = new Date().getTime();
                    if (now - mStartTime > 60000) {
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
                        mMeasuring = false;
                    }
                }
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        if (sensor.getType() == Sensor.TYPE_HEART_RATE) {
            Log.d("HeartSync", "Updating the UI with accuracy.");
            sendAccuracyToUi(i);
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

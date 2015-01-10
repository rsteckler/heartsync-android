package com.ryansteckler.heartsync;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lecho.lib.hellocharts.model.PointValue;

/**
 * Created by rsteckler on 1/3/15.
 */
public class UpdateFitService extends IntentService {

    private GoogleApiClient mGoogleApiFitnessClient;
    private int mLastHeartrate = 0;

    private boolean mSending = false;
    private boolean mProcessing = false;

    public static final int TYPE_UPDATE_HEART_RATE = 0;
    public static final int TYPE_GET_HEART_RATE_DATA = 1;

    @Override
    public void onDestroy() {
        Log.d("HeartSync", "UpdateFitService destroyed");
        if (mGoogleApiFitnessClient.isConnected()) {
            Log.d("HeartSync", "Disconecting Google Fit.");
            mGoogleApiFitnessClient.disconnect();
        }
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("HeartSync", "UpdateFitService created");
        buildFitnessClient();
        mGoogleApiFitnessClient.connect();
    }

    public UpdateFitService() {
        super("UpdateFitService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        int type = intent.getIntExtra("requestType", -1);
        if (type == TYPE_UPDATE_HEART_RATE) {

            int heartRate = intent.getIntExtra("heartRate", 0);
            if (heartRate != 0 && heartRate != mLastHeartrate) {
                mLastHeartrate = heartRate;

                Log.d("HeartSync", "Updating Google Fit with heartRate: " + heartRate);
                mSending = true;

                Log.d("HeartSync", "Blocking service until fit update completes");
                while (mSending) {
                    try {
                        if (!mProcessing) {
                            sendHeartRateToFit(heartRate);
                        }
                        Thread.sleep(1000, 0);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Log.d("HeartSync", "Fit update complete.  Allowing Android to destroy the service.");
            }
        } else if (type == TYPE_GET_HEART_RATE_DATA) {
                Log.d("HeartSync", "Requesting heart rate history from Google Fit");
                mSending = true;

                Log.d("HeartSync", "Blocking service until fit update completes");
                while (mSending) {
                    try {
                        if (!mProcessing) {
                            getHeartRateHistory();
                        }
                        Thread.sleep(1000, 0);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Log.d("HeartSync", "Fit update complete.  Allowing Android to destroy the service.");
        }
    }

    private void getHeartRateHistory() {
        if (mGoogleApiFitnessClient.isConnected()) {
            mProcessing = true;
            // Setting a start and end date using a range of 1 year before this moment.
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.YEAR, -1);
            long startTime = cal.getTimeInMillis();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);

            final long firstBucketTime = cal.getTimeInMillis();
            final long bucketInterval = 1000 * 60 * 60;

            final DataReadRequest readRequest = new DataReadRequest.Builder()
                    .read(DataType.TYPE_HEART_RATE_BPM)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build();

            // Invoke the History API to fetch the data with the query and await the result of
            // the read request.

            new Thread(new Runnable() {
                @Override
                public void run() {

                    DataReadResult dataReadResult =
                            Fitness.HistoryApi.readData(mGoogleApiFitnessClient, readRequest).await(1, TimeUnit.MINUTES);

                    DataSet heartRateData = dataReadResult.getDataSet(DataType.TYPE_HEART_RATE_BPM);

                    ArrayList<PointValue> points = new ArrayList<PointValue>();

                    long bucketStart = firstBucketTime;
                    long bucketEnd = firstBucketTime + bucketInterval;

                    float hrMinInBucket = 0;
                    float hrMaxInBucket = 0;
                    float hrCountInBucket = 0;
                    float hrSumInBucket = 0;

                    for (DataPoint dp : heartRateData.getDataPoints()) {
                        for(Field field : dp.getDataType().getFields()) {
                            long timestamp = dp.getTimestamp(TimeUnit.MILLISECONDS);
                            float heartRate = dp.getValue(field).asFloat();

                            boolean foundBucket = false;
                            while (!foundBucket) {
                                if (timestamp <= bucketStart) {
                                    //Add this to the current bucket
                                    hrSumInBucket += heartRate;
                                    hrCountInBucket++;
                                    if (hrMinInBucket == 0) {
                                        hrMinInBucket = heartRate;
                                        hrMaxInBucket = heartRate;
                                    } else if (heartRate > hrMaxInBucket) {
                                        hrMaxInBucket = heartRate;
                                    } else if (heartRate < hrMinInBucket) {
                                        hrMinInBucket = heartRate;
                                    }

                                    foundBucket = true;
                                } else {
                                    //Save this bucket if there's anything in it
                                    if (hrCountInBucket > 0) {
                                        float hrAverageInBucket = hrSumInBucket / hrCountInBucket;
                                        PointValue pv = new PointValue(bucketEnd, (int) hrAverageInBucket);
                                        String label = "Min: " + hrMinInBucket + " Max: " + hrMaxInBucket + " Avg: " + (int)hrAverageInBucket;
                                        pv.setLabel(label.toCharArray());
                                        points.add(pv);
                                    }
                                    //Move up to the next bucket.
                                    bucketStart += bucketInterval;
                                    bucketEnd += bucketInterval;

                                    hrMinInBucket = 0;
                                    hrMaxInBucket = 0;
                                    hrCountInBucket = 0;
                                    hrSumInBucket = 0;

                                }
                            }

                        }
                    }

                    publishHeartRateData(points);
                    mSending = false;
                    mProcessing = false;
                }
            }).start();

        }

    }

    private void publishHeartRateData(ArrayList<PointValue> heartRateData) {
        Intent intent = new Intent("fitHistory");
        // You can also include some extra data.
        intent.putExtra("fitHistory", heartRateData);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    private void buildFitnessClient() {
        // Create the Google API Client
        mGoogleApiFitnessClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.API)
                .addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i("HeartSync", "Google Fit connected.");
                                // Now you can make calls to the Fitness APIs.
                                // Put application specific code here.
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i("HeartSync", "Google Fit Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i("HeartSync", "Google Fit Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i("HeartSync", "Google Fit Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
                                    Log.i("HeartSync", "Google Fit No resolution. Cause: " + result.getErrorCode());
                                }
                                mSending = false;
                                mProcessing = false;

                            }
                        }
                )
                .build();
    }

    private void sendHeartRateToFit(int heartRate) {
        if (mGoogleApiFitnessClient.isConnected()) {
            mProcessing = true;
            // Set a start and end time for our data, using a start time of 1 hour before this moment.
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            long startTime = cal.getTimeInMillis();

            // Create a data source
            DataSource dataSource = new DataSource.Builder()
                    .setAppPackageName(this)
                    .setDataType(DataType.TYPE_HEART_RATE_BPM)
                    .setName("HeartSync - heartrate")
                    .setType(DataSource.TYPE_RAW)
                    .build();

            // Create a data set
            final DataSet dataSet = DataSet.create(dataSource);
            // For each data point, specify a start time, end time, and the data value -- in this case,
            // the number of new steps.
            dataSet.add(
                    dataSet.createDataPoint()
                            .setTimestamp(startTime, TimeUnit.MILLISECONDS)
                            .setFloatValues(heartRate)
            );

            // Then, invoke the History API to insert the data and await the result, which is
            // possible here because of the {@link AsyncTask}. Always include a timeout when calling
            // await() to prevent hanging that can occur from the service being shutdown because
            // of low memory or other conditions.
            new Thread(new Runnable() {
                @Override
                public void run() {

                    Log.i("HeartSync", "Inserting the measurement to Google Fit");
                    com.google.android.gms.common.api.Status insertStatus =
                            Fitness.HistoryApi.insertData(mGoogleApiFitnessClient, dataSet)
                                    .await(30, TimeUnit.SECONDS);

                    // Before querying the data, check to see if the insertion succeeded.
                    if (!insertStatus.isSuccess()) {
                        Log.i("HeartSync", "Failed to insert the measurement to Google Fit.");
                    }

                    // At this point, the data has been inserted and can be read.
                    Log.i("HeartSync", "Successfully inserted measurement to Google Fit.");
                    mSending = false;
                    mProcessing = false;

                }
            }).start();

        } else {
            Log.w("HeartSync", "Google Fit isn't connected.  Can't send the measurement.");
        }
    }

}

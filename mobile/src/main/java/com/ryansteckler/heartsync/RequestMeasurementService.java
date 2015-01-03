package com.ryansteckler.heartsync;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by rsteckler on 1/3/15.
 */
public class RequestMeasurementService extends IntentService {


    private GoogleApiClient mGoogleApiClient;

    public RequestMeasurementService() {
        super("RequestMeasurementService");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        Log.d("HeartSync", "RequestMeasurementService starting a request to the watch.");

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d("HeartSync", "RequestMeasurementService connected to watch: " + connectionHint);
                        // Now you can use the Data Layer API

                        //Send to the phone
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                                for (Node node : nodes.getNodes()) {
                                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), "/measureNow", null).await();
                                    if (!result.getStatus().isSuccess()) {
                                        Log.e("HeartSync", "RequestMeasurementService failed to send message to watch: " + result.getStatus());
                                    }
                                }
                                Log.d("HeartSync", "RequestMeasurementService sent request to watch.");

                                Log.d("HeartSync", "RequestMeasurementService releasing wakelock.");
                                RequestMeasurementReceiver.completeWakefulIntent(intent);

                            }
                        }).start();

                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d("HeartSync", "RequestMeasurementService connection to watch suspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d("HeartSync", "RequestMeasurementService connection to watch failed: " + result);
                        Log.d("HeartSync", "RequestMeasurementService releasing wakelock.");
                        RequestMeasurementReceiver.completeWakefulIntent(intent);

                    }
                })
                        // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

}

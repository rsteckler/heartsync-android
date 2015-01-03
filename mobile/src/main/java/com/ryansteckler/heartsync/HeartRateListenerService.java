package com.ryansteckler.heartsync;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by rsteckler on 1/2/15.
 */
public class HeartRateListenerService extends WearableListenerService {

    private final static int TYPE_HEARTRATE = 0;
    private final static int TYPE_ACCURACY = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("HeartSync", "HeartRateListenerService created");

    }

    @Override
    public void onDestroy() {
        Log.d("HeartSync", "HeartRateListenerService destroyed");
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (messageEvent.getPath().equals("/heartrate")) {
            int heartRate = ByteBuffer.wrap(messageEvent.getData()).order(ByteOrder.LITTLE_ENDIAN).getInt();
            Log.d("HeartSync", "Received heartRate from watch: " + heartRate);

            //Update the UI.
            Log.d("HeartSync", "Updating the UI with heartrate.");
            sendHeartRateToUi(heartRate);

            Intent service = new Intent(this, UpdateFitService.class);
            service.putExtra("heartRate", heartRate);
            startService(service);


        } else if (messageEvent.getPath().equals("/accuracy")) {
            int accuracy = ByteBuffer.wrap(messageEvent.getData()).order(ByteOrder.LITTLE_ENDIAN).getInt();

            Log.d("HeartSyncPhone", "Received accuracy from watch: " + accuracy);
            Log.d("HeartSync", "Updating the UI with accuracy.");
            sendAccuracyToUi(accuracy);

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

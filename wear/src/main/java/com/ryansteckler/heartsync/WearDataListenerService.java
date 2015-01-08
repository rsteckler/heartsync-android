package com.ryansteckler.heartsync;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by rsteckler on 1/2/15.
 */
public class WearDataListenerService extends WearableListenerService  {


    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("HeartSync", "Creating WearDataListenerService.");
    }

    @Override
    public void onDestroy() {
        Log.d("HeartSync", "Destroying WearDataListenerService.");

        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (messageEvent.getPath().equals("/measureNow")) {

            Log.d("HeartSync", "Measurement requested.");

            Intent service = new Intent(this, HeartRateMeasurementService.class);
            int continual = ByteBuffer.wrap(messageEvent.getData()).order(ByteOrder.LITTLE_ENDIAN).getInt();
            service.putExtra("mode", continual == 1 ? HeartRateMeasurementService.MODE_CONTINUAL : HeartRateMeasurementService.MODE_ONE);

            Log.d("HeartSync", "Starting measurement service.");
            startService(service);
        }
    }

}

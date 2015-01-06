package com.ryansteckler.heartsync;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

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
            Log.d("HeartSync", "Starting measurement service.");
            Intent service = new Intent(this, HeartRateMeasurementService.class);
            service.putExtra("mode", HeartRateMeasurementService.MODE_ONE);
            startService(service);
        }
    }

}

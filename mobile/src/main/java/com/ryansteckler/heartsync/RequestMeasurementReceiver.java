package com.ryansteckler.heartsync;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * Created by rsteckler on 1/2/15.
 */
public class RequestMeasurementReceiver extends WakefulBroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("HeartSync", "Alarm fired to request measurement.");

        // This is the Intent to deliver to our service.
        Intent service = new Intent(context, RequestMeasurementService.class);

        // Start the service, keeping the device awake while it is launching.
        Log.d("HeartSync", "Starting measurement service.");
        startWakefulService(context, service);
    }

}

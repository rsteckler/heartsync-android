package com.ryansteckler.heartsync;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import java.text.DateFormat;
import java.util.Date;
import java.util.prefs.Preferences;

/**
 * Created by rsteckler on 1/2/15.
 */
public class RequestMeasurementReceiver extends WakefulBroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("HeartSync", "Alarm fired to request measurement.");

        Log.d("HeartSync", "Calculating next alarm time.");
        SharedPreferences preferences = context.getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);
        String nextUpdate = "unscheduled";
        if (preferences.getBoolean(MainFragment.PREF_ENABLE_AUTO_UPDATE, true)) {
            int frequencyId = preferences.getInt(MainFragment.PREF_UPDATE_FREQUENCY, 0);
            long interval = MainFragment.frequencyIdToInterval(frequencyId);

            Date nextUpdateDate = new Date(System.currentTimeMillis() + interval);
            nextUpdate = DateFormat.getDateTimeInstance().format(nextUpdateDate);
        }

        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(MainFragment.PREF_NEXT_UPDATE, nextUpdate);
        edit.apply();

        sendToUi(context, MainFragment.TYPE_NEXT_UPDATE, nextUpdate);

    // This is the Intent to deliver to our service.
        Intent service = new Intent(context, RequestMeasurementService.class);

        // Start the service, keeping the device awake while it is launching.
        Log.d("HeartSync", "Starting measurement service.");
        startWakefulService(context, service);
    }

    private void sendToUi(Context context, int type, String value) {
        Intent intent = new Intent("heartRateUpdate");
        // You can also include some extra data.
        if (type == MainFragment.TYPE_NEXT_UPDATE) {
            intent.putExtra("nextUpdate", value);
        }

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

}

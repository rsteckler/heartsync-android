package com.ryansteckler.heartsync;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.util.Date;

/**
 * Created by rsteckler on 1/2/15.
 */
public class HeartRateListenerService extends WearableListenerService {

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
            service.putExtra("requestType", UpdateFitService.TYPE_UPDATE_HEART_RATE);
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

        //Also write pref
        SharedPreferences preferences = this.getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);

        Date lastUpdateDate = new Date(System.currentTimeMillis());
        String lastUpdate = DateFormat.getDateTimeInstance().format(lastUpdateDate);

        if (heartRate > -1) {
            lastUpdate += " - " + heartRate + " bpm";
            sendToUi(MainFragment.TYPE_HEARTRATE, heartRate);

        } else {
            lastUpdate += " - unreadable";
        }


        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(MainFragment.PREF_LAST_UPDATE, lastUpdate);
        edit.apply();

        sendToUi(MainFragment.TYPE_LAST_UPDATE, lastUpdate);

    }

    private void sendAccuracyToUi(int accuracy) {
        sendToUi(MainFragment.TYPE_ACCURACY, accuracy);
    }

    private void sendMonitoringToUi(boolean monitoring) {
        sendToUi(MainFragment.TYPE_MONITORING, monitoring);
    }

    private void sendToUi(int type, int value) {
        Intent intent = new Intent("heartRateUpdate");
        // You can also include some extra data.
        if (type == MainFragment.TYPE_HEARTRATE) {
            intent.putExtra("heartRate", value);
        } else if (type == MainFragment.TYPE_ACCURACY) {
            intent.putExtra("accuracy", value);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendToUi(int type, boolean value) {
        Intent intent = new Intent("heartRateUpdate");
        // You can also include some extra data.
        if (type == MainFragment.TYPE_MONITORING) {
            intent.putExtra("monitoring", value);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendToUi(int type, String value) {
        Intent intent = new Intent("heartRateUpdate");
        // You can also include some extra data.
        if (type == MainFragment.TYPE_LAST_UPDATE) {
            intent.putExtra("lastUpdate", value);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.d("HeartSync", "onDataChanged");

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.d("HeartSync", "DataItem deleted: " + event.getDataItem().getUri());
            } else if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.d("HeartSync", "DataItem changed: " + event.getDataItem().getUri());

                if (event.getDataItem().getUri().getPath().equals("/monitoring")) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    boolean monitoring = dataMapItem.getDataMap().getBoolean("monitoring", false);

                    Log.d("HeartSync", "Monitoring on the watch has: " + (monitoring ? "started" : "stopped"));

                    //Store this so the UI can check offline.
                    SharedPreferences prefs = getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);
                    SharedPreferences.Editor edit = prefs.edit();
                    edit.putBoolean(MainFragment.PREF_MONITORING_NOW, monitoring);
                    edit.apply();

                    //Set the ui appropriately.
                    sendMonitoringToUi(monitoring);

                }

            }
        }
    }

}

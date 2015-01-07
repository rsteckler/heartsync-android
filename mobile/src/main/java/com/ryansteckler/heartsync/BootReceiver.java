package com.ryansteckler.heartsync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Created by rsteckler on 1/2/15.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {

            final SharedPreferences prefs = context.getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("enable_auto_update", true);
            int spinnerItem = prefs.getInt("update_frequency", 6);

            long interval = AlarmManager.INTERVAL_DAY;
            if (enabled) {

                if (spinnerItem == 0) {
                    interval = 5 * 60000;
                } else if (spinnerItem == 1) {
                    interval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
                } else if (spinnerItem == 2) {
                    interval = AlarmManager.INTERVAL_HALF_HOUR;
                } else if (spinnerItem == 3) {
                    interval = AlarmManager.INTERVAL_HOUR;
                } else if (spinnerItem == 4) {
                    interval = AlarmManager.INTERVAL_HOUR * 4;
                } else if (spinnerItem == 5) {
                    interval = AlarmManager.INTERVAL_HALF_DAY;
                } else if (spinnerItem == 6) {
                    interval = AlarmManager.INTERVAL_DAY;
                }

                PendingIntent alarmPendingIntent;
                Intent alarmIntent = new Intent(context, RequestMeasurementReceiver.class);
                alarmPendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + interval, interval, alarmPendingIntent);

                Date nextUpdateDate = new Date(System.currentTimeMillis() + interval);

                String nextUpdate = DateFormat.getDateTimeInstance().format(nextUpdateDate);

                SharedPreferences.Editor edit = prefs.edit();
                edit.putString("next_update", nextUpdate);
                edit.apply();

            }

        }
    }
}

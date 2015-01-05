package com.ryansteckler.heartsync;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.ryansteckler.heartsync.inappbilling.IabHelper;
import com.ryansteckler.heartsync.inappbilling.IabResult;
import com.ryansteckler.heartsync.inappbilling.Inventory;
import com.ryansteckler.heartsync.inappbilling.Purchase;

public class MainActivity extends Activity {
    private GoogleApiClient mGoogleApiFitnessClient;

    private TextView mRateTextView;
    private TextView mAccuracyText;

    IabHelper mHelper;
    private boolean mIsPremium = false;

    private static final int REQUEST_OAUTH = 1;

    /**
     *  Track whether an authorization activity is stacking over the current activity, i.e. when
     *  a known auth error is being resolved, such as showing the account chooser or presenting a
     *  consent dialog. This avoids common duplications as might happen on screen rotations, etc.
     */
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;

    ArrayAdapter<CharSequence> mSpinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupBilling();

        mRateTextView = (TextView)findViewById(R.id.textRate);
        mAccuracyText = (TextView)findViewById(R.id.textAccuracy);

        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(mHeartRateReceiver, new IntentFilter("heartRateUpdate"));

        final Spinner spinner = (Spinner) findViewById(R.id.spinnerFrequency);
        final Switch switchEnabled = (Switch)findViewById(R.id.switchAutoUpdate);
        switchEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, final boolean b) {
                //Update the data item and store the setting.
                final SharedPreferences prefs = getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("enable_auto_update", b);
                editor.apply();

                setMeasurementAlarm(b, spinner);

            }
        });


        // Create an ArrayAdapter using the string array and a default spinner layout
        mSpinnerAdapter = ArrayAdapter.createFromResource(this, R.array.frequency_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(mSpinnerAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, final int i, long l) {
                final SharedPreferences prefs = getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("update_frequency", i);
                editor.apply();

                boolean b = switchEnabled.isChecked();
                setMeasurementAlarm(b, spinner);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Button buttonDonate = (Button)findViewById(R.id.buttonDonate);
        buttonDonate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.launchPurchaseFlow(MainActivity.this, "donate_1", 1, mPurchaseFinishedListener, "1");
            }
        });

        //Do first run stuff
        final SharedPreferences prefs = getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);
        boolean firstRun = prefs.getBoolean("first_run", true);
        if (firstRun) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("enable_auto_update", true);
            editor.putInt("update_frequency", 6);
            editor.apply();

            setMeasurementAlarm(true, spinner);

            buildFitnessClient();
            new AlertDialog.Builder(this)
                    .setTitle("Connect to Google Fit")
                    .setMessage("We need to connect your Google Fit account to update your stats.")
                    .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            mGoogleApiFitnessClient.connect();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }

        updateDonationUi();

    }

    // Our handler for received Intents. This will be called whenever an Intent
    // with an action named "custom-event-name" is broadcasted.
    private BroadcastReceiver mHeartRateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            if (intent.hasExtra("heartRate")) {
                final int heartRate = intent.getIntExtra("heartRate", 6);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRateTextView.setText(String.valueOf(heartRate));
                    }
                });
            }
            if (intent.hasExtra("accuracy")) {

                int accuracy = intent.getIntExtra("accuracy", 0);
                String accuracyText = "";
                if (accuracy == 0) {
                    accuracyText = "--";
                } else if (accuracy == 1) {
                    accuracyText = "Low";
                } else if (accuracy == 2) {
                    accuracyText = "Medium";
                } else if (accuracy == 3) {
                    accuracyText = "High";
                }

                final String textToSet = accuracyText;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAccuracyText.setText(textToSet);
                    }
                });

            }
        }
    };

    private void setMeasurementAlarm(boolean b, Spinner spinner) {
        long interval = AlarmManager.INTERVAL_DAY;
        if (b) {

            int spinnerItem = spinner.getSelectedItemPosition();
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
            Intent alarmIntent = new Intent(this, RequestMeasurementReceiver.class);
            alarmPendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + interval, interval, alarmPendingIntent);
        } else {
            PendingIntent alarmPendingIntent;
            Intent alarmIntent = new Intent(this, RequestMeasurementReceiver.class);
            alarmPendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(alarmPendingIntent);

        }

    }

    private void setupBilling() {
        final IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(IabResult result, Inventory inventory) {

                if (result.isFailure()) {
                    // update UI accordingly
                    updateDonationUi();
                    Log.d("HeartSync", "IAP result failed with code: " + result.getMessage());
                }
                else {
                    // does the user have the premium upgrade?
                    Log.d("HeartSync", "IAP result succeeded");
                    if (inventory != null) {
                        Log.d("HeartSync", "IAP inventory exists");

                        if (inventory.hasPurchase("donate_1")) {
                            Log.d("HeartSync", "IAP inventory contains a donation");

                            mIsPremium = true;
                        }
                    }
                    // update UI accordingly
                    if (mIsPremium) {
                        updateDonationUi();
                    }
                }
            }
        };

        //Normally we would secure this key, but we're not licensing this app.
        String base64billing = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnrQWRI255GwTy8vewcA35kXt84yaUKasRri0DFQVIW2bcoSd4sci+JnvnpVhnmGZYyVjC7/y8g8ftQE/CUfLjDgSyIlqAYV0zFnjUwEI+emGD+BSMl2XxHLNXosJiNRbxOtvX4H+Qu1QgefDYsC3vcEG/+3EGjEXqIu/b0zldPouIhz9F96/YRzgZWbcA+eM1zdOg/3gzcn851EIul2+g+s3V8RjZ0+zA4+mJ+F0okhoddtO8H3ecHmVud3t44yimf2WBD+TVbJzcGh0geID5FlOw3287+zMBQ3PE7SVN2fu0oPd0mFUIpFhQUUOl+vpSC7hiVT//UydSpu3vEoRLwIDAQAB";
        mHelper = new IabHelper(this, base64billing);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result)
            {
                if (!result.isSuccess()) {
                    Log.d("HeartSync", "In-app Billing setup failed: " + result);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Pro features unavailable.")
                            .setMessage("Your device doesn't support In App Billing.  You won't be able to purchase the Pro features of HeartSync.  This could be because you need to update your Google Play Store application, or because you live in a country where In App Billing is disabled.")
                            .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();

                }
                else {
                    mHelper.queryInventoryAsync(false, mGotInventoryListener);
                }

            }
        });

    }

    private void updateDonationUi() {
        final SharedPreferences prefs = getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("enable_auto_update", true);
        int spinnerItem = prefs.getInt("update_frequency", 0);

        Switch switchEnabled = (Switch)findViewById(R.id.switchAutoUpdate);
        switchEnabled.setChecked(enabled);
        switchEnabled.setEnabled(mIsPremium);

        Spinner spinner = (Spinner)findViewById(R.id.spinnerFrequency);
        spinner.setSelection(spinnerItem);
        spinner.setEnabled(mIsPremium);

        Button donateButton = (Button)findViewById(R.id.buttonDonate);
        donateButton.setVisibility(mIsPremium ? View.GONE : View.VISIBLE );
    }

    @Override
    protected void onDestroy() {
        if (mGoogleApiFitnessClient != null) {
            if (mGoogleApiFitnessClient.isConnected()) {
                mGoogleApiFitnessClient.disconnect();
            }
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartRateReceiver);

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (mGoogleApiFitnessClient != null) {
                    if (!mGoogleApiFitnessClient.isConnecting() && !mGoogleApiFitnessClient.isConnected()) {
                        mGoogleApiFitnessClient.connect();
                    }
                }
            }
        }

        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }

    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase)
        {
            if (result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED ||
                    result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE ||
                    result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_DEVELOPER_ERROR ||
                    result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ERROR ||
                    result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED ||
                    result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE)
            {
                Toast.makeText(MainActivity.this, "Thank you for the thought, but the donation failed.", Toast.LENGTH_LONG).show();
            }
            else if (result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
                Toast.makeText(MainActivity.this, "Thank you for the thought, but you've already donated!", Toast.LENGTH_LONG).show();
            }
            else if (result.isSuccess()) {
                Toast.makeText(MainActivity.this, "Thank you SO much for donating!", Toast.LENGTH_LONG).show();
                mIsPremium = true;
                updateDonationUi();
                if (purchase.getSku().contains("consumable")) {
                    mHelper.consumeAsync(purchase, mConsumeFinishedListener);
                }
            }
            else
            {
                Toast.makeText(MainActivity.this, "Thank you for the thought, but the donation failed.", Toast.LENGTH_LONG).show();
            }

        }

        IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
            public void onConsumeFinished(Purchase purchase, IabResult result) {
                //Do nothing
            }
        };
    };


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }
    /**
     *  Build a {@link GoogleApiClient} that will authenticate the user and allow the application
     *  to connect to Fitness APIs. The scopes included should match the scopes your app needs
     *  (see documentation for details). Authentication will occasionally fail intentionally,
     *  and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     *  can address. Examples of this include the user never having signed in before, or having
     *  multiple accounts on the device and needing to specify which account to use, etc.
     */
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
                                Log.i("HeartSync", "Activity Thread Google Fit connected.");
                                // Now you can make calls to the Fitness APIs.
                                // Put application specific code here.
                                //Only needed to get the oauth scope setup.
                                Log.i("HeartSync", "Activity Thread Google Fit disconnecting.  We only needed to make sure connections work.");
                                mGoogleApiFitnessClient.disconnect();

                                final SharedPreferences prefs = getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putBoolean("first_run", false);
                                editor.apply();

                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i("HeartSync", "Activity Thread Google Fit Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i("HeartSync", "Activity Thread Google Fit Connection lost.  Reason: Service Disconnected");
                                }
                            }

                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i("HeartSync", "Activity Thread Google Fit Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
                                            MainActivity.this, 0).show();
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                    try {
                                        Log.i("HeartSync", "Activity Thread Google Fit Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(MainActivity.this,
                                                REQUEST_OAUTH);
                                    } catch (IntentSender.SendIntentException e) {
                                        Log.e("HeartSync",
                                                "Activity Thread Google Fit Exception while starting resolution activity", e);
                                    }
                                }
                            }
                        }
                )
                .build();
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//        if (id == R.id.action_settings) {
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }

}

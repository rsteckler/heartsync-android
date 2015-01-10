package com.ryansteckler.heartsync;

import android.animation.Animator;
import android.animation.ValueAnimator;
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
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ProgressBar;
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

import org.w3c.dom.Text;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.view.LineChartView;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link com.ryansteckler.heartsync.MainFragment.OnMainFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MainFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class MainFragment extends Fragment {

    public static final String PREF_UPDATE_FREQUENCY = "update_frequency";
    public static final String PREF_ENABLE_AUTO_UPDATE = "enable_auto_update";
    public static final String PREF_FIRST_RUN = "first_run";
    public static final String PREF_MONITORING_NOW = "monitoring_now";
    public static final String PREF_NEXT_UPDATE = "next_update";
    public static final String PREF_LAST_UPDATE = "last_update";
    private GoogleApiClient mGoogleApiFitnessClient;

    private TextView mRateTextView;
    private TextView mAccuracyTextView;
    private TextView mNextUpdateTextView;
    private TextView mLastUpdateTextView;
    private TextView mRefreshHistoryButton;
    private ChartFragment mChartFragment;
    private Spinner mFrequencySpinner;
    private Switch mAutoUpdateSwitch;
    private ProgressBar mBeatProgress;
    private ImageButton mToggleWorkoutModeButton;
    private Button mDonateButton;
    private TextView mCheckNowButton;
    private ArrayAdapter<CharSequence> mFrequencySpinnerAdapter;

    IabHelper mBillingHelper;
    private boolean mIsPremium = false;
    /**
     *  Track whether an authorization activity is stacking over the current activity, i.e. when
     *  a known auth error is being resolved, such as showing the account chooser or presenting a
     *  consent dialog. This avoids common duplications as might happen on screen rotations, etc.
     */
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private static final int REQUEST_OAUTH = 1;
    private SharedPreferences mPreferences;
    private ValueAnimator mProgressAnimation;
    private BeatAnimationListener mBeatAnimationListener;

    public final static int TYPE_HEARTRATE = 0;
    public final static int TYPE_ACCURACY = 1;
    public final static int TYPE_MONITORING = 2;
    public final static int TYPE_NEXT_UPDATE = 3;
    public final static int TYPE_LAST_UPDATE = 4;

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnMainFragmentInteractionListener mListener;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MainFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MainFragment newInstance(String param1, String param2) {
        MainFragment fragment = new MainFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }
    public MainFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreferences = getActivity().getSharedPreferences("com.ryansteckler.heartsync" + "_preferences", Context.MODE_PRIVATE);

        setupControls(view);

        if (isFirstRun()) {
            //Do first run stuff
            //Set initial prefs
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putBoolean(PREF_ENABLE_AUTO_UPDATE, true);
            editor.putInt(PREF_UPDATE_FREQUENCY, 6);
            editor.putBoolean(PREF_FIRST_RUN, false);
            editor.apply();

            setMeasurementAlarm(true, 6);

        }

        setupBilling(view);

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mHeartRateReceiver, new IntentFilter("heartRateUpdate"));

        //Make sure we're connected to Fit
        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }
        verifyFitConnection();
    }

    private void verifyFitConnection() {
        buildFitnessClient();
        mGoogleApiFitnessClient.connect();
    }

    private boolean isFirstRun() {
        return mPreferences.getBoolean(PREF_FIRST_RUN, true);
    }

    private void setupControls(View view) {
        mRateTextView = (TextView)view.findViewById(R.id.textRate);
        mAccuracyTextView = (TextView)view.findViewById(R.id.textAccuracy);
        mFrequencySpinner = (Spinner) view.findViewById(R.id.spinnerFrequency);
        mAutoUpdateSwitch = (Switch)view.findViewById(R.id.switchAutoUpdate);
        mCheckNowButton = (TextView)view.findViewById(R.id.textCheckNow);
        mBeatProgress = (ProgressBar)view.findViewById(R.id.progressBeat);
        mToggleWorkoutModeButton = (ImageButton)view.findViewById(R.id.measureNowButton);

        //Setup the spinner adapter.
        // Create an ArrayAdapter using the string array and a default spinner layout
        mFrequencySpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.frequency_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        mFrequencySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mFrequencySpinner.setAdapter(mFrequencySpinnerAdapter);

        mDonateButton = (Button)view.findViewById(R.id.buttonDonate);
        mDonateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBillingHelper.launchPurchaseFlow(getActivity(), "donate_1", 1, mPurchaseFinishedListener, "1");
            }
        });

        updateDonationUi(view);

        boolean enabled = mPreferences.getBoolean(PREF_ENABLE_AUTO_UPDATE, true);
        int freq = mPreferences.getInt(PREF_UPDATE_FREQUENCY, 6);
        mFrequencySpinner.setSelection(freq);
        mFrequencySpinner.setTag(freq);

        mAutoUpdateSwitch.setChecked(enabled);
        mAutoUpdateSwitch.setTag(enabled);

        String nextUpdate = mPreferences.getString(PREF_NEXT_UPDATE, "unscheduled");
        mNextUpdateTextView = (TextView)view.findViewById(R.id.textNextUpdate);
        mNextUpdateTextView.setText("Next update: " + nextUpdate);

        String lastUpdate = mPreferences.getString(PREF_LAST_UPDATE, "never");
        mLastUpdateTextView = (TextView)view.findViewById(R.id.textLastUpdate);
        mLastUpdateTextView.setText("Last update: " + lastUpdate);

        // Create a new Fragment to be placed in the activity layout
        mChartFragment = ChartFragment.newInstance(null, null);

        // Add the fragment to the 'fragment_container' FrameLayout
        getActivity().getSupportFragmentManager().beginTransaction()
                .add(R.id.chartFragmentContainer, mChartFragment).commit();

        mRefreshHistoryButton = (TextView)view.findViewById(R.id.textRefreshHistory);
        mRefreshHistoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mChartFragment.requestFitData();
            }
        });

        mAutoUpdateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, final boolean b) {
                if (b != (Boolean) mAutoUpdateSwitch.getTag()) {
                    mAutoUpdateSwitch.setTag(b);
                    //Update the data item and store the setting.
                    SharedPreferences.Editor editor = mPreferences.edit();
                    editor.putBoolean(PREF_ENABLE_AUTO_UPDATE, b);
                    editor.apply();

                    setMeasurementAlarm(b, mFrequencySpinner.getSelectedItemPosition());
                }
            }
        });

        mCheckNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Request a measurement now
                Intent service = new Intent(getActivity(), RequestMeasurementService.class);
                getActivity().startService(service);

                //And reset the alarm
                boolean autoUpdate = mPreferences.getBoolean(PREF_ENABLE_AUTO_UPDATE, true);
                int frequency = mPreferences.getInt(PREF_UPDATE_FREQUENCY, 6);
                setMeasurementAlarm(autoUpdate, frequency);
            }
        });

        mFrequencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, final int i, long l) {
                if (i != (Integer) mFrequencySpinner.getTag()) {
                    mFrequencySpinner.setTag(i);
                    SharedPreferences.Editor editor = mPreferences.edit();
                    editor.putInt(PREF_UPDATE_FREQUENCY, i);
                    editor.apply();

                    boolean b = mAutoUpdateSwitch.isChecked();
                    setMeasurementAlarm(b, i);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                Intent service = new Intent(getActivity(), RequestMeasurementService.class);
                service.putExtra("continual", true);
                getActivity().startService(service);

            }
        });

        mToggleWorkoutModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


            }
        });

        mBeatProgress.setProgress(0);

        mProgressAnimation = ValueAnimator.ofInt(0, 100);
        mBeatAnimationListener = new BeatAnimationListener(mBeatProgress, mProgressAnimation);
        mProgressAnimation.addListener(mBeatAnimationListener);
        mProgressAnimation.addUpdateListener(mBeatAnimationListener);
        mProgressAnimation.setDuration(900);
        mProgressAnimation.setStartDelay(100); //Create a small gap between each step, so they look discrete
        mProgressAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

        if (mPreferences.getBoolean(PREF_MONITORING_NOW, false)) {
            mBeatAnimationListener.mKeepRunning = true;
            mProgressAnimation.start();
        }
    }

    private class BeatAnimationListener implements Animator.AnimatorListener, ValueAnimator.AnimatorUpdateListener {
        public boolean mKeepRunning = true;
        @Override
        public void onAnimationCancel(Animator animator) {
        }

        @Override
        public void onAnimationRepeat(Animator animator) {
        }

        @Override
        public void onAnimationStart(Animator animator) {
        }

        private ProgressBar mProgressChecking;
        ValueAnimator mProgressAnimation;

        public BeatAnimationListener(ProgressBar progressChecking, ValueAnimator progressAnimation) {
            mProgressChecking = progressChecking;
            mProgressAnimation = progressAnimation;
        }

        @Override
        public void onAnimationUpdate(final ValueAnimator animator) {
            int curValue = (Integer) animator.getAnimatedValue();
            mProgressChecking.setProgress(curValue);
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            mProgressChecking.setProgress(0);
            if (mKeepRunning) {
                mProgressAnimation.start();
            }
        }
    }


    // Our handler for received Intents. This will be called whenever an Intent
    // with an action named "custom-event-name" is broadcasted.
    private BroadcastReceiver mHeartRateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            if (intent.hasExtra("heartRate")) {
                final int heartRate = intent.getIntExtra("heartRate", 6);
                getActivity().runOnUiThread(new Runnable() {
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

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAccuracyTextView.setText(textToSet);
                    }
                });

            }
            if (intent.hasExtra("monitoring")) {
                boolean monitoring = intent.getBooleanExtra("monitoring", false);
                if (monitoring) {
                    //Start the animations.
                    mBeatAnimationListener.mKeepRunning = true;
                    mProgressAnimation.start();
                } else {
                    mBeatAnimationListener.mKeepRunning = false;
                }

            }
            if (intent.hasExtra("nextUpdate")) {
                final String nextUpdate = intent.getStringExtra("nextUpdate");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mNextUpdateTextView.setText("Next update: " + (nextUpdate != null ? nextUpdate : "unscheduled"));
                    }
                });
            }
            if (intent.hasExtra("lastUpdate")) {
                final String lastUpdate = intent.getStringExtra("lastUpdate");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLastUpdateTextView.setText("Last update: " + (lastUpdate != null ? lastUpdate : "never"));
                    }
                });
            }
        }
    };

    private void setMeasurementAlarm(boolean b, int frequency) {
        long interval = AlarmManager.INTERVAL_DAY;

        String nextUpdate = "unscheduled";
        AlarmManager alarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
        PendingIntent alarmPendingIntent;
        Intent alarmIntent = new Intent(getActivity(), RequestMeasurementReceiver.class);
        alarmPendingIntent = PendingIntent.getBroadcast(getActivity(), 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        if (b) {

            interval = frequencyIdToInterval(frequency);

            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + interval, interval, alarmPendingIntent);

            Date nextUpdateDate = new Date(System.currentTimeMillis() + interval);

            nextUpdate = DateFormat.getDateTimeInstance().format(nextUpdateDate);

        } else {
            alarmManager.cancel(alarmPendingIntent);

        }

        SharedPreferences.Editor edit = mPreferences.edit();
        edit.putString(PREF_NEXT_UPDATE, nextUpdate);
        edit.apply();

        mNextUpdateTextView.setText("Next update: " + nextUpdate);

    }

    public static long frequencyIdToInterval(int frequency) {
        long interval = 0;
        if (frequency == 0) {
            interval = 5 * 60000;
        } else if (frequency == 1) {
            interval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
        } else if (frequency == 2) {
            interval = AlarmManager.INTERVAL_HALF_HOUR;
        } else if (frequency == 3) {
            interval = AlarmManager.INTERVAL_HOUR;
        } else if (frequency == 4) {
            interval = AlarmManager.INTERVAL_HOUR * 4;
        } else if (frequency == 5) {
            interval = AlarmManager.INTERVAL_HALF_DAY;
        } else if (frequency == 6) {
            interval = AlarmManager.INTERVAL_DAY;
        }
        return interval;
    }

    private void setupBilling(final View view) {
        final IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(IabResult result, Inventory inventory) {

                if (result.isFailure()) {
                    // update UI accordingly
                    updateDonationUi(view);
                    Log.d("HeartSync", "IAP result failed with code: " + result.getMessage());
                }
                else {
                    // does the user have the premium upgrade?
                    Log.d("HeartSync", "IAP result succeeded");
                    if (inventory != null) {
                        Log.d("HeartSync", "IAP inventory exists");

                        if (inventory.hasPurchase("donate_1")) {
                            Log.d("HeartSync", "IAP inventory contains a purchase");

                            mIsPremium = true;
                        }
                    }
                    // update UI accordingly
                    if (mIsPremium) {
                        updateDonationUi(view);
                    }
                }
            }
        };

        //Normally we would secure this key, but we're not licensing this app.
        String base64billing = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnrQWRI255GwTy8vewcA35kXt84yaUKasRri0DFQVIW2bcoSd4sci+JnvnpVhnmGZYyVjC7/y8g8ftQE/CUfLjDgSyIlqAYV0zFnjUwEI+emGD+BSMl2XxHLNXosJiNRbxOtvX4H+Qu1QgefDYsC3vcEG/+3EGjEXqIu/b0zldPouIhz9F96/YRzgZWbcA+eM1zdOg/3gzcn851EIul2+g+s3V8RjZ0+zA4+mJ+F0okhoddtO8H3ecHmVud3t44yimf2WBD+TVbJzcGh0geID5FlOw3287+zMBQ3PE7SVN2fu0oPd0mFUIpFhQUUOl+vpSC7hiVT//UydSpu3vEoRLwIDAQAB";
        mBillingHelper = new IabHelper(getActivity(), base64billing);
        mBillingHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    Log.d("HeartSync", "In-app Billing setup failed: " + result);
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Pro features unavailable.")
                            .setMessage("Your device doesn't support In App Billing.  You won't be able to purchase the Pro features of HeartSync.  This could be because you need to update your Google Play Store application, or because you live in a country where In App Billing is disabled.")
                            .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();

                } else {
                    mBillingHelper.queryInventoryAsync(false, mGotInventoryListener);
                }

            }
        });

    }

    private void updateDonationUi(View view) {
        boolean enabled = mPreferences.getBoolean(PREF_ENABLE_AUTO_UPDATE, true);
        int spinnerItem = mPreferences.getInt(PREF_UPDATE_FREQUENCY, 0);

        Switch switchEnabled = (Switch)view.findViewById(R.id.switchAutoUpdate);
        switchEnabled.setChecked(enabled);
        switchEnabled.setEnabled(mIsPremium);

        Spinner spinner = (Spinner)view.findViewById(R.id.spinnerFrequency);
        spinner.setSelection(spinnerItem);
        spinner.setEnabled(mIsPremium);

        Button donateButton = (Button)view.findViewById(R.id.buttonDonate);
        donateButton.setVisibility(mIsPremium ? View.GONE : View.VISIBLE );
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == Activity.RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (mGoogleApiFitnessClient != null) {
                    if (!mGoogleApiFitnessClient.isConnecting() && !mGoogleApiFitnessClient.isConnected()) {
                        mGoogleApiFitnessClient.connect();
                    }
                }
            }
        }

        if (!mBillingHelper.handleActivityResult(requestCode, resultCode, data)) {
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
                Toast.makeText(getActivity(), "Thank you for the thought, but the purchase failed.", Toast.LENGTH_LONG).show();
            }
            else if (result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
                Toast.makeText(getActivity(), "Thank you for the thought, but you've already unlocked Pro Features!", Toast.LENGTH_LONG).show();
            }
            else if (result.isSuccess()) {
                Toast.makeText(getActivity(), "Thank you SO much for purchasing HeartSync!", Toast.LENGTH_LONG).show();
                mIsPremium = true;
                updateDonationUi(getView());
                if (purchase.getSku().contains("consumable")) {
                    mBillingHelper.consumeAsync(purchase, mConsumeFinishedListener);
                }
            }
            else
            {
                Toast.makeText(getActivity(), "Thank you for the thought, but the purchase failed.", Toast.LENGTH_LONG).show();
            }

        }

        IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
            public void onConsumeFinished(Purchase purchase, IabResult result) {
                //Do nothing
            }
        };
    };


    @Override
    public void onSaveInstanceState(Bundle outState) {
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
        mGoogleApiFitnessClient = new GoogleApiClient.Builder(getActivity())
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
                            public void onConnectionFailed(final ConnectionResult result) {
                                Log.i("HeartSync", "Activity Thread Google Fit Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
                                            getActivity(), 0).show();
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                        Log.i("HeartSync", "Activity Thread Google Fit Attempting to resolve failed connection");

                                        new AlertDialog.Builder(getActivity())
                                                .setTitle("Connect to Google Fit")
                                                .setMessage("We need to connect your Google Fit account to update your stats.")
                                                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        try {
                                                            authInProgress = true;
                                                            result.startResolutionForResult(getActivity(), REQUEST_OAUTH);
                                                        } catch (IntentSender.SendIntentException e) {
                                                            Log.e("HeartSync",
                                                                    "Activity Thread Google Fit Exception while starting resolution activity", e);
                                                        }
                                                    }
                                                })
                                                .setIcon(android.R.drawable.ic_dialog_alert)
                                                .show();
                                }
                            }
                        }
                )
                .build();
    }

    @Override
    public void onDestroy() {
        if (mGoogleApiFitnessClient != null) {
            if (mGoogleApiFitnessClient.isConnected()) {
                mGoogleApiFitnessClient.disconnect();
            }
        }
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mHeartRateReceiver);

        super.onDestroy();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnMainFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnMainFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onMainFragmentInteraction(Uri uri);
    }

}

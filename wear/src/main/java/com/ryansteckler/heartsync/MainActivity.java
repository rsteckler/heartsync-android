package com.ryansteckler.heartsync;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity  {

    private TextView mRateText;
    private TextView mAccuracyText;
    private BeatAnimationListener mBeatAnimationListener;
    private ValueAnimator mBeatAnimation;
    private ProgressBar mBeatProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mRateText = (TextView) stub.findViewById(R.id.textCurrentMeasure);
                mAccuracyText = (TextView) stub.findViewById(R.id.textAccuracy);

                LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(mHeartRateReceiver, new IntentFilter("heartRateUpdate"));

                mBeatProgress = (ProgressBar)stub.findViewById(R.id.progressBeat);
                mBeatProgress.setProgress(0);

                mBeatAnimation = ValueAnimator.ofInt(0, 100);
                mBeatAnimationListener = new BeatAnimationListener(mBeatProgress, mBeatAnimation);
                mBeatAnimation.addListener(mBeatAnimationListener);
                mBeatAnimation.addUpdateListener(mBeatAnimationListener);
                mBeatAnimation.setDuration(900);
                mBeatAnimation.setStartDelay(100); //Create a small gap between each step, so they look discrete
                mBeatAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

                ImageButton startStop = (ImageButton)stub.findViewById(R.id.imageButton);
                startStop.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        boolean currentlyMeasuring = HeartRateMeasurementService.getMeasuring();
                            Intent service = new Intent(MainActivity.this, HeartRateMeasurementService.class);
                            service.putExtra("mode", currentlyMeasuring ? HeartRateMeasurementService.MODE_NONE : HeartRateMeasurementService.MODE_CONTINUAL);
                            startService(service);
                    }
                });

                if (HeartRateMeasurementService.getMeasuring()) {
                    mBeatProgress.setProgress(0);
                    mBeatAnimationListener.mKeepRunning = true;

                    //Start the animations.
                    mBeatAnimation.start();
                }

            }
        });
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRateText.setText(String.valueOf(heartRate) + " bpm");
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
            if (intent.hasExtra("monitoring")) {
                boolean monitoring = intent.getBooleanExtra("monitoring", false);
                if (!monitoring) {
                    //Stop the animation
                    mBeatAnimationListener.mKeepRunning = false;
                    mRateText.setText("Not Measuring");

                } else {
                    //Start the animation
                    mBeatProgress.setProgress(0);
                    mBeatAnimationListener.mKeepRunning = true;

                    //Start the animations.
                    mBeatAnimation.start();
                    mRateText.setText("Measuring. Please wait...");

                }
            }

        }
    };

    @Override
    protected void onDestroy() {
//Why did I have this in here?
//        Intent service = new Intent(this, HeartRateMeasurementService.class);
//        service.putExtra("mode", HeartRateMeasurementService.MODE_NONE);
//        startService(service);

        super.onDestroy();
    }

}

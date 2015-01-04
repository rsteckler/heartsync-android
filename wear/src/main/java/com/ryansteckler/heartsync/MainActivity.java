package com.ryansteckler.heartsync;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity  {

    private TextView mRateText;
    private TextView mAccuracyText;

    private boolean mStarted = false;


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

                final ProgressBar progressBeat = (ProgressBar)stub.findViewById(R.id.progressBeat);
                progressBeat.setProgress(0);

                final ValueAnimator progressAnimation = ValueAnimator.ofInt(0, 100);
                final BeatAnimationListener welcomeListener = new BeatAnimationListener(progressBeat, progressAnimation);
                progressAnimation.addListener(welcomeListener);
                progressAnimation.addUpdateListener(welcomeListener);
                progressAnimation.setDuration(900);
                progressAnimation.setStartDelay(100); //Create a small gap between each step, so they look discrete
                progressAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

                ImageButton startStop = (ImageButton)stub.findViewById(R.id.imageButton);
                startStop.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        if (mStarted) {
                            //Stop
                            Intent service = new Intent(MainActivity.this, HeartRateMeasurementService.class);
                            service.putExtra("mode", HeartRateMeasurementService.MODE_NONE);
                            startService(service);

                            welcomeListener.mKeepRunning = false;

                            mStarted = false;

                        } else {
                            //Start
                            // This is the Intent to deliver to our service.
                            Intent service = new Intent(MainActivity.this, HeartRateMeasurementService.class);
                            service.putExtra("mode", HeartRateMeasurementService.MODE_CONTINUAL);
                            startService(service);

                            progressBeat.setProgress(0);
                            welcomeListener.mKeepRunning = true;

                            //Start the animations.
                            progressAnimation.start();

                            mStarted = true;
                        }
                    }
                });

                if (HeartRateMeasurementService.mMeasuring) {
                    progressBeat.setProgress(0);
                    welcomeListener.mKeepRunning = true;

                    //Start the animations.
                    progressAnimation.start();

                    mStarted = true;
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
                        mRateText.setText(String.valueOf(heartRate));
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

    @Override
    protected void onDestroy() {
        Intent service = new Intent(this, HeartRateMeasurementService.class);
        service.putExtra("mode", HeartRateMeasurementService.MODE_NONE);
        startService(service);

        super.onDestroy();
    }

}

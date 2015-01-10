package com.ryansteckler.heartsync;

import android.app.ApplicationErrorReport;
import android.app.FragmentManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;

import fr.nicolaspomepuy.androidwearcrashreport.mobile.CrashInfo;
import fr.nicolaspomepuy.androidwearcrashreport.mobile.CrashReport;


public class MainActivity extends FragmentActivity implements MainFragment.OnMainFragmentInteractionListener, ChartFragment.OnChartFragmentInteractionListener{

    private ViewPager mPager;
    private PagerAdapter mPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Instantiate a ViewPager and a PagerAdapter.
//        mPager = (ViewPager) findViewById(R.id.pager);

//        mPagerAdapter = new SliderAdapter(getSupportFragmentManager());
//        mPager.setAdapter(mPagerAdapter);
//        mPager.setPageTransformer(false, new SliderTransformer());


        CrashReport.getInstance(this).setOnCrashListener(new CrashReport.IOnCrashListener() {
            @Override
            public void onCrashReceived(CrashInfo crashInfo) {
                // Manage the crash
                CrashReport.getInstance(MainActivity.this).reportToPlayStore(MainActivity.this);
            }
        });
    }


    @Override
    public void onChartFragmentInteraction(Uri uri) {

    }

    @Override
    public void onMainFragmentInteraction(Uri uri) {

    }
}

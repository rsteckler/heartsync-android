package com.ryansteckler.heartsync;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.SparseArray;
import android.view.ViewGroup;

/**
 * Created by rsteckler on 1/7/15.
 */
public class SliderAdapter extends FragmentStatePagerAdapter {

    private static final int NUM_PAGES = 2;

    public SliderAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        if (position == 1) {
            return ChartFragment.newInstance(null, null);
        }
        else if (position == 0) {
            return MainFragment.newInstance(null, null);
        }

        return null;
    }

    @Override
    public int getCount() {
        return NUM_PAGES;
    }

    SparseArray<Fragment> registeredFragments = new SparseArray<Fragment>();

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        registeredFragments.put(position, fragment);
        return fragment;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        registeredFragments.remove(position);
        super.destroyItem(container, position, object);
    }

    public Fragment getRegisteredFragment(int position) {
        return registeredFragments.get(position);
    }

}

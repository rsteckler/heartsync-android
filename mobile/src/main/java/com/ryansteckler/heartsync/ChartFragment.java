package com.ryansteckler.heartsync;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.Field;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.listener.LineChartOnValueSelectListener;
import lecho.lib.hellocharts.listener.ViewportChangeListener;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PreviewLineChartView;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link ChartFragment.OnChartFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link ChartFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class ChartFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private static final String TAG = "HeartSync-ChartFragment";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private LineChartView mLineChart;
    private PreviewLineChartView mLineChartPreview;
    private LineChartData mChartData;
    private LineChartData mPreviewData;
    private static final float PREVIEW_MULTIPLIER = 4;
    private OnChartFragmentInteractionListener mListener;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ChartFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ChartFragment newInstance(String param1, String param2) {
        ChartFragment fragment = new ChartFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public ChartFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mFitDataReceiver, new IntentFilter("fitHistory"));

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_chart, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mLineChart = (LineChartView) view.findViewById(R.id.chartHeartRate);
        mLineChartPreview = (PreviewLineChartView) view.findViewById(R.id.chartHeartRatePreview);

        requestFitData();

        // Disable zoom/scroll for previewed chart, visible chart ranges depends on preview chart viewport so
        // zoom/scroll is unnecessary.
        mLineChart.setZoomEnabled(false);
        mLineChart.setMaxZoom(30);
        mLineChart.setZoomLevelWithAnimation(1, 1, 30);
        mLineChart.setZoomType(ZoomType.HORIZONTAL);
        mLineChart.setScrollEnabled(true);
        mLineChartPreview.setScrollEnabled(false);
        mLineChartPreview.setZoomEnabled(false);

        mLineChart.setViewportChangeListener(new ViewportListener());
        mLineChart.setOnValueTouchListener(new LineChartOnValueSelectListener() {
            @Override
            public void onValueSelected(int lineIndex, int pointIndex, PointValue value) {
                Toast.makeText(getActivity(), new String(value.getLabel()), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onValueDeselected() {

            }
        });

        mLineChart.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View pv, MotionEvent motionEvent) {
                pv.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            }
        });


    }

    // Our handler for received Intents. This will be called whenever an Intent
    // with an action named "custom-event-name" is broadcasted.
    private BroadcastReceiver mFitDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            if (intent.hasExtra("fitHistory")) {

                final ArrayList<PointValue> historyData = (ArrayList<PointValue>)intent.getSerializableExtra("fitHistory");
                if (historyData.size() > 0) {

                    Line line = new Line(historyData);
                    line.setColor(ChartUtils.COLOR_RED);
                    line.setHasPoints(true);// too many values so don't draw points.

                    List<Line> lines = new ArrayList<Line>();
                    lines.add(line);


                    mChartData = new LineChartData(lines);

                    //Calculate the bottom axis labels
                    long currentAxisTimestamp = (long) historyData.get(0).getX();
                    long lastTimestamp = (long) historyData.get(historyData.size() - 1).getX();

                    List<AxisValue> axisValues = new ArrayList<AxisValue>();


                    final long interval = 1000 * 60 * 60;
                    while (currentAxisTimestamp <= lastTimestamp) {
                        //Add an axis with this timestamp

                        //Get the label for this time.
                        String curLabel = getLabelForTimestamp(currentAxisTimestamp);
                        axisValues.add(new AxisValue(currentAxisTimestamp, curLabel.toCharArray()));
                        currentAxisTimestamp += interval;
                    }

                    mChartData.setAxisXBottom(new Axis(axisValues).setName("Time"));
                    mChartData.setAxisYLeft(Axis.generateAxisFromRange(30f, 190f, 10f).setHasLines(true).setName("Heart Rate"));

                    // prepare preview data, is better to use separate deep copy for preview chart.
                    // Set color to grey to make preview area more visible.
                    mPreviewData = new LineChartData(mChartData);
                    mPreviewData.getLines().get(0).setColor(ChartUtils.DEFAULT_DARKEN_COLOR);

                    mLineChart.setLineChartData(mChartData);
                    mLineChartPreview.setLineChartData(mPreviewData);
                    mLineChartPreview.getLineChartData().getLines().get(0).setHasPoints(false);

                    Viewport tempViewport = new Viewport(mLineChartPreview.getMaximumViewport());
                    tempViewport.bottom = 30;
                    tempViewport.top = 190;
                    mLineChartPreview.setMaximumViewport(tempViewport);
                    mLineChartPreview.setCurrentViewport(tempViewport);
                    mLineChart.setMaximumViewport(tempViewport);
                    mLineChart.setCurrentViewport(tempViewport);

                    previewX(true);
                }
            }
        }
    };

    private String getLabelForTimestamp(long timestamp) {
        String label = "";
        Calendar pointTime = Calendar.getInstance();
        pointTime.setTimeInMillis(timestamp);
        if (pointTime.get(Calendar.HOUR_OF_DAY) == 0) {
            //Include the date
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
            label = sdf.format(pointTime.getTime());
        } else {
            //Insert the hour

            //Special case for noon
            if (pointTime.get(Calendar.HOUR) == 0 && pointTime.get(Calendar.AM_PM) == Calendar.PM) {
                label = "noon";
            } else {
                label = pointTime.get(Calendar.HOUR) + (pointTime.get(Calendar.AM_PM) == Calendar.AM ? "am" : "pm");
            }

        }
        return label;
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mFitDataReceiver);
        super.onDestroy();
    }

    public void requestFitData() {

        //Clear the chart, and show loading.
        mLineChart.setLineChartData(null);
        mLineChartPreview.setLineChartData(null);

        //Start Service and wait for broadcast
        Intent service = new Intent(getActivity(), UpdateFitService.class);
        service.putExtra("requestType", UpdateFitService.TYPE_GET_HEART_RATE_DATA);
        getActivity().startService(service);


    }


    private void previewX(boolean animate) {

        mLineChart.setZoomLevelWithAnimation(mLineChart.getMaximumViewport().right - (1000 * 60 * 60), mLineChart.getMaximumViewport().centerY(), 30);
        //Weirdness because I can't just scroll or zoom to the right side of the graph because the rightmost
        //point isn't considered "inside" the graph.  I also can't just subtract 1 because of floating math.
        //Instead, we'll aim somewhere on the right side by subtracting one "hour"

//        Viewport tempViewport = new Viewport(mLineChart.getMaximumViewport());
//        float dx = tempViewport.width() / PREVIEW_MULTIPLIER;
//        tempViewport.inset(dx, 0);
//        if(animate) {
//            mLineChartPreview.setCurrentViewportWithAnimation(tempViewport);
//        }else{
//            mLineChartPreview.setCurrentViewport(tempViewport);
//        }
//        mLineChartPreview.setZoomType(ZoomType.HORIZONTAL);
    }


    /**
     * Viewport listener for  chart(uppoer one). in {@link #onViewportChanged(Viewport)} method change
     * viewport of upper chart.
     */
    private class ViewportListener implements ViewportChangeListener {

        @Override
        public void onViewportChanged(Viewport newViewport) {
            // don't use animation, it is unnecessary when using preview chart.
            mLineChartPreview.setCurrentViewport(newViewport);
        }

    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onChartFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnChartFragmentInteractionListener) activity;
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
    public interface OnChartFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onChartFragmentInteraction(Uri uri);
    }



}

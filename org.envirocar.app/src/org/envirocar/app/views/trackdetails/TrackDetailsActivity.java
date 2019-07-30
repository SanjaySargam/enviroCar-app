/**
 * Copyright (C) 2013 - 2019 the enviroCar community
 *
 * This file is part of the enviroCar app.
 *
 * The enviroCar app is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The enviroCar app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with the enviroCar app. If not, see http://www.gnu.org/licenses/.
 */
package org.envirocar.app.views.trackdetails;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.widget.Toolbar;
import androidx.transition.TransitionManager;

import android.transition.Slide;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.TileSet;

import org.envirocar.app.R;
import org.envirocar.app.handler.PreferencesHandler;
import org.envirocar.app.injection.BaseInjectorActivity;
import org.envirocar.app.main.BaseApplicationComponent;
import org.envirocar.app.views.utils.MapUtils;
import org.envirocar.core.entity.Car;
import org.envirocar.core.entity.Measurement;
import org.envirocar.core.entity.Track;
import org.envirocar.core.exception.FuelConsumptionException;
import org.envirocar.core.exception.NoMeasurementsException;
import org.envirocar.core.exception.UnsupportedFuelTypeException;
import org.envirocar.core.logging.Logger;
import org.envirocar.core.trackprocessing.statistics.TrackStatisticsProvider;
import org.envirocar.core.utils.CarUtils;
import org.envirocar.storage.EnviroCarDB;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTouch;
import rx.schedulers.Schedulers;


/**
 * @author dewall
 */
public class TrackDetailsActivity extends BaseInjectorActivity {
    private static final Logger LOG = Logger.getLogger(TrackDetailsActivity.class);

    private static final String EXTRA_TRACKID = "org.envirocar.app.extraTrackID";
    private static final String EXTRA_TITLE = "org.envirocar.app.extraTitle";

    public static void navigate(Activity activity, View transition, int trackID) {
        Intent intent = new Intent(activity, TrackDetailsActivity.class);
        intent.putExtra(EXTRA_TRACKID, trackID);
        ActivityOptionsCompat options = ActivityOptionsCompat
                //.makeSceneTransitionAnimation(activity, transition, "transition_track_details");
                .makeBasic();

        ActivityCompat.startActivity(activity, intent, options.toBundle());
    }

    private static final DecimalFormat DECIMAL_FORMATTER_TWO_DIGITS = new DecimalFormat("#.##");
    private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance();
    private static final DateFormat UTC_DATE_FORMATTER = new SimpleDateFormat("HH:mm:ss", Locale
            .ENGLISH);

    static {
        UTC_DATE_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    }


    @Inject
    protected EnviroCarDB mEnvirocarDB;

    TrackSpeedMapOverlay trackMapOverlay;

    @BindView(R.id.activity_track_details_fab)
    protected FloatingActionButton mFAB;
    protected MapboxMap mapboxMap;
    @BindView(R.id.activity_track_details_header_map)
    protected MapView mMapView;
    @BindView(R.id.activity_track_details_header_toolbar)
    protected Toolbar mToolbar;
    @BindView(R.id.activity_track_details_attr_description_value)
    protected TextView mDescriptionText;
    @BindView(R.id.track_details_attributes_header_duration)
    protected TextView mDurationText;
    @BindView(R.id.track_details_attributes_header_distance)
    protected TextView mDistanceText;
    @BindView(R.id.activity_track_details_attr_begin_value)
    protected TextView mBeginText;
    @BindView(R.id.activity_track_details_attr_end_value)
    protected TextView mEndText;
    @BindView(R.id.activity_track_details_attr_car_value)
    protected TextView mCarText;
    @BindView(R.id.activity_track_details_attr_emission_value)
    protected TextView mEmissionText;
    @BindView(R.id.activity_track_details_attr_consumption_value)
    protected TextView mConsumptionText;
    protected MapboxMap mapboxMapExpanded;
    @BindView(R.id.activity_track_details_expanded_map)
    protected MapView mMapViewExpanded;
    @BindView(R.id.activity_track_details_expanded_map_container)
    protected ConstraintLayout mMapViewExpandedContainer;
    @BindView(R.id.activity_track_details_appbar_layout)
    protected AppBarLayout mAppBarLayout;
    @BindView(R.id.activity_track_details_scrollview)
    protected NestedScrollView mNestedScrollView;
    @BindView(R.id.activity_track_details_expanded_map_cancel)
    protected ImageView mMapViewExpandedCancel;
    @BindView(R.id.activity_track_details_header_map_container)
    protected FrameLayout mMapViewContainer;
    @BindView(R.id.consumption_container)
    protected RelativeLayout mConsumptionContainer;
    @BindView(R.id.co2_container)
    protected RelativeLayout mCo2Container;
    @BindView(R.id.descriptionTv)
    protected TextView descriptionTv;
    @BindView(R.id.fragment_dashboard_frag_map_follow_fab)
    protected FloatingActionButton mCentreFab;

    private List<Point> routeCoordinates = new ArrayList<>();
    protected ArrayList<LatLng> latLngs = new ArrayList<>();
    private LatLngBounds mTrackBoundingBox;
    private LatLngBounds mViewBoundingBox;
    private Track track;

    private boolean mIsCentredOnTrack;

    @Override
    protected void injectDependencies(BaseApplicationComponent baseApplicationComponent) {
        baseApplicationComponent.inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initActivityTransition();
        setContentView(R.layout.activity_track_details_layout);
        // Inject all annotated views.
        ButterKnife.bind(this);
        mMapView.onCreate(savedInstanceState);
        mMapViewExpanded.onCreate(savedInstanceState);
        supportPostponeEnterTransition();

        // Set the toolbar as default actionbar.
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Get the track to show.
        int mTrackID = getIntent().getIntExtra(EXTRA_TRACKID, -1);
        Track.TrackId trackid = new Track.TrackId(mTrackID);
        Track track = mEnvirocarDB.getTrack(trackid)
                .subscribeOn(Schedulers.io())
                .toBlocking()
                .first();
        this.track = track;
        trackMapOverlay = new TrackSpeedMapOverlay(track);
        String itemTitle = track.getName();
        CollapsingToolbarLayout collapsingToolbarLayout = findViewById
                (R.id.collapsing_toolbar);
        collapsingToolbarLayout.setTitle(itemTitle);
        collapsingToolbarLayout.setExpandedTitleColor(getResources().getColor(android.R.color
                .transparent));
        collapsingToolbarLayout.setStatusBarScrimColor(getResources().getColor(android.R.color
                .transparent));

        TextView title = findViewById(R.id.title);
        title.setText(itemTitle);
        // Initialize the mapview and the trackpath
        initMapView();
        initViewValues(track);

        updateStatusBarColor();
        mIsCentredOnTrack = true;
        mCentreFab.setVisibility(View.VISIBLE);
        mFAB.setOnClickListener(v -> {
           TrackStatisticsActivity.createInstance(TrackDetailsActivity.this, mTrackID);
        });

        //closing the expanded mapview on "cancel" button clicked
        mMapViewExpandedCancel.setOnClickListener(v-> closeExpandedMapView());
        //expanding the expandable mapview on clicking the framelayout which is surrounded by header map view in collapsingtoolbarlayout
        mMapViewContainer.setOnClickListener(v-> expandMapView());
    }

    private void updateStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Set the statusbar to be transparent with a grey touch
            getWindow().setStatusBarColor(Color.parseColor("#3f3f3f3f"));
        }
    }

    @OnTouch(R.id.activity_track_details_expanded_map)
    protected boolean onTouchMapView() {
        if (mIsCentredOnTrack) {
            mIsCentredOnTrack = false;
            TransitionManager.beginDelayedTransition((ViewGroup) mMapViewExpandedContainer,new androidx.transition.Slide(Gravity.RIGHT));
            mCentreFab.setVisibility(View.VISIBLE);
        }
        return false;
    }

    @OnClick(R.id.fragment_dashboard_frag_map_follow_fab)
    protected void onClickFollowFab() {
        final LatLngBounds viewBbox = mViewBoundingBox;//trackMapOverlay.getViewBoundingBox();
        if (!mIsCentredOnTrack) {
            mIsCentredOnTrack = true;
            TransitionManager.beginDelayedTransition((ViewGroup)mMapViewExpandedContainer,new androidx.transition.Slide(Gravity.LEFT));
            mCentreFab.setVisibility(View.GONE);
            mapboxMapExpanded.easeCamera(CameraUpdateFactory.newLatLngBounds(viewBbox, 50),2500);
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        //if the expandable mapview is expanded, then close it first
        if(mMapViewExpandedContainer != null && mMapViewExpandedContainer.getVisibility() == View.VISIBLE){
            closeExpandedMapView();
        }else{
            super.onBackPressed();
        }
    }

    /**
     * Initializes the activity enter and return transitions of the activity.
     */
    private void initActivityTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Create a new slide transition.
            Slide transition = new Slide();
            transition.excludeTarget(android.R.id.statusBarBackground, true);
            Window window = getWindow();

            // Set the created transition as enter and return transition.
            window.setEnterTransition(transition);
            window.setReturnTransition(transition);
        }
    }

    /**
     * Initializes the MapView, its base layers and settings.
     */
    private void initMapView() {
        final LatLngBounds viewBbox = mViewBoundingBox;//trackMapOverlay.getViewBoundingBox();
        TileSet layer = MapUtils.getOSMTileLayer();
        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap tep) {

                tep.getUiSettings().setLogoEnabled(false);
                tep.getUiSettings().setAttributionEnabled(false);
                tep.setStyle(new Style.Builder().fromUrl("https://api.maptiler.com/maps/basic/style.json?key=YJCrA2NeKXX45f8pOV6c "), new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        initRouteCoordinates(track);
                        GeoJsonSource geoJsonSource = new GeoJsonSource("source-id", FeatureCollection.fromFeatures(new Feature[] {Feature.fromGeometry(
                                LineString.fromLngLats(routeCoordinates)
                        )}));
                        style.addSource(geoJsonSource);
                        LineLayer lineLayer = new LineLayer("linelayer", "source-id").withSourceLayer("source-id").withProperties(
                                PropertyFactory.lineColor(Color.parseColor("#0065A0")),
                                PropertyFactory.lineWidth(3f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                        );
                        style.addLayer(lineLayer);
                        tep.moveCamera(CameraUpdateFactory.newLatLngBounds(mViewBoundingBox, 50));
                        setUpStartStopIcons(style, tep);
                        //style.addSource(trackMapOverlay.getGeoJsonSource());
                        //style.addLayer(trackMapOverlay.getLineLayer());

                    }
                });
                //tep.moveCamera(CameraUpdateFactory.newLatLngBounds(viewBbox, 50));
                mapboxMap = tep;
                mapboxMap.setMaxZoomPreference(layer.getMaxZoom());
                mapboxMap.setMinZoomPreference(layer.getMinZoom());
            }
        });

        mMapViewExpanded.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap1) {
                mapboxMap1.getUiSettings().setLogoEnabled(false);
                mapboxMap1.getUiSettings().setAttributionEnabled(false);
                mapboxMap1.setStyle(new Style.Builder().fromUrl("https://api.maptiler.com/maps/basic/style.json?key=YJCrA2NeKXX45f8pOV6c "), new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        initRouteCoordinates(track);
                        GeoJsonSource geoJsonSource = new GeoJsonSource("source-id", FeatureCollection.fromFeatures(new Feature[] {Feature.fromGeometry(
                                LineString.fromLngLats(routeCoordinates)
                        )}));
                        style.addSource(geoJsonSource);
                        LineLayer lineLayer = new LineLayer("linelayer", "source-id").withSourceLayer("source-id").withProperties(
                                PropertyFactory.lineColor(Color.parseColor("#0065A0")),
                                PropertyFactory.lineWidth(3f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                        );
                        style.addLayer(lineLayer);
                        mapboxMap1.moveCamera(CameraUpdateFactory.newLatLngBounds(mViewBoundingBox, 50));
                        setUpStartStopIcons(style, mapboxMap1);
                    }
                });
                mapboxMapExpanded = mapboxMap1;
                mapboxMapExpanded.setMaxZoomPreference(18);
                mapboxMapExpanded.setMinZoomPreference(1);
                //mapboxMapExpanded.getStyle().getLayer("linelayer").setProperties(visibility(Property.VISIBLE));
            }
        });
    }

    private void setUpStartStopIcons(@NonNull Style loadedMapStyle, @NonNull MapboxMap loadedMapBox) {
        loadedMapStyle.addImage("stop-marker",
                BitmapFactory.decodeResource(
                        TrackDetailsActivity.this.getResources(), R.drawable.stop_marker));

        loadedMapStyle.addImage("start-marker",
                BitmapFactory.decodeResource(
                        TrackDetailsActivity.this.getResources(), R.drawable.start_marker));

        int size = track.getMeasurements().size();
        List<Point> markerCoordinates = new ArrayList<>();
        if(size>=2)
        {
            LOG.info("Point 1:"+track.getMeasurements().get(0).getLongitude() + track.getMeasurements().get(0).getLatitude());
            LOG.info("Point 2:" + size + " "+track.getMeasurements().get(size-1).getLongitude() + track.getMeasurements().get(size-1).getLatitude());
            markerCoordinates.add(Point.fromLngLat(track.getMeasurements().get(0).getLongitude(),track.getMeasurements().get(0).getLatitude()));
            markerCoordinates.add(Point.fromLngLat(track.getMeasurements().get(size-1).getLongitude(),track.getMeasurements().get(size-1).getLatitude()));
            GeoJsonSource geoJsonSource = new GeoJsonSource("marker-source1", Feature.fromGeometry(
                    Point.fromLngLat(markerCoordinates.get(0).longitude(), markerCoordinates.get(0).latitude())));
            loadedMapStyle.addSource(geoJsonSource);

            SymbolLayer symbolLayer = new SymbolLayer("marker-layer1", "marker-source1");
            symbolLayer.withProperties(
                    PropertyFactory.iconImage("start-marker"),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
            );
            loadedMapStyle.addLayer(symbolLayer);
            geoJsonSource = new GeoJsonSource("marker-source2", Feature.fromGeometry(
                    Point.fromLngLat(markerCoordinates.get(1).longitude(), markerCoordinates.get(1).latitude())));
            loadedMapStyle.addSource(geoJsonSource);

            symbolLayer = new SymbolLayer("marker-layer2", "marker-source2");
            symbolLayer.withProperties(
                    PropertyFactory.iconImage("stop-marker"),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
            );
            loadedMapStyle.addLayer(symbolLayer);

        }
    }

    //function which expands the mapview
    private void expandMapView(){
        //mMapViewExpandedContainer.setVisibility(View.VISIBLE);
        mMapViewExpanded.setVisibility(View.VISIBLE);
        animateShowView(mMapViewExpandedContainer,R.anim.translate_slide_in_top_fragment);
        animateHideView(mAppBarLayout,R.anim.translate_slide_out_top_fragment);
        animateHideView(mNestedScrollView,R.anim.translate_slide_out_bottom);
        animateHideView(mFAB,R.anim.fade_out);
    }

    //function which closes the expanded mapview
    private void closeExpandedMapView(){
        final LatLngBounds viewBbox = mViewBoundingBox;//trackMapOverlay.getViewBoundingBox();
        //mMapViewExpanded.setVisibility(GONE);
        //mMapViewExpandedContainer.setVisibility(GONE);
        //mMapView.setVisibility(View.VISIBLE);
        //mAppBarLayout.setVisibility(View.VISIBLE);
        //mNestedScrollView.setVisibility(View.VISIBLE);
        //mFAB.setVisibility(View.VISIBLE);
        //mMapViewExpandedContainer.setVisibility(GONE);
        //mMapViewExpanded.setVisibility(View.INVISIBLE);
        //mAppBarLayout.setVisibility(View.VISIBLE);
        //mNestedScrollView.setVisibility(View.VISIBLE);
        //mFAB.setVisibility(View.VISIBLE);
        //mMapViewExpanded.setVisibility(GONE);
        mMapViewExpandedContainer.setVisibility(View.GONE);
        animateHideView(mMapViewExpandedContainer,R.anim.translate_slide_out_top_fragment);
        animateShowView(mAppBarLayout,R.anim.translate_slide_in_top_fragment);
        animateShowView(mNestedScrollView,R.anim.translate_slide_in_bottom_fragment);
        animateShowView(mFAB,R.anim.fade_in);
        mapboxMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mViewBoundingBox, 50), new MapboxMap.CancelableCallback() {
            @Override
            public void onCancel() {
                LOG.info("mMapView move camera was cancelled.");
            }

            @Override
            public void onFinish() {
                LOG.info("mMapView move camera finished.");
            }
        });
        LOG.info("mMapViewExpanded visibility: " + mMapViewExpanded.getVisibility());
        LOG.info("mMapViewExpandedContainer visibility: " + mMapViewExpandedContainer.getVisibility());
        LOG.info("mMapView visibility: " + mMapView.getVisibility());
    }

    private void initRouteCoordinates(Track track) {
        // Create a list to store our line coordinates.
        routeCoordinates.clear();

        List<Measurement> temp = track.getMeasurements();
        for(Measurement measurement : temp)
        {
            routeCoordinates.add(Point.fromLngLat(measurement.getLongitude(),measurement.getLatitude()));
        }
        LOG.info("routeCoordinates of " + track.getName() +": " + routeCoordinates.size());
        LOG.info(routeCoordinates.get(0).toString());
        latLngs.clear();
        for(int i = 0; i< routeCoordinates.size(); ++i){
            latLngs.add(new LatLng(routeCoordinates.get(i).latitude(), routeCoordinates.get(i).longitude()));
        }

        mTrackBoundingBox = new LatLngBounds.Builder()
                .includes(latLngs)
                .build();

        // The view bounding box of the pathoverlay
        mViewBoundingBox = LatLngBounds.from(
                mTrackBoundingBox.getLatNorth() + 0.01,
                mTrackBoundingBox.getLonEast() + 0.01,
                mTrackBoundingBox.getLatSouth() - 0.01,
                mTrackBoundingBox.getLonWest() - 0.01);
    }

    //general function to animate the view and hide it
    private void animateHideView(View view, int animResource){
       Animation animation = AnimationUtils.loadAnimation(getApplicationContext(),animResource);
       view.startAnimation(animation);
       view.setVisibility(View.GONE);
    }

    //general function to animate and show the view
    private void animateShowView(View view, int animResource){
        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(),animResource);
        view.setVisibility(View.VISIBLE);
        view.startAnimation(animation);
    }

    private void initViewValues(Track track) {
        try {
            final String text = UTC_DATE_FORMATTER.format(new Date(
                    track.getDuration()));
            mDistanceText.setText(String.format("%s km",
                    DECIMAL_FORMATTER_TWO_DIGITS.format(((TrackStatisticsProvider) track)
                            .getDistanceOfTrack())));
            mDurationText.setText(text);

            mDescriptionText.setText(track.getDescription());
            mCarText.setText(CarUtils.carToStringWithLinebreak(track.getCar()));
            mBeginText.setText(DATE_FORMAT.format(new Date(track.getStartTime())));
            mEndText.setText(DATE_FORMAT.format(new Date(track.getEndTime())));

            // show consumption and emission either when the fuel type of the track's car is
            // gasoline or the beta setting has been enabled.
            if(!track.hasProperty(Measurement.PropertyKey.SPEED)){
                mConsumptionContainer.setVisibility(View.GONE);
                mCo2Container.setVisibility(View.GONE);
                descriptionTv.setText(R.string.gps_track_details);
            }
            else if (track.getCar().getFuelType() == Car.FuelType.GASOLINE ||
                    PreferencesHandler.isDieselConsumptionEnabled(this)) {
                mEmissionText.setText(DECIMAL_FORMATTER_TWO_DIGITS.format(
                        ((TrackStatisticsProvider) track).getGramsPerKm()) + " g/km");
                mConsumptionText.setText(
                        String.format("%s l/h\n%s l/100 km",
                                DECIMAL_FORMATTER_TWO_DIGITS.format(
                                        ((TrackStatisticsProvider) track)
                                                .getFuelConsumptionPerHour()),

                                DECIMAL_FORMATTER_TWO_DIGITS.format(
                                        ((TrackStatisticsProvider) track).getLiterPerHundredKm())));
            } else {
                mEmissionText.setText(R.string.track_list_details_diesel_not_supported);
                mConsumptionText.setText(R.string.track_list_details_diesel_not_supported);
                mEmissionText.setTextColor(Color.RED);
                mConsumptionText.setTextColor(Color.RED);
            }

        } catch (FuelConsumptionException e) {
            e.printStackTrace();
        } catch (NoMeasurementsException e) {
            e.printStackTrace();
        } catch (UnsupportedFuelTypeException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
        mMapViewExpanded.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        supportStartPostponedEnterTransition();
        mMapView.onResume();
        mMapViewExpanded.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
        mMapViewExpanded.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
        mMapViewExpanded.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
        mMapViewExpanded.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
        mMapViewExpanded.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
        mMapViewExpanded.onSaveInstanceState(outState);
    }
}

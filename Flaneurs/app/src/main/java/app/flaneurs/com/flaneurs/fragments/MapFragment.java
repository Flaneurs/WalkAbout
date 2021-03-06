package app.flaneurs.com.flaneurs.fragments;

import android.Manifest;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import app.flaneurs.com.flaneurs.FlaneurApplication;
import app.flaneurs.com.flaneurs.R;
import app.flaneurs.com.flaneurs.models.Post;
import app.flaneurs.com.flaneurs.utils.LocationProvider;
import app.flaneurs.com.flaneurs.utils.ParseProxyObject;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MapFragment extends SupportMapFragment implements LocationProvider.ILocationListener {

    public static final String TAG = MapFragment.class.getSimpleName();
    public static final String ARG_SHOULD_TRACK_LOCATION = "ARG_SHOULD_TRACK_LOCATION";
    public static final String ARG_SHOULD_LOCK_MAP = "ARG_SHOULD_LOCK_MAP";
    public static final String ARG_LAT_LNG = "ARG_LAT_LNG";
    public static final String ARG_POSTS = "ARG_POSTS";

    private GoogleMap mGoogleMap;
    private LocationProvider mLocationProvider;
    private PostMarkerGetter mPostMarkerGetter;

    private Location mLocation;

    private boolean shouldTrackLocation;
    private boolean shouldLockMap;
    private ArrayList<ParseProxyObject> posts;
    private LatLng point;

    public static MapFragment newInstance(boolean shouldTrackLocation, boolean shouldLockMap, LatLng latLng, ArrayList<ParseProxyObject> parseProxyObjects) {
        Log.d(TAG, "newInstance");
        MapFragment mapFragment = new MapFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SHOULD_TRACK_LOCATION, shouldTrackLocation);
        args.putBoolean(ARG_SHOULD_LOCK_MAP, shouldLockMap);
        args.putParcelable(ARG_LAT_LNG, latLng);
        args.putSerializable(ARG_POSTS, parseProxyObjects);
        mapFragment.setArguments(args);
        return mapFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        if (arguments != null) {
            shouldTrackLocation = arguments.getBoolean(ARG_SHOULD_TRACK_LOCATION);
            shouldLockMap = arguments.getBoolean(ARG_SHOULD_LOCK_MAP);
            point = arguments.getParcelable(ARG_LAT_LNG);
            posts = (ArrayList<ParseProxyObject>) arguments.getSerializable(ARG_POSTS);
        } else {
            shouldTrackLocation = false;
            point = null;
            posts = null;
        }

        mPostMarkerGetter = new PostMarkerGetter();

        getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                Log.d(TAG, "onMapReady");
                loadMap(googleMap);
            }
        });
    }

    protected void loadMap(GoogleMap googleMap) {
        Log.d(TAG, "loadMap");
        mGoogleMap = googleMap;
        if (mGoogleMap != null) {
            Log.d(TAG, "Map Fragment was loaded properly.");

            DisplayMetrics metrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

            int topPadding = (shouldTrackLocation || shouldLockMap) ? 0 : (int) (50.0/1280 * metrics.heightPixels);
            int bottomPadding = (shouldTrackLocation) ? (int) (130.0/1280 * metrics.heightPixels) : 0;
            mGoogleMap.setPadding(0, topPadding, 0, bottomPadding);
            if (shouldLockMap) {
                mGoogleMap.getUiSettings().setAllGesturesEnabled(false);
            } else {
                mGoogleMap.setInfoWindowAdapter(new CustomWindowAdapter(getActivity().getLayoutInflater()));
            }

            if (point != null) {
                markLatLng(point);
            } else if (posts != null && posts.size() > 0) {
                final LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                for (ParseProxyObject post : posts) {
                    LatLng markerPoint = addMarkerForPost(post, false);
                    bounds.include(markerPoint);
                }
                LatLngBounds newBounds = bounds.build();

                if (!shouldTrackLocation) {
                    mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newBounds.getCenter(), 5.0f));
                    mGoogleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                        @Override
                        public void onMapLoaded() {
                            mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 50));
                        }
                    });
                }
            }
            if (shouldTrackLocation) {
                MapFragmentPermissionsDispatcher.getMyLocationWithCheck(this);
            }
        } else {
            Toast.makeText(getContext(), "Error - Map was null!", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Map was null.");
        }
    }

    public void populateMapWithPosts(ArrayList<ParseProxyObject> posts) {
        Handler handler = new Handler();
        for (int i=0; i<posts.size(); i++) {
            final ParseProxyObject post = posts.get(i);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    addMarkerForPost(post, true);
                }
            }, 50 * i);
        }
    }

    public LatLng addMarkerForPost(ParseProxyObject post, boolean withAnimation) {
        double[] latLng = post.getParseGeoPointArray(Post.KEY_POST_LOCATION);
        LatLng point = new LatLng(latLng[0], latLng[1]);
        Marker marker = addMarkerAtLatLng(point, post.getInt(Post.KEY_POST_UPVOTECOUNT), false);
        mPostMarkerGetter.putPostMarker(post, marker);

        if (withAnimation) {
            marker.setAlpha(0);
            dropPinEffect(marker);
        }

        return point;
    }

    private Marker addMarkerAtLatLng(LatLng latLng, int upVoteCount, boolean isOwn) {
        int resource;
        if (isOwn) {
            resource = R.drawable.walk_marker_green;
        } else if (upVoteCount < 5) {
            resource = R.drawable.walk_marker_0;
        } else if (upVoteCount < 10) {
            resource = R.drawable.walk_marker_5;
        } else if (upVoteCount < 15) {
            resource = R.drawable.walk_marker_10;
        } else if (upVoteCount < 20) {
            resource = R.drawable.walk_marker_15;
        } else {
            resource = R.drawable.walk_marker_20;
        }

        BitmapDescriptor defaultMarker = BitmapDescriptorFactory.fromResource(resource);
        return mGoogleMap.addMarker(new MarkerOptions()
                .position(latLng)
                .icon(defaultMarker));
    }

    public void markLatLng(LatLng latLng) {
        mGoogleMap.clear();
        addMarkerAtLatLng(latLng, 0, true);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
        mGoogleMap.moveCamera(cameraUpdate);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MapFragmentPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void getMyLocation() {
        if (mGoogleMap != null) {
            mGoogleMap.setMyLocationEnabled(true);
            mLocationProvider = FlaneurApplication.getInstance().locationProvider;
            mLocationProvider.addListener(this);
        }
    }

    public void onLocationChanged(Location location) {
        if (mLocation == null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            CameraUpdate cameraUpdate1 = CameraUpdateFactory.newLatLngZoom(latLng, 10);
            CameraUpdate cameraUpdate2 = CameraUpdateFactory.newLatLngZoom(latLng, 15);
            mGoogleMap.moveCamera(cameraUpdate1);
            mGoogleMap.animateCamera(cameraUpdate2);
        }
        mLocation = location;
    }


    public void onPostPickup(String postId) {
        if (mPostMarkerGetter != null) {
            Marker marker = mPostMarkerGetter.getMarkerForPostId(postId);
            if (marker != null) {
                if (marker.isInfoWindowShown()) {
                    marker.hideInfoWindow();
                }
                pickUpPinEffect(marker);
            }
        }
    }

    private void dropPinEffect(final Marker marker) {
        // Handler allows us to repeat a code block after a specified delay
        final android.os.Handler handler = new android.os.Handler();
        final long start = SystemClock.uptimeMillis();
        final long bounceDuration = 1500;
        final long fadeDuration = 750;

        // Use the bounce interpolator
        final android.view.animation.Interpolator bounceInterpolator = new BounceInterpolator();
        final android.view.animation.Interpolator linearInterpolator = new LinearInterpolator();

        // Animate marker with a bounce updating its position every 15 ms
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;

                // Calculate t for bounce based on elapsed time
                float tb_init = Math.max(1 - bounceInterpolator.getInterpolation((float) elapsed / bounceDuration), 0);
                float tb = ((float)elapsed/bounceDuration < 0.66) ? tb_init : tb_init * 0.1f;
                // Set the anchor
                marker.setAnchor(0.5f, 1.0f + 8 * tb);

                // Calculate t for fade based on elapsed time
                float ta = Math.min(linearInterpolator.getInterpolation((float) elapsed / fadeDuration), 1);
                // Update alpha
                marker.setAlpha(ta);

                if (tb > 0.0 || ta < 1.0) {
                    // Post this event again 15ms from now
                    handler.postDelayed(this, 15);
                }
            }
        });
    }

    private void pickUpPinEffect(final Marker marker) {
        // Handler allows us to repeat a code block after a specified delay
        final android.os.Handler handler = new android.os.Handler();
        final long start = SystemClock.uptimeMillis();
        final long duration = 500;

        // Use the bounce interpolator
        final android.view.animation.Interpolator interpolator = new AnticipateInterpolator();

        // Animate marker with a bounce updating its position every 15 ms
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;

                // Calculate t for bounce based on elapsed time
                float t = Math.min(interpolator.getInterpolation((float) elapsed / duration), 1);

                // Set the anchor
                marker.setAnchor(0.5f, 1.0f + 8 * t);
                marker.setAlpha(1-t);

                if (t < 1.0) {
                    // Post this event again 15ms from now
                    handler.postDelayed(this, 15);
                } else {
                    // done elapsing, hide from map
                    marker.remove();
                    mPostMarkerGetter.removeMarker(marker);
                }
            }
        });
    }

    class CustomWindowAdapter implements GoogleMap.InfoWindowAdapter {
        LayoutInflater mInflater;

        public CustomWindowAdapter(LayoutInflater i) {
            mInflater = i;
        }

        // This defines the contents within the info window based on the marker
        @Override
        public View getInfoContents(Marker marker) {
            // Get the associated post, bail if null
            ParseProxyObject post = mPostMarkerGetter.getPostForMarkerId(marker.getId());
            if (post == null) {
                return null;
            }

            // Getting view from the layout file
            View v = mInflater.inflate(R.layout.custom_info_window, null);

            // Populate fields
            TextView tvInfoWindowAddress = (TextView) v.findViewById(R.id.tvInfoWindowAddress);
            String addressString = post.getString(Post.KEY_POST_ADDRESS);
            if (addressString == null || addressString.length() == 0) {
                tvInfoWindowAddress.setText(R.string.unknown_address_text);
            } else {
                tvInfoWindowAddress.setText(addressString);
            }

            TextView tvInfowWindowViews = (TextView) v.findViewById(R.id.tvInfoWindowViews);
            String viewCountString = String.format("%d", post.getInt(Post.KEY_POST_VIEWCOUNT));
            tvInfowWindowViews.setText(viewCountString);

            TextView tvInfoWindowUpvotes = (TextView) v.findViewById(R.id.tvInfoWindowUpvotes);
            String upvoteCountString = String.format("%d", post.getInt(Post.KEY_POST_UPVOTECOUNT));
            tvInfoWindowUpvotes.setText(upvoteCountString);

            // Return info window contents
            return v;
        }

        // This changes the frame of the info window; returning null uses the default frame.
        // This is just the border and arrow surrounding the contents specified above
        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }
    }

    class PostMarkerGetter {
        private Map<String, ParseProxyObject> markerPostMap;
        private Map<String, Marker> postMarkerMap;

        public PostMarkerGetter() {
            markerPostMap = new HashMap<>();
            postMarkerMap = new HashMap<>();
        }

        public void putPostMarker(ParseProxyObject post, Marker marker) {
            markerPostMap.put(marker.getId(), post);
            postMarkerMap.put(post.getObjectId(), marker);
        }

        public Marker getMarkerForPostId(String postId) {
            return postMarkerMap.get(postId);
        }

        public ParseProxyObject getPostForMarkerId(String markerId) {
            return markerPostMap.get(markerId);
        }

        public void removePost(ParseProxyObject post) {
            Marker marker = getMarkerForPostId(post.getObjectId());
            removeMarkerForPostId(post.getObjectId());
            removePostForMarkerId(marker.getId());
        }

        public void removeMarker(Marker marker) {
            ParseProxyObject post = getPostForMarkerId(marker.getId());
            removeMarkerForPostId(post.getObjectId());
            removePostForMarkerId(marker.getId());
        }

        public void removeMarkerForPostId(String postId) {
            postMarkerMap.remove(postId);
        }

        public void removePostForMarkerId(String markerId) {
            markerPostMap.remove(markerId);
        }
    }
}

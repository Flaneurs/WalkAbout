package app.flaneurs.com.flaneurs.activities;

import android.Manifest;
import android.animation.Animator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.transition.Explode;
import android.transition.Transition;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.Toast;

import com.astuetz.PagerSlidingTabStrip;
import com.facebook.appevents.AppEventsLogger;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import app.flaneurs.com.flaneurs.FlaneurApplication;
import app.flaneurs.com.flaneurs.R;
import app.flaneurs.com.flaneurs.adapters.MapStreamPagerAdapter;
import app.flaneurs.com.flaneurs.fragments.MapFragment;
import app.flaneurs.com.flaneurs.fragments.StreamFragment;
import app.flaneurs.com.flaneurs.models.Post;
import app.flaneurs.com.flaneurs.models.User;
import app.flaneurs.com.flaneurs.services.PickupService;
import app.flaneurs.com.flaneurs.utils.BadgeDrawable;
import app.flaneurs.com.flaneurs.utils.LocationProvider;
import app.flaneurs.com.flaneurs.utils.ParseProxyObject;
import app.flaneurs.com.flaneurs.utils.RevealLayout;
import app.flaneurs.com.flaneurs.utils.Utils;
import butterknife.Bind;
import butterknife.ButterKnife;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class DiscoverActivity extends AppCompatActivity implements LocationProvider.ILocationListener {

    public static final String TAG = DiscoverActivity.class.getSimpleName();

    @Bind(R.id.vpViewPager)
    ViewPager viewPager;

    @Bind(R.id.psTabs)
    PagerSlidingTabStrip slidingTabStrip;

    RevealLayout mRevealLayout;
    View mRevealView;

    private LocationProvider mLocationProvider;
    private MapFragment mMapFragment;
    private StreamFragment mStreamFragment;

    private Location mLocation;

    private FloatingActionButton mFab;

    public final String APP_TAG = "flaneurs";
    public final static int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1034;
    public String photoFileName = "photo.jpg";
    public final static int POST_REQUEST_CODE = 42;

    private MenuItem mInboxItem;

    private Transition.TransitionListener mEnterTransitionListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);
        ButterKnife.bind(this);
        DiscoverActivityPermissionsDispatcher.getMyLocationWithCheck(this);
        FlaneurApplication.getInstance().pickupService.onColdLaunch();

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(PickupService.PICKUP_EVENT));

        ViewGroup vg = (ViewGroup) (getWindow().getDecorView().getRootView());
        LayoutInflater myinflater = getLayoutInflater();
        View customView = myinflater.inflate(R.layout.reveal_layout, null);
        vg.addView(customView);

        mMapFragment = MapFragment.newInstance(true, false, null, null);

        StreamFragment.StreamConfiguration streamConfiguration = new StreamFragment.StreamConfiguration();
        streamConfiguration.setStreamType(StreamFragment.StreamType.AllPosts);
        mStreamFragment = StreamFragment.createInstance(streamConfiguration);

        viewPager.setAdapter(new MapStreamPagerAdapter(getSupportFragmentManager(), mMapFragment, mStreamFragment));

        slidingTabStrip.setViewPager(viewPager);

        mRevealLayout = (RevealLayout) customView.findViewById(R.id.reveal_layout);
        mRevealView = customView.findViewById(R.id.reveal_view);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setVisibility(View.INVISIBLE);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFab.setClickable(false);
                mFab.setVisibility(View.INVISIBLE);

                int[] location = new int[2];
                mFab.getLocationOnScreen(location);
                location[0] += mFab.getWidth() / 2;

                // create Intent to take a picture and return control to the calling application
                final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, getPhotoFileUri(photoFileName)); // set the image file name

                mRevealView.setVisibility(View.VISIBLE);
                mRevealLayout.setVisibility(View.VISIBLE);

                mRevealLayout.show(location[0], location[1]); // Expand from center of FAB. Actually, it just plays reveal animation.
                mFab.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // If you call startActivityForResult() using an intent that no app can handle, your app will crash.
                        // So as long as the result is not null, it's safe to use the intent.
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            // Start the image capture intent to take photo
                            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                        }
                        /**
                         * Without using R.anim.hold, the screen will flash because of transition
                         * of Activities.
                         */
                        overridePendingTransition(0, R.anim.hold);
                    }
                }, 600); // 600 is default duration of reveal animation in RevealLayout
                mFab.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mFab.setClickable(true);
                        mFab.setVisibility(View.VISIBLE);
                        mRevealLayout.setVisibility(View.INVISIBLE);
                        mRevealView.setVisibility(View.INVISIBLE);
                    }
                }, 960); // Or some numbers larger than 600.
            }
        });

        mEnterTransitionListener = new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(Transition transition) {

            }

            @Override
            public void onTransitionEnd(Transition transition) {
                enterReveal();
            }

            @Override
            public void onTransitionCancel(Transition transition) {

            }

            @Override
            public void onTransitionPause(Transition transition) {

            }

            @Override
            public void onTransitionResume(Transition transition) {

            }
        };
        getWindow().setEnterTransition(new Explode());
        getWindow().getEnterTransition().addListener(mEnterTransitionListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DiscoverActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void getMyLocation() {
        Log.d(TAG, "getMyLocation");
        mLocationProvider = FlaneurApplication.getInstance().locationProvider;
        mLocationProvider.connect();
        mLocationProvider.addListener(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, String.format("onLocationChanged: %s", location.toString()));
        if (mLocation == null) {
            Log.d(TAG, "mLocation is null!");
            ParseGeoPoint currentPoint = new ParseGeoPoint(location.getLatitude(), location.getLongitude());
            Log.d(TAG, String.format("currentPoint: %s", currentPoint.toString()));
            ParseQuery<Post> query = ParseQuery.getQuery("Post");
            query.whereNear(Post.KEY_POST_LOCATION, currentPoint);
            query.setLimit(20);
            query.include(Post.KEY_POST_AUTHOR);
            // query.setCachePolicy(ParseQuery.CachePolicy.CACHE_THEN_NETWORK);
            Log.d(TAG, "make post query");
            query.findInBackground(new FindCallback<Post>() {
                @Override
                public void done(List<Post> objects, ParseException e) {
                    Log.d(TAG, "post query returned");
                    if (objects != null && objects.size() > 0) {
                        ParseObject.pinAllInBackground(objects);
                        configureViewWithPosts(objects);
                    }
                }
            });
        }
        mLocation = location;
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String address = intent.getStringExtra(PickupService.PICKUP_ADDRESS);
            String postId = intent.getStringExtra(PickupService.PICKUP_POST_ID);
            Utils.fireLocalNotification(DiscoverActivity.this, address);
            updateInboxIcon();
            Log.v("DiscoverActivity", "Sending local notif for pickup at " + address);

            mMapFragment.onPostPickup(postId);
        }
    };

    @Override
    protected void onDestroy() {
        // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }

    private void configureViewWithPosts(List<Post> posts) {
        Log.d(TAG, "configureViewWithPosts");
        // Build ArrayList of ParseProxyObjects to pass into MapFragment
        ArrayList<ParseProxyObject> postsProxy = new ArrayList<>();
        for (Post post : posts) {
            postsProxy.add(new ParseProxyObject(post));
        }

        mMapFragment.populateMapWithPosts(postsProxy);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Uri takenPhotoUri = getPhotoFileUri(photoFileName);
                Intent i = new Intent(DiscoverActivity.this, ComposeActivity.class);

                if (mLocation != null) {
                    i.putExtra(ComposeActivity.COMPOSE_LAT_ID, mLocation.getLatitude());
                    i.putExtra(ComposeActivity.COMPOSE_LONG_ID, mLocation.getLongitude());
                }
                i.putExtra(ComposeActivity.COMPOSE_IMAGE_ID, takenPhotoUri.getPath());
                startActivityForResult(i, POST_REQUEST_CODE);

            } else { // Result was a failure
                Toast.makeText(this, "Picture wasn't taken!", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == POST_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                ParseProxyObject post = (ParseProxyObject) data.getExtras().getSerializable(Post.KEY_POST);
                mMapFragment.addMarkerForPost(post, true);
            } else { // Result was a failure
                Toast.makeText(this, "Flan wasn't posted!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onLaunchCamera() {
        // create Intent to take a picture and return control to the calling application
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, getPhotoFileUri(photoFileName)); // set the image file name

        // If you call startActivityForResult() using an intent that no app can handle, your app will crash.
        // So as long as the result is not null, it's safe to use the intent.
        if (intent.resolveActivity(getPackageManager()) != null) {
            // Start the image capture intent to take photo
            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        }
    }

    // Returns the Uri for a photo stored on disk given the fileName
    public Uri getPhotoFileUri(String fileName) {
        // Only continue if the SD Card is mounted
        if (isExternalStorageAvailable()) {
            // Get safe storage directory for photos
            // Use `getExternalFilesDir` on Context to access package-specific directories.
            // This way, we don't need to request external read/write runtime permissions.
            File mediaStorageDir = new File(
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES), APP_TAG);

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
                Log.d(APP_TAG, "failed to create directory");
            }

            // Return the file target for the photo based on filename
            return Uri.fromFile(new File(mediaStorageDir.getPath() + File.separator + fileName));
        }
        return null;
    }

    // Returns true if external storage for photos is available
    private boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_discover, menu);

        // Get the notifications MenuItem and LayerDrawable (layer-list)
        mInboxItem = menu.findItem(R.id.miInbox);

        updateInboxIcon();
        return true;
    }

    public void onProfileViewOnClick(MenuItem mi) {
        Intent i = new Intent(this, ProfileActivity.class);
        i.putExtra(ProfileActivity.USER_ID, User.currentUser().getObjectId());
        startActivity(i);
    }

    public void onInboxViewOnClick(MenuItem mi) {
        Intent i = new Intent(this, InboxActivity.class);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppEventsLogger.activateApp(this);
        updateInboxIcon();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Logs 'app deactivate' App Event.
        AppEventsLogger.deactivateApp(this);
    }

    private void updateInboxIcon() {
        Log.e("test", "Updateding menu icon");
        if (mInboxItem == null) {
            return;
        }

        LayerDrawable icon = (LayerDrawable) mInboxItem.getIcon();
        // Update LayerDrawable's BadgeDrawable
        int newInboxCount = FlaneurApplication.getInstance().pickupService.getNewItemsCount();

        Log.e("test", "updated menu icon: "+ newInboxCount);

        BadgeDrawable.setBadgeCount(DiscoverActivity.this, icon, newInboxCount);


        mInboxItem.setVisible(true);
        invalidateOptionsMenu();
    }

    void enterReveal() {
        // previously invisible view
        final View myView = mFab;

        // get the center for the clipping circle
        int cx = myView.getMeasuredWidth() / 2;
        int cy = myView.getMeasuredHeight() / 2;

        // get the final radius for the clipping circle
        int finalRadius = Math.max(myView.getWidth(), myView.getHeight()) / 2;

        // create the animator for this view (the start radius is zero)
        Animator anim =
                ViewAnimationUtils.createCircularReveal(myView, cx, cy, 0, finalRadius);

        // make the view visible and start the animation
        myView.setVisibility(View.VISIBLE);
        anim.start();
    }
}

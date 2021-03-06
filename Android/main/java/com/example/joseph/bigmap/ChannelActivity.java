package com.example.joseph.bigmap;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.SparseArrayCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.java_websocket.exceptions.WebsocketNotConnectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChannelActivity extends FragmentActivity implements OnMapReadyCallback {
    private static String TAG = "ChannelActivity";
    public static final String PREFS_NAME = "StoredUserInfo";
    SharedPreferences sharedPreferences;

    private String header;
    private Button broadcastButton;
    protected int channelId;
    protected Boolean broadcasting;

    private GoogleMap map;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.channel_activity);

        // start map fragment
        if (savedInstanceState == null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            Fragment mapFragment = fragmentManager.findFragmentById(R.id.map);
            SupportMapFragment supportMapFragment = (SupportMapFragment) mapFragment;
            supportMapFragment.getMapAsync(this);
        }

        // set header title
        channelId = getIntent().getIntExtra("channelId", 0);
        header = "Channel " + channelId;
        ((TextView) findViewById(R.id.channel_header)).setText(header);

        // This channel 'x' will be noted in LocationService as the one that needs updates
        LocationService.activeChannel = channelId;

        // set the state of a button, either from shared preferences or "off" by default
        sharedPreferences = getSharedPreferences(PREFS_NAME, 0);
        if (sharedPreferences.contains(header)) {
            broadcasting = sharedPreferences.getBoolean(header, false);
        } else {
            broadcasting = false;
        }
        broadcastButton = (Button) findViewById(R.id.channel_button);
        setBroadcastState(broadcasting);

        broadcastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // change current state of broadcasting
                setBroadcastState(!broadcasting);
            }
        });
    }

    @Override
    public void onRestart() {
        map.clear();
        try {
            registerReceiver(websocketReceiver, filter);
            LocationService.webSocket.getAllBroadcastersLocation(channelId);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "tried to register an already registered receiver");
        } catch (WebsocketNotConnectedException e) {
            Toast.makeText(getApplicationContext(),
                    "Can't connect to server", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Websocket is not connected");
        }
        super.onRestart();
    }

    @Override
    public void onStop() {
        try {
            unregisterReceiver(websocketReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "tried to unregister an unregistered receiver");
        }
        super.onStop();
    }

    public void setBroadcastState(Boolean selectedState) {
        if (selectedState) { // user is broadcasting
            broadcastButton.setBackgroundColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.broadcasting));
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                startBroadcasting();
                broadcastButton.setText("Broadcasting");
            } else {
                Toast.makeText(getApplicationContext(),
                        "GPS permissions not granted", Toast.LENGTH_SHORT).show();
            }
        } else { // user not broadcasting
            stopBroadcasting();
            broadcastButton.setBackgroundColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.notBroadcasting));
            broadcastButton.setText("Click to broadcast");
        }
    }

    private void startBroadcasting() {
        if (checkPermissionsEnabled()) {
            broadcasting = true;
            storeBroadcastState();
            APIHandler.setBroadcastingChannels();
            // Location will be updated after this
            LocationService.webSocket.sendLocation();
        }
    }

    private void stopBroadcasting() {
        broadcasting = false;
        storeBroadcastState();
        APIHandler.setBroadcastingChannels();
        if (APIHandler.broadcastingChannels.size() == 0) {
            LocationService.webSocket.stopBroadcasting();
        }
    }

    private void storeBroadcastState() {
        // saves the state of channel broadcasting
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(header, broadcasting);
        editor.apply();

        // update the list in the APIHandler
        APIHandler.setBroadcastingChannels();
    }

    public Boolean checkPermissionsEnabled() {
        return ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /*******************
     * Maps API Methods
     *******************/
    SparseArrayCompat userMarkers; // <userId, userMarker>
    IntentFilter filter;
    BroadcastReceiver websocketReceiver = new BroadcastReceiver() {
        // receives broadcaster's location from websocket
        @Override
        public void onReceive(Context context, Intent intent) {
            // determine if there is a batch of users or just one
            Bundle b = intent.getExtras();
            if (b.containsKey("broadcaster-batch")) {
                addBroadcasterMarkers(b.getStringArray("broadcaster-batch"));
            } else if (b.containsKey("broadcaster-update")) {
                updateMarker(Arrays.asList(b.getStringArray("broadcaster-update")));
            }
        }
    };

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        /******************************************************************************************
         * center on user location
         * The code for this was taken and edited from:
         * stackoverflow.com/questions/18425141/android-google-maps-api-v2-zoom-to-current-location
         ******************************************************************************************/
        if (checkPermissionsEnabled()) {
            try {
                LocationManager locationManager =
                        (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                Criteria criteria = new Criteria();
                Location location =
                        locationManager.getLastKnownLocation(
                                locationManager.getBestProvider(criteria, false));
                if (location != null && channelId == LocationService.activeChannel) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(location.getLatitude(), location.getLongitude()), 13));
                    CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(new LatLng(location.getLatitude(), location.getLongitude()))
                            .zoom(17).build();
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }
                map.setMyLocationEnabled(true);

                // ready the map to receive and draw the broadcaster's markers
                setWebsocketReceiver();
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    public void setWebsocketReceiver () {
        userMarkers = new SparseArrayCompat<>();
        filter = new IntentFilter("BROADCAST_ACTION");
        registerReceiver(websocketReceiver, filter);
        map.clear();
        try {
            LocationService.webSocket.getAllBroadcastersLocation(channelId);
        } catch (WebsocketNotConnectedException e) {
            Toast.makeText(getApplicationContext(),
                    "Can't connect to server", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Websocket is not connected");
        }
    }

    /**
     *   @param broadcasterBatch
     *                   - will be returned as a batch of user strings with their id,
     *                      location, and other info appended. "broadcaster-batch"
     *                      is added to the string when the server returns the result,
     *                      so that onMessage knows where to send it.
     *      Example:
     *          broadcaster-batch\n
     *          [userId] [lat] [long]\n
     *          [userId] [lat] [long] [status update]\n
     *          ...
     */
    public void addBroadcasterMarkers(String[] broadcasterBatch) {
        userMarkers = new SparseArrayCompat<Marker>();
        List broadcaster = new ArrayList<String>();
        for (int i = 1; i < broadcasterBatch.length; i++) {
            do {
                broadcaster.add(broadcasterBatch[i]);
            } while (!broadcasterBatch[i++].contains("\n"));
            updateMarker(broadcaster);
            broadcaster.clear();
        }
    }

    /**
     * @param broadcaster
     *                  - a single location with a userId. This updates the
     *                     current map marker with the new location
     *      Example:
     *          broadcaster-update
     *          [userId] [lat] [long] [status update]
     */
    public void updateMarker(List<String> broadcaster) {
        MarkerOptions options = new MarkerOptions();
        // TODO: show the user's name and not their id
        int userId = Integer.parseInt(broadcaster.get(0));
        Marker marker = (Marker) userMarkers.get(userId);
        if (marker != null) {
            marker.remove();
        }

        if (broadcaster.size() == 3 && broadcaster.get(1).equals("0")) {
            return; // user isn't broadcasting, don't keep the dot
        } else if (broadcaster.size() >= 5) {
            // TODO: make a new update 'pop up' to people seeing the location
            options.title(userId + "\n" + broadcaster.get(3));
        } else {
            options.title(broadcaster.get(0));
        }
        double lat = Double.parseDouble(broadcaster.get(1).trim());
        double lng = Double.parseDouble(broadcaster.get(2).trim());
        options.position(new LatLng(lat, lng));
        options.icon(BitmapDescriptorFactory.fromResource(R.drawable.purple_dot));

        userMarkers.put(userId, map.addMarker(options));
    }
}

package locationemitter.crossbar.io.locationemitter;

import android.annotation.SuppressLint;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERM_REQ_CODE = 1;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private Session mWAMPSession;
    private TextView mStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mStatusText = findViewById(R.id.status_text);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationCallback();
        createLocationRequest();
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectToServerAndPublishLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    private void connectToServerAndPublishLocation() {
        mWAMPSession = new Session();
        mStatusText.setText("Connecting...");
        mWAMPSession.addOnJoinListener((session, details) -> {
            mStatusText.setText("Connected");
            startLocationUpdates();
        });
        Client wampClient = new Client(mWAMPSession, "ws://192.168.100.42:8080/ws", "realm1");
        wampClient.connect().whenComplete((exitInfo, throwable) -> stopLocationUpdates());
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(500);
        mLocationRequest.setFastestInterval(500);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    double lon = location.getLongitude();
                    double lat = location.getLatitude();
                    float speed = location.getSpeed();
                    mWAMPSession.publish("io.crossbar.location", lon, lat, speed);
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION},
                    LOCATION_PERM_REQ_CODE);
        } else {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        }
    }

    private void stopLocationUpdates() {
        if (mWAMPSession.isConnected()) {
            mWAMPSession.leave();
        }
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        mStatusText.setText("Disconnected");
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERM_REQ_CODE && grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }
}

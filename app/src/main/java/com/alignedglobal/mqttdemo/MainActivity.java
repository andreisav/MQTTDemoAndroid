package com.alignedglobal.mqttdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View.OnClickListener;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.util.Log;
import android.location.Location;
import android.location.LocationListener;

import org.json.JSONException;
import org.json.JSONObject;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;


public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    public enum MotionState {
        UNKNOWN,
        STOPPED,
        MOVING
    }

    EditText etText;
    Button btnSend;
    TextView txtTemp;
    TextView txtLocation;
    TextView textSending;
    TextView txtMessages;
    TextView txtMotionState;
    TextView txtSending;
    private MqttAndroidClient mMQTTAndroidClient;
    private SensorManager mSensorManager;
    private Sensor mSensorAccel;
    //    private Sensor _sensorGyro;
//    private Sensor _sensorMagnetic;
    private int hitCount = 0;
    private double hitSum = 0;
    private double hitResult = 0;
    private float[] mGravity;
    private double mAccel;
    private double mAccelCurrent;
    private double mAccelLast;

    private final int SAMPLE_SIZE = 100; // change this sample size as you want, higher is more precise but slow measure.
    private final double THRESHOLD = 0.2; // change this threshold as you want, higher is more spike movement

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String TOPIC_PUB = "as_demo_mqtt/devices/%s/telemetry";
    private static final String TOPIC_SUB = "as_demo_mqtt/devices/%s/commands";
    private static final String MQTT_HOST = "tcp://test.mosquitto.org:1883"; //TODO
    //    private static final String MQTT_HOST = "tcp://iot.eclipse.org:1883"; ///TODO
    private boolean mSending = true;
    private boolean mTracking = true;
    private MotionState mMotionState = MotionState.UNKNOWN;
    private String mDeviceId = "234389908509";//TODO figure out which one to use
    protected LocationManager mLocationManager;
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 5;
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 1;
    Location mLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        txtTemp = (TextView) findViewById(R.id.txtTemp);
        txtMessages = (TextView) findViewById(R.id.txtMessages);
        txtMotionState = (TextView) findViewById(R.id.txtMotionState);
        txtSending = (TextView) findViewById(R.id.textSending);
        etText = (EditText) findViewById(R.id.editTemp);
        btnSend = (Button) findViewById(R.id.btnSend);

        // get sensorManager and initialise sensor listeners
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        mSensorAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        _sensorGyro =  mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//        _sensorMagnetic =  mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        txtLocation = (TextView) findViewById(R.id.txtLocation);

        //TODO
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return;
            ActivityCompat.requestPermissions( this, new String[] {  android.Manifest.permission.ACCESS_COARSE_LOCATION,  android.Manifest.permission.ACCESS_FINE_LOCATION},
                   99);
            mLocationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
        }
        else {
            mLocationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
        }

        mLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (mLocation != null) {
            txtLocation.setText("Latitude:" + mLocation.getLatitude() + " Longitude:" + mLocation.getLongitude());
        }


        //TODO configure
        mMQTTAndroidClient = new MqttAndroidClient(this.getApplicationContext(), MQTT_HOST, "as_demo_mqtt_client_" + mDeviceId);
        mMQTTAndroidClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.d(TAG, "Connection was lost!", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String msg = new String(message.getPayload());
                Log.d(TAG, "Message Arrived!: " + topic + ": " + msg);
                txtMessages.setText("In:" + msg + "\n" + txtMessages.getText());
                JSONObject json = new JSONObject(msg);

                if (!json.isNull("command")) {
                    String cmd = json.getString("command").toUpperCase();

                    if ("STOP".equals(cmd)) {
                        setSending(false);
                        //TODO send ack back
                    } else if ("RESUME".equals(cmd)) {
                        setSending(true);
                    } else if ("SOUND".equals(cmd)) {
                        android.net.Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        android.media.MediaPlayer mp = android.media.MediaPlayer.create(getApplicationContext(), notification);
                        mp.start();
                    } else if ("LOCATION".equals(cmd)) {
                        publishLocation();
                    }
                }

//                txtMessages.append("In:" + message.getPayload().toString() + "\n");


            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d(TAG, "Delivery Complete!");
            }
        });

        //MQTT connection
        doConnect();


        btnSend.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {

                String str = etText.getText().toString();
                JSONObject json = new JSONObject();
                try {
                    json.put("temp", str);
                    json.put("unit", "F");
                } catch (JSONException ex) {
                    Log.e(TAG, "MQQT Exception: " + ex.getMessage(), ex);
                }
                // publish
                Log.d(TAG, "Publishing temp message..");
                publishTelemetryMessage("temp", json);
                txtTemp.setText(str);
                etText.setText("");
            }
        });
    }

    private void publishTelemetryMessage(String key, JSONObject data) {
        JSONObject json = new JSONObject();
        try {
            json.put("did", mDeviceId);
            json.put("tx", System.currentTimeMillis());
            json.put("telemetry", new JSONObject().put(key, data));
            publishMessage(String.format(TOPIC_PUB, mDeviceId), json.toString());
            txtMessages.setText("Out:" + json.toString() + "\n" + txtMessages.getText());
        } catch (JSONException ex) {
            Log.e(TAG, "MQQT Exception: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected void onResume() {
        super.onResume();
        initListeners();
    }

    //TODO we may want to continue tracking even while in the background
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }


    @Override
    public void onLocationChanged(Location location) {
        mLocation = location;
        Log.d("Location changed: %s", "Latitude:" + location.getLatitude() + ", Longitude:" + location.getLongitude());
        Log.d(TAG, "Publishing location message..");
        publishLocation();

    }

    private void publishLocation() {
        if (mLocation == null)
            return;
        txtLocation.setText("Latitude:" + mLocation.getLatitude() + " Longitude:" + mLocation.getLongitude());
        JSONObject json = new JSONObject();
        try {
            json.put("lat", mLocation.getLatitude());
            json.put("lat", mLocation.getLatitude());
            json.put("long", mLocation.getLongitude());
            publishTelemetryMessage("loc", json);
        } catch (JSONException ex) {
            Log.e(TAG, "MQQT Exception: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d("Location","disable");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("Location:","enable");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d("Location", "status");
    }


    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                Log.d(TAG, "TYPE_ACCELEROMETER");
                mGravity = event.values.clone();
                // Shake detection
                double x = mGravity[0];
                double y = mGravity[1];
                double z = mGravity[2];
                mAccelLast = mAccelCurrent;
                mAccelCurrent = Math.sqrt(x * x + y * y + z * z);
                double delta = mAccelCurrent - mAccelLast;
                mAccel = mAccel * 0.9f + delta;

                if (hitCount <= SAMPLE_SIZE) {
                    hitCount++;
                    hitSum += Math.abs(mAccel);
                } else {
                    hitResult = hitSum / SAMPLE_SIZE;

                    Log.d(TAG, String.valueOf(hitResult));
                    //TODO: this logic could be on the server
                    if (hitResult > THRESHOLD) {
                        Log.d(TAG, "MOVING");
                        mMotionState = MotionState.MOVING;
                        txtMotionState.setText("Moving"); //TODO
                    } else {
                        Log.d(TAG, "Stopped moving");
                        mMotionState = MotionState.STOPPED;
                        txtMotionState.setText("Stopped"); //TODO
                    }

                    JSONObject json = new JSONObject();
                    try {
                        json.put("state", mMotionState).put("x", x).put("y", y).put("z", z);
                        publishTelemetryMessage("accel", json);
                    } catch (JSONException ex) {
                        Log.e(TAG, "JSON Exception: " + ex.getMessage());
                    }

                    hitCount = 0;
                    hitSum = 0;
                    hitResult = 0;
                }
                break;

            case Sensor.TYPE_GYROSCOPE:
                Log.d(TAG, "TYPE_GYROSCOPE");
                // process gyro data
//                gyroFunction(event);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                Log.d(TAG, "TYPE_MAGNETIC_FIELD");
                // copy new magnetometer data into magnet array
//                System.arraycopy(event.values, 0, magnet, 0, 3);
                break;
        }
    }

    private void initListeners() {
        mSensorManager.registerListener(this,
                mSensorAccel, SensorManager.SENSOR_DELAY_NORMAL);
//
//        mSensorManager.registerListener(this,
//                _sensorGyro, SensorManager.SENSOR_DELAY_NORMAL);

//        mSensorManager.registerListener(this,
//                _sensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void setTracking(boolean on) {
        mTracking = on;
    }

    private void setSending(boolean on) {
        mSending = on;
        txtSending.setText(mSending ? "Sending" : "Not Sending");
    }

    private void publishMessage(String topic, String msg) {
        if (!mSending || !mMQTTAndroidClient.isConnected())
            return;

        try {
            mMQTTAndroidClient.publish(topic, new MqttMessage(msg.getBytes()));
        } catch (MqttException ex) {
            Log.e(TAG, "Failed to send: " + ex.getMessage(), ex);
        }
    }

    private void doConnect() {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);

            mMQTTAndroidClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Connection Success!");
                    try {
                        Log.d(TAG, "Subscribing to devices/commands/" + mDeviceId);
                        mMQTTAndroidClient.subscribe(String.format(TOPIC_SUB, mDeviceId), 0);
                        Log.d(TAG, "Subscribed to: " + String.format(TOPIC_SUB, mDeviceId) + "/" + mDeviceId);
                        publishLocation();
                    } catch (MqttException ex) {
                        Log.e(TAG, "MQQT Connection Exception: " + ex.getMessage());
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                }
            });
        } catch (MqttException ex) {
            Log.w(TAG, "MQQT Exception: " + ex.getMessage());
        }
    }

}

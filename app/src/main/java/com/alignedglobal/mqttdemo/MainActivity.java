package com.alignedglobal.mqttdemo;

import android.media.RingtoneManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View.OnClickListener;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;


//TODO  fix logging
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public enum MotionState {
        UNKNOWN,
        STOPPED,
        MOVING
    }

    EditText etText;
    Button btnSend;
    TextView txtTemp;
    TextView txtMessages;
    TextView txtMotionState;
    TextView txtSending;
    private  MqttAndroidClient mMQTTAndroidClient;
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
    private static final String TOPIC_PUB = "as_demo_mqtt/devices/telemetry";
    private static final String TOPIC_SUB = "as_demo_mqtt/devices/commands";
    private static final String MQTT_HOST = "tcp://test.mosquitto.org:1883"; //TODO
//    private static final String MQTT_HOST = "tcp://iot.eclipse.org:1883"; ///TODO
    private boolean mSending = true;
    private boolean mTracking = true;
    private MotionState mMotionState = MotionState.UNKNOWN;
    private String mDeviceId = "234389908509";//TODO figure out which one to use

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

        txtTemp = (TextView)findViewById(R.id.txtTemp);
        txtMessages = (TextView)findViewById(R.id.txtMessages);
        txtMotionState = (TextView)findViewById(R.id.txtMotionState);
        txtSending = (TextView)findViewById(R.id.textSending);
        etText = (EditText)findViewById(R.id.editTemp);
        btnSend = (Button)findViewById(R.id.btnSend);

        // get sensorManager and initialise sensor listeners
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        mSensorAccel =  mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        _sensorGyro =  mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//        _sensorMagnetic =  mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        //TODO configure
        mMQTTAndroidClient = new MqttAndroidClient(this.getApplicationContext(), MQTT_HOST, "as_demo_mqtt_client_"+mDeviceId);
        mMQTTAndroidClient.setCallback(new MqttCallback() {
            //TODO need reconnect
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
                    }
                    else if ("SOUND".equals(cmd)) {
                        android.net.Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        android.media.MediaPlayer mp = android.media.MediaPlayer.create(getApplicationContext(), notification);
                        mp.start();
                    }
                }

//                txtMessages.append("In:" + message.getPayload().toString() + "\n");


            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d(TAG, "Delivery Complete!");
            }
        });
        // TODO investigate service launch
        try {
            mMQTTAndroidClient.connect(null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Connection Success!");
                    try {
                        Log.d(TAG, "Subscribing to devices/commands/" + mDeviceId);
                        mMQTTAndroidClient.subscribe(TOPIC_SUB  + "/" + mDeviceId, 0);
                        Log.d(TAG, "Subscribed to: "  + TOPIC_SUB  + "/" + mDeviceId);
                    } catch (MqttException ex) {
                        Log.e(TAG, "MQQT Connection Exception: " + ex.getMessage());
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable ex) {
                    Log.e(TAG,  ex.getMessage(), ex);
                }
            });
        } catch (MqttException ex) {
            Log.w(TAG, "MQQT Exception: " + ex.getMessage());
        }

        btnSend.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                String str = etText.getText().toString();
                // publish
                Log.d(TAG, "Publishing message..");
                JSONObject json = new JSONObject();
                try {
                    json.put("did", mDeviceId);
                    json.put("tx", System.currentTimeMillis());
                    json.put("telemetry", new JSONObject().put("temp", str));
                }
                catch (JSONException ex) {
                    Log.e(TAG, "MQQT Exception: " + ex.getMessage(), ex);
                }

                publishMessage(TOPIC_PUB + "/" + mDeviceId, json.toString());
                txtMessages.setText("Out:" + json.toString() + "\n" + txtMessages.getText());

                txtTemp.setText(str);
                etText.setText("");
            }
        });
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

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
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
                        Log.d(TAG, "Stop Walking");
                        mMotionState = MotionState.STOPPED;
                        txtMotionState.setText("Stopped"); //TODO
                    }

                    JSONObject json = new JSONObject();
                    try {
                        json.put("did", mDeviceId);
                        json.put("tx", System.currentTimeMillis());
                        json.put("telemetry", new JSONObject().put("sensor", "accel").put("state", mMotionState).put("x", x).put("y", y).put("z", z));
                    }
                    catch (JSONException ex) {
                        Log.e(TAG, "JSON Exception: " + ex.getMessage());
                    }

                    //TODO possibly make async to make sure we're not blocking
                    publishMessage(TOPIC_PUB + "/" + mDeviceId, json.toString());
                    txtMessages.setText("Out:" + json.toString() + "\n" + txtMessages.getText());


                    hitCount = 0;
                    hitSum = 0;
                    hitResult = 0;
                }
                break;

            case Sensor.TYPE_GYROSCOPE:
                Log.d(TAG,"TYPE_GYROSCOPE");
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
        txtSending.setText(mSending?"Sending":"Not Sending");
    }

    private void publishMessage(String topic, String msg) {
        if (!mSending)
            return;

        try {
            mMQTTAndroidClient.publish(topic, new MqttMessage(msg.getBytes()));
        } catch (MqttException ex) {
            Log.e(TAG, "Failed to send: " + ex.getMessage(), ex);
        }
    }

}

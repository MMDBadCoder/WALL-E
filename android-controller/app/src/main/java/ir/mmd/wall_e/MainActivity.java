package ir.mmd.wall_e;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private EditText ipAddressEditText;
    private Button startButton;
    private TextView statusTextView;
    private TextView sensitivityLabel;
    private SeekBar sensitivitySeekBar;
    private boolean isSending = false;
    private String serverIp;
    private int serverPort = 12345; // Change this to your server's port

    private float[] accelerometerData = new float[3];
    private float[] magnetometerData = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];

    private float sensitivity = 2.0f; // Default sensitivity (middle value)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipAddressEditText = findViewById(R.id.ipAddressEditText);
        startButton = findViewById(R.id.startButton);
        statusTextView = findViewById(R.id.statusTextView);
        sensitivityLabel = findViewById(R.id.sensitivityLabel);
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Set up sensitivity slider
        sensitivitySeekBar.setMax(400); // Range from 0 to 400
        sensitivitySeekBar.setProgress(200); // Default value in the middle (2.0)
        sensitivityLabel.setText("Sensitivity: 2.0");

        sensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sensitivity = progress / 100.0f; // Convert progress to sensitivity (0.0 to 4.0)
                sensitivityLabel.setText(String.format("Sensitivity: %.1f", sensitivity));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not used
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not used
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isSending) {
                    serverIp = ipAddressEditText.getText().toString();
                    if (serverIp.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Please enter IP address", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    isSending = true;
                    statusTextView.setText("Status: Sending");
                    startButton.setText("Stop");
                    updateBackgroundColor(0); // Reset background color
                } else {
                    isSending = false;
                    statusTextView.setText("Status: Stopped");
                    startButton.setText("Start");
                    updateBackgroundColor(-1); // Set to light red
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isSending) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelerometerData = event.values.clone();
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magnetometerData = event.values.clone();
            }

            // Calculate orientation angles
            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerData, magnetometerData)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles);

                // Convert radians to degrees
                float pitch = (float) Math.toDegrees(orientationAngles[1]); // Tilt forward/backward
                float roll = (float) Math.toDegrees(orientationAngles[2]); // Tilt left/right

                // Map angles to coefficients (-100 to 100) and apply sensitivity
                int angleCoefficientA = (int) (pitch / 90 * 100 * sensitivity);
                int angleCoefficientB = (int) (roll / 90 * 100 * sensitivity);

                // Clamp values to -100 to 100
                angleCoefficientA = Math.max(-100, Math.min(100, angleCoefficientA));
                angleCoefficientB = Math.max(-100, Math.min(100, angleCoefficientB));

                // Get motor values using custom methods
                int motorA = getMotorAValue(angleCoefficientA, angleCoefficientB);
                int motorB = getMotorBValue(angleCoefficientA, angleCoefficientB);

                // Send motor values via UDP
                String message = motorA + "," + motorB;
                new SendUdpTask().execute(message);

                // Update background color based on max of absolute values
                int maxAbsoluteValue = Math.max(Math.abs(angleCoefficientA), Math.abs(angleCoefficientB));
                updateBackgroundColor(maxAbsoluteValue);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    private void updateBackgroundColor(int maxAbsoluteValue) {
        int backgroundColor;
        if (!isSending) {
            // Stopped state: Light red
            backgroundColor = Color.argb(255, 255, 200, 200); // Light red
        } else {
            // Sending state: Gradient between white and green
            int greenValue = (int) (255 * (maxAbsoluteValue / 100.0)); // Normalize to 0-255
            greenValue = Math.min(255, greenValue); // Ensure it doesn't exceed 255
            backgroundColor = Color.argb(255, 255 - greenValue, 255, 255 - greenValue); // White to green
        }
        findViewById(R.id.rootLayout).setBackgroundColor(backgroundColor);
    }

    private int getMotorAValue(int angleCoefficientA, int angleCoefficientB) {
        int sum = angleCoefficientB * 2;
        int diff = angleCoefficientA;
        int value = diff + (sum - diff) / 2;
        value = Math.max(value, -100);
        value = Math.min(value, 100);
        return value;
    }

    private int getMotorBValue(int angleCoefficientA, int angleCoefficientB) {
        int sum = angleCoefficientB * 2;
        int diff = angleCoefficientA;
        int value = (sum - diff) / 2;
        value = Math.max(value, -100);
        value = Math.min(value, 100);
        return value;
    }

    private class SendUdpTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... messages) {
            try {
                DatagramSocket socket = new DatagramSocket();
                InetAddress serverAddress = InetAddress.getByName(serverIp);
                byte[] sendData = messages[0].getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                socket.send(sendPacket);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
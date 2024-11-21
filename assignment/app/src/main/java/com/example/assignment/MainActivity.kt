package com.example.assignment

import org.eclipse.paho.android.service.MqttAndroidClient
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import org.eclipse.paho.client.mqttv3.*

class MainActivity : AppCompatActivity() {

    private val BROKER_URL = "tcp://broker.sundaebytestt.com:1883"
    private val TOPIC = "assignment/location"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000

    private lateinit var studentIdEditText: EditText
    private lateinit var startPublishingButton: Button
    private lateinit var stopPublishingButton: Button

    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isPublishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        studentIdEditText = findViewById(R.id.studentIdEditText)
        startPublishingButton = findViewById(R.id.startPublishingButton)
        stopPublishingButton = findViewById(R.id.stopPublishingButton)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mqttClient = MqttAndroidClient(applicationContext, BROKER_URL, MqttClient.generateClientId())

        startPublishingButton.setOnClickListener { startPublishing() }
        stopPublishingButton.setOnClickListener { stopPublishing() }

        setupLocationCallback()
        setupMqttCallback()
    }

    private fun startPublishing() {
        val studentId = studentIdEditText.text.toString()
        if (studentId.isEmpty()) {
            Toast.makeText(this, "Please enter your student ID", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        try {
            mqttClient.connect()
            isPublishing = true
            startPublishingButton.isEnabled = false
            stopPublishingButton.isEnabled = true
            fusedLocationClient.requestLocationUpdates(createLocationRequest(), locationCallback, null)
        } catch (e: MqttException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to connect to MQTT broker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPublishing() {
        if (isPublishing) {
            isPublishing = false
            stopPublishingButton.isEnabled = false
            startPublishingButton.isEnabled = true
            fusedLocationClient.removeLocationUpdates(locationCallback)
            try {
                mqttClient.disconnect()
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
             fun onLocationResult(locationResult: LocationResult?) {
                if (isPublishing && locationResult != null) {
                    for (location in locationResult.locations) {
                        publishLocation(location)
                    }
                }
            }
        }
    }

    private fun publishLocation(location: Location) {
        val message = "Student ID: ${studentIdEditText.text}, Latitude: ${location.latitude}, Longitude: ${location.longitude}"
        try {
            mqttClient.publish(TOPIC, MqttMessage(message.toByteArray()))
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPublishing()
            } else {
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMqttCallback() {
        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Toast.makeText(this@MainActivity, "Connection lost: ${cause?.message}", Toast.LENGTH_SHORT).show()
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                // complete if needed
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // complete if needed
            }
        })
    }
}
package kp.zaliczenie;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import static android.R.color.black;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    //zmienne do wylaczania ekranu
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private int field = 0x00000020;

    //zmienne dla czujnika zblizeniowego
    private SensorManager proximitySensorManager;
    private Sensor proximitySensor;

    //zmienne dla latarki
    private Camera camera;
    private Camera.Parameters parameter;
    private boolean deviceHasFlash = false;
    private boolean isLightOn = false;
    private boolean autoLightEnable = false;
    private Button saveBtn;
    private Switch lightSwitch;
    private Switch autoLightSwitch;

    //zmienne dla kompasu
    private Sensor magneticSensor;
    private ImageView imageView;
    private SensorManager magneticSensorManager;
    private float currentDegree = 0f;

    //zmienne dla lokalizacji
    TextView dostawca;
    TextView dlugosc;
    TextView szerokosc;
    Criteria cr;
    Location loc;
    String mojDostawca;
    LocationManager mylm;

    //lokalizacja w pliku
    String logString = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //wyłączenie orientacji ekranu
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //inicjalizacja przycisku i switchy
        saveBtn = (Button)findViewById(R.id.saveBtn);
        lightSwitch = (Switch)findViewById(R.id.switch1);
        autoLightSwitch = (Switch)findViewById(R.id.switch2);

        //sprawdzenie czy urzadzenie posiada latarke
        deviceHasFlash = getApplication().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        if (!deviceHasFlash) {
            Toast.makeText(MainActivity.this, "Przepraszamy, ale twoje urządzenie nie posiada latarki!", Toast.LENGTH_LONG).show();
            return;
        } else {
            this.camera = Camera.open(0);
            parameter = this.camera.getParameters();
        }

        //akcja dla switcha latarki
        lightSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isLightOn) {
                    turnOnTheFlash();
                    isLightOn = true;
                } else {
                    turnOffTheFlash();
                    isLightOn = false;
                }
            }
        });

        //akcja dla switcha latarki automatycznej
        autoLightSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!autoLightEnable) {
                    autoLightEnable = true;
                } else {
                    if (isLightOn) {
                        turnOffTheFlash();
                        isLightOn = false;
                    }
                    autoLightEnable = false;
                }
            }
        });

        //akcja dla przycisku zapisu
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File sdcard = Environment.getExternalStorageDirectory();
                File dir = new File(sdcard.getAbsolutePath() + "/Kompas/");
                dir.mkdir();
                File fle = new File(dir, "Lokalizacja.txt");
                try {
                    FileOutputStream os = new FileOutputStream(fle);
                    String outString = "[" + DateFormat.getDateTimeInstance().format(new Date()) + "] " + logString;
                    os.write(outString.getBytes());
                    os.close();
                    Toast. makeText (getApplicationContext(),"Lokalizacja zapisana w: Kompas/Lokalizacja.txt",Toast. LENGTH_LONG ).show();
                }catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //czujnik zblizeniowy - wylaczenie ekranu
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(field, getLocalClassName());
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        //czujnik światła - funkcjonalnosc latarki automatycznej
        proximitySensorManager = (SensorManager) getSystemService(Service.SENSOR_SERVICE);
        proximitySensor = proximitySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        //czujnik kompasu
        magneticSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        magneticSensor = magneticSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        imageView = (ImageView)findViewById(R.id.imageView);

        //pola dla lokalizacji
        dostawca = (TextView) findViewById(R.id.dostawcaView);
        dlugosc = (TextView) findViewById(R.id.dlugoscView);
        szerokosc = (TextView) findViewById(R.id.szerokoscView);

        //ustawienie domyslnych wartosci pol
        saveBtn.setEnabled(false);
        dostawca.setText("Położenie: nie znaleziono");
        dlugosc.setText("");
        szerokosc.setText("");

        //wczytanie lokalizacji i dostawcy
        cr = new Criteria();
        mylm = (LocationManager) getSystemService(LOCATION_SERVICE);
        mojDostawca = mylm.getBestProvider(cr, true);
        loc = mylm.getLastKnownLocation(mojDostawca);
    }

    private void turnOffTheFlash() {
        parameter.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        this.camera.setParameters(parameter);
        this.camera.stopPreview();
        isLightOn = false;
    }

    private void turnOnTheFlash() {
        if (this.camera != null) {
            parameter = this.camera.getParameters();
            parameter.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            this.camera.setParameters(parameter);
            this.camera.startPreview();
            isLightOn = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        proximitySensorManager.unregisterListener(this);
        magneticSensorManager.unregisterListener(this);
        mylm.removeUpdates(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        proximitySensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        if(magneticSensorManager != null){
            magneticSensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            Toast.makeText(MainActivity.this, "Not supported", Toast.LENGTH_SHORT).show();
        }
        mylm.requestLocationUpdates(mojDostawca, 400, 1, this);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT && autoLightEnable){
            if ((int) event.values[0] < 100) {
                turnOnTheFlash();
            } else {
                turnOffTheFlash();
            }
        }

        //rotacja obrazka
        int degree  = Math.round(event.values[0]);
        RotateAnimation rotateAnimation = new RotateAnimation(currentDegree, -degree,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setDuration(1000);
        rotateAnimation.setFillAfter(true);

        imageView.startAnimation(rotateAnimation);
        currentDegree = -degree;
    }

    @Override
    public void onLocationChanged(Location location) {
        mojDostawca = mylm.getBestProvider(cr, true);
        loc = mylm.getLastKnownLocation(mojDostawca);
        dostawca.setText("Położenie: " + "");
        if(loc.getLongitude() < 0){
            logString = loc.getLongitude() + " S";
            dlugosc.setText(loc.getLongitude() + " S");
        } else{
            logString = loc.getLongitude() + " N";
            dlugosc.setText(loc.getLongitude() + " N");
        }
        if(loc.getLatitude() < 0){
            logString = logString + "  " + loc.getLatitude() + " W";
            szerokosc.setText(loc.getLatitude() + " W");
        } else{
            logString = logString + "  " + loc.getLatitude() + " E";
            szerokosc.setText(loc.getLatitude() + " E");
        }
        saveBtn.setEnabled(true);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

package edu.np.ece.virtualbeaconone;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;

import java.util.Arrays;

import static android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;
import static android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;
import static android.view.View.GONE;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Context context;
    private BeaconTransmitter mBeaconTransmitter;

    EditText etUuid, etMajor, etMinor;
    Button btStart, btStop, btBurst;
    ProgressBar progressBar;
    TextView tvStatus;

    final int JOB_ID = 1;
    String uuid, major, minor;

    private View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (TextUtils.isEmpty(etUuid.getText())
                    || TextUtils.isEmpty(etMajor.getText())
                    || TextUtils.isEmpty(etMinor.getText())) {
                Toast.makeText(context, "Missing UUID, Major or Minor", Toast.LENGTH_SHORT).show();
                return;
            }
            uuid = etUuid.getText().toString();
            major = etMajor.getText().toString();
            minor = etMinor.getText().toString();

            switch (view.getId()) {
                case R.id.btStart:
                    startBeacon(uuid, major, minor);
                    break;
                case R.id.btStop:
                    stopBeacon();
                    break;

                case R.id.btBurst:
                    // Extras for your job.
                    PersistableBundle extras = new PersistableBundle();
                    extras.putString("uuid", uuid);
                    extras.putString("major", major);
                    extras.putString("minor", minor);

                    JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(MainActivity.this, BeaconJobService.class))
                            .setMinimumLatency(1000)    // wait at least
                            .setOverrideDeadline(5000)  // maximum delay
                            .setRequiresCharging(false)
                            .setRequiresDeviceIdle(false)
                            .setExtras(extras)
                            .build();
                    JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    jobScheduler.schedule(job);
                    break;
            }
        }
    };

    private void startBeacon(String uuid, String major, String minor) {
        stopBeacon();

        Beacon beacon = new Beacon.Builder()
                .setId1(uuid)
                .setId2(major)
                .setId3(minor)
                .setManufacturer(0x0118) // https://www.bluetooth.com/specifications/assigned-numbers/company-identifiers
                .setTxPower(-59)
                .setDataFields(Arrays.asList(new Long[]{0l})) // Remove this for beacon layouts without d: fields
                .setBluetoothName("TestBeacon")
                .build();

        mBeaconTransmitter.setAdvertiseMode(ADVERTISE_MODE_LOW_LATENCY);
        mBeaconTransmitter.setAdvertiseTxPowerLevel(ADVERTISE_TX_POWER_HIGH);
        mBeaconTransmitter.startAdvertising(beacon, new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "Advertisement start failed with code: " + errorCode);
            }

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i(TAG, "Advertisement start succeeded.");
            }
        });
    }

    private void stopBeacon() {
        if (mBeaconTransmitter == null) {
            return;
        }
        if (mBeaconTransmitter.isStarted())
            mBeaconTransmitter.stopAdvertising();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this.getBaseContext();

        etUuid = (EditText) this.findViewById(R.id.etUuid);
        etMajor = (EditText) this.findViewById(R.id.etMajor);
        etMinor = (EditText) this.findViewById(R.id.etMinor);
        btStart = (Button) this.findViewById(R.id.btStart);
        btStart.setOnClickListener(listener);
        btStop = (Button) this.findViewById(R.id.btStop);
        btStop.setOnClickListener(listener);
        btBurst = (Button) this.findViewById(R.id.btBurst);
        btBurst.setOnClickListener(listener);
        tvStatus = (TextView) this.findViewById(R.id.tvStatus);
        progressBar = (ProgressBar) this.findViewById(R.id.progressBar);

        if (!BleUtil.isBluetoothEnabled()) {
            BleUtil.enableBluetooth();
            //Set a filter to only receive bluetooth state changed events.
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mReceiver, filter);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            boolean ok = this.isTransmissionSupported();
            btStart.setEnabled(ok);
            btStop.setEnabled(ok);
            btBurst.setEnabled(ok);
        }

        // Sets up to transmit as an iBeacon beacon.
        BeaconParser beaconParser = new BeaconParser()
                .setBeaconLayout(BleUtil.LAYOUT_IBEACON);
        mBeaconTransmitter = new BeaconTransmitter(getApplicationContext(), beaconParser);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (bluetoothState) {
                    case BluetoothAdapter.STATE_ON:
                        progressBar.setVisibility(GONE);
                        break;
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        stopBeacon();
        super.onDestroy();
    }

    private boolean isTransmissionSupported() {
        int result = BeaconTransmitter.checkTransmissionSupported(context);
        switch (result) {
            case BeaconTransmitter.SUPPORTED:
                tvStatus.setText("BeaconTransmitter.SUPPORTED");
                Log.d(TAG, "BeaconTransmitter.SUPPORTED");
                return true;
            case BeaconTransmitter.NOT_SUPPORTED_BLE:
                tvStatus.setText("BeaconTransmitter.NOT_SUPPORTED_BLE");
                Log.d(TAG, "BeaconTransmitter.NOT_SUPPORTED_BLE");
                return false;
            case BeaconTransmitter.NOT_SUPPORTED_MIN_SDK:
                tvStatus.setText("BeaconTransmitter.NOT_SUPPORTED_MIN_SDK");
                Log.d(TAG, "BeaconTransmitter.NOT_SUPPORTED_MIN_SDK");
                return false;
            case BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER:
                tvStatus.setText("BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER");
                Log.d(TAG, "BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER");
                return false;
            // Ignore NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS
        }
        return false;
    }

}

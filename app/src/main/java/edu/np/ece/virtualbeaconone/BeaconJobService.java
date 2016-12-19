package edu.np.ece.virtualbeaconone;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;

import java.util.Arrays;

import static android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;
import static android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;

/**
 * Created by zqi2 on 17/12/16.
 */

public class BeaconJobService extends JobService {
    final static String TAG = BeaconJobService.class.getSimpleName();

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "onStartJob(): " + params.getJobId());
        new JobTask(this).execute(params);
        //-- True: if your service needs to process work on a separate thread
        //-- False: if there's no more work to be done for this job.
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        //-- This will be called by system, especially when need to cancel job
        Log.i(TAG, "onStopJob(): " + params.getJobId());
        //-- True: if the job needs to be re-scheduled
        //-- False: if no need re-run
        return false;
    }

    private static class JobTask extends AsyncTask<JobParameters, Void, JobParameters> {
        final long WAIT_MS = 10000;
        private final JobService jobService;
        private BeaconTransmitter mBeaconTransmitter;

        public JobTask(JobService jobService) {
            this.jobService = jobService;
        }

        @Override
        protected void onPreExecute() {
            Log.i(TAG, "onPreExecute()");
            super.onPreExecute();
            // Sets up to transmit as an iBeacon beacon.
            BeaconParser beaconParser = new BeaconParser()
                    .setBeaconLayout(BleUtil.LAYOUT_IBEACON);
            mBeaconTransmitter = new BeaconTransmitter(jobService, beaconParser);
        }

        @Override
        protected JobParameters doInBackground(JobParameters... params) {
            Log.i(TAG, "doInBackground()");
            PersistableBundle extras = params[0].getExtras();
            String uuid = extras.getString("uuid");
            String major = extras.getString("major");
            String minor = extras.getString("minor");
            startBeacon(uuid, major, minor);
            //-- Wait for a while
            SystemClock.sleep(WAIT_MS);
            stopBeacon();

            return params[0];
        }

        @Override
        protected void onPostExecute(JobParameters jobParameters) {
            Log.i(TAG, "onPostExecute()");
            jobService.jobFinished(jobParameters, false);
        }

        private void startBeacon(String uuid, String major, String minor) {
            Log.i(TAG, "startBeacon()");

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
            Log.i(TAG, "stopBeacon()");
            if (mBeaconTransmitter == null) {
                return;
            }
            if (mBeaconTransmitter.isStarted())
            mBeaconTransmitter.stopAdvertising();
            mBeaconTransmitter = null;
        }
    }
}

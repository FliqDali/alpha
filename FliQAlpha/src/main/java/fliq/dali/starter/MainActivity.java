package fliq.dali.starter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.parse.GetCallback;
import com.parse.GetDataCallback;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import fliq.dali.alpha.R;


/*Created by Ben Ribovich
Adapted from
http://kittensandcode.blogspot.com/2014/08/ibeacons-and-android-parsing-uuid-major.html
 */

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String TAG = "asdFliq";
    BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    ImageView mImageView;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        mHandler = new Handler();
        scanLeDevice(true);
        mImageView = (ImageView)findViewById(R.id.imageView);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    Log.d("found device", "found generic device");
                    if (device.getName() != null && device.getName().startsWith("AprilBeacon")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Found FliQ Device", Toast.LENGTH_SHORT).show();
                            }
                        });
                        int startByte = 2;
                        boolean patternFound = false;
                        while (startByte <= 5) {
                            if (((int) scanRecord[startByte + 2] & 0xff) == 0x02 && //Identifies an iBeacon
                                    ((int) scanRecord[startByte + 3] & 0xff) == 0x15) { //Identifies correct data length
                                patternFound = true;
                                break;
                            }
                            startByte++;
                        }

                        if (patternFound) {
                            //Convert to hex String
                            byte[] uuidBytes = new byte[16];
                            System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16);
                            String hexString = bytesToHex(uuidBytes);

                            //Here is your UUID
                            final String uuid = hexString.substring(0, 8) + "-" +
                                    hexString.substring(8, 12) + "-" +
                                    hexString.substring(12, 16) + "-" +
                                    hexString.substring(16, 20) + "-" +
                                    hexString.substring(20, 32);

                            //Here is your Major value
                            int major = (scanRecord[startByte + 20] & 0xff) * 0x100 + (scanRecord[startByte + 21] & 0xff);

                            //Here is your Minor value
                            int minor = (scanRecord[startByte + 22] & 0xff) * 0x100 + (scanRecord[startByte + 23] & 0xff);


                            Log.d(TAG, uuid);
                            Log.d(TAG, String.valueOf(major));
                            Log.d(TAG, String.valueOf(minor));

                            ParseQuery<ParseObject> query = ParseQuery.getQuery("BeaconDetail");
                            query.whereEqualTo("UUID", uuid);
                            query.whereEqualTo("Major", major);
                            query.whereEqualTo("Minor", minor);
                            //ParseObject beaconEntry = new ParseObject("BeaconDetail");
                            query.getFirstInBackground(new GetCallback<ParseObject>() {
                                public void done(ParseObject object, ParseException e) {
                                    if (object == null) {
                                        Log.d("score", "The getFirst request failed.");
                                    } else {
                                        Log.d("score", "Retrieved the object.");
                                        // Locate the column named "ImageName" and set
                                        // the string
                                        ParseFile fileObject = (ParseFile) object.get("itinerary");
                                        fileObject.getDataInBackground(new GetDataCallback() {

                                            public void done(byte[] data, ParseException e) {
                                                if (e == null) {
                                                    Log.d("test", "We've got data in data.");
                                                    // Decode the Byte[] into
                                                    // Bitmap
                                                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                                                    mImageView.setImageBitmap(bmp);

                                                }
                                            }

                                        });

                                    }

                                }
                            });
                        }
                    }
                }
            };

    /**
     * bytesToHex method
     * Found on the internet
     * http://stackoverflow.com/a/9855338
     */
    static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    protected void onDestroy() {
        
        super.onDestroy();
    }
}

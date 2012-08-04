/* Application demonstrates the interaction between Seeeduino ADK and Android Device
 * using Niels Brouwers' MicroBridge library. 
 * 
 * Android Device: Any device with Android v1.5 which supports ADB(Android Debug Bridge).   
 *  
 * This application uses a very simple (or a trivial) design to make it understandable.
 * 
 * Overview:
 * 1.ADK Main Board periodically samples Analog Channel 0 and sends it
 *   to Android Device for display. This value is displayed using a TextView and SeekBar Widgets
 * 
 * 2.Android device controls the state of a LED connected to Digital Pin 12 of ADK Main Board.
 *   A Button Widget used for this.
 * 
 * Microbridge uses ADB based client-server implementation. The Server code that runs on Android
 * device runs in a separate thread. Hence any update to UI widgets value has to be carried out 
 * in UI thread. This application uses XML based UI creation as it is easier for adding addition
 * UI Widgets. 
 * 
 */
package ch.cern.cosmicraydetector;

import java.io.IOException;

import org.microbridge.server.AbstractServerListener;
import org.microbridge.server.Server;

import android.app.Activity;
import android.content.Intent;
import android.location.Criteria;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.*;
import java.text.*;

public class CosmicRayDetector extends Activity implements OnClickListener,
		LocationListener {

	private int adcSensorValue = 10;
	private static final String TAG = "CosmicRayDetector";

	private TextView tvAdcvalue;
	private SeekBar sbAdcValue;
	private Button bOutPutLED;

	private boolean LEDState = false; // initially OFF

	private Server server = null;
	private Handler mHandler;

	private LocationManager service;
	private double lat;
	private double lng;
	private String nmeaTimestamp;
	private String provider;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		bOutPutLED = (Button) findViewById(R.id.buttonOuputLED);
		bOutPutLED.setOnClickListener(this);

		/* Create TCP server (based on MicroBridge LightWeight Server) */
		this.initTcpServer();

		this.initLocationService();

		/* Poll every 10 seconds and generate a timestamp */
		this.pollLocation(1000);

	}

	private void pollLocation(final int delay) {
		mHandler = new Handler();

		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try {
						Thread.sleep(delay);
						mHandler.post(getLocation());
					} catch (Exception e) {
						// TODO: handle exception
					}
				}
			}

			private Runnable getLocation() {
				Location location = service.getLastKnownLocation(provider);

				if (location != null) {
					onLocationChanged(location);
				} else {
					Log.w(TAG, "Location not available");
				}
				return null;
			}

		}).start();
	}

	private void initLocationService() {
		service = (LocationManager) getSystemService(LOCATION_SERVICE);
		service.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1l, 1l,
				this);

		service.addNmeaListener(new NmeaListener() {
			public void onNmeaReceived(long timestamp, String nmea) {

				if (nmea.contains("GPGGA")) {
					String x = nmea.split(",")[1];
					nmeaTimestamp = x.substring(0, 2) + ":" + x.substring(2, 4)
							+ ":" + x.substring(4, 6);

					SimpleDateFormat datefmt = new SimpleDateFormat(
							"dd/MM/yy");
					NumberFormat numfmt = new DecimalFormat("+#;-#");

					nmeaTimestamp = (String) datefmt.format(new Date()).toString()
							+ " " + nmeaTimestamp + " 00100 "
							+ numfmt.format((lat * 3600000)) + " "
							+ numfmt.format((lng * 3600000))
							+ " altitude frac";
					System.out.println(nmeaTimestamp);
				}
			}
		});

		boolean enabled = service
				.isProviderEnabled(LocationManager.GPS_PROVIDER);

		/*
		 * Check if enabled and if not send user to the GSP settings. A better
		 * solution would be to display a dialog and suggesting to go to the
		 * settings
		 */
		if (!enabled) {
			Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			startActivity(intent);
		}

		provider = service.getBestProvider(new Criteria(), false);
	}

	private void initTcpServer() {

		try {
			/*
			 * Use the same port number used in ADK Main Board firmware
			 */
			server = new Server(4568);
			server.start();
		} catch (IOException e) {
			Log.e("CosmicRayDetector ADK", "Unable to start TCP server", e);
			System.exit(-1);
		}

		server.addListener(new AbstractServerListener() {

			@Override
			public void onReceive(org.microbridge.server.Client client,
					byte[] data) {

				if (data.length < 2)
					return;
				adcSensorValue = (data[0] & 0xff) | ((data[1] & 0xff) << 8);

				/*
				 * Any update to UI can not be carried out in a non UI thread
				 * like the one used for Server. Hence runOnUIThread is used.
				 */
				runOnUiThread(new Runnable() {
					public void run() {

						SeekBar sbAdcValue = (SeekBar) findViewById(R.id.sbADCValue);
						sbAdcValue.setProgress(adcSensorValue);

						TextView tvAdcvalue = (TextView) findViewById(R.id.tvADCValue);
						String s = "timestamp: " + nmeaTimestamp + " value: "
								+ String.valueOf(adcSensorValue) + "\n";
						tvAdcvalue.append(s);

						resetADKCounter();
					}
				});

			}

		});
	}

	public void resetADKCounter() {

	}

	/* Called when the LED button is clicked */
	public void onClick(View v) {
		byte data;

		/* Toggle the state of LED */
		if (LEDState == true) {
			LEDState = false;
			data = 0;
			bOutPutLED.setText("LED Off");
		} else {
			LEDState = true;
			data = 1;
			bOutPutLED.setText("LED On");
		}

		try {
			/* Send the state of LED to ADK Main Board as a byte */
			server.send(new byte[] { (byte) data });
		} catch (IOException e) {
			Log.e("CosmicRayDetector ADK", "problem sending TCP message", e);
		}

	}

	public void onLocationChanged(Location location) {
		lat = (location.getLatitude());
		lng = (location.getLongitude());

		//Log.i(TAG, lat + ", " + lng);
	}

	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub

	}

	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub

	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub

	}

}

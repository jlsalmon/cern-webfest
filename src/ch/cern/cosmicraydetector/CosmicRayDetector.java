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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
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
import android.widget.Toast;

public class CosmicRayDetector extends Activity implements OnClickListener,
		LocationListener {

	private int adcSensorValue = 0;
	private static final String TAG = "CosmicRayDetector";

	private TextView tvAdcvalue;
	private SeekBar sbAdcValue;
	private Button bOutPutLED;
	private TextView fixView;

	private boolean LEDState = false; // initially OFF

	private Server server = null;
	private Handler mHandler;

	private LocationManager service;
	private double lat;
	private double lng;
	private String nmeaTimestamp;
	private String eventTime;
	private double fraction;
	private String provider;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		fixView = (TextView) findViewById(R.id.tvCaption);
		bOutPutLED = (Button) findViewById(R.id.buttonOuputLED);
		bOutPutLED.setOnClickListener(this);

		/* Create TCP server (based on MicroBridge LightWeight Server) */
		this.initTcpServer();

		this.initLocationService();

		/* Poll every 10 seconds and generate a timestamp */
		this.pollArduino(1000);

	}

	private void pollArduino(final int delay) {
		mHandler = new Handler();

		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try {
						Thread.sleep(delay);
						mHandler.post(resetArduinoCounter());
					} catch (Exception e) {
						// TODO: handle exception
					}
				}
			}

			private Runnable resetArduinoCounter() {

				byte data = '0';
				try {
					server.send(new byte[] { data });
				} catch (IOException e) {
					e.printStackTrace();
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

					Log.i(TAG, nmea);
					Log.i(TAG, x);

					if (x.length() > 1) {
						eventTime = x.substring(0, 2) + ":" + x.substring(2, 4)
								+ ":" + x.substring(4, 6);
					}

					SimpleDateFormat datefmt = new SimpleDateFormat("dd/MM/yy");
					NumberFormat numfmt = new DecimalFormat("+#;-#");

					nmeaTimestamp = (String) datefmt.format(new Date())
							.toString()
							+ " "
							+ eventTime
							+ " 00100 "
							+ numfmt.format((lat * 3600000))
							+ " "
							+ numfmt.format((lng * 3600000)) + " " + fraction;

					// Toast.makeText(getApplicationContext(), nmeaTimestamp,
					// Toast.LENGTH_SHORT).show();

					if (eventTime == null) {
						fixView.setText("No GPS fix");
					} else {
						fixView.setText("GPS fix acquired");
					}
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

		Criteria c = new Criteria();
		c.setAccuracy(Criteria.ACCURACY_FINE);
		provider = service.getBestProvider(c, false);
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

				if (adcSensorValue < 10000) {
					fraction = (float) adcSensorValue * 0.000212f;
				}

				/*
				 * Any update to UI can not be carried out in a non UI thread
				 * like the one used for Server. Hence runOnUIThread is used.
				 */
				runOnUiThread(new Runnable() {
					public void run() {

						Toast.makeText(getApplicationContext(),
								Integer.toString(adcSensorValue),
								Toast.LENGTH_SHORT).show();

						Location location = service
								.getLastKnownLocation(provider);

						if (location != null) {
							onLocationChanged(location);
						} else {
							Log.w(TAG, "Location not available");
						}

						TextView tvAdcvalue = (TextView) findViewById(R.id.tvADCValue);

						String x;
						if (eventTime == null) {
							x = "Event detected (no GPS info)\n";
						} else {
							x = "Event detected at: " + eventTime + "\n"
									+ "\t timestamp: " + nmeaTimestamp + "\n"
									+ "\t counter: " + adcSensorValue + "\n";
						}

						// String s = "timestamp: " + nmeaTimestamp + " value: "
						// + String.valueOf(adcSensorValue) + "\n";
						tvAdcvalue.append(x);

						new Thread(new Runnable() {

							public void run() {
								sendDataToCosm(nmeaTimestamp);
							}
						}).start();

					}

				});

			}

		});
	}

	private void sendDataToCosm(String nmeaTimestamp) {
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(
				"http://www.posttestserver.com/post.php?dir=populous");

		try {
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
			nameValuePairs.add(new BasicNameValuePair("nmeaTimestamp",
					nmeaTimestamp));
			nameValuePairs.add(new BasicNameValuePair("counter", Integer
					.toString(adcSensorValue)));
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

			// Execute HTTP Post Request
			HttpResponse response = httpclient.execute(httppost);

		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}

	}

	/* Called when the LED button is clicked */
	public void onClick(View v) {
		byte data;

		/* Toggle the state of LED */
		if (LEDState == true) {
			LEDState = false;
			data = 0;
		} else {
			LEDState = true;
			data = 1;
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

		// Log.i(TAG, lat + ", " + lng);
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

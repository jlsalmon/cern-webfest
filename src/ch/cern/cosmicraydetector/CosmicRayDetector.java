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

import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Button;

public class CosmicRayDetector extends Activity implements OnClickListener {
	private int adcSensorValue = 10;

	// UI Widgets
	TextView tvAdcvalue;
	SeekBar sbAdcValue;
	Button bOutPutLED;

	boolean LEDState = false; // initially OFF

	// Create TCP server (based on MicroBridge LightWeight Server).
	// Note: This Server runs in a separate thread.
	Server server = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		bOutPutLED = (Button) findViewById(R.id.buttonOuputLED);
		bOutPutLED.setOnClickListener(this);

		// Create TCP server (based on MicroBridge LightWeight Server)
		try {
			server = new Server(4568); // Use the same port number used in ADK
										// Main Board firmware
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

				// Any update to UI can not be carried out in a non UI thread
				// like the one used
				// for Server. Hence runOnUIThread is used.
				runOnUiThread(new Runnable() {
					public void run() {
						new UpdateData().execute(adcSensorValue);

					}
				});

			}

		});

	} // End of TCP Server code

	// UpdateData Asynchronously sends the value received from ADK Main Board.
	// This is triggered by onReceive()
	class UpdateData extends AsyncTask<Integer, Integer, String> {
		// Called to initiate the background activity
		@Override
		protected String doInBackground(Integer... sensorValue) {

			// Init SeeekBar Widget to display ADC sensor value in SeekBar
			// Max value of SeekBar is set to 1024
			SeekBar sbAdcValue = (SeekBar) findViewById(R.id.sbADCValue);
			sbAdcValue.setProgress(sensorValue[0]);
			return (String.valueOf(sensorValue[0])); // This goes to result

		}

		// Called when there's a status to be updated
		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			// Not used in this case
		}

		// Called once the background activity has completed
		@Override
		protected void onPostExecute(String result) {
			// Init TextView Widget to display ADC sensor value in numeric.
			TextView tvAdcvalue = (TextView) findViewById(R.id.tvADCValue);
			tvAdcvalue.setText(String.valueOf(result));

		}
	}

	// Called when the LED button is clicked
	public void onClick(View v) {
		byte data;

		// Toggle the state of LED
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
			// Send the state of LED to ADK Main Board as a byte
			server.send(new byte[] { (byte) data });
		} catch (IOException e) {
			Log.e("CosmicRayDetector ADK", "problem sending TCP message", e);
		}

	}

}

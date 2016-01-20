/*************************************************************
 * Application for detecting and predicting seizures using data
 * from the Zephyr Bioharness
 * Used Holograph library created by Daniel Nadeau
 */

package com.example.seizeguard;
import android.app.Activity;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.R.*;
import android.app.Activity;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;

import com.example.seizeguard.*;

/*
import com.echo.holographlibrary.Line;
import com.echo.holographlibrary.LineGraph;
import com.echo.holographlibrary.LinePoint;
 */
import zephyr.android.BioHarnessBT.*;

//Main page main activity
public class MainActivity extends Activity {

	//classes and variables for bluetooth communication
	BluetoothAdapter adapter = null;
	BTClient _bt;
	ZephyrProtocol _protocol;
	NewConnectedListener _NConnListener;
	boolean connected = false;

	//message packet ids
	private final int ECG_MSG_ID = 0x22;
	private final int RtoR_MSG_ID = 0x24;
	private final int HEART_RATE = 0x100;
	//other packet options if included from NewConnectedListener
	/*private final int RESPIRATION_RATE = 0x101;
	private final int SKIN_TEMPERATURE = 0x102;
	private final int POSTURE = 0x103;
	private final int PEAK_ACCLERATION = 0x104;
	private final int ECG = 0x105;*/

	//specifications for graphing ECG data
	int ECG_samples=10;
	short ECG_packet = 63;
	int ECG_NUM = ECG_packet*ECG_samples;
	short ECG_Data[] = new short[ECG_NUM];
	float ECGmin = 400;
	float ECGmax = 600;

	//specifications for RtoR data
	//this is used for graphing instantaneous heart rate
	int RtoR_samples=10;
	int RtoR_packet=18;
	int RtoR_NUM=RtoR_samples*RtoR_packet;
	int RtoR_Data[] = new int[RtoR_NUM];
	int RtoRmin = 0;
	int RtoRmax = 150;
	//R to R data for Seizure detection
	int RtoR_Long = 200;
	double RtoR_Data_Long[] = new double [RtoR_Long];


	//old heart rate data from Bioharness
	int HRnum = 10;
	short HR_Data[] = new short[HRnum];
	int HRmin = 0;
	int HRmax = 150;
	//variables for heart beating animation
	int current=0;
	int beatTime=2;
	boolean beat=false;
	boolean beating=false;

	//gauge shift variables
	int current_gauge=0;
	int gauge_count=0;
	boolean initial=false;

	//sound
	private final int duration = 10; // seconds
	private final int sampleRate = 8000;
	private final int numSamples = duration * sampleRate;
	private final double sample[] = new double[numSamples];
	private final double freqOfTone = 440; // hz
	private final byte generatedSnd[] = new byte[2 * numSamples];
	boolean AlertSound = false;
	Handler handler = new Handler();

	//when page is created
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		//Sending a message to android that we are going to initiate a pairing request
		IntentFilter filter = new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST");
		//Registering a new BTBroadcast receiver from the Main Activity context with pairing request event
		this.getApplicationContext().registerReceiver(new BTBroadcastReceiver(), filter);
		// Registering the BTBondReceiver in the application that the status of the receiver has changed to Paired
		IntentFilter filter2 = new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED");
		this.getApplicationContext().registerReceiver(new BTBondReceiver(), filter2);

		//set text of status message
		TextView tv = (TextView) findViewById(R.id.connection_status);
		String ErrorText  = "Not Connected to BioHarness";
		tv.setText(ErrorText);

		//initialize the data for the graph (will be overwritten)
		for(int x=0;x<ECG_NUM;x++) {ECG_Data[x]=500;}
		for(int x=0;x<HRnum;x++) {HR_Data[x]=60;}
		for(int x=0;x<RtoR_NUM;x++) {RtoR_Data[x]=60;}
		for(int x=0;x<RtoR_Long;x++) {RtoR_Data_Long[x]=0;}

		//create the sound to use
		generate_sound();


	}

	//classes for bluetooth communication
	private class BTBondReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle b = intent.getExtras();
			BluetoothDevice device = adapter.getRemoteDevice(b.get("android.bluetooth.device.extra.DEVICE").toString());
			Log.d("Bond state", "BOND_STATED = " + device.getBondState());
		}
	}
	private class BTBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("BTIntent", intent.getAction());
			Bundle b = intent.getExtras();
			Log.d("BTIntent", b.get("android.bluetooth.device.extra.DEVICE").toString());
			Log.d("BTIntent", b.get("android.bluetooth.device.extra.PAIRING_VARIANT").toString());
			try {
				BluetoothDevice device = adapter.getRemoteDevice(b.get("android.bluetooth.device.extra.DEVICE").toString());
				Method m = BluetoothDevice.class.getMethod("convertPinToBytes", new Class[] {String.class} );
				byte[] pin = (byte[])m.invoke(device, "1234");
				m = device.getClass().getMethod("setPin", new Class [] {pin.getClass()});
				Object result = m.invoke(device, pin);
				Log.d("BTTest", result.toString());
			} catch (SecurityException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (NoSuchMethodException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	//get messages from connection to NewConnectedListener
	final  Handler Newhandler = new Handler(){
		public void handleMessage(Message msg)
		{

			TextView tv;
			switch (msg.what)
			{
			//get bioharness heartrate
			case HEART_RATE:
				String HeartRatetext = msg.getData().getString("HeartRate");
				if(HeartRatetext!=null) {
					for(int i=0;i<(HRnum-1);i++) {
						HR_Data[i]=HR_Data[i+1];
					}
					HR_Data[HRnum-1]=Short.parseShort(HeartRatetext);
					tv = (TextView)findViewById(R.id.labelHeartRate);
					//System.out.println("Heart Rate Info is "+ HeartRatetext);
					if (tv != null)tv.setText(HeartRatetext);

				}
				break;
			//get ecg signals
			case ECG_MSG_ID:
				short ECG[] = msg.getData().getShortArray("ECG");
				for(int i=0;i<ECG_samples-1;i++) {
					for(int j=0;j<ECG_packet;j++) {
						ECG_Data[(i*ECG_packet)+j]=ECG_Data[((i+1)*ECG_packet)+j];
					}
				}
				for(int i=0;i<ECG_packet;i++) {
					ECG_Data[((ECG_samples-1)*ECG_packet)+i]=ECG[i];

				}

				Line l = new Line();
				LinePoint p;
				for (int x = 0; x < ECG_NUM; x++) {
					p = new LinePoint(x, ECG_Data[x]);
					l.addPoint(p);
				}
				l.setShowingPoints(false);


				l.setColor(getResources().getColor(R.color.ECGgraph));
				LineGraph lG = (LineGraph) findViewById(R.id.ECGgraph);
				lG.removeAllLines();
				lG.addLine(l);
				lG.setRangeY(ECGmin,ECGmax);
				lG.setLineToFill(0);
				lG.setFillColor(getResources().getColor(R.color.background));
				lG.setFillAlpha(100);//was 100
				lG.setFillStrokeWidth(0);//was 3

				//stuff for controlling heart beating
				current++;
				if(current>=beatTime && beat==false) {
					ImageView IV = (ImageView)findViewById(R.id.heart_pic);
					IV.setImageDrawable(getResources().getDrawable(R.drawable.hr_nopulse));
					beatTime=1;
					current=0;
					beat=true;}
				else if(current>=beatTime && beat==true) {
					ImageView IV = (ImageView)findViewById(R.id.heart_pic);
					IV.setImageDrawable(getResources().getDrawable(R.drawable.hr_pulse));
					beatTime=(int)(300.0/(float)HR_Data[HRnum-1])-1;
					current=0;
					beat=false;}

				//stuff for controlling the gauge
				if(current_gauge>=0) {
					if(gauge_count>=2 || initial==true) {
						ImageView IV = (ImageView)findViewById(R.id.gauge);
						IV.setImageDrawable(getResources().getDrawable(R.drawable.gauge));
						String variableValue = "gauge" + Integer.toString(current_gauge);
						IV.setImageResource(getResources().getIdentifier(variableValue, "drawable", getPackageName()));
						current_gauge-=10;
						gauge_count=0;
						initial=false;

						IV = (ImageView)findViewById(R.id.health_status);
						if(current_gauge>=180) {
							IV.setImageDrawable(getResources().getDrawable(R.drawable.alert));
						}
						else if(current_gauge>=120) {
							IV.setImageDrawable(getResources().getDrawable(R.drawable.warning_orange));
						}
						else if(current_gauge>=60) {
							IV.setImageDrawable(getResources().getDrawable(R.drawable.warning_yellow));
						}
						else {
							IV.setImageDrawable(getResources().getDrawable(R.drawable.normal));
						}	
					}
					else {
						gauge_count++;
						ImageView IV = (ImageView)findViewById(R.id.health_status);
						if(current_gauge>=180) {
							IV.setImageDrawable(getResources().getDrawable(R.drawable.alert));
						}
						else if(current_gauge>=120) {
							IV.setImageDrawable(getResources().getDrawable(R.drawable.warning_orange));
						}
						else if(current_gauge>=60) {
							IV.setImageDrawable(getResources().getDrawable(R.drawable.warning_yellow));
						}
						else {
							IV.setImageDrawable(getResources().getDrawable(R.drawable.normal));
						}	
					}
				}			
				break;

			//get R to R data from bioharness
			case RtoR_MSG_ID:
				int RtoR[] = msg.getData().getIntArray("RtoR");
				for(int i=0;i<RtoR_samples-1;i++) {
					for(int j=0;j<RtoR_packet;j++) {
						RtoR_Data[(i*RtoR_packet)+j]=RtoR_Data[((i+1)*RtoR_packet)+j];
					}
				}

				double most_recent=RtoR_Data_Long[RtoR_Long-1];
				for(int i=0;i<RtoR_packet;i++) {
					float temp=RtoR[i];
					if(RtoR[i] > 0x8000) {
						float temp2= (RtoR[i]^0xFFFF) + 1;

						temp=temp2/1000;
						if(most_recent!=temp) {
							for(int j=0;j<RtoR_Long-1;j++) {
								RtoR_Data_Long[j]=RtoR_Data_Long[j+1];
								//System.out.print(RtoR_Data_Long[j]);
							}
							most_recent=temp;
							RtoR_Data_Long[RtoR_Long-1]=temp;
						}
						if(temp>0 ) {
							RtoR_Data[((RtoR_samples-1)*RtoR_packet)+i]=(int) (60/temp);
						}
						else
							RtoR_Data[((RtoR_samples-1)*RtoR_packet)+i]=0;
					}
					else {
						temp=temp/1000;
						if(most_recent!=temp) {
							for(int j=0;j<RtoR_Long-1;j++) {
								RtoR_Data_Long[j]=RtoR_Data_Long[j+1];
							}
							most_recent=temp;
							RtoR_Data_Long[RtoR_Long-1]=temp;
						}
						//System.out.println("Heart Rate Info is "+ temp);
						if(temp>0) {
							RtoR_Data[((RtoR_samples-1)*RtoR_packet)+i]=(int) (60/temp);
						}
						else
							RtoR_Data[((RtoR_samples-1)*RtoR_packet)+i]=0;
					}



				}

				Line l3 = new Line();
				LinePoint p3;
				for (int x = 0; x < RtoR_NUM; x++) {
					p3 = new LinePoint(x, RtoR_Data[x]);
					l3.addPoint(p3);
				}
				l3.setShowingPoints(true);

				// Set line color
				//l3.setColor(Color.parseColor("#FFFFFF"));
				l3.setColor(getResources().getColor(R.color.background));	
				LineGraph lG3 = (LineGraph) findViewById(R.id.RtoRgraph);
				lG3.removeAllLines();
				lG3.addLine(l3);
				lG3.setRangeY(RtoRmin,RtoRmax);
				lG3.setLineToFill(0);
				//lG3.setFillColor(Color.parseColor("#FFFFFF"));
				lG3.setFillColor(getResources().getColor(R.color.background));
				lG3.setFillAlpha(100);//was 100
				lG3.setFillStrokeWidth(0);//was 3
				lG3.showMinAndMaxValues(true);//was 3
				lG3.setTextColor(getResources().getColor(R.color.HRgraph));

				detectSeizure();

				break;
			}
		}

	};


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);

		return true;
	}

	//connect and disconnect with bluetooth through options menu
	public boolean onOptionsItemSelected(MenuItem item) {
		int selected = item.getOrder();
		switch(selected) {
		//connect selected
		case 0:
			if(connected==false) {
				String BhMacID = "00:07:80:9D:8A:E8";
				//String BhMacID = "00:07:80:88:F6:BF";
				adapter = BluetoothAdapter.getDefaultAdapter();

				Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

				if (pairedDevices.size() > 0) 
				{
					for (BluetoothDevice device : pairedDevices) 
					{
						if (device.getName().startsWith("BH")) 
						{
							BluetoothDevice btDevice = device;
							BhMacID = btDevice.getAddress();
							break;
						}
					}
				}

				BluetoothDevice Device = adapter.getRemoteDevice(BhMacID);
				String DeviceName = Device.getName();
				_bt = new BTClient(adapter, BhMacID);
				_NConnListener = new NewConnectedListener(Newhandler,Newhandler);
				_bt.addConnectedEventListener(_NConnListener);


				if(_bt.IsConnected())
				{
					_bt.start();
					TextView tv = (TextView) findViewById(R.id.connection_status);
					String ErrorText  = "Connected to BioHarness";
					tv.setText(ErrorText);
					connected=true;
					//Reset all the values to 0s

				}
				else
				{
					TextView tv = (TextView) findViewById(R.id.connection_status);
					String ErrorText  = "Unable to Connect";
					tv.setText(ErrorText);
					connected=false;
				}     
			}
			break;
			//disconnect selected
		case 1:
			if(connected==true) {
				TextView tv = (TextView) findViewById(R.id.connection_status);
				String ErrorText  = "Disconnected from BioHarness";
				tv.setTextColor(Color.parseColor("#A9A9A9"));
				tv.setText(ErrorText);
				//This disconnects listener from acting on received messages	
				//_bt.removeConnectedEventListener(_NConnListener);
				//Close the communication with the device & throw an exception if failure
				_bt.Close();
				connected=false;
			}
			break;
		}
		return false;
	}

	//generate alarm sound
	private void generate_sound() {
		for (int i = 0; i < numSamples; ++i) {
			sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
		}
		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalized.
		int idx = 0;
		for (final double dVal : sample) {
			// scale to maximum amplitude
			final short val = (short) ((dVal * 32767));
			// in 16 bit wave PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte) (val & 0x00ff);
			generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
		}
	}

	//go to history page
	public void history_view(View view) {
		Intent intent = new Intent(this, HistoryActivity.class);
		startActivity(intent);
	}

	//go to demo
	public void demo_view(View view) {
		Intent intent = new Intent(this, DemoActivity.class);
		startActivity(intent);
	}

	//will check for seizure and change status message
	public void detectSeizure() {
		//for(int j=0;j<RtoR_Long;j++) {
			//RtoR_Data_Long[j]=RtoR_Data_Long[j+1];
			//System.out.println(RtoR_Data_Long[j]);
		//}
		int PredictionLevel=0;
		SeizurePredictor SP = new SeizurePredictor();

		PredictionLevel = SP.PredictSeizure(RtoR_Data_Long, RtoR_Long);

		//ImageView IV = (ImageView)findViewById(R.id.health_status);
		//System.out.println(PredictionLevel);
		if(PredictionLevel>0) {
			if(PredictionLevel>=4) {
				if(AlertSound==false) {
					final Thread thread = new Thread(new Runnable() {
						public void run() {
							handler.post(new Runnable() {

								public void run() {
									playSound();
								}
							});
						}
					});
					thread.start();
					//AlertSound=true;
				}
			}
			ImageView IV = (ImageView)findViewById(R.id.health_status);
			if(PredictionLevel==4) {
				current_gauge=240;
				IV.setImageDrawable(getResources().getDrawable(R.drawable.alert));
			}
			else if(PredictionLevel==3) {
				current_gauge=180;
				IV.setImageDrawable(getResources().getDrawable(R.drawable.warning_orange));
			}
			else if(PredictionLevel==2) {
				current_gauge=120;
				IV.setImageDrawable(getResources().getDrawable(R.drawable.warning_yellow));
			}
			else if(PredictionLevel==1) {
				current_gauge=60;
			}
			initial=true;
		}
		else {
		}
	}

	//play the created sound
	void playSound(){
		final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_ALARM,
				sampleRate, AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
				AudioTrack.MODE_STATIC);
		audioTrack.write(generatedSnd, 0, generatedSnd.length);
		audioTrack.play();
	}

}

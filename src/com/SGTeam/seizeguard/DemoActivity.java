package com.example.seizeguard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.seizeguard.*;

public class DemoActivity extends Activity {
	//current positions in list of data
	int ECG_counter=0;
	int RtoR_counter=0;
	int HR_counter=0;

	int ECG_NUM = 400;

	//long list of ecg data from files
	public  ArrayList<Integer> ECG_Data  = new ArrayList<Integer>();
	public  ArrayList<Integer> RtoR_Data  = new ArrayList<Integer>();

	int delay=0;
	int delay_count=0;

	int HRnum = 200;
	int HR_Data[] = {};
	int ECG_samples=10;
	short ECG_packet = 63;
	int ECG_NUMshow = ECG_samples*ECG_packet;
	int ECG_Datashow[] = new int[ECG_NUMshow];
	int current_ECG=0;
	float ECGmin = -6000;
	float ECGmax = 6000;

	int RtoR_samples=10;
	int RtoR_packet=18;
	int RtoR_NUMshow=RtoR_samples*RtoR_packet;
	int RtoR_Datashow[] = new int[RtoR_NUMshow];
	int current_RtoR=0;
	int RtoRmin = 0;
	int RtoRmax = 150;

	int HRnumshow = 10;
	int HR_Datashow[] = new int[HRnumshow];
	
	//R to R data for seizure detection
	int RtoR_Long = 200;
	double RtoR_Data_Long[] = new double [RtoR_Long];

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
	public Thread thread = null;
	private final int duration = 3; // seconds was 10
	private final int sampleRate = 8000;
	private final int numSamples = duration * sampleRate;
	private final double sample[] = new double[numSamples];
	private final double freqOfTone = 440; // hz

	private final byte generatedSnd[] = new byte[2 * numSamples];

	boolean AlertSound = false;

	boolean killAll=false;

	//timed periodic events to simulate bioharness
	int time=0;
	boolean end =false;
	boolean stopped=false;
	Handler handler = new Handler();
	Handler timerHandler = new Handler();
	Runnable timerRunnable = new Runnable() {

		@Override
		public void run() {
			time+=10;
			//end the demo a little early to prevent crashing
			if(current_RtoR>=RtoR_Data.size()-20 || current_ECG>=ECG_Data.size()-20) { //426000
				finish();
				end=true;
			}
			
			//show when clinical onset occurred
			if(time==38800) {
				AlertDialog alertDialog = new AlertDialog.Builder(DemoActivity.this).create();
				alertDialog.setTitle("Clinical Onset");
				alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
				// here you can add functions
					timerHandler.postDelayed(timerRunnable, 0);
				}
				});	
				alertDialog.show();
			}
			
			else {
				//changing heart rate text
				if(0==time%250) {
					for(int i=0;i<(HRnumshow-1);i++) {
						HR_Datashow[i]=HR_Datashow[i+1];
					}
					double temp=RtoR_Data.get(current_RtoR)/1000.0;
					HR_Datashow[HRnumshow-1]=(int)(60/temp);
					TextView tv = (TextView)findViewById(R.id.labelHeartRate);
					if (tv != null)tv.setText(String.valueOf(HR_Datashow[HRnumshow-1]));

				}

				//simulate ECG packet
				if(0==time%90) { //was 88
					if(current_ECG>=ECG_Data.size()-20) { 
						finish();
						end=true;
						current_ECG=0;
					}
					else {
						for(int i=0;i<ECG_samples-1;i++) {
							for(int j=0;j<ECG_packet;j++) {
								ECG_Datashow[(i*ECG_packet)+j]=ECG_Datashow[((i+1)*ECG_packet)+j];
							}
						}
						for(int i=0;i<ECG_packet;i++) {
							ECG_Datashow[((ECG_samples-1)*ECG_packet)+i]=ECG_Data.get(current_ECG);
							current_ECG++;
						}

						Line l = new Line();
						LinePoint p;
						for (int x = 0; x < ECG_NUM; x++) {
							p = new LinePoint(x, ECG_Datashow[x]);
							l.addPoint(p);
						}
						l.setShowingPoints(false);

						// Set line color
						//l.setColor(Color.parseColor("#008000"));
						l.setColor(getResources().getColor(R.color.ECGgraph));
						LineGraph lG = (LineGraph) findViewById(R.id.ECGgraph);
						lG.removeAllLines();
						lG.addLine(l);
						lG.setRangeY(ECGmin,ECGmax);
						lG.setLineToFill(0);
						lG.setFillColor(getResources().getColor(R.color.background));
						lG.setFillAlpha(100);//was 100
						lG.setFillStrokeWidth(0);//was 3	

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
							beatTime=(int)(300.0/(float)HR_Datashow[HRnumshow-1])-1;
							current=0;
							beat=false;}

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

						detectSeizure();
					}
				}
				//simulate R to R packet
				if(0==time%300) { //was 308
					if(current_RtoR>=RtoR_Data.size()-20) { 
						finish();
						current_RtoR=0;
					}
					else{
						for(int i=0;i<RtoR_samples-1;i++) {
							for(int j=0;j<RtoR_packet;j++) {
								RtoR_Datashow[(i*RtoR_packet)+j]=RtoR_Datashow[((i+1)*RtoR_packet)+j];
							}
						}

						double most_recent=RtoR_Data_Long[RtoR_Long-1];

						for(int i=0;i<RtoR_packet;i++) {

							double temp=((float)RtoR_Data.get(current_RtoR))/1000.0;

							current_RtoR++;
							if(most_recent!=temp) {
								for(int j=0;j<RtoR_Long-1;j++) {
									RtoR_Data_Long[j]=RtoR_Data_Long[j+1];
								}
								RtoR_Data_Long[RtoR_Long-1]=temp;
								most_recent=temp;
							}
							temp=(int)(60/temp);
							RtoR_Datashow[((RtoR_samples-1)*RtoR_packet)+i]=(int)temp;
						}	

						Line l3 = new Line();
						LinePoint p3;
						for (int x = 0; x < RtoR_NUMshow; x++) {
							p3 = new LinePoint(x, RtoR_Datashow[x]);
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

					}
				}
				//timerHandler.postDelayed(this, 1);
				if(!killAll)
					timerHandler.postDelayed(this, 10);
			}
		}
	};


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_demo);
		TextView tv = (TextView) findViewById(R.id.connection_status);
		String ErrorText  = "Connected to BioHarness";
		tv.setText(ErrorText);

		killAll=false;

		for(int x=0;x<ECG_NUMshow;x++) {ECG_Datashow[x]=500;}
		for(int x=0;x<HRnumshow;x++) {HR_Datashow[x]=60;}
		for(int x=0;x<RtoR_NUMshow;x++) {RtoR_Datashow[x]=60;}
		for(int x=0;x<RtoR_Long;x++) {RtoR_Data_Long[x]=0;}
		delay=0;	

		InputStream is = null;
		try {

			try {
				is = getAssets().open("170_RRI2.csv");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				//System.out.println("Did not open");
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = "";
			try {
				while ((line = reader.readLine()) != null) {
					String[] numbers = line.split(",");
					for(int i=0;i<numbers.length;i++) {
						RtoR_Data.add(Integer.parseInt(numbers[i]));
						//System.out.println(Integer.toString(i));
					}
				}
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		finally {
			try {
				is.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		InputStream is2 = null;
		try {

			try {
				is2 = getAssets().open("170_ECG.csv");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				//System.out.println("Did not open");
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is2));
			String line = "";
			try {
				while ((line = reader.readLine()) != null) {
					String[] numbers = line.split(",");
					for(int i=0;i<numbers.length;i++) {
						ECG_Data.add(Integer.parseInt(numbers[i]));
						//System.out.println(Integer.toString(ECG_Data.get(i)));
					}
				}
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		finally {
			try {
				is2.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		generate_sound();
		//start receiving simulated packets
		timerHandler.postDelayed(timerRunnable, 0);
	}

	private void generate_sound() {
		for (int i = 0; i < numSamples; ++i) {
			sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
		}

		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalised.
		int idx = 0;
		for (final double dVal : sample) {
			// scale to maximum amplitude
			final short val = (short) ((dVal * 32767));
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte) (val & 0x00ff);
			generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

		}
	}

	//call seizure detection algorithm
	public void detectSeizure() {
		//for(int i=0;i<RtoR_Long;i++) {
			//System.out.println(Double.toString(RtoR_Data_Long[i]));
		//}

		int PredictionLevel=0;
		SeizurePredictor SP = new SeizurePredictor();

		PredictionLevel = SP.PredictSeizure(RtoR_Data_Long, RtoR_Long);

		//ImageView IV = (ImageView)findViewById(R.id.health_status);

		if(PredictionLevel>0) {
			//IV.setImageDrawable(getResources().getDrawable(R.drawable.alert));
			if(PredictionLevel>=4) {
				
				if(AlertSound==false) {
					thread = new Thread(new Runnable() {
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
			//IV.setImageDrawable(getResources().getDrawable(R.drawable.normal));
		}
	}

	//play generated alarm sound
	void playSound(){
		final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_ALARM,
				sampleRate, AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
				AudioTrack.MODE_STATIC);
		audioTrack.write(generatedSnd, 0, generatedSnd.length);
		if(audioTrack!=null) {
			audioTrack.play();
		}
	}


	//stuff for safety
	public void onBackPressed() {
		super.onBackPressed();
		finish();
	}
	
	public void onPause() {
		super.onPause();
		killAll=true;
		finish();
		ECG_counter=0;
		RtoR_counter=0;
		if(thread!=null) {
			thread.interrupt();}
		for(int x=0;x<ECG_NUMshow;x++) {ECG_Datashow[x]=500;}
		for(int x=0;x<HRnumshow;x++) {HR_Datashow[x]=60;}
		for(int x=0;x<RtoR_NUMshow;x++) {RtoR_Datashow[x]=60;}
		for(int x=0;x<RtoR_Long;x++) {RtoR_Data_Long[x]=0;}
	}
	public void onStop() {
		super.onStop();
		killAll=true;
		finish();
		ECG_counter=0;
		RtoR_counter=0;
		if(thread!=null) {
			thread.interrupt();}
		for(int x=0;x<ECG_NUMshow;x++) {ECG_Datashow[x]=500;}
		for(int x=0;x<HRnumshow;x++) {HR_Datashow[x]=60;}
		for(int x=0;x<RtoR_NUMshow;x++) {RtoR_Datashow[x]=60;}
		for(int x=0;x<RtoR_Long;x++) {RtoR_Data_Long[x]=0;}
	}
}


package com.seizuredetection.ecg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class EcgSignal {
	
	static int Fs = 200;
	static double R2_threshold = 0.8;
	static double slope_lower_threshold = 0.7;
	
	//This is the sum of R-R Intervals. Amount of time the signal spans
	double Time = 0;
	
	//variables for HR regression line
	double slope=0;
	double R2=0;
	double Intercept=0;
	
	
	ArrayList<Integer> RawSignal = new ArrayList<Integer>();
	ArrayList<Double> HeartRate = new ArrayList<Double>();
	ArrayList<Double> RRI = new ArrayList<Double>();
		
	public EcgSignal(){
		//Does nothing
	}
	
	//constructor from data file (for testing)
	public EcgSignal(String dataFileName) throws Exception{
				
		BufferedReader bReader = new BufferedReader(new FileReader(dataFileName));
		
		String line;
		
		while((line = bReader.readLine()) != null){
		
		String data[] = line.split("\t");
		
		
			for(int i=0;i<data.length;i++){
				RRI.add(Double.parseDouble(data[i]));
			}
		}
		
	}
	
	public void SetEcg(int[] EcgData,int length){
		//code to import to ArrayList
		for(int i=0;i<length;i++){
			RawSignal.add(EcgData[i]);
		}
		
	}
	
	public void BufferEcg(int[] EcgData,int length){
		
		for(int i=0;i<length;i++){
			RawSignal.remove(0); //Remove the first element
			RawSignal.add(EcgData[i]); //Add the desired element to the end
		}
	}
	
	public void SetHeartRate(double[] HrData,int length){
		for(int i=0;i<length;i++){
			HeartRate.add(HrData[i]);
		}
	}
	
	public void BufferHeartRate(double[] HrData,int length){
		for(int i=0;i<length;i++){
			HeartRate.remove(0);
			HeartRate.add(HrData[i]);
		}
	}
	
	public void SetRRI(double[] RRIData,int length){
		for(int i=0;i<length;i++){
			RRI.add(RRIData[i]);
			Time+=RRIData[i];
		}
	}
	
	public void SetRRI(double RRIPoint){
		RRI.add(RRIPoint);
		Time+=RRIPoint;
	}
	
	public void SetRRI(double RRIPoint,int ind){
		RRI.add(ind, RRIPoint);
		Time+=RRIPoint;
	}
	
	public void RmRRI(int index){
		Time-=RRI.get(index);
		RRI.remove(index);
	}
	
	public void BufferRRI(double[] RRIData,int length){
		for(int i=0;i<length;i++){
			Time-=RRI.get(0);
			RRI.remove(0);
			RRI.add(RRIData[i]);
			Time+=RRIData[i];
		}
	}
	
	public void BufferRRI(double RRIPoint){
		
		Time-=RRI.get(0);
		RRI.remove(0);
		RRI.add(RRIPoint);
		Time+=RRIPoint;
	}
	
	public static double[] convertDoubles(ArrayList<Double> doubles)
	{
	    double[] ans = new double[doubles.size()];
	    for(int i=0;i<doubles.size();i++){
	    	ans[i] = doubles.get(i).doubleValue();
	    }
	    
	    return ans;
	}
	
	//public boolean PredictSeizure(){
	
	//}
	
	public boolean DetectSeizure(){
		boolean SeizureFlag=false;
		double resid = 0,sd=0;
		double summer = 0;
		int length=RRI.size();
		
		
		//Integer[] EcgArray = RawSignal.toArray(new Integer[] {}); 
		
		double[] RriArray = convertDoubles(RRI);
		double[] HrArray = new double[length];
		double[] TimeArray = new double[length];
		
		for(int i=0;i<(length);i++){
			TimeArray[i]=summer;
			HrArray[i]=60/(RriArray[i]);
			summer+=RriArray[i];
		}
		
		SimpleRegression LinFit = new SimpleRegression();
		
		//for(int i=0;i<(length);i++){
		//	System.out.print("Time[i]: ");
		//	System.out.print(TimeArray[i]);
		//	System.out.print("\tHR[i]: ");
		//	System.out.print(HrArray[i]);
		//	System.out.print("\t RR[i]: ");
		//	System.out.print(RriArray[i]);
		//	System.out.print("\n");
		//}
		for(int i=0;i<(length);i++){
			LinFit.addData(TimeArray[i],HrArray[i]);
		}
		
		slope = LinFit.getSlope();
		R2 = LinFit.getRSquare();
		Intercept = LinFit.getIntercept();
		
		if(R2>R2_threshold && slope > slope_lower_threshold){
			SeizureFlag = true;
		}else if(slope > 0.3){ //transform the data and remove outliers. Don't waste
							   //time if slope is less than 0.3 
			//1) If residual is > +/-1.96 sd away from 0, remove
			sd = Math.sqrt(LinFit.getMeanSquareError());
			for(int i=0;i<length;i++){
				resid = HrArray[i] - LinFit.predict(TimeArray[i]); 
				if(Math.abs(resid) > 1.96*sd){
					LinFit.removeData(TimeArray[i], HrArray[i]);
				}
				
			}
			
			slope = LinFit.getSlope();
			R2 = LinFit.getRSquare();
			Intercept = LinFit.getIntercept();
			
			if(R2>R2_threshold && slope > slope_lower_threshold){
				SeizureFlag=true;
			}
		}
		
				
		return SeizureFlag;
	}
	
	
	
	
	void print(){
		for(int i=0;i<RawSignal.size();i++){
			System.out.println(RawSignal.get(i) + "\n");
		}
	
	}
}
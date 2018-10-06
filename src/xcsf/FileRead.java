package xcsf;

import java.io.*;
import java.util.*;

/**
 * Reads the training and test files and makes available training and test 
 * instances per request at each iteration. The values can be
 * loaded from a {@link Properties} file using the {@link #load(String)} method.
 * 
 * @author Shabnam Nazmi
 */

public class FileRead { 
	
	int inputSize;
	int outputSize;
	int dataSize;
	int Dataref;
	String filename;
	ArrayList rawData = new ArrayList();
	ArrayList Data_formatted = new ArrayList();
	double[] Data_X;
	double[] Data_Y;
	int Data_label;
	Random rand = new Random();

	// getters
	public int getInputsize() {
		return inputSize;
	}
	
	public int getOutputsize() {
		return outputSize;
	}
		
	public double[] getInstanceX() {
		return Data_X;		
	}
	
	public double[] getInstanceY() {
		return Data_Y;		
	}
	
	public int getInstanceLabel() {
		return Data_label;
	}
	
	public ArrayList getDataset() {
		return rawData;
	}
	
	public ArrayList getDataFormatted() {
		return Data_formatted;
	}
	
	public int getDataSize() {
		return this.dataSize;
	}
	
	// setters
	public void setInputsize(int isize) {
		this.inputSize = isize;		
	}
	
	public void setOutoutsize(int osize) {
		this.outputSize = osize;		
	}
	
	public void setFilename(String name) {
		this.filename = name;
	}
	
	public void setInstanceX(double[] x) {
		this.Data_X = x;
	}
	
	public void setInstanceY(double[] y) {
		this.Data_Y = y;
	}
		
	public void setDataset(ArrayList Data) {
		this.rawData = Data;
	}
	
	public void setDataFormatted(ArrayList Data_format) {
		this.Data_formatted = Data_format;
	}
	
	public void setDataRef(int dataref) {
		this.Dataref = dataref;
	}
	
	public void setLabel(int label) {
		this.Data_label = label;
	}
	
	
	// Read data text file
	public void FileRead(String Filename) throws IOException {		
		    	
    	try (BufferedReader br = new BufferedReader(new FileReader(Filename))) {
    	    String line;
    	    String[] myString;
    	    ArrayList rawData = new ArrayList();
    	    
    	    line = br.readLine();
    	    while ((line = br.readLine()) != null) {
        	    ArrayList dataLine = new ArrayList();
    	    	myString = line.split("\t");
    	    	
    	    	for(int i=0; i < myString.length; i++) {
    	    		dataLine.add(Double.parseDouble(myString[i]));
    	    	} 
    	    	rawData.add(dataLine);  
    	    this.setDataset(rawData);	
        	this.dataSize = rawData.size();
    	    }
    	}
	}
	
	// shuffle raw data
	public void setDataFormatted() {
		ArrayList rawData = this.getDataset();	
		//Collections.shuffle(rawData);
		this.setDataFormatted(rawData);
		
	}
	
	// get an input and output vector from the data set
	public void getInstance() {
		double [] input = new double[this.getInputsize()];
		double [] output = new double[this.getOutputsize()];					
		double [] sample = this.refTracker();	
		int i = 0;
		while(i < this.getInputsize()) {
			input[i] = sample[i];
			i++;
		}
		this.setInstanceX(input);

		while(i < inputSize+outputSize) {
			output[i - this.getInputsize()] = sample[i];
			i++;
		}
		this.setInstanceY(output);
		this.setLabel((int) sample[inputSize+outputSize]);
		
	}
	
	public double[] refTracker() {
		ArrayList data = this.getDataFormatted();
		List<Double> temp = new ArrayList<Double>();
		double[] sample = new double [ this.getInputsize() + this.getOutputsize() + 1 ];
		 
		if (this.Dataref < this.dataSize) {
			temp = (List) data.get(this.Dataref);			
			for (int i = 0; i < temp.size(); i++) {	
				sample[i] = temp.get(i);
			}
			this.setDataRef(this.Dataref + 1);
		} 
		else {
			this.setDataRef(0);
			temp = (List) data.get(this.Dataref);
			for (int i = 0; i < temp.size(); i++) {			
				sample[i] = temp.get(i).doubleValue();
			}
			this.setDataRef(this.Dataref + 1);
		}
		return sample;
	}
	
	public void loadData(Boolean isTrain) {
		String TrainFilename = XCSFConstants.TrainFilename;
		String TestFilename = XCSFConstants.TestFilename;		
		this.setInputsize(XCSFConstants.Inputsize);
    	this.setOutoutsize(XCSFConstants.Outputsize);
    	this.setDataRef(0);
    	   	
    	if (isTrain) {    	
	    	try {
				this.FileRead(TrainFilename);
				this.setDataFormatted();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}else {
	    	try {
				this.FileRead(TestFilename);
				this.setDataFormatted();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}    	
	}
}   






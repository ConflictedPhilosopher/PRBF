/*
 * created on Jul 29, 2008
 */
package xcsf.classifier;

import java.io.PrintStream;
import java.util.Arrays;

import xcsf.XCSFConstants;
import xcsf.XCSFUtils;

/**
 * Implementation of a simple constant prediction based on the Widrow-Hoff rule.
 * 
 * @author Patrick Stalph
 */
public class PredictionConstant implements Prediction {

    private int predictionLength; // dimension of function output
    private double[] prediction;

    /**
     * Default constructor with given input length and the actual function value
     * (used as initial prediction). The constant prediction only depends on the
     * condition shape, not on the prediction input.
     * 
     * @param inputlength
     *            not used
     * @param initialPrediction
     *            the initial prediction value
     */
    public PredictionConstant(@SuppressWarnings("unused") int inputlength//
            /*
             * although the length is not required for this prediction, the
             * classloader for arbitrary prediction classes requires the
             * signature: (int, double[])
             */
            , double[] initialPrediction) {
        this.predictionLength = initialPrediction.length;
        this.prediction = new double[predictionLength];
        for (int i = 0; i < predictionLength; i++) {
            this.prediction[i] = initialPrediction[i];
        }
    }

    /**
     * Parses the given String array. For details, how this constructor is
     * called, see {@link Classifier#parse(String, String, String)}.
     * 
     * @param args
     *            the splited String
     */
    public PredictionConstant(String[] args) {
        this();
        this.predictionLength = Integer.parseInt(args[0]);
        this.prediction = XCSFUtils.FileIO.parseDoubleArray(args[1]);
    }

    /**
     * Private empty constructor for efficient cloning and parsing.
     */
    private PredictionConstant() {
        // empty
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#predict(double[])
     */
    public double[] predict(double[] input) {
        double[] pred = new double[predictionLength];
        System.arraycopy(this.prediction, 0, pred, 0, predictionLength);
        return pred;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#updatePrediction(double[], double[])
     */
    public void update(double[] input, double[] functionValue) {
        // update using the Widrow-Hoff delta rule (ignoring prediction input)
        for (int i = 0; i < predictionLength; i++) {
            double error = functionValue[i] - prediction[i];
            this.prediction[i] += XCSFConstants.beta * error;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#crossover(xcsf.classifier.Prediction)
     */
    public void crossover(Prediction otherPrediction) {
        PredictionConstant other = (PredictionConstant) otherPrediction;
        for (int i = 0; i < predictionLength; i++) {
            double avg = this.prediction[i] + other.prediction[i];
            this.prediction[i] = other.prediction[i] = avg / 2.0;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#reproduce()
     */
    public Prediction reproduce() {
        PredictionConstant clone = new PredictionConstant();
        clone.predictionLength = this.predictionLength;
        clone.prediction = new double[predictionLength];
        System.arraycopy(this.prediction, 0, clone.prediction, 0,
                predictionLength);
        return clone;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "prediction{" + Arrays.toString(this.prediction) + "}";
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#write(java.io.PrintStream,
     * java.lang.CharSequence)
     */
    public void write(PrintStream out, CharSequence separator) {
        StringBuffer s = new StringBuffer();
        s.append(this.predictionLength);
        s.append(separator);
        s.append(Arrays.toString(this.prediction));
        out.print(s.toString());
    }
}

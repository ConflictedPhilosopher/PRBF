/*
 * created on Jul 31, 2008
 */
package xcsf.classifier;

import java.io.PrintStream;
import java.util.Arrays;

import xcsf.XCSFConstants;
import xcsf.XCSFUtils;

/**
 * Implementation of a quadratic recursive least squares prediction. Mixed terms
 * are also included, thus this prediction is able to rotate and is not
 * necessarily aligned to the problem axis.
 * 
 * @author Patrick Stalph
 */
public class PredictionQuadraticRLS implements Prediction {

    // arrays for temporary storage to avoid mem alloc.
    private static double[] tmpExtendedPredInput;
    private static double[] tmpGainVector;
    private static double[][] tmpMatrix1;
    private static double[][] tmpMatrix2;

    private int inputLength; // (2 * dimension of function input) + 1 (offset)
    private int predictionLength; // dimension of function output
    private double[][] coefficients; // coefficients of linear/polynomial fit
    private double[][] gainMatrix; // some kind of magic matrix
    private double[] prediction; // array, to avoid mem-alloc

    /**
     * Default constructor with given input length and the actual function value
     * (used as initial prediction).
     * 
     * @param inputLength
     *            the length of prediction input
     * @param initialPrediction
     *            the initial prediction value
     */
    public PredictionQuadraticRLS(int inputLength, double[] initialPrediction) {
        // offset(1) + n linear + n quadratic + n*(n-1)/2 mixed terms
        this.inputLength = 1 + 2 * inputLength + inputLength
                * (inputLength - 1) / 2;
        this.predictionLength = initialPrediction.length;
        this.coefficients = new double[this.predictionLength][this.inputLength];
        this.gainMatrix = new double[this.inputLength][this.inputLength];
        this.prediction = new double[this.predictionLength];
        // init coefficients
        for (int p = 0; p < this.predictionLength; p++) {
            // first coefficient is the offset
            if (XCSFConstants.predictionOffsetValue > 0) {
                this.coefficients[p][0] = initialPrediction[p];
            } else {
                this.coefficients[p][0] = 0;
            }
            for (int i = 1; i < this.inputLength; i++) {
                this.coefficients[p][i] = 0;
            }
        }
        // init gainMatrix
        this.initializeGainMatrix();

        // create temporary arrays
        init(this.inputLength);
    }

    /**
     * Parses the given String array. For details, how this constructor is
     * called, see {@link Classifier#parse(String, String, String)}.
     * 
     * @param args
     *            the splited String
     */
    public PredictionQuadraticRLS(String[] args) {
        this();
        this.inputLength = Integer.parseInt(args[0]);
        this.predictionLength = Integer.parseInt(args[1]);
        this.prediction = XCSFUtils.FileIO.parseDoubleArray(args[2]);
        this.coefficients = XCSFUtils.FileIO.parse2dDoubleArray(args[3]);
        this.gainMatrix = XCSFUtils.FileIO.parse2dDoubleArray(args[4]);
        init(this.inputLength);
    }

    /**
     * Initializes static arrays, if necessary.
     * 
     * @param inputLength
     *            the dimensionality of this prediction
     */
    private static void init(int inputLength) {
        if (tmpGainVector == null || tmpGainVector.length != inputLength) {
            tmpGainVector = new double[inputLength];
            tmpExtendedPredInput = new double[inputLength];
            tmpMatrix1 = new double[inputLength][inputLength];
            tmpMatrix2 = new double[inputLength][inputLength];
        }
    }

    /**
     * Private empty constructor for efficient cloning and parsing.
     */
    private PredictionQuadraticRLS() {
        // empty
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#predict(double[])
     */
    public double[] predict(double[] input) {
        for (int p = 0; p < predictionLength; p++) {
            // first coefficient is offset
            prediction[p] = coefficients[p][0]
                    * XCSFConstants.predictionOffsetValue;
            int length = input.length;
            int index = 1;
            // multiply linear coefficients with the prediction input
            for (int i = 0; i < length; i++) {
                prediction[p] += coefficients[p][index++] * input[i];
            }
            // multiply quadratic coefficients with prediction input
            for (int i = 0; i < length; i++) {
                for (int j = i; j < length; j++) {
                    prediction[p] += coefficients[p][index++] * input[i]
                            * input[j];
                }
            }
        }
        return this.prediction;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#updatePrediction(double[], double[])
     */
    public void update(double[] input, double[] functionValue) {
        // extend prediction input
        tmpExtendedPredInput[0] = XCSFConstants.predictionOffsetValue;
        int length = input.length;
        int index = 1;
        // linear terms
        for (int i = 0; i < length; i++) {
            tmpExtendedPredInput[index++] = input[i];
        }
        // quadratic terms
        for (int i = 0; i < length; i++) {
            for (int j = i; j < length; j++) {
                tmpExtendedPredInput[index++] = input[i] * input[j];
            }
        }

        // 1. determine gain vector = gainMatrix * extendedPredInput
        XCSFUtils.Matrix.multiply(this.gainMatrix, tmpExtendedPredInput,
                tmpGainVector, this.inputLength);

        // 2. divide gain vector by lambda + <diffStateExtended, k>
        double divisor = XCSFConstants.lambdaRLS;
        for (int i = 0; i < this.inputLength; i++) {
            divisor += tmpExtendedPredInput[i] * tmpGainVector[i];
        }
        for (int i = 0; i < this.inputLength; i++) {
            tmpGainVector[i] /= divisor;
        }

        // 3. update coefficients using the error (functionValue - prediction)
        // Note, that "this.prediction" is up to date at the moment!
        for (int p = 0; p < this.predictionLength; p++) {
            double error = functionValue[p] - this.prediction[p];
            for (int i = 0; i < this.inputLength; i++) {
                this.coefficients[p][i] += error * tmpGainVector[i];
            }
        }

        // 4. update gainMatrix:
        // gainMatrix = (I - rank1(helpV1, diffStateExt)) * gainMatrix
        for (int i = 0; i < this.inputLength; i++) {
            for (int j = 0; j < this.inputLength; j++) {
                double tmp = tmpGainVector[i] * tmpExtendedPredInput[j];
                if (i == j) {
                    tmpMatrix1[i][j] = 1.0 - tmp;
                } else {
                    tmpMatrix1[i][j] = -tmp;
                }
            }
        }
        XCSFUtils.Matrix.multiply(tmpMatrix1, this.gainMatrix, tmpMatrix2,
                this.inputLength);
        // 5. divide gainMatrix entries by lambda
        for (int row = 0; row < this.inputLength; row++) {
            for (int col = 0; col < this.inputLength; col++) {
                this.gainMatrix[row][col] = tmpMatrix2[row][col]
                        / XCSFConstants.lambdaRLS;
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#crossover(xcsf.classifier.Prediction)
     */
    public void crossover(Prediction otherPrediction) {
        PredictionQuadraticRLS other = (PredictionQuadraticRLS) otherPrediction;
        for (int p = 0; p < this.predictionLength; p++) {
            for (int i = 0; i < this.inputLength; i++) {
                double avg = this.coefficients[p][i] + other.coefficients[p][i];
                this.coefficients[p][i] = other.coefficients[p][i] = avg / 2.0;
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#reproduce()
     */
    public PredictionQuadraticRLS reproduce() {
        PredictionQuadraticRLS clone = new PredictionQuadraticRLS();
        // cloned fields
        clone.inputLength = this.inputLength;
        clone.predictionLength = this.predictionLength;
        clone.coefficients = new double[this.predictionLength][this.inputLength];
        for (int p = 0; p < this.predictionLength; p++) {
            for (int i = 0; i < this.inputLength; i++) {
                clone.coefficients[p][i] = this.coefficients[p][i];
            }
        }
        // modified fields
        clone.prediction = new double[this.predictionLength];
        clone.gainMatrix = new double[this.inputLength][this.inputLength];
        clone.initializeGainMatrix();
        return clone;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        String s = "prediction{in=" + (this.inputLength - 1) + ",out="
                + this.predictionLength + " coef=";
        for (double[] coeff : this.coefficients) {
            s += Arrays.toString(coeff);
        }
        s += ", gain=";
        for (double[] row : this.gainMatrix) {
            s += Arrays.toString(row);
        }
        return s + "}";
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Prediction#write(java.io.PrintStream,
     * java.lang.CharSequence)
     */
    public void write(PrintStream out, CharSequence separator) {
        StringBuffer s = new StringBuffer();
        s.append(this.inputLength);
        s.append(separator);
        s.append(this.predictionLength);
        s.append(separator);
        s.append(Arrays.toString(this.prediction));
        s.append(separator);
        s.append(Arrays.deepToString(this.coefficients));
        s.append(separator);
        s.append(Arrays.deepToString(this.gainMatrix));
        out.print(s.toString());
    }

    /**
     * Resets the gain matrix.
     */
    public void resetGainMatrix() {
        for (int i = 0; i < this.inputLength; i++) {
            this.gainMatrix[i][i] += XCSFConstants.rlsInitScaleFactor;
        }
    }

    /**
     * Initializes the gainMatrix for this RLS prediction.
     */
    private void initializeGainMatrix() {
        for (int row = 0; row < this.gainMatrix.length; row++) {
            for (int col = 0; col < this.gainMatrix.length; col++) {
                this.gainMatrix[row][col] = (row != col) ? 0
                        : XCSFConstants.rlsInitScaleFactor;
            }
        }
    }
}

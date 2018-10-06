package xcsf;

import java.util.Arrays;

/**
 * Handles the state description for double valued input states. Basically a
 * collection of input and output arrays of a <code>Function</code>. This class
 * also allows for separation of condition and prediction inputs.
 * 
 * @see xcsf.Function#nextProblemInstance()
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class StateDescriptor {

    private boolean sameInput;
    private double[] conditionInput;
    private double[] predictionInput;
    private double[] output;

    /**
     * Default constructor, if the same input is used for condition and
     * prediction.
     * 
     * @param input
     *            the input for matching and prediction
     * @param output
     *            the output of the function for the given <code>input</code>
     */
    public StateDescriptor(double[] input, double[] output) {
        this(input, input, output);
        this.sameInput = true;
    }

    /**
     * Alternative constructor, if different input arrays are used for matching
     * and prediction.
     * 
     * @param conditionInput
     *            the input for classifier matching
     * @param predictionInput
     *            the input for prediction generation
     * @param output
     *            the output of the function for the given input
     */
    public StateDescriptor(double[] conditionInput, double[] predictionInput, double[] output) {
        this.conditionInput = conditionInput;
        this.predictionInput = predictionInput;
        this.output = output;
        this.sameInput = false;
    }

    /**
     * Returns the condition input, that is the function input.
     * 
     * @return the condition input
     */
    public double[] getConditionInput() {
        return this.conditionInput;
    }

    /**
     * Returns the prediction input, if the function specifies different
     * condition and prediction input. Otherwise the condition input is
     * returned.
     * 
     * @return the prediction input.
     */
    public double[] getPredictionInput() {
        return this.predictionInput;
    }

    /**
     * Returns the function output, that is <tt>f(input)</tt>.
     * 
     * @return the function value
     */
    public double[] getOutput() {
        return this.output;
    }

    /**
     * Returns <code>true</code> if the function input is used for matching and
     * prediction. Returns <code>false</code> if there are different inputs for
     * matching and prediction.
     * 
     * @return <code>true</code> if the condition input is also used for
     *         prediction; <code>false</code> otherwise.
     */
    public boolean isSameInput() {
        return this.sameInput;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        if (this.sameInput) {
            return "input=" + Arrays.toString(this.conditionInput)
                    + ", output=" + Arrays.toString(this.output);
        } else {
            return "conInput=" + Arrays.toString(this.conditionInput)
                    + ", preInput=" + Arrays.toString(this.predictionInput)
                    + ", output=" + Arrays.toString(this.output);
        }
    }
}

package xcsf.examples;

import xcsf.XCSFUtils;
import xcsf.Function.SimpleFunction;

/**
 * Implementation of an non-uniformly sampled linear function.
 * 
 * @author Patrick Stalph
 */
public class LinearImbalanced extends SimpleFunction {

    /**
     * Default constructor.
     * 
     * @param scale
     *            the scaling factor
     * @param modifier
     *            the function modifier
     * @param noiseDeviation
     *            the (gaussian) noise deviation
     * @param dim
     *            the input dimension of the function
     */
    public LinearImbalanced(double scale, double modifier,
            double noiseDeviation, int dim) {
        super(scale, modifier, noiseDeviation, dim);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.Function.SimpleFunction#generateInput()
     */
    protected void generateInput() {
        for (int i = 0; i < super.dim; i++) {
            super.input[i] = Math.pow(XCSFUtils.Random.uniRand(),
                    super.modifier);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.Function.SimpleFunction#evaluate()
     */
    protected double evaluate() {
        double sum = 0.0;
        for (int i = 0; i < super.dim; i++) {
            sum += input[i];
        }
        return sum / super.dim;
    }
}

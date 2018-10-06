package xcsf.examples;

import xcsf.Function.SimpleFunction;

/**
 * Implementation of a multi-dimensional linear function, where the gradient is
 * the same for each dimension.
 * 
 * @author Patrick Stalph
 */
public class Linear extends SimpleFunction {

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
    public Linear(double scale, double modifier, double noiseDeviation, int dim) {
        super(scale, modifier, noiseDeviation, dim);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.Function.SimpleFunction#evaluate()
     */
    protected double evaluate() {
        double sum = 0.0;
        for (int i = 0; i < super.dim; i++) {
            sum += super.input[i];
        }
        return sum * super.modifier;
    }
}

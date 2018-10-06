package xcsf.examples;

import xcsf.Function.SimpleFunction;

/**
 * Implements a radial, Gaussian-like function.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class Radial extends SimpleFunction {

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
    public Radial(double scale, double modifier, double noiseDeviation, int dim) {
        super(scale, modifier, noiseDeviation, dim);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.Function.SimpleFunction#evaluate()
     */
    protected double evaluate() {
        // exp(-1 * sum_i (x_i-0.5)^2 * mod)
        double sum = 0.0;
        for (double v : input) {
            sum += (v - 0.5) * (v - 0.5);
        }
        return Math.exp(-1. * sum * super.modifier);
    }

}

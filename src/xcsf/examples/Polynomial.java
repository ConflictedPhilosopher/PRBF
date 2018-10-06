package xcsf.examples;

import xcsf.Function.SimpleFunction;

/**
 * Implementation of a multi-dimensional quadratic function.
 * 
 * @author Patrick Stalph
 */
public class Polynomial extends SimpleFunction {

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
    public Polynomial(double scale, double modifier, double noiseDeviation,
            int dim) {
        super(scale, modifier, noiseDeviation, dim);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.Function.SimpleFunction#evaluate()
     */
    protected double evaluate() {
        double output = 0;
        for (double d : super.input) {
            output += d * d;
        }
        return output * modifier / dim;
    }
}

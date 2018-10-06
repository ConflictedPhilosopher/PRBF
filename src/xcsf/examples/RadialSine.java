package xcsf.examples;

import xcsf.Function.SimpleFunction;

/**
 * Implements a radial, wave function.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class RadialSine extends SimpleFunction {

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
    public RadialSine(double scale, double modifier, double noiseDeviation,
            int dim) {
        super(scale, modifier, noiseDeviation, dim);
    }

    /*
     * (non-Javadoc)
     * 
     * @see function.Function#evaluate(double[])
     */
    protected double evaluate() {
        double sum = 0.0;
        for (double v : super.input) {
            sum += (v - .5) * (v - .5);
        }
        return Math.exp(-16. * sum / super.dim)
                * Math.cos(2.0 * Math.PI * sum * super.modifier);
    }

}

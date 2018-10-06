package xcsf.examples;

import xcsf.XCSFUtils;
import xcsf.Function.SimpleFunction;

/**
 * Implements Schaal&Atkeson's (1998) ridge function.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class CrossedRidge extends SimpleFunction {

    /**
     * Default constructor.
     * 
     * @param scale
     *            the scaling factor
     * @param noiseDeviation
     *            the (gaussian) noise deviation
     * @param dim
     *            Specifies the input dimension <tt>>= 2</tt> of the function.
     *            The dimension itself is two-dimensional, other dimensions just
     *            contain gaussian noise.
     */
    public CrossedRidge(double scale, double noiseDeviation, int dim) {
        super(scale, 0, noiseDeviation, dim);
        if (dim < 2)
            throw new IllegalArgumentException();
    }

    /*
     * (non-Javadoc)
     * 
     * @see function.Function#evaluate()
     */
    protected double evaluate() {
        // dimension 1,2
        double valX = super.input[0] * 2. - 1.;
        double valY = super.input[1] * 2. - 1.;
        double sq1 = valX * valX;
        double sq2 = valY * valY;
        // maximum of 3 functions
        double f1 = Math.exp(-10. * sq1);
        double f2 = Math.exp(-50. * sq2);
        double f3 = 1.25 * Math.exp(-5. * (sq1 + sq2));
        double value = Math.max(f1, Math.max(f2, f3));

        // dimension 3-10: constant, redundant => do nothing
        // dimension 11-20: noise N(0, 0.05²) gaussian
        for (int d = 10; d < super.dim; d++) {
            value += XCSFUtils.Random.normRand() * 0.0025;
        }
        return value;
    }
}

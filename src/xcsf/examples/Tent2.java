package xcsf.examples;

import xcsf.Function.SimpleFunction;

/**
 * Implements a function with several linear subspaces that form spikes.
 * 
 * @author Patrick Stalph
 */
public class Tent2 extends SimpleFunction {

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
    public Tent2(double scale, double modifier, double noiseDeviation, int dim) {
        super(scale, modifier, noiseDeviation, dim);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.Function.SimpleFunction#evaluate()
     */
    protected double evaluate() {
        double maxDist = -1;
        for (int i = 0; i < super.dim; i++) {
            double dist = Math.abs((super.input[i] * super.modifier) % 1 - 0.5);
            if (dist > maxDist) {
                maxDist = dist;
            }
        }
        // gradient 1, range [0:0.5 / modifier]
        return (0.5 - maxDist) * 2 / super.modifier;
    }
}

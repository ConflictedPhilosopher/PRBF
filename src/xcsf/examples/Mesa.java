package xcsf.examples;

import xcsf.Function.SimpleFunction;

/**
 * Implementation of a non-continous function, where a sine-like gap separates
 * the two classes (0 and 1).
 * 
 * @author Patrick Stalph
 */
public class Mesa extends SimpleFunction {

    /**
     * Default constructor.
     * 
     * @param scale
     *            the scaling factor
     * @param modifier
     *            the function modifier
     * @param noiseDeviation
     *            the (gaussian) noise deviation
     */
    public Mesa(double scale, double modifier, double noiseDeviation) {
        super(scale, modifier, noiseDeviation, 2);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.Function.SimpleFunction#evaluate()
     */
    protected double evaluate() {
        double v = super.modifier / 10.0
                * Math.sin(2 * Math.PI * super.input[0]) + 0.5;
        if (super.input[1] < v) {
            return 0;
        } else {
            return 1;
        }
    }
}

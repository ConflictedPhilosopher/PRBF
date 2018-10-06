package xcsf.examples;

import xcsf.Function.SimpleFunction;

/**
 * Implements a sine-in-sine function.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class SineInSine extends SimpleFunction {

    /**
     * Default constructor.
     * 
     * @param scale
     * @param modifier
     * @param noiseDeviation
     */
    public SineInSine(double scale, double modifier, double noiseDeviation) {
        super(scale, modifier, noiseDeviation, 2);
    }

    /*
     * (non-Javadoc)
     * 
     * @see function.Function#evaluate()
     */
    protected double evaluate() {
        // f(x,y) = sin(mod * pi * (x + sin(pi * y)))
        return Math.sin(super.modifier * Math.PI
                * (super.input[0] + Math.sin(Math.PI * super.input[1])));
    }
}

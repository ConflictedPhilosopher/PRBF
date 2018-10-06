package xcsf.classifier;

import java.io.PrintStream;

/**
 * This interface is used to determine function predictions, that is the
 * value(s) a classifier predicts given a specific input. Consequently
 * implementations provide a function that maps function inputs to predicted
 * output values. The prediction may be updated, given the actual function value
 * (environmental feedback).
 * <p>
 * Implementations must provide a constructor with the signature as specified by
 * {@link #CONSTRUCTOR_SIGNATURE} in order to allow for loading of arbitrary
 * implementations using the <code>ClassLoader</code>. Furthermore,
 * implementations should provide a constructor with the signature specified by
 * {@link #CONSTRUCTOR_PARSER_SIGNATURE}, because this allows for parsing of
 * arbitrary implementations.
 * 
 * @author Patrick Stalph
 */
public interface Prediction {

    /**
     * Implementations must provide one public constructor with this signature
     * in order to allow for loading of arbitrary implementations using a
     * <code>ClassLoader</code>.
     */
    public final static Class<?>[] CONSTRUCTOR_SIGNATURE = { int.class,
            double[].class };

    /**
     * Implementations must provide one public constructor with this signature
     * in order to allow for loading of arbitrary implementations using a
     * <code>ClassLoader</code>.
     */
    public final static Class<?>[] CONSTRUCTOR_PARSER_SIGNATURE = { String[].class };

    /**
     * Generates the prediction using the given input. If condition and
     * prediction inputs are not separated, the <code>input</code> is the
     * condition centered input, that is
     * 
     * <pre>
     * input[i] = functionInput[i] - condition.center[i];
     * </pre>
     * 
     * In contrast, for separated condition and prediction inputs the prediction
     * input is directly fed into this method.
     * 
     * @param input
     *            the prediction input
     * @return the predicted output at the given input
     */
    public double[] predict(double[] input);

    /**
     * Updates this prediction using the given prediction input and the actual
     * function value.
     * 
     * @param input
     *            the prediction input
     * @param functionValue
     *            the actual function value at the given input
     */
    public void update(double[] input, double[] functionValue);

    /**
     * Mixes the genotype of this prediction with the <code>other</code>
     * prediction. Usually this is done by averaging the two predictions.
     * 
     * @param other
     *            the prediction to cross with
     */
    public void crossover(Prediction other);

    /**
     * Creates a copy of this prediction for reproduction purpose.
     * 
     * @return a copy of this prediction
     */
    public Prediction reproduce();

    /**
     * Writes this prediction to the given <code>PrintStream</code> using the
     * specified separator. In order to allow for parsing of the written string,
     * implementations should specify a constructor with the signature specified
     * by {@link #CONSTRUCTOR_PARSER_SIGNATURE}. The constructor parses the
     * output of this method and reconstructs this object.
     * 
     * @param ps
     *            The printstream to write to
     * @param separator
     *            The separator to use
     */
    public void write(PrintStream ps, CharSequence separator);
}

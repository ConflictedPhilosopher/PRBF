package xcsf.classifier;

import java.io.PrintStream;

import xcsf.XCSFConstants;

/**
 * This interface is used to determine if classifiers match. Therefore,
 * implementations may specify a geometric shape, but arbitrary conditions are
 * possible, too.
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
public interface Condition {

    /**
     * Implementations must provide one public constructor with this signature
     * in order to allow for loading of arbitrary implementations using a
     * <code>ClassLoader</code>.
     */
    public final static Class<?>[] CONSTRUCTOR_SIGNATURE = { double[].class };

    /**
     * Implementations must provide one public constructor with this signature
     * in order to allow for parsing of arbitrary implementations using a
     * <code>ClassLoader</code>.
     */
    public final static Class<?>[] CONSTRUCTOR_PARSER_SIGNATURE = { String[].class };

    /** lower input space boundary */
    public final static double LOWER_BOUND = 0.0;

    /** upper input space boundary */
    public final static double UPPER_BOUND = 1.0;

    /** lower human readable angle */
    public final static double LOWER_ROTATION_BOUND = 0;

    /** upper human readable angle */
    public final static double UPPER_ROTATION_BOUND = 2 * Math.PI;

    /**
     * Returns <code>true</code>, if this condition matches the given
     * <code>input</code>, that is if the given point satisfies this condition.
     * Implementations should make use of {@link #getActivity(double[])} to
     * determine matching.
     * 
     * @param input
     *            the input to match
     * @return <code>true</code> if this condition matches the given
     *         <code>input</code>; <code>false</code> otherwise.
     */
    public boolean doesMatch(double[] input);

    /**
     * Calculates the activity of this condition concerning the given
     * <code>input</code>. If the input equals the center of this condition, the
     * activity is 1 (maximum). The higher the distance to this condition, the
     * lower the activity.
     * 
     * @param input
     *            the input for this condition
     * @return the activity of this condition for the given <code>input</code>
     */
    public double getActivity(double[] input);

    /**
     * Compares <code>this</code> condition with the <code>other</code>
     * condition regarding generality. If this condition covers all inputs that
     * the other condition covers, this condition is said to be more general.
     * 
     * @param other
     *            the possibly less general condition
     * @return <code>true</code>, if this condition is more general than the
     *         other condition; <code>false</code> otherwise.
     */
    public boolean isMoreGeneral(Condition other);

    /**
     * Determines the volume of this condition, which is used to measure
     * generality.
     * 
     * @return the volume of the condition
     */
    public double getVolume();

    /**
     * Returns the center of this condition.
     * 
     * @return the center of this condition
     */
    public double[] getCenter();

    /**
     * Checks if this condition equals the <code>other</code> condition.
     * 
     * @param other
     *            the other condition
     * @return <code>true</code> if this condition equals the other condition;
     *         <code>false</code> otherwise.
     */
    public boolean equals(Condition other);

    /**
     * Crossover routine for this type of condition. The probability for
     * crossover equals {@link XCSFConstants#pX}. However, this method is called
     * everytime, thus implementations must take care of the probability.
     * 
     * @param other
     *            the condition to cross with
     */
    public void crossover(Condition other);

    /**
     * Mutation routine for this type of condition. The probability for mutation
     * of one allele equals {@link XCSFConstants#pM} divided by the number of
     * alleles.
     */
    public void mutation();

    /**
     * Creates a copy of this condition.
     * 
     * @return a copy of this condition for reproduction purpose
     */
    public Condition reproduce();

    /**
     * Writes this condition to the given <code>PrintStream</code> using the
     * specified separator. In order to allow for parsing of the written string,
     * implementations should specify a constructor with the signature specified
     * by {@link #CONSTRUCTOR_PARSER_SIGNATURE}. The constructor parses the
     * output of this method and reconstructs this object.
     * 
     * @param ps
     *            the printstream to write to
     * @param separator
     *            the sequence to use for separation of values
     */
    public void write(PrintStream ps, CharSequence separator);
}

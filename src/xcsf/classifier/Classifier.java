package xcsf.classifier;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import xcsf.MatchSet;
import xcsf.StateDescriptor;
import xcsf.XCSFConstants;
import xcsf.XCSFUtils;

/**
 * This class encapsulates methods for matching, prediction and classifier
 * updates. A classifier contains a {@link Condition} and a {@link Prediction}.
 * Several implementations for the condition and prediction interfaces are
 * available.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class Classifier implements Cloneable {

    // constructors for instantiation of arbitrary implementations
    private static Constructor<?> conditionCoverer;
    private static Constructor<?> predictionCoverer;
    private static Constructor<?> conditionParser;
    private static Constructor<?> predictionParser;

    // temporary
    private static double[] tmpCenterDifference;

    // classifier fields
    private Condition condition;
    private Prediction prediction;
    private double fitness;
    private int numerosity;
    private int experience;
    private double setSizeEstimate;
    private double predictionError;
    private int timestamp;

    /**
     * Default constructor used for covering. See the {@link MatchSet} class for
     * details on the covering mechanism.
     * 
     * @param state
     *            the state to cover
     * @param timestamp
     *            the timestamp of this new classifier
     */
    public Classifier(StateDescriptor state, int timestamp) {
        this.fitness = XCSFConstants.fitnessIni;
        this.numerosity = 1;
        this.experience = 0;
        this.setSizeEstimate = 1;
        this.predictionError = XCSFConstants.predictionErrorIni;
        this.timestamp = timestamp;

        // static initializer
        init(state.getConditionInput().length);

        // first call: load constructors for condition/prediction
        if (conditionCoverer == null) {
            conditionCoverer = XCSFUtils.loadConstructor(
                    XCSFConstants.conditionType, Condition.class,
                    Condition.CONSTRUCTOR_SIGNATURE);
            predictionCoverer = XCSFUtils.loadConstructor(
                    XCSFConstants.predictionType, Prediction.class,
                    Prediction.CONSTRUCTOR_SIGNATURE);
            if (conditionCoverer == null || predictionCoverer == null) {
                // critical error :-( exit.
                System.err.println("Failed to instantiate constructors "
                        + "for condition and/or prediction.");
                System.exit(0);
            }
        }

        // create instances of condition/prediction
        try {
            // constructor calls according to specified signatures
            this.condition = (Condition) conditionCoverer.newInstance(state
                    .getConditionInput());
            this.prediction = (Prediction) predictionCoverer.newInstance(state
                    .getPredictionInput().length, state.getOutput());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();// cannot happen, signature checked.
        } catch (InstantiationException e) {
            e.printStackTrace();// ? should not happen
        } catch (IllegalAccessException e) {
            e.printStackTrace();// ? should not happen
        } catch (InvocationTargetException e) {
            e.printStackTrace();// constructor has thrown exception
        }
    }

    /**
     * Empty constructor for efficient cloning and parsing.
     */
    private Classifier() {
        // empty
    }

    /**
     * Initializes static arrays, if necessary.
     * 
     * @param conditionInputLength
     *            the dimension of the condition input
     */
    private static void init(int conditionInputLength) {
        // assure correct array length
        if (tmpCenterDifference == null) {
            tmpCenterDifference = new double[conditionInputLength];
        } else if (tmpCenterDifference.length != conditionInputLength) {
            tmpCenterDifference = new double[conditionInputLength];
        }
    }

    /**
     * Computes the activity of the <code>Condition</code> for the given
     * <tt>state</tt>.
     * 
     * @param state
     *            the state to match
     * @return the activity for the <tt>state</tt>
     */
    public double getActivity(StateDescriptor state) {
        return this.condition.getActivity(state.getConditionInput());
    }

    /**
     * Checks if the <code>Condition</code> matches the given <tt>state</tt>.
     * 
     * @param state
     *            the state to match
     * @return <code>true</code>, if the condition matches; <code>false</code>
     *         otherwise.
     */
    public boolean doesMatch(StateDescriptor state) {
        return this.condition.doesMatch(state.getConditionInput());
    }

    /**
     * Generates the prediction for the given <code>state</code>.
     * 
     * @param state
     *            the state to use for prediction.
     * @return the prediction for the given <code>state</code>.
     */
    public double[] predict(StateDescriptor state) {
        if (state.isSameInput()) {
            double[] input = state.getConditionInput();
            double[] center = this.condition.getCenter();
            for (int i = 0; i < input.length; i++) {
                tmpCenterDifference[i] = input[i] - center[i];
            }
            return this.prediction.predict(tmpCenterDifference);
        } else {
            return this.prediction.predict(state.getPredictionInput());
        }
    }

    /**
     * Crossover routine mixes the genotype (condition and prediction) of this
     * classifier and the given <code>other</code> classifier.
     * 
     * @param other
     *            the classifier to cross with
     */
    public void crossover(Classifier other) {
        // fitness & predictionError
        double avgPredictionError = (this.predictionError + other.predictionError) / 2.0;
        this.predictionError = other.predictionError = avgPredictionError;
        double avgfitness = (this.fitness + other.fitness) / 2.0;
        this.fitness = other.fitness = avgfitness;

        // predictions & conditions
        this.prediction.crossover(other.prediction);
        this.condition.crossover(other.condition);
    }

    /**
     * Modifies the the genotype of the <code>condition</code> of this
     * classifier.
     */
    public void mutation() {
        this.condition.mutation();
    }

    /**
     * Updates the experience, prediction and predictionError of this
     * classifier. The two update methods are seperated for performance reasons.
     * 
     * @param state
     *            the state, which this classifier currently matches
     */
    public void update1(StateDescriptor state) {
        // ---[ experience ]---
        this.experience++;
        // ---[ prediction ]---
        if (state.isSameInput()) {
            double[] input = state.getConditionInput();
            double[] center = this.condition.getCenter();
            for (int i = 0; i < input.length; i++) {
                tmpCenterDifference[i] = input[i] - center[i];
            }
            this.prediction.update(tmpCenterDifference, state.getOutput());
        } else {
            this.prediction.update(state.getPredictionInput(), state
                    .getOutput());
        }

        // ---[ predictionError ]---
        // calculate absolute error
        double absError = 0;
        double[] currentPrediction = this.predict(state);
        double[] actualValue = state.getOutput();
        for (int i = 0; i < currentPrediction.length; i++) {
            absError += Math.abs(currentPrediction[i] - actualValue[i]);
        }
        // update using multiplier: max{1/exp, beta}
        double max = 1.0 / experience;
        if (XCSFConstants.beta > max) {
            max = XCSFConstants.beta;
        }
        predictionError += max * (absError - predictionError);
    }

    /**
     * Updates the setSizeEstimate and the fitness. The update methods are
     * seperated for performance reasons.
     * <p>
     * Precondition: {@link #update1(StateDescriptor)} was called before.
     * 
     * @param accuracySum
     *            the sum of accuracies in the <code>MatchSet</code> this
     *            classifier is in
     * @param numerositySum
     *            the sum of numerosities in the <code>MatchSet</code> this
     *            classifier is in
     */
    public void update2(double accuracySum, int numerositySum) {
        // ---[ setSizeEstimate ]---
        // update using widrow hoff rule: max{1/exp, beta}
        double learningRate = 1.0 / experience;
        if (XCSFConstants.beta > learningRate) {
            learningRate = XCSFConstants.beta;
        }
        this.setSizeEstimate += learningRate
                * (numerositySum - this.setSizeEstimate);
        // ---[ fitness ]---
        this.fitness += XCSFConstants.beta
                * (((this.getAccuracy() * numerosity) / accuracySum) - fitness);
    }

    /**
     * Creates a copy of this classifier for reproduction. Note that the
     * numerosity is reduced to one and the fitness is proportinal to the
     * previous numerosity, thus, this method does not return a clone.
     * 
     * @return a copy of this classifier for reproduction
     */
    public Classifier reproduce() {
        Classifier clone = new Classifier();
        // cloned fields
        clone.condition = this.condition.reproduce();
        clone.prediction = this.prediction.reproduce();
        clone.setSizeEstimate = this.setSizeEstimate;
        clone.predictionError = this.predictionError
                * XCSFConstants.predictionErrorReduction;
        clone.timestamp = this.timestamp;
        // modified fields
        clone.fitness = this.fitness / this.numerosity
                * XCSFConstants.fitnessReduction;
        clone.numerosity = 1;
        clone.experience = 0;
        return clone;
    }

    /**
     * Returns the "vote" (not the probability) for deletion of this classifier.
     * The higher the returned value, the more likely is the deletion of this
     * classifier.
     * 
     * @param meanFitness
     *            the mean fitness in the population
     * @return the vote for deletion of this classifier
     */
    public double getDeletionVote(double meanFitness) {
        double microfitness = fitness / numerosity;
        if (microfitness >= XCSFConstants.delta * meanFitness
                || experience < XCSFConstants.theta_del) {
            // low vote, if microfitness is high or cl is inexperienced
            return setSizeEstimate * numerosity;
        } else {
            // high vote, if microfitness is low
            return setSizeEstimate * numerosity * meanFitness / microfitness;
        }
    }

    /**
     * Determines the current accuracy of the classifier based on its current
     * prediction error estimate.
     * 
     * @return the current classifier accuracy
     */
    public double getAccuracy() {
        if (predictionError <= XCSFConstants.epsilon_0) {
            return 1.0;
        } else {
            return XCSFConstants.alpha
                    * Math.pow(XCSFConstants.epsilon_0 / predictionError,
                            XCSFConstants.nu);
        }
    }

    /**
     * Returns <code>true</code>, if this classifier is experienced and accurate
     * enough to subsume other classifiers.
     * 
     * @return <code>true</code>, if this classifier can subsume others;
     *         <code>false</code> otherwise.
     */
    public boolean canSubsume() {
        return this.experience > XCSFConstants.theta_sub
                && this.predictionError < XCSFConstants.epsilon_0;
    }

    /**
     * Determines if this classifier is more general than the other. For
     * <code>Condition</code> implementations that define a geometric shape,
     * this method indicates if this classifier "contains" the other classifier.
     * 
     * @param other
     *            the possibly less general classifier
     * @return <code>true</code>, if this classifier is more general than the
     *         other; <code>false</code> otherwise.
     */
    public boolean isMoreGeneral(Classifier other) {
        return this.condition.isMoreGeneral(other.condition);
    }

    /**
     * Returns the condition of this classifier.
     * 
     * @return the condition
     */
    public Condition getCondition() {
        return this.condition;
    }

    /**
     * Returns the prediction of this classifier.
     * 
     * @return the prediction
     */
    public Prediction getPrediction() {
        return this.prediction;
    }

    /**
     * Returns the fitness of this classifier.
     * 
     * @return the fitness
     */
    public double getFitness() {
        return this.fitness;
    }

    /**
     * Returns the prediction error of this classifier.
     * 
     * @return the prediction error
     */
    public double getPredictionError() {
        return this.predictionError;
    }

    /**
     * Determines the generality of this classifiers condition. For
     * <code>Condition</code> implementations that define a geometric shape,
     * this method returns the volume of the condition.
     * 
     * @return the generality of the classifiers condition
     */
    public double getGenerality() {
        return this.condition.getVolume();
    }

    /**
     * Returns the numerosity of this classifier.
     * 
     * @return the numerosity
     */
    public int getNumerosity() {
        return this.numerosity;
    }

    /**
     * Adds <code>val</code> to the numerosity of this classifier.
     * 
     * @param val
     *            the value to add
     */
    public void addNumerosity(int val) {
        this.numerosity += val;
    }

    /**
     * Returns the experience of this classifier.
     * 
     * @return the experience
     */
    public int getExperience() {
        return this.experience;
    }

    /**
     * Returns the estimated average matchset size of this classifier.
     * 
     * @return the estimated average matchset size
     */
    public double getSetSizeEstimate() {
        return this.setSizeEstimate;
    }

    /**
     * Returns the timestamp of this classifier.
     * 
     * @return the timestamp
     */
    public int getTimestamp() {
        return this.timestamp;
    }

    /**
     * Sets the timestamp of this classifier.
     * 
     * @param timestamp
     *            the timestamp to set
     */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "cl{fit="
                + this.fitness //
                + ",num="
                + this.numerosity //
                + ",exp="
                + this.experience //
                + ",setSizeEst="
                + this.setSizeEstimate//
                + " " + this.condition.toString() + " "
                + this.prediction.toString() + "}";
    }

    /**
     * Writes this classifier to the given stream. Note that this method does
     * not use serialization, but writes plain text. In order to allow for
     * parsing of this classifier, underlying {@link Condition} and
     * {@link Prediction} implementations must provide a constructor with the
     * signature according to {@link Condition#CONSTRUCTOR_PARSER_SIGNATURE} and
     * {@link Prediction#CONSTRUCTOR_PARSER_SIGNATURE}.
     * 
     * @param ps
     *            the printstream to write to
     * @param separator1
     *            the separator for classifier items
     * @param separator2
     *            the separator for condition and prediction items
     */
    public void write(PrintStream ps, CharSequence separator1,
            CharSequence separator2) {
        StringBuffer s = new StringBuffer();
        s.append(this.fitness);
        s.append(separator1);
        s.append(this.numerosity);
        s.append(separator1);
        s.append(this.experience);
        s.append(separator1);
        s.append(this.setSizeEstimate);
        s.append(separator1);
        s.append(this.timestamp);
        s.append(separator1);
        s.append(this.predictionError);
        ps.print(s.toString());
        // condition
        ps.print(separator1);
        ps.print(this.condition.getClass().getName() + separator1);
        this.condition.write(ps, separator2);
        // prediction
        ps.print(separator1);
        ps.print(this.prediction.getClass().getName() + separator1);
        this.prediction.write(ps, separator2);
    }

    /**
     * Parses the given String that represents a <code>Classifier</code>.
     * 
     * @param s
     *            The classifier String as produced by
     *            {@link Classifier#write(PrintStream, CharSequence, CharSequence)}
     * @param splitRegex1
     *            the regex pattern to split classifier items
     * @param splitRegex2
     *            the regex pattern to split condition and prediction items
     * @return The parsed classifier object.
     * @throws InvocationTargetException
     *             if the constructor call (condition or prediction) class
     *             resulted in an exception.
     * @throws IllegalAccessException
     *             if the constructor (condition or prediction) enforces Java
     *             language access control and the underlying constructor is
     *             inaccessible.
     * @throws InstantiationException
     *             if the class that declares the underlying constructor
     *             (condition or prediction) represents an abstract class.
     * @throws IllegalArgumentException
     *             if the number of actual and formal parameters of the
     *             constructor call (condition or prediction) differ; if an
     *             unwrapping conversion for primitive arguments fails; or if,
     *             after possible unwrapping, a parameter value cannot be
     *             converted to the corresponding formal parameter type by a
     *             method invocation conversion; if the constructor (condition
     *             or prediction) pertains to an enum type.
     */
    public static Classifier parse(String s, String splitRegex1,
            String splitRegex2) throws IllegalArgumentException,
            InstantiationException, IllegalAccessException,
            InvocationTargetException {
        String[] splited = s.split(splitRegex1);
        Classifier cl = new Classifier();
        int i = 0;
        cl.fitness = Double.parseDouble(splited[i++]);
        cl.numerosity = Integer.parseInt(splited[i++]);
        cl.experience = Integer.parseInt(splited[i++]);
        cl.setSizeEstimate = Double.parseDouble(splited[i++]);
        cl.timestamp = Integer.parseInt(splited[i++]);
        cl.predictionError = Double.parseDouble(splited[i++]);

        // condition
        String conditionClass = splited[i++];
        if (conditionParser == null
                || !conditionClass.equals(conditionParser.getDeclaringClass()
                        .getName())) {
            conditionParser = XCSFUtils.loadConstructor(conditionClass,
                    Condition.class, Condition.CONSTRUCTOR_PARSER_SIGNATURE);
        }
        Object[] args = { splited[i++].split(splitRegex2) };
        cl.condition = (Condition) conditionParser.newInstance(args);

        // prediction
        String predictionClass = splited[i++];
        if (predictionParser == null
                || !predictionClass.equals(predictionParser.getDeclaringClass()
                        .getName())) {
            predictionParser = XCSFUtils.loadConstructor(predictionClass,
                    Prediction.class, Prediction.CONSTRUCTOR_PARSER_SIGNATURE);
        }
        args[0] = splited[i++].split(splitRegex2);
        cl.prediction = (Prediction) predictionParser.newInstance(args);

        // static initializers
        init(cl.condition.getCenter().length);
        return cl;
    }
}

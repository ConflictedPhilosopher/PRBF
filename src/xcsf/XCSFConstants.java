package xcsf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Properties;

import xcsf.classifier.ConditionRotatingEllipsoid;
import xcsf.classifier.PredictionLinearRLS;

/**
 * Makes available all kinds of XCSF constants (static). The values can be
 * loaded from a {@link Properties} file using the {@link #load(String)} method.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class XCSFConstants {

    /*
     * TODO Remove static access in order to allow for multiple XCSF instances
     * with different settings.
     */

    // ---[ Datasets ]-------------------------------------------------------
	/**
     * Specifies the name/path of the text file containing the training data.
     */
	public static String TrainFilename = "train.txt";
	public static String TestFilename = "test.txt";
	public static int Inputsize = 2;
	public static int Outputsize = 3;
	
	// ---[ Experiments ]----------------------------------------------------
    /**
     * Specifies the number of investigated experiments, when calling
     * {@link XCSF#runExperiments()}. This value is ignored, when using
     * {@link XCSF#runSingleExperiment}.
     */
    public static int numberOfExperiments = 1;
    /**
     * The number of test instances that should be averaged in the performance
     * evaluation.
     */
    public static int averageExploitTrials = 500;
    /**
     * The initialization of the pseudo random generator. Must be at least one
     * and smaller than 2147483647. Will be used only if "doRandomize" is set to
     * false.
     */
    
    public void setAvgExploitTrials(int max) {
    	this.averageExploitTrials = max;
    }
    public static long initialSeed = 101;
    /**
     * Specifies if the seed should be randomized (based on the current
     * milliseconds of the computer time).
     */
    public static boolean doRandomize = false;
    /**
     * Indicates the use of multi-threaded matching to speed up the learning
     * time. Note that multi-threaded experiments are not reproducable due to
     * concurrency.
     * 
     * @see MatchSet
     */
    public static boolean multiThreading = true;
    /**
     * If the actual population size is below this threshold, serial single-core
     * matching is applied. If the size is above the threshold, multi-threading
     * is activated. The value can also be set to 'auto' (values less than
     * zero), which indicates that XCSF automatically adapts the threshold
     * depending on periodical system time measurements. The latter assures that
     * XCSF does not waste time with multi-threading, where not appropriate, but
     * starts multi-threading, when there is a speedup. Default: auto (-1)
     */
    public static int threadingThreshold = -1;
    /**
     * This flag indicates verbose mode, i.e. XCSF writes informative messages
     * about current error and other things to <tt>System.out</tt>.
     */
    public static boolean verbose = true;

    // ---[ XCSF settings ]--------------------------------------------------
    /**
     * The number of learning iterations in one experiment.
     */
    public static int maxLearningIterations = 100000;
    /**
     * Specifies the maximal number of micro-classifiers in the population.
     */
    public static int maxPopSize = 6400;
    /**
     * The error threshold specifies the desired prediction error that XCSF
     * tries to reach. Classifiers with a prediction error below this value are
     * considered "accurate".
     */
    public static double epsilon_0 = 0.01;
    /**
     * Specifies, which condition type is used, e.g. rotating hyperellipoids.
     * Default: {@link ConditionRotatingEllipsoid}
     */
    public static String conditionType = ConditionRotatingEllipsoid.class
            .getName();
    /**
     * Specifies the prediction type, for example linear recursive least
     * squares. Default: {@link PredictionLinearRLS}
     */
    public static String predictionType = PredictionLinearRLS.class.getName();
    /**
     * Specifies the minimum size of a condition part. Default: 0.01
     */
    public static double minConditionStretch = 0.01;
    /**
     * Specifies the variation in size of randomly created conditions, ofter
     * referred to as r0. This value should be set according to the
     * dimensionality of the problem, such that a randomly initialized
     * population covers the whole input space with high probability. For
     * standard condition implementations, the search space has a volume of one.
     */
    public static double coverConditionRange = 0.99;
    /**
     * The learning rate for updating fitness, prediction error, and action set
     * size estimate in XCS's classifiers. Default: 0.1
     */
    public static double beta = 0.1;
    /**
     * The fraction of the mean fitness of the population below which the
     * fitness of a classifier may be considered in its vote for deletion.
     * Default: 0.1
     */
    public static double delta = 0.1;
    /**
     * The accuracy factor (decrease) in inaccurate classifiers. Default: 1
     */
    public static double alpha = 1;

    // ---[ Compaction and Matching ]----------------------------------------
    /**
     * The percentage of learning steps after which compaction starts, that is
     * compaction starts at iteration
     * 
     * <pre>
     * startCompaction * maxLearningIterations
     * </pre>
     */
    public static double startCompaction = 0.9;
    /**
     * The compaction type. Default: 1
     * <ul>
     * <li>0 = condensation, normal matching
     * <li>1 = condensation, closest classifier matching
     * <li>2 = greedy compaction plus condensation, normal matching
     * <li>3 = greedy compaction plus condensation and closest classifier
     * matching.
     * </ul>
     */
    public static int compactionType = 1;
    /**
     * Specifies if the num closest classifiers should be considered in the
     * match set. Otherwise, normal threshold matching applies Default: false
     */
    public static boolean doNumClosestMatch = false;
    /**
     * The number of closest classifiers that will be included in the match set
     * if doNumCloestMatch is set to true; Default: 20
     */
    public static int numClosestMatch = 20;

    // ---[ Evolution Parameters ]-------------------------------------------
    /**
     * The threshold for the GA application. Default: 50.
     */
    public static int theta_GA = 50;
    /**
     * Choice of selection type 0 = proportionate selection; ]0,1] = tournament
     * selection (set-size proportional) Default: .4 (tournament selection)
     */
    public static double selectionType = 0.4;
    /**
     * The relative probability of mutation. For each allele, the probability of
     * mutation is <tt>mu=pM/n</tt>, where <tt>n</tt> is the number of alleles
     * in the condition. Set to one, the probability for one allele is
     * <tt>1/n</tt> and, thus, on average one allele is modified. Default: 1.0
     */
    public static double pM = 1.0;
    /**
     * The probability to apply crossover to the offspring, often termed chi.
     * Default: 1
     */
    public static double pX = 1.0;
    /**
     * Specified the threshold over which the fitness of a classifier may be
     * considered in its deletion probability. Default: 20
     */
    public static int theta_del = 20;
    /**
     * The experience of a classifier required to be a subsumer. Default: 20
     */
    public static int theta_sub = 20;
    /**
     * Specifies if GA subsumption should be executed. Default: true
     */
    public static boolean doGASubsumption = true;

    // --- [ Classifier error and fitness parameters ]-----------------------
    /**
     * Specifies the exponent in the power function for the fitness evaluation.
     * Default: 5
     */
    public static double nu = 5;
    /**
     * The factor (reduction) of the prediction error when generating an
     * offspring classifier. Default: 1
     */
    public static double predictionErrorReduction = 1.0;
    /**
     * The factor (reduction) of the fitness when generating an offspring
     * classifier. Default: .1
     */
    public static double fitnessReduction = 0.1;
    /**
     * The initial prediction error value when generating a new classifier (e.g
     * in covering). Default: 0
     */
    public static double predictionErrorIni = 0.0;
    /**
     * The initial fitness value when generating a new classifier (e.g in
     * covering). Default: .01
     */
    public static double fitnessIni = 0.01;

    // ---[ Recursive Least Squares Prediction ]-----------------------------
    /**
     * The initialization vector of the diagonal in the inverse covariance
     * matrix for RLS-based predictions. Default: 1000
     */
    public static double rlsInitScaleFactor = 1000;
    /**
     * Forget rate for RLS Danger: small values may lead to instabilities!
     * Default: 1
     */
    public static double lambdaRLS = 1;
    /**
     * If set, then after the specified percentage of iterations, all gain
     * matrizes are reset according to the initial scale factor. Default: starts
     * with compaction.
     * 
     * @see XCSFConstants#rlsInitScaleFactor
     */
    public static double resetRLSPredictionsAfterSteps = startCompaction;
    /**
     * The offset value that is added in real-valued prediction for the
     * prediction generation (added to the input values).
     */
    public static double predictionOffsetValue = 1;

    /***************************************************************************
     * Trys to set the properties from the specified <tt>filename</tt>.
     * 
     * @param filename
     *            The name (relative to start directory or absolute) to the
     *            parameter file.
     */
    public static void load(String filename) {
        Properties prop = new Properties();
        try {
            FileInputStream in = new FileInputStream(filename);
            prop.load(in);
            in.close();
        } catch (IOException e) {
            File f = new File(filename);
            System.err.println("Failed to load properties from");
            System.err.println("   " + f.getAbsolutePath());
            System.err.println("Continuing with default values.");

            return;
        }
        // for each field in this class, set the property.
        for (Field f : XCSFConstants.class.getFields()) {
            String value = prop.getProperty(f.getName());
            if (value == null) {
                // ---[ property not found ]---
                System.err.println("Failed to load property '" + f.getName()
                        + "'");
            } else if (f.getName().equals("resetRLSPredictionsAfterSteps")
                    && value.toLowerCase().equals("startcompaction")) {
                // ---[ special treatment ]---
                resetRLSPredictionsAfterSteps = startCompaction;
            } else if (f.getName().equals("threadingThreshold")
                    && value.toLowerCase().equals("auto")) {
                // ---[ special treatment ]---
                threadingThreshold = -1;
            } else {
                // ---[ property found (regular case) ]---
                XCSFConstants.setValue(f, value);
            }
        }
    }

    /**
     * Trys to set the properties from the default file (that is
     * <tt>xcsf.ini</tt>).
     */
    public static void load() {
        load("xcsf.ini");
    }

    /**
     * Sets the value for the given <code>Field</code> using the given value.
     * Supported types: 'boolean', 'int', 'double', 'long', 'class
     * java.lang.String'.
     * 
     * @param field
     *            the field to set.
     * @param value
     *            the value to set.
     * @exception IllegalAccessException
     *                if the underlying field is inaccessible.
     * @exception IllegalArgumentException
     *                if the specified object is not an instance of the class or
     *                interface declaring the underlying field (or a subclass or
     *                implementor thereof), or if an unwrapping conversion
     *                fails.
     */
    private static void setValue(Field field, String value) {
        try {
            if (field.getType().toString().equals("boolean")) {
                field.setBoolean(field, Boolean.parseBoolean(value));
            } else if (field.getType().toString().equals("int")) {
                field.setInt(field, Integer.parseInt(value));
            } else if (field.getType().toString().equals("double")) {
                field.setDouble(field, Double.parseDouble(value));
            } else if (field.getType().toString().equals("long")) {
                field.setLong(field, Long.parseLong(value));
            } else if (field.getType().toString().equals(
                    "class java.lang.String")) {
                field.set(field, value);
            } else {
                System.err.println("Failed to set property '" + field.getName()
                        + "': IllegalArgument '" + value + "'");
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}

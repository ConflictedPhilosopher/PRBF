package xcsf;

import java.io.IOException;
import java.io.PrintStream;

import xcsf.XCSFUtils.Gnuplot;
import xcsf.classifier.Classifier;

/**
 * This class encapsulates all methods related to performance measurement.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class PerformanceEvaluator {

    /**
     * Description strings for the performance output.
     */
    public final static String[] HEADER = { "Iteration", "avg. Error",
            "Macro Classifier", "Micro Classifier", "avg. Matchset Macro Cl.",
            "avg. MatchSet Micro Cl.", "avg. Pred.Error", "avg. Fitness",
            "avg. Generality", "avg. Experience", "avg. SetSizeEstimate",
            "avg. Timestamp", "avg. Individual Error" };

    // format string for String.format(String, args..), length 80
    private final static String SYSO_FORMAT = "| %6d | %-12g | %6d %6d"
            + " %5d %5d | %-10g | %-10g |";
    // header, length 80
    private final static String SYSO_HEADER = "|  iter. | avg. err.    "
            + "|popSize  micro msSize micro|  fitness   | generality |"
            + System.getProperty("line.separator")
            + "+--------+--------------+--------------"
            + "-------------+------------+------------+";

    private double[][] predictionError;
    private int[] matchSetSize;
    private int[] matchSetNumerositySum;
    private int experiment;
    private double[][][] avgPerformance;
    private double[][] testPredictionError;

    /**
     * Default constructor.
     */
    PerformanceEvaluator() {
        this.predictionError = new double[XCSFConstants.averageExploitTrials][];
        this.matchSetSize = new int[XCSFConstants.averageExploitTrials];
        this.matchSetNumerositySum = new int[XCSFConstants.averageExploitTrials];
        this.experiment = -1;
        this.avgPerformance = new double[XCSFConstants.numberOfExperiments][XCSFConstants.maxLearningIterations
                / XCSFConstants.averageExploitTrials][];
    }
    
    void setTestPredErrorSize(int dataSize, int outputSize) {
    	this.testPredictionError = new double[dataSize][outputSize];
    }

    /**
     * Returns the performance of the current experiment. Note that this method
     * returns the complete array, even if not filled with values!
     * 
     * @return performance array of the current experiment
     */
    double[][] getCurrentExperimentPerformance() {
        return this.avgPerformance[this.experiment];
    }

    /**
     * Indicates, that the next experiment has begun.
     */
    void nextExperiment() {
        this.experiment++;
    }

    /**
     * Evaluates and stores the performance.
     * 
     * @param population
     *            the current population
     * @param matchSet
     *            the current matchset
     * @param iteration
     *            the current iteration
     * @param noiselessFunctionValue
     *            the noiseless function value, if available; the noisy value otherwise.
     * @param functionPrediction
     *            XCSF's current prediction
     */
    void evaluate(Population population, MatchSet matchSet, int iteration, double[] noiselessFunctionValue, double[] functionPrediction) {
        // during the exploitTrial: store error, match-set size & numerosity-sum
        double error[] = new double[functionPrediction.length];
        for (int i = 0; i < functionPrediction.length; i++) {
            error[i] = Math.abs(noiselessFunctionValue[i] - functionPrediction[i]);
        }
        int index = (iteration - 1) % XCSFConstants.averageExploitTrials;
        this.predictionError[index] = error;
        this.matchSetSize[index] = matchSet.size;
        this.matchSetNumerositySum[index] = 0;
        for (int i = 0; i < matchSet.size; i++) {
            Classifier cl = matchSet.elements[i];
            this.matchSetNumerositySum[index] += cl.getNumerosity();
        }
        // if one trial is completed, evaluate the average performance.
        if (index == XCSFConstants.averageExploitTrials - 1) {
            // iteration is multiple of avgExplTrials, e.g. 500, 1000...
            this.avgPerformance[experiment][iteration / XCSFConstants.averageExploitTrials - 1] = evaluatePerformance(population);
            // print performance
            if (XCSFConstants.numberOfExperiments == 1) {
                XCSFUtils.println(performanceToString(iteration, this.avgPerformance[experiment][iteration / XCSFConstants.averageExploitTrials - 1]));
            }
        } else if (iteration == 1 && XCSFConstants.numberOfExperiments == 1) {
            // first iteration, print the header
            XCSFUtils.println(SYSO_HEADER);
        }
    }
    
    /**
    * Calculate prediction error on a single test instance
    * 
    * @param iteration
    *            the current iteration
    * @param noiselessFunctionValue
    *            the noiseless function value, if available; the noisy value otherwise.
    * @param functionPrediction
    *            XCSF's current prediction
    */            
    void testPredictionErr(int iteration, double[] noiselessFunctionValue, double[] functionPrediction) {
    	double error[] = new double[functionPrediction.length];
    	for (int i = 0; i < functionPrediction.length; i++) {
            error[i] = Math.abs(noiselessFunctionValue[i] - functionPrediction[i]);
        }
    	this.testPredictionError[iteration] = error; 
    }
    
    /**
     * Calculate average performance measure on all test samples
     * 
     * @param testSetSize
     * 			the number of samples in the test data
     * @return the average prediction error on all test instances
     */
    
    public double[] testPerformance(int testSetSize) {
    	int outputDim = this.testPredictionError[0].length;
    	double[] avgPredError = new double[outputDim];
    	
    	for (int expl = 0; expl < this.testPredictionError.length; expl++) {
            for (int dim = 0; dim < outputDim; dim++) {
                avgPredError[dim] += this.testPredictionError[expl][dim];
            }
        }
    	
    	for (int dim = 0; dim < outputDim; dim++) {
            avgPredError[dim] /= testSetSize;
        }
    	return avgPredError;
    }

    /**
     * Writes the average performance (averaged over all experiments) to the
     * given <code>file</code>.
     * 
     * @param file
     *            the file to write to
     */
    void writeAvgPerformance(String file) {
        int rows = this.avgPerformance[0].length;
        int length = this.avgPerformance[0][0].length;
        double[][] mean = new double[rows][length];
        double[][] deviation = new double[rows][length];
        for (int row = 0; row < rows; row++) {
            determineMeanAndDeviation(row, mean[row], deviation[row]);
        }

        // write to file
        try {
            PrintStream ps = new PrintStream(file + ".txt");
            ps.println(getHeader(true));
            for (int row = 0; row < rows; row++) {
                String str = meanAndVarianceToString((row + 1)
                        * XCSFConstants.averageExploitTrials, mean[row],
                        deviation[row]);
                ps.println(str);
            }
            ps.flush();
            ps.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // create plot
        try {
            Gnuplot gnuplot = new Gnuplot();
            gnuplot.execute("set xlabel 'number of learning steps'");
            gnuplot.execute("set ylabel 'pred. error, macro cl.'");
            gnuplot.execute("set logscale y");
            gnuplot.execute("set mytics 10");
            gnuplot.execute("set style data errorlines");
            gnuplot.execute("set term postscript eps enhanced color");
            gnuplot.execute("set out '" + file + ".eps'");
            gnuplot.execute("plot '" + file + ".txt' "
                    + "using 1:2:2:($2+$3) title 'pred. error' pt 1, "
                    + "'' using 1:4:4:($4+$5) title 'macro cl.' pt 2, "
                    + "'' using 1:16:16:($16+$17) title 'generality' pt 3");
            gnuplot.execute("save '" + file + ".plt'");
            gnuplot.close();
        } catch (IOException e) {
            // ignore, gnuplot not installed
        }
    }

    /**
     * Returns a String containing mean and variance values of the final
     * iteration.
     * 
     * @return the performance of the final iteration
     */
    String getAvgFinalPerformance() {
        int lastRow = this.avgPerformance[0].length - 1;
        int length = this.avgPerformance[0][0].length;
        double[] mean = new double[length];
        double[] deviation = new double[length];
        determineMeanAndDeviation(lastRow, mean, deviation);
        StringBuffer sb = new StringBuffer(80);
        for (int i = 0; i < mean.length; i++) {
            if (i > 0)
                sb.append("\t");
            sb.append(mean[i]);
            sb.append("\t");
            sb.append(deviation[i]);
        }
        return sb.toString();
    }

    /**
     * Compute mean and variance for the specified row.
     * 
     * @param row
     *            The index of the performance array, that is, the row'th
     *            performance trial
     * @param mean
     *            On return: contains the mean of that row
     * @param deviation
     *            On return: contains the deviation of that row
     */
    private void determineMeanAndDeviation(int row, double[] mean,
            double[] deviation) {
        int experiments = this.avgPerformance.length;
        for (int i = 0; i < mean.length; i++) {
            // calculate mean & variance for performance[exp][row]
            for (int exp = 0; exp < experiments; exp++) {
                mean[i] += avgPerformance[exp][row][i] / experiments;
            }
            if (experiments > 1) {
                for (int exp = 0; exp < experiments; exp++) {
                    double dif = avgPerformance[exp][row][i] - mean[i];
                    deviation[i] += (dif * dif) / (experiments - 1.0);
                }
                deviation[i] = Math.sqrt(deviation[i]);
            } else {
                deviation[i] = 0;
            }
        }
    }

    /**
     * Evaluates the performance array for the given <code>population</code>.
     * 
     * @param population
     *            the population to evaluate
     * @return a double array containing several performance values
     * @see #getHeader()
     */
    private double[] evaluatePerformance(Population population) {
        int outputDim = this.predictionError[0].length;

        // ---[ avg prediction error, matchSetSize & -numerositySum ]---
        double[] avgPredError = new double[outputDim];
        double avgMatchSetSize = 0;
        double avgMatchSetNumerositySum = 0;
        for (int expl = 0; expl < this.predictionError.length; expl++) {
            for (int dim = 0; dim < outputDim; dim++) {
                avgPredError[dim] += this.predictionError[expl][dim];
            }
            avgMatchSetSize += this.matchSetSize[expl];
            avgMatchSetNumerositySum += this.matchSetNumerositySum[expl];
        }
        for (int dim = 0; dim < outputDim; dim++) {
            avgPredError[dim] /= XCSFConstants.averageExploitTrials;
        }
        avgMatchSetSize /= XCSFConstants.averageExploitTrials;
        avgMatchSetNumerositySum /= XCSFConstants.averageExploitTrials;

        // ---[ calculate values ]---
        double[] performance = new double[11 + outputDim];
        // 1) avg prediction error (sum over all dim)
        for (int dim = 0; dim < outputDim; dim++) {
            performance[0] += avgPredError[dim];
        }
        // 2) population size
        performance[1] = population.size;
        // 3) population numerositySum
        int popNumerositySum = 0; // see loop below
        // 4) avg matchSetSize
        performance[3] = avgMatchSetSize;
        // 5) avg matchSetNumerositySum
        performance[4] = avgMatchSetNumerositySum;
        // 6-11) population performance
        performance[5] = 0;
        performance[6] = 0;
        performance[7] = 0;
        performance[8] = 0;
        performance[9] = 0;
        for (int i = 0; i < population.size; i++) {
            Classifier cl = population.elements[i];
            // 3) numerositySum
            popNumerositySum += cl.getNumerosity();
            // 6) predictionerror
            performance[5] += cl.getPredictionError() * cl.getNumerosity();
            // 7) fitness
            performance[6] += cl.getFitness();
            // 8) generality
            performance[7] += cl.getGenerality() * cl.getNumerosity();
            // 9) experience
            performance[8] += cl.getExperience() * cl.getNumerosity();
            // 10) setSizeEstimate
            performance[9] += cl.getSetSizeEstimate() * cl.getNumerosity();
            // 11) timeStamp
            performance[10] += cl.getTimestamp() * cl.getNumerosity();
        }
        performance[2] = popNumerositySum;
        performance[5] /= popNumerositySum;
        performance[6] /= popNumerositySum;
        performance[7] /= popNumerositySum;
        performance[8] /= popNumerositySum;
        performance[9] /= popNumerositySum;
        performance[10] /= popNumerositySum;

        // 12-?) avg prediction error per dimension
        for (int dim = 0; dim < outputDim; dim++) {
            performance[11 + dim] = avgPredError[dim];
        }
        return performance;
    }

    /**
     * Returns the header for performance files.
     * 
     * @param deviation
     *            indicates, if the deviation is included.
     * @return the header for performance files.
     */
    private static String getHeader(boolean deviation) {
        String header = "#" + HEADER[0];
        for (int i = 1; i < HEADER.length; i++) {
            header += "\t" + HEADER[i];
            if (deviation) {
                header += "[mean]\t[deviation]";
            }
        }
        return header;
    }

    /**
     * Creates a nice <code>String</code> from the given array.
     * 
     * @param iteration
     *            the current iteration
     * @param performance
     *            the performance
     * @return a nice formatted string
     * @see #SYSO_HEADER
     */
    private static String performanceToString(int iteration,
            double[] performance) {
        return String.format(SYSO_FORMAT, //
                iteration, //
                performance[0], // avg error
                (int) performance[1], // macro popsize
                (int) performance[2], // micro
                (int) performance[3], // macro matchset size
                (int) performance[4], // micro
                performance[6], // avg fitness
                performance[7] // avg generality
                );
    }

    /**
     * Creates a <code>String</code> to store performance mean and variance.
     * 
     * @param iteration
     *            the current iteration
     * @param mean
     *            the mean of the performance
     * @param variance
     *            the sample variance of the performance
     * @return a nice formatted String
     */
    private static String meanAndVarianceToString(int iteration, double[] mean,
            double[] variance) {
        String str = iteration + "";
        for (int i = 0; i < mean.length; i++) {
            str += "\t" + mean[i] + "\t" + variance[i];
        }
        return str;
    }
}

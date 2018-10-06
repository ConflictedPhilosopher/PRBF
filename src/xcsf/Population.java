package xcsf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;

import xcsf.classifier.Classifier;

/**
 * The Population class extends <code>ClassifierSet</code> and offers deletion
 * as well as greedy compaction methods.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class Population extends ClassifierSet {

    private final static String SEPARATOR1 = ";";
    private final static String SEPARATOR2 = ":";

    /**
     * Comparator to allow for sorting of classifiers by predictionError.
     * <p>
     * Note: inexperienced classifiers are assumed to have high error.
     */
    private final static Comparator<Classifier> COMPACTION_COMPARATOR = new Comparator<Classifier>() {

        private final static double INEXPERIENCED = 1000;

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Comparator#compare(Object, Object)
         */
        public int compare(Classifier cl1, Classifier cl2) {
            int exp1 = cl1.getExperience();
            int exp2 = cl2.getExperience();
            if (exp1 < XCSFConstants.theta_sub
                    && exp2 < XCSFConstants.theta_sub) {
                // both inexperienced => equality for sorter
                return 0;
            } else if (exp1 < XCSFConstants.theta_sub) {
                // o1 low experience => assume high error
                return Double.compare(INEXPERIENCED, cl2.getPredictionError());
            } else if (exp2 < XCSFConstants.theta_sub) {
                // o2 low experience => assume high error
                return Double.compare(cl1.getPredictionError(), INEXPERIENCED);
            } else {
                // two experienced classifiers: compare prediction error
                return Double.compare(cl1.getPredictionError(), cl2
                        .getPredictionError());
            }
        }
    };

    /**
     * Default constructor creates an empty population.
     */
    public Population() {
        super();
    }

    /**
     * Delete <code>number</code> classifiers by roulette wheel selection.
     * 
     * @param number
     *            the number of classifiers to delete
     */
    void deleteWorstClassifiers(int number) {
        // ---[ init roulette wheel ]---
        double meanFitness = 0;
        int numerositySum = 0;
        int n = size;
        for (int i = 0; i < n; i++) {
            meanFitness += elements[i].getFitness();
            numerositySum += elements[i].getNumerosity();
        }
        meanFitness /= numerositySum;
        double[] rouletteWheel = new double[n];
        rouletteWheel[0] = elements[0].getDeletionVote(meanFitness);
        for (int i = 1; i < n; i++) {
            rouletteWheel[i] = rouletteWheel[i - 1]
                    + elements[i].getDeletionVote(meanFitness);
        }

        // ---[ delete number classifiers with given roulettewheel ]---
        int[] deletedIndices = new int[number];
        for (int i = 0; i < number; i++) {
            deletedIndices[i] = -1;
        }
        int deleted = 0; // increased, if numerosity is reduced
        int reallyDeleted = 0; // increased if numerosity is reduced to 0
        while (deleted < number) {
            double choicePoint = rouletteWheel[n - 1]
                    * XCSFUtils.Random.uniRand();
            int index = binaryRWSearch(rouletteWheel, choicePoint);
            // choicepoint found. classifier at index stil exists?
            boolean alreadyDeleted = false;
            for (int j = 0; j < deleted; j++) {
                if (deletedIndices[j] == index) {
                    alreadyDeleted = true;
                    break;
                }
            }
            // if classifier is fine, reduce numerosity
            // else re-roll random index
            if (!alreadyDeleted) {
                Classifier cl = elements[index];
                cl.addNumerosity(-1);
                if (cl.getNumerosity() == 0) {
                    // really delete this classifier after while-loop
                    deletedIndices[deleted] = index;
                    reallyDeleted++;
                }
                deleted++;
            }
        }
        // delete classifiers with zero-numerosity
        if (reallyDeleted > 0) {
            int[] indices = new int[reallyDeleted];
            int i = 0;
            for (int index : deletedIndices) {
                if (index != -1) {
                    indices[i++] = index;
                }
            }
            super.remove(indices);
        }
    }

    /**
     * Applies the greedy compaction mechanism specified in the IEEE TEC paper
     * (Butz, Lanzi, Wilson, 2008). Greedily considers all experienced,
     * low-error classifiers and subsumes all that overlap with the center of
     * the candidate classifier.
     */
    void applyGreedyCompaction() {
        if (size < 2) {
            return;
        }
        // sorting the population based on experience & predictionError
        super.sort(COMPACTION_COMPARATOR);
        // now the first element has lowest error
        // least elements are inexperienced

        for (int i = 0; i < size; i++) {
            Classifier clLow = elements[i];
            double[] reference = clLow.getCondition().getCenter();
            for (int j = i + 1; j < size; j++) {
                Classifier clHigh = elements[j];
                // if clHigh matches clLow.center, delete clHigh
                // remember: high index => high error and vice versa
                if (clHigh.getCondition().doesMatch(reference)) {
                    // clLow subsumes clHigh
                    clLow.addNumerosity(clHigh.getNumerosity());
                    super.remove(j);
                    j--; // don't miss element after deletion index
                }
            }
        }
    }

    /**
     * Implementation of a binary search for the rouletteWheel. Returns the
     * index with
     * 
     * <pre>
     * rw[index-1] &lt;= choicePoint
     * rw[index]   &gt;  choicePoint
     * </pre>
     * 
     * Precondition: <tt>0 <= choicePoint < rw[rw.length-1]</tt>
     * 
     * @param rw
     *            the ascending sorted roulettewheel
     * @param choicePoint
     *            the chosen point in the given roulettewheel
     * @return the first index with <tt>rw[index] > choicePoint</tt>
     */
    private static int binaryRWSearch(double[] rw, double choicePoint) {
        int low = 0;
        int high = rw.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (choicePoint < rw[mid]) {
                high = mid;
            } else if (choicePoint > rw[mid]) {
                low = mid + 1;
            } else { // rare case: exactly hit the key, quick return
                return mid + 1;
            }
        }
        return low;
    }

    /**
     * Writes this population to the given file using default separators.
     * 
     * @param file
     *            the file to write to
     * @throws FileNotFoundException
     *             If the given file object does not denote an existing,
     *             writable regular file and a new regular file of that name
     *             cannot be created, or if some other error occurs while
     *             opening or creating the file
     */
    public void writePopulation(File file) throws FileNotFoundException {
        writePopulation(file, SEPARATOR1, SEPARATOR2);
    }

    /**
     * Parses the given <code>File</code> and returns the contained population.
     * 
     * @param file
     *            The file that contains the population String as returned by
     *            {@link Population#writePopulation(File)}.
     * @return the parsed population
     * @throws IOException
     *             If any I/O errors occur during parsing.
     * @throws InvocationTargetException
     *             If the constructor call to the condition or prediction class
     *             resulted in an exception.
     * @throws IllegalAccessException
     *             If the constructor (condition or prediction) enforces Java
     *             language access control and the underlying constructor is
     *             inaccessible.
     * @throws InstantiationException
     *             If the class that declares the underlying constructor
     *             represents an abstract class.
     * @throws IllegalArgumentException
     *             If the number of actual and formal parameters differ; if an
     *             unwrapping conversion for primitive arguments fails; or if,
     *             after possible unwrapping, a parameter value cannot be
     *             converted to the corresponding formal parameter type by a
     *             method invocation conversion; if this constructor pertains to
     *             an enum type.
     */
    public Population parse(File file) throws IOException,
            IllegalArgumentException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        return parse(file, SEPARATOR1, SEPARATOR2, 0);
    }

    /**
     * Writes this population to the given file using the specified separators.
     * 
     * @param file
     *            the file to write to
     * @param seperator1
     *            The separator for classifier items
     * @param separator2
     *            The separator for condition and prediction items
     * @throws FileNotFoundException
     *             If the given file object does not denote an existing,
     *             writable regular file and a new regular file of that name
     *             cannot be created, or if some other error occurs while
     *             opening or creating the file
     */
    public void writePopulation(File file, CharSequence seperator1,
            CharSequence separator2) throws FileNotFoundException {
        PrintStream ps = new PrintStream(file);
        for (int i = 0; i < size; i++) {
            elements[i].write(ps, seperator1, separator2);
            ps.println();
        }
        ps.flush();
        ps.close();
    }

    /**
     * Parses the given <code>file</code> and returns the contained population.
     * 
     * @param file
     *            The file that contains the population String as returned by
     *            {@link Population#writePopulation(File, CharSequence, CharSequence)}
     *            .
     * @param splitRegex1
     *            the regex pattern to split classifier items
     * @param splitRegex2
     *            the regex pattern to split condition and prediction items
     * @param skipLines
     *            the number of lines to skip
     * @return The parsed population.
     * @throws IOException
     *             If any I/O errors occur during parsing.
     * @throws InvocationTargetException
     *             If the constructor call to the condition or prediction class
     *             resulted in an exception.
     * @throws IllegalAccessException
     *             If the constructor (condition or prediction) enforces Java
     *             language access control and the underlying constructor is
     *             inaccessible.
     * @throws InstantiationException
     *             If the class that declares the underlying constructor
     *             represents an abstract class.
     * @throws IllegalArgumentException
     *             If the number of actual and formal parameters differ; if an
     *             unwrapping conversion for primitive arguments fails; or if,
     *             after possible unwrapping, a parameter value cannot be
     *             converted to the corresponding formal parameter type by a
     *             method invocation conversion; if this constructor pertains to
     *             an enum type.
     */
    public Population parse(File file, String splitRegex1, String splitRegex2,
            int skipLines) throws IOException, IllegalArgumentException,
            InstantiationException, IllegalAccessException,
            InvocationTargetException {
        BufferedReader in = new BufferedReader(new FileReader(file));
        this.clear();
        // skip lines
        for (int i = 0; i < skipLines && in.ready(); i++) {
            in.readLine();
        }
        // parse classifiers
        while (in.ready()) {
            String line = in.readLine().trim();
            if (line.length() > 0) {
                Classifier cl = Classifier
                        .parse(line, splitRegex1, splitRegex2);
                this.add(cl);
            }
        }
        in.close();
        return this;
    }
}

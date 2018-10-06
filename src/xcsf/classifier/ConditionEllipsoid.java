package xcsf.classifier;

import java.io.PrintStream;
import java.util.Arrays;

import xcsf.XCSFConstants;
import xcsf.XCSFUtils;

/**
 * Ellipsoidal condition without rotation.
 * 
 * @author Patrick Stalph
 */
public class ConditionEllipsoid implements Condition {

    // static array to improve performance.
    private static double[] tmpArray2;

    // center, stretch & engles define location & shape of this hyperellipsoid
    private int dimension;
    private double[] center;
    private double[] stretch;

    // to avoid multiple calculations for one state
    private double[] conditionInput;
    private double squareDistance;
    // to avoid mem-alloc for matching
    private double[] tmpArray1;

    /**
     * Default constructor for covering creates a condition, that matches the
     * given <code>conditionInput</code>.
     * 
     * @param conditionInput
     *            the input for this condition
     */
    public ConditionEllipsoid(double[] conditionInput) {
        this(conditionInput.length);
        // center matches input
        for (int i = 0; i < dimension; i++) {
            center[i] = conditionInput[i];
        }
        // random stretch between min and min+range
        for (int i = 0; i < dimension; i++) {
            stretch[i] = XCSFConstants.minConditionStretch
                    + XCSFUtils.Random.uniRand()
                    * XCSFConstants.coverConditionRange;
        }
    }

    /**
     * Parses the given String array. For details, how this constructor is
     * called, see {@link Classifier#parse(String, String, String)}.
     * 
     * @param args
     *            the splited String
     */
    public ConditionEllipsoid(String[] args) {
        this(Integer.parseInt(args[0]));
        this.center = XCSFUtils.FileIO.parseDoubleArray(args[1]);
        this.stretch = XCSFUtils.FileIO.parseDoubleArray(args[2]);
    }

    /**
     * Private alternative constructor for cloning and parsing.
     * 
     * @param dimension
     *            the dimensionality of this condition
     */
    private ConditionEllipsoid(int dimension) {
        this.dimension = dimension;
        tmpArray1 = new double[dimension];
        this.center = new double[dimension];
        this.stretch = new double[dimension];
        this.tmpArray1 = new double[dimension];
        // static initializer
        if (tmpArray2 == null || tmpArray2.length != dimension) {
            tmpArray2 = new double[dimension];
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#doesMatch(double[])
     */
    public boolean doesMatch(double[] input) {
        if (!XCSFUtils.arrayEquals(this.conditionInput, input)) {
            this.squareDistance = calculateRelativeSquaredDistance(input);
            this.conditionInput = input;
        }
        return this.squareDistance < 1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#getActivity(double[])
     */
    public double getActivity(double[] input) {
        if (!XCSFUtils.arrayEquals(this.conditionInput, input)) {
            this.squareDistance = calculateRelativeSquaredDistance(input);
            this.conditionInput = input;
        }
        return Math.exp(-this.squareDistance);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#isMoreGeneral(xcsf.classifier.Condition)
     */
    public boolean isMoreGeneral(Condition otherCondition) {
        ConditionEllipsoid other = (ConditionEllipsoid) otherCondition;
        System.arraycopy(other.center, 0, tmpArray2, 0, dimension);
        // check inclusion for each dimension (and both directions)
        for (int dim = 0; dim < dimension; dim++) {
            // check for equal shape
            if (this.center[dim] != other.center[dim]
                    || this.stretch[dim] != other.stretch[dim]) {
                tmpArray2[dim] += other.stretch[dim];
                if (calculateRelativeSquaredDistance(tmpArray2) > 1) {
                    return false;
                }
                tmpArray2[dim] -= 2 * other.stretch[dim];
                if (calculateRelativeSquaredDistance(tmpArray2) > 1) {
                    return false;
                }
                tmpArray2[dim] = other.center[dim];
            } // else equal shape in dim => same generality.
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#mutation()
     */
    public void mutation() {
        double probability = XCSFConstants.pM / (2 * dimension);
        // first, mutate the center: relative to shape & size
        for (int i = 0; i < dimension; i++) {
            if (XCSFUtils.Random.uniRand() < probability) {
                // rnd in [-1,1)
                double rnd = (2.0 * XCSFUtils.Random.uniRand()) - 1.0;
                center[i] += rnd * this.stretch[i];
                if (center[i] < LOWER_BOUND) {
                    center[i] = LOWER_BOUND;
                } else if (center[i] > UPPER_BOUND) {
                    center[i] = UPPER_BOUND;
                }
            }
        }

        // second, mutate the stretch: uni-rnd between 50% and 200% of stretch
        for (int i = 0; i < dimension; i++) {
            if (XCSFUtils.Random.uniRand() < probability) {
                double rnd = 1.0;
                if (XCSFUtils.Random.uniRand() < 0.5) {
                    // enlarge (up to twice the stretch)
                    rnd += XCSFUtils.Random.uniRand();
                } else {
                    // shrink (down to half the stretch)
                    rnd -= 0.5 * XCSFUtils.Random.uniRand();
                }
                // rnd [0.5 : 2]
                stretch[i] *= rnd; // no bound check necessary
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#crossover(xcsf.classifier.Condition)
     */
    public void crossover(Condition otherCondition) {
        ConditionEllipsoid other = (ConditionEllipsoid) otherCondition;
        if (XCSFUtils.Random.uniRand() < XCSFConstants.pX) {
            // center
            for (int i = 0; i < dimension; i++) {
                if (XCSFUtils.Random.uniRand() < 0.5) {
                    XCSFUtils.flip(i, this.center, other.center);
                }
            }
            // stretch
            for (int i = 0; i < dimension; i++) {
                if (XCSFUtils.Random.uniRand() < 0.5) {
                    XCSFUtils.flip(i, this.stretch, other.stretch);
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#getVolume()
     */
    public double getVolume() {
        double volume = Math.pow(2.0, dimension - 1) / dimension * Math.PI;
        for (int i = 0; i < dimension; i++) {
            volume *= stretch[i];
        }
        return volume;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "condition{center" + Arrays.toString(this.center) + " stretch"
                + Arrays.toString(this.stretch) + "}";
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#copy()
     */
    public Condition reproduce() {
        ConditionEllipsoid clone = new ConditionEllipsoid(this.dimension);
        // copy values
        System.arraycopy(center, 0, clone.center, 0, dimension);
        System.arraycopy(stretch, 0, clone.stretch, 0, dimension);
        return clone;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#equals(xcsf.classifier.Condition)
     */
    public boolean equals(Condition otherCondition) {
        ConditionEllipsoid other = (ConditionEllipsoid) otherCondition;
        for (int i = 0; i < dimension; i++) {
            if (this.center[i] != other.center[i]
                    || this.stretch[i] != other.stretch[i]) {
                return false;
            }
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#getCenter()
     */
    public double[] getCenter() {
        return this.center;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#write(java.io.PrintStream,
     * java.lang.CharSequence)
     */
    public void write(PrintStream out, CharSequence separator) {
        StringBuffer s = new StringBuffer();
        s.append(this.dimension);
        s.append(separator);
        s.append(Arrays.toString(this.center));
        s.append(separator);
        s.append(Arrays.toString(this.stretch));
        out.print(s);
    }

    /**
     * Returns the stretch of this condition.
     * 
     * @return the stretch
     */
    public double[] getStretch() {
        return this.stretch;
    }

    /**
     * This method calculates the distance from the center to <code>point</code>
     * , where the distance is...
     * <ul>
     * <li>0, iff center == point
     * <li>0 > x > 1, iff this hyperellipsoid contains point
     * <li>1, iff point lies on the hyperellipsoid surface
     * <li>x > 1 else
     * </ul>
     * 
     * @param point
     *            the condition input
     * @return the relative distance
     */
    private double calculateRelativeSquaredDistance(double[] point) {
        // vector from center -> point, divided by stretch
        for (int i = 0; i < dimension; i++) {
            tmpArray1[i] = point[i] - center[i];
            tmpArray1[i] /= stretch[i];
        }
        double dist = 0;
        for (int i = 0; i < dimension; i++) {
            dist += tmpArray1[i] * tmpArray1[i];
        }
        return dist;
    }
}

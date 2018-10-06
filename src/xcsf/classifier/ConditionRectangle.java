package xcsf.classifier;

import java.io.PrintStream;
import java.util.Arrays;

import xcsf.XCSFConstants;
import xcsf.XCSFUtils;

/**
 * Implementation of a rectangular condition without rotation.
 * 
 * @author Patrick Stalph
 */
public class ConditionRectangle implements Condition {

    // center, stretch & engles define location & shape of this hyperrectangle
    private int dimension;
    private double[] center;
    private double[] stretch;

    // to avoid multiple calculations for one state
    private double[] conditionInput;
    private double maxDistance;
    // to avoid mem-alloc for matching
    private double[] tmpArray;

    /**
     * Default constructor for covering.
     * 
     * @param conditionInput
     *            the input for this condition
     */
    public ConditionRectangle(double[] conditionInput) {
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
    public ConditionRectangle(String[] args) {
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
    private ConditionRectangle(int dimension) {
        this.dimension = dimension;
        this.center = new double[dimension];
        this.stretch = new double[dimension];
        this.tmpArray = new double[dimension];
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#doesMatch(double[])
     */
    public boolean doesMatch(double[] input) {
        if (!XCSFUtils.arrayEquals(this.conditionInput, input)) {
            this.calculateMaxDistance(input);
            // store input to avoid overhead
            this.conditionInput = input;
        }
        return this.maxDistance < 1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#getActivity(double[])
     */
    public double getActivity(double[] input) {
        if (!XCSFUtils.arrayEquals(this.conditionInput, input)) {
            this.calculateMaxDistance(input);
            // store input to reduce overhead
            this.conditionInput = input;
        }
        return Math.exp(-this.maxDistance);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#isMoreGeneral(xcsf.classifier.Condition)
     */
    public boolean isMoreGeneral(Condition otherCondition) {
        ConditionRectangle other = (ConditionRectangle) otherCondition;
        for (int i = 0; i < dimension; i++) {
            double l1 = center[i] - stretch[i];
            double u1 = center[i] + stretch[i];
            double l2 = other.center[i] - other.stretch[i];
            double u2 = other.center[i] + other.stretch[i];
            if (l1 > l2 || u1 < u2) {
                return false;
            }
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#getVolume()
     */
    public double getVolume() {
        double volume = 1;
        for (int i = 0; i < dimension; i++) {
            volume *= 2 * stretch[i];
        }
        return volume;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#crossover(xcsf.classifier.Condition)
     */
    public void crossover(Condition otherCondition) {
        ConditionRectangle other = (ConditionRectangle) otherCondition;
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
     * @see xcsf.classifier.Condition#equals(xcsf.classifier.Condition)
     */
    public boolean equals(Condition otherCondition) {
        ConditionRectangle other = (ConditionRectangle) otherCondition;
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
        ConditionRectangle clone = new ConditionRectangle(this.dimension);
        System.arraycopy(center, 0, clone.center, 0, dimension);
        System.arraycopy(stretch, 0, clone.stretch, 0, dimension);
        return clone;
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
     * Computes the relative distance to this condition. The distance is zero at
     * the center, one on the border and greater one outside the rectangle.
     * 
     * @param input
     *            the input for this condition
     */
    private void calculateMaxDistance(double[] input) {
        // vector from center -> point, divided by stretch
        for (int i = 0; i < dimension; i++) {
            tmpArray[i] = input[i] - center[i];
            tmpArray[i] /= stretch[i];
        }
        // calculate max distance
        this.maxDistance = 0;
        for (int i = 0; i < dimension; i++) {
            double relativeDist = Math.abs(tmpArray[i]);
            if (relativeDist > this.maxDistance) {
                this.maxDistance = relativeDist;
            }
        }
    }
}

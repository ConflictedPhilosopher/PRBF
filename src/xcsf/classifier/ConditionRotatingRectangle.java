package xcsf.classifier;

import java.io.PrintStream;
import java.util.Arrays;

import xcsf.XCSFConstants;
import xcsf.XCSFUtils;

/**
 * Implementation of a rotating hyper-rectangle condition analogous to the
 * {@link ConditionRotatingEllipsoid}.
 * 
 * @author Patrick Stalph
 */
public class ConditionRotatingRectangle implements Condition {

    // temporary arrays to avoid mem alloc.
    private static double[] tmpArray2;
    private static double[][] tmpSingleRotation;
    private static double[][] tmpTransformation;
    private static double[][] tmpMatrix;

    // center, stretch & engles define location & shape of this hyperrectangle
    private int dimension;
    private double[] center;
    private double[] stretch;
    private double[] angle;
    // derived transformation matrices
    private double[][] transform;
    private double[][] inverseTransform;
    // flag to indicate changes in center/stretch/angle => recalculate transf.
    private boolean changed;

    // to avoid multiple calculations for one state
    private double[] conditionInput;
    private double maxDistance;
    // to avoid mem-alloc for matching
    private double[] tmpArray1;

    /**
     * Default constructor for covering.
     * 
     * @param conditionInput
     *            the input for this condition
     */
    public ConditionRotatingRectangle(double[] conditionInput) {
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
        // random angle
        for (int i = 0; i < this.angle.length; i++) {
            angle[i] = XCSFUtils.Random.uniRand() * 2.0 * Math.PI;
        }

        // static initializer and transformation matrices for covering only
        init(dimension);
        this.recalculateTransformationMatrix();
    }

    /**
     * Parses the given String array. For details, how this constructor is
     * called, see {@link Classifier#parse(String, String, String)}.
     * 
     * @param args
     *            the splited String
     */
    public ConditionRotatingRectangle(String[] args) {
        this(Integer.parseInt(args[0]));
        this.center = XCSFUtils.FileIO.parseDoubleArray(args[1]);
        this.stretch = XCSFUtils.FileIO.parseDoubleArray(args[2]);
        this.angle = XCSFUtils.FileIO.parseDoubleArray(args[3]);
        this.inverseTransform = XCSFUtils.FileIO.parse2dDoubleArray(args[4]);
        this.transform = XCSFUtils.FileIO.parse2dDoubleArray(args[5]);
        init(this.dimension);
    }

    /**
     * Private alternative constructor for cloning and parsing.
     * 
     * @param dimension
     *            the dimensionality of this condition
     */
    private ConditionRotatingRectangle(int dimension) {
        this.dimension = dimension;
        this.center = new double[dimension];
        this.stretch = new double[dimension];
        this.angle = new double[dimension * (dimension - 1) / 2];
        // derived: the transformation matrices of this hyperellipsoid
        this.transform = new double[dimension + 1][dimension + 1];
        this.inverseTransform = new double[dimension + 1][dimension + 1];
        // tmp array for matching
        tmpArray1 = new double[dimension];
    }

    /**
     * Initializes static arrays, if necessary.
     * 
     * @param dimension
     *            the dimensionality of this condition
     */
    private static void init(int dimension) {
        if (tmpArray2 == null || tmpArray2.length != dimension) {
            tmpArray2 = new double[dimension];
            tmpMatrix = new double[dimension][dimension];
            tmpTransformation = new double[dimension + 1][dimension + 1];
            tmpSingleRotation = new double[dimension][dimension];
            for (int i = 0; i < dimension; i++) {
                for (int j = 0; j < dimension; j++) {
                    tmpSingleRotation[i][j] = (i != j) ? 0 : 1;
                }
            }
        }
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
        ConditionRotatingRectangle other = (ConditionRotatingRectangle) otherCondition;
        // map unit hypercube to other and map that one inverse to
        // hypercube of this rectangle.
        XCSFUtils.Matrix.multiply(this.inverseTransform, other.transform,
                tmpTransformation, dimension + 1);

        // check, if resulting transformation stays in unit cube,
        // i.e. each edge.dimension < 1
        return checkEdges(0);
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
        ConditionRotatingRectangle other = (ConditionRotatingRectangle) otherCondition;
        this.changed = other.changed = false;
        if (XCSFUtils.Random.uniRand() < XCSFConstants.pX) {
            // center
            for (int i = 0; i < dimension; i++) {
                if (XCSFUtils.Random.uniRand() < 0.5) {
                    this.changed = other.changed = true;
                    XCSFUtils.flip(i, this.center, other.center);
                }
            }
            // stretch
            for (int i = 0; i < dimension; i++) {
                if (XCSFUtils.Random.uniRand() < 0.5) {
                    this.changed = other.changed = true;
                    XCSFUtils.flip(i, this.stretch, other.stretch);
                }
            }
            // angles
            for (int i = 0; i < this.angle.length; i++) {
                if (XCSFUtils.Random.uniRand() < 0.5) {
                    this.changed = other.changed = true;
                    XCSFUtils.flip(i, this.angle, other.angle);
                }
            }
        }
        // don't check this.changed, because mutation is called anyways!
        // see last line of mutation() method.
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#mutation()
     */
    public void mutation() {
        double probability = XCSFConstants.pM / (2 * dimension + angle.length);
        // first, mutate the center: relative to shape & size
        for (int i = 0; i < dimension; i++) {
            if (XCSFUtils.Random.uniRand() < probability) {
                this.changed = true;
                // rnd in [-1,1)
                tmpArray1[i] = (2.0 * XCSFUtils.Random.uniRand()) - 1.0;
            } else {
                tmpArray1[i] = 0;
            }
        }
        // now tmpArray1 contains mutation vector for center
        if (this.changed) {
            // multiply with transformation matrix without translation part
            XCSFUtils.Matrix.multiply(this.transform, tmpArray1, tmpArray2,
                    dimension);
            // tmpArray2 is mutation vector in shape & size of the
            // hyperellipsoid
            for (int i = 0; i < dimension; i++) {
                center[i] += tmpArray2[i];
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
                this.changed = true;
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

        // mutate angles, max 45°
        for (int i = 0; i < angle.length; i++) {
            if (XCSFUtils.Random.uniRand() < probability) {
                this.changed = true;
                // max rotation: PI/4 = 45°
                double change = XCSFUtils.Random.uniRand() * Math.PI * 0.25;
                angle[i] += (XCSFUtils.Random.uniRand() < 0.5) ? change
                        : -change;
                // stay in (human readable) bounds, although not necessary
                if (angle[i] < LOWER_ROTATION_BOUND) {
                    angle[i] += 2 * Math.PI; // +360°
                } else if (angle[i] > UPPER_ROTATION_BOUND) {
                    angle[i] -= 2 * Math.PI; // -360°
                }
            }
        }
        // if anything (center/stretch/angle) is changed: recalulate the matrix
        if (this.changed) {
            recalculateTransformationMatrix();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#equals(xcsf.classifier.Condition)
     */
    public boolean equals(Condition otherCondition) {
        ConditionRotatingRectangle other = (ConditionRotatingRectangle) otherCondition;
        for (int i = 0; i < dimension; i++) {
            if (this.center[i] != other.center[i]
                    || this.stretch[i] != other.stretch[i]) {
                return false;
            }
        }
        for (int i = 0; i < this.angle.length; i++) {
            if (this.angle[i] != other.angle[i]) {
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
                + Arrays.toString(this.stretch) + " angles"
                + Arrays.toString(this.angle) + "}";
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.classifier.Condition#reproduce()
     */
    public Condition reproduce() {
        ConditionRotatingRectangle clone = new ConditionRotatingRectangle(
                this.dimension);
        // copy values
        System.arraycopy(center, 0, clone.center, 0, dimension);
        System.arraycopy(stretch, 0, clone.stretch, 0, dimension);
        System.arraycopy(angle, 0, clone.angle, 0, angle.length);
        clone.inverseTransform[dimension][dimension] = clone.transform[dimension][dimension] = 1;
        for (int i = 0; i < dimension; i++) {
            System.arraycopy(transform[i], 0, clone.transform[i], 0,
                    dimension + 1);
            System.arraycopy(inverseTransform[i], 0, clone.inverseTransform[i],
                    0, dimension + 1);
        }
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
        s.append(separator);
        s.append(Arrays.toString(this.angle));
        s.append(separator);
        s.append(Arrays.deepToString(this.inverseTransform));
        s.append(separator);
        s.append(Arrays.deepToString(this.transform));
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
     * Returns the rotation angles of this condition.
     * 
     * @return the rotation angles
     */
    public double[] getAngles() {
        return this.angle;
    }

    /**
     * Returns the transformation matrix of this condition.
     * 
     * @return the transformation matrix
     */
    public double[][] getTransform() {
        return this.transform;
    }

    /**
     * Returns the inverse transformation matrix of this condition.
     * 
     * @return the inverse transformation matrix
     */
    public double[][] getInverseTransform() {
        return this.inverseTransform;
    }

    /**
     * Computes the forward and inverse transformations (stored as matrices) for
     * this rotated rectangle. Subsequent calls for matching, activity,
     * generality, etc. can use these transformations without redundant
     * re-calculation.
     */
    public void recalculateTransformationMatrix() {
        setInverseTransform(this.inverseTransform, center, stretch, angle,
                dimension);
        setTransform(this.transform, center, stretch, angle, dimension);
        this.changed = false;
        this.conditionInput = null; // reset activity calculation
    }

    /**
     * This method uses the inverse transformation to map the input to the
     * unit-hyper-cube. Then the maximum distance to the origin is returned,
     * which is in fact the max. relative distance to the rotated
     * hyperrectangle. Distance is zero at the center, one on the border and
     * greater one outside the rectangle.
     * 
     * @param input
     *            the input for this condition
     */
    private void calculateMaxDistance(double[] input) {
        // use inverse transformation:
        // rectangle coodrinate system -> default coordinate system
        XCSFUtils.Matrix.multiplyExtended(inverseTransform, input, tmpArray1,
                dimension);
        // calculate max distance
        this.maxDistance = 0;
        for (int i = 0; i < dimension; i++) {
            double relativeDist = Math.abs(tmpArray1[i]);
            if (relativeDist > this.maxDistance) {
                this.maxDistance = relativeDist;
            }
        }
    }

    /**
     * This method checks all edges of the unit-hyper-cube for inclusion in the
     * rotated and translated hyper-rectangle, which is given by
     * 
     * <pre>
     * tmpTransformation = this.inverseTransform * other.transform
     * </pre>
     * 
     * @param dim
     *            The current dimension of the edge to vary
     * @return <code>true</code>, if all edges are contained; <code>false</code>
     *         otherwise.
     */
    private boolean checkEdges(int dim) {
        // end of recursion: check edge
        if (dim == dimension) {
            // tmpArray1 contains edge
            // tmpTransformation contains crazy transformation
            XCSFUtils.Matrix.multiplyExtended(tmpTransformation, tmpArray1,
                    tmpArray2, dimension);
            for (int i = 0; i < dimension; i++) {
                if (Math.abs(tmpArray2[i]) > 1) {
                    return false;
                }
            }
            return true;
        }
        // recursion: try both edges, i.e. 1 & -1 for this dimension
        tmpArray1[dim] = 1;
        if (!checkEdges(dim + 1)) {
            // fast fail
            return false;
        }
        tmpArray1[dim] = -1;
        return checkEdges(dim + 1);
    }

    /**
     * Sets <code>matrix</code> to the rotated rectangular transformation
     * matrix, i.e. a matrix of size (<code>dim+1</code> x <code>dim+1</code>)
     * with
     * 
     * <pre>
     * matrix = translationMatrix * rotationMatrix * stretchMatrix
     * </pre>
     * 
     * <p>
     * The matrix can be used to transform any point from the default coordinate
     * system into the rotated rectangular coordinate system (origin =
     * <code>center</code>, scaled with <code>stretch</code> and rotated by
     * <code>angle</code>).
     * 
     * @param matrix
     *            the destination matrix of size (<code>dim+1</code> x
     *            <code>dim+1</code>), which will hold the transformation
     * @param center
     *            the center of the rectangle
     * @param stretch
     *            the stretch of the rectangle
     * @param angle
     *            the rotation angles of the rectangle
     * @param dim
     *            the dimension of the coordinate system
     */
    private static void setTransform(double[][] matrix, double[] center,
            double[] stretch, double[] angle, int dim) {
        // matrix = translation * rotation * stretch
        // 1. set identity & translation
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                matrix[i][j] = (i != j) ? 0 : 1;
            }
            // last column: translation
            matrix[i][dim] = center[i];
            // last row: zero
            matrix[dim][i] = 0;
        }
        // last row&column: one
        matrix[dim][dim] = 1;

        // 2. rotation
        int a = angle.length - 1; // angle index
        for (int i = dim - 1; i >= 0; i--) {
            for (int j = dim - 1; j > i; j--) {
                // singleRotation == identity at this point.
                tmpSingleRotation[i][i] = tmpSingleRotation[j][j] = Math
                        .cos(angle[a]);
                tmpSingleRotation[i][j] = -Math.sin(angle[a]);
                tmpSingleRotation[j][i] = -tmpSingleRotation[i][j];

                // multiply inverseTransformation with singlerotation
                XCSFUtils.Matrix.multiply(matrix, tmpSingleRotation, tmpMatrix,
                        dim);
                XCSFUtils.Matrix.copyMatrix(tmpMatrix, matrix, dim);
                a--;

                // reset singleRotation to identity
                tmpSingleRotation[i][i] = 1.0;
                tmpSingleRotation[i][j] = 0.0;
                tmpSingleRotation[j][i] = 0.0;
                tmpSingleRotation[j][j] = 1.0;
            }
        }

        // 3. stretch
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                matrix[i][j] *= stretch[j];
            }
        }
    }

    /**
     * Sets <code>matrix</code> to the inverse rotated-rectangular
     * transformation matrix, i.e. a matrix of size (<code>dim+1</code> x
     * <code>dim+1</code>) with
     * 
     * <pre>
     * matrix = inverse stretchMatrix * inverse rotationMatrix * inverse translationMatrix
     * </pre>
     * 
     * <p>
     * The matrix can be used to transform any point from the rotated
     * rectangular coordinate system (origin = <code>center</code>, scaled with
     * <code>stretch</code> and rotated by <code>angle</code>) into the default
     * coordinate system (zero origin, identity stretch and no rotation).
     * 
     * @param matrix
     *            the destination matrix of size (<code>dim+1</code> x
     *            <code>dim+1</code>), which will hold the inverse
     *            transformation
     * @param center
     *            the center of the rectangle
     * @param stretch
     *            the stretch of the rectangle
     * @param angle
     *            the rotation angles of the rectangle
     * @param dim
     *            the dimension of the coordinate system
     */
    private static void setInverseTransform(double[][] matrix, double[] center,
            double[] stretch, double[] angle, int dim) {
        // matrix = stretch^-1 * rotation^-1 * translation^-1
        // 1. set identity & inverse stretch
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                matrix[i][j] = (i != j) ? 0 : 1.0 / stretch[i];
            }
            // last column & row: zero
            matrix[i][dim] = matrix[dim][i] = 0;
        }
        // last row&column: one
        matrix[dim][dim] = 1;

        // 2. inverse rotation
        int a = 0; // angle index
        for (int i = 0; i < dim; i++) {
            for (int j = i + 1; j < dim; j++) {
                // singleRotation == identity at this point.
                tmpSingleRotation[i][i] = tmpSingleRotation[j][j] = Math
                        .cos(angle[a]);
                tmpSingleRotation[i][j] = Math.sin(angle[a]);
                tmpSingleRotation[j][i] = -tmpSingleRotation[i][j];

                // multiply inverseTransformation with singlerotation
                XCSFUtils.Matrix.multiply(matrix, tmpSingleRotation, tmpMatrix,
                        dim);
                XCSFUtils.Matrix.copyMatrix(tmpMatrix, matrix, dim);
                a++;

                // reset singleRotation to identity
                tmpSingleRotation[i][i] = 1.0;
                tmpSingleRotation[i][j] = 0.0;
                tmpSingleRotation[j][i] = 0.0;
                tmpSingleRotation[j][j] = 1.0;
            }
        }

        // 3. inverse translation: only last column changes
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                matrix[i][dim] -= matrix[i][j] * center[j];
            }
        }
    }
}

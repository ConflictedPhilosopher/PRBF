package xcsf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Vector;
import java.util.regex.Pattern;

/**
 * Collection of several utilities (for example, quick matrix operations without
 * memory allocation) and convenience methods.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class XCSFUtils {

    /**
     * Computes the mean of the given values, that is the average value.
     * 
     * @param values
     *            the values
     * @return the mean of <code>values</code>
     */
    public static double mean(double[] values) {
        double mean = 0;
        for (double d : values) {
            mean += d;
        }
        mean /= values.length;
        return mean;
    }

    /**
     * Computes the sample variance of the given values, that is the quadratic
     * deviation from mean.
     * 
     * @param values
     *            the values
     * @param mean
     *            the mean of <code>values</code>
     * @return he variance of <code>values</code>
     */
    public static double variance(double[] values, double mean) {
        double var = 0;
        for (double d : values) {
            var += (d - mean) * (d - mean);
        }
        var /= (values.length - 1);
        return var;
    }

    /**
     * Computes the standard deviation of the given values, that is the square
     * root of the sample variance.
     * 
     * @param values
     *            the values
     * @param mean
     *            the mean of <code>values</code>
     * @return the standard deviation of <code>values</code>
     */
    public static double standardDeviation(double[] values, double mean) {
        return Math.sqrt(variance(values, mean));
    }

    /**
     * Flip the content at the given position of the arrays. On return,
     * <code>array1</code> contains at the given <code>position</code> the value
     * of <code>array2</code> and vice versa. This method is used for crossover
     * routines.
     * 
     * @param position
     *            the array index to flip
     * @param array1
     *            the first array
     * @param array2
     *            the second array
     */
    public static void flip(int position, double[] array1, double[] array2) {
        double tmp = array1[position];
        array1[position] = array2[position];
        array2[position] = tmp;
    }

    /**
     * Faster implementation of the {@link Arrays#equals(Object)} method for two
     * double arrays. This method is used to avoid multiple activity, matching
     * or prediction calculations. Note that this method does no length checks
     * for performance reasons. The behavior is undefined, if
     * 
     * <pre>
     * array1.length != array2.length
     * </pre>
     * 
     * @param array1
     *            the first double array
     * @param array2
     *            the second double array of the same length (this is not
     *            checked!)
     * @return <code>true</code>, if the arrays contain the same values;
     *         <code>false</code> otherwise.
     */
    public static boolean arrayEquals(double[] array1, double[] array2) {
        if (array1 == array2) {
            return true;
        } else if (array1 == null || array2 == null) {
            return false;
        }
        for (int i = 0; i < array1.length; i++) {
            if (array1[i] != array2[i]) {
                return false; // fast fail
            }
        }
        return true;
    }

    /**
     * Checks the given <code>path</code> for existance and returns a valid path
     * with ending {@link File#separator}. If the given path is
     * <code>null</code>, an empty String is returned, indicating that the
     * current directory is used.
     * 
     * @param path
     *            the relative or absolute path to check
     * @return a valid path
     * @throws IllegalArgumentException
     *             if the directory denoted by <code>path</code> does not exist.
     */
    public static String checkPath(String path) {
        if (path != null && path.length() > 0) {
            if (!path.endsWith(File.separator)) {
                path = path + File.separator;
            }
            if (!new File(path).isDirectory()) {
                throw new IllegalArgumentException("path '" + path
                        + "' does not exist.");
            }
        } else {
            path = "";
        }
        return path;
    }

    /**
     * If in verbose mode, the given String is extended (whitespaces) to the
     * given length and printed.
     * 
     * @param string
     *            the String to print
     * @param length
     *            the desired length of the String
     * @see XCSFConstants#verbose
     */
    public static void print(String string, int length) {
        if (XCSFConstants.verbose) {
            System.out.format("%-" + length + "s", string);
        }
    }

    /**
     * Println the given String, if in verbose mode.
     * 
     * @param s
     *            the String to println.
     * @see XCSFConstants#verbose
     */
    public static void println(String s) {
        if (XCSFConstants.verbose) {
            System.out.println(s);
        }
    }

    /**
     * Determines the <code>num</code> first objects, where an object may
     * contain multiple copies specified in <code>nums</code>. Routine
     * determines at least the <code>num</code> first entries, if the entry at
     * the boundary contains more than 1 (specified in <code>nums</code>), it
     * will still be included.
     * 
     * @param votes
     *            the sorting criterion (largest first)
     * @param nums
     *            the number of entries each entry represents
     * @param objs
     *            the objects sorting according to the criteria
     * @param size
     *            the number of actual entries in the arrays
     * @param num
     *            the numer of largest objects put to the front
     * @return the number of first entries that make up the num largest entries
     */
    public static int putNumFirstObjectsFirst(double[] votes, int[] nums,
            Object[] objs, int size, int num) {
        putNumFirstRec(votes, nums, objs, 0, size - 1, num);
        int numsum = 0;
        for (int i = 0; i < size; i++) {
            numsum += nums[i];
            if (numsum >= num) {
                return i + 1;
            }
        }
        return size;
    }

    /**
     * Puts the objects with the highest votes first. An object objs[i] is
     * regarded as nums[i] identical objects. Does NOT sort but simply puts the
     * first num highest objects first in the array (in a generally random
     * order). Algorithms works in linear time (length of array) on average!
     * 
     * @param votes
     *            an array of votes of the corresponding objects
     * @param nums
     *            the number of identical objects each object entry represents
     * @param objs
     *            the actual array of objects
     * @param begin
     *            the first object in the array considered in the procedure
     * @param end
     *            the last object considered in the procedure
     * @param num
     *            the number of objects to be put first
     */
    private static void putNumFirstRec(double[] votes, int[] nums,
            Object[] objs, int begin, int end, int num) {
        if (num <= 0 || begin >= end) {
            return;
        }
        int helpN;
        double help = 0;
        Object helpO;
        double pivot = votes[end];
        int pivotN = nums[end];
        Object pivotO = objs[end];
        if (end - begin > 20) { // choose pivot out of 3 if long array
            // remains
            int pos0 = begin
                    + (int) (XCSFUtils.Random.uniRand() * (end + 1 - begin));
            int pos1 = begin
                    + (int) (XCSFUtils.Random.uniRand() * (end + 1 - begin));
            int pos2 = begin
                    + (int) (XCSFUtils.Random.uniRand() * (end + 1 - begin));
            int posChosen = 0;
            if (votes[pos2] < votes[pos1]) {
                if (votes[pos1] < votes[pos0]) {
                    posChosen = pos1;
                } else if (votes[pos0] < votes[pos2]) {
                    posChosen = pos2;
                } else {
                    posChosen = pos0;
                }
            } else {
                if (votes[pos2] < votes[pos0]) {
                    posChosen = pos2;
                } else if (votes[pos0] < votes[pos1]) {
                    posChosen = pos1;
                } else {
                    posChosen = pos0;
                }
            }
            // flip(posChosen, end)
            pivot = votes[posChosen];
            pivotN = nums[posChosen];
            pivotO = objs[posChosen];
            votes[posChosen] = votes[end];
            nums[posChosen] = nums[end];
            objs[posChosen] = objs[end];
            votes[end] = pivot;
            nums[end] = pivotN;
            objs[end] = pivotO;
        }
        int i = begin;
        int numSize = 0;
        for (int j = begin; j < end; j++) {
            if (pivot <= votes[j]) {
                // exchange elements - remember numSize on the left
                help = votes[j];
                votes[j] = votes[i];
                votes[i] = help;
                helpN = nums[j];
                nums[j] = nums[i];
                nums[i] = helpN;
                helpO = objs[j];
                objs[j] = objs[i];
                objs[i] = helpO;
                numSize += nums[i];
                i++;
            }
        }
        votes[end] = votes[i];
        nums[end] = nums[i];
        objs[end] = objs[i];
        votes[i] = pivot;
        nums[i] = pivotN;
        objs[i] = pivotO;
        if (num < numSize) {
            // more than numSize on the left - solve that part
            putNumFirstRec(votes, nums, objs, begin, i - 1, num);
        } else if (num < numSize + nums[i]) {
            // done including the pivotelement
            return;
        } else {
            // still need to get more from the right - solve that part
            putNumFirstRec(votes, nums, objs, i + 1, end, num - numSize
                    - nums[i]);
        }
    }

    /**
     * Loads a <code>Constructor</code> for the specified binary
     * <code>className</code>. For details on class loading see
     * {@link Class#forName(String)}. The given class must implement a
     * constructor with the given signature. Furthermore, this method asserts
     * that the class implements the specified interface. Consequently all
     * objects instantiated by the constructor can be cast to this interface.
     * The resulting constructor can be used in the following manner.
     * 
     * <pre>
     * InterfaceClass obj = (InterfaceClass) constructor.newInstance(signature);
     * </pre>
     * 
     * @param className
     *            the fully qualified binary name of the desired class
     * @param implementedInterface
     *            an interface to be implemented by the loaded class
     * @param constructorSignature
     *            an array of <code>Class</code> objects representing the
     *            signature of the constructor to be loaded
     * @return Returns a constructor, with the given signature, that
     *         instantiates objects of the given <code>className</code>, which
     *         implement the given interface. The method returns
     *         <code>null</code> if the constructor cannot be loaded for some
     *         reason.
     * @throws IllegalArgumentException
     *             if the loaded <code>Class</code> does not implement the
     *             desired interface or if it cannot be instantiated because it
     *             is either abstract, or interface, or array, or primitive.
     */
    public static Constructor<?> loadConstructor(String className,
            Class<?> implementedInterface, Class<?>[] constructorSignature) {
        try {
            // 1. try to load the class itself
            Class<?> c = Class.forName(className);
            // 2. check, if class implements the desired interface
            boolean found = false;
            for (Object ob : c.getInterfaces())
                found |= ob.equals(implementedInterface);
            if (!found)
                throw new IllegalArgumentException("Class " + className
                        + " is not implementing the "
                        + implementedInterface.getSimpleName() + " interface.");

            // 3. assert that class can be instantiated
            int modifiers = c.getModifiers();
            if (Modifier.isAbstract(modifiers)
                    || Modifier.isInterface(modifiers) || c.isArray()
                    || c.isPrimitive()) {
                throw new IllegalArgumentException("Class " + className
                        + " cannot be instantiated, because the class is"
                        + " 1) abstract or 2) an interface or"
                        + " 3) an array or 4) primitive.");
            }
            // 4. try to instantiate constructor with the desired signature
            return c.getConstructor(constructorSignature);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            String signature = "";
            for (int i = 0; i < constructorSignature.length; i++) {
                if (i > 0)
                    signature += ", ";
                signature += constructorSignature[i].getCanonicalName();
            }
            System.err
                    .println("The class "
                            + className
                            + " implements the "
                            + implementedInterface.getSimpleName()
                            + " \n"
                            + "interface, but does not provide the required constructor:\n"
                            + "  new " + className + "(" + signature + ")");
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Static methods for fast matrix calculations without memory allocation.
     * 
     * @author Patrick Stalph
     */
    public static class Matrix {

        /**
         * Multiplies <code>srcMatrix</code> (<tt>n</tt> by <tt>n</tt>) with
         * <code>srcVector</code> (length <tt>n</tt>) and puts the results into
         * <code>destination</code> (length <tt>n</tt>), that is
         * 
         * <pre>
         * destination = srcMatrix * srcVector
         * </pre>
         * 
         * Note that this method does no checks (null or length) for performance
         * reasons and does not allocate new double arrays.
         * 
         * @param srcMatrix
         *            the source quadratic <tt>n</tt> by <tt>n</tt> matrix
         * @param srcVector
         *            the source vector of length <tt>n</tt>
         * @param destination
         *            the destination vector of length <tt>n</tt>
         * @param n
         *            the size of the arrays to be multiplied
         */
        public static void multiply(double[][] srcMatrix, double[] srcVector,
                double[] destination, int n) {
            for (int i = 0; i < n; i++) {
                destination[i] = srcMatrix[i][0] * srcVector[0];
                for (int j = 1; j < n; j++) {
                    destination[i] += srcMatrix[i][j] * srcVector[j];
                }
            }
        }

        /**
         * Multiplies the extended <code>srcMatrixExt</code> (<tt>n+1</tt> by
         * <tt>n+1</tt>) with <code>srcVector</code> (length <tt>n</tt> ), adds
         * the translational component and puts the results into
         * <code>destination</code> (length <tt>n</tt>), that is
         * 
         * <pre>
         * destination  = srcMatrixExt * srcVector
         * destination += srcMatrixExt[lastColumn]
         * </pre>
         * 
         * Note that this method does no checks (null or length) for performance
         * reasons and does not allocate new double arrays.
         * 
         * @param srcMatrixExt
         *            the extended source quadratic <tt>n+1</tt> by <tt>n+1</tt>
         *            matrix
         * @param srcVector
         *            the source vector of length <tt>n</tt>
         * @param destination
         *            the destination vector of length <tt>n</tt>
         * @param n
         *            the size of the arrays to be multiplied
         */
        public static void multiplyExtended(double[][] srcMatrixExt,
                double[] srcVector, double[] destination, int n) {
            for (int i = 0; i < n; i++) {
                destination[i] = srcMatrixExt[i][0] * srcVector[0];
                for (int j = 1; j < n; j++) {
                    destination[i] += srcMatrixExt[i][j] * srcVector[j];
                }
                // translation
                destination[i] += srcMatrixExt[i][n];
            }
        }

        /**
         * Multiplies the quadratic matrix <code>srcA</code> with the quadratic
         * matrix <code>srcB</code> and puts the results into
         * <code>destination</code>, that is
         * 
         * <pre>
         * destination = srcA * srcB
         * </pre>
         * 
         * Note that this method does no checks (null or length) for performance
         * reasons and does not allocate new double arrays.
         * 
         * @param srcA
         *            the first quadratic source matrix of size <tt>n</tt> by
         *            <tt>n</tt>
         * @param srcB
         *            the second quadratic source matrix of size <tt>n</tt> by
         *            <tt>n</tt>
         * @param destination
         *            the quadratic destination matrix of size <tt>n</tt> by
         *            <tt>n</tt>
         * @param n
         *            the size of the arrays to be multiplied
         */
        public static void multiply(double[][] srcA, double[][] srcB,
                double[][] destination, int n) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    destination[i][j] = srcA[i][0] * srcB[0][j];
                    for (int k = 1; k < n; k++) {
                        destination[i][j] += srcA[i][k] * srcB[k][j];
                    }
                }
            }
        }

        /**
         * Copies the quadratic matrix <code>source</code> (<tt>n</tt> by
         * <tt>n</tt>) into <code>destination</code>.
         * <p>
         * Note that this method does no checks (null or length) for performance
         * reasons and does not allocate new double arrays.
         * 
         * @param source
         *            quadratic source matrix of size <tt>n</tt> by <tt>n</tt>
         * @param destination
         *            quadratic destination matrix of size <tt>n</tt> by
         *            <tt>n</tt>
         * @param n
         *            the size of the arrays to be multiplied.
         */
        public static void copyMatrix(double[][] source,
                double[][] destination, int n) {
            for (int i = 0; i < n; i++) {
                System.arraycopy(source[i], 0, destination[i], 0, n);
            }
        }

    }

    /**
     * This class is used to communicate with a gnuplot instance. See
     * http://www.gnuplot.info/ for details about gnuplot. Note that gnuplot
     * exits, when the java program does. However, by sending the
     * <tt>&quot;exit&quot;</tt> command to gnuplot via
     * {@link Gnuplot#execute(String)}, the gnuplot process exits, too. In order
     * to quit a gnuplot session, the method {@link Gnuplot#close()} first sends
     * the <tt>&quot;exit&quot;</tt> command to gnuplot and then closes the
     * communication channel.
     * <p>
     * If the default constructor does not work on your system, try to set the
     * gnuplot executable in the public static field
     * {@link xcsf.XCSFUtils.Gnuplot#executable}.
     * 
     * @author Patrick O. Stalph
     */
    public static class Gnuplot {

        /**
         * This static String can be specified, if the default doesn't work.
         */
        public static String executable = null;

        // communication channel: console.output -> process.input
        private PrintStream console;

        /**
         * Default constructor executes a OS-specific command to start gnuplot
         * and establishes the communication. If this constructor does not work
         * on your machine, you can specify the executable in
         * {@link xcsf.XCSFUtils.Gnuplot#executable}.
         * <p>
         * <b>Windows</b><br/>
         * Gnuplot is expected to be installed at the default location, that is
         * 
         * <pre>
         * C:\&lt;localized program files directory&gt;\gnuplot\bin\pgnuplot.exe
         * </pre>
         * 
         * where the <tt>Program Files</tt> directory name depends on the
         * language set for the OS. This constructor retrieves the localized
         * name of this directory.
         * <p>
         * <b>Linux</b><br/>
         * On linux systems the <tt>gnuplot</tt> executable has to be linked in
         * one of the default pathes in order to be available system-wide.
         * <p>
         * <b>Other Operating Systems</b><br/>
         * Other operating systems are not available to the developers and
         * comments on how defaults on these systems would look like are very
         * welcome.
         * 
         * @throws IOException
         *             if the system fails to execute gnuplot
         */
        public Gnuplot() throws IOException {
            // determine executable, if no executable is given
            if (executable == null) {
                String os = System.getProperty("os.name").toLowerCase();

                if (os.contains("linux")) {
                    // assume that path is set
                    executable = "gnuplot";

                } else if (os.contains("windows")) {
                    // assume default installation path, i.e.
                    // <localized:program files>/gnuplot/
                    String programFiles = System.getenv("ProgramFiles");
                    if (programFiles == null) { // nothing found? ups.
                        programFiles = "C:" + File.separatorChar
                                + "Program Files";
                    }
                    // assert separator
                    if (!programFiles.endsWith(File.separator))
                        programFiles += File.separatorChar;
                    executable = programFiles + "gnuplot" + File.separatorChar
                            + "bin" + File.separatorChar + "pgnuplot.exe";

                } else {
                    throw new IOException("Operating system '" + os
                            + "' is not supported. "
                            + "If you have Gnuplot installed, "
                            + "specify the executable command via"
                            + System.getProperty("line.separator")
                            + "xcsf.XCSFUtils.Gnuplot.executable "
                            + "= \"your executable\"");
                }
            }

            // start the gnuplot process and connect channels
            Process p = Runtime.getRuntime().exec(executable);
            this.console = new PrintStream(p.getOutputStream());
        }

        /**
         * Sends the given <code>command</code> to gnuplot. Multiple commands
         * can be seperated with a semicolon.
         * 
         * @param command
         *            the command to execute on the gnuplot process
         */
        public void execute(String command) {
            if (this.console == null) {
                return; // ignore
            }
            this.console.println(command);
            this.console.flush();
        }

        /**
         * Exit gnuplot and close the in/out streams. TODO make sure this is
         * always called!
         */
        public void close() {
            if (this.console == null) {
                return; // ignore
            }
            this.execute("exit");
            this.console.close();
            this.console = null;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#finalize()
         */
        protected void finalize() throws Throwable {
            this.close();
            super.finalize();
        }
    }

    /**
     * Static methods to handle basic / CSV files.
     * 
     * @author Patrick Stalph
     */
    public static class FileIO {

        // comments start with this character
        private final static String COMMENT_CHAR = "#";
        // regex for column splitting: one or more whitespaces (space, tab)
        private final static String DEFAULT_REGEX = "\\s+";

        /**
         * Parse the given CSV file (space or tab separated; several whitespaces
         * are processed as one whitespace character). Lines with a leading hash
         * symbol '#' are treated as comment lines and not returned.
         * 
         * @param file
         *            the CSV file to parse
         * @param skipLines
         *            Specifies the number of lines to skip. For example, if the
         *            first line contains the header, this may be skipped by
         *            using <code>skipLines=1</code>.
         * @return the data contained in the given file, excluding comments
         * @throws IOException
         *             if any I/O error occurs
         */
        public static String[][] parseCSV(File file, int skipLines)
                throws IOException {
            return parseCSV(file, DEFAULT_REGEX, skipLines);
        }

        /**
         * Parse the given CSV file using the given regex for splitting columns.
         * Lines with a leading hash symbol '#' are treated as comment lines and
         * not returned.
         * 
         * @param file
         *            the CSV file to read
         * @param splitregex
         *            the regular expression (see {@link Pattern}) used to split
         *            the content of one line
         * @param skipLines
         *            Specifies the number of lines to skip. For example, if the
         *            first line contains the header, this may be skipped by
         *            using <code>skipLines=1</code>.
         * @return String array containing all non-empty non-comment lines.
         * @throws IOException
         *             if any I/O error occurs
         */
        public static String[][] parseCSV(File file, String splitregex,
                int skipLines) throws IOException {
            BufferedReader in = new BufferedReader(new FileReader(file));
            Vector<String> lines = new Vector<String>();
            for (int i = 0; i < skipLines; i++) {
                in.readLine();
            }
            while (in.ready()) {
                String line = in.readLine().trim();
                if (line.length() > 0 && !line.startsWith(COMMENT_CHAR)) {
                    lines.add(line);
                }
            }
            in.close();
            int size = lines.size();
            String[][] data = new String[size][];
            for (int i = 0; i < size; i++) {
                data[i] = lines.get(i).split(splitregex);
                for (int j = 0; j < data[i].length; j++) {
                    data[i][j] = data[i][j].trim();
                }
            }
            return data;
        }

        /**
         * Parse the given CSV file (space or tab separated double values).
         * 
         * @param file
         *            the CSV file to parse
         * @return the data of the CSV file as double[][] array
         * @throws IOException
         *             if any I/O error occurs
         */
        public static double[][] parseDoubleCSV(File file) throws IOException {
            String[][] stringData = parseCSV(file, DEFAULT_REGEX, 0);
            double[][] data = new double[stringData.length][];
            for (int row = 0; row < stringData.length; row++) {
                data[row] = new double[stringData[row].length];
                for (int col = 0; col < stringData[row].length; col++) {
                    data[row][col] = Double.parseDouble(stringData[row][col]);
                }
            }
            return data;
        }

        /**
         * Writes a two-dimensional double array to the given file in a space
         * separated fashion. Files written by this method can be parsed by
         * {@link #parseDoubleCSV(File)}.
         * 
         * @param file
         *            the file to write to
         * @param content
         *            the content of the CSV file
         * @throws IOException
         *             if any I/O error occurs
         */
        public static void writeDoubleCSV(File file, double[][] content)
                throws IOException {
            PrintStream ps = new PrintStream(file);
            for (double[] row : content) {
                for (double value : row) {
                    ps.print(value + " ");
                }
                ps.println();
            }
            ps.flush();
            ps.close();
        }

        /**
         * Parses a double array from the given String. The string
         * representation consists of a list of the array's elements, enclosed
         * in square brackets (<tt>"[]"</tt>). Adjacent elements are separated
         * by the character <tt>","</tt> (a comma). Whitespace characters are
         * ignored. Elements are converted to doubles as by
         * {@link Double#parseDouble(String)}.
         * 
         * @param string
         *            the string containing a double array
         * @return the parsed double array
         * @see Arrays#toString(double[])
         */
        public static double[] parseDoubleArray(String string) {
            // remove leading/trailing spaces & braces
            string = string.trim();
            Arrays.toString(new double[0]);
            string = string.substring(1, string.length() - 1);
            // split by comma
            String[] splited = string.split(",");
            int l = splited.length;
            double[] arr = new double[l];
            for (int i = 0; i < l; i++) {
                arr[i] = Double.parseDouble(splited[i].trim());
            }
            return arr;
        }

        /**
         * Parses a two dimensional double array. The string representation
         * consists of a list of one-dimensional arrays, enclosed in square
         * brackets (<tt>"[]"</tt>) and adjacent elements are separated by the
         * character <tt>","</tt> (a comma). The one-dimensional representation
         * consists of a list of the array's elements, enclosed in square
         * brackets. Adjacent elements are separated by the character
         * <tt>","</tt>. Any whitespace characters are ignored. Elements are
         * converted to doubles as by {@link Double#parseDouble(String)}. The
         * format looks like the following.
         * 
         * <pre>
         * [[x00, x01, ...], [x10, x11, ...]]
         * </pre>
         * 
         * @param string
         *            contains a two-dimensional double array
         * @return the parsed two-dimensional double array
         * @see Arrays#deepToString(Object[])
         */
        public static double[][] parse2dDoubleArray(String string) {
            // remove whitespaces, replace the separator for the first
            // dimension, and remove leading/trailing braces
            string = string.replaceAll("\\s+", "").replaceAll("\\],", "\\];");
            string = string.substring(1, string.length() - 1);
            String[] splited = string.split(";"); // split by new separator
            double[][] arr = new double[splited.length][];
            for (int i = 0; i < splited.length; i++) {
                arr[i] = parseDoubleArray(splited[i]);
            }
            return arr;
        }

        /**
         * Copies a file. The content of the <code>source</code> file is copied
         * to the <code>destination</code> file.
         * 
         * @param source
         *            the source file
         * @param destination
         *            the destination file (created, if it does not exists)
         */
        public static void fileCopy(File source, File destination) {
            try {
                FileChannel srcChannel = new FileInputStream(source)
                        .getChannel();
                FileChannel dstChannel = new FileOutputStream(destination)
                        .getChannel();
                dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
                srcChannel.close();
                dstChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Failed to copy " + source.getName()
                        + " to " + destination.getName() + ".");
            }
        }
    }

    /**
     * Implementation of a random number generator. We're not trusting the
     * java.util.Random class :)
     * 
     * @author Martin Butz
     */
    public static class Random {

        /**
         * Constant for the random number generator (modulus of PMMLCG = 2^31
         * -1).
         */
        private final static long _M = 2147483647;

        /**
         * Constant for the random number generator (default = 16807).
         */
        private final static long _A = 16807;

        /**
         * Constant for the random number generator (=_M/_A).
         */
        private final static long _Q = _M / _A;

        /**
         * Constant for the random number generator (=_M mod _A).
         */
        private final static long _R = _M % _A;

        /**
         * The current random number value in long format.
         */
        private static long seed = 101;

        /**
         * Sets a random seed in order to randomize the pseudo random generator.
         * 
         * @param s
         *            the seed to set.
         */
        public static void setSeed(long s) {
            seed = s;
        }

        /**
         * Returns the current random number generator seed value
         * 
         * @return The RNG seed value.
         */
        public static long getSeed() {
            return seed;
        }

        /**
         * Returns a random number between zero and one.
         * 
         * @return the current random number
         */
        public static double uniRand() {
            long hi = seed / _Q;
            long lo = seed % _Q;
            long test = _A * lo - _R * hi;

            if (test > 0)
                seed = test;
            else
                seed = test + _M;

            return (double) (seed) / _M;
        }

        /**
         * Indicates if another normaly distributed random number has already
         * been generated.
         */
        private static boolean haveUniNum = false;

        /**
         * A generated uniformly distributed random number
         */
        private static double uniNum = 0;

        /**
         * Returns a normally distributed random number with mean 0 and standard
         * deviation 1.
         * 
         * @return A random number - normally distributed.
         */
        public static double normRand() {
            if (haveUniNum) {
                haveUniNum = false;
                return uniNum;
            } else {
                double x1, x2, w;
                do {
                    x1 = 2.0 * uniRand() - 1.0;
                    x2 = 2.0 * uniRand() - 1.0;
                    w = x1 * x1 + x2 * x2;
                } while (w >= 1.0);

                w = Math.sqrt((-2.0 * Math.log(w)) / w);
                uniNum = x1 * w;
                haveUniNum = true;
                return x2 * w;
            }
        }
    }
}

package xcsf.examples;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Vector;

import xcsf.FileRead;
import xcsf.Function;
import xcsf.XCSF;
import xcsf.XCSFConstants;
import xcsf.XCSFListener;
import xcsf.XCSFUtils;
import xcsf.listener.ConditionsGUI2D3D;
import xcsf.listener.OutputWriter2D;
import xcsf.listener.PerformanceGUI;
import xcsf.listener.PopulationWriter;
import xcsf.listener.PredictionErrorPlot;
import xcsf.listener.PredictionPlot;
import xcsf.listener.ProgressGUI;

/**
 * The {@link #main(String[])} method loads the <code>XCSConstants</code>,
 * functions, Visualization, creates the <code>XCSF</code> instance and
 * evaluates the functions.
 * 
 * @author Patrick Stalph
 */
public class ExampleMain {

    /**
     * The settings file for XCSF.
     */
    public final static String SETTINGS_FILE = "xcsf.ini";

    /**
     * The functions file contains informations about the functions to learn.
     */
    public final static String FUNCTION_FILE = "xcsf_functions.ini";

    /**
     * The visualization file specifies if and how visualization is used.
     */
    public final static String VISUALIZATION_FILE = "xcsf_visualization.ini";

    /**
     * If the settings and experimental results should be written out after all
     * runs.
     */
    private static boolean writeOutput = false;
    /**
     * If the final population should be written out after each run.
     */
    private static boolean writePopulation = true;

    /**
     * The file name that is used to create the output folder. If set manually,
     * make sure that the ending {@link File#separatorChar} is included.
     */
    private static String outputFolder = "output/";

    private static Vector<XCSFListener> listeners = new Vector<XCSFListener>();

    /**
     * Main method loads the <code>XCSConstants</code>, initializes functions,
     * registers listeners and starts XCSF. This method is used for the
     * executable JAR package, too.
     * 
     * @param args
     *            Available arguments include:
     *            <ul>
     *            <li><tt>-o</tt> or <tt>--writeOutput</tt><br/>
     *            Indicates, if any output is written to the file system.
     *            <li><tt>-p</tt> or <tt>--writePopulation</tt><br/>
     *            Indicates, if populations are written to the file system.
     *            <li><tt>-d 'path'</tt> or <tt>--directory 'path'</tt><br/>
     *            Specifies the <tt>path</tt> to write to.
     *            </ul>
     * @throws UnsupportedEncodingException 
     * @throws FileNotFoundException 
     */
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
    	XCSFConstants.load("xcsf.ini");
    	FileRead read = new FileRead();
    	
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--help")) {
                    printUsage();
                    return;
                } else if (args[i].equals("-o") || args[i].equals("--writeOutput")) {
                    writeOutput = true;
                } else if (args[i].equals("-p") || args[i].equals("--writePopulation")) {
                    writePopulation = true;
                } else if (args[i].equals("-d") || args[i].equals("--directory")) {
                    i++;
                    if (i == args.length)
                        throw new IllegalArgumentException("Missing argument 'path'.");
                    outputFolder = args[i];
                } else {
                    throw new IllegalArgumentException("Unknown argument: '" + args[i] + "'");
                }
            }
        }
        System.out.println("Run with --help to see " + "a list of available commands.");

        // ---[ load ini files ]---
        initSettings();
        Function[] functions = initFunctions();
        initVisualization(functions, read);
        System.out.println("");
        

        // ---[ run xcsf for every function ]----------------------------------
        for (Function f : functions) {
            XCSF xcsf = new XCSF(read);
            xcsf.addListeners(listeners);
            System.out.println(xcsf.toString());
            // run several single experiments
            xcsf.runExperiments();

            if (writeOutput) {
                String performanceFilename = outputFolder + f.getClass().getSimpleName() + "-performance";
                xcsf.writePerformance(performanceFilename);
            }
        } // done
                
        
        if (writeOutput) {
            // store settings
            File source = new File(SETTINGS_FILE);
            File dest = new File(outputFolder + SETTINGS_FILE);
            XCSFUtils.FileIO.fileCopy(source, dest);
            // store functions.ini
            source = new File(FUNCTION_FILE);
            dest = new File(outputFolder + FUNCTION_FILE);
            XCSFUtils.FileIO.fileCopy(source, dest);
        }
    }

    /**
     * Explain, how this class can be used.
     */
    private static void printUsage() {
        System.out.println("Usage: java " + ExampleMain.class.getSimpleName()
                + " [options]");
        System.out.println("The following options are available:");
        System.out.println(" -o or --writeOutput");
        System.out.println("    Indicates that performance output"
                + " is written to the filesystem.");
        System.out.println(" -p or --writePopulation");
        System.out
                .println("    Inidicates that populations are written to the filesystem.");
        System.out.println(" -d 'path' or --directory 'path'");
        System.out.println("    Secifies the path to write to.");
    }

    /**
     * Loads and parsed the settings files for xcsf.
     */
    
    private static void initSettings() {
        System.out.format("%-" + 50 + "s", "loading " + SETTINGS_FILE);
        XCSFConstants.load(SETTINGS_FILE);
        System.out.println("done");
        System.out.println(" + " + (new File(SETTINGS_FILE)).getAbsolutePath());

        // init seed for random number generator
        System.out.format("%-" + 50 + "s",
                "initializing random number generator");
        if (XCSFConstants.doRandomize) {
            XCSFUtils.Random
                    .setSeed(11 + (System.currentTimeMillis()) % 100000);
        } else {
            XCSFUtils.Random.setSeed(XCSFConstants.initialSeed);
        }
        System.out.println("done, " + (XCSFConstants.doRandomize ? "rnd-" : "")
                + "seed = " + XCSFConstants.initialSeed);

        // init output directory & writers
        if (writeOutput || writePopulation) {
            // assure correct format for outputFolder
            if (outputFolder.length() > 0) {
                if (!outputFolder.endsWith(File.pathSeparator)) {
                    outputFolder = outputFolder + File.separatorChar;
                }
                File outputDirectory = new File(outputFolder);
                System.out
                        .format("%-" + 50 + "s", "initializing output folder");
                if (!outputDirectory.exists() && !outputDirectory.mkdir()) {
                    System.err
                            .println("FATAL ERROR\nFailed to create output directory: '"
                                    + outputFolder + "'");
                    System.exit(1);
                }
                System.out.println("done");
                System.out.println(" + " + outputDirectory.getAbsolutePath());
            } else {
                System.out.println("done");
                System.out.println(" + current working directory");
            }
            if (writePopulation) {
                listeners.add(new PopulationWriter(outputFolder));
            }
        }
    }
    

    /**
     * Loads the <code>Function</code> array to evaluate.
     * 
     * @return the functions.
     */
    
    
    private static Function[] initFunctions() {
        System.out.format("%-" + 50 + "s", "loading " + FUNCTION_FILE);
        Function[] functions = null;
        try {
            FileInputStream in = new FileInputStream(FUNCTION_FILE);
            Properties p = new Properties();
            p.load(in);
            String functionNames = p.getProperty("functions");
            int dim = Integer.parseInt(p.getProperty("functionInputDimension"));
            double scale = Double.parseDouble(p.getProperty("functionScale"));
            double modifier = Double.parseDouble(p.getProperty("functionModifier"));
            double noiseDeviation = Double.parseDouble(p.getProperty("functionNormNoiseDeviation"));
            in.close();
            functions = getFunctions(functionNames, scale, modifier, noiseDeviation, dim);
            System.out.println("done, " + functions.length + " function(s)");
            for (Function f : functions) {
                System.out.println(" + " + f.getClass().getSimpleName());
            }
        } catch (Exception e) {
            File f = new File(FUNCTION_FILE);
            System.err.println("CRITICAL: Failed to load functions from");
            System.err.println("   " + f.getAbsolutePath());
            e.printStackTrace();
            System.exit(1);
        }
        return functions;
    }

    /**
     * Factory method produces a <code>Function</code> array using the given
     * parameters.
     * 
     * @param functionNames
     *            the comma-separated names of the functions.
     * @param scale
     *            the function scale.
     * @param modifier
     *            the function modifier.
     * @param noiseDeviation
     *            the (Gaussian) noise deviation.
     * @param dim
     *            the input dimension of the function.
     * @return the function.
     * @throws IllegalArgumentException
     *             if one function/dimension is not available.
     */
    private static Function[] getFunctions(String functionNames, double scale, double modifier, double noiseDeviation, int dim) {
        String[] splited = functionNames.split(",");
        int n = splited.length;
        Function[] functions = new Function[n];
        for (int i = 0; i < n; i++) {
            functions[i] = getFunction(splited[i].trim().toLowerCase(), scale, modifier, noiseDeviation, dim);
        }
        return functions;
    }

    /**
     * Factory method produces a <code>Function</code> object using the given
     * parameters.
     * 
     * @param functionName
     *            the name of the function.
     * @param scale
     *            the function scale.
     * @param modifier
     *            the function modifier.
     * @param noiseDeviation
     *            the (gaussian) noise deviation.
     * @param dim
     *            the input dimension of the function.
     * @return the function.
     * @throws IllegalArgumentException
     *             if the function/dimension is not available.
     */
    private static Function getFunction(String functionName, double scale, double modifier, double noiseDeviation, int dim) {
        String name = functionName.trim().toLowerCase();
        if (name.equals("sine")) {
            return new Sine(scale, modifier, noiseDeviation, dim);
        } else if (name.equals("radial")) {
            return new Radial(scale, modifier, noiseDeviation, dim);
        } else if (name.equals("radialsine")) {
            return new RadialSine(scale, modifier, noiseDeviation, dim);
        } else if (name.equals("linear")) {
            return new Linear(scale, modifier, noiseDeviation, dim);
        } else if (name.equals("linearimbalanced")) {
            return new LinearImbalanced(scale, modifier, noiseDeviation, dim);
        } else if (name.equals("crossedridge")) {
            return new CrossedRidge(scale, noiseDeviation, dim);
        } else if (name.equals("polynomial")) {
            return new Polynomial(scale, modifier, noiseDeviation, dim);
        } else if (name.equals("tent1")) {
            return new Tent1(scale, modifier, noiseDeviation, dim);
        } else if (name.equals("tent2")) {
            return new Tent2(scale, modifier, noiseDeviation, dim);

            // 2D functions
        } else if (name.equals("mesa") && dim == 2) {
            return new Mesa(scale, modifier, noiseDeviation);
        } else if (name.equals("sineinsine") && dim == 2) {
            return new SineInSine(scale, modifier, noiseDeviation);
        } else {
            throw new IllegalArgumentException(
                    "Function and/or dimension not available: '" + name + "' "
                            + dim + "D.");
        }
    }

    /**
     * Load the visualization, if activated.
     * 
     * @param functions
     *            the functions to be evaluated.
     */
    private static void initVisualization(Function[] functions, FileRead read) {
        System.out.format("%-" + 50 + "s", "loading " + VISUALIZATION_FILE);
        int dimension = functions[0].getConditionInputDimension();
        
        // parts that need to be added
        // dimension = read.getInputsize();
        // ************************************
        
        for (int i = 1; i < functions.length; i++) {
            if (functions[i].getConditionInputDimension() != dimension) {
                System.err.println("Inconsistent Dimensions for " + "Visualization.");
                dimension = -1;
                break;
            }
        }

        // add outputwriter, if applicable
        if (writeOutput && dimension == 2) {
            listeners.add(new OutputWriter2D(outputFolder, true, 0, 0.2, 0.2));
        }

        // add visualization
        try {
            FileInputStream in = new FileInputStream(VISUALIZATION_FILE);
            Properties p = new Properties();
            p.load(in);

            boolean doVisualization = Boolean.parseBoolean(p
                    .getProperty("doVisualization"));
            String gnuplotExe = p.getProperty("gnuplotExecutable");
            if (!gnuplotExe.toLowerCase().equals("default")) {
                XCSFUtils.Gnuplot.executable = gnuplotExe;
            }
            if (doVisualization) {
                ConditionsGUI2D3D.slowMotion = Boolean.parseBoolean(p
                        .getProperty("slowMotion"));
                ConditionsGUI2D3D.visualizationSteps = Integer.parseInt(p
                        .getProperty("updateVisualizationSteps"));
                ConditionsGUI2D3D.visualizedConditionSize = Float.parseFloat(p
                        .getProperty("relativeVisualizedConditionSize"));
                ConditionsGUI2D3D.visualizationTransparency = Float
                        .parseFloat(p
                                .getProperty("visualizationTransparencyDegree"));
                ConditionsGUI2D3D.visualizationDelay = Integer.parseInt(p
                        .getProperty("visualizationDelay"));
                /*
                 * Add the listeners. Performance for all dimensions, no
                 * preconditions. ConditionsGIO for 2D (always) and 3D, if
                 * Java3D is installed. PredictionPlot for 2D, if GnuPlot is
                 * installed.
                 */
                listeners.add(new PerformanceGUI(true));
                listeners.add(new ProgressGUI(true));
                if (dimension == 2) {
                    listeners.add(new ConditionsGUI2D3D());
                    try {
                        listeners.add(new PredictionErrorPlot());
                        listeners.add(new PredictionPlot());
                    } catch (IOException e) {
                        System.err.println("Failed to execute GnuPlot!");
                    }
                } else if (dimension == 3) {
                    // check for Java3D
                    try {
                        XCSF.class.getClassLoader().loadClass(
                                "com.sun.j3d.utils.universe.SimpleUniverse");
                        listeners.add(new ConditionsGUI2D3D());
                    } catch (ClassNotFoundException e) {
                        System.err.println("Java3D is not installed! ");
                    }
                }
            }
            in.close();
        } catch (IOException e) {
            File f = new File(VISUALIZATION_FILE);
            System.err.println("Failed to load visualization settings from");
            System.err.println("   " + f.getAbsolutePath());
        }
        System.out.println("done");
    }
}

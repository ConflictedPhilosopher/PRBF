package xcsf.listener;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import xcsf.MatchSet;
import xcsf.Population;
import xcsf.StateDescriptor;
import xcsf.XCSFConstants;
import xcsf.XCSFListener;
import xcsf.XCSFUtils;
import xcsf.XCSFUtils.Gnuplot;

/**
 * Writes output for two-dimensional functions. In particular, a screenshot of
 * the population of conditions is stored to a file and the prediction is
 * plotted using gnuplot.
 * 
 * @author Patrick Stalph
 */
public class OutputWriter2D implements XCSFListener {

    // Image size: 800 x 800 pixels
    private final static int IMAGE_SIZE = 800;
    private final static String EXTENSION = "png";

    private boolean onlyFirstExp;
    private int interval;
    private double relativeConditionSize;
    private double transparency;
    private int exp;
    private String path;
    private String name;
    private String tmpFilename;

    /**
     * Default constructor.
     * 
     * @param path
     *            Specifies the path to write to. If this is <tt>null</tt> or
     *            <tt>""</tt>, then files are written to the current path.
     * @param onlyFirstExperiment
     *            Specifies that the listener should create a screenshot(s) for
     *            the first experiment only or for every experiment.
     * @param interval
     *            Specifies how often the listener should take screenshots. An
     *            interval of 0 indicates that only one screenshot at the end of
     *            the experiment should be taken.
     * @param relativeConditionSize
     *            the relative condition size
     * @param transparency
     *            the transparency of the conditions
     */
    public OutputWriter2D(String path, boolean onlyFirstExperiment,
            int interval, double relativeConditionSize, double transparency) {
        this.path = XCSFUtils.checkPath(path);
        this.onlyFirstExp = onlyFirstExperiment;
        if (interval > 0) {
            // screenshot every interval
            this.interval = interval;
        } else {
            // screenshot at the end of run
            this.interval = XCSFConstants.maxLearningIterations;
        }
        this.relativeConditionSize = relativeConditionSize;
        this.transparency = transparency;
        try {
            File tmpfile = File.createTempFile(this.getClass().getSimpleName(),
                    "gnuplot");
            tmpfile.deleteOnExit();
            tmpFilename = tmpfile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.XCSFListener#nextExperiment(int, java.lang.String)
     */
    public void nextExperiment(int experiment, String functionName) {
        this.exp = experiment;
        this.name = functionName;
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.XCSFListener#stateChanged(int, xcsf.Population, xcsf.MatchSet,
     * xcsf.StateDescriptor, double[][])
     */
    public void stateChanged(int iteration, Population population,
            MatchSet matchSet, StateDescriptor state, double[][] performance) {
        if (iteration % interval != 0) {
            return;
        }
        if (this.onlyFirstExp && this.exp != 0) {
            return;
        }
        int dim = population.get(0).getCondition().getCenter().length;
        if (dim != 2) {
            return;
        }

        // ok... there is no way around: prepare your camera!
        String filename = this.path + this.name;
        if (!this.onlyFirstExp) {
            String expString;
            if (this.exp < 10) {
                expString = "0" + this.exp;
            } else {
                expString = "" + this.exp;
            }
            filename += expString;
        }
        if (this.interval != XCSFConstants.maxLearningIterations) {
            filename += "-" + iteration;
        }
        this.createConditionImage2D(population, matchSet, state, filename);
        this.createPredictionPlot(population, iteration, filename);
    }

    /**
     * Creates the population plot.
     * 
     * @param population
     *            the population
     * @param matchSet
     *            current matchset
     * @param state
     *            current state
     * @param filename
     *            the filename to write to
     */
    private void createConditionImage2D(Population population,
            MatchSet matchSet, StateDescriptor state, String filename) {
        // 1. store the settings of the gui
        float guiTransparency = ConditionsGUI2D3D.visualizationTransparency;
        float guiSize = ConditionsGUI2D3D.visualizedConditionSize;
        // 2. settings for the photo
        ConditionsGUI2D3D.visualizationTransparency = Math.max(Math.min(
                (float) this.transparency, 1), 0);
        ConditionsGUI2D3D.visualizedConditionSize = Math.max(Math.min(
                (float) this.relativeConditionSize, 1), 0);
        // 3. smiiiile
        BufferedImage img = ConditionsGUI2D3D.Visualization2D.createImage2D(
                population, matchSet, state, false, IMAGE_SIZE);
        filename += "-pop";
        try {
            ImageIO.write(img, EXTENSION, new File(filename + "." + EXTENSION));
        } catch (IOException e) {
            System.err.println(this.getClass().getSimpleName() + ": "
                    + e.getMessage());
        }
        // 4. restore settings
        ConditionsGUI2D3D.visualizationTransparency = guiTransparency;
        ConditionsGUI2D3D.visualizedConditionSize = guiSize;
    }

    /**
     * Creates the prediction plot.
     * 
     * @param population
     *            the population
     * @param iteration
     *            the current iteration
     * @param filename
     *            the filename to write to
     */
    private void createPredictionPlot(Population population, int iteration,
            String filename) {
        filename += "-pred";
        try {
            // setup stuff
            Gnuplot gnuplot = new Gnuplot();
            for (String cmd : PredictionPlot.GNUPLOT_CMD) {
                gnuplot.execute(cmd);
            }
            PredictionPlot.createIsoamples(population, tmpFilename);
            gnuplot.execute("set term postscript eps enhanced color");
            gnuplot.execute("set out '" + filename + ".eps'");
            gnuplot.execute("set title 'iteration " + iteration + "'");
            gnuplot.execute("splot '" + tmpFilename + "' title 'prediction'");
            gnuplot.execute("save '" + filename + ".plt'");
            gnuplot.close();

            XCSFUtils.FileIO.fileCopy(new File(tmpFilename), new File(filename
                    + ".dat"));
        } catch (IOException e) {
            // gnuplot not installed
        }
    }
}

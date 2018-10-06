package xcsf.listener;

import java.io.File;
import java.io.FileNotFoundException;

import xcsf.MatchSet;
import xcsf.Population;
import xcsf.StateDescriptor;
import xcsf.XCSFConstants;
import xcsf.XCSFListener;
import xcsf.XCSFUtils;

/**
 * Writes populations to the filesystem, either in a comma separated fashion or
 * using {@link Population#writePopulation(File)}.
 * 
 * @author Patrick Stalph
 */
public class PopulationWriter implements XCSFListener {

    private int interval;
    private int exp;
    private String path;
    private String name;

    /**
     * Default constructor.
     * 
     * @param path
     *            the path to write files to
     */
    public PopulationWriter(String path) {
        this(path, -1);
    }

    /**
     * Alternative constructor.
     * 
     * @param path
     *            the path to write files to
     * @param interval
     *            the interval in which files are written
     */
    public PopulationWriter(String path, int interval) {
        this.path = XCSFUtils.checkPath(path);
        this.interval = interval;
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
        if (interval == -1 && iteration != XCSFConstants.maxLearningIterations) {
            return;
        } else if (iteration % interval != 0) {
            return;
        }
        // build filename
        String expString, itString = String.valueOf(iteration / 1000);
        if (this.exp < 10) {
            expString = "0" + this.exp;
        } else {
            expString = "" + this.exp;
        }
        while (itString.length() < String.valueOf(
                XCSFConstants.maxLearningIterations / 1000).length()) {
            itString = "0" + itString;
        }
        String filename = this.path + this.name + "-exp" + expString + "-it"
                + itString + "k";
        // write to file
        try {
            population.writePopulation(new File(filename + ".population"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}

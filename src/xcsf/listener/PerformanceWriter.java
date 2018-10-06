package xcsf.listener;

import xcsf.MatchSet;
import xcsf.Population;
import xcsf.StateDescriptor;
import xcsf.XCSF;
import xcsf.XCSFConstants;
import xcsf.XCSFListener;
import xcsf.XCSFUtils;

/**
 * Writes XCSF's performance to the filesystem.
 * 
 * @author Patrick Stalph
 */
public class PerformanceWriter implements XCSFListener {

    private XCSF xcsfLink;
    private int exp;
    private String path;
    private String name;

    /**
     * Default constructor.
     * 
     * @param path
     *            the path to write files to
     * @param xcsfLink
     *            reference to XCSF in order to retrieve performance more easily
     */
    public PerformanceWriter(String path, XCSF xcsfLink) {
        this.path = XCSFUtils.checkPath(path);
        this.xcsfLink = xcsfLink;
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
        if (this.exp != XCSFConstants.numberOfExperiments - 1
                || iteration != XCSFConstants.maxLearningIterations) {
            return;
        }
        String performanceFilename = this.path + this.name + "-performance";
        this.xcsfLink.writePerformance(performanceFilename);
    }

}

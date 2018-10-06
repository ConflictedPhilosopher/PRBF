package xcsf.listener;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import xcsf.MatchSet;
import xcsf.Population;
import xcsf.StateDescriptor;
import xcsf.XCSFConstants;
import xcsf.XCSFListener;
import xcsf.XCSFUtils.Gnuplot;
import xcsf.classifier.Classifier;

/**
 * Visualizes the fitness-weighted prediction error in the population.
 * 
 * @author Patrick Stalph
 */
public class PredictionErrorPlot implements XCSFListener {

    // 21 tics, interval [0:1] - if computed via for-loop: precision problems!
    private final static double[] TICS = { 0, .05, .1, .15, .2, .25, .3, .35,
            .4, .45, .5, .55, .6, .65, .7, .75, .8, .85, .9, .95, 1 };

    // initial gnuplot commands
    final static String[] GNUPLOT_CMD = { "set grid", //
            "set xrange[0:1]", //
            "set yrange[0:1]", //
            "set logscale z",//
            "set xlabel 'x'", //
            "set ylabel 'y'", //
            "set zlabel 'p.err.'", //
            "set style data lines", //
            "set contour", //
            "set surface", //
            "set hidden3d", //
            "set dgrid3d " + TICS.length + "," + TICS.length, //
    };

    private MatchSet ms = new MatchSet(false);
    private double[][][] samples = new double[TICS.length][TICS.length][3];

    private Gnuplot console;
    private String tmpFilename;

    /**
     * Default constructor.
     * 
     * @throws IOException
     *             if gnuplot cannot be instantiated
     * 
     */
    public PredictionErrorPlot() throws IOException {
        this.console = new Gnuplot();
        for (String cmd : GNUPLOT_CMD) {
            this.console.execute(cmd);
        }
        File tmpfile = File.createTempFile(this.getClass().getSimpleName(),
                "gnuplot");
        tmpfile.deleteOnExit();
        tmpFilename = tmpfile.getAbsolutePath();
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.XCSFListener#nextExperiment(int, java.lang.String)
     */
    public void nextExperiment(int experiment, String functionName) {
        // ignore
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.XCSFListener#stateChanged(int, xcsf.Population, xcsf.MatchSet,
     * xcsf.StateDescriptor, double[][])
     */
    public void stateChanged(int iteration, Population population,
            MatchSet matchSet, StateDescriptor state, double[][] performance) {
        if (iteration % XCSFConstants.averageExploitTrials != 0
                || state.getConditionInput().length != 2) {
            return;
        }
        // create 2D samples
        createIsoSamples(population);
        // plot
        this.console.execute("set title 'iteration " + iteration + "'");
        this.console.execute("splot '" + tmpFilename
                + "' using 1:2:3 title 'avg prediction error'");
    }

    /**
     * Calculates isosamples for use with gnuplot.
     * 
     * @param population
     *            the population
     */
    private void createIsoSamples(Population population) {
        // calculate samples by matching all 441 states...
        int n = TICS.length;
        double[] output = new double[1];
        try {
            PrintStream ps = new PrintStream(tmpFilename);
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    double[] input = { TICS[x], TICS[y] };
                    StateDescriptor state = new StateDescriptor(input, output);
                    ms.match(state, population);
                    if (ms.size() == 0) {
                        ms.setNumClosestMatching(true);
                        ms.match(state, population);
                        ms.setNumClosestMatching(false);
                    }
                    samples[x][y][0] = input[0];
                    samples[x][y][1] = input[1];
                    samples[x][y][2] = getAvgPredictionError();
                    ps.println(samples[x][y][0] + " " + samples[x][y][1] + " "
                            + samples[x][y][2]);
                }
            }
            ps.flush();
            ps.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Computes the fitness-weighted prediction error in the current matchset.
     * 
     * @return the fitness-weighted prediction error in the matchset
     */
    private double getAvgPredictionError() {
        double avg = 0, fitnesssum = 0;
        for (Classifier cl : ms) {
            fitnesssum += cl.getFitness();
            avg += cl.getPredictionError() * cl.getFitness();
        }
        return avg / fitnesssum;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#finalize()
     */
    protected void finalize() throws Throwable {
        this.console.close();
        super.finalize();
    }
}

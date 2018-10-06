/*
 * created on Oct 14, 2009
 */
package xcsf.examples;

import xcsf.Function;
import xcsf.XCSF;
import xcsf.XCSFConstants;
import xcsf.Function.BlockingFunction;
import xcsf.listener.PerformanceGUI;
import xcsf.listener.ProgressGUI;

/**
 * This class shows an example of multi-threaded use of XCSF for distributed
 * online learning.
 * 
 * @author Patrick Stalph
 */
public class ExampleMultiThreading {

    final static Function SINE = new Sine(1, 4, 0, 2);

    /**
     * Examplary implementation of the BlockingFunction class that simply
     * forwards to the sine function.
     * 
     * @author Patrick Stalph
     */
    static class MyFunction extends BlockingFunction {
        public int getPredictionInputDimension() {
            return SINE.getPredictionInputDimension();
        }

        public int getOutputDimension() {
            return SINE.getOutputDimension();
        }

        public double[] getNoiselessFunctionValue() {
            return SINE.getNoiselessFunctionValue();
        }

        public int getConditionInputDimension() {
            return SINE.getConditionInputDimension();
        }
    }

    /**
     * Main method starts two threads, one for XCSF and one for the function.
     * 
     * @param args
     *            not used.
     */
    public static void main(String[] args) {
        final BlockingFunction f = new MyFunction();
        XCSFConstants.load();
        final XCSF xcsf = new XCSF(f);
        xcsf.addListener(new PerformanceGUI(true));
        xcsf.addListener(new ProgressGUI(true));

        Thread xcsfThread = new Thread(new Runnable() {
            /*
             * (non-Javadoc)
             * 
             * @see java.lang.Runnable#run()
             */
            public void run() {
                xcsf.runExperiments();
            }
        });

        Thread functionThread = new Thread(new Runnable() {
            /*
             * (non-Javadoc)
             * 
             * @see java.lang.Runnable#run()
             */
            public void run() {
                for (int i = 0; i < XCSFConstants.maxLearningIterations; i++) {
                    // wait for random amount
                    try {
                        Thread.sleep((long) (Math.random() * 10));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.print(".");
                    if (i % 80 == 0) {
                        System.out.println();
                    }

                    // now make a new sample available to XCSF
                    f.addProblemInstance(SINE.nextProblemInstance());
                }
            }
        });

        xcsfThread.start();
        functionThread.start();
    }
}

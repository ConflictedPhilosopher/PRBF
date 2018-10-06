package xcsf.listener;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import xcsf.MatchSet;
import xcsf.Population;
import xcsf.StateDescriptor;
import xcsf.XCSFConstants;
import xcsf.XCSFListener;

/**
 * Implements a {@link JProgressBar} linked to XCSF's current progress, that is
 * the current iteration relative to the total number of iterations. This is
 * especially usefull, when calling XCSF from a GUI and the actual progress is
 * of interest. This class extends {@link javax.swing.JPanel}, thus it can be
 * embedded in any other GUI. However, the constructor can be instructed to
 * create a {@link JFrame} that makes this class a stand-alone GUI.
 * 
 * @author Patrick Stalph
 */
public class ProgressGUI extends JPanel implements XCSFListener {

    private static final long serialVersionUID = 4032546909186449011L;

    private JProgressBar progressBar;

    /**
     * Default constructor.
     * 
     * @param createJFrame
     *            Indicates if this constructor shall create a
     *            <code>JFrame</code> with this object as its content.
     */
    public ProgressGUI(boolean createJFrame) {
        super();
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        super.add(this.progressBar);

        if (createJFrame) {
            JFrame f = new JFrame("Progress");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(this);
            f.pack();
            f.setResizable(false);
            f.setLocation(200, 300);
            f.setVisible(true);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.XCSFListener#nextExperiment(int, java.lang.String)
     */
    public void nextExperiment(int experiment, String functionName) {
        this.progressBar.setValue(0);
    }

    /*
     * (non-Javadoc)
     * 
     * @see xcsf.XCSFListener#stateChanged(int, xcsf.Population, xcsf.MatchSet,
     * xcsf.StateDescriptor, double[][])
     */
    public void stateChanged(int iteration, Population population,
            MatchSet matchSet, StateDescriptor state, double[][] performance) {
        int progress = (int) (100.0 * iteration / XCSFConstants.maxLearningIterations);
        if (progress == progressBar.getValue()) {
            return;
        }
        progressBar.setValue(progress);
    }
}

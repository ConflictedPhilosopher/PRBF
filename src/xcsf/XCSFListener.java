package xcsf;

/**
 * This interface can be implemented to observe <code>Population</code> and
 * <code>MatchSet</code> changes in <code>XCSF</code>. This is especially useful
 * for visualization purposes as well as performance logging.
 * 
 * @author Patrick O. Stalph
 */
public interface XCSFListener {

    /**
     * This method indicates that a new experiment has begun.
     * 
     * @param experiment
     *            the index of the following experiment, starting at <tt>0</tt>
     *            and ending at {@link XCSFConstants#numberOfExperiments}-1
     * @param functionName
     *            the name of the function to be learned
     */
    public void nextExperiment(int experiment, String functionName);

    /**
     * This method is called once per iteration of <code>XCSF</code>, just
     * before the next iteration starts.
     * 
     * @param iteration
     *            the current iteration of xcsf, starting at <tt>1</tt> and
     *            ending at {@link XCSFConstants#maxLearningIterations}
     * @param population
     *            the current population
     * @param matchSet
     *            the current matchset
     * @param state
     *            the current function sample
     * @param performance
     *            the performance for the current experiment as returned from
     *            {@link PerformanceEvaluator#getCurrentExperimentPerformance()}
     * 
     */
    public void stateChanged(int iteration, Population population,
            MatchSet matchSet, StateDescriptor state, double[][] performance);
}

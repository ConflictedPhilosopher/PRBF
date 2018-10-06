package xcsf;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Vector;

//import xcsf.classifier.FileRead;
import xcsf.classifier.PredictionLinearRLS;
import xcsf.classifier.PredictionQuadraticRLS;

/**
 * The main XCSF class coordinates the learning process and listeners. A typical
 * use case looks as simple as this:
 * 
 * <pre>
 * // load your function, implementing the xcsf.functions.Function interface
 * Function f = new Sine(1, 4, 0, 2);
 * // load XCSF settings from a file
 * XCSFConstants.load(&quot;xcsf.ini&quot;);
 * // create the XCSF instance
 * XCSF xcsf = new XCSF(f);
 * // add some listeners for performance monitoring or visualization
 * xcsf.addListener(new PerformanceGUI(true));
 * // start the experiments
 * xcsf.runExperiments();
 * </pre>
 * 
 * @author Patrick O. Stalph, Martin V. Butz, Shabnam Nazmi
 * @see xcsf.Function
 * @see xcsf.XCSFListener
 */
public class XCSF {

    // the function to approximate
    private Function function;
    // list of registered listeners
    private Vector<XCSFListener> listeners = new Vector<XCSFListener>();
    // keeps track of learning performance, including avg. error and pop. size
    private PerformanceEvaluator performanceEvaluator;
	private xcsf.FileRead read;
    
    /**
     * XCSF constructor creates the xcsf instance using the given
     * <code>function</code>.
     * 
     * @param function
     *            the function to be learned
     */
    public XCSF(FileRead read ) {
        //this.function = function;
        this.read = read;
    }

    /**
     * Convenience constructor that also loads XCSF's settings from the given
     * <code>settingsFilename</code>.
     * 
     * @param function
     *            the function to be learned
     * @param settingsFilename
     *            the filename of XCSF's settings file to be loaded by
     *            {@link XCSFConstants#load(String)}.
     */
    public XCSF(FileRead read, String settingsFilename) {
        this(read);
        XCSFConstants.load(settingsFilename);
    }

    /**
     * This method starts all experiments by calling
     * {@link #runSingleExperiment()} for
     * {@link XCSFConstants#numberOfExperiments} times. Furthermore, in verbose
     * mode some informations about the progress are printed to
     * <code>System.out</code>.
     * @throws UnsupportedEncodingException 
     * @throws FileNotFoundException 
     */
    public void runExperiments() throws FileNotFoundException, UnsupportedEncodingException {
    	Boolean isTrain = true;
    	this.read.loadData(isTrain);
    	
        XCSFUtils.println("");
        this.performanceEvaluator = new PerformanceEvaluator();
        // run several single experiments
        for (int exp = 0; exp < XCSFConstants.numberOfExperiments; exp++) {
            // listeners: indicate next experiment
            for (XCSFListener l : listeners) {
                l.nextExperiment(exp, "myData");
            }

            if (XCSFConstants.numberOfExperiments > 1) {
                XCSFUtils.print(" > " + "myData" + ", Experiment " + (exp + 1) + "/" + XCSFConstants.numberOfExperiments, 60);
            } else {
                XCSFUtils.println("Running PRBF...");
            }
            long time = System.currentTimeMillis();

            // start xcsf single run
            Population pop = this.runSingleExperiment();
            
            //this.testModel(pop);

            time = (System.currentTimeMillis() - time) / 1000;
            XCSFUtils.println("done in " + (int) (time / 60) + "m " + (time % 60) + "s");
        }
    }

    /**
     * This method starts one experiment for
     * {@link XCSFConstants#maxLearningIterations} and returns the final
     * population.
     * 
     * @return the final population
     * @throws UnsupportedEncodingException 
     * @throws FileNotFoundException 
     */
    public Population runSingleExperiment() throws FileNotFoundException, UnsupportedEncodingException {    	
        return runSingleExperiment(new Population());
    }

    /**
     * This method starts one experiment for
     * {@link XCSFConstants#maxLearningIterations} using the given initial
     * <code>population</code>.
     * 
     * @param population
     *            the initial population
     * @return the final population, which is a reference to the given
     *         <tt>population</tt>
     * @throws UnsupportedEncodingException 
     * @throws FileNotFoundException 
     */
    public Population runSingleExperiment(Population population) throws FileNotFoundException, UnsupportedEncodingException {
        if (this.performanceEvaluator == null) {
            this.performanceEvaluator = new PerformanceEvaluator();
        }
        MatchSet matchSet = new MatchSet(XCSFConstants.doNumClosestMatch, XCSFConstants.multiThreading);
        EvolutionaryComp evolutionaryComponent = new EvolutionaryComp();
        this.performanceEvaluator.nextExperiment();
        
        // -----[ main loop ]-----
        for (int iteration = 1; iteration <= XCSFConstants.maxLearningIterations; iteration++) {
            // 1) get next problem instance            
             this.read.getInstance();
             StateDescriptor State = new StateDescriptor(this.read.getInstanceX(), this.read.getInstanceY());
                        
            // 2) match & cover if necessary
            matchSet.match(State, population);// most computational time here
            matchSet.ensureStateCoverage(population, iteration);
            // 3) evaluate performance
            double[] functionPrediction = matchSet.getWeightedPrediction();                               
          
            double[] funcValue = read.getInstanceY();
            
            this.performanceEvaluator.evaluate(population, matchSet, iteration, funcValue, functionPrediction);
            // 4) update matching classifiers
            matchSet.updateClassifiers();
            // 5) evolution
            if (iteration <= XCSFConstants.maxLearningIterations-read.getDataSize()) {
            	evolutionaryComponent.evolve(population, matchSet, State, iteration);
            }

            // inform listeners
            if (!listeners.isEmpty()) {
                double[][] performance = this.performanceEvaluator.getCurrentExperimentPerformance();
                for (XCSFListener l : listeners) {
                    l.stateChanged(iteration, population, matchSet, State, performance);
                }
            }

            // reset rls prediction at next iteration?
            if ((XCSFConstants.predictionType.equalsIgnoreCase(PredictionLinearRLS.class.getName()) || XCSFConstants.predictionType
                    .equalsIgnoreCase(PredictionQuadraticRLS.class.getName())) && iteration + 1 == 
                    (int) (XCSFConstants.resetRLSPredictionsAfterSteps * XCSFConstants.maxLearningIterations)) {
                for (int i = 0; i < population.size; i++) {
                    ((PredictionLinearRLS) population.elements[i].getPrediction()).resetGainMatrix();
                }
            }

            // start compaction at next iteration?
            if (iteration + 1 == (int) (XCSFConstants.startCompaction * XCSFConstants.maxLearningIterations)) {
                evolutionaryComponent.setCondensation(true);
                if (XCSFConstants.compactionType % 2 == 1) {
                    // type 1 & 3
                    matchSet.setNumClosestMatching(true);
                }
                if (XCSFConstants.compactionType >= 2) {
                    // type 2 & 3
                    population.applyGreedyCompaction();
                }
            }
        } // ---[ end loop ]------
        
        
        /**
         * PRBF method:
         * Evaluates the trained model against the training data and reports the average possibility
         * prediction error as well as the crisp label prediction error.
         * 
         * Crisp label predictions are reported in a text file.
         */
        
        System.out.println("Starting the PRBF population evaluation on training data...");
        Boolean isTrain = true;
    	this.read.loadData(isTrain);
        XCSFConstants cons = new XCSFConstants();
        cons.setAvgExploitTrials(this.read.getDataSize());
        double[] error = new double[this.read.getOutputsize()];
        double[] errorPi = new double[this.read.getOutputsize()];
        int crispDecision;
        int correcPrediction = 0;
        PrintWriter writer = new PrintWriter("Class_prediction_train.txt", "UTF-8");
        writer.println("Correct\tPredicted");
        
        DecimalFormat df = new DecimalFormat("#.###");
        
        int noMatchCount = 0;
        for (int iteration = 0; iteration < this.read.getDataSize(); iteration++) {       	       	
        	this.read.getInstance();
            StateDescriptor State = new StateDescriptor(this.read.getInstanceX(), this.read.getInstanceY());
            matchSet.match(State, population);

            if (matchSet.size == 0) {
            	//System.out.println("no match!");
            	noMatchCount ++;
            	continue;            	
            }else {
            	double[] functionPrediction = matchSet.getWeightedPrediction();
            	
            	for (int i = 0; i < functionPrediction.length; i++) {
            		if (functionPrediction[i] < 0) {
            			functionPrediction[i] = 0.0;
            		}
            		if (functionPrediction[i] > 1) {
            			functionPrediction[i] = 1.0;
            		}
            	}
                
                matchSet.calculateFusedPrediction();
                double[] intersectPrediction = matchSet.getIntersectPrediction();
                double[] unionPrediction = matchSet.getUnionPrediction();
                double consistencyIdx = matchSet.getConsistencyIdx();
                double[] fusedPrediction;
                
                if (consistencyIdx < 0.1) {
                	System.out.println("Inconsistent sources...switches to union combination.");
                	fusedPrediction = unionPrediction;
                }
                else {
                	fusedPrediction = intersectPrediction;
                }
                crispDecision = matchSet.maxValue(fusedPrediction);
                if (crispDecision == this.read.getInstanceLabel()) {
                	correcPrediction ++;
                }
                writer.print(Integer.toString(this.read.getInstanceLabel()));
                writer.print("\t");
                writer.println(Integer.toString(crispDecision));


                double[] funcValue = read.getInstanceY();
                            
                for (int it = 0;it < this.read.getOutputsize(); it++) {
                	error[it] += Math.abs(functionPrediction[it] - funcValue[it]);
                	errorPi[it] += Math.abs(fusedPrediction[it] - funcValue[it]);
                }
            }           
        }
        
        double aveError = 0;
        double aveErrorPi = 0;
        double accuracy = 0;
        
        for (int it = 0; it < this.read.getOutputsize(); it++) {
        	aveError += error[it];
        	aveErrorPi += errorPi[it];
        }
        aveError /= this.read.getDataSize();
        aveErrorPi /= this.read.getDataSize();
        accuracy = (double) correcPrediction/this.read.getDataSize();
        //System.out.println(aveError);
        //System.out.println(aveErrorPi);
        System.out.println("Training accuracy: ");
        System.out.println(accuracy);
        System.out.println("No match count: ");
        System.out.println(noMatchCount);
        
        writer.close();
        
        /**
         * PRBF method:
         * Evaluates the trained model against the test data and reports the average possibility
         * prediction error as well as the crisp label prediction error.
         * 
         * Crisp label predictions are reported in a text file.
         */
        
        System.out.println("Starting the PRBF population evaluation on test data...");
        isTrain = false;
    	this.read.loadData(isTrain);
        cons.setAvgExploitTrials(this.read.getDataSize());
        error = new double[this.read.getOutputsize()];
        errorPi = new double[this.read.getOutputsize()];
        //int crispDecision;
        correcPrediction = 0;
        PrintWriter writer2 = new PrintWriter("Class_prediction_test.txt", "UTF-8");
        writer2.println("Correct Class\tPredicted Class");
        String formatted;
        
        noMatchCount = 0;
        for (int iteration = 0; iteration < this.read.getDataSize(); iteration++) {       	       	
        	this.read.getInstance();
            StateDescriptor State = new StateDescriptor(this.read.getInstanceX(), this.read.getInstanceY());
            matchSet.match(State, population);

            if (matchSet.size == 0) {
            	//System.out.println("no match!");
            	noMatchCount ++;
            	continue;            	
            }else {
            	double[] functionPrediction = matchSet.getWeightedPrediction();
            	
            	for (int i = 0; i < functionPrediction.length; i++) {
            		if (functionPrediction[i] < 0) {
            			functionPrediction[i] = 0.0;
            		}
            		if (functionPrediction[i] > 1) {
            			functionPrediction[i] = 1.0;
            		}
            	}
                
                matchSet.calculateFusedPrediction();
                double[] intersectPrediction = matchSet.getIntersectPrediction();
                double[] unionPrediction = matchSet.getUnionPrediction();
                double consistencyIdx = matchSet.getConsistencyIdx();
                double[] fusedPrediction;
                
                if (consistencyIdx < 0.1) {
                	System.out.println("Inconsistent sources...switches to union combination.");
                	fusedPrediction = unionPrediction;
                }
                else {
                	fusedPrediction = intersectPrediction;
                }
                crispDecision = matchSet.maxValue(fusedPrediction);
                if (crispDecision == this.read.getInstanceLabel()) {
                	correcPrediction ++;
                }
                writer2.print(Integer.toString(this.read.getInstanceLabel()));
                writer2.print("\t");
                writer2.print(Integer.toString(crispDecision));
                writer2.print("\t[");
                formatted = df.format(fusedPrediction[0]);
                writer2.print(formatted);
                writer2.print(", ");
                formatted = df.format(fusedPrediction[1]);
                writer2.print(formatted);
                writer2.println("]");

                double[] funcValue = read.getInstanceY();
                            
                for (int it = 0;it < this.read.getOutputsize(); it++) {
                	error[it] += Math.abs(functionPrediction[it] - funcValue[it]);
                	errorPi[it] += Math.abs(fusedPrediction[it] - funcValue[it]);
                }
            }           
        }
        
        aveError = 0;
        aveErrorPi = 0;
        accuracy = 0;
        
        for (int it = 0; it < this.read.getOutputsize(); it++) {
        	aveError += error[it];
        	aveErrorPi += errorPi[it];
        }
        aveError /= this.read.getDataSize();
        aveErrorPi /= this.read.getDataSize();
        accuracy = (double) correcPrediction/this.read.getDataSize();
        //System.out.println(aveError);
        System.out.println("Pi estimation error:");
        System.out.println(aveErrorPi);
        System.out.println("Test accuracy: ");
        System.out.println(accuracy);
        System.out.println("No match count: ");
        System.out.println(noMatchCount);
        
        writer2.close();

        // make sure that child threads are closed.
        try {
            matchSet.shutDownThreads();
            matchSet = null;
        } catch (Throwable e) {
            // ignore
        }
        return population;
    }

    
    public void testModel(Population population) {
    	System.out.println("Running test script...");

    	Boolean isTrain = false;
    	this.read.loadData(isTrain);
    	
    	MatchSet matchSet = new MatchSet(XCSFConstants.doNumClosestMatch, XCSFConstants.multiThreading);
    	this.performanceEvaluator = new PerformanceEvaluator();
    	this.performanceEvaluator.setTestPredErrorSize(read.getDataSize(), read.getOutputsize());

    	
    	for (int iteration = 0; iteration <= read.getDataSize()-1; iteration++) {
    		this.read.getInstance();
            StateDescriptor State = new StateDescriptor(this.read.getInstanceX(), this.read.getInstanceY());
            double[] funcValue = read.getInstanceY();
            
            matchSet.match(State, population);
            double[] functionPrediction = matchSet.getWeightedPrediction();
                                    
            if ((XCSFConstants.predictionType.equalsIgnoreCase(PredictionLinearRLS.class.getName()) || XCSFConstants.predictionType
                    .equalsIgnoreCase(PredictionQuadraticRLS.class.getName())) && iteration + 1 == 
                    (int) (XCSFConstants.resetRLSPredictionsAfterSteps * XCSFConstants.maxLearningIterations)) {
                for (int i = 0; i < population.size; i++) {
                    ((PredictionLinearRLS) population.elements[i].getPrediction()).resetGainMatrix();
                }
            }
            
            if (!listeners.isEmpty()) {
            	this.performanceEvaluator.testPredictionErr(iteration, funcValue, functionPrediction);
            }
       	}
    	
    	double[] testError = this.performanceEvaluator.testPerformance(read.getDataSize());
    	System.out.println(testError[0] + testError[1]);
    }
    
    
    /**
     * Returns mean and variance values of the final iteration represented in a
     * tab-separated String.
     * 
     * @return the performance of the final iteration
     */
    public String getFinalPerformance() {
        return this.performanceEvaluator.getAvgFinalPerformance();
    }

    /**
     * Writes the performance to the given <code>filename</code>.
     * 
     * @param filename
     *            the file to write to
     */
    public void writePerformance(String filename) {
        this.performanceEvaluator.writeAvgPerformance(filename);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        StringBuffer sb = new StringBuffer(100);
        sb.append("PRBF on ");
        sb.append( "myData" );
        sb.append(" in ");
        sb.append(XCSFConstants.Inputsize);
        sb.append("-D");
        sb.append(", settings:");
        sb.append(System.getProperty("line.separator"));
        sb.append(" * max ");
        sb.append(XCSFConstants.maxPopSize);
        sb.append(" CLs for ");
        sb.append(XCSFConstants.maxLearningIterations);
        sb.append(" iterations, target error=");
        sb.append(XCSFConstants.epsilon_0);
        sb.append(System.getProperty("line.separator"));
        sb.append(" * ");
        sb.append(XCSFConstants.conditionType
                .substring(1 + XCSFConstants.conditionType.lastIndexOf('.')));
        sb.append("/");
        sb.append(XCSFConstants.predictionType
                .substring(1 + XCSFConstants.predictionType.lastIndexOf('.')));
        if (XCSFConstants.multiThreading
                && Runtime.getRuntime().availableProcessors() > 1) {
            sb.append(", up to ");
            sb.append(Runtime.getRuntime().availableProcessors());
            sb.append(" threads");
        }
        return sb.toString();
    }

    /**
     * Registers the given listener. Listeners are informed about changes at the
     * end of every iteration in XCSF.
     * 
     * @param listener
     *            the listener to register
     * @see XCSFListener#stateChanged(int, Population, MatchSet,
     *      StateDescriptor, double[][])
     */
    public void addListener(XCSFListener listener) {
        for (XCSFListener el : listeners) {
            if (el.getClass().equals(listener.getClass())) {
                System.err.println("Listener of Class '" + el.getClass()
                        + "' already registered - ignoring " + listener);
                return;
            }
        }
        if (!listeners.contains(listener))
            listeners.add(listener);
    }

    /**
     * Convenience method to add several listeners at once. This can be useful,
     * when various experiments are run and the XCSF instance has to be
     * re-created, but the listeners (e.g. visualizations) remain the same.
     * 
     * @param listener
     *            a collection (list or set) of listeners
     */
    public void addListeners(Collection<XCSFListener> listener) {
        for (XCSFListener l : listener) {
            addListener(l);
        }
    }

    /**
     * Removes the given listener (object reference).
     * 
     * @param l
     *            The listener to remove.
     */
    public void removeListener(XCSFListener l) {
        listeners.remove(l);
    }

    /**
     * Remove all listeners.
     */
    public void clearListeners() {
        listeners.clear();
    }
}

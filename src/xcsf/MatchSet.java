package xcsf;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import xcsf.XCSFUtils.Random;
import xcsf.classifier.Classifier;

/**
 * The MatchSet class extends <code>ClassifierSet</code> and encapsulates
 * methods used for matching, covering and updates. Since matching consumes most
 * of the computational time for larger populations and/or complex conditions, a
 * parallelized matching procedure is available in order to exploit multi-core
 * architectures.
 * <p>
 * IMPORTANT: Experiments are NOT reproducible when using parallel matching! The
 * order of classifiers in the match-set is not deterministic (and match-sets are
 * not sorted after matching), thus following operations that depend on the
 * ordering (e.g. GA selection) are not deterministic as well, although random
 * numbers are generated in a deterministic way (fixed seed).
 * <p>
 * If 100% reproducible results are required (e.g. for debugging purposes),
 * there are two possibilities: Turn off parallel matching by setting the
 * threadingThreshold to <tt>Integer.MAX_VALUE</tt> or implement a sorted
 * match-set.
 * 
 * @author Patrick O. Stalph, Martin V. Butz, Shabnam Nazmi
 */
public class MatchSet extends ClassifierSet {

    private int adaptationAccuracy;

    // flag that indicates closest classifier matching
    private boolean numClosestMatching;
    // current state to be matched
    protected StateDescriptor state;
    // link to the population, not synchronized!
    protected Classifier[] popElements;
    // the population size
    protected int popSize;

    // serial single-core matching for population sizes below this threshold
    private int threadingThreshold;
    // adapt the threshold online depending on matching time estimates
    private boolean adaptiveThreading = true;
    // measured computational time for matching in milliseconds
    private long serialMatchingTime = 0, parallelMatchingTime = 0;
    // population size, where last adaptation check was done
    private int lastCheckSize = 0;

    // the farmer and n-1 workers. Not initialized for serial matching.
    private WorkerThread[] workers;
    // frequency for matching threads, equals n
    protected int frequency;
    // synchronization via initial and final barrier
    protected CyclicBarrier barrierStart, barrierEnd;

	private double[] intersectPrediction;

	private double consistencyIdx;

	private double[] unionPrediction;

    /**
     * Default constructor creates the match-set and initializes
     * <code>WorkerThread</code> objects, if desired and if more than one
     * processor is available.
     * <p>
     * The constructor starts <tt>n-1</tt> <code>WorkerThread</code> (where
     * <tt>n</tt> is the number of available processors), when using parallel
     * matching. These threads have to be destroyed properly by calling
     * {@link MatchSet#shutDownThreads()}, when the match-set is no longer used.
     * Otherwise these threads lay dormant until the java program terminates.
     * <p>
     * Note that experiments are NOT reproducible when using parallel matching!
     * See the class description for details.
     * 
     * @param doNumClosestMatch
     *            <code>true</code>, if closest classifier matching (CCM) shall
     *            be used
     * @param multithreaded
     *            <code>true</code>, if parallel matching shall be used
     */
    public MatchSet(boolean doNumClosestMatch, boolean multithreaded) {
        super();
        this.numClosestMatching = doNumClosestMatch;
        int n = Runtime.getRuntime().availableProcessors();
        if (multithreaded && n > 1) {
            initWorkers(n);
            this.adaptationAccuracy = Math.max(1, XCSFConstants.maxPopSize / 25);
            if (XCSFConstants.threadingThreshold < 0) {
                // automatic adaptation
                this.adaptiveThreading = true;
                this.threadingThreshold = adaptationAccuracy;
            } else {
                // fixed threshold
                this.threadingThreshold = XCSFConstants.threadingThreshold;
                this.adaptiveThreading = false;
            }
        } else {
            this.threadingThreshold = Integer.MAX_VALUE;
            this.adaptiveThreading = false;
        }
    }

    /**
     * Convenience constructor creates a simple match-set. Experiments will be
     * reproducible if the seed for random numbers is fixed via
     * {@link Random#setSeed(long)}.
     * 
     * @param doNumClosestMatch
     *            <code>true</code>, if closest classifier matching (CCM) shall
     *            be used
     */
    public MatchSet(boolean doNumClosestMatch) {
        this(doNumClosestMatch, false);
    }

    /**
     * Initializes worker threads.
     * 
     * @param n
     *            total number of threads (including main)
     */
    private void initWorkers(int n) {
        // n processors, 1=farmer/main, 2..n=worker(s)
        // two synchronization barriers for start & end of matching
        barrierStart = new CyclicBarrier(n);
        barrierEnd = new CyclicBarrier(n);
        // n clusters for n threads
        frequency = n;
        // create n-1 worker threads
        workers = new WorkerThread[n - 1];
        for (int i = 1; i < n; i++) {
            workers[i - 1] = new WorkerThread(i);
            workers[i - 1].start(); // initially dormant.
        }
    }

    /**
     * Sets the closest classifier matching (CCM) flag.
     * 
     * @param numClosestMatching
     *            <code>true</code> if closest classifier matching shall be used
     */
    public void setNumClosestMatching(boolean numClosestMatching) {
        this.numClosestMatching = numClosestMatching;
    }

    /**
     * Creates the match-set for the <code>currentState</code>. This method
     * chooses the appropriate way for matching (serial, parallel, closest
     * classifier matching) depending on the current settings. Furthermore, the
     * method handles the adaptation of the threading threshold, if applicable.
     * 
     * @param currentState
     *            the state to match
     * @param population
     *            the population
     */
    public void match(StateDescriptor currentState, Population population) {
        super.clear();
        state = currentState;
        popSize = population.size;
        popElements = population.elements;
        if (numClosestMatching) {
            // case1: closest classifier matching
            this.serialClostestClassifierMatching();
        } else if (adaptiveThreading && popSize - lastCheckSize >= adaptationAccuracy) {
            // case2: adaptation
            if (serialMatchingTime == 0) { // estimate serial time
                serialMatchingTime = -System.nanoTime();
                this.serialMatching();
                serialMatchingTime += System.nanoTime();
            } else { // estimate parallel time and update threshold
                parallelMatchingTime = -System.nanoTime();
                this.parallelMatching();
                parallelMatchingTime += System.nanoTime();

                // now both times are estimated -> compare
                if (serialMatchingTime < parallelMatchingTime) {
                    // threading threshold above current size
                    this.threadingThreshold = popSize + adaptationAccuracy;
                    lastCheckSize = popSize;
                    serialMatchingTime = parallelMatchingTime = 0;
                } else {
                    // threading threshold below current size
                    if (XCSFConstants.numberOfExperiments == 1) {
                        XCSFUtils.println("! parallel matching, if"
                                + " number of macro classifiers > "
                                + threadingThreshold);
                    }
                    this.adaptiveThreading = false;
                }
            }
        } else if (popSize < threadingThreshold) {
            // case3: regular serial matching
            this.serialMatching();
        } else {
            // case4: parallel matching
            this.parallelMatching();
        }
    }

    /**
     * PRBF method:
     * 
     * Calculates the average fitness-weighted prediction.
     * 
     * @return The fitness-weighted prediction of all classifiers in this
     *         match-set
     * @see Classifier#predict(StateDescriptor)
     */
    public double[] getWeightedPrediction() {
        int i = 0;
        double[] clPrediction = elements[i].predict(state);
        int n = clPrediction.length;
        double clFitness = elements[i].getFitness();
        double fitnessSum = clFitness;
        double[] avgPrediction = new double[n];
        for (int j = 0; j < n; j++) {
            avgPrediction[j] = clPrediction[j] * clFitness;
        }

        for (i = 1; i < size; i++) {
            clPrediction = elements[i].predict(this.state);  // function value prediction of the i'th element given the this.state
            clFitness = elements[i].getFitness();
            fitnessSum += clFitness;
            for (int j = 0; j < n; j++) {
                avgPrediction[j] += clPrediction[j] * clFitness;   // fitness weighted average prediction of classifiers in match set
            }
        }
        for (i = 0; i < n; i++) {
            avgPrediction[i] /= fitnessSum;
        }
        return avgPrediction;
    }
    
    /**
     * PRBF method: 
     * 
     * Calculates the fused prediction.
     * 
     * @return The fused prediction of all classifiers in this
     *         match-set
     * @see Classifier#predict(StateDescriptor)
     */

    public void calculateFusedPrediction() {
    	int i = 0;
        double[] clPrediction = elements[i].predict(state);
        int n = clPrediction.length;
        double clFitness = elements[i].getFitness();
        double[] singlePrediction = new double[n];
        double alpha = 0;

        this.intersectPrediction = new double[n];
        this.unionPrediction = new double[n];
        
        double[] matchsetFitness = new double[size];
        for (i = 0; i < size; i++) {
        	matchsetFitness[i] = elements[i].getFitness();
        }
                
        double maxFitness = matchsetFitness[maxValue(matchsetFitness)];
        double minFitness = minValue(matchsetFitness);
        
        // Initialize alpha
    	if (maxFitness == minFitness) {
    		alpha = 1;
    	}
    	else {
        	alpha = (clFitness - minFitness)/(maxFitness - minFitness);
    	}    
    	
    	// Initialize union and intersection
    	for (int l = 0; l < clPrediction.length; l++) {
    		if (clPrediction[l] < 0) {
    			clPrediction[l] = 0.0;
    		}
    		if (clPrediction[l] > 1) {
    			clPrediction[l] = 1.0;
    		}
    	}
        for (int j = 0; j < n; j++) {            	
        	this.intersectPrediction[j] = Math.min(clPrediction[j], alpha) + 1 - alpha;
        }      
        for (int j = 0; j < n; j++) {            	
        	this.unionPrediction[j] = alpha - Math.min(1 - clPrediction[j], alpha);
        }

        // Calculate weighted intersection and fuse predictions on match set
        for (i = 0; i < size; i++) {
        	clPrediction = elements[i].predict(this.state);
        	for (int l = 0; l < clPrediction.length; l++) {
        		if (clPrediction[l] < 0) {
        			clPrediction[l] = 0.0;
        		}
        		if (clPrediction[l] > 1) {
        			clPrediction[l] = 1.0;
        		}
        	}
        	clFitness = elements[i].getFitness();
        	if (maxFitness == minFitness) {
        		alpha = 1;
        	}
        	else {
            	alpha = (clFitness - minFitness)/(maxFitness - minFitness);
        	}
        	
        	double [] tempPrediction = clPrediction;
        	for (int j = 0; j < n; j++) {
            	if (clPrediction[j] < 0) {
            		tempPrediction[j] = 0; 
            	}
            	else if(clPrediction[j] > 1) {
            		tempPrediction[j] = 1;
            	}
            	singlePrediction[j] = Math.min(tempPrediction[j], alpha) + 1 - alpha; 
        		if (singlePrediction[j] < 0) {
        			System.out.println("wait!");
        		}
            }
        	
            for (int j = 0; j < n; j++)	{
            	if(singlePrediction[j] < this.intersectPrediction[j]) {
                	this.intersectPrediction[j] = singlePrediction[j];
                }  	
            }          
        }
        
        // Calculate weighted union and fuse predictions on match set
        for (i = 1; i < size; i++) {
        	clPrediction = elements[i].predict(this.state);
        	for (int l = 0; l < clPrediction.length; l++) {
        		if (clPrediction[l] < 0) {
        			clPrediction[l] = 0.0;
        		}
        		if (clPrediction[l] > 1) {
        			clPrediction[l] = 1.0;
        		}
        	}
        	clFitness = elements[i].getFitness();
        	if (maxFitness == minFitness) {
        		alpha = 1;
        	}
        	else {
            	alpha = (clFitness - minFitness)/(maxFitness - minFitness);
        	}
        	double [] tempPrediction = clPrediction;
            for (int j = 0; j < n; j++) {   
            	if (clPrediction[j] < 0) {
            		tempPrediction[j] = 0; 
            	}
            	else if(clPrediction[j] > 1) {
            		tempPrediction[j] = 1;
            	}
            	singlePrediction[j] = alpha - Math.min(1 - tempPrediction[j], alpha);
        		if (singlePrediction[j] > 1) {
        			System.out.println("wait!");
        		}
            }
            
            for (int j = 0; j < n; j++) {
                if(singlePrediction[j] > this.unionPrediction[j]) {
                	this.unionPrediction[j] = singlePrediction[j];
                }                
            }
        }
        
        this.consistencyIdx = this.intersectPrediction[maxValue(this.intersectPrediction)];
        //this.consistencyIdx = maxValue(this.intersectPrediction);
    }
    
    /**
   	 *
     * @return The fused prediction according to the
     * fuzzy intersection operator.
     */
    public double[] getIntersectPrediction() {
    	return this.intersectPrediction;
    }
    
    /**
     * 
     * @return The fused prediction according to the
     * fuzzy union operator.
     */
    public double[] getUnionPrediction() {
    	return this.unionPrediction;
    }
    
    /**
     * 
     * @return The consistency index among possibility distributions.
     */
    
    public double getConsistencyIdx() {
    	return this.consistencyIdx;
    }
    
    /**
     * Determined the element with a maximum value in a given array.
     * 
     * @param array to determine its element with the maximum value.
     * @return The index to the element with the maximum value.
     */
    
    public int maxValue (double[] array){
    	
	    if (array == null) {
	        throw new IllegalArgumentException("The Array must not be null");
	    } else if (array.length == 0) {
	        throw new IllegalArgumentException("Array cannot be empty.");
	    }	
	     int bestIdx = -1;
	     double max = Double.NEGATIVE_INFINITY;
	     for (int i = 0; i < array.length; i++) {
	       double elem = array[i];
	       if (elem > max) {
	         max = elem;
	         bestIdx = i;
	       }
	     }
	     return bestIdx;
    }
    
    
    /**
     * Determined the element with the minimum value in a given array.
     * 
     * @param array to determine its element with the minimum value.
     * @return The the value of the element with a minimum value.
     */
    public double minValue(double[] array) {
        // Validates input
        if (array == null) {
            throw new IllegalArgumentException("The Array must not be null");
        } else if (array.length == 0) {
            throw new IllegalArgumentException("Array cannot be empty.");
        }
    
        // Finds and returns min
        double min = array[0];
        for (int i = 1; i < array.length; i++) {
            if (Double.isNaN(array[i])) {
                return Double.NaN;
            }
            if (array[i] < min) {
                min = array[i];
            }
        }
        return min;
    }
   
    
    /**
     * Checks if current state is covered. If not, the method generates a
     * covering classifier and also deletes if the population is full.
     * 
     * @param population
     *            the classifier population
     * @param iteration
     *            the current iteration
     */
    void ensureStateCoverage(Population population, int iteration) {
        if (size == 0) {
            // create new classifier
            Classifier newCl = new Classifier(state, iteration);
            this.add(newCl);
            // delete from population, if necessary
            int numerositySum = 0;
            for (int i = 0; i < population.size; i++) {
                numerositySum += population.elements[i].getNumerosity();
            }
            int toDelete = numerositySum + 1 - XCSFConstants.maxPopSize;
            if (toDelete > 0) {
                population.deleteWorstClassifiers(toDelete);
                if (XCSFConstants.numberOfExperiments == 1) {
                    //XCSFUtils.println("Covering, although population is full @ "
                                    //+ iteration + ": " + state);
                }
            }
            // and finally add the new classifier
            population.add(newCl);
        }
    }

    /**
     * Increases experience, updates prediction, predictionError,
     * setSizeEstimate and fitness of each classifier in this match-set.
     * 
     * @see Classifier#update1(StateDescriptor)
     * @see Classifier#update2(double, int)
     */
    void updateClassifiers() {
        double accuracySum = 0;
        int numerositySum = 0;
        // update1: experience, prediction and predictionError
        for (int i = 0; i < size; i++) {
            Classifier cl = elements[i];
            cl.update1(state);
            // calculate accuracySum & numerositySum for update2
            accuracySum += cl.getAccuracy() * cl.getNumerosity();
            numerositySum += cl.getNumerosity();
        }
        // update2: setSizeEstimate and fitness
        for (int i = 0; i < size; i++) {
            elements[i].update2(accuracySum, numerositySum);
        }
    }

    /**
     * Adds all matching classifiers of the population to this match-set.
     * 
     * @param population
     *            the set of classifiers, that is checked for matching
     */
    private void serialMatching() {
        // ---[ standard matching ]---
        for (int i = 0; i < popSize; i++) {
            Classifier cl = popElements[i];
            if (cl.doesMatch(state)) {
                this.add(cl);
            }
        }
    }

    /**
     * Implementation of the Closest Classifier Matching (CCM) procedure.
     * 
     * @author Martin V. Butz
     */
    private void serialClostestClassifierMatching() {
        Classifier[] clSetHelp = new Classifier[popSize];
        int[] nums = new int[popSize];
        double[] votes = new double[popSize];
        for (int i = 0; i < popSize; i++) {
            clSetHelp[i] = popElements[i];
            votes[i] = clSetHelp[i].getActivity(state);
            nums[i] = clSetHelp[i].getNumerosity();
        }
        int firstNum = XCSFUtils.putNumFirstObjectsFirst(votes, nums,
                clSetHelp, popSize, XCSFConstants.numClosestMatch);
        // now add the classifiers to this match set.
        for (int i = 0; i < firstNum; i++) {
            this.add(clSetHelp[i]);
        }
    }

    /**
     * Implements the parallel matching by dividing the population into
     * <tt>n</tt> clusters, where the calling thread works on the first cluster,
     * while <tt>n-1</tt> worker threads work on their own clusters without
     * synchronization. The only two synchronization points needed are at the
     * beginning (all threads have to be ready) and at the end (all threads have
     * finished their job).
     * <p>
     * A visual representation of the matching process is depicted below, where
     * dots <tt>..</tt> represent an active thread state, underscores
     * <tt>__</tt> represent sleeping state. An asterisk <tt>*</tt> is a
     * synchronization point, e.g. a <code>CyclicBarrier</code>.
     * 
     * <pre>
     * Threads     | Time line
     * ------------+----------------------------------
     *  main       | ..prepare work..*..matching..*..continue
     *  worker-1   | ________________*..matching..*__________
     *  ...        | ________________*..matching..*__________
     *  worker-n-1 | ________________*..matching..*__________
     * </pre>
     * 
     * The clustering is simply done by assigning each thread a frequency (e.g.
     * the number of processors) and an offset such that the main thread matches
     * every <tt>n</tt> classifiers, while worker-i matches every <tt>n</tt>
     * plus offset <tt>i</tt> classifiers.
     * 
     * @author Patrick Stalph
     */
    private void parallelMatching() {
        try {
            // state1: synchronize with workers
            barrierStart.await();
            // state2: farmer and all workers are ready

            // state2: farmer does parallel matching
            for (int i = 0; i < popSize; i += frequency) {
                if (popElements[i].doesMatch(state)) {
                    add(popElements[i]);// synchronized
                }
            }

            // state2: synchronize with workers
            barrierEnd.await();
            // state1: farmer and all workers are done

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (BrokenBarrierException e) {
            e.printStackTrace();
        }
    }

    /**
     * Interrupts all running worker threads and indicates that this matchset is
     * no longer used. Calls to this method lead to a graceful shutdown of
     * additionally created threads. These threads would lie dormant (daemons),
     * if not interrupted. Calls to {@link XCSF#runSingleExperiment()} include a
     * call to this method after learning is finished in order to free
     * resources.
     * <p>
     * Important: The <code>MatchSet</code> object cannot be used in parallel
     * mode after calling this method.
     * 
     * @throws InterruptedException
     */
    void shutDownThreads() throws InterruptedException {
        for (WorkerThread w : workers) {
            w.interrupt(); // results in InterruptedException for workers
            w.join(); // wait for the worker to exit
            w = null;
        }
        this.workers = null;
        this.barrierStart = null;
        this.barrierEnd = null;
        // safety: turn off parallel matching
        this.threadingThreshold = Integer.MAX_VALUE;
        this.adaptiveThreading = false;
    }

    /**
     * This worker class implements a thread responsible for matching one
     * cluster of the population. Creating my own threads and synchronizing with
     * the main thread is faster than using <code>java.util.concurrent</code>
     * thread pools.
     * 
     * @author Patrick Stalph
     */
    class WorkerThread extends Thread {

        private int offset;

        /**
         * Default constructor.
         * 
         * @param offset
         */
        public WorkerThread(int offset) {
            super("MatchingWorker-" + offset);
            this.offset = offset;
            /*
             * Allows for sloppy shutdown. If not set to daemon, the JVM does
             * not exit until this thread dies.
             */
            this.setDaemon(true);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Thread#run()
         */
        public void run() {
            try {
                for (;;) { // infinite daemon loop until interrupted

                    // state1: synchronize with farmer
                    barrierStart.await();
                    // state2: farmer and all workers are ready

                    // state2: worker does parallel matching
                    for (int i = offset; i < popSize; i += frequency) {
                        if (popElements[i].doesMatch(state)) {
                            add(popElements[i]);// synchronized
                        }
                    }

                    // state2: synchronize with farmer
                    barrierEnd.await();
                    // state1: farmer and all workers are done.
                    // farmer (main) continues with updates & co,
                    // while workers get some rest.
                }
            } catch (InterruptedException e) {
                return; // shutdown
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
    }

    // /**
    // * Tried to write a faster code...
    // * <p>
    // * Result: MyBarrier is NOT faster than <code>CyclicBarrier</code>. Thus,
    // we
    // * don't use it.
    // *
    // * @author Patrick Stalph
    // */
    // protected static class MyBarrier {
    //
    // private int parties;
    // private int count = 0;
    //
    // public MyBarrier(int parties) {
    // this.parties = parties;
    // }
    //
    // public synchronized void await() throws InterruptedException,
    // BrokenBarrierException {
    // count++;
    // if (count == parties) {
    // count = 0;
    // this.notifyAll();
    // } else {
    // this.wait();
    // }
    // }
    // }
}

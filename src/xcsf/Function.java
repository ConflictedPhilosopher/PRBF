package xcsf;

import java.util.concurrent.LinkedBlockingQueue;

import xcsf.classifier.FileReadingDemo;
import xcsf.examples.LinearImbalanced;

/**
 * This is the general function interface for {@link XCSF}. If you want to
 * implement your own function, also take a look at the two convenience classes
 * <code>SimpleFunction</code> and <code>FunctionThread</code>.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public interface Function {

    /**
     * Returns the next problem instance for XCSF to learn. For theoretical
     * problems this will usually be a randomly generated input and the
     * corresponding scaled (and possibly noisy) output. If a complete dataset
     * is available, this method may just pick the next sample of the set.
     * 
     * @return the next problem instance
     * @see StateDescriptor
     */
    public StateDescriptor nextProblemInstance();

    /**
     * This method is optional and only used to assess XCSF's performance more
     * precisely on noisy problems. If <tt>null</tt> is returned from this
     * method, the regular output as set in {@link #nextProblemInstance()} is
     * used.
     * <p>
     * Returns the function value(s) as a double vector without noise. The
     * function value refers to the last problem input instance generated.
     * 
     * @return The noiseless function value(s).
     * @throws IllegalArgumentException
     *             if no problem instance is generated, yet.
     */
    public double[] getNoiselessFunctionValue();

    /**
     * Returns the condition input dimension.
     * 
     * @return the dimension of the condition input space.
     */
    public int getConditionInputDimension();

    /**
     * Returns the prediction input dimension.
     * 
     * @return the dimension of the prediction input space.
     */
    public int getPredictionInputDimension();

    /**
     * Returns the function output dimension.
     * 
     * @return the dimension of the function output space.
     */
    public int getOutputDimension();

    /**
     * This class encapsulates methods for simple test-functions with
     * 1-dimensional output. Extending classes only need to implement the
     * {@link #evaluate()} method to define the function. For non-uniformly
     * sampled functions, the {@link #generateInput()} method may be overridden
     * to realize an imbalanced sampling.
     * 
     * @author Patrick Stalph
     */
    public abstract class SimpleFunction implements Function {

        protected double[] input;
        protected double modifier;
        protected int dim;

        private double scale;
        private double noiseDeviation;
        private double[] noiseless;
        
        /**
         * Default constructor.
         * 
         * @param scale
         *            the scaling factor
         * @param modifier
         *            the function modifier
         * @param noiseDeviation
         *            the (Gaussian) noise deviation
         * @param dim
         *            the input dimension of the function
         */
        public SimpleFunction(double scale, double modifier, double noiseDeviation, int dim) {
            this.scale = scale;
            this.noiseDeviation = noiseDeviation;
            this.modifier = modifier;
            this.dim = dim;
            this.input = new double[dim];
        }

        /*
         * (non-Javadoc)
         * 
         * @see function.Function#getNextProblemInstance()
         */
        public StateDescriptor nextProblemInstance() {
            this.generateInput();
            // calculate scaled output
            double[] tempOut = this.evaluate();
            this.noiseless = new double[1];
            for (int i=0; i<tempOut.length; i++) {
            	  this.noiseless[i] = tempOut[i] * this.scale;
            	}
                        
            //this.noiseless = this.scale * this.evaluate;
            //double[] output = new double[] { noiseless };
            
            
            double[] output = this.noiseless ;

            if (this.noiseDeviation != 0) {
                // add noise
                output[0] += this.noiseDeviation * XCSFUtils.Random.normRand();
            }
            return new StateDescriptor(input.clone(), output);
        }

        /*
         * (non-Javadoc)
         * 
         * @see function.Function#getCurrentNoiselessFunctionValue()
         */
        public double[] getNoiselessFunctionValue() {
            //return new double[] { noiseless };
            return noiseless;

        }

        /*
         * (non-Javadoc)
         * 
         * @see function.Function#getConditionInputDimension()
         */
        public int getConditionInputDimension() {
            return this.dim;
        }

        /*
         * (non-Javadoc)
         * 
         * @see function.Function#getPredictionInputDimension()
         */
        public int getPredictionInputDimension() {
            return this.dim;
        }

        /*
         * (non-Javadoc)
         * 
         * @see function.Function#getOutputDimension()
         */
        public int getOutputDimension() {
            return 1;
        }

        /*
         * (non-Javadoc)
         * 
         * @see xcsf.functions.Function.SimpleFunction#toString()
         */
        public String toString() {
            return this.getClass().getSimpleName();
        }

        /**
         * Samples the input for this function. Uniform sampling is used by
         * default. For an example of imbalanced sampling, see
         * {@link LinearImbalanced}.
         */
        protected void generateInput() {
            // generate uniformly random input in [0,1]^n
            for (int i = 0; i < this.dim; i++) {
                input[i] = XCSFUtils.Random.uniRand();
            }
        }

        /**
         * Evaluates this function using the generated {@link #input}.
         * Implementations may use the {@link #modifier} to yield variable
         * behavior. The field {@link #dim} specifies the dimensionality of the
         * input. For each problem instance, the output of this method is scaled
         * and Gaussian noise is added. For an examplary implementation see the
         * {@link xcsf.examples.Sine} function.
         * 
         * @return the output for this function corresponding to the generated
         *         <code>input</code>
         */
        protected abstract double[] evaluate();
    }

    /**
     * This implementation of the <code>Function</code> interface provides
     * functionality for independent threading (one XCSF {@link Thread} and one
     * thread for the function itself). This way XCSF can be synchronized to the
     * problem at hand.
     * <p>
     * By starting the XCSF thread, XCSF calls the
     * {@link #nextProblemInstance()} method, which blocks until samples are
     * available. Another thread may call
     * {@link BlockingFunction#addProblemInstance(StateDescriptor)} to make new
     * samples available in a synchronized way.
     * 
     * @author Patrick Stalph
     */
    public abstract class BlockingFunction implements Function {

        // queue of size Integer.MAX_VALUE
        private LinkedBlockingQueue<StateDescriptor> states = new LinkedBlockingQueue<StateDescriptor>();

        /**
         * Adds a new <code>StateDescriptor</code> object to the list of
         * available problem instances to be learned. Additionally this method
         * will wake up an XCSF <code>Thread</code> that is waiting for the next
         * problem instance. This method cannot be called from the XCSF thread,
         * since this one is sleeping.
         * 
         * @param state
         *            the next problem instance to be learned
         */
        public synchronized void addProblemInstance(StateDescriptor state) {
            try {
                this.states.put(state);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // nofify waiting Thread (XCSF) that a new state is available.
            this.notify();
        }

        // ########## Function interface #####################################

        /**
         * Method of the <code>Function</code> interface.
         * <p>
         * Returns the next problem instance to be learned by <code>XCSF</code>.
         * If no problem instance is available, the calling <code>Thread</code>
         * (XCSF) will wait until notified, i.e. a new problem instance is added
         * by another <code>Thread</code>.
         * 
         * @see Function#nextProblemInstance()
         * @see BlockingFunction#addProblemInstance(StateDescriptor)
         */
        public synchronized StateDescriptor nextProblemInstance() {
            // Object.wait() should be used in a loop.
            while (states.isEmpty()) {
                /*
                 * XCSF has called this method. Since no StateDescriptor is
                 * available, the Thread will wait until notified by a
                 * addProblemInstance call from another Thread.
                 */
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // now states is not empty and this thread (XCSF) is notified.
            return states.poll();
        }

        /*
         * (non-Javadoc)
         * 
         * @see xcsf.Function#getNoiselessFunctionValue()
         */
        public abstract double[] getNoiselessFunctionValue();

        /*
         * (non-Javadoc)
         * 
         * @see functions.Function#getConditionInputDimension()
         */
        public abstract int getConditionInputDimension();

        /*
         * (non-Javadoc)
         * 
         * @see functions.Function#getOutputDimension()
         */
        public abstract int getOutputDimension();

        /*
         * (non-Javadoc)
         * 
         * @see functions.Function#getPredictionInputDimension()
         */
        public abstract int getPredictionInputDimension();
    }
}

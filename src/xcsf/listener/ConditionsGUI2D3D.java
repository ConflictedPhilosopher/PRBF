package xcsf.listener;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import xcsf.MatchSet;
import xcsf.Population;
import xcsf.StateDescriptor;
import xcsf.XCSF;
import xcsf.XCSFConstants;
import xcsf.XCSFListener;
import xcsf.classifier.Classifier;
import xcsf.classifier.Condition;
import xcsf.classifier.ConditionEllipsoid;
import xcsf.classifier.ConditionRectangle;
import xcsf.classifier.ConditionRotatingEllipsoid;
import xcsf.classifier.ConditionRotatingRectangle;

/**
 * Implements the <code>XCSFListener</code> interface to provide a population
 * visualization for two dimensional conditions only. The code is not straight
 * forward - sorry for that - but very efficient and covers all repaint
 * possibilites.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
public class ConditionsGUI2D3D extends JFrame implements XCSFListener,
        ChangeListener, ActionListener, ComponentListener, WindowStateListener {

    private static final long serialVersionUID = 2272880085308642254L;
    private final static int MIN_STEPS = 1;
    private final static int MAX_STEPS = 1000;
    private final static int MIN_DELAY = 0;
    private final static int MAX_DELAY = 5000;

    /**
     * The number of steps to wait, until the population is visualized.
     */
    public static int visualizationSteps = XCSFConstants.averageExploitTrials;

    /**
     * The delay in milliseconds after the population is visualized. Note, that
     * the visualization is NOT synchronized with xcsf, since the event
     * dispatched thread calls the paint(Graphics) method! Therefore it is
     * possible, that the population or some classifiers change during paint()
     * calls. If slowMotion is true, the listener/xcsf thread is blocked, until
     * paint() returns.
     */
    public static int visualizationDelay = 0;

    /**
     * A scaling factor for the conditions.
     */
    public static float visualizedConditionSize = 0.2f;

    /**
     * The transparency of the conditions.
     */
    public static float visualizationTransparency = 0.2f;

    /**
     * This flag indicates, if the matching should be visualized. If this flag
     * is set, the population is visualized every step and
     * {@link #visualizationSteps} is ignored! Additionally the xcsf thread is
     * blocked, until the paint() method returns.
     */
    public static boolean slowMotion = false;

    // sliders & slowmode-button
    private JSlider slider1;
    private JSlider slider2;
    private JSlider slider3;
    private JSlider slider4;
    private JToggleButton slowModeButton;
    private int fastMotionSteps;
    private JLabel bottomLabel;

    // population is visualized here
    private ConditionVisualization conditionVis;

    /**
     * Default constructor.
     */
    public ConditionsGUI2D3D() {
        super("Condition Visualization");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLayout(new BorderLayout());
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int xSize = (int) (0.5 * screen.getWidth());
        int ySize = (int) (1.0 * screen.getHeight());
        this.setBounds(0, 0, xSize, ySize);
        this.setLocation((int) screen.getWidth() - xSize, 0);
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
     * @see xcsf.listener.XCSFListener#stateChanged(int, java.util.Vector,
     * java.util.Vector, xcsf.StateDescriptor)
     */
    public void stateChanged(int iteration, Population population,
            MatchSet matchSet, StateDescriptor state, double[][] performance) {
        if (this.conditionVis == null) {
            // first call
            int dim = population.get(0).getCondition().getCenter().length;
            if (dim < 2 || dim > 3) {
                // invalid dimension
                //System.err.println(ConditionsGUI2D3D.class.getName()
                        //+ ": illegal dimension (" + dim + ")");
                return;
            }
            JPanel contentPane = new JPanel(new BorderLayout());
            if (init(dim, contentPane)) {
                super.setContentPane(contentPane);
                super.setVisible(true);
            } else {
                System.err.println(ConditionsGUI2D3D.class.getName()
                        + ": Dimension==3, but Java3d is not installed.");
            }
        } else if (iteration % visualizationSteps != 0 && !slowMotion) {
            return;
        } else if (this.getExtendedState() == Frame.ICONIFIED
                || !this.conditionVis.isShowing()) {
            // save some CPU, if the panel is not visible :-)
            return;
        }
        this.bottomLabel.setText("Iteration: " + iteration
                + ", MacroClassifiers: " + population.size());
        /*
         * don't call the Java event-dispatched repaint manager for painting the
         * conditions... no sense, since XCSF uses full cpu. Instead we do the
         * time-consuming part right here (blocking XCSF): We create a image of
         * the population and leave only the image-painting job for the repaint
         * manager. This makes the gui nicely responsive and assures that the
         * gui always shows the corresponding population instead of beeing stuck
         * painting. The only problem now is, that the gui buttons & sliders
         * don't result in a repaint of the image. This is handled by my
         * RepaintManager implementation, which assures low cpu use for sliders
         * (many repaint calls per second, if slider is moved!). EDIT: The 3D
         * case is included now. Java3D doesn't use the repaint method anyways,
         * so the complex part (changing scenegraph, transformations, material
         * and transparency) is blocking XCSF anyways.
         */
        this.conditionVis.repaintNow(population, matchSet, state);

        try {
            Thread.sleep(visualizationDelay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(ActionEvent e) {
        ConditionsGUI2D3D.slowMotion = this.slowModeButton.isSelected();
        if (slowMotion) {
            fastMotionSteps = visualizationSteps;
            this.slider1.setValue(1);
        } else {
            if (fastMotionSteps > 1) {
                visualizationSteps = fastMotionSteps;
                this.slider1.setValue(fastMotionSteps);
            }
        }
        this.slider1.setEnabled(!ConditionsGUI2D3D.slowMotion);
        this.repaint(); // this call only affects buttons & sliders
        this.conditionVis.repaintSoon(); // this actually schedules the job
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.swing.event.ChangeListener#stateChanged(javax.swing.event.ChangeEvent
     * )
     */
    public void stateChanged(ChangeEvent e) {
        if (!((JSlider) e.getSource()).getValueIsAdjusting()) {
            return;
        }
        if (e.getSource().equals(this.slider1)) {
            ConditionsGUI2D3D.visualizationSteps = this.slider1.getValue();
        } else if (e.getSource().equals(this.slider2)) {
            ConditionsGUI2D3D.visualizationDelay = this.slider2.getValue();
        } else if (e.getSource().equals(this.slider3)) {
            ConditionsGUI2D3D.visualizedConditionSize = this.slider3.getValue() / 100.0f;
        } else if (e.getSource().equals(this.slider4)) {
            ConditionsGUI2D3D.visualizationTransparency = this.slider4
                    .getValue() / 100.0f;
        }
        this.repaint(); // this call only affects buttons & sliders
        this.conditionVis.repaintSoon(); // this actually schedules the job
    }

    /*
     * (non-Javadoc)
     * 
     * @seejava.awt.event.ComponentListener#componentHidden(java.awt.event.
     * ComponentEvent)
     */
    public void componentHidden(ComponentEvent e) {
        // ignore
    }

    /*
     * (non-Javadoc)
     * 
     * @seejava.awt.event.ComponentListener#componentMoved(java.awt.event.
     * ComponentEvent )
     */
    public void componentMoved(ComponentEvent e) {
        // ignore
    }

    /*
     * (non-Javadoc)
     * 
     * @seejava.awt.event.ComponentListener#componentResized(java.awt.event.
     * ComponentEvent)
     */
    public void componentResized(ComponentEvent e) {
        this.conditionVis.repaintSoon();
    }

    /*
     * (non-Javadoc)
     * 
     * @seejava.awt.event.ComponentListener#componentShown(java.awt.event.
     * ComponentEvent )
     */
    public void componentShown(ComponentEvent e) {
        this.conditionVis.repaintSoon();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * java.awt.event.WindowStateListener#windowStateChanged(java.awt.event.
     * WindowEvent)
     */
    public void windowStateChanged(WindowEvent e) {
        this.conditionVis.repaintSoon();
    }

    /**
     * Initializes the GUI.
     * 
     * @param dimension
     *            the dimension of the conditions to visualize
     * @param contentpane
     *            the contentpane to add elements
     * @return <code>false</code> if java3d is not installed, but the
     *         <code>dimension</code> is three; <code>true</code> otherwise.
     */
    private boolean init(int dimension, JPanel contentpane) {
        // top
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = 1;
        // slowmode button
        slowModeButton = new JToggleButton("Show Matchset",
                ConditionsGUI2D3D.slowMotion);
        slowModeButton.addActionListener(this);
        c.gridheight = 2;
        top.add(slowModeButton, c);
        c.gridheight = 1;
        // slider 1
        c.gridx++;
        if (visualizationSteps > MAX_STEPS) {
            visualizationSteps = MAX_STEPS;
        } else if (visualizationSteps < MIN_STEPS) {
            visualizationSteps = MIN_STEPS;
        }
        this.slider1 = new JSlider(MIN_STEPS, MAX_STEPS,
                ConditionsGUI2D3D.visualizationSteps);
        slider1.setMajorTickSpacing(MAX_STEPS - MIN_STEPS);
        slider1.setMinorTickSpacing((MAX_STEPS - MIN_STEPS + 1) / 10);
        slider1.setPaintTicks(true);
        slider1.setPaintLabels(true);
        slider1.setEnabled(!slowMotion);
        slider1.addChangeListener(this);
        top.add(new JLabel("Visualization Steps", SwingConstants.CENTER), c);
        c.gridy++;
        top.add(this.slider1, c);
        // slider 2
        c.gridy = 0;
        c.gridx++;
        if (visualizationDelay > MAX_DELAY) {
            visualizationDelay = MAX_DELAY;
        } else if (visualizationDelay < MIN_DELAY) {
            visualizationDelay = MIN_DELAY;
        }
        this.slider2 = new JSlider(MIN_DELAY, MAX_DELAY,
                ConditionsGUI2D3D.visualizationDelay);
        slider2.setMajorTickSpacing((MAX_DELAY - MIN_DELAY) / 2);
        slider2.setMinorTickSpacing((MAX_DELAY - MIN_DELAY) / 10);
        slider2.setPaintLabels(true);
        slider2.setPaintTicks(true);
        slider2.addChangeListener(this);
        top.add(new JLabel("Visualization Delay (ms)", SwingConstants.CENTER),
                c);
        c.gridy++;
        top.add(this.slider2, c);
        // slider 3
        c.gridy = 0;
        c.gridx++;
        if (visualizedConditionSize > 1f) {
            visualizedConditionSize = 1f;
        } else if (visualizedConditionSize < 0f) {
            visualizedConditionSize = 0f;
        }
        this.slider3 = new JSlider(0, 100,
                (int) (ConditionsGUI2D3D.visualizedConditionSize * 100));
        slider3.setMajorTickSpacing(50);
        slider3.setMinorTickSpacing(10);
        slider3.setPaintLabels(true);
        slider3.setPaintTicks(true);
        slider3.addChangeListener(this);
        top.add(new JLabel("Condition Size (%)", SwingConstants.CENTER), c);
        c.gridy++;
        top.add(this.slider3, c);
        // slider 4
        c.gridy = 0;
        c.gridx++;
        if (visualizationTransparency > 1f) {
            visualizationTransparency = 1f;
        } else if (visualizationTransparency < 0f) {
            visualizationTransparency = 0f;
        }
        this.slider4 = new JSlider(0, 100,
                (int) (ConditionsGUI2D3D.visualizationTransparency * 100));
        slider4.setMajorTickSpacing(50);
        slider4.setMinorTickSpacing(10);
        slider4.setPaintLabels(true);
        slider4.setPaintTicks(true);
        slider4.addChangeListener(this);
        top.add(new JLabel("Transparency (%)", SwingConstants.CENTER), c);
        c.gridy++;
        top.add(this.slider4, c);
        top.setBorder(BorderFactory.createEtchedBorder());
        contentpane.add(top, BorderLayout.NORTH);

        // center
        if (dimension == 2) {
            this.conditionVis = new Visualization2D();
        } else if (dimension == 3) {
            // make sure that java3d is installed to prevent exceptions.
            try {
                XCSF.class.getClassLoader().loadClass(
                        "com.sun.j3d.utils.universe.SimpleUniverse");
            } catch (ClassNotFoundException e) {
                return false;
            }
            this.conditionVis = new ConditionVisualization3D();
        } else {
            throw new IllegalArgumentException(
                    "Illegal ConditionVisualization dimension: " + dimension);
        }
        contentpane.add(this.conditionVis, BorderLayout.CENTER);

        // bottom
        this.bottomLabel = new JLabel("Initializing..", SwingConstants.CENTER);
        this.bottomLabel.setBorder(BorderFactory.createEtchedBorder());
        contentpane.add(this.bottomLabel, BorderLayout.SOUTH);

        this.addComponentListener(this);
        this.addWindowStateListener(this);
        return true;
    }

    /**
     * Abstract superclass for two- and three-dimensional visualizations of
     * classifier conditions.
     * 
     * @author Patrick Stalph
     */
    static abstract class ConditionVisualization extends JPanel {

        private static final long serialVersionUID = -5612110860381297504L;

        /**
         * Default constructor.
         */
        ConditionVisualization() {
            super();
        }

        /**
         * Repaint immediatley.
         * 
         * @param population
         *            the population
         * @param machtSet
         *            the matchset
         * @param state
         *            the current function sample
         */
        abstract void repaintNow(Population population, MatchSet machtSet,
                StateDescriptor state);

        /**
         * Repaint as soon as possible.
         */
        abstract void repaintSoon();
    }

    /**
     * Implementation of a manager that handels repaint requests such that
     * multiple requests at the same time (specified by
     * {@link RepaintManager#PERIOD}) result in one repaint only.
     * 
     * @author Patrick Stalph
     */
    static abstract class RepaintManager {

        // 4 updates / second
        private final static long PERIOD = 250;
        // number of periods to wait before actually repainting.
        private final static int DELAY = 2;

        private Timer timer;
        private RepaintTask delayTask;

        /**
         * Requests a repaint. The actual painting (call of {@link #doPaint()})
         * is executed after a given delay.
         */
        public synchronized void requestRepaint() {
            assertTimer();
            // schedule a paint job, if not done, yet.
            if (delayTask.counter < 0) {
                // reset the counter
                delayTask.counter = DELAY;
            }
        }

        /**
         * The repaint is done in another method and can be removed from the
         * current schedule.
         */
        public synchronized void repaintDoneElsewhere() {
            assertTimer();
            // remove job from schedule
            delayTask.counter = -1;
        }

        /**
         * Implementations put the heavy painting code in here.
         */
        protected abstract void doPaint();

        /**
         * Initialize the timer and schedula the default task (not the paint
         * job, just a timertask!), if not running. This lazy singleton
         * initialization prevents overhead.
         */
        private void assertTimer() {
            if (timer == null) {
                timer = new Timer("XCSFRepaintManager");
                delayTask = this.new RepaintTask();
                timer.schedule(delayTask, 0, PERIOD);
            }
        }

        /**
         * Simple <code>TimerTask</code> implementation with three states.
         * <p>
         * <li>counter < 0: No repaint is scheduled, do nothing.
         * <li>counter = 0: The repaint is executed and the counter set to -1.
         * <li>counter > 0: A repaint was requested and is already scheduled.
         * The counter is frequently reduced.
         * 
         * @author Patrick Stalph
         */
        class RepaintTask extends TimerTask {

            protected int counter = -1;

            /*
             * (non-Javadoc)
             * 
             * @see java.util.TimerTask#run()
             */
            public synchronized void run() {
                if (counter == 0) {
                    counter--;
                    doPaint();
                } else if (counter > 0) {
                    counter--;
                }
            }
        }
    }

    /**
     * Visualization Implementation for the two dimensional case.
     * 
     * @author Patrick Stalph
     */
    static class Visualization2D extends ConditionVisualization {

        private static final long serialVersionUID = 6199970283187565576L;

        private RepaintTimer repaintTimer;
        private JLabel visLabel;

        // links for repaint calls
        Population populationLink;
        MatchSet matchSetLink;
        StateDescriptor stateLink;

        /**
         * Default constructor.
         */
        Visualization2D() {
            super();
            this.visLabel = new JLabel();
            this.visLabel.setHorizontalAlignment(SwingConstants.CENTER);
            this.visLabel.setVerticalAlignment(SwingConstants.NORTH);
            super.add(this.visLabel);
            this.repaintTimer = new RepaintTimer();
        }

        /*
         * (non-Javadoc)
         * 
         * @see utils.ConditionsGUI2D.Visualization#repaintNow()
         */
        void repaintNow(Population population, MatchSet matchSet,
                StateDescriptor state) {
            this.populationLink = population;
            this.matchSetLink = matchSet;
            this.stateLink = state;
            this.redrawImage();
        }

        /*
         * (non-Javadoc)
         * 
         * @see utils.ConditionsGUI2D.Visualization#repaintSoon()
         */
        void repaintSoon() {
            this.repaintTimer.requestRepaint();
        }

        /**
         * This expensive method repaints the image, sets the image to the label
         * and calls the Java event-dispatched repaintmanager.
         */
        void redrawImage() {
            if (populationLink != null && matchSetLink != null
                    && stateLink != null) {
                BufferedImage image = createImage2D(populationLink,
                        matchSetLink, stateLink, slowMotion, Math.min(this
                                .getWidth(), this.getHeight()));
                this.visLabel.setIcon(new ImageIcon(image));
                this.repaint();
                // Repaint is finished, clear repaint requests. This may be
                // useful if this method is not called by the RepaintTimer.
                this.repaintTimer.repaintDoneElsewhere();
            }
        }

        /**
         * Creates a <code>BufferedImage</code> of the given population.
         * 
         * @param population
         *            the population
         * @param matchSet
         *            the matchset
         * @param state
         *            the current function sample
         * @param showMatchSet
         *            <code>true</code> if the matchset should be colored
         *            separately; <code>false</code> otherwise.
         * @param width
         *            the image width
         * @return the image
         */
        static BufferedImage createImage2D(Population population,
                MatchSet matchSet, StateDescriptor state, boolean showMatchSet,
                int width) {
            BufferedImage image = new BufferedImage(width, width,
                    BufferedImage.TYPE_INT_RGB);
            int margin = (int) (0.05 * width); // 5% margin
            int range = (int) (.9 * width); // => 90% range
            Graphics g = image.getGraphics();
            Graphics2D g2 = (Graphics2D) g;

            // white background for input space
            g2.setColor(Color.WHITE);
            g2.fillRect(margin, margin, range, range);
            // enable antialiasing & transparency
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    1.f - visualizationTransparency));

            if (!showMatchSet) {
                // fast view: paint all cl's with fitness color
                for (Classifier cl : population) {
                    double fit = cl.getFitness();
                    fit = Math.pow(fit, .3);
                    g2.setColor(new Color((int) (255 * (.8 - .8 * fit)),
                            (int) (255 * (.8 - .8 * fit)), 255));
                    g2.fill(createShape2D(cl.getCondition(), range, margin));
                }
            } else {
                // slow mode: paint non-matching cl's with fitness color
                // and matching cl's with border & activity color
                for (Classifier cl : population) {
                    if (!cl.doesMatch(state)) {
                        // use fitness for coloring
                        double fit = cl.getFitness();
                        fit = Math.pow(fit, .3);
                        g2.setColor(new Color((int) (255 * (.8 - .8 * fit)),
                                (int) (255 * (.8 - .8 * fit)), 255));
                        g2
                                .fill(createShape2D(cl.getCondition(), range,
                                        margin));
                    }
                }
                // paint matchset
                for (Classifier cl : matchSet) {
                    // con.doesMatch: draw border + use activity for color
                    float act = (float) cl.getActivity(state);
                    g2.setColor(new Color(0.0f, 1.0f, 0.0f, act));
                    Shape shape = createShape2D(cl.getCondition(), range,
                            margin);
                    g2.fill(shape);
                    g2.setColor(new Color(0.0f, 0.0f, 0.0f, act));
                    g2.draw(shape);
                }
                // paint condition input
                int x = (int) (margin + (state.getConditionInput()[0] * range));
                int y = (int) (margin + ((1.0 - state.getConditionInput()[1]) * range));
                g2.setColor(Color.RED);
                g2.drawLine(x - 5, y - 5, x + 5, y + 5);
                g2.drawLine(x + 5, y - 5, x - 5, y + 5);
            }
            return image;
        }

        /**
         * Creates the shape for the given condition.
         * 
         * @param condition
         *            the ellipsoidal condition.
         * @param range
         *            the range
         * @param offset
         *            the offset
         * @return the shape object used for 2D-painting
         */
        private static Shape createShape2D(Condition condition, int range,
                int offset) {
            if (condition instanceof ConditionEllipsoid) {
                // ---[ ellipsoid
                // ]------------------------------------------------
                ConditionEllipsoid con = (ConditionEllipsoid) condition;
                double stretchX = visualizedConditionSize * con.getStretch()[0]
                        * 2 * range;
                double stretchY = visualizedConditionSize * con.getStretch()[1]
                        * 2 * range;
                Ellipse2D ellipse = new Ellipse2D.Double(-.5 * stretchX, -.5
                        * stretchY, stretchX, stretchY);
                AffineTransform at = AffineTransform.getRotateInstance(0);
                Shape ellRot = at.createTransformedShape(ellipse);
                // set position (invert y because y.top is 0
                // and y.bottom is this.getHeight())
                at.setToTranslation(offset + con.getCenter()[0] * range, offset
                        + (1.0 - con.getCenter()[1]) * range);
                return at.createTransformedShape(ellRot);
            } else if (condition instanceof ConditionRotatingEllipsoid) {
                // ---[ rotating ellipsoid
                // ]---------------------------------------
                ConditionRotatingEllipsoid con = (ConditionRotatingEllipsoid) condition;
                double stretchX = visualizedConditionSize * con.getStretch()[0]
                        * 2 * range;
                double stretchY = visualizedConditionSize * con.getStretch()[1]
                        * 2 * range;
                Ellipse2D ellipse = new Ellipse2D.Double(-.5 * stretchX, -.5
                        * stretchY, stretchX, stretchY);
                // rotate
                AffineTransform at = AffineTransform.getRotateInstance(-con
                        .getAngles()[0]);
                Shape ellRot = at.createTransformedShape(ellipse);
                // set position (invert y because y.top is 0
                // and y.bottom is this.getHeight())
                at.setToTranslation(offset + con.getCenter()[0] * range, offset
                        + (1.0 - con.getCenter()[1]) * range);
                return at.createTransformedShape(ellRot);
            } else if (condition instanceof ConditionRectangle) {
                // ---[ rectangle
                // ]-----------------------------------------------
                ConditionRectangle con = (ConditionRectangle) condition;
                double stretchX = visualizedConditionSize * con.getStretch()[0]
                        * 2 * range;
                double stretchY = visualizedConditionSize * con.getStretch()[1]
                        * 2 * range;
                Rectangle2D rectangle = new Rectangle2D.Double(-.5 * stretchX,
                        -.5 * stretchY, stretchX, stretchY);
                AffineTransform at = AffineTransform.getRotateInstance(0);
                Shape rotatedRectanlge = at.createTransformedShape(rectangle);
                // set position (invert y because y.top is 0
                // and y.bottom is this.getHeight())
                at.setToTranslation(offset + con.getCenter()[0] * range, offset
                        + (1.0 - con.getCenter()[1]) * range);
                return at.createTransformedShape(rotatedRectanlge);
            } else if (condition instanceof ConditionRotatingRectangle) {
                // ---[ rotating rectangle
                // ]---------------------------------------
                ConditionRotatingRectangle con = (ConditionRotatingRectangle) condition;
                double stretchX = visualizedConditionSize * con.getStretch()[0]
                        * 2 * range;
                double stretchY = visualizedConditionSize * con.getStretch()[1]
                        * 2 * range;
                Rectangle2D rectangle = new Rectangle2D.Double(-.5 * stretchX,
                        -.5 * stretchY, stretchX, stretchY);
                // rotate
                AffineTransform at = AffineTransform.getRotateInstance(-con
                        .getAngles()[0]);
                Shape rotatedRectanlge = at.createTransformedShape(rectangle);
                // set position (invert y because y.top is 0
                // and y.bottom is this.getHeight())
                at.setToTranslation(offset + con.getCenter()[0] * range, offset
                        + (1.0 - con.getCenter()[1]) * range);
                return at.createTransformedShape(rotatedRectanlge);
            } else {
                throw new IllegalArgumentException("The condition type '"
                        + condition.getClass().getName()
                        + "' is not supported.");
            }
        }

        /**
         * This RepaintManager implementation just calls the expensive image
         * painting method.
         * 
         * @author Patrick Stalph
         */
        class RepaintTimer extends RepaintManager {

            /*
             * (non-Javadoc)
             * 
             * @see utils.RepaintManager#doPaint()
             */
            protected void doPaint() {
                redrawImage();
            }
        }
    }
}

/*
 * created on Oct 23, 2009
 */
package xcsf.listener;

import java.awt.GridLayout;
import java.util.Vector;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.IndexedLineArray;
import javax.media.j3d.Material;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.TransparencyAttributes;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

import xcsf.MatchSet;
import xcsf.Population;
import xcsf.StateDescriptor;
import xcsf.XCSFConstants;
import xcsf.classifier.Classifier;
import xcsf.classifier.Condition;
import xcsf.classifier.ConditionEllipsoid;
import xcsf.classifier.ConditionRectangle;
import xcsf.classifier.ConditionRotatingEllipsoid;
import xcsf.classifier.ConditionRotatingRectangle;
import xcsf.listener.ConditionsGUI2D3D.ConditionVisualization;

import com.sun.j3d.utils.behaviors.mouse.MouseRotate;
import com.sun.j3d.utils.behaviors.mouse.MouseTranslate;
import com.sun.j3d.utils.behaviors.mouse.MouseZoom;
import com.sun.j3d.utils.geometry.Box;
import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;
import com.sun.j3d.utils.universe.SimpleUniverse;

/**
 * Visualization implementation for the three-dimensional condition
 * visualization {@link ConditionsGUI2D3D}. This class is separated due to the
 * following situation:
 * <ul>
 * <li>Java3D is not installed.
 * <li>A two-dimensional condition structure is to be visualized using the
 * <code>ConditionsGUI2D3D</code> class
 * </ul>
 * Although the 3D code is never called, the <code>ClassLoader</code> loads the
 * class, but cannot resolve the unknown classes from Java3D. Consequently an
 * exception is thrown, although the 2D code is expected to run without Java3D.
 * <p>
 * Structure of the scenegraph:
 * 
 * <pre>
 * Universe (and Locale)
 *  + BG: ViewBranch
 *  + BG: ContentBranchGroup
 *      + directionalLight
 *      + TG: MouseTransformGroup (rotate, translate, zoom)
 *          + TG: center first quadrant
 *              + ambientLight
 *              + coordinateAxis
 *              + BG-TG-Sphere
 *                ...
 *              + BG-TG-Sphere
 * </pre>
 * 
 * @author Patrick Stalph
 */
class ConditionVisualization3D extends ConditionVisualization {

    private static final long serialVersionUID = 2976135848759208115L;

    private final static Color3f BLACK = new Color3f(0f, 0f, 0f);
    private final static float SHININESS = 1.0f;
    private final static Transform3D ZERO_TRANSFORM = new Transform3D(
            new double[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
    private static double[] array = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 1 };
    private static double[][] matrix = new double[4][4];
    private static Transform3D t3d = new Transform3D();

    private Population populationLink;
    private StateDescriptor stateLink;

    private TransformGroup tgContent;
    private Vector<BranchGroup> conditionBGs = new Vector<BranchGroup>();
    private Vector<TransformGroup> conditionTGs = new Vector<TransformGroup>();
    private Vector<Appearance> conditionAps = new Vector<Appearance>();

    /**
     * Default constructor.
     */
    ConditionVisualization3D() {
        super();
        // create a SimpleUniverse on a Canvas3D, which is shown on-screen
        Canvas3D canvas3D = new Canvas3D(SimpleUniverse
                .getPreferredConfiguration());
        // TODO also resize Canvas3D vertically, if JPanel is resized
        // horizontally.
        super.setLayout(new GridLayout(1, 1));
        super.add(canvas3D);
        SimpleUniverse universe = new SimpleUniverse(canvas3D);
        universe.getViewingPlatform().setNominalViewingTransform();

        BranchGroup scene = createSceneGraph();
        universe.addBranchGraph(scene);
    }

    /*
     * (non-Javadoc)
     * 
     * @see utils.ConditionsGUI.Visualization#repaintNow(xcsf.Population,
     * xcsf.MatchSet, xcsf.StateDescriptor)
     */
    void repaintNow(Population population, MatchSet machtSet,
            StateDescriptor state) {
        this.populationLink = population;
        this.stateLink = state;
        this.repaintSoon();
    }

    /*
     * (non-Javadoc)
     * 
     * @see utils.ConditionsGUI.Visualization#repaintSoon()
     */
    void repaintSoon() {
        /*
         * (1) adapt the number of branchgroup-transformgroup-primitive paths in
         * the scenegraph. The number is increased in 10% steps. Therefor the
         * tgContent Group contains maximally ten sub-BranchGroups which contain
         * itself several (10% of maxPopSize) TransformGroup/Primitive objects
         * for condition rendering.
         */
        this.adaptSize();
        /*
         * (2) for each classifier set the corresponding position & shape (by
         * modifying its TransformGroup matrix), color (by modifying the
         * material) and transparency.
         */
        if (!ConditionsGUI2D3D.slowMotion) {
            // fast view: paint all cl's with fitness color
            for (int index = 0; index < populationLink.size(); index++) {
                this.setConditionProperties(index, populationLink.get(index),
                        false);
            }
        } else {
            // slow motion: paint matchset with activity color and others
            // with fitness color.
            for (int index = 0; index < populationLink.size(); index++) {
                Classifier cl = populationLink.get(index);
                this.setConditionProperties(index, cl, cl.doesMatch(stateLink));
            }
        }
        /*
         * (3) using the 10% technique we have several unused primitives. Their
         * scale is set to zero (leaving them invisible).
         */
        for (int index = this.conditionTGs.size() - 1; index >= this.populationLink
                .size(); index--) {
            this.conditionTGs.get(index).setTransform(ZERO_TRANSFORM);
        }
    }

    /**
     * Adapt the number of BranchGroup objects in the scenegraph and
     * consequently the number of TransformGroup / Appearance objects in the
     * Vector objects.
     * <p>
     * Strategy: if more elements are necesary, 10% of maxPopSize are added
     * (invisible, but available for visualization). If 15% more elements are
     * available than needed, 10% are removed. This prevents
     * add-remove-add-remove cycles at the border.
     */
    private void adaptSize() {
        int num = (int) Math.ceil(XCSFConstants.maxPopSize * 0.1);
        while (populationLink.size() > this.conditionTGs.size()) {
            Condition con = this.populationLink.get(0).getCondition();
            // add 10% of maxPopulationSize
            BranchGroup bg = new BranchGroup();
            bg.setCapability(BranchGroup.ALLOW_DETACH);
            for (int i = 0; i < num; i++) {
                TransformGroup tg = new TransformGroup(ZERO_TRANSFORM);
                tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
                bg.addChild(tg);
                Appearance ap = new Appearance();
                ap.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
                ap
                        .setCapability(Appearance.ALLOW_TRANSPARENCY_ATTRIBUTES_WRITE);
                // switch shape: box / sphere
                Primitive primitive = null;
                if (con instanceof ConditionRectangle) {
                    primitive = new Box(1f, 1f, 1f, Primitive.GENERATE_NORMALS,
                            ap);
                } else if (con instanceof ConditionRotatingRectangle) {
                    primitive = new Box(1f, 1f, 1f, Primitive.GENERATE_NORMALS,
                            ap);
                } else if (con instanceof ConditionEllipsoid) {
                    primitive = new Sphere(1f, Primitive.GENERATE_NORMALS, ap);
                } else if (con instanceof ConditionRotatingEllipsoid) {
                    primitive = new Sphere(1f, Primitive.GENERATE_NORMALS, ap);
                } else {
                    throw new IllegalArgumentException("Unkown Condition: "
                            + con);
                }
                tg.addChild(primitive);
                this.conditionTGs.add(tg);
                this.conditionAps.add(ap);
            }
            this.conditionBGs.add(bg);
            bg.compile();
            this.tgContent.addChild(bg);
        }
        while (this.conditionTGs.size() - populationLink.size() > XCSFConstants.maxPopSize * 0.2) {
            // remove 10% of maxPopulationSize if 20% are available.
            // This should prevent time-consuming remove-add-remove cycles.
            BranchGroup bg = this.conditionBGs.lastElement();
            this.tgContent.removeChild(bg);
            this.conditionBGs.remove(bg);
            int index = conditionTGs.size() - 1;
            for (int i = 0; i < num; i++) {
                this.conditionTGs.remove(index);
                this.conditionAps.remove(index);
                index--;
            }
        }
    }

    /**
     * Sets the properties at the given index.
     * 
     * @param index
     *            the index to modify
     * @param cl
     *            the classifier corresponding to the index
     * @param showActivity
     *            <code>true</code> if the classifier matches and matching
     *            classifiers shall be differently colored; <code>false</code>
     *            otherwise.
     */
    private void setConditionProperties(int index, Classifier cl,
            boolean showActivity) {
        /*
         * Sometimes concurrent (gui thread vs main) calls of the repaintSoon()
         * method can produce bad vector sizes resulting in an ugly
         * ArrayOutOfBoundsException. This workaround is ugly too, but simple &
         * better than an exception.
         */
        if (this.conditionTGs.size() <= index) {
            return;
        }
        // calculate transformation matrix
        transformationMatrix2Array(cl.getCondition());
        t3d.set(array);
        // calculate color
        Color3f col = null;
        Color3f specular = null;
        if (showActivity) {
            col = new Color3f((float) (.2), 1, 1);
            specular = new Color3f((float) (.8), 1, 1);
        } else {
            double fit = Math.pow(cl.getFitness(), 0.1);
            col = new Color3f((float) (0.8 - 0.8 * fit),
                    (float) (0.8 - 0.8 * fit), (float) 0.9);
            specular = new Color3f((float) (0.9 - 0.6 * fit),
                    (float) (0.9 - 0.6 * fit), 1f);
        }
        // set transformation, material and transparency
        this.conditionTGs.get(index).setTransform(t3d);
        this.conditionAps.get(index).setMaterial(
                new Material(col, BLACK, col, specular, SHININESS));
        this.conditionAps.get(index).setTransparencyAttributes(
                new TransparencyAttributes(TransparencyAttributes.FASTEST,
                        ConditionsGUI2D3D.visualizationTransparency));
    }

    /**
     * Creates the scene graph for java3d.
     * 
     * @return the root of the scene graph
     */
    private BranchGroup createSceneGraph() {
        BranchGroup root = new BranchGroup();
        BoundingSphere boundingSphere = new BoundingSphere(
                new Point3d(0, 0, 0), 1000.0);
        Background background = new Background(new Color3f(0.5f, 0.5f, 0.5f));
        background.setApplicationBounds(boundingSphere);
        root.addChild(background);

        // ---[ MouseTransformGroup ]---
        TransformGroup tgMouse = new TransformGroup();
        tgMouse.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        tgMouse.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
        MouseRotate myMouseRotate = new MouseRotate();
        myMouseRotate.setTransformGroup(tgMouse);
        myMouseRotate.setSchedulingBounds(new BoundingSphere());
        root.addChild(myMouseRotate);
        MouseTranslate myMouseTranslate = new MouseTranslate();
        myMouseTranslate.setTransformGroup(tgMouse);
        myMouseTranslate.setSchedulingBounds(new BoundingSphere());
        root.addChild(myMouseTranslate);
        MouseZoom myMouseZoom = new MouseZoom();
        myMouseZoom.setTransformGroup(tgMouse);
        myMouseZoom.setSchedulingBounds(new BoundingSphere());
        root.addChild(myMouseZoom);
        root.addChild(tgMouse);
        // center first quadrant = translate(-0.5,-0.5,-0.5)
        Transform3D transl = new Transform3D();
        transl.setTranslation(new Vector3d(-0.5, -0.5, -0.5));
        this.tgContent = new TransformGroup(transl);
        tgContent.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        tgContent.setCapability(Group.ALLOW_CHILDREN_WRITE);
        tgMouse.addChild(tgContent);

        // ---[ Directional and Ambient Light ]---
        Color3f lightColor = new Color3f(.6f, .6f, .6f);
        Vector3f lightDirection = new Vector3f(4.0f, -7.0f, -12.0f);
        DirectionalLight light = new DirectionalLight(lightColor,
                lightDirection);
        light.setInfluencingBounds(boundingSphere);
        root.addChild(light);
        Color3f ambientColor = new Color3f(1.0f, 1.0f, 1.0f);
        AmbientLight ambientLight = new AmbientLight(ambientColor);
        ambientLight.setInfluencingBounds(boundingSphere);
        tgContent.addChild(ambientLight);

        // ---[ Coordinate Axis and Bounding Box ]---
        tgContent.addChild(new Axis());
        Appearance cubeAp = new Appearance();
        cubeAp.setTransparencyAttributes(new TransparencyAttributes(
                TransparencyAttributes.FASTEST, 0.8f));
        cubeAp.setMaterial(new Material(BLACK, BLACK, BLACK, new Color3f(1, 1,
                1), 1.0f));
        Box cube = new Box(0.5f, 0.5f, 0.5f, Primitive.GENERATE_NORMALS, cubeAp);
        transl.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        TransformGroup cubeTG = new TransformGroup(transl);
        cubeTG.addChild(cube);
        tgContent.addChild(cubeTG);

        // Let Java 3D perform optimizations on this scene graph.
        root.compile();
        return root;
    }

    /**
     * The transformation matrix for the three dimensional shape is a 4*4 matrix
     * which represents translation, rotation and stretch in one matrix.
     * <p>
     * The upper left 3*3 matrix represents stretch and rotation, the right
     * column represents the translational component.
     * <p>
     * E.g. a transform matrix for a rectangle/ellipsoid without rotation looks
     * like this:
     * 
     * <pre>
     * s(x)  0    0   t(x)
     *  0   s(y)  0   t(y)
     *  0    0   s(z) t(z)
     *  0    0    0    1
     * </pre>
     * 
     * where s(x) is the stretch in x-dimension, t(y) is the translation in
     * y-dimension.
     * 
     * @param con
     *            the condition
     */
    private void transformationMatrix2Array(Condition con) {
        if (con instanceof ConditionRectangle) {
            // rectangle without rotation.
            double[] stretch = ((ConditionRectangle) con).getStretch();
            double[] center = ((ConditionRectangle) con).getCenter();
            for (int i = 0; i < 3; i++) {
                matrix[i][i] = stretch[i];
                matrix[i][3] = center[i];
            }
            // set other (rotational) positions to zero
            matrix[0][1] = matrix[0][2] = matrix[1][0] = matrix[1][2] = matrix[2][0] = matrix[2][1] = 0;
        } else if (con instanceof ConditionEllipsoid) {
            // ellipsoid without rotation
            double[] stretch = ((ConditionEllipsoid) con).getStretch();
            double[] center = ((ConditionEllipsoid) con).getCenter();
            for (int i = 0; i < 3; i++) {
                matrix[i][i] = stretch[i];
                matrix[i][3] = center[i];
            }
            // set other (rotational) positions to zero
            matrix[0][1] = matrix[0][2] = matrix[1][0] = matrix[1][2] = matrix[2][0] = matrix[2][1] = 0;
        } else if (con instanceof ConditionRotatingRectangle) {
            // rotating rectangle - matrix already computed.
            matrix = ((ConditionRotatingRectangle) con).getTransform();
        } else if (con instanceof ConditionRotatingEllipsoid) {
            // rotating ellipsoid - matrix already computed.
            matrix = ((ConditionRotatingEllipsoid) con).getTransform();
        } else {
            throw new IllegalArgumentException("Unknown condition type.");
        }
        // convert matrix to array and scale by relative visualized size
        float scale = ConditionsGUI2D3D.visualizedConditionSize;
        array[0] = matrix[0][0] * scale;
        array[1] = matrix[0][1] * scale;
        array[2] = matrix[0][2] * scale;
        array[4] = matrix[1][0] * scale;
        array[5] = matrix[1][1] * scale;
        array[6] = matrix[1][2] * scale;
        array[8] = matrix[2][0] * scale;
        array[9] = matrix[2][1] * scale;
        array[10] = matrix[2][2] * scale;
        array[3] = matrix[0][3];
        array[7] = matrix[1][3];
        array[11] = matrix[2][3];
    }

    /**
     * The Axis class represents the x-, y- and z-coordinate-axis using a
     * <code>IndexLineArray</code>.
     * 
     * @author Patrick Stalph
     */
    private class Axis extends Shape3D {
        private final static float axis0 = 0f;
        private final static float axis1 = 1.1f;
        private final static float arrowLength = 0.1f;
        private final static float dist = 0.1f;
        // derived for convenience
        private final static float arrow0 = axis1 - arrowLength;

        /**
         * Default constructor creates the axis visual object.
         */
        public Axis() {
            IndexedLineArray axisLines = new IndexedLineArray(18,
                    GeometryArray.COORDINATES, 30);
            // x-axis start > end
            axisLines.setCoordinate(0, new Point3f(axis0, 0f, 0f));
            axisLines.setCoordinate(1, new Point3f(axis1, 0f, 0f));
            // arrowhead endpoints
            axisLines.setCoordinate(2, new Point3f(arrow0, +dist, +dist));
            axisLines.setCoordinate(3, new Point3f(arrow0, +dist, -dist));
            axisLines.setCoordinate(4, new Point3f(arrow0, -dist, +dist));
            axisLines.setCoordinate(5, new Point3f(arrow0, -dist, -dist));
            // y-axis start > end
            axisLines.setCoordinate(6, new Point3f(0f, axis0, 0f));
            axisLines.setCoordinate(7, new Point3f(0f, axis1, 0f));
            // arrowhead endpoints
            axisLines.setCoordinate(8, new Point3f(+dist, arrow0, +dist));
            axisLines.setCoordinate(9, new Point3f(+dist, arrow0, -dist));
            axisLines.setCoordinate(10, new Point3f(-dist, arrow0, +dist));
            axisLines.setCoordinate(11, new Point3f(-dist, arrow0, -dist));
            // z-axis start > end
            axisLines.setCoordinate(12, new Point3f(0f, 0f, axis0));
            axisLines.setCoordinate(13, new Point3f(0f, 0f, axis1));
            // arrowhead endpoints
            axisLines.setCoordinate(14, new Point3f(+dist, +dist, arrow0));
            axisLines.setCoordinate(15, new Point3f(+dist, -dist, arrow0));
            axisLines.setCoordinate(16, new Point3f(-dist, +dist, arrow0));
            axisLines.setCoordinate(17, new Point3f(-dist, -dist, arrow0));
            // connect the lines
            axisLines.setCoordinateIndex(0, 0);
            axisLines.setCoordinateIndex(1, 1);
            axisLines.setCoordinateIndex(2, 2);
            axisLines.setCoordinateIndex(3, 1);
            axisLines.setCoordinateIndex(4, 3);
            axisLines.setCoordinateIndex(5, 1);
            axisLines.setCoordinateIndex(6, 4);
            axisLines.setCoordinateIndex(7, 1);
            axisLines.setCoordinateIndex(8, 5);
            axisLines.setCoordinateIndex(9, 1);
            axisLines.setCoordinateIndex(10, 6);
            axisLines.setCoordinateIndex(11, 7);
            axisLines.setCoordinateIndex(12, 8);
            axisLines.setCoordinateIndex(13, 7);
            axisLines.setCoordinateIndex(14, 9);
            axisLines.setCoordinateIndex(15, 7);
            axisLines.setCoordinateIndex(16, 10);
            axisLines.setCoordinateIndex(17, 7);
            axisLines.setCoordinateIndex(18, 11);
            axisLines.setCoordinateIndex(19, 7);
            axisLines.setCoordinateIndex(20, 12);
            axisLines.setCoordinateIndex(21, 13);
            axisLines.setCoordinateIndex(22, 14);
            axisLines.setCoordinateIndex(23, 13);
            axisLines.setCoordinateIndex(24, 15);
            axisLines.setCoordinateIndex(25, 13);
            axisLines.setCoordinateIndex(26, 16);
            axisLines.setCoordinateIndex(27, 13);
            axisLines.setCoordinateIndex(28, 17);
            axisLines.setCoordinateIndex(29, 13);
            this.setGeometry(axisLines);
        }
    }
}
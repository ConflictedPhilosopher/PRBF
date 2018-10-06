package xcsf;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

import xcsf.classifier.Classifier;
import xcsf.classifier.Condition;

/**
 * This implementation of a set is close to {@link java.util.ArrayList}, but
 * slightly specialized in order to increase the performance. The two classes
 * {@link Population} and {@link MatchSet} extend this abstract class.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
abstract class ClassifierSet implements Iterable<Classifier> {

    int size;
    Classifier[] elements;

    /**
     * Default constructor for subclasses only.
     */
    ClassifierSet() {
        this.elements = new Classifier[XCSFConstants.maxPopSize];
        this.size = 0;
    }

    /**
     * Returns an iterator over the classifiers in this set.
     * 
     * @return The <code>Iterator</code> over the <code>Classifier</code>
     *         objects of this set..
     */
    public Iterator<Classifier> iterator() {
        return new Itr();
    }

    /**
     * Returns the classifier at the specified <code>index</code>.
     * 
     * @param index
     *            the index of the classifier
     * @return the classifier at the given index
     */
    public Classifier get(int index) {
        this.rangeCheck(index);
        return elements[index];
    }

    /**
     * Returns the number of elements in this list.
     * 
     * @return the number of elements
     */
    public int size() {
        return size;
    }

    /**
     * Searches this set for a classifier equal to the given classifier. If no
     * such classifier is found, the method returns <code>null</code>.
     * 
     * @param condition
     *            the condition to search for
     * @return a classifier with an identical condition, if one is found;
     *         <code>null</code> otherwise;
     */
    Classifier findIdenticalCondition(Condition condition) {
        for (int i = 0; i < size; i++) {
            if (elements[i].getCondition().equals(condition)) {
                return elements[i];
            }
        }
        return null; // nothing found
    }

    /**
     * Sorts this array of <code>Classifier</code> objects according to the
     * order induced by the specified comparator.
     * 
     * @param comparator
     *            The comparator to determine the order of the array. A
     *            <tt>null</tt> value indicates that the elements'
     *            {@linkplain Comparable natural ordering} should be used.
     * @see Arrays#sort(Object[], int, int, Comparator)
     */
    void sort(Comparator<Classifier> comparator) {
        Arrays.sort(elements, 0, size, comparator);
    }

    /**
     * Removes all of the elements from this set. Actually the elements are not
     * removed, but the insertion index is set to zero.
     */
    void clear() {
        size = 0;
        // no null asignment for GC. This is called frequently for matchsets,
        // which are filled again anyways.
    }

    /**
     * Adds the specified <code>Classifier</code> to this set. This method DOES
     * NOT check for numerosity limits, because all callers do. The method is
     * synchronized to allow for multi-threaded matching.
     * 
     * @param classifier
     *            the classifier to be added
     */
    synchronized void add(Classifier classifier) {
        elements[size++] = classifier;
    }

    /**
     * Removes the element at the specified position in this list. Shifts any
     * subsequent elements to the left (subtracts one from their indices).
     * 
     * @param index
     *            the index of the element to be removed
     * @throws IndexOutOfBoundsException
     *             if the given <code>index</code> is not valid
     */
    void remove(int index) {
        rangeCheck(index);
        // shift subsequent elements
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved);
        }
        size--;
        // no null assignment for GC
    }

    /**
     * Removes the elements at the specified positions in this list. Shifts any
     * subsequent elements to the left (subtracts one from their indices).
     * 
     * @param indices
     *            the indices of the elements to be removed
     * @throws IndexOutOfBoundsException
     *             if any of the given <code>indices</code> is not valid
     */
    void remove(int[] indices) {
        Arrays.sort(indices);
        this.rangeCheck(indices[indices.length - 1]); // check largest index
        int i, numMoved;
        for (i = 0; i < indices.length - 1; i++) {
            // shift elements between indices
            numMoved = indices[i + 1] - indices[i] - 1;
            // shift by 1 + i
            System.arraycopy(elements, indices[i] + 1, elements,
                    indices[i] - i, numMoved);
        }
        // last call: upper border = this.size
        numMoved = size - indices[i] - 1;
        System.arraycopy(elements, indices[i] + 1, elements, indices[i] - i,
                numMoved);
        size -= indices.length;
        // no null assignment for GC
    }

    /**
     * Throws <code>IndexOutOfBoundsException</code> if the index exceeds the
     * size. Elements at this index may exists, but may not be accessed.
     * 
     * @param index
     *            the index to check
     */
    private void rangeCheck(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: "
                    + size);
    }

    /**
     * Implementation of the <code>Iterator</code> interface without check for
     * concurrent modification.
     * 
     * @author Patrick Stalph
     */
    class Itr implements Iterator<Classifier> {

        // Index of element to be returned by subsequent call to next.
        private int cursor = 0;
        // flag to indicate removal and prevent remove call before next call
        private boolean preventRemoval = true;

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#hasNext()
         */
        public boolean hasNext() {
            return this.cursor != size();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#next()
         */
        public Classifier next() {
            this.preventRemoval = false;
            return ClassifierSet.this.elements[this.cursor++];
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#remove()
         */
        public void remove() {
            if (this.preventRemoval) {
                return;
            }
            this.cursor--;
            this.preventRemoval = true;
            ClassifierSet.this.remove(this.cursor);
        }
    }
}

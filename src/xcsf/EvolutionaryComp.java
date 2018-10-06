package xcsf;

import java.util.ArrayList;

import xcsf.classifier.Classifier;
import xcsf.classifier.Condition;
import xcsf.classifier.Prediction;

/**
 * This class encapsulates the evolutionary process of XCSF, that is selection,
 * crossover, mutation and insertion/subsumption. However, the {@link Condition}
 * and {@link Prediction} classes specify their own crossover and mutation
 * routines, thus the details <i>how</i> the variation works is left to the
 * corresponding classes.
 * 
 * @author Patrick O. Stalph, Martin V. Butz
 */
class EvolutionaryComp {

    // currently, the selection size is fixed. However, the code is able to
    // cope with various sizes (cf. Stalph 2008, IWLCS workshop)
    private final static int SELECTION_SIZE = 2;
    // indicates that condensation is active
    private boolean condensation;

    /**
     * Default constructor.
     */
    EvolutionaryComp() {
        this.condensation = false;
    }

    /**
     * Starts the evolutionary process.
     * 
     * @param population
     *            the current population.
     * @param matchSet
     *            the matchset to evolve.
     * @param state
     *            the state, this <code>matchSet</code> matched.
     * @param iteration
     *            the current iteration.
     */
    void evolve(Population population, MatchSet matchSet,
            StateDescriptor state, int iteration) {
        // calculate some derived values
        double avgTimestampSum = 0.0;
        double fitnessSum = 0.0;
        int numerositySum = 0;
        for (int i = 0; i < matchSet.size; i++) {
            Classifier cl = matchSet.elements[i];
            fitnessSum += cl.getFitness();
            avgTimestampSum += cl.getTimestamp() * cl.getNumerosity();
            numerositySum += cl.getNumerosity();
        }
        avgTimestampSum /= numerositySum;

        // Don't do a GA if the theta_GA threshold is not reached, yet
        if (iteration - avgTimestampSum < XCSFConstants.theta_GA) {
            return;
        }
        // update timestamp of the matchset
        for (int i = 0; i < matchSet.size; i++) {
            matchSet.elements[i].setTimestamp(iteration);
        }

        // ---[ selection ]---
        ArrayList<Classifier> parents = selection(matchSet, fitnessSum);
        ArrayList<Classifier> offspring = new ArrayList<Classifier>(parents
                .size());
        for (Classifier cl : parents) {
            // note that this is not really a clone: experience = 0,
            // numerosity = 1, fitness = old.fitness / old.numerosity
            offspring.add(cl.reproduce());
        }

        // ---[ crossover & mutation ]---
        if (!this.condensation) {
            // select classifier pairs. start from end of vector
            int index = offspring.size() - 1;
            while (index > 0) { // two indices/classifiers left
                Classifier cl1 = offspring.get(index--);
                Classifier cl2 = offspring.get(index--);
                // crossover & mutation
                cl1.crossover(cl2);
                cl1.mutation();
                cl2.mutation();
            }
            // mutate last classifier without crossover, if size is odd
            if (index == 0) {
                offspring.get(0).mutation();
            }
        }

        // ---[ insertion ]---
        this.insertion(offspring, parents, matchSet, population, state);
    }

    /**
     * Sets the condensation (cf. Wilson 1998) flag. If <code>true</code>, the
     * mutation and crossover are turned off, while reproduction, selection, and
     * deletion stil work. This way the population is refined and its size in
     * terms of macro classifiers reduced over time.
     * 
     * @param value
     *            the value to set
     */
    void setCondensation(boolean value) {
        this.condensation = value;
    }

    /**
     * Selects {link #SELECTION_SIZE} classifiers from the matchset using either
     * tournament selection or roulette wheel selection (depending on
     * {@link XCSFConstants#selectionType}.
     * 
     * @param matchset
     *            the matchset to select from
     * @param fitnessSum
     *            the sum of fitnesses of the <code>matchset</code>
     * @return the selected classifiers
     */
    ArrayList<Classifier> selection(MatchSet matchset, double fitnessSum) {
        ArrayList<Classifier> selection = new ArrayList<Classifier>(
                SELECTION_SIZE);
        if (XCSFConstants.selectionType == 0) {
            // roulette Wheel Selection
            for (int i = 0; i < SELECTION_SIZE; i++) {
                Classifier cl = selectClassifierRW(matchset, fitnessSum);
                selection.add(cl);
            }
        } else {
            // tournament selection
            for (int i = 0; i < SELECTION_SIZE; i++) {
                Classifier cl = selectClassifierTS(matchset);
                selection.add(cl);
            }
        }
        return selection;
    }

    /**
     * Inserts the reproduced classifiers in the population. If this exceeds the
     * maximum size (specified by {@link XCSFConstants#maxPopSize}), classifiers
     * are deleted from the population before insertion. Furthermore, the
     * inserted classifiers may be subsumed by others in the population.
     * 
     * @param offspring
     *            the offspring to insert
     * @param parents
     *            the parents of the given <code>offspring</code> (same order!)
     * @param matchSet
     *            the matchset
     * @param population
     *            the population to insert into
     * @param state
     *            the machted state
     */
    void insertion(ArrayList<Classifier> offspring,
            ArrayList<Classifier> parents, MatchSet matchSet,
            Population population, StateDescriptor state) {
        // don't exceed population.maxSize
        int numerositySum = 0;
        for (int i = 0; i < population.size; i++) {
            numerositySum += population.elements[i].getNumerosity();
        }
        int toDelete = numerositySum + offspring.size()
                - XCSFConstants.maxPopSize;
        if (toDelete > 0) {
            population.deleteWorstClassifiers(toDelete);
        }

        // insert new classifiers
        if (XCSFConstants.doGASubsumption) {
            // subsumption
            for (int i = 0; i < offspring.size(); i++) {
                Classifier cl = offspring.get(i);
                if (cl.doesMatch(state)) {
                    subsumeClassifier(cl, parents, population, matchSet);
                } else {
                    insertClassifier(cl, population, matchSet, false);
                }
            }
        } else {
            // no subsumption
            for (Classifier cl : offspring) {
                insertClassifier(cl, population, matchSet, cl.doesMatch(state));
            }
        }
    }

    /**
     * Tries to subsume the given offspring classifier. Therefore the parents
     * and the current matchset is checked for subsumers. If none are found, the
     * classifier is added to the population in the regular way.
     * 
     * @param offspring
     *            the classifier to subsume
     * @param parents
     *            the parents at this iteration of the evolution (not
     *            necessarily the parents of the offspring classifier)
     * @param population
     *            the population
     * @param matchSet
     *            the matchset
     */
    private static void subsumeClassifier(Classifier offspring,
            ArrayList<Classifier> parents, Population population,
            MatchSet matchSet) {
        // 1) check parents
        for (Classifier clP : parents) {
            if (clP.canSubsume() && clP.isMoreGeneral(offspring)) {
                clP.addNumerosity(1);
                return;
            }
        }
        // 2) check matchSet
        ArrayList<Classifier> choices = new ArrayList<Classifier>();
        for (int i = 0; i < matchSet.size; i++) {
            Classifier cl = matchSet.elements[i];
            if (cl.canSubsume() && cl.isMoreGeneral(offspring)) {
                choices.add(cl);
            }
        }
        if (choices.size() > 0) {
            int index = (int) (XCSFUtils.Random.uniRand() * choices.size());
            choices.get(index).addNumerosity(1);
            return;
        }
        // 3) If no subsumer was found, add the classifier to the population
        insertClassifier(offspring, population, matchSet, true);
    }

    /**
     * Inserts the classifier into the given population. If an identical
     * classifier is found in the population, this classifiers numerosity is
     * increased instead of insertion of the new classifier.
     * 
     * @param cl
     *            the classifier to insert
     * @param population
     *            the population
     * @param matchSet
     *            the matchset
     * @param doesMatch
     *            <code>true</code>, if the classifier matches the current
     *            state; <code>false</code> otherwise. The derived value is
     *            given to this method to avoid multiple matching calls.
     */
    private static void insertClassifier(Classifier cl, Population population,
            MatchSet matchSet, boolean doesMatch) {
        if (doesMatch) {
            // 1. doesMatch && matchSet.getIdenticalCl != null
            Classifier identical = matchSet.findIdenticalCondition(cl
                    .getCondition());
            if (identical != null) {
                identical.addNumerosity(1);
                return;
            }
        } else {
            // 2. !doesMatch && pop.getIdentical != null
            Classifier identical = population.findIdenticalCondition(cl
                    .getCondition());
            if (identical != null) {
                identical.addNumerosity(1);
                return;
            }
        }
        // 3. no identical classifier found: simply add the new one
        population.add(cl);
    }

    /**
     * Selects one classifier using roulette wheel selection according to the
     * fitnesses of the classifiers in the matchset.
     * 
     * @param matchSet
     *            the matchset
     * @param fitSum
     *            the sum of fitnesses in the matchset
     * @return the selected classifier
     */
    private static Classifier selectClassifierRW(MatchSet matchSet,
            double fitSum) {
        double choiceP = XCSFUtils.Random.uniRand() * fitSum;
        int i = 0;
        Classifier cl = matchSet.elements[i];
        double sum = cl.getFitness();
        while (choiceP > sum) {
            i++;
            cl = matchSet.elements[i];
            sum += cl.getFitness();
        }
        return cl;
    }

    /**
     * Selects a classifier using set-size proportionate tournament selection.
     * 
     * @param matchSet
     *            the matchset
     * @return the selected classifier
     */
    private static Classifier selectClassifierTS(MatchSet matchSet) {
        Classifier selected = null;
        double bestFitness = 0;
        while (selected == null) {
            for (int i = 0; i < matchSet.size; i++) {
                Classifier cl = matchSet.elements[i];
                double microFitness = cl.getFitness() / cl.getNumerosity();
                for (int j = 0; j < cl.getNumerosity(); j++) {
                    if ((XCSFUtils.Random.uniRand() < XCSFConstants.selectionType)
                            && (selected == null || microFitness > bestFitness)) {
                        selected = cl;
                        bestFitness = microFitness;
                        // move to next classifier in the matchset.
                        break;
                    }

                }
            }
        }
        return selected;
    }
}

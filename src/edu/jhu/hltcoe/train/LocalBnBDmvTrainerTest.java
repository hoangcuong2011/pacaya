package edu.jhu.hltcoe.train;

import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;

import edu.jhu.hltcoe.data.SentenceCollection;
import edu.jhu.hltcoe.gridsearch.dmv.BnBDmvTrainerTest;
import edu.jhu.hltcoe.gridsearch.dmv.CptBoundsDeltaFactory;
import edu.jhu.hltcoe.gridsearch.dmv.DmvDantzigWolfeRelaxation;
import edu.jhu.hltcoe.gridsearch.dmv.DmvProblemNode;
import edu.jhu.hltcoe.gridsearch.dmv.DmvDantzigWolfeRelaxation.CutCountComputer;
import edu.jhu.hltcoe.util.Prng;

public class LocalBnBDmvTrainerTest {

    static {
        BasicConfigurator.configure();
        // Logger.getRootLogger().setLevel(Level.TRACE);
    }

    @Before
    public void setUp() {
        Prng.seed(1234567890);
        DmvProblemNode.clearActiveNode();
    }

    @Test
    public void testTwo() {
        double epsilon = 0.4;
        int numRestarts = 1;
        double offsetProb = 0.1;
        double probOfSkipCm = 0.1;
        double bnbTimeoutSeconds = 2;
        double timeoutSeconds = 10;
        ViterbiTrainer viterbiTrainer = ViterbiTrainerTest.getDefaultCkyViterbiTrainer();
        DmvDantzigWolfeRelaxation relax = new DmvDantzigWolfeRelaxation(null, 1, new CutCountComputer());
        CptBoundsDeltaFactory brancher = BnBDmvTrainerTest.getDefaultBrancher();
        LocalBnBDmvTrainer trainer = new LocalBnBDmvTrainer(viterbiTrainer, epsilon, brancher, relax, bnbTimeoutSeconds, numRestarts,
                offsetProb, probOfSkipCm, timeoutSeconds, null);

        SentenceCollection sentences = new SentenceCollection();
        sentences.addSentenceFromString("the cat");
        sentences.addSentenceFromString("the hat");
        trainer.train(new DmvTrainCorpus(sentences));
    }

}

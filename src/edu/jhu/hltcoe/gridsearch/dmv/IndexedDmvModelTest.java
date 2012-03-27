package edu.jhu.hltcoe.gridsearch.dmv;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import edu.jhu.hltcoe.data.DepTree;
import edu.jhu.hltcoe.data.Sentence;
import edu.jhu.hltcoe.data.SentenceCollection;
import edu.jhu.hltcoe.data.WallDepTreeNode;
import edu.jhu.hltcoe.data.Word;
import edu.jhu.hltcoe.model.dmv.DmvModel;
import edu.jhu.hltcoe.parse.pr.DepSentenceDist;


public class IndexedDmvModelTest {

    static {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.TRACE);
    }
    
    @Test
    public void testCounts() {
        SentenceCollection sentences = new SentenceCollection();
        sentences.addSentenceFromString("N V");
        sentences.addSentenceFromString("N V N N");
        sentences.addSentenceFromString("D N");
        IndexedDmvModel idm = new IndexedDmvModel(sentences);
                
        assertEquals(1+6+3*2*2, idm.getNumConds());
        // Root
        assertEquals(3, idm.getNumParams(0));
        // Child
        assertEquals(3, idm.getNumParams(4));
        // Decision
        assertEquals(2, idm.getNumParams(10));
        
        assertEquals("child_{0,0,0}(2)", idm.getName(1,2));
        

        
        for (int i=0; i<idm.getNumSentVars(0); i++) {
            int c = idm.getC(0, i);
            int m = idm.getM(0, i);
            System.out.println(idm.getName(c, m));
        }
        assertEquals(2+2+(1+3+1+3), idm.getNumSentVars(0));
        
        int c,m,s;
        // Root: D
        c = 0;
        m = 2;
        s = 2;
        assertEquals("root(2)", idm.getName(c,m));
        // Should exist in sent 2
        assertEquals(1, idm.getMaxFreq(s, idm.getSi(s, c, m)));
        // Should not exists in sent 0 or 1
        assertEquals(-1, idm.getSi(0, c, m));
        assertEquals(-1, idm.getSi(1, c, m));
        
        // Child: Noun --> Noun, Left
        c = 1;
        m = 0;
        s = 1;
        assertEquals("child_{0,0,0}(0)", idm.getName(c,m));
        // Should exist in sent 1
        assertEquals(3, idm.getMaxFreq(s, idm.getSi(s, c, m)));
        // Should not exist in sent 0 or 2 (sent 2 should only have the right edge)
        assertEquals(-1, idm.getSi(0, c, m));
        assertEquals(-1, idm.getSi(2, c, m));
        
        // Decision: 
        c = 1+6;
        m = 0;
        s = 1;
        assertEquals("dec_{0,0,0}(0)", idm.getName(c,m));
        // Should exist in sent 1
        assertEquals(3, idm.getMaxFreq(s, idm.getSi(s, c, m)));
        s=0;
        assertEquals(1, idm.getMaxFreq(s, idm.getSi(s, c, m)));
        s=2;
        assertEquals(1, idm.getMaxFreq(s, idm.getSi(s, c, m)));
        
        int[][] maxFreqCm = idm.getTotalMaxFreqCm();
        assertEquals(2, maxFreqCm[0][1]);
    }
    
    @Test
    public void testDepSentenceDist() {
        SentenceCollection sentences = new SentenceCollection();
        sentences.addSentenceFromString("N V");
        sentences.addSentenceFromString("N V N N");
        sentences.addSentenceFromString("D N");
        IndexedDmvModel idm = new IndexedDmvModel(sentences);

        System.out.print("alphabet: ");
        for (int i=0; i<sentences.getLabelAlphabet().size(); i++){
            System.out.printf("(%d,%s)\n",i,sentences.getLabelAlphabet().lookupIndex(i));
        }
        
        
        int s=0;
        double[] sentParams = new double[idm.getNumSentVars(s)];

        assertEquals("child_{0,1,0}(1)", idm.getName(2,1));
        Arrays.fill(sentParams, 0.5);
        // Root --> Verb
        sentParams[idm.getSi(s, 0, 1)] = 0.1;
        // Noun --> Verb, Right
        sentParams[idm.getSi(s, 2, 1)] = 1.0;
        // Noun, Left, Adj, Stop
        sentParams[idm.getSi(s, 7, 0)] = 1.5;
        DepSentenceDist dsd = idm.getDepSentenceDist(sentences.get(0), s, sentParams);
        for (int i=0; i<dsd.child.length; i++) {
            for (int j=0; j<dsd.child[i].length; j++) {
                System.out.printf("(%d,%d)=%f\n", i,j,dsd.child[i][j][0]);
            }
        }
        assertEquals(0.1, dsd.root[1], 1e-13);
        assertEquals(1.0, dsd.child[1][0][0], 1e-13);
        assertEquals(1.5, dsd.decision[0][0][0][0], 1e-13);
    }
    
    @Test
    public void testGetDmvModel() {
        SentenceCollection sentences = new SentenceCollection();
        sentences.addSentenceFromString("N V");
        sentences.addSentenceFromString("N V N N");
        sentences.addSentenceFromString("D N");
        IndexedDmvModel idm = new IndexedDmvModel(sentences);
        
        double[][] logProbs = new double[idm.getNumConds()][];
        for (int c=0; c<logProbs.length; c++) {
            logProbs[c] = new double[idm.getNumParams(c)];
            Assert.assertTrue(logProbs[c].length == 2 || logProbs[c].length == 3);
        }
        
        logProbs[0][1] = Math.log(0.2);
        logProbs[1][1] = Math.log(0.4);
        logProbs[3][0] = Math.log(0.6);
        
        DmvModel model = idm.getDmvModel(logProbs);
        
        assertEquals(0.2, model.getChooseWeights(WallDepTreeNode.WALL_LABEL, "l").get(new Word("V")), 1e-13);
        assertEquals(0.4, model.getChooseWeights(new Word("N"), "l").get(new Word("V")), 1e-13);
        assertEquals(0.6, model.getChooseWeights(new Word("V"), "l").get(new Word("N")), 1e-13);
    }
    

    @Test
    public void testGetSentSol() {
        SentenceCollection sentences = new SentenceCollection();
        sentences.addSentenceFromString("N V");
        sentences.addSentenceFromString("N V N N N");
        sentences.addSentenceFromString("D N");
        IndexedDmvModel idm = new IndexedDmvModel(sentences);
        
        int s = 1;
        Sentence sentence = sentences.get(s);
        DepTree depTree = new DepTree(sentence, new int[]{1, -1, 4, 4, 1}, true);
        
        int[] sentSol = idm.getSentSol(sentence, s, depTree);
        System.out.println("Sentence solution: "); //Arrays.toString(sentSol));
        for (int i=0; i<sentSol.length; i++) {
            int c = idm.getC(s, i);
            int m = idm.getM(s, i);
            System.out.println(i + ": " + idm.getName(c, m) + " " + sentSol[i]);
        }
        
        assertEquals(1, sentSol[1]);
        // noun --> noun left
        assertEquals(2, sentSol[2]);
        // stop right noun adj
        assertEquals(4, sentSol[12]); 
        // cont left noun adj
        assertEquals(1, sentSol[9]); 
        // cont left noun non-adj
        assertEquals(1, sentSol[11]); 
    }
    
}
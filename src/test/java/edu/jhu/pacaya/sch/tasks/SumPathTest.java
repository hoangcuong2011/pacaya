package edu.jhu.pacaya.sch.tasks;

import static edu.jhu.pacaya.sch.graph.DiEdge.edge;
import static edu.jhu.pacaya.sch.util.Indexed.enumerate;
import static edu.jhu.pacaya.sch.util.TestUtils.toArray;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.junit.Test;

import edu.jhu.pacaya.sch.Schedule;
import edu.jhu.pacaya.sch.graph.DiEdge;
import edu.jhu.pacaya.sch.graph.WeightedIntDiGraph;
import edu.jhu.pacaya.sch.tasks.SumPaths.RecordingDoubleConsumer;
import edu.jhu.pacaya.sch.util.Indexed;
import edu.jhu.pacaya.sch.util.ScheduleUtils;
import edu.jhu.pacaya.sch.util.dist.TruncatedNormal;

public class SumPathTest {
    // tolerance for double comparison
    private static double tol = 1E-9;

    @Test
    public void testSumWalks() {
        RealMatrix m = new Array2DRowRealMatrix(
                new double[][] { { 0.3, 0.0, 0.3 }, { 0.1, 0.6, 0.7 }, { 0.0, 0.4, 0.2 }, });
        WeightedIntDiGraph g = WeightedIntDiGraph.fromMatrix(m);
        RealVector s = new ArrayRealVector(new double[] { 1, 2, 3 });
        RealVector t = new ArrayRealVector(new double[] { 5, 7, 4 });
        {
            RecordingDoubleConsumer record = new RecordingDoubleConsumer();
            List<DiEdge> emptySchedule = Arrays.asList();
            assertEquals(31.0, SumPaths.approxSumPaths(g, s, t, emptySchedule.iterator(), record), tol);
            assertEquals(31.0, record.getRecord().get(0), tol);
            assertEquals(Arrays.asList(31.0), record.getRecord());
        }
        {
            RecordingDoubleConsumer record = new RecordingDoubleConsumer();
            assertEquals(39.66, SumPaths.approxSumPaths(g, s, t, Arrays.asList(edge(1, 2), edge(0, 2), edge(0, 0), edge(0, 2)).iterator(),
                    record), tol);
            assertArrayEquals(new double[] { 31.0, 36.6, 37.8, 39.3, 39.66 }, toArray(record.getRecord()), tol);

        }
        {
            assertEquals(1509.9999999999886, SumPaths.approxSumPaths(g, s, t, ScheduleUtils.cycle(g.getEdges().iterator(), 1000), null), tol);
        }
        
    }

    @Test
    public void testSumWalks2() {
        RealMatrix m = new Array2DRowRealMatrix(
                new double[][] { { 0.3, 0.0, 0.3 }, { 0.1, 0.6, 0.7 }, { 0.0, 0.4, 0.2 }, });
        WeightedIntDiGraph g = WeightedIntDiGraph.fromMatrix(m);
        RealVector s = new ArrayRealVector(new double[] { 1, 2, 3 });
        RealVector t = new ArrayRealVector(new double[] { 5, 7, 4 });
//      edge(0, 0), // 0
//      edge(0, 1), // --- has 0.0
//      edge(0, 2), // 1
//      edge(1, 0), // 2
//      edge(1, 1), // 3
//      edge(1, 2), // 4
//      edge(2, 0), // --- has 0.0
//      edge(2, 1), // 5
//      edge(2, 2), // 6

        assertEquals(7, g.getEdges().size());

        Schedule sch = new Schedule(4, 1, 0, 1);
        {
            // if all prob mass is on a single timestep
            double sigma = 1E-9;
            for (Indexed<Double> finalResult : enumerate(Arrays.asList(31.0, 36.6, 37.8, 39.3, 39.66))) {
                for (double lambda : Arrays.asList(0.0, 0.3)) {
                    SumPaths sp = new SumPaths(g,  s, t, lambda, sigma);
                    double goldSum = sp.getGold();
                    sch.setHaltTime(finalResult.index());
                    assertEquals(-(goldSum - finalResult.get()) - lambda * finalResult.index(), sp.score(sch), tol);
                }
            }
        }
        {
            // if prob mass is spread
            double sigma = 1;
            int haltTime = 2;
            double lambda = 0.3;
            sch.setHaltTime(haltTime);
            SumPaths sp = new SumPaths(g,  s, t, 0.3, sigma);
            double gs = sp.getGold();
            List<Double> haltProbs = Arrays.asList(0,1,2,3,4).stream().map(i -> TruncatedNormal.probabilityTruncZero(i, i+1, haltTime + 0.5, sigma)).collect(Collectors.toList());
            List<Double> results = Arrays.asList(31.0, 36.6, 37.8, 39.3, 39.66);
            assertEquals(haltProbs.size(), results.size());
            double expectedTotal = 0.0;
            double probUsed = 0.0;
            for (int i = 0; i < haltProbs.size(); i++) {
                double thisResult = (results.get(i) - gs) - lambda * i;
                double thisProb = haltProbs.get(i);
                expectedTotal += thisResult * thisProb;
                probUsed += thisProb;
            }
            expectedTotal += (1.0 - probUsed) * (results.get(results.size() - 1) - lambda * TruncatedNormal.meanTruncLower(haltTime + 0.5, sigma, haltProbs.size()));
            // problem: even though the accuracy won't change over the
            // rest of time, time itself will
            // we want to compute the mean of the part of the guassian in the tail
            // we w 
            assertEquals(expectedTotal, sp.score(sch), tol); 
        }


        
        /*
        
        assertEquals(39.66, SumPaths.approxSumPaths(g, s, t, Arrays.asList(edge(1, 2), edge(0, 2), edge(0, 0), edge(0, 2)).iterator(),
                record), tol);
        assertArrayEquals(new double[] { 31.0, 36.6, 37.8, 39.3, 39.66 }, toArray(record.getRecord()), tol);

        sp.score(s)
        RecordingDoubleConsumer record = new RecordingDoubleConsumer();
        assertEquals(31.0, SumPaths.approxSumPaths(g, s, t, emptySchedule.iterator(), record), tol);
  */      
    }

}

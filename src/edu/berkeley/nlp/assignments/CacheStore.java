package edu.berkeley.nlp.assignments;

import edu.berkeley.nlp.util.Pair;

/**
 * @author Keith Stone
 */
public class CacheStore {
    double unaryMax;
    double binaryMax;
    PCFGParserTester.UnaryRule unaryRule;
    PCFGParserTester.BinaryRule binaryRule;
    int splitIndex;

    public CacheStore() {
        binaryMax  = Double.NEGATIVE_INFINITY;
        unaryMax   = Double.NEGATIVE_INFINITY;
        splitIndex = -1;
    }

    public double getUnaryMax() {
        return unaryMax;
    }

    public double getBinaryMax() {
        return binaryMax;
    }

    public PCFGParserTester.UnaryRule getUnaryRule() {
        return unaryRule;
    }

    public PCFGParserTester.BinaryRule getBinaryRule() {
        return binaryRule;
    }

    public int getSplitIndex() {
        return splitIndex;
    }

    public void addUnaryRule(double score, PCFGParserTester.UnaryRule rule) {
        if (score > unaryMax) {
            unaryMax = score;
            unaryRule = rule;
        }
    }

    public void addBinaryRule(double score, PCFGParserTester.BinaryRule rule, int split) {
        if (score > binaryMax) {
            this.binaryMax  = score;
            this.binaryRule = rule;
            this.splitIndex = split;
        }
    }
}

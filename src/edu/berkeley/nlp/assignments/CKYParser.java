package edu.berkeley.nlp.assignments;

import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.util.CollectionUtils;

import java.util.*;

/**
 * @author Keith Stone
 */
public class CKYParser extends PCFGParserTester.Parser {
    PCFGParserTester.Lexicon lexicon;
    PCFGParserTester.UnaryClosure uc;
    PCFGParserTester.Grammar grammar;

    Map<String, Tree<String>> binaryPiTrees;
    Map<String, Tree<String>> unaryPiTrees;
    Map<String, Double>       binaryPiScores;
    Map<String, Double>       unaryPiScores;
    Set<String>               rootTags;

    public CKYParser(List<Tree<String>> trainTrees) {
        System.out.print("Annotating / binarizing training trees ... ");
        List<Tree<String>> annotatedTrainTrees = annotateTrees(trainTrees);
        System.out.println("done.");

        System.out.print("Building grammar ... ");
        grammar = new PCFGParserTester.Grammar(annotatedTrainTrees);
        System.out.println("done. (" + grammar.getStates().size() + " states)");
        uc = new PCFGParserTester.UnaryClosure(grammar);
        //System.out.println(uc);

        // Store all possible start tags for parsing
        rootTags = new HashSet<String>();

        for (Tree<String> tree : trainTrees) {
            rootTags.add(tree.getLabel());
        }

        lexicon = new PCFGParserTester.Lexicon(annotatedTrainTrees);

        System.out.println("done.");
    }

    public Tree<String> getBestParse(List<String> sentence) {
        binaryPiTrees  = new HashMap<String, Tree<String>>();
        unaryPiTrees   = new HashMap<String, Tree<String>>();
        binaryPiScores = new HashMap<String, Double>();
        unaryPiScores  = new HashMap<String, Double>();

        double binary_max = 0.0;
        double unary_max = 0.0;
        String binaryTag = "ROOT";
        String unaryTag  = "ROOT";
        for (String tag : rootTags) {
            if (sentence.size() > 1) {
                double binary_score = binaryPi(sentence, 0, sentence.size() - 1, tag);
                if (binary_score > binary_max) {
                    binary_max = binary_score;
                    binaryTag = tag;
                }
            }

            double unary_score  =  unaryPi(sentence, 0, sentence.size() - 1, tag);
            if (unary_score > unary_max) {
                unary_max = unary_score;
                unaryTag = tag;
            }
        }

        Tree<String> annotatedBestParse;
        if (binary_max > unary_max) {
            annotatedBestParse = binaryPiTrees.get(cacheKey(0, sentence.size() - 1, binaryTag));
        } else {
            annotatedBestParse =  unaryPiTrees.get(cacheKey(0, sentence.size() - 1, unaryTag));
        }
        return PCFGParserTester.TreeAnnotations.unAnnotateTree(annotatedBestParse);
    }

    private double unaryPi(List<String> sentence, int i, int j, String tag) {
        // Illegal
        if (i > j) {
            throw new IllegalArgumentException("I is not allowed to be larger than J");
        }
        // Always check the cache first
        String cache_key = cacheKey(i,j,tag);
        if (unaryPiScores.containsKey(cache_key)) {
            return unaryPiScores.get(cache_key);
        }
        if (i == j) {
            // Base case
            double max = 0.0;
            List<PCFGParserTester.UnaryRule> closure = uc.getClosedUnaryRulesByParent(tag);
            for (PCFGParserTester.UnaryRule rule : closure) {
                double ruleScore =  rule.getScore();
                double expansionScore = lexicon.scoreTagging(sentence.get(i), rule.getChild());
                double score = ruleScore * expansionScore;
                if (score > max) {
                    max = score;
                    Tree<String> word = new Tree<String>(sentence.get(i));
                    Tree<String> toTerminal = new Tree<String>(rule.getChild(), Collections.singletonList(word));
                    Tree<String> tree = new Tree<String>(rule.getParent(), Collections.singletonList(toTerminal));
                    unaryPiScores.put(cache_key, score);
                    unaryPiTrees .put(cache_key, tree);
                }
            }
            return max;
        } else {
            // Recursion
            double max = 0.0;
            List<PCFGParserTester.UnaryRule> closure = uc.getClosedUnaryRulesByParent(tag);
            for (PCFGParserTester.UnaryRule rule : closure) {
                double ruleScore      = rule.getScore();
                double expansionScore = binaryPi(sentence, i, j, rule.getChild());
                double score = ruleScore * expansionScore;
                if (score > max) {
                    max = score;
                    Tree<String> child = binaryPiTrees.get(cacheKey(i, j, rule.getChild()));
                    Tree<String> tree = new Tree<String>(rule.getParent(), Collections.singletonList(child));

                    unaryPiScores.put(cache_key, score);
                    unaryPiTrees .put(cache_key, tree);
                }
            }
            return max;
        }
    }

    private double binaryPi(List<String> sentence, int i, int j, String tag) {
        // Illegal
        if (i > j) {
            throw new IllegalArgumentException("I is not allowed to be larger than J");
        }
        // Always check the cache first
        String cache_key = cacheKey(i,j,tag);
        if (binaryPiScores.containsKey(cache_key)) {
            return binaryPiScores.get(cache_key);
        }
        if (i == j) {
            throw new IllegalArgumentException("Cannot binary split a single node");
        } else {
            double max_score = 0.0;
            for (int s = i + 1; s <= j; s++) {
                List<PCFGParserTester.BinaryRule> binaryRules = grammar.getBinaryRulesByParent(tag);
                for ( PCFGParserTester.BinaryRule binaryRule : binaryRules) {
                    double ruleScore  = binaryRule.getScore();
                    double leftScore  = unaryPi(sentence, i, s - 1, binaryRule.getLeftChild()) ;
                    double rightScore = unaryPi(sentence, s,     j, binaryRule.getRightChild());
                    double score = ruleScore * leftScore * rightScore;
                    if (score > max_score) {
                        max_score = score;

                        Tree<String> leftTree  = unaryPiTrees.get(cacheKey(i, s - 1, binaryRule.getLeftChild()));
                        Tree<String> rightTree = unaryPiTrees.get(cacheKey(s,     j, binaryRule.getRightChild()));
                        List<Tree<String>> children = new ArrayList<Tree<String>>();
                        children.add(leftTree);
                        children.add(rightTree);
                        Tree<String> tree = new Tree<String>(binaryRule.getParent(), children);

                        binaryPiScores.put(cache_key, score);
                        binaryPiTrees .put(cache_key, tree);
                    }
                }
            }

            return max_score;
        }
    }

    private String cacheKey(int i, int j, String tag) {
        return i + "_" + j + "_" + tag;
    }
}

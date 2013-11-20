package edu.berkeley.nlp.assignments;

import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.util.CollectionUtils;

import java.util.*;

/**
 * @author Keith Stone
 */
public class CKYParser extends PCFGParserTester.Parser {
    PCFGParserTester.UnaryClosure uc;
    PCFGParserTester.Grammar grammar;

    Map<String, Tree<String>> binaryPiTrees;
    Map<String, Tree<String>> unaryPiTrees;
    Map<String, Double>       binaryPiScores;
    Map<String, Double>       unaryPiScores;
    Set<String>               rootTags;
    PCFGParserTester.TreeAnnotations.MarkovContext context;

    public CKYParser(List<Tree<String>> trainTrees,  PCFGParserTester.TreeAnnotations.MarkovContext context) {
        this.context = context;

        System.out.print("Annotating / binarizing training trees ... ");
        List<Tree<String>> annotatedTrainTrees = annotateTrees(trainTrees, context);
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

        // Oops case, create the default tree.
        if (Math.max(binary_max, unary_max) == 0.0) {
             return default_parse(sentence);
        }

        normalize_structure(annotatedBestParse);

        return PCFGParserTester.TreeAnnotations.unAnnotateTree(annotatedBestParse);
    }

    private void normalize_structure(Tree<String> tree) {
        if (tree.getChildren().size() == 1) {
            Tree<String> child = tree.getChildren().get(0);
            if (tree.getLabel().equals(child.getLabel())) {
                tree.setChildren(child.getChildren());
            } else {
                List<String> path = uc.getPath(new PCFGParserTester.UnaryRule(tree.getLabel(), child.getLabel()));
                if (path.size() > 2) {
                    Tree<String> topOfChain = null;
                    for (int i = path.size() - 1; i >= 0; i--) {
                        topOfChain = new Tree<String>(path.get(i), (i == path.size() - 1) ? child.getChildren() : Collections.singletonList(topOfChain));
                    }
                    tree.setChildren(Collections.singletonList(topOfChain));
                }
            }
            for (Tree<String> grandchild : child.getChildren()) {
                normalize_structure(grandchild);
            }
        } else {
            for (Tree<String> child : tree.getChildren()) {
                normalize_structure(child);
            }
        }
    }

    private Tree<String> default_parse(List<String> sentence) {
        List<Tree<String>> children = new ArrayList<Tree<String>>();
        for (String word : sentence) {
            String tag = getBestTag(word);
            Tree<String> wordTree = new Tree<String>(word, new ArrayList<Tree<String>>());
            children.add(new Tree<String>(tag, Collections.singletonList(wordTree)));
        }
        return new Tree<String>("ROOT", children);
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
        double max = Double.NEGATIVE_INFINITY;
        if (i == j) {
            // Base case
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
        } else {
            // Recursion
            List<PCFGParserTester.UnaryRule> closure = uc.getClosedUnaryRulesByParent(tag);
            for (PCFGParserTester.UnaryRule rule : closure) {
                String childRule = rule.getChild();
                double ruleScore      = rule.getScore();
                double expansionScore = binaryPi(sentence, i, j, childRule);
                double score = ruleScore * expansionScore;
                if (score > max) {
                    max = score;
                    Tree<String> child = binaryPiTrees.get(cacheKey(i, j, childRule));
                    Tree<String> tree = new Tree<String>(rule.getParent(), Collections.singletonList(child));

                    unaryPiScores.put(cache_key, score);
                    unaryPiTrees .put(cache_key, tree);
                }
            }
        }
        return Math.max(max, 0.0);
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
            double max_score = Double.NEGATIVE_INFINITY;
            for (int s = i + 1; s <= j; s++) {
                List<PCFGParserTester.BinaryRule> binaryRules = grammar.getBinaryRulesByParent(tag);
                for ( PCFGParserTester.BinaryRule binaryRule : binaryRules) {
                    double ruleScore  = binaryRule.getScore();
                    double leftScore  = unaryPi(sentence, i, s - 1, binaryRule.getLeftChild()) ;
                    double rightScore = unaryPi(sentence, s,     j, binaryRule.getRightChild());
                    double score = ruleScore *leftScore * rightScore;
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
            return Math.max(max_score, 0.0);
        }
    }

    private String cacheKey(int i, int j, String tag) {
        return i + "_" + j + "_" + tag;
    }
}

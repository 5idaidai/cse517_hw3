package edu.berkeley.nlp.assignments;

import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Pair;

import java.util.*;

/**
 * @author Keith Stone
 */
public class CKYParser extends PCFGParserTester.Parser {
    PCFGParserTester.UnaryClosure uc;
    PCFGParserTester.Grammar grammar;
    Set<String>               rootTags;

    public CKYParser(List<Tree<String>> trainTrees,  PCFGParserTester.TreeAnnotations.MarkovContext context) {
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

        for (Tree<String> tree : annotatedTrainTrees) {
            rootTags.add(tree.getLabel());
        }

        lexicon = new PCFGParserTester.Lexicon(annotatedTrainTrees);

        System.out.println("done.");
    }

    public Tree<String> getBestParse(List<String> sentence) {
        TreeCache tc = new TreeCache(sentence, grammar);

        double binary_max = 0.0;
        double unary_max = 0.0;
        String binaryTag = "ROOT";
        String unaryTag  = "ROOT";
        for (String tag : rootTags) {
            if (sentence.size() > 1) {
                double binary_score = binaryPi(sentence, 0, sentence.size() - 1, tag, tc);
                if (binary_score > binary_max) {
                    binary_max = binary_score;
                    binaryTag = tag;
                }
            }

            double unary_score  =  unaryPi(sentence, 0, sentence.size() - 1, tag, tc);
            if (unary_score > unary_max) {
                unary_max = unary_score;
                unaryTag = tag;
            }
        }

        Tree<String> annotatedBestParse;
        if (binary_max > unary_max) {
            annotatedBestParse = tc.buildBinaryTree(sentence, binaryTag);
        } else {
            annotatedBestParse = tc.buildUnaryTree (sentence, unaryTag );
        }

        // Oops case, create the default tree.
        if (Math.max(binary_max, unary_max) == 0.0) {
            annotatedBestParse = default_parse(sentence);
        } else {
            normalize_structure(annotatedBestParse);
        }

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

    private double unaryPi(List<String> sentence, int i, int j, String tag, TreeCache treeCache) {
        // Illegal
        if (i > j) {
            throw new IllegalArgumentException("I is not allowed to be larger than J");
        }

        // Always check the cache first
        CacheStore cache = treeCache.getCache(i,j,tag);
        if (cache.getUnaryMax() >= 0.0) {
            return cache.getUnaryMax();
        }

        List<PCFGParserTester.UnaryRule> closure = uc.getClosedUnaryRulesByParent(tag);
        for (PCFGParserTester.UnaryRule rule : closure) {
            String childTag = rule.getChild();
            double ruleScore =  rule.getScore();
            double expansionScore;
            if (i == j) {
                expansionScore =  lexicon.scoreTagging(sentence.get(i), childTag);
            } else {
                expansionScore = binaryPi(sentence, i, j, childTag, treeCache);
            }
            double score = ruleScore * expansionScore;
            cache.addUnaryRule(score, rule);
        }

        return Math.max(cache.getUnaryMax(), 0.0);
    }

    private double binaryPi(List<String> sentence, int i, int j, String tag, TreeCache treeCache) {
        // Illegal
        if (i > j) {
            throw new IllegalArgumentException("I is not allowed to be larger than J");
        }

        // Always check the cache first
        CacheStore cache = treeCache.getCache(i,j,tag);
        if (cache.getBinaryMax() >= 0.0) {
            return cache.getBinaryMax();
        }
        if (i == j) {
            throw new IllegalArgumentException("Cannot binary split a single node");
        } else {
            for (int s = i + 1; s <= j; s++) {
                List<PCFGParserTester.BinaryRule> binaryRules = grammar.getBinaryRulesByParent(tag);
                for ( PCFGParserTester.BinaryRule binaryRule : binaryRules) {
                    double ruleScore  = binaryRule.getScore();
                    double leftScore  = unaryPi(sentence, i, s - 1, binaryRule.getLeftChild(),  treeCache);
                    double rightScore = unaryPi(sentence, s,     j, binaryRule.getRightChild(), treeCache);
                    double score = ruleScore *leftScore * rightScore;
                    cache.addBinaryRule(score, binaryRule, s);
                }
            }
            return Math.max(cache.getBinaryMax(), 0.0);
        }
    }
}

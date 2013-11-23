package edu.berkeley.nlp.assignments;

import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Pair;

import java.util.*;

/**
 * @author Keith Stone
 */
public class CKYParser extends PCFGParserTester.Parser {
    private class TreeCache {
        private class CacheStore {
            public CacheStore() {
                binaryMax = Double.NEGATIVE_INFINITY;
                unaryMax  = Double.NEGATIVE_INFINITY;
            }

            public double getUnaryMax() {
                return unaryMax;
            }

            public void setUnaryMax(double unaryMax) {
                this.unaryMax = unaryMax;
            }

            public void setBinaryMax(double binaryMax) {
                this.binaryMax = binaryMax;
            }

            public double getBinaryMax() {
                return binaryMax;
            }

            public Tree<String> getUnaryTree() {
                return unaryTree;
            }

            public void setUnaryTree(Tree<String> unaryTree) {
                this.unaryTree = unaryTree;
            }

            public Tree<String> getBinaryTree() {
                return binaryTree;
            }

            public void setBinaryTree(Tree<String> binaryTree) {
                this.binaryTree = binaryTree;
            }

            public void addUnaryTree(PCFGParserTester.UnaryRule rule, double score, Tree<String> subTree) {
                Tree<String> tree = new Tree<String>(rule.getParent(), Collections.singletonList(subTree));
                this.setUnaryMax(score);
                this.setUnaryTree(tree);
            }

            public void addBinaryTree(double score, Tree<String> tree) {
                this.setBinaryMax(score);
                this.setBinaryTree(tree);
            }

            double unaryMax;
            double binaryMax;
            Tree<String> unaryTree;
            Tree<String> binaryTree;
        }
        Map<Integer, Map<Integer, Map<String, CacheStore>>> cache;

        public TreeCache(List<String> sentence, PCFGParserTester.Grammar grammar) {
            int n = sentence.size();
            cache = new HashMap<Integer, Map<Integer, Map<String, CacheStore>>>(n);
            for (int i =0; i < n; i++) {
                cache.put(i, new HashMap<Integer, Map<String, CacheStore>>(n));
                for (int j = i; j < n; j++) {
                    cache.get(i).put(j, new HashMap<String, CacheStore>(grammar.getStates().size()));
                }
            }
        }

        public Tree<String> getBinaryTree(int i, int j, String tag) {
            return getCache(i,j, tag).getBinaryTree();
        }

        public Tree<String> getUnaryTree(int i, int j, String tag) {
            return getCache(i,j, tag).getUnaryTree();
        }

        private CacheStore getCache(int i, int j, String tag) {
            CacheStore cache = this.cache.get(i).get(j).get(tag);
            if (cache == null) {
                cache = new CacheStore();
                this.cache.get(i).get(j).put(tag, cache);
            }
            return cache;
        }
    }

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

        for (Tree<String> tree : trainTrees) {
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
            annotatedBestParse = tc.getBinaryTree(0, sentence.size() - 1, binaryTag);
        } else {
            annotatedBestParse = tc.getUnaryTree( 0, sentence.size() - 1, unaryTag );
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

    private double unaryPi(List<String> sentence, int i, int j, String tag, TreeCache treeCache) {
        // Illegal
        if (i > j) {
            throw new IllegalArgumentException("I is not allowed to be larger than J");
        }
        // Always check the cache first
        TreeCache.CacheStore cache = treeCache.getCache(i,j,tag);
        if (cache.getUnaryMax() >= 0.0) {
            return cache.getUnaryMax();
        }
        if (i == j) {
            // Base case
            List<PCFGParserTester.UnaryRule> closure = uc.getClosedUnaryRulesByParent(tag);
            for (PCFGParserTester.UnaryRule rule : closure) {
                double ruleScore =  rule.getScore();
                double expansionScore = lexicon.scoreTagging(sentence.get(i), rule.getChild());
                double score = ruleScore * expansionScore;
                if (score > cache.getUnaryMax()) {
                    Tree<String> wordTree = new Tree<String>(sentence.get(i));
                    Tree<String> subTree = new Tree<String>(rule.getChild(), Collections.singletonList(wordTree));
                    cache.addUnaryTree(rule, score, subTree);
                }
            }
        } else {
            // Recursion
            List<PCFGParserTester.UnaryRule> closure = uc.getClosedUnaryRulesByParent(tag);
            for (PCFGParserTester.UnaryRule rule : closure) {
                String childTag = rule.getChild();
                double ruleScore      = rule.getScore();
                double expansionScore = binaryPi(sentence, i, j, childTag, treeCache);
                double score = ruleScore * expansionScore;
                if (score > cache.getUnaryMax()) {
                    Tree<String> subTree = treeCache.getBinaryTree(i, j, childTag);
                    cache.addUnaryTree(rule, score, subTree);
                }
            }
        }
        return Math.max(cache.getUnaryMax(), 0.0);
    }

    private double binaryPi(List<String> sentence, int i, int j, String tag, TreeCache treeCache) {
        // Illegal
        if (i > j) {
            throw new IllegalArgumentException("I is not allowed to be larger than J");
        }
        // Always check the cache first
        TreeCache.CacheStore cache = treeCache.getCache(i,j,tag);
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
                    if (score > cache.getBinaryMax()) {
                        Tree<String> leftTree  = treeCache.getUnaryTree(i, s - 1, binaryRule.getLeftChild());
                        Tree<String> rightTree = treeCache.getUnaryTree(s,     j, binaryRule.getRightChild());
                        List<Tree<String>> children = new ArrayList<Tree<String>>();
                        children.add(leftTree);
                        children.add(rightTree);
                        Tree<String> tree = new Tree<String>(binaryRule.getParent(), children);
                        cache.addBinaryTree(score, tree);
                    }
                }
            }
            return Math.max(cache.getBinaryMax(), 0.0);
        }
    }
}

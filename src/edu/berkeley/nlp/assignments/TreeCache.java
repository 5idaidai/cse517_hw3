package edu.berkeley.nlp.assignments;

import edu.berkeley.nlp.ling.Tree;

import java.util.*;

/**
 * @author Keith Stone
 */
public class TreeCache {
    Map<Integer, Map<Integer, Map<String, CacheStore>>> cache;

    public TreeCache(List<String> sentence, PCFGParserTester.Grammar grammar) {
        int n = sentence.size();
        cache = new HashMap<Integer, Map<Integer, Map<String, CacheStore>>>(n);
        for (int i = 0; i < n; i++) {
            cache.put(i, new HashMap<Integer, Map<String, CacheStore>>(n));
            for (int j = i; j < n; j++) {
                cache.get(i).put(j, new HashMap<String, CacheStore>(grammar.getStates().size()));
                //for (String tag : grammar.getStates()) cache.get(i).get(j).put(tag, new CacheStore());
            }
        }
    }

    public Tree<String> buildBinaryTree(List<String> sentence, String rootTag) {
        return buildBinaryTree(0, sentence.size() - 1, rootTag, sentence);
    }

    public Tree<String> buildUnaryTree(List<String> sentence, String rootTag) {
        return buildUnaryTree(0, sentence.size() - 1, rootTag, sentence);
    }

    private Tree<String> buildBinaryTree(int i, int j, String tag, List<String> sentence) {
        CacheStore cs = getCache(i, j, tag);
        if (cs == null) return null;

        PCFGParserTester.BinaryRule rule = cs.getBinaryRule();
        int s = cs.getSplitIndex();
        if (rule == null) return null;

        Tree<String> leftTree  = buildUnaryTree(i, s - 1, rule.getLeftChild(), sentence);
        Tree<String> rightTree = buildUnaryTree(s, j, rule.getRightChild(), sentence);
        List<Tree<String>> children = new ArrayList<Tree<String>>();
        children.add(leftTree);
        children.add(rightTree);
        Tree<String> tree = new Tree<String>(rule.getParent(), children);
        return tree;
    }

    private Tree<String> buildUnaryTree(int i, int j, String tag, List<String> sentence) {
        CacheStore cs = getCache(i, j, tag);
        if (cs == null) return null;

        PCFGParserTester.UnaryRule rule = cs.getUnaryRule();
        if (rule == null) return null;

        if (i == j) {
            Tree<String> word = new Tree<String>(sentence.get(i));
            Tree<String> pos  = new Tree<String>(rule.getChild(),  Collections.singletonList(word));
            Tree<String> tree = new Tree<String>(rule.getParent(), Collections.singletonList(pos));
            return tree;
        } else {
            Tree<String> child = buildBinaryTree(i, j, rule.getChild(), sentence);
            Tree<String> tree = new Tree<String>(rule.getParent(), Collections.singletonList(child));
            return tree;
        }
    }

    public CacheStore getCache(int i, int j, String tag) {
        CacheStore cache = this.cache.get(i).get(j).get(tag);
        if (cache == null) {
            cache = new CacheStore();
            this.cache.get(i).get(j).put(tag, cache);
        }
        return cache;
    }
}

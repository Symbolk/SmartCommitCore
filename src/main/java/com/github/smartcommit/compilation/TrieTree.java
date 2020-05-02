package com.github.smartcommit.compilation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

public class TrieTree {

  private TrieNode root; // 根节点

  public TrieTree() {
    this.root = new TrieNode();
  }

  private class TrieNode { // 节点类
    private int num; // 通过的字符串数（包含在此结束的字符串）
    private int count; // 刚好在这里结束的单词数
    private Map<Character, TrieNode> son; // 记录子节点

    TrieNode() {
      num = 1;
      count = 0;
      son = new TreeMap<>(); // TreeMap用于排序
    }
  }

  public void add(String word) { // 在字典树中插入一个字符串
    if (StringUtils.isBlank(word)) {
      return;
    }
    TrieNode node = root;
    char[] letters = word.toCharArray();
    for (char c : letters) {
      if (node.son.containsKey(c)) {
        node.son.get(c).num++;
      } else {
        node.son.put(c, new TrieNode());
      }
      node = node.son.get(c);
    }
    node.count++;
  }

  public int countWord(String word) { // 计算字符串出现的次数
    return count(word, false);
  }

  public int countPrefix(String prefix) { // 计算前缀出现的次数
    return count(prefix, true);
  }

  public boolean contain(String word) { // 是否含有字符串
    return count(word, false) > 0;
  }

  public int count(String word, boolean isPrefix) { // 计算字符串/前缀出现的次数
    if (StringUtils.isBlank(word)) return 0;
    TrieNode node = root;
    char[] letters = word.toCharArray();
    for (char c : letters) {
      if (node.son.containsKey(c)) node = node.son.get(c);
      else return 0;
    }
    return isPrefix ? node.num : node.count;
  }

  public Map<String, Integer> getSortedWordsAndCounts() { // 获取排序号的字符串和其出现次数
    Map<String, Integer> map = new TreeMap<>();
    getSortedWordsAndCounts(root, map, StringUtils.EMPTY);
    return map;
  }

  private void getSortedWordsAndCounts(TrieNode node, Map<String, Integer> map, String pre) {
    for (Map.Entry<Character, TrieNode> e : node.son.entrySet()) {
      String prefix = pre + e.getKey();
      if (e.getValue().count > 0) {
        map.put(prefix, e.getValue().count);
      }
      getSortedWordsAndCounts(e.getValue(), map, prefix);
    }
  }

  public Collection<String> getSortedWords() { // 获取排好序的字符串
    Collection<String> list = new ArrayList<>();
    getSortedWords(root, list, StringUtils.EMPTY);
    return list;
  }

  private void getSortedWords(TrieNode node, Collection<String> list, String pre) {
    for (Map.Entry<Character, TrieNode> e : node.son.entrySet()) {
      String prefix = pre + e.getKey();
      if (e.getValue().count > 0) {
        list.add(prefix);
      }
      getSortedWords(e.getValue(), list, prefix);
    }
  }

  public String getMaxCommonPrefix() { // 获取最大公共前缀
    TrieNode node = root;
    String maxPrefix = StringUtils.EMPTY;
    while (node.son.size() == 1 && node.count == 0) {
      for (Map.Entry<Character, TrieNode> e : node.son.entrySet()) {
        node = e.getValue();
        maxPrefix += e.getKey();
      }
    }
    return maxPrefix;
  }

  public static void main(String[] args) { // 测试
    TrieTree trie = new TrieTree();
    //        trie.add("he");
    trie.add("hf");
    trie.add("hfz");
    trie.add("hfz");
    trie.add("hfz");
    trie.add("hfzy");
    //        trie.add("hg");
    //        trie.add("eh");
    //        trie.add("eh");
    //        trie.add("ek");

    System.out.println(trie.countWord("hfz"));
    System.out.println(trie.countPrefix("hfz"));
    System.out.println(trie.contain("eh"));
    System.out.println(trie.getSortedWords());
    System.out.println(trie.getSortedWordsAndCounts());
    System.out.println(trie.getMaxCommonPrefix());
  }
}

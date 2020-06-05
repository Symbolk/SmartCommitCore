package com.github.smartcommit;

import com.github.smartcommit.evaluation.Evaluation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDistance {
//  @BeforeAll
//  public static void setUpBeforeAll() {
//    PropertyConfigurator.configure("log4j.properties");
//  }

  @Test
  public void testHierarchyDis() {
    Map<String, Integer> hier1 = new HashMap<>();
    hier1.put("hunk", 5);
    hier1.put("member", 6);
    hier1.put("class", 7);
    hier1.put("package", 8);
    Map<String, Integer> hier2 = new HashMap<>();
    hier2.put("hunk", 4);
    hier2.put("member", 6);
    hier2.put("class", 9);
    hier2.put("package", 10);
    assertThat(compareHierarchy(hier1, hier2)).isEqualTo(1);
  }

  private int compareHierarchy(Map<String, Integer> hier1, Map<String, Integer> hier2) {
    if (hier1.isEmpty() || hier2.isEmpty()) {
      return -1;
    }
    int res = 4;
    for (Map.Entry<String, Integer> entry : hier1.entrySet()) {
      if (hier2.containsKey(entry.getKey())) {
        if (hier2.get(entry.getKey()).equals(entry.getValue())) {
          int t = -1;
          switch (entry.getKey()) {
            case "hunk":
              t = 0;
              break;
            case "member":
              t = 1;
              break;
            case "class":
              t = 2;
              break;
            case "package":
              t = 3;
              break;
          }
          res = Math.min(res, t);
        }
      }
    }
    return res;
  }

  @Test
  public void testEditDistance() {
    List<Integer> list1 = new ArrayList<>();
    list1.add(0);
    list1.add(1);
    list1.add(2);
    list1.add(3);
    List<Integer> list2 = new ArrayList<>();
    list2.add(1);
    list2.add(3);
    list2.add(0);
    list2.add(2);
    assertThat(Evaluation.editDistance(list1, list2)).isEqualTo(2);
  }
}

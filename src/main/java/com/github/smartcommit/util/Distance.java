package com.github.smartcommit.util;

public class Distance {
  public static void main(String[] args) {
    String s1 = "13|45";
    String s3 = "134|5";
    String s2 = "135|4";
    System.out.println(DLDistance(s1, s2));
    System.out.println(getSimilarity(s1, s2));
    System.out.println(LDistance("first second third", "second"));
  }

  private static int DLDistance(String s1, String s2) {
    int m = (s1 == null) ? 0 : s1.length();
    int n = (s2 == null) ? 0 : s2.length();
    if (m == 0) {
      return n;
    }
    if (n == 0) {
      return m;
    }
    int[] p = new int[n + 1];
    int[] p1 = new int[n + 1];
    int[] t = new int[n + 1];
    for (int i = 0; i < p.length; i++) {
      p[i] = i;
    }
    int d = 0;
    int cost = 0;
    char s1_c, s2_c;
    for (int i = 0; i < m; i++) {
      t[0] = i + 1;
      s1_c = s1.charAt(i);
      for (int j = 1; j < p.length; j++) {
        s2_c = s2.charAt(j - 1);
        cost = (s1_c == s2_c) ? 0 : 1;
        d = Math.min(Math.min(t[j - 1], p[j]) + 1, p[j - 1] + cost);
        if (i > 0 && j > 1 && s1_c == s2.charAt(j - 2) && s1.charAt(i - 1) == s2_c) {
          d = Math.min(d, p1[j - 2] + cost);
        }
        t[j] = d;
      }
      p1 = p;
      p = t;
      t = new int[n + 1];
    }
    return d;
  }

  public static float getSimilarity(String s1, String s2) {
    if (s1 == null || s2 == null) {
      if (s1 == s2) {
        return 1.0f;
      }
      return 0.0f;
    }
    float d = DLDistance(s1, s2);
    return 1 - (d / Math.max(s1.length(), s2.length()));
  }

  public static int LDistance(String sentence1, String sentence2) {
    String[] s1 = sentence1.split(" ");
    String[] s2 = sentence2.split(" ");
    int[][] solution = new int[s1.length + 1][s2.length + 1];

    for (int i = 0; i <= s2.length; i++) {
      solution[0][i] = i;
    }

    for (int i = 0; i <= s1.length; i++) {
      solution[i][0] = i;
    }

    int m = s1.length;
    int n = s2.length;
    for (int i = 1; i <= m; i++) {
      for (int j = 1; j <= n; j++) {
        if (s1[i - 1].equals(s2[j - 1])) solution[i][j] = solution[i - 1][j - 1];
        else
          solution[i][j] =
              1
                  + Math.min(
                      solution[i][j - 1], Math.min(solution[i - 1][j], solution[i - 1][j - 1]));
      }
    }
    return solution[s1.length][s2.length];
  }
}

package com.github.smartcommit;

import org.junit.jupiter.api.Test;
import smile.validation.metric.AdjustedRandIndex;

import static org.assertj.core.api.Assertions.*;

public class MetricsTest {
  @Test
  public void testAdjustedRandIndex() {
    int[] a = new int[] {0, 0, 1, 1};
    int[] b = {0, 0, 1, 2};
    double ari = AdjustedRandIndex.of(a, b);
    assertThat(ari).isCloseTo(0.57, withinPercentage(10));
    assertThat(ari).isCloseTo(0.57, within(0.01));
    assertThat(ari).isCloseTo(0.57, offset(0.01));
    assertThat((float) ari).isCloseTo(0.57f, within(0.01f));
  }
}

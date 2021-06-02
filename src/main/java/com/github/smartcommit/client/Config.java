package com.github.smartcommit.client;

import java.io.File;

/** Store the constants as the config */
public final class Config {
  // at commit
  public static final String REPO_NAME = "nomulus";
  public static final String REPO_PATH = "~/coding/dev" + File.separator + REPO_NAME;

  // in working tree
//  public static final String REPO_NAME = "SmartCommitCore 2";
//  public static final String REPO_PATH = "~/coding/dev" + File.separator + REPO_NAME;

  public static final String COMMIT_ID = "906b054f4b7a2e38681fd03282996955406afd65";

  // arguments
  public static final Double WEIGHT_THRESHOLD = 1.0D;
  public static final Double MIN_SIMILARITY = 0.8D;
  // {hunk: 0 (default), member: 1, class: 2, package: 3}
  public static final Integer MAX_DISTANCE = 2;
  public static final String REPO_ID = String.valueOf(REPO_NAME.hashCode());
  public static final String TEMP_DIR =
      System.getProperty("user.home")
          + File.separator
          + ".mergebot"
          + File.separator
          + "repos"
          + File.separator
          + REPO_NAME
          + "_mergebot"
          + File.separator
          + "smart_commit";
  public static final String JRE_PATH =
      System.getProperty("java.home") + File.separator + "lib/rt.jar";
}

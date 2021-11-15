package com.github.smartcommit.client;

import java.io.File;

/** Store the constants as the config */
public final class Config {
  // at commit
  public static final String REPO_NAME = "SmartCommitCore";
  public static final String REPO_PATH = System.getProperty("user.dir");

  // in working tree
  //  public static final String REPO_NAME = "SmartCommitCore";
  //  public static final String REPO_PATH = "~/coding/dev" + File.separator + REPO_NAME;

  // arguments
  public static final Double WEIGHT_THRESHOLD = 0D;
  public static final Double MIN_SIMILARITY = 0.8D;
  // {hunk: 0 (default), member: 1, class: 2, package: 3}
  public static final Integer MAX_DISTANCE = 2;
  public static final String REPO_ID = String.valueOf(REPO_NAME.hashCode());
  public static final String TEMP_DIR =
      System.getProperty("user.home")
          + File.separator
          + "smartcommit"
          + File.separator
          + "temp"
          + File.separator
          + REPO_NAME;
  public static final String JRE_PATH =
      System.getProperty("java.home") + File.separator + "lib/rt.jar";
}

package com.github.smartcommit.client;

import java.io.File;

/** Store the constants as the config */
public final class Config {
  // at commit
//  public static final String REPO_NAME = "nomulus";
  public static final String COMMIT_ID = "906b054f4b7a2e38681fd03282996955406afd65";
//  public static final String REPO_PATH = "/Users/symbolk/coding/data" + File.separator + REPO_NAME;

  // in working tree
  public static final String REPO_NAME = "SmartCommitCore";
  public static final String REPO_PATH = System.getProperty("user.dir");

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

package com.github.smartcommit.client;

import java.io.File;

/** Store the constants as the config */
public final class Config {
  public static final String REPO_NAME = "nomulus";
  public static final String COMMIT_ID = "906b054f4b7a2e38681fd03282996955406afd65";
  public static final String DATA_DIR = "/Users/symbolk/coding/data";

  public static final String REPO_PATH = DATA_DIR + File.separator + REPO_NAME;
  public static final String TEMP_DIR = DATA_DIR + File.separator + "temp";
  public static final String JRE_PATH =
      System.getProperty("java.home") + File.separator + "lib/rt.jar";
}

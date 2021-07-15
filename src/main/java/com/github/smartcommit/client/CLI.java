package com.github.smartcommit.client;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.github.smartcommit.model.Group;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class CLI {
  // command line options
  @Parameter(
      names = {"-r", "--repo"},
      arity = 1,
      order = 0,
      description = "Absolute root path of the target Git repository.")
  String repoPath = "";

  @Parameter(
      names = {"-w", "--working"},
      arity = 0,
      order = 1,
      description = "Analyze the current working tree (default).")
  Boolean analyzeWorkingTree = true;

  @Parameter(
      names = {"-c", "--commit"},
      arity = 1,
      order = 2,
      description = "Analyze a specific commit by providing its ID.")
  String commitID = "";

  // output path
  @Parameter(
      names = {"-o", "-output"},
      arity = 1,
      order = 3,
      description = "Specify the path to output the result.")
  String outputPath =
      System.getProperty("user.home") + File.separator + ".smartcommit" + File.separator + "repos";

  // options&args
  @Parameter(
      names = {"-ref", "--detect-refactoring"},
      arity = 1,
      description = "Whether to enable refactoring detection.")
  Boolean detectRef = true;

  @Parameter(
      names = {"-nj", "--process-non-java"},
      arity = 1,
      description = "Whether to further process non-java changes.")
  Boolean processNonJava = true;

  @Parameter(
      names = {"-wt", "--weight-threshold"},
      arity = 1,
      description = "Set the threshold for weight filtering (default: 0.6).")
  Double weightThreshold = 0.6D;

  @Parameter(
      names = {"-ms", "--min-similarity"},
      arity = 1,
      description = "Set the minimum similarity (default: 0.8).")
  Double minSimilarity = 0.8D;

  @Parameter(
      names = {"-md", "--max-distance"},
      arity = 1,
      description = "Set the maximum distance (default: 0).")
  Integer maxDistance = 0;

  public static void main(String[] args) {
    // config the logger
    //    PropertyConfigurator.configure("log4j.properties");
    // use basic configuration when packaging
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.ERROR);

    try {
      CLI cli = new CLI();
      cli.run(args);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Run merging according to given options
   *
   */
  private void run(String[] args) throws Exception {
    JCommander commandLineOptions = new JCommander(this);
    try {
      commandLineOptions.parse(args);
      checkArguments(this);

      String repoName = getRepoName(repoPath);
      outputPath = outputPath + File.separator + repoName;

      SmartCommit smartCommit =
          new SmartCommit(generateRepoID(repoName), repoName, repoPath, outputPath);

      // apply options
      smartCommit.setDetectRefactorings(detectRef);
      smartCommit.setProcessNonJavaChanges(processNonJava);
      smartCommit.setWeightThreshold(weightThreshold);
      smartCommit.setMinSimilarity(minSimilarity);
      smartCommit.setMaxDistance(maxDistance);

      Map<String, Group> groups;
      if (analyzeWorkingTree) {
        groups = smartCommit.analyzeWorkingTree();
      } else {
        groups = smartCommit.analyzeCommit(commitID);
      }
      if (groups != null && !groups.isEmpty()) {
        System.out.println("End analysis, results saved under: " + outputPath);
      } else {
        System.out.println("End analysis, but found no Changes.");
      }
    } catch (ParameterException pe) {
      System.err.println(pe.getMessage());
      commandLineOptions.setProgramName("SmartCommit");
      commandLineOptions.usage();
    }
  }

  private String generateRepoID(String repoName) {
    return String.valueOf(repoName.hashCode());
  }

  private String getRepoName(String repoPath) {
    String repoName = "";
    Path path = Paths.get(repoPath);
    try (Git git = Git.open(path.toFile())) {
      Repository repository = git.getRepository();
      // getDirectory() returns the .git file
      repoName = repository.getDirectory().getParentFile().getName();
      String branch = repository.getBranch();
      System.out.println("Begin analysis, [repo] " + repoName + " [branch] " + branch);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return repoName;
  }

  /**
   * Check if args are valid
   *
   */
  private void checkArguments(CLI cli) {
    if (cli.repoPath.isEmpty()) {
      throw new ParameterException(
          "Please at least specify the Git repository to analyze with -r.");
    } else {
      File d = new File(cli.repoPath);
      if (!d.exists()) {
        throw new ParameterException(cli.repoPath + " does not exist!");
      }
      if (!d.isDirectory()) {
        throw new ParameterException(cli.repoPath + " is not a directory!");
      }
      if (!checkRepoValid(cli.repoPath)) {
        throw new ParameterException(cli.repoPath + " is not a valid Git repository!");
      }
      if (!cli.commitID.isEmpty()) {
        cli.analyzeWorkingTree = false;
      }
    }
  }

  private boolean checkRepoValid(String repoPath) {
    return RepositoryCache.FileKey.isGitRepository(new File(repoPath, ".git"), FS.DETECTED);
  }
}

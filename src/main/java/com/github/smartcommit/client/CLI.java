package com.github.smartcommit.client;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.github.smartcommit.model.Group;
import org.apache.log4j.BasicConfigurator;
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
      description = "Absolute root path of the target Git repository.")
  String repoPath = "";

  @Parameter(
      names = {"-c", "--commit"},
      arity = 1,
      description = "Commit ID to be analyzed.")
  String commitID = "";
  // output path
  @Parameter(
      names = {"-o", "-output"},
      arity = 1,
      description = "Specify the path to output the result.")
  String outputPath =
      System.getProperty("user.home") + File.separator + ".smartcommit" + File.separator + "repos";
  // configs

  public static void main(String[] args) {
    // config the logger
    //    PropertyConfigurator.configure("log4j.properties");
    // use basic configuration when packaging
    BasicConfigurator.configure();

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
   * @param args
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
      smartCommit.setDetectRefactorings(true);
      smartCommit.setProcessNonJavaChanges(false);
      smartCommit.setSimilarityThreshold(Config.SIMI_THRESHOLD);
      smartCommit.setDistanceThreshold(Config.DIS_THRESHOLD);

      if (commitID.isEmpty()) {
        Map<String, Group> groups = smartCommit.analyzeWorkingTree();
      } else {
        Map<String, Group> groups = smartCommit.analyzeCommit(commitID);
      }
      System.out.println("End analysis, results saved under: " + outputPath);
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
   * @param cli
   */
  private void checkArguments(CLI cli) {
    if (cli.repoPath.isEmpty()) {
      throw new ParameterException("Please specify a Git repository root path to analyze.");
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
    }
  }

  private boolean checkRepoValid(String repoPath) {
    if (RepositoryCache.FileKey.isGitRepository(new File(repoPath, ".git"), FS.DETECTED)) {
      return true;
    } else {
      return false;
    }
  }
}

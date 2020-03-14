package com.github.smartcommit.evaluation;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.refactoringminer.api.GitService;
import org.refactoringminer.util.GitServiceImpl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mine atomic commits and also composite commits from specified repos */
public class DataMiner {
  private static final Logger logger = Logger.getLogger(DataMiner.class);

  public static void main(String[] args) {
    BasicConfigurator.configure();

    String repoDir = "/Users/symbolk/coding/data/repos/";
    String resultsDir = "/Users/symbolk/coding/data/results/";
    String tempDir = "/Users/symbolk/coding/data/temp/";

    String repoName = "jruby";
    String repoPath = repoDir + repoName;

    // !merge && fix/close/resolve/issue && #issueid/number
    // atomic: # == 1
    // composite: & > 1 || (# == 1 && and/also/plus/too/other)

    GitService gitService = new GitServiceImpl();
    List<RevCommit> atomicCommits = new ArrayList<>();
    List<RevCommit> compositeCommits = new ArrayList<>();
    String regex = "#[0-9]+?\\s+";
    Pattern pattern = Pattern.compile(regex);
    try (Repository repository = gitService.openRepository(repoPath)) {
      // iterate commits from master:HEAD
      try (RevWalk walk = gitService.createAllRevsWalk(repository, repository.getBranch())) {
        for (RevCommit commit : walk) {
          if (commit.getParentCount() == 1) {
            // for each:
            // get commit msg
            // check if linked with issue
            // check if atomic or composite
            // add commit id and msg to results
            // stop if collected 100 composite commits and 100 atomic commits
            String msg = commit.getFullMessage().toLowerCase();
            if (msg.contains("merge ")) {
              continue;
            }
            if (msg.contains("fix ")
                || msg.contains("close ")
                || msg.contains("resolve ")
                || msg.contains("issue ")
                || msg.contains("#")) {
              Matcher matcher = pattern.matcher(msg);
              Set<String> numbers = new HashSet<>();
              while (matcher.find()) {
                numbers.add(matcher.group());
              }
              if (numbers.size() == 1) {
                atomicCommits.add(commit);
              } else if (numbers.size() > 1
                  && (msg.contains("and")
                      || msg.contains("also ")
                      || msg.contains("plus ")
                      || msg.contains("other ")
                      || msg.contains("too "))) {
                compositeCommits.add(commit);
              }
            }
          }
        }
      }
      System.out.println(atomicCommits.get(6).getFullMessage());
      System.out.println(compositeCommits.get(1).getFullMessage());
      // write results into csv file

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Yet another way to list all commits
   *
   * @param repoPath
   */
  private void listCommits(String repoPath) {
    Path path = Paths.get(repoPath);
    try (Git git = Git.open(path.toFile())) {
      Iterable<RevCommit> commits = git.log().all().call();
      Repository repository = git.getRepository();
      String branch = repository.getBranch();
      System.out.println(branch);
      for (Iterator<RevCommit> iter = commits.iterator(); iter.hasNext(); ) {
        RevCommit commit = iter.next();
        System.out.println(commit.getAuthorIdent());
        System.out.println(commit.getFullMessage());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

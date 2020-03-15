package com.github.smartcommit.evaluation;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.refactoringminer.api.GitService;
import org.refactoringminer.util.GitServiceImpl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.WARN);

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
                  && (msg.contains("and ")
                      || msg.contains("also ")
                      || msg.contains("&")
                      || msg.contains("plus ")
                      || msg.contains("other ")
                      || msg.contains("too "))) {
                compositeCommits.add(commit);
              }
            }
          }
        }
      }

      saveSamplesInDB(repoName, "atomic", atomicCommits);
      saveSamplesInDB(repoName, "composite", compositeCommits);
      // write results into csv file
      //      saveSamplesInCSV(atomicCommits, resultsDir + repoName + "_atomic.csv");
      //      saveSamplesInCSV(compositeCommits, resultsDir + repoName + "_composite.csv");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Save samples in mongodb
   *
   * @param repoName
   * @param dbName
   * @param commits
   */
  private static void saveSamplesInDB(String repoName, String dbName, List<RevCommit> commits) {
    MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
    MongoClient mongoClient = new MongoClient(connectionString);
    MongoDatabase db = mongoClient.getDatabase(dbName);
    MongoCollection<Document> col = db.getCollection(repoName);
    // !!! drop the last testing results
//    col.drop();

    for (RevCommit commit : commits) {
      Document commitDoc = new Document("repo_name", repoName);
      commitDoc.append("commit_id", commit.getName()).append("commit_msg", commit.getFullMessage());

      col.insertOne(commitDoc);
    }
    mongoClient.close();
  }

  /**
   * Save samples in csv files
   *
   * @param commits
   * @param resultFilePath
   * @throws IOException
   */
  private static void saveSamplesInCSV(List<RevCommit> commits, String resultFilePath)
      throws IOException {
    File file = new File(resultFilePath);

    if (!file.exists() && !file.isDirectory()) {
      //      file.mkdirs();
      file.createNewFile();
    }

    try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
      for (RevCommit commit : commits) {
        bw.write(commit.getName() + "~~" + commit.getFullMessage().trim().replaceAll("\n", ""));
        bw.newLine();
        bw.flush();
      }
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

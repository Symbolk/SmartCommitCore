package com.github.smartcommit.evaluation;

import com.github.smartcommit.util.Utils;
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
import java.text.BreakIterator;
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

    String repoName = "gradle";
    String repoPath = repoDir + repoName;
    // number of examined commits
    int numCommits = 0;
    System.out.println("Mining " + repoName);
    // !merge && fix/close/resolve/issue && #issueid/number
    // atomic: # == 1 && !bullet list <= 1
    // composite: (# > 1 || and/also/plus/too/other || bullet list))

    GitService gitService = new GitServiceImpl();
    List<RevCommit> atomicCommits = new ArrayList<>();
    List<RevCommit> compositeCommits = new ArrayList<>();
    Pattern issuePattern = Pattern.compile("#[0-9]+?\\s+");
    Pattern bulletPattern = Pattern.compile("\\*|-\\s+");
    try (Repository repository = gitService.openRepository(repoPath)) {
      // iterate commits from master:HEAD
      try (RevWalk walk = gitService.createAllRevsWalk(repository, repository.getBranch())) {
        for (RevCommit commit : walk) {
          numCommits+=1;
          // no merge commits
          if (commit.getParentCount() == 1) {
            String msg = commit.getFullMessage().toLowerCase();
            if (msg.contains("merge") || msg.contains("merging")) {
              continue;
            }
            if (anyMatch(
                msg,
                new String[] {
                  "issue", "#", "fix", "close", "resolve", "solve"
                })) { // Other format: JRUBY-XXX XSTR-XXX

              // extract issue ids
              Set<String> issueIDs = new HashSet<>();
              Matcher issueMatcher = issuePattern.matcher(msg);
              while (issueMatcher.find()) {
                issueIDs.add(issueMatcher.group());
              }

              Matcher bulletMatcher = bulletPattern.matcher(msg);
              int bulletNum = 0;
              while (bulletMatcher.find()) {
                bulletNum++;
              }

              if (bulletNum > 1
                  || containsMultipleVerbs(commit.getFullMessage())
                  || issueIDs.size() > 1) {
                compositeCommits.add(commit);
                System.out.println("[C]" + commit.getName());
              } else if (issueIDs.size() == 1) {
                atomicCommits.add(commit);
                System.out.println("[A]" + commit.getName());
              }
            }
          }
        }
      }

      System.out.println("[Total]: " + numCommits);
      System.out.println("[Composite]: " + compositeCommits.size() +
              "("+ Utils.formatDouble((double)compositeCommits.size()*100/numCommits)+"%)");
      System.out.println("[Atomic]: " + atomicCommits.size());
      // save results into mongodb
//      saveSamplesInDB(repoName, "atomic", atomicCommits);
//      saveSamplesInDB(repoName, "composite", compositeCommits);
      // write results into csv file
      //      saveSamplesInCSV(atomicCommits, resultsDir + repoName + "_atomic.csv");
      //      saveSamplesInCSV(compositeCommits, resultsDir + repoName + "_composite.csv");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static boolean containsMultipleVerbs1(String msg) {
    String[] verbs =
        new String[] {
          "add",
          "fix",
          "change",
          "modif",
          "remove",
          "delete",
          "refactor",
          "format",
          "rename",
          "reformat",
          "patch"
        };
    //    String[] words = msg.split("\\s+");
    int num = 0;
    for (String v : verbs) {
      if (msg.contains(v)) {
        num += 1;
        if (num > 1) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean containsMultipleVerbs(String msg) {
    List<String> sentences = new ArrayList<>();
    BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
    iterator.setText(msg);
    int start = iterator.first();
    for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
      sentences.add(msg.substring(start, end).toLowerCase());
    }
    String[] verbs =
        new String[] {
          "add",
          "fix",
          "change",
          "modif",
          "remove",
          "delete",
          "refactor",
          "format",
          "rename",
          "reformat",
          "patch"
        };
    //    String[] words = msg.split("\\s+");
    int num = 0;
    for (String sentence : sentences) {
      if (anyMatch(sentence, verbs)) {
        num += 1;
        if (num > 1) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean anyMatch(String str, String[] keywords) {
    return Arrays.stream(keywords).parallel().anyMatch(str::contains);
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
    col.drop();

    for (RevCommit commit : commits) {
      Document commitDoc = new Document("repo_name", repoName);
      commitDoc
          .append("commit_id", commit.getName())
          .append("commit_time", commit.getAuthorIdent().getWhen())
          .append("committer_name", commit.getAuthorIdent().getName())
          .append("committer_email", commit.getAuthorIdent().getEmailAddress())
          .append("commit_msg", commit.getFullMessage());

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

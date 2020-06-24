package com.github.smartcommit.commitmsg;

import com.github.smartcommit.client.Config;
import com.github.smartcommit.client.SmartCommit;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommitMsgGeneratorTest {

  private static String repoName = "nomulus";
  private static String repoPath =
          System.getProperty("user.dir") + File.separator + ".." + File.separator + repoName;
  private static String cloneUrl = "https://github.com/google/nomulus";
  public static String tempDir =
          System.getProperty("user.home")
                  + File.separator
                  + ".mergebot"
                  + File.separator
                  + "repos"
                  + File.separator
                  + repoName
                  + "_mergebot"
                  + File.separator
                  + "smart_commit";

  @BeforeAll
  public static void setUpBeforeAll() {
    //    extractDiffHunkIDs(tempDir);
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
    // clone test repo if not exist
    try {
      cloneIfNotExists(repoPath, cloneUrl);
    } catch (Exception e) {
      e.printStackTrace();
    }

    // prepare workspace
    try (Git git = Git.open(Paths.get(repoPath).toFile())) {
      git.reset()
              .setMode(ResetCommand.ResetType.HARD)
              .setRef("bc7f3546c73631ff241dd4406b2317d1cc1b7a58")
              .call();
      git.reset().setMode(ResetCommand.ResetType.MIXED).setRef("HEAD~").call();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static Repository cloneIfNotExists(String repoPath, String cloneUrl) throws Exception {
    File folder = new File(repoPath);
    Repository repository;
    if (folder.exists()) {
      RepositoryBuilder builder = new RepositoryBuilder();
      repository =
              builder.setGitDir(new File(folder, ".git")).readEnvironment().findGitDir().build();

      System.out.println(
              "Repo " + cloneUrl + " has been cloned, current branch is " + repository.getBranch());

    } else {
      System.out.println("Cloning " + cloneUrl + " ...");
      Git git =
              Git.cloneRepository()
                      .setDirectory(folder)
                      .setURI(cloneUrl)
                      .setCloneAllBranches(false)
                      .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                      .call();
      repository = git.getRepository();
      System.out.println(
              "Done cloning " + cloneUrl + ", current branch is " + repository.getBranch());
    }
    return repository;
  }

  @Test
  void testCommitMsgGenerator() {
    SmartCommit smartCommit =
            new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);
    smartCommit.setDetectRefactorings(true);
    smartCommit.setProcessNonJavaChanges(false);
    smartCommit.setSimilarityThreshold(Config.SIMI_THRESHOLD);
    smartCommit.setDistanceThreshold(Config.DIS_THRESHOLD);

    // expected commitMsg
    List<String> expected = new ArrayList<>();
    expected.add("Feature - Add ImportStatement and Remove Comment");
    expected.add("Feature - Add MethodDeclaration saveNew");
    expected.add("Docs - Other file change");
    expected.add("Style - Code reformat");
    expected.add("Refactor - Rename Method savePremiumList");

    try {
      Map<String, Group> groups =
              smartCommit.analyzeCommit("bc7f3546c73631ff241dd4406b2317d1cc1b7a58");
      Map<String, DiffHunk> id2DiffHunkMap = smartCommit.getId2DiffHunkMap();

      List<String> commitMsgs = new ArrayList<>();
      for (Map.Entry entry : groups.entrySet()) {
        Group group = groups.get(entry.getKey());
        List<String> msgs = smartCommit.generateCommitMsg(group);
        commitMsgs.add(msgs.get(0));
      }
      assertEquals(expected, commitMsgs);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
  public static void main(String[] args) {
    SmartCommit smartCommit =
            new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);
    smartCommit.setDetectRefactorings(true);
    smartCommit.setProcessNonJavaChanges(false);
    smartCommit.setSimilarityThreshold(Config.SIMI_THRESHOLD);
    smartCommit.setDistanceThreshold(Config.DIS_THRESHOLD);

    // expected commitMsg
    List<String> expected = new ArrayList<>();
    expected.add("Feature - Add ImportStatement and Remove Comment");
    expected.add("Feature - Add MethodDeclaration saveNew");
    expected.add("Docs - Other file change");
    expected.add("Style - Code reformat");
    expected.add("Refactor - Rename Method savePremiumList");

    try {
      String commitID = null;
      commitID = "1911c116";
      commitID = "6990d605"; // RM error
      commitID = "b8df0bac";
      commitID = "7880aab3";
      commitID = "d09fc7ee";
      commitID = "3098048f";
      commitID = "e8ff4081";
      commitID = "02e71062";
      commitID = "3a9e5d39";
      commitID = "bc7f3546c73631ff241dd4406b2317d1cc1b7a58";

      Map<String, Group> groups = smartCommit.analyzeCommit(commitID);

      List<String> commitMsgs = new ArrayList<>();
      for (Map.Entry entry : groups.entrySet()) {
        Group group = groups.get(entry.getKey());
        List<String> msgs = smartCommit.generateCommitMsg(group);
        commitMsgs.add(msgs.get(0));
      }
      assertEquals(expected, commitMsgs);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
   */
}

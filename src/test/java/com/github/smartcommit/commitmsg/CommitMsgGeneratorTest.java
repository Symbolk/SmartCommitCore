package com.github.smartcommit.commitmsg;

import com.github.smartcommit.intent.model.MsgClass;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Hunk;
import com.github.smartcommit.model.constant.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

  public static void main(String[] args) {
    args =
            new String[]{
                    "/Users/Chuncen/Desktop/Repos/nomulus", "nomulus"
            };
    String repoPath = args[0];
    String collectionName = args[1];

    testgenerator();

  }

  // TODO
  /*
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
    String expected = "Add importStatement";
    // add importStatement and importStatement

    try {
      Map<String, Group> groups = smartCommit.analyzeWorkingTree();
      smartCommit.exportGroupDetails(groups);

      for (Map.Entry entry : groups.entrySet()) {
        Group group = groups.get(entry.getKey());
        String received =  smartCommit.generateCommitMsg(group).get(0);

        assertEquals(expected, received);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }


  }


  @Test
  void generateDetailedMsgs() {
    SmartCommit smartCommit =
            new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);
    smartCommit.setDetectRefactorings(true);
    smartCommit.setProcessNonJavaChanges(false);
    smartCommit.setSimilarityThreshold(Config.SIMI_THRESHOLD);
    smartCommit.setDistanceThreshold(Config.DIS_THRESHOLD);

    try {
      Map<String, Group> groups = smartCommit.analyzeWorkingTree();
      Map<String, DiffHunk> id2DiffHunkMap = smartCommit.getId2DiffHunkMap();


      List<DiffHunk> diffHunks = new ArrayList<>();
      for (Map.Entry entry : id2DiffHunkMap.entrySet()) {
        DiffHunk diffHunk = id2DiffHunkMap.get(entry.getKey());
        diffHunks.add(diffHunk);
      }

      CommitMsgGenerator commitMsgGenerator = new CommitMsgGenerator(diffHunks);

      // expected commitMsg
      String expected = "Add importStatement";
      // add importStatement and importStatement
      String received = commitMsgGenerator.generateDetailedMsgs(MsgClass.FEAT, GroupLabel.FEATURE).get(0);

      assertEquals(expected, received);

    } catch (Exception e) {
      e.printStackTrace();
    }


  }

   */

  public static void testgenerator() {
    String path = null;
    Hunk bHunk = new Hunk(Version.BASE, path, 0, 1, ContentType.CODE, null);
    Hunk cHunk = new Hunk(Version.CURRENT, path, 0, 1, ContentType.CODE, null);
    DiffHunk diffHunk = new DiffHunk(0, FileType.JAVA, ChangeType.ADDED, bHunk, cHunk);


    Action actionA = new Action(Operation.ADD, "ImportStatement", "A");
    Action actionB = new Action(Operation.ADD, "ImportStatement", "B");
    Action actionC = new Action(Operation.ADD, "ImportStatement", "C");
    Action actionD = new Action(Operation.ADD, "ImportStatement", "D");

    List<Action> astActions = new ArrayList<>();
    astActions.add(actionA);
    astActions.add(actionC);
    List<Action> refActions = new ArrayList<>();
    refActions.add(actionB);
    refActions.add(actionD);
    diffHunk.setAstActions(astActions);
    diffHunk.setRefActions(refActions);
    List<DiffHunk> diffHunks = new ArrayList<>();
    diffHunks.add(diffHunk);


    // expected commitMsg
    String expected = "Feature - Add ImportStatement";
    // add importStatement and importStatement

    CommitMsgGenerator commitMsgGenerator = new CommitMsgGenerator(diffHunks);
    String received = commitMsgGenerator.generateDetailedMsgs(MsgClass.FEAT, GroupLabel.FEATURE).get(0);
    assertEquals(expected, received);


  }


}


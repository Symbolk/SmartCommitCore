package com.github.smartcommit.intent;

// DataCollector

import com.github.gumtreediff.actions.ChawatheScriptGenerator;
import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.model.Delete;
import com.github.gumtreediff.actions.model.Insert;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.actions.model.Update;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.TreeContext;
import com.github.smartcommit.client.Config;
import com.github.smartcommit.client.SmartCommit;
import com.github.smartcommit.core.GroupGenerator;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.intent.model.*;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.constant.Operation;
import com.github.smartcommit.util.Utils;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

// Main Class: Commit message:  Get, Label and Store
public class CommitInfoHandler {
  private static final Logger logger = Logger.getLogger(GroupGenerator.class);

  public static void main(String[] args) {
    args = new String[] {"/Users/Chuncen/Desktop/Repos/lwjgl3", "commitTrainingSample"};
    String repoPath = args[0];
    String collectionName = args[1];
    // CommitTrainingSample
    List<CommitTrainingSample> commitsInfo = new ArrayList<>();

    try {
      CommitsCollector(repoPath, commitsInfo);
      MongoDatabase database = MongoDBUtil.getConnection("localhost", "27017", "commitsDB");
      MongoCollection<Document> collection = database.getCollection(collectionName);
      trainningSampleAnalyzer(repoPath, commitsInfo, collection);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Split "git commit"
  public static boolean CommitsCollector(
      String REPO_DIR, List<CommitTrainingSample> commitTrainingSample) {
    String log = Utils.runSystemCommand(REPO_DIR, StandardCharsets.UTF_8, "git", "log");
    String[] parts = log.split("\\ncommit ");
    parts[0] = parts[0].substring("commit ".length());
    for (String part : parts) {
      CommitTrainingSample tempCommitTrainingSample = new CommitTrainingSample();
      String[] body = part.split("\\nAuthor: | <|>\\nDate:   |\\n\\n  ");
      if (body.length < 5) continue;
      // String commitID
      tempCommitTrainingSample.setCommitID(body[0].substring(0, 40));
      // String committer
      tempCommitTrainingSample.setCommitter(body[1]);
      // String committerEmail
      tempCommitTrainingSample.setCommitEmail(body[2]);
      // String commitTime
      tempCommitTrainingSample.setCommitTime(body[3]);
      // String commitMsg
      tempCommitTrainingSample.setCommitMsg(body[4]);
      // Add into List
      commitTrainingSample.add(tempCommitTrainingSample);
    }
    return true;
  }

  // get IntentList and ActionList
  public static boolean trainningSampleAnalyzer(
      String repoPath,
      List<CommitTrainingSample> commitTrainingSample,
      MongoCollection<Document> collection)
      throws Exception {

    // Analyze the Sample List
    Integer size = commitTrainingSample.size();
    for (int i = 20; i < size; i++) {
      ExecutorService service = Executors.newSingleThreadExecutor();
      Future<?> f = null;
      try {
        int finalI = i;
        Runnable r =
            () -> {
              try {
                analyzeCommitTrainingSample(
                    repoPath, commitTrainingSample, collection, finalI, size);
              } catch (Exception e) {
                e.printStackTrace();
              }
            };
        f = service.submit(r);
        f.get(300, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        f.cancel(true);
        logger.warn(String.format("Ignore refactoring detection due to timeout: "), e);
      } catch (ExecutionException | InterruptedException e) {
        logger.warn(String.format("Ignore refactoring detection due to RM error: "), e);
        //        e.printStackTrace();
      } finally {
        service.shutdown();
      }
    }

    return true;
  }

  private static void analyzeCommitTrainingSample(
      String repoPath,
      List<CommitTrainingSample> commitTrainingSample,
      MongoCollection<Document> collection,
      int i,
      int size)
      throws Exception {
    int index = repoPath.lastIndexOf(File.separator);
    String repoName = repoPath.substring(index + 1);
    String repoID = String.valueOf(Math.abs(repoName.hashCode()));

    CommitTrainingSample tempCommitTrainingSample = commitTrainingSample.get(i);
    String commitID = tempCommitTrainingSample.getCommitID();
    logger.warn(String.format("Proceeding: " + commitID + "  " + (i + 1) + "/" + size));
    tempCommitTrainingSample.setRepoID(repoID);
    tempCommitTrainingSample.setRepoPath(repoPath);
    tempCommitTrainingSample.setRepoName(repoName);

    String commitMsg = tempCommitTrainingSample.getCommitMsg().toLowerCase();
    String tempDir = "~/Downloads/";
    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
    DataCollector dataCollector = new DataCollector(repoName, tempDir);

    // get Intent from commitMsg
    Intent intent = getIntentFromMsg(commitMsg);
    tempCommitTrainingSample.setIntent(intent);
    // get List<IntentDescription> from commitMsg
    List<IntentDescription> intentList = getIntentDescriptionFromMsg(commitMsg);
    tempCommitTrainingSample.setIntentDescription(intentList);

    // add DiffFiles, though the same as DiffHunks
    List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);
    tempCommitTrainingSample.setDiffFiles(diffFiles);

    // add astActionList using gumtree
    tempCommitTrainingSample =
        generateGumtreeActionsFromCodeChange(tempCommitTrainingSample, repoAnalyzer);
    // add DiffHunkActions using DiffHunks
    List<Action> DiffHunkActions =
        generateActionListFromDiffHunks(tempCommitTrainingSample, dataCollector);
    tempCommitTrainingSample.setDiffHunksActions(DiffHunkActions);
    // add refactorCodeChange using RefactoringMiner
    List<RefactorMinerAction> refactorMinerActions =
        getRefactorCodeChangesFromCodeChange(repoPath, commitID);
    tempCommitTrainingSample.setRefactorMinerActions(refactorMinerActions);

    // Load into DB
    loadTrainSampleToDB(collection, tempCommitTrainingSample);
  }

  // generate Intent from Message
  private static Intent getIntentFromMsg(String commitMsg) {
    String[] parts = commitMsg.toLowerCase().split("\\n");
    for (int i = 0; i < parts.length; i++) {
      for (Intent intent : Intent.values()) {
        if (parts[i].contains(intent.label)) {
          return intent;
        }
      }
    }
    return Intent.OTHERS;
  }

  // generate IntentDescription from Message
  private static List<IntentDescription> getIntentDescriptionFromMsg(String commitMsg) {
    int msgSize = commitMsg.length();
    List<IntentDescription> IntentDescriptions = new ArrayList<>();
    List<IntentDescription> ids = new ArrayList<>();
    List<Integer> indexes = new ArrayList<>();
    // record contains and indexes
    for (IntentDescription id : IntentDescription.values()) {
      String des = id.label.toLowerCase();
      Integer index = commitMsg.indexOf(des);
      if (index > -1) {
        ids.add(id);
        indexes.add(index);
      }
    }
    // get ids in index order
    for (int i = 0; i < msgSize; i++) {
      for (int j = 0; j < indexes.size(); j++) {
        if (i == indexes.get(j)) {
          IntentDescriptions.add(ids.get(j));
          i += ids.get(j).label.length() - 1;
        }
      }
    }
    return IntentDescriptions;
  }

  // generate Gumtree action list from code changes: diffFile and EditScript
  private static CommitTrainingSample generateGumtreeActionsFromCodeChange(
      CommitTrainingSample tempCommitTrainingSample, RepoAnalyzer repoAnalyzer) {
    try { // if no FileChange
      List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(tempCommitTrainingSample.getCommitID());
      // get EditScript from diffFiles, and get ActionList from EditScript
      List<AstAction> tempActionList = new ArrayList<>();
      Integer sizeDiff = diffFiles.size();
      for (int j = 0; j < sizeDiff; j++) {
        String baseContent = diffFiles.get(j).getBaseContent();
        String currentContent = diffFiles.get(j).getCurrentContent();
        // File added or deleted, thus no content
        if (baseContent == null
            || baseContent.equals("")
            || currentContent == null
            || currentContent.equals("")) {
          tempCommitTrainingSample.addGumtreeCountFileChange();
          continue;
        }
        EditScript editScript = generateEditScript(baseContent, currentContent);
        if (editScript != null) {
          List<AstAction> actionList = generateAstActionList(editScript);
          tempActionList.addAll(actionList);
          tempCommitTrainingSample.setGumtreeActionList(tempActionList);
        } else {
          // Only doc change, thus no CodeChange and AbstractJdtTree generated
          tempCommitTrainingSample.addGumtreeCountDocChange();
        }
      }
    } catch (Exception e) {
      // e.printStackTrace();
      // Lack of File, thus no DiffFiles generated
      tempCommitTrainingSample.addGumtreeCountFileChange();
    }
    return tempCommitTrainingSample;
  }
  // generate Edit Script from file contents
  private static EditScript generateEditScript(String baseContent, String currentContent) {
    JdtTreeGenerator generator = new JdtTreeGenerator();
    try {
      TreeContext oldContext = generator.generateFrom().string(baseContent);
      TreeContext newContext = generator.generateFrom().string(currentContent);

      Matcher matcher = Matchers.getInstance().getMatcher();
      MappingStore mappings = matcher.match(oldContext.getRoot(), newContext.getRoot());
      EditScript editScript = new ChawatheScriptGenerator().computeActions(mappings);
      return editScript;
    } catch (Exception e) {
      // e.printStackTrace();
      // Failure to generate AbstractJdtTree because of the docChange instead of codeChange
      return null;
    }
  }
  // generate Commit Info from different file pathway
  private static List<AstAction> generateAstActionList(EditScript editScript) {
    List<AstAction> actionList = new ArrayList<>();
    for (Iterator iter = editScript.iterator(); iter.hasNext(); ) {
      com.github.gumtreediff.actions.model.Action action =
          (com.github.gumtreediff.actions.model.Action) iter.next();
      ASTOperation ASTOperation = null;
      if (action instanceof Insert) {
        ASTOperation = ASTOperation.ADD;
      } else if (action instanceof Delete) {
        ASTOperation = ASTOperation.DEL;
      } else if (action instanceof Move) {
        ASTOperation = ASTOperation.MOV;
      } else if (action instanceof Update) {
        ASTOperation = ASTOperation.UPD;
      }
      AstAction myAction = new AstAction(ASTOperation, action.getNode().getType().toString());
      actionList.add(myAction);
    }
    return actionList;
  }

  private static Action convertAstActionToAction(AstAction astAction) {
    Operation op = Operation.UKN;
    for (Operation operation : Operation.values()) {
      if (astAction.getASTOperation().label.equals(operation.label)) {
        op = operation;
        break;
      }
    }
    return new Action(op, astAction.getASTNodeType(), "");
  }

  // generate DiffHunkActions from diffHunks
  private static List<Action> generateActionListFromDiffHunks(
      CommitTrainingSample tempCommitTrainingSample, DataCollector dataCollector) throws Exception {

    String REPO_ID = tempCommitTrainingSample.getRepoID();
    String REPO_NAME = tempCommitTrainingSample.getRepoName();
    String REPO_PATH = tempCommitTrainingSample.getRepoPath();
    String TEMP_DIR = "~/Downloads/";
    String COMMIT_ID = tempCommitTrainingSample.getCommitID();
    List<Action> AstActions = new ArrayList<>();

    SmartCommit smartCommit = new SmartCommit(REPO_ID, REPO_NAME, REPO_PATH, TEMP_DIR);
    smartCommit.setDetectRefactorings(false);
    smartCommit.setSimilarityThreshold(Config.SIMI_THRESHOLD);
    smartCommit.setDistanceThreshold(Config.DIS_THRESHOLD);

    try {
      Map<String, Group> groups = smartCommit.analyzeCommit(COMMIT_ID);
      Map<String, DiffHunk> id2DiffHunkMap = smartCommit.getId2DiffHunkMap();
      for (Map.Entry<String, Group> entry : groups.entrySet()) {
        Group group = entry.getValue();
        List<String> diffHunkIDs = group.getDiffHunkIDs();
        for (String id : diffHunkIDs) {
          DiffHunk diffHunk = id2DiffHunkMap.getOrDefault(id.split(":")[1], null);
          if (diffHunk != null) AstActions.addAll(dataCollector.analyzeASTActions(diffHunk));
        }
      }
    } catch (Exception e) {
      System.out.println("DiffHunk Graph building failure");
    }
    return AstActions;
  }

  // generate RefactorCodeChangeFromCodeChange
  private static List<RefactorMinerAction> getRefactorCodeChangesFromCodeChange(
      String repoPath, String commitID) {
    GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
    org.refactoringminer.api.GitService gitService = new GitServiceImpl();
    List<RefactorMinerAction> refactorMinerActions = new ArrayList<>();
    try {
      Repository repo = gitService.openRepository(repoPath);
      miner.detectAtCommit(
          repo,
          commitID,
          new RefactoringHandler() {
            @Override
            public void handle(String commitId, List<Refactoring> refactorings) {
              // System.out.println("Refactorings at " + commitId);
              if (refactorings.isEmpty()) {
                System.out.println("No refactoring generated");
              } else {
                for (Refactoring ref : refactorings) {
                  RefactorMinerAction refactorMinerAction =
                      new RefactorMinerAction(ref.getRefactoringType(), ref.getName());
                  refactorMinerActions.add(refactorMinerAction);
                }
              }
            }
          });
    } catch (Exception e) {
      System.out.println("Repo Not Exist");
      // e.printStackTrace();
    }
    return refactorMinerActions;
  }

  private static Action convertRefactorCodeChangeToAction(RefactorMinerAction refactorMinerAction) {
    Operation op = Operation.UKN;
    for (Operation operation : Operation.values()) {
      if (refactorMinerAction.getName().equals(operation.label)) {
        op = operation;
        break;
      }
    }
    return new Action(op, refactorMinerAction.getRefactoringType(), "");
  }

  // Load given commitTrainingSample into given DB collection
  public static void loadTrainSampleToDB(
      MongoCollection<Document> collection, CommitTrainingSample commitTrainingSample) {
    try {
      Document doc1 = new Document();
      doc1.put("repoID", commitTrainingSample.getRepoID());
      doc1.put("repoPath", commitTrainingSample.getRepoPath());
      doc1.put("repoName", commitTrainingSample.getRepoName());
      doc1.put("commitID", commitTrainingSample.getCommitID());
      doc1.put("commitMsg", commitTrainingSample.getCommitMsg());
      doc1.put("committer", commitTrainingSample.getCommitter());
      doc1.put("committerEmail", commitTrainingSample.getCommitterEmail());
      doc1.put("commitTime", commitTrainingSample.getCommitTime());
      doc1.put("commitIntent", commitTrainingSample.getIntent().get0().getLabel());
      doc1.put(
          "commitIntentDescription", String.valueOf(commitTrainingSample.getIntentDescription()));
      doc1.put("GumtreeCountFileChange", commitTrainingSample.getGumtreeCountFileChange());
      doc1.put("GumtreeCountDocChange", commitTrainingSample.getGumtreeCountDocChange());

      {
        Integer NumOfDiffFiles = 0,
            NumOfDiffFilesJava = 0,
            NumOfDiffFilesNonJAVA = 0,
            NumOfDiffFilesAdded = 0,
            NumOfDiffFilesModified = 0,
            NumOfDiffFilesAddedJAVA = 0,
            NumOfDiffFilesModifiedJAVA = 0,
            NumOfDiffFilesAddedXML = 0,
            NumOfDiffFilesModifiedXML = 0,
            NumOfDiffHunks = 0,
            AveLinesOfDiffHunks = 0,
            SumOfLinesAdded = 0,
            SumOfLinesDeleted = 0,
            SumOfLinesModified = 0,
            SumOfLinesChanged = 0,
            SumOfLinesChangedJava = 0,
            SumOfLinesChangedXML = 0,
            SumOfLinesChangedOthers = 0;
        // add DiffFile to DB
        List<DiffFile> diffFiles = commitTrainingSample.getDiffFiles();
        if (diffFiles != null) {
          List<Document> Files = new ArrayList<>();
          // diffFile Level
          for (DiffFile diffFile : diffFiles) {
            Document addrAttr = new Document();

            String RepoID = diffFile.getRepoID();
            addrAttr.put("RepoID", RepoID);
            String RepoName = diffFile.getRepoName();
            addrAttr.put("RepoName", RepoName);
            String FileID = diffFile.getFileID();
            addrAttr.put("FileID", FileID);
            String RawHeaders = diffFile.getRawHeaders().toString();
            addrAttr.put("RawHeaders", RawHeaders);

            String FileStatus = diffFile.getStatus().label;
            addrAttr.put("FileStatus", FileStatus);
            String FileType = diffFile.getFileType().label;
            addrAttr.put("FileType", FileType);

            String BaseRelativePath = diffFile.getBaseRelativePath();
            addrAttr.put("BaseRelativePath", BaseRelativePath);
            String CurrentRelativePath = diffFile.getCurrentRelativePath();
            addrAttr.put("CurrentRelativePath", CurrentRelativePath);
            String BaseContent = diffFile.getBaseContent();
            addrAttr.put("BaseContent", BaseContent);
            String CurrentContent = diffFile.getCurrentContent();
            addrAttr.put("CurrentContent", CurrentContent);
            Integer Index = diffFile.getIndex();
            addrAttr.put("Index", Index);
            List<DiffHunk> diffHunks = diffFile.getDiffHunks();
            addrAttr.put("NumOfDiffHunks", diffHunks.size());
            // diffHunk Level
            if (diffHunks.size() > 1) {
              List<Integer> nums = new ArrayList<>();
              List<List<String>> rawDiffsList = new ArrayList<>();
              for (int i = 0; i < diffHunks.size(); i++) {
                DiffHunk diffHunk = diffHunks.get(i);
                Integer num =
                    diffHunk.getBaseEndLine()
                        - diffHunk.getBaseStartLine()
                        + diffHunk.getCurrentEndLine()
                        - diffHunk.getCurrentStartLine();
                if (num > 0) SumOfLinesAdded += num;
                else if (num < 0) SumOfLinesDeleted += num;
                else SumOfLinesAdded += num;
                SumOfLinesChanged += num;
                if (FileType.equals("Java")) SumOfLinesChangedJava += num;
                else if (FileType.equals("XML")) SumOfLinesChangedXML += num;
                else SumOfLinesChangedOthers += num;

                nums.add(num);
                List<String> rawDiffs = diffHunk.getRawDiffs();
                rawDiffsList.add(rawDiffs);
              }
              addrAttr.put("changedLines", nums.toString());
              addrAttr.put("RawDiffsList", rawDiffsList);
            }

            if (FileStatus.equals("modified")) {
              NumOfDiffFilesModified += 1;
            } else if (FileStatus.equals("added")) {
              NumOfDiffFilesAdded += 1;
            }

            if (FileType.equals("Java")) {
              NumOfDiffFilesJava += 1;
              if (FileStatus.equals("modified")) NumOfDiffFilesModifiedJAVA += 1;
              if (FileStatus.equals("added")) NumOfDiffFilesAddedJAVA += 1;
            } else {
              NumOfDiffFilesNonJAVA += 1;
              if (FileType.equals("XML")) {
                if (FileStatus.equals("modified")) NumOfDiffFilesModifiedXML += 1;
                if (FileStatus.equals("added")) NumOfDiffFilesAddedXML += 1;
              }
            }

            NumOfDiffFiles = diffFiles.size();
            NumOfDiffHunks = diffHunks.size();
            AveLinesOfDiffHunks = SumOfLinesChanged / NumOfDiffHunks;

            Files.add(addrAttr);
          }

          doc1.put("DiffFiles", Files);
          Document features = new Document();
          features.put("NumOfDiffFiles", NumOfDiffFiles);
          features.put("NumOfDiffFilesJava", NumOfDiffFilesJava);
          features.put("NumOfDiffFilesNonJAVA", NumOfDiffFilesNonJAVA);
          features.put("NumOfDiffFilesAdded", NumOfDiffFilesAdded);
          features.put("NumOfDiffFilesModified", NumOfDiffFilesModified);
          features.put("NumOfDiffFilesAddedJAVA", NumOfDiffFilesAddedJAVA);
          features.put("NumOfDiffFilesModifiedNonJAVA", NumOfDiffFilesModifiedJAVA);
          features.put("NumOfDiffFilesAddedXML", NumOfDiffFilesAddedXML);
          features.put("NumOfDiffFilesModifiedXML", NumOfDiffFilesModifiedXML);
          features.put("NumOfDiffHunks", NumOfDiffHunks);
          features.put("AveLinesOfDiffHunks", AveLinesOfDiffHunks);
          features.put("SumOfLinesAdded", SumOfLinesAdded);
          features.put("SumOfLinesDeleted", SumOfLinesDeleted);
          features.put("SumOfLinesModified", SumOfLinesModified);
          features.put("SumOfLinesChanged", SumOfLinesChanged);
          features.put("SumOfLinesChangedJava", SumOfLinesChangedJava);
          features.put("SumOfLinesChangedXML", SumOfLinesChangedXML);
          features.put("SumOfLinesOthers", SumOfLinesChangedOthers);
          doc1.put("18Features", features);
        }
      }

      {
        List<Action> Actions1 = new ArrayList<>();
        // add ActionList to DB
        List<AstAction> GumtreeActions = commitTrainingSample.getGumtreeActionList();
        if (GumtreeActions != null) {
          List<Document> actions = new ArrayList<>();
          for (AstAction astAction : GumtreeActions) {
            Document addrAttr = new Document();
            Actions1.add(convertAstActionToAction(astAction));
            addrAttr.put("operation", String.valueOf(astAction.getASTOperation()));
            addrAttr.put("astNodeType", astAction.getASTNodeType());
            actions.add(addrAttr);
          }
          doc1.put("GumtreeActions", actions);
        }

        List<Action> Actions2 = new ArrayList<>();
        // add DiffHunkActions to DB
        List<Action> DiffHunkActions = commitTrainingSample.getDiffHunksActions();
        if (DiffHunkActions != null) {
          List<Document> actions = new ArrayList<>();
          for (Action DiffHunkAction : DiffHunkActions) {
            Document addrAttr = new Document();
            addrAttr.put("operation", DiffHunkAction.getOperation().toString());
            addrAttr.put("DiffHunkType", DiffHunkAction.getTypeFrom());
            actions.add(addrAttr);
            Actions2.add(DiffHunkAction);
          }
          doc1.put("DiffHunkActions", actions);
        }

        List<Action> Actions3 = new ArrayList<>();
        // add refactorCodeChange to DB
        List<RefactorMinerAction> refactorMinerActions =
            commitTrainingSample.getRefactorMinerActions();
        if (refactorMinerActions != null) {
          List<Document> actions = new ArrayList<>();
          for (RefactorMinerAction refactorMinerAction : refactorMinerActions) {
            Document addrAttr = new Document();
            addrAttr.put("operation", refactorMinerAction.getOperation());
            addrAttr.put("refactoringType", refactorMinerAction.getRefactoringType());
            actions.add(addrAttr);
            Actions3.add(convertRefactorCodeChangeToAction(refactorMinerAction));
          }
          doc1.put("refactorMinerActions", actions);
        }
        {
          // add 3in1 to DB
          List<Document> actions = new ArrayList<>();
          Integer sizeActions1 = Actions1.size();
          Integer sizeActions2 = Actions2.size();
          Integer sizeActions3 = Actions3.size();
          for (int i = 0; i < sizeActions1; i++) {
            Document addrAttr = new Document();
            addrAttr.put("Gumtree", Actions1.get(i).toString());
            actions.add(addrAttr);
          }
          for (int i = 0; i < sizeActions2; i++) {
            Document addrAttr = new Document();
            addrAttr.put("DiffHunk", Actions2.get(i).toString());
            actions.add(addrAttr);
          }
          for (int i = 0; i < sizeActions3; i++) {
            Document addrAttr = new Document();
            addrAttr.put("RefactorMiner", Actions3.get(i).toString());
            actions.add(addrAttr);
          }
          doc1.put("AllActions", actions);
        }
      }

      collection.insertOne(doc1);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

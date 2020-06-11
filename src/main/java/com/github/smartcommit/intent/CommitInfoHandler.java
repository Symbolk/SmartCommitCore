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

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

// Main Class: Commit message:  Get, Label and Store
public class CommitInfoHandler {
  private static final Logger logger = Logger.getLogger(GroupGenerator.class);

  public static void main(String[] args) {
    args =
            new String[]{
                    "/Users/Chuncen/Desktop/Repos/refactoring-toy-example", "commitTrainingSample"
            };
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
    String log = Utils.runSystemCommand(REPO_DIR, Charset.defaultCharset(), "git", "log");
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
    for (int i = 0; i < size; i++) {
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
    // get Intent from commitMsg
    Intent intent = getIntentFromMsg(commitMsg);
    tempCommitTrainingSample.setIntent(intent);
    // get List<IntentDescription> from commitMsg
    List<IntentDescription> intentList = getIntentDescriptionFromMsg(commitMsg);
    tempCommitTrainingSample.setIntentDescription(intentList);

    String tempDir = "~/";
    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
    DataCollector dataCollector = new DataCollector(repoName, tempDir);

    // add DiffFiles
    List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);
    tempCommitTrainingSample.setDiffFiles(diffFiles);

    // add astActionList using gumtree
    tempCommitTrainingSample =
            generateGumtreeActionsFromCodeChange(tempCommitTrainingSample, repoAnalyzer);
    // add DiffHunkActions using DiffHunks
    List<Action> DiffHunkActions =
            generateDiffHunkActionListFromSmartCommit(tempCommitTrainingSample, dataCollector);
    tempCommitTrainingSample.setDiffHunksActions(DiffHunkActions);

    // Load into DB
    loadTrainSampleToDB(collection, tempCommitTrainingSample);
  }

  // generate Intent from Message
  private static Intent getIntentFromMsg(String commitMsg) {
    Integer index0, index;
    Intent intent0 = Intent.OTHERS;
    String[] parts = commitMsg.toLowerCase().split("\\n");
    for (int i = 0; i < parts.length; i++) {
      for (Intent intent : Intent.values()) {
        index0 = parts[i].length() + 1;
        index = parts[i].indexOf(intent.label);
        if (index.equals(-1)) continue;
        if (index < index0) {
          index0 = index;
          intent0 = intent;
        }
      }
    }
    return intent0;
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

  // generate Gumtree action list from code changes EditScript
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
          continue;
        }
        // EditScript editScript = generateEditScript(baseContent, currentContent);

        JdtTreeGenerator generator = new JdtTreeGenerator();
        TreeContext oldContext = generator.generateFrom().string(baseContent);
        TreeContext newContext = generator.generateFrom().string(currentContent);

        Matcher matcher = Matchers.getInstance().getMatcher();
        MappingStore mappings = matcher.match(oldContext.getRoot(), newContext.getRoot());
        EditScript editScript = new ChawatheScriptGenerator().computeActions(mappings);

        if (editScript != null) {
          // List<AstAction> actionList = generateAstActionList(editScript);
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
          tempActionList.addAll(actionList);
          tempCommitTrainingSample.setGumtreeActionList(tempActionList);
        } else {
          logger.warn(String.format("no CodeChange and AbstractJdtTree generated "));
        }
      }
    } catch (Exception e) {
      logger.warn(String.format("no DiffFiles generated "), e);
    }
    return tempCommitTrainingSample;
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

  // generate DiffHunkActions from SmartCommit
  private static List<Action> generateDiffHunkActionListFromSmartCommit(
          CommitTrainingSample tempCommitTrainingSample, DataCollector dataCollector) throws Exception {

    String REPO_ID = tempCommitTrainingSample.getRepoID();
    String REPO_NAME = tempCommitTrainingSample.getRepoName();
    String REPO_PATH = tempCommitTrainingSample.getRepoPath();
    String TEMP_DIR = "~/";
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

  // Load given commitTrainingSample into given DB collection
  public static void loadTrainSampleToDB(
      MongoCollection<Document> collection, CommitTrainingSample commitTrainingSample) {
    try {
      Document doc = new Document();
      // raw
      Document raw = new Document();
      {
        Document RepoCommit = new Document();
        RepoCommit.put("repoID", commitTrainingSample.getRepoID());
        RepoCommit.put("repoPath", commitTrainingSample.getRepoPath());
        RepoCommit.put("repoName", commitTrainingSample.getRepoName());
        RepoCommit.put("commitID", commitTrainingSample.getCommitID());
        RepoCommit.put("commitMsg", commitTrainingSample.getCommitMsg());
        RepoCommit.put("committer", commitTrainingSample.getCommitter());
        RepoCommit.put("committerEmail", commitTrainingSample.getCommitterEmail());
        RepoCommit.put("commitTime", commitTrainingSample.getCommitTime());
        raw.put("Info", RepoCommit);

        List<DiffFile> diffFiles = commitTrainingSample.getDiffFiles();
        raw.put("NumOfDiffFiles", diffFiles.size());

        for (DiffFile diffFile : diffFiles) {
          int diffFileIndex = 0;
          Document diffFileDoc = new Document();
          diffFileDoc.put("FileType", diffFile.getFileType().toString());
          diffFileDoc.put("FileStatus", diffFile.getStatus().toString());
          diffFileDoc.put(
                  "index",
                  diffFile.getIndex()); // the index of the diff file in the current repo, start from 0
          diffFileDoc.put("baseRelativePath", diffFile.getBaseRelativePath());
          diffFileDoc.put("currentRelativePath", diffFile.getCurrentRelativePath());

          diffFileDoc.put("baseContent", diffFile.getBaseContent());
          diffFileDoc.put("currentContent", diffFile.getCurrentContent());

          List<DiffHunk> diffHunks = diffFile.getDiffHunks();
          diffFileDoc.put("NumOfDiffHunks", diffHunks.size());

          for (DiffHunk diffHunk : diffHunks) {
            int diffHunkIndex = 0;
            Document diffHunkDoc = new Document();
            diffHunkDoc.put("ChangeType", diffHunk.getChangeType().toString());
            diffHunkDoc.put("baseHunkContentType", diffHunk.getBaseHunk().getContentType().toString());
            diffHunkDoc.put("currentHunkContentType", diffHunk.getCurrentHunk().getContentType().toString());
            List<String> RawDiffList = diffHunk.getRawDiffs();
            diffHunkDoc.put("numOfRawDiffList", RawDiffList.size());
            if (!RawDiffList.isEmpty()) {
              Document rawdiffs = new Document();
              int rawDiffIndex = 0;
              for (String rawdiff : RawDiffList) {
                rawdiffs.put("Line " + rawDiffIndex++, rawdiff);
              }
              diffHunkDoc.put("RawDiffList", rawdiffs);
            }
            diffFileDoc.put("DiffHunk " + diffHunkIndex++, diffHunkDoc);
          }
          raw.put("DiffFile " + diffFileIndex++, diffFileDoc);
        }

      }
      // Extraction
      Document extract = new Document();
      {
        Document fromMsg = new Document();
        fromMsg.put("Intent", commitTrainingSample.getIntent().getLabel());
        fromMsg.put(
                "commitIntentDescription", String.valueOf(commitTrainingSample.getIntentDescription()));
        extract.put("fromMsg", fromMsg);

        Document fromCommit = new Document();
        {
          // add GumtreeAction
          List<AstAction> GumtreeActions = commitTrainingSample.getGumtreeActionList();
          if (GumtreeActions != null) {
            List<Document> actions = new ArrayList<>();
            for (AstAction astAction : GumtreeActions) {
              Document addrAttr = new Document();
              addrAttr.put("operation", String.valueOf(astAction.getASTOperation()));
              addrAttr.put("astNodeType", astAction.getASTNodeType());
              actions.add(addrAttr);
            }
            fromCommit.put("GumtreeActions", actions);
          }
        }
        {
          // add DiffHunkActions
          List<Action> DiffHunkActions = commitTrainingSample.getDiffHunksActions();
          List<Document> actions = new ArrayList<>();
          for (Action DiffHunkAction : DiffHunkActions) {
            Document addrAttr = new Document();
            addrAttr.put("operation", DiffHunkAction.getOperation().toString());
            addrAttr.put("DiffHunkType", DiffHunkAction.getTypeFrom());
            actions.add(addrAttr);
          }
          fromCommit.put("DiffHunkActions", actions);
        }
        extract.put("fromCommit", fromCommit);
      }
      doc.put("Raw", raw);
      doc.put("Extraction", extract);
      collection.insertOne(doc);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

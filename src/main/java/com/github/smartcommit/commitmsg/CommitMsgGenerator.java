package com.github.smartcommit.commitmsg;

import com.github.smartcommit.client.SmartCommit;
import com.github.smartcommit.intent.model.MsgClass;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.GroupLabel;
import com.github.smartcommit.model.constant.Operation;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class CommitMsgGenerator {
  private static final Logger Logger = org.apache.log4j.Logger.getLogger(SmartCommit.class);
  private List<Action> astActions;
  private List<Action> refactorActions;
  private String commitMsg;
  private List<DiffHunk> diffHunks;

  public CommitMsgGenerator(List<DiffHunk> diffHunks) {
    this.astActions = new ArrayList<>();
    this.refactorActions = new ArrayList<>();
    this.diffHunks = diffHunks;
    for (DiffHunk diffHunk : diffHunks) {
      this.astActions.addAll(diffHunk.getAstActions());
      this.refactorActions.addAll(diffHunk.getRefActions());
    }
  }

  /**
   * Vectorize the group features
   *
   * @return
   */
  public List<Integer> generateGroupVector() {
    // Operation: 4(astAction)
    // Type: 99(astAction)
    int AstOperationSum = 4;
    int AstTypeSum = 99;
    int AstSum = AstOperationSum * AstTypeSum;
    int numFeatures = 18;
    int vectorSize = AstSum + numFeatures;

    List<Integer> vectors = new ArrayList<>(Collections.nCopies(vectorSize, 0));

    int indexOperation, indexType, indexFinal, valueFinal;
    try {
      for (Action action : astActions) {
        indexOperation = action.getOperationIndex();
        String typeFrom = action.getTypeFrom();
        // when DiffHunk Graph building fails, get "code" and contribute nothing to vector
        if (typeFrom.equals("Code")) continue;
        indexType = -1;
        for (AstActionType astActionType : AstActionType.values()) {
          if (typeFrom.equals(astActionType.label)) {
            indexType = astActionType.index;
            break;
          }
        }
        if (indexType != -1) {
          indexFinal = (indexOperation - 1) * AstTypeSum + indexType - 1;
          valueFinal = vectors.get(indexFinal) + 1;
          vectors.set(indexFinal, valueFinal);
        }
      }

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
      Set<String> fileIDs = new HashSet<>();

      for (DiffHunk diffHunk : diffHunks) {
        String fileID = diffHunk.getFileID();
        if (fileIDs.contains(fileID)) continue;
        fileIDs.contains(fileID);
        String fileType = diffHunk.getFileType().label;
        String fileStatus = diffHunk.getChangeType().label;
        if (fileType.equals("Java")) NumOfDiffFilesJava += 1;
        else NumOfDiffFilesNonJAVA += 1;
        if (fileStatus.equals("Add")) {
          NumOfDiffFilesAdded += 1;
          if (fileType.equals("Java")) NumOfDiffFilesAddedJAVA += 1;
          else if (fileType.equals("XML")) NumOfDiffFilesAddedXML += 1;
        } else if (fileStatus.equals("Modify")) {
          NumOfDiffFilesModified += 1;
          if (fileType.equals("Java")) NumOfDiffFilesModifiedJAVA += 1;
          else if (fileType.equals("XML")) NumOfDiffFilesModifiedXML += 1;
        }
        Integer num =
            diffHunk.getBaseEndLine()
                - diffHunk.getBaseStartLine()
                + diffHunk.getCurrentEndLine()
                - diffHunk.getCurrentStartLine();
        if (num > 0) SumOfLinesAdded += num;
        else if (num < 0) SumOfLinesDeleted += num;
        else SumOfLinesAdded += num;
        SumOfLinesChanged += num;
        if (fileType.equals("Java")) SumOfLinesChangedJava += num;
        else if (fileType.equals("XML")) SumOfLinesChangedXML += num;
        else SumOfLinesChangedOthers += num;
      }
      NumOfDiffFiles = fileIDs.size();
      NumOfDiffHunks = diffHunks.size();
      AveLinesOfDiffHunks = SumOfLinesChanged / NumOfDiffHunks;

      vectors.set(AstSum + 0, NumOfDiffFiles);
      vectors.set(AstSum + 1, NumOfDiffFilesJava);
      vectors.set(AstSum + 2, NumOfDiffFilesNonJAVA);
      vectors.set(AstSum + 3, NumOfDiffFilesAdded);
      vectors.set(AstSum + 4, NumOfDiffFilesModified);
      vectors.set(AstSum + 5, NumOfDiffFilesAddedJAVA);
      vectors.set(AstSum + 6, NumOfDiffFilesModifiedJAVA);
      vectors.set(AstSum + 7, NumOfDiffFilesAddedXML);
      vectors.set(AstSum + 8, NumOfDiffFilesModifiedXML);
      vectors.set(AstSum + 9, NumOfDiffHunks);
      vectors.set(AstSum + 10, AveLinesOfDiffHunks);
      vectors.set(AstSum + 11, SumOfLinesAdded);
      vectors.set(AstSum + 12, SumOfLinesDeleted);
      vectors.set(AstSum + 13, SumOfLinesModified);
      vectors.set(AstSum + 14, SumOfLinesChanged);
      vectors.set(AstSum + 15, SumOfLinesChangedJava);
      vectors.set(AstSum + 16, SumOfLinesChangedXML);
      vectors.set(AstSum + 17, SumOfLinesChangedOthers);

    } catch (Exception e) {
      e.printStackTrace();
      Logger.info("fail to generate Group Vector");
    }
    return vectors;
  }

  /**
   * Invoke the AI model to generate template commit msg
   *
   * @param vectors
   * @return
   */
  public MsgClass invokeAIModel(List<Integer> vectors) {
    return null;
  }

  /**
   * Generate final detailed commit message GroupLabel - ObjectMsg(MsgClass + Object)
   *
   * @return
   */
  public List<String> generateDetailedMsgs(MsgClass msgClass, GroupLabel intentLabel) {

    // Count Frequency of typeFrom in Actions whose q equals key currently
    String key = msgClass.label;

    List<Action> actions = new ArrayList<>();
    actions.addAll(astActions);
    actions.addAll(refactorActions);

    // Matched Cases: count by frequency
    List<Integer> IndexesUnlabeledMatched = getTop2IndexTypeFrom(actions, key);
    // Special Cases: package, class, method，interface
    List<Integer> IndexesLabeled = get4IndexOfTypeFrom(actions);

    // 2 matches between MsgClass and AstAction
    if (IndexesUnlabeledMatched.size() > 1) {
      Action action0 = actions.get(IndexesUnlabeledMatched.get(0));
      Action action1 = actions.get(IndexesUnlabeledMatched.get(1));
      commitMsg = generateObjectMsgFromActions(action0, false, action1, false);
    }
    // 1 matches between MsgClass and AstAction
    else if (IndexesUnlabeledMatched.size() == 1) {
      if (IndexesLabeled.size() > 0) {
        Action action0 = actions.get(IndexesUnlabeledMatched.get(0));
        Action action1 = actions.get(IndexesLabeled.get(0));
        commitMsg = generateObjectMsgFromActions(action0, false, action1, true);
      } else {
        Action action0 = actions.get(IndexesUnlabeledMatched.get(0));
        commitMsg = generateObjectMsgFromActions(action0, false);
      }

    }
    // 0 match between MsgClass and AstAction
    else {
      // Unmatched Cases: count by frequency
      List<Integer> IndexesUnlabeledUnmatched = getTop2IndexTypeFrom(actions, "");
      // 2 special cases: package, class, method，interface
      if (IndexesLabeled.size() > 1) {
        Action action0 = actions.get(IndexesLabeled.get(0));
        Action action1 = actions.get(IndexesLabeled.get(1));
        commitMsg = generateObjectMsgFromActions(action0, true, action1, true);
      }
      // 1 special cases: package, class, method，interface
      else if (IndexesLabeled.size() == 1) {
        Action action0 = actions.get(IndexesLabeled.get(0));
        commitMsg = generateObjectMsgFromActions(action0, true);
      }
      // 0 special case: package, class, method，interface
      else {
        // 2 normal cases: top2
        if (IndexesUnlabeledUnmatched.size() > 1) {
          Action action0 = actions.get(IndexesUnlabeledUnmatched.get(0));
          Action action1 = actions.get(IndexesUnlabeledUnmatched.get(1));
          commitMsg = generateObjectMsgFromActions(action0, false, action1, false);
        }
        // 1 normal cases: only1
        else if (IndexesUnlabeledUnmatched.size() == 1) {
          Action action0 = actions.get(IndexesUnlabeledUnmatched.get(0));
          commitMsg = generateObjectMsgFromActions(action0, false);
        }
        // 0 normal case: no
        else {
          commitMsg = key + " ...";
        }
      }
    }

    // generate recommendedCommitMsg
    List<String> recommendedCommitMsgs = new ArrayList<>();
    String iLabel = "";
    if (intentLabel.label.equals("Others")) {
      if (key.equals("Fix")
          || key.equals(" Test")
          || key.equals("Reformat")
          || key.equals("Document")
          || key.equals("Revert")
          || key.equals("Refactor")) iLabel = key.toUpperCase();
      else iLabel = "FUNCTIONCHANGE";
    } else if (!intentLabel.label.equals(key) && key.equals("Fix")) {
      iLabel = key.toUpperCase();
    } else iLabel = intentLabel.toString();
    if (intentLabel.label.equals("Non-Java")) commitMsg = key + " code or files";
    recommendedCommitMsgs.add(iLabel + " - " + commitMsg);

    // read json to get templates
    JSONObject jsonObject = new JSONObject(getTemplate());
    JSONArray jsonArray = jsonObject.getJSONArray(key);
    for (int i = 0; i < jsonArray.length(); i++)
      recommendedCommitMsgs.add(jsonArray.get(i).toString());

    return recommendedCommitMsgs;
  }

  // get max2Count of Frequency in Actions
  private List<Integer> getTop2IndexTypeFrom(List<Action> Actions, String key) {
    int sizeActions = Actions.size();
    int count[] = new int[sizeActions];
    for (int i = 0; i < sizeActions; i++) {
      String typeFrom = Actions.get(i).getTypeFrom();
      if (typeFrom.equals("Code")) continue;
      if (Actions.get(i).getTypeFrom().isEmpty()) continue;
      if (!key.equals("") && !Actions.get(i).getOperation().label.equals(key)) continue;
      for (int j = 0; j < i; j++) {
        if (Actions.get(j).getTypeFrom().equals(typeFrom)) {
          count[j]++;
          break;
        }
      }
      count[i] = 1;
    }
    // lazy to sort, simply cycle once
    int max1 = 0, max2 = 0;
    int max1Index = 0, max2Index = 0;
    for (int i = 0; i < sizeActions; i++) {
      if (count[i] > max1) {
        max2 = max1;
        max1 = count[i];
        max2Index = max1Index;
        max1Index = i;
      } else if (count[i] > max2) {
        max2 = count[i];
        max2Index = i;
      }
    }
    List<Integer> maxIndexes = new ArrayList<>();
    if (max1 > 0) maxIndexes.add(max1Index);
    if (max2Index != max1Index) maxIndexes.add(max2Index);
    return maxIndexes;
  }

  // get 4Indexes(appear for the first time) in Actions: package, class, method，interface
  private List<Integer> get4IndexOfTypeFrom(List<Action> Actions) {
    int sizeActions = Actions.size();
    List<Integer> Indexes = new ArrayList<>();
    for (int i = 0; i < sizeActions && Indexes.size() < 2; i++)
      if (Actions.get(i).getTypeFrom().toLowerCase().contains("package")) Indexes.add(i);
    for (int i = 0; i < sizeActions && Indexes.size() < 2; i++)
      if (Actions.get(i).getTypeFrom().toLowerCase().contains("class")) Indexes.add(i);
    for (int i = 0; i < sizeActions && Indexes.size() < 2; i++)
      if (Actions.get(i).getTypeFrom().toLowerCase().contains("interface")) Indexes.add(i);
    for (int i = 0; i < sizeActions && Indexes.size() < 2; i++)
      if (Actions.get(i).getTypeFrom().toLowerCase().contains("method")) Indexes.add(i);
    return Indexes;
  }

  // read json to get template
  public String getTemplate() {
    String pathName = getClass().getClassLoader().getResource("MsgTemplate.json").getPath();
    System.out.println("testPath: " + pathName);
    BufferedReader in =
        new BufferedReader(
            new InputStreamReader(
                this.getClass().getClassLoader().getResourceAsStream("MsgTemplate.json")));
    StringBuffer buffer = new StringBuffer();
    String line = "";
    String content = null;
    try {
      while ((line = in.readLine()) != null) {
        buffer.append(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    content = buffer.toString();
    Logger.info("Get template from template json file");
    return content;
  }

  // only for label is needed
  private String generateObjectMsgFromActions(Action action0, boolean needLabel) {
    String objectMsg = null;
    if (action0.getOperation().equals(Operation.ADD)) objectMsg = "Add ";
    else objectMsg = "Modify ";
    if (needLabel) {
      if (action0.getLabelFrom().isEmpty()) objectMsg += action0.getTypeFrom();
      else objectMsg += action0.getTypeFrom() + " " + action0.getLabelFrom();
    } else {
      objectMsg += action0.getTypeFrom();
    }
    return objectMsg;
  }

  private String generateObjectMsgFromActions(
      Action action0, boolean needLabel0, Action action1, boolean needLabel1) {
    String objectMsg;
    // Operation
    if (action0.getOperation().equals(Operation.ADD)
        && action1.getOperation().equals(Operation.ADD)) objectMsg = "Add ";
    else objectMsg = "Modify ";
    // Type + label
    boolean Label0 = !action0.getLabelFrom().isEmpty() && needLabel0;
    boolean Label1 = !action1.getLabelFrom().isEmpty() && needLabel1;
    if (action0.getTypeFrom().equals(action1.getTypeFrom())) {
      if (!Label0 && !Label1) objectMsg += action0.getTypeFrom();
      else if (Label0 && !Label1) objectMsg += action0.getTypeFrom() + " " + action0.getLabelFrom();
      else if (!Label0 && Label1) objectMsg += action1.getTypeFrom() + " " + action1.getLabelFrom();
      else if (Label0 && Label1)
        objectMsg +=
            action0.getTypeFrom() + " " + action0.getLabelFrom() + " and " + action1.getLabelFrom();
    } else {
      if (!Label0 && !Label1) objectMsg += action0.getTypeFrom() + " and " + action1.getTypeFrom();
      else if (Label0 && !Label1)
        objectMsg +=
            action0.getTypeFrom() + " " + action0.getLabelFrom() + " and " + action1.getTypeFrom();
      else if (!Label0 && Label1)
        objectMsg +=
            action0.getTypeFrom() + " and " + action1.getTypeFrom() + " " + action1.getLabelFrom();
      else if (Label0 && Label1)
        objectMsg +=
            action0.getTypeFrom()
                + " "
                + action0.getLabelFrom()
                + " and "
                + action1.getTypeFrom()
                + " "
                + action1.getLabelFrom();
    }
    return objectMsg;
  }
}

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

    // generate commitMsgs
    List<String> commitMsgs = new ArrayList<>();
    commitMsg = generateCommitMsg(msgClass, intentLabel);
    commitMsgs.add(commitMsg);
    // read json to get templates
    JSONObject jsonObject = new JSONObject(getTemplate());
    JSONArray jsonArray = jsonObject.getJSONArray(msgClass.label);
    for (int i = 0; i < jsonArray.length(); i++) commitMsgs.add(jsonArray.get(i).toString());

    return commitMsgs;
  }

  private String generateCommitMsg(MsgClass msgClass, GroupLabel intentLabel) {
    String commitMsg = null;
    List<Integer> top2Index, prior4Index;
    switch (intentLabel) {
      case REFACTOR:
        commitMsg = "Refactor - ";
        top2Index = getTop2Index(refactorActions);
        if (!top2Index.isEmpty()) commitMsg += getRefactorObject(top2Index, refactorActions);
        else {
          prior4Index = getPrior4Index(refactorActions);
          if (!prior4Index.isEmpty()) commitMsg += getRefactorObject(prior4Index, refactorActions);
          else {
            if (!refactorActions.isEmpty())
              commitMsg +=
                  refactorActions.get(0).getOperation().label
                      + " "
                      + refactorActions.get(0).getLabelFrom();
            else {
              commitMsg = "Chore - Modify code";
            }
          }
        }
        return commitMsg;
      case REFORMAT:
        return "Style - Code reformat";
      case DOC:
        return "Docs - Document change";
      case CONFIG:
        return "Docs - Configuration file change";
      case NONJAVA:
        return "Docs - Other file change";
      case FIX:
      case FEATURE:
      case OTHER:
        commitMsg = msgClass.label + " - ";
        top2Index = getTop2Index(astActions);
        if (!top2Index.isEmpty()) commitMsg += getOtherObject(top2Index, astActions);
        else {
          prior4Index = getPrior4Index(astActions);
          if (!prior4Index.isEmpty()) commitMsg += getOtherObject(prior4Index, astActions);
          else {
            if (!astActions.isEmpty())
              commitMsg +=
                  astActions.get(0).getOperation().label
                      + " "
                      + astActions.get(0).getLabelFrom();
            else {
              commitMsg = "Chore - Modify code";
            }
          }
        }
        return commitMsg;
      default:
        return "Chore - Modify code";
    }
  }

  private String getRefactorObject(List<Integer> indexes, List<Action> actions) {
    if (indexes.size() == 2) {
      Action action0 = actions.get(indexes.get(0));
      Action action1 = actions.get(indexes.get(1));
      if (action0.getOperation().equals(action1.getOperation())) {
        return action0.getOperation().label
            + " "
            + action0.getTypeFrom()
            + " and "
            + action1.getTypeFrom();
      } else {
        return action0.getOperation().label
            + " "
            + action0.getTypeFrom()
            + " and "
            + action1.getOperation().label
            + " "
            + action1.getTypeFrom();
      }
    } else {
      Action action0 = actions.get(indexes.get(0));
      return action0.getOperation().label + " " + action0.getLabelFrom();
    }
  }

  private String getOtherObject(List<Integer> indexes, List<Action> actions) {
    if (indexes.size() == 2) {
      Action action0 = actions.get(indexes.get(0));
      Action action1 = actions.get(indexes.get(1));
      if (action0.getOperation().equals(action1.getOperation())) {
        if (action0.getTypeFrom().equals(action1.getTypeFrom())) {
          return action0.getOperation().label
                  + " "
                  + action0.getTypeFrom()
                  + " "
                  + action1.getLabelFrom()
                  + " and "
                  + action1.getLabelFrom();
        } else return action0.getOperation().label
                + " "
                + action0.getTypeFrom()
                + " "
                + action0.getLabelFrom()
                + " and "
                + action1.getTypeFrom()
                + " "
                + action1.getLabelFrom();
      } else {
        return action0.getOperation().label
                + " "
                + action0.getTypeFrom()
                + " "
                + action0.getLabelFrom()
                + " and "
                + action1.getOperation().label
                + " "
                + action1.getTypeFrom()
                + " "
                + action1.getLabelFrom();
      }
    } else {
      Action action0 = actions.get(indexes.get(0));
      return action0.getOperation().label
              + " "
              + action0.getTypeFrom()
              + " "
              + action0.getLabelFrom();
    }
  }

  // get top2Index: Operation and typeFrom both as identifier
  private List<Integer> getTop2Index(List<Action> Actions) {
    int sizeActions = Actions.size();
    int count[] = new int[sizeActions];
    for (int i = 0; i < sizeActions; i++) {
      Operation operation = Actions.get(i).getOperation();
      String typeFrom = Actions.get(i).getTypeFrom();
      if (typeFrom.equals("Code")) continue;
      if (Actions.get(i).getTypeFrom().isEmpty()) continue;
      for (int j = 0; j < i; j++) {
        if (Actions.get(j).getOperation().equals(operation)
            && Actions.get(j).getTypeFrom().equals(typeFrom)) {
          count[j]++;
          break;
        }
      }
      count[i] = 1;
    }
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

  // get prior4Indexes(appear for the first time): package, class, method，interface
  private List<Integer> getPrior4Index(List<Action> Actions) {
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
}

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

        Integer num = diffHunk.getRawDiffs().size();
        String changeType = diffHunk.getChangeType().label;
        if (changeType.equals("Add")) {
          SumOfLinesAdded += num;
        } else if (changeType.equals("Delete")) {
          SumOfLinesDeleted += num;
        } else {
          SumOfLinesModified += num;
        }
        SumOfLinesChanged += num;

        if (fileType.equals("Java")) SumOfLinesChangedJava += num;
        else if (fileType.equals("XML")) SumOfLinesChangedXML += num;
        else SumOfLinesChangedOthers += num;
      }
      NumOfDiffFiles = fileIDs.size();
      NumOfDiffHunks = diffHunks.size();
      if (NumOfDiffHunks > 0) AveLinesOfDiffHunks = SumOfLinesChanged / NumOfDiffHunks;

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
    commitMsg = commitMsg.replace("Delete ", "Remove ");
    commitMsg = commitMsg.replace("protected ", "");
    commitMsg = commitMsg.replace("public ", "");
    commitMsg = commitMsg.replace("private ", "");
    commitMsg = commitMsg.replace("abstract ", "");
    commitMsg = commitMsg.replace("()", "");
    commitMsgs.add(commitMsg);
    // read json to get templates
    JSONObject jsonObject = new JSONObject(getTemplate());
    JSONArray jsonArray = jsonObject.getJSONArray(msgClass.label);
    for (int i = 0; i < jsonArray.length(); i++) commitMsgs.add(jsonArray.get(i).toString());
    return commitMsgs;
  }

  private String generateCommitMsg(MsgClass msgClass, GroupLabel intentLabel) {
    String commitMsg = null;
    List<Action> actions = new ArrayList<>();
    switch (intentLabel) {
      case REFACTOR:
        commitMsg = "Refactor - ";
        // find Package
        actions = findActionPac(refactorActions);
        if (!actions.isEmpty()) {
          Boolean onlyOne = true;
          for (Action action : actions) {
            String operation = action.getOperation().label;
            String typeFrom = action.getTypeFrom();
            String labelFrom = action.getLabelFrom();
            if (onlyOne) onlyOne = false;
            else commitMsg += " and ";
            commitMsg += operation + " " + typeFrom + " " + labelFrom;
          }
        } else {
          // find Class, Method, Interface
          actions = findActionCMI(refactorActions);
          if (!actions.isEmpty()) {
            // find Top2 via Type&Label
            actions = findActionTop2TL(actions);
            commitMsg += addTypeLabel(actions);
          } else {
            // find Random2
            actions = findActionRand2(refactorActions);
            if (!actions.isEmpty()) commitMsg += addType(actions);
            else {
              return "Chore - Modify code";
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
        // find Package
        actions = findActionPac(astActions);
        if (!actions.isEmpty()) {
          Boolean onlyOne = true;
          for (Action action : actions) {
            String operation = action.getOperation().label;
            String typeFrom = action.getTypeFrom();
            String labelFrom = action.getLabelFrom();
            if (onlyOne) onlyOne = false;
            else commitMsg += " and ";
            commitMsg += operation + " " + typeFrom + " " + labelFrom;
          }
        } else {
          // find Class, Method, Interface
          actions = findActionCMI(astActions);
          if (!actions.isEmpty()) {
            // find Top2 via Type&Label
            actions = findActionTop2TL(actions);
            commitMsg += addTypeLabel(actions);
          } else {
            // fine Top2 via Type
            actions = findActionTop2T(astActions);
            if (!actions.isEmpty()) {
              commitMsg += addType(actions);
            } else {
              // find Random2
              actions = findActionRand2(astActions);
              if (!actions.isEmpty()) commitMsg += addType(actions);
              else {
                return "Chore - Modify code";
              }
            }
          }
        }
        return commitMsg;
      default:
        return "Chore - Modify code";
    }
  }

  // add type info into commitMsg
  private String addType(List<Action> actions) {
    String str = null;
    if (actions.size() == 2) {
      Action action0 = actions.get(0);
      Action action1 = actions.get(1);
      if (action0.getOperation().equals(action1.getOperation())) {
        if (action0.getTypeFrom().equals(action1.getTypeFrom()))
          return action0.getOperation().label + " " + action0.getTypeFrom();
        else {
          return action0.getOperation().label
                  + " "
                  + action0.getTypeFrom()
                  + " and "
                  + action1.getTypeFrom();
        }
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
      Action action0 = actions.get(0);
      return action0.getOperation().label + " " + action0.getTypeFrom();
    }
  }

  // add type and label info into commitMsg
  private String addTypeLabel(List<Action> actions) {
    if (actions.size() == 2) {
      Action action0 = actions.get(0);
      Action action1 = actions.get(1);
      if (action0.getOperation().equals(action1.getOperation())) {
        if (action0.getTypeFrom().equals(action1.getTypeFrom())) {
          if (action0.getLabelFrom().equals(action1.getLabelFrom()))
            return action0.getOperation().label
                    + " "
                    + action0.getTypeFrom()
                    + " "
                    + action0.getLabelFrom();
          else
            return action0.getOperation().label
                    + " "
                    + action0.getTypeFrom()
                    + " "
                    + action0.getLabelFrom()
                    + " and "
                    + action1.getLabelFrom();
        } else {
          return action0.getOperation().label
                  + " "
                  + action0.getTypeFrom()
                  + " "
                  + action0.getLabelFrom()
                  + " and "
                  + action1.getTypeFrom()
                  + " "
                  + action1.getLabelFrom();
        }
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
      Action action0 = actions.get(0);
      return action0.getOperation().label
              + " "
              + action0.getTypeFrom()
              + " "
              + action0.getLabelFrom();
    }
  }

  // get top2 via Type&Label
  private List<Action> findActionTop2TL(List<Action> Actions) {
    int sizeActions = Actions.size();
    int[] count = new int[sizeActions];
    for (int i = 0; i < sizeActions; i++) {
      String typeFrom = Actions.get(i).getTypeFrom();
      String labelFrom = Actions.get(i).getTypeFrom();
      if (typeFrom.equals("Code")) continue;
      if (Actions.get(i).getTypeFrom().isEmpty()) continue;
      for (int j = 0; j < i; j++) {
        if (Actions.get(j).getLabelFrom().equals(labelFrom)
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
    List<Action> actions = new ArrayList<>();
    if (max1 > 0) actions.add(Actions.get(max1Index));
    if (max2Index != max1Index
            && !Actions.get(max1Index).getTypeFrom().equals(Actions.get(max2Index).getTypeFrom())
            && !Actions.get(max1Index).getLabelFrom().equals(Actions.get(max2Index).getLabelFrom()))
      actions.add(Actions.get(max2Index));
    return actions;
  }

  // get top2 via Type
  private List<Action> findActionTop2T(List<Action> Actions) {
    int sizeActions = Actions.size();
    int[] count = new int[sizeActions];
    for (int i = 0; i < sizeActions; i++) {
      String typeFrom = Actions.get(i).getTypeFrom();
      if (typeFrom.equals("Code")) continue;
      if (Actions.get(i).getTypeFrom().isEmpty()) continue;
      for (int j = 0; j < i; j++) {
        if (Actions.get(j).getTypeFrom().equals(typeFrom)) {
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
    List<Action> actions = new ArrayList<>();
    if (max1 > 0) actions.add(Actions.get(max1Index));
    if (max2Index != max1Index
            && !Actions.get(max1Index).getTypeFrom().equals(Actions.get(max2Index).getTypeFrom()))
      actions.add(Actions.get(max2Index));
    return actions;
  }

  // get package
  private List<Action> findActionPac(List<Action> Actions) {
    int sizeActions = Actions.size();
    List<Action> prior4Actions = new ArrayList<>();
    for (Action action : Actions) {
      String typeFrom = action.getTypeFrom().toLowerCase();
      if (typeFrom.equals("Code")) continue;
      if (action.getLabelFrom().isEmpty()) continue;
      if (typeFrom.contains("package")) prior4Actions.add(action);
    }
    return prior4Actions;
  }

  // get class, method，interface
  private List<Action> findActionCMI(List<Action> Actions) {
    int sizeActions = Actions.size();
    List<Action> actions = new ArrayList<>();
    for (Action action : Actions) {
      String typeFrom = action.getTypeFrom().toLowerCase();
      if (typeFrom.equals("Code")) continue;
      if (action.getLabelFrom().isEmpty()) continue;
      if (typeFrom.contains("class")
              || typeFrom.contains("method")
              || typeFrom.contains("interface")) actions.add(action);
    }
    return actions;
  }

  // get class, method，interface
  private List<Action> findActionRand2(List<Action> Actions) {
    int sizeActions = Actions.size();
    List<Action> actions = new ArrayList<>();
    if (sizeActions > 2) {
      actions.add(Actions.get(0));
      actions.add(Actions.get(1));
    } else actions.add(Actions.get(0));
    return actions;
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

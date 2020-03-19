package com.github.smartcommit.commitmsg;

import com.github.smartcommit.client.SmartCommit;
import com.github.smartcommit.intent.model.MsgClass;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.GroupLabel;
import com.github.smartcommit.model.constant.Operation;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommitMsgGenerator {
  private static final Logger Logger = org.apache.log4j.Logger.getLogger(SmartCommit.class);
  private List<Action> astActions;
  private List<Action> refactorActions;
  private String commitMsg;

  public CommitMsgGenerator(List<Action> astActions, List<Action> refactorActions) {
    this.astActions = astActions;
    this.refactorActions = refactorActions;
  }
  /*
  public static void main(String[] args) {
    List<Action> astActions = new ArrayList<>();
    astActions.add(new Action(Operation.EXTRACT, "SingleVariableDeclaration", "VarA"));
    astActions.add(new Action(Operation.EXTRACT, "SimpleName", "VarB"));
    astActions.add(new Action(Operation.EXTRACT, "SimpleName", "VarC"));
    List<Action> refActions = new ArrayList<>();
    refActions.add(new Action(Operation.DEL, "Class", "E"));
    refActions.add(new Action(Operation.EXTRACT, "SingleVariableDeclaration", "G"));

    CommitMsgGenerator commitMsgGenerator = new CommitMsgGenerator(astActions, refActions);
    System.out.println(commitMsgGenerator.generateDetailedMsgs(MsgClass.ADD, GroupLabel.FEATURE));
  }
   */
  /**
   * Vectorize the group features
   *
   * @return
   */
  public List<Integer> generateGroupVector() {
    // Operation: 4(astAction) 13(refAction)
    // Type: 99(astAction) 18(refAction)
    int AstOperationSum = 4;
    int RefOperationSum = 13;
    int AstTypeSum = 99;
    int RefTypeSum = 18;
    int AstSum = AstOperationSum * AstTypeSum;
    int RefSum = RefOperationSum * RefTypeSum;
    int vectorSize = AstSum + RefSum;

    List<Integer> vectors = new ArrayList<>(Collections.nCopies(vectorSize, 0));
    int indexOperation, indexType, indexFinal;
    try {
      for (Action action : astActions) {
        indexType = 0;
        indexOperation = action.getOperationIndex();
        if (indexOperation == AstOperationSum + RefOperationSum + 1) continue;
        // when DiffHunk Graph building fails, get "code" and contribute nothing to vector
        else if (action.getTypeFrom().equals("Code")) continue;
        else {
          for (AstActionType astActionType : AstActionType.values()) {
            if (action.getTypeFrom().equals(astActionType.label)) {
              indexType = astActionType.index;
              break;
            }
          }
        }
        if (indexType != 0) {
          indexFinal = (indexOperation - 1) * AstTypeSum + indexType - 1;
          vectors.set(indexFinal, vectors.get(indexFinal) + 1);
        }
      }

      for (Action action : refactorActions) {
        indexType = 0;
        // Operation contains both ast and ref, while the index of Ref need to start from 0
        indexOperation = action.getOperationIndex() - AstOperationSum;
        if (indexOperation == AstOperationSum + RefOperationSum + 1) continue;
        else {
          for (RefActionType refActionType : RefActionType.values()) {
            if (action.getTypeFrom().equals(refActionType.label)) {
              indexType = refActionType.index;
              break;
            }
          }
        }
        if (indexType != 0) {
          indexFinal = AstSum + (indexOperation - 1) * RefTypeSum + indexType - 1;
          vectors.set(indexFinal, vectors.get(indexFinal) + 1);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      Logger.info("fail to generate Group Vector");
    }

    return vectors;
  }

  public List<Integer> generateSeparatedGroupVector() {
    // Operation: 4(astAction) 13(refAction)
    // Type: 99(astAction) 18(refAction)
    int AstOperationSum = 4;
    int AstTypeSum = 99;
    int AstSum = AstOperationSum * AstTypeSum;

    int RefOperationSum = 13;
    int RefTypeSum = 18;
    int RefSum = RefOperationSum * RefTypeSum;

    if (refactorActions.isEmpty()) {
      List<Integer> vectors = new ArrayList<>(Collections.nCopies(AstSum, 0));
      int indexOperation = 0, indexType = 0, indexFinal = 0;
      for (Action action : astActions) {
        indexOperation = action.getOperationIndex();
        // Operation "Unknown" contributes nothing to vector
        if (indexOperation == AstOperationSum + RefOperationSum + 1) continue;
        // When DiffHunk Graph building failed, TypeFrom "Code" contributes nothing to vector
        if (action.getTypeFrom().equals("Code")) continue;
        else {
          for (AstActionType astActionType : AstActionType.values()) {
            if (action.getTypeFrom().equals(astActionType.label)) {
              indexType = astActionType.index;
              break;
            }
          }
          indexFinal = (indexOperation - 1) * AstTypeSum + indexType - 1;
        }
        vectors.set(indexFinal, vectors.get(indexFinal) + 1);
      }
      return vectors;
    } else {
      List<Integer> vectors = new ArrayList<>(Collections.nCopies(RefSum, 0));
      int indexOperation = 0, indexType = 0, indexFinal = 0;
      for (Action action : refactorActions) {
        // Operation contains both ast and ref, while the index of Ref need to start from 0
        indexOperation = action.getOperationIndex() - AstOperationSum;
        // Operation "Unknown" contributes nothing to vector
        if (indexOperation == AstOperationSum + RefOperationSum + 1) continue;
        else {
          for (RefActionType refActionType : RefActionType.values()) {
            if (action.getTypeFrom().equals(refActionType.label)) {
              indexType = refActionType.index;
              break;
            }
          }
          indexFinal = (indexOperation - 1) * RefTypeSum + indexType - 1;
        }
        vectors.set(indexFinal, vectors.get(indexFinal) + 1);
      }
      return vectors;
    }
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
    if(intentLabel.label.equals("Non-Java"))
      commitMsg = key + " code or files";
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

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
   * Generate final detailed commit message
   *
   * @return
   */
  public List<String> generateDetailedMsgs(MsgClass msgClass, GroupLabel intentLabel) {

    // Count Frequency of typeFrom in Actions whose q equals key currently
    String key = msgClass.label;
    List<Action> actions = new ArrayList<>();
    for (Action action : astActions)
      if (action.getOperation().label.equals(key)
          || (action.getOperation().label.equals("Delete") && key.equals("Remove")))
        actions.add(action);
    for (Action action : refactorActions) {
      String op = action.getOperation().label;
      if (op.equals(key)
          || (key.equals("Refactor")
              && (op.equals("Convert")
                  || op.equals("Extract")
                  || op.equals("Introduce")
                  || op.equals("Merge")
                  || op.equals("Parameterize")
                  || op.equals("Pull up")
                  || op.equals("Pull down")
                  || op.equals("Split")))
          || (key.equals("Remove") && op.equals("Delete"))) actions.add(action);
    }

    // no actions matched
    if (actions.isEmpty()) {
      commitMsg = key + "  No matched action";
    } else {
      // extend commitMsg
      String tempExtend = null;
      // Special Cases: package, class, method，interface
      List<Integer> Indexes = get4IndexOfTypeFrom(actions);
      if (!Indexes.isEmpty()) {
        // 1 operation + 4 cases(type + label)
        // Two object
        if (Indexes.size() == 2) {
          Action action0 = actions.get(Indexes.get(0));
          Action action1 = actions.get(Indexes.get(1));
          // suppose both LabelFrom is not null
          if (action0.getTypeFrom().equals(action1.getTypeFrom())) {
            // the same type
            if (action0.getOperation().equals(Operation.ADD)) {
              // the same operation: ADD
              commitMsg =
                  "Add "
                      + action0.getTypeFrom()
                      + " "
                      + action0.getLabelFrom()
                      + " and "
                      + action1.getLabelFrom();
            } else {
              // not the same operation
              commitMsg =
                  "Modify "
                      + action0.getTypeFrom()
                      + " "
                      + action0.getLabelFrom()
                      + " and "
                      + action1.getLabelFrom();
            }
          } else {
            // not the same type
            if (action0.getOperation().equals(Operation.ADD)
                && action1.getOperation().equals(Operation.ADD)) {
              commitMsg =
                  "Add "
                      + action0.getTypeFrom()
                      + " "
                      + action0.getLabelFrom()
                      + " and "
                      + action1.getTypeFrom()
                      + " "
                      + action1.getLabelFrom();
            } else {
              commitMsg =
                  "Modify "
                      + action0.getTypeFrom()
                      + " "
                      + action0.getLabelFrom()
                      + " and "
                      + action1.getTypeFrom()
                      + " "
                      + action1.getLabelFrom();
            }
          }
        }
        // One object
        else {
          Action action0 = actions.get(Indexes.get(0));
          if (action0.getOperation().equals(Operation.ADD)) {
            commitMsg = "Add " + action0.getTypeFrom() + " " + action0.getLabelFrom();
          } else {
            commitMsg = "Modify " + action0.getTypeFrom() + " " + action0.getLabelFrom();
          }
        }
      } else {
        // 1 operation + 2 types(no label)
        Indexes = getMax2IndexTypeFrom(actions);
        if (Indexes.size() == 2) {
          Action action0 = actions.get(Indexes.get(0));
          Action action1 = actions.get(Indexes.get(1));
          commitMsg = key + " ";
          if (action0.getTypeFrom().equals(action1.getTypeFrom())) {
            commitMsg +=
                action0.getTypeFrom()
                    + " "
                    + action0.getLabelFrom()
                    + " and "
                    + action1.getLabelFrom();
          } else {
            commitMsg +=
                action0.getTypeFrom()
                    + " "
                    + action0.getLabelFrom()
                    + " and "
                    + action1.getTypeFrom()
                    + " "
                    + action1.getLabelFrom();
          }
        } else {
          // the final case, no matter what type/label it is
          Action action0 = actions.get(Indexes.get(0));
          if (action0.getLabelFrom().isEmpty()) commitMsg = key + " " + action0.getTypeFrom();
          else {
            commitMsg = key + " " + action0.getTypeFrom() + " " + action0.getLabelFrom();
          }
        }
      }
    }

    // read json to get templates
    JSONObject jsonObject = new JSONObject(getTemplate());
    JSONArray jsonArray = jsonObject.getJSONArray(key);

    // generate and return recommendedCommitMsgs
    List<String> recommendedCommitMsgs = new ArrayList<>();
    String iLabel = "";
    if (intentLabel.label.equals("Others")) {
      if (key.equals("Fix") || key.equals("Refactor")) iLabel = key.toUpperCase();
      else iLabel = "FUNCTIONCHANGE";
    } else iLabel = intentLabel.toString();
    recommendedCommitMsgs.add(iLabel + " : " + commitMsg);
    for (int i = 0; i < jsonArray.length(); i++)
      recommendedCommitMsgs.add(jsonArray.get(i).toString());
    return recommendedCommitMsgs;
  }

  // get max2Count of Frequency in Actions
  private List<Integer> getMax2IndexTypeFrom(List<Action> Actions) {
    int sizeActions = Actions.size();
    int count[] = new int[sizeActions];
    for (int i = 0; i < sizeActions; i++) {
      String typeFrom = Actions.get(i).getTypeFrom();
      if (typeFrom.equals("Code")) continue;
      for (int j = 0; j < i; j++) {
        if (Actions.get(j).getTypeFrom().equals(typeFrom)) {
          count[j]++;
          break;
        }
      }
      count[i] = 0;
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
    maxIndexes.add(max1Index);
    if (max2 != max1) maxIndexes.add(max2Index);
    return maxIndexes;
  }

  // get 4Indexes(appear for the first time) in Actions: package, class, method，interface
  private List<Integer> get4IndexOfTypeFrom(List<Action> Actions) {
    int sizeActions = Actions.size();
    List<Integer> Indexes = new ArrayList<>();
    int sizeIndexes = 0;

    for (int i = 0; i < sizeActions; i++) {
      String typeFrom = Actions.get(i).getTypeFrom();
      if (Actions.get(i).getLabelFrom().isEmpty()) continue;
      if (Actions.get(i).getTypeFrom().equals("Code")) continue;
      if (typeFrom.equals("PackageDeclaration")) {
        Indexes.add(i);
        sizeIndexes++;
        if (sizeIndexes == 2) return Indexes;
      } else if (typeFrom.equals("ClassInstanceCreation")
          || typeFrom.equals("AnonymousClassDeclaration")
          || typeFrom.equals("Class")
          || typeFrom.equals("Subclass")
          || typeFrom.equals("Superclass")) {
        Indexes.add(i);
        sizeIndexes++;
        if (sizeIndexes == 2) return Indexes;
      } else if (typeFrom.equals("Interface")) {
        Indexes.add(i);
        sizeIndexes++;
        if (sizeIndexes == 2) return Indexes;
      } else if (typeFrom.equals("MethodDeclaration")
          || typeFrom.equals("MethodInvocation")
          || typeFrom.equals("SuperMethodInvocation")
          || typeFrom.equals("MethodRef")
          || typeFrom.equals("MethodRefParameter")
          || typeFrom.equals("ExpressionMethodReference")
          || typeFrom.equals("SuperMethodReference")
          || typeFrom.equals("TypeMethodReference")) {
        Indexes.add(i);
        sizeIndexes++;
        if (sizeIndexes == 2) return Indexes;
      }
    }
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

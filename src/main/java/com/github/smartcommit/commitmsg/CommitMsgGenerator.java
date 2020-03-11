package com.github.smartcommit.commitmsg;

import com.github.smartcommit.intent.model.MsgClass;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.GroupLabel;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommitMsgGenerator {
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
    int AstSum = AstOperationSum*AstTypeSum;
    int RefSum = RefOperationSum*RefTypeSum;
    int vectorSize = AstSum + RefSum;

    List<Integer> vectors = new ArrayList<>(Collections.nCopies(vectorSize,0));
    int indexOperation = 0, indexType = 0, indexFinal = 0;

    for(Action action : astActions){
      indexOperation = action.getOperationIndex();
      if(indexOperation == AstOperationSum + RefOperationSum + 1)
        continue;
      // when DiffHunk Graph building fails, get "code" and contribute nothing to vector
      else if(action.getTypeFrom().equals("Code"))
        continue;
      else {
        for(AstActionType astActionType: AstActionType.values()){
          if(action.getTypeFrom().equals(astActionType.label)){
            indexType = astActionType.index;
            break;
          }
        }
        indexFinal = (indexOperation-1)*AstTypeSum + indexType-1;
      }
      vectors.set(indexFinal, vectors.get(indexFinal)+1);
    }

    for(Action action : refactorActions){
      // Operation contains both ast and ref, while the index of Ref need to start from 0
      indexOperation = action.getOperationIndex() - AstOperationSum;
      if (indexOperation == AstOperationSum + RefOperationSum + 1)
        continue;
      else {
        for (RefActionType refActionType : RefActionType.values()) {
          if (action.getTypeFrom().equals(refActionType.label)) {
            indexType = refActionType.index;
            break;
          }
        }
        indexFinal = AstSum + (indexOperation - 1) * RefTypeSum + indexType - 1;
      }
      vectors.set(indexFinal, vectors.get(indexFinal)+1);
    }
    return vectors;
  }

  public List<Integer> generateSeparatedGroupVector() {
    // Operation: 4(astAction) 13(refAction)
    // Type: 99(astAction) 18(refAction)
    int AstOperationSum = 4;
    int AstTypeSum = 99;
    int AstSum = AstOperationSum*AstTypeSum;

    int RefOperationSum = 13;
    int RefTypeSum = 18;
    int RefSum = RefOperationSum*RefTypeSum;

    if(refactorActions.isEmpty()) {
      List<Integer> vectors = new ArrayList<>(Collections.nCopies(AstSum,0));
      int indexOperation = 0, indexType = 0, indexFinal = 0;
      for(Action action : astActions){
        indexOperation = action.getOperationIndex();
        // Operation "Unknown" contributes nothing to vector
        if(indexOperation == AstOperationSum + RefOperationSum + 1)
          continue;
        // When DiffHunk Graph building failed, TypeFrom "Code" contributes nothing to vector
        if(action.getTypeFrom().equals("Code"))
          continue;
        else {
          for(AstActionType astActionType: AstActionType.values()){
            if(action.getTypeFrom().equals(astActionType.label)){
              indexType = astActionType.index;
              break;
            }
          }
          indexFinal = (indexOperation-1)*AstTypeSum + indexType-1;
        }
        vectors.set(indexFinal, vectors.get(indexFinal)+1);
      }
      return vectors;
    } else {
      List<Integer> vectors = new ArrayList<>(Collections.nCopies(RefSum,0));
      int indexOperation = 0, indexType = 0, indexFinal = 0;
      for(Action action : refactorActions){
        // Operation contains both ast and ref, while the index of Ref need to start from 0
        indexOperation = action.getOperationIndex() - AstOperationSum;
        // Operation "Unknown" contributes nothing to vector
        if (indexOperation == AstOperationSum + RefOperationSum + 1)
          continue;
        else {
          for (RefActionType refActionType : RefActionType.values()) {
            if (action.getTypeFrom().equals(refActionType.label)) {
              indexType = refActionType.index;
              break;
            }
          }
          indexFinal = (indexOperation - 1)*RefTypeSum + indexType-1;
        }
        vectors.set(indexFinal, vectors.get(indexFinal)+1);
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
  public  List<String> generateDetailedMsgs(MsgClass msgClass, GroupLabel intentLabel) {

    // Count Frequency of typeFrom in Actions whose q equals key currently
    String key = msgClass.label;
    List<Action> actions = new ArrayList<>();
    for(Action action : astActions)
      if(action.getOperation().label.equals(key)) actions.add(action);
    for(Action action : refactorActions)
      if(action.getOperation().label.equals(key)) actions.add(action);

    // no actions matched
    if(actions.isEmpty()) {
      commitMsg = key;
    } else {
      // extend commitMsg
      String tempExtend;
      // make short circuit: package, class, method，interface
      List<Integer> Indexes = get4IndexOfTypeFrom(actions, key);
      if(!Indexes.isEmpty()) {
        // 1 operation + 4 cases(type + label)
        commitMsg = key;
        Action action = null;
        boolean theFirst = true;
        for(int i = 0; i < 4 && Indexes.get(i) != -1; i++) {
          action = actions.get(Indexes.get(i));
          if(!theFirst) commitMsg += ", and";
          tempExtend = extendCommitMsg("Special4Cases", action);
          if(!tempExtend.isEmpty()) commitMsg += tempExtend;
          theFirst = false;
        }
      } else {
        // 1 operation + 2 types(no label)
        Indexes = getMax3IndexTypeFrom(actions, key);
        Action action0 = actions.get(Indexes.get(0));
        commitMsg = key;
        tempExtend = extendCommitMsg("", action0);
        if(!tempExtend.isEmpty()) commitMsg += tempExtend;
        if(Indexes.get(1) != Indexes.get(0)) {
          Action action1 = actions.get(Indexes.get(1));
          if(!action1.getTypeFrom().equals(action0.getTypeFrom())) {
            commitMsg += ", and";
            tempExtend = extendCommitMsg("", action1);
            if(!tempExtend.isEmpty()) commitMsg += tempExtend;
          }
        }
      }
    }

    // read json to get templates
    File file=new File("./src/MsgTemplate.json");
    String content= null;
    try {
      content = FileUtils.readFileToString(file,"UTF-8");
    } catch (IOException e) {
      e.printStackTrace();
    }
    JSONObject jsonObject=new JSONObject(content);
    JSONArray jsonArray = jsonObject.getJSONArray(key);

    // generate and return recommendedCommitMsgs
    List<String> recommendedCommitMsgs = new ArrayList<>();
    String iLabel = "";
    if(intentLabel.label.equals("Others")) iLabel = key.toUpperCase();
    else iLabel = intentLabel.toString();
    recommendedCommitMsgs.add(iLabel+" : " + commitMsg);
    for(int i = 0; i < jsonArray.length(); i ++)
      recommendedCommitMsgs.add(jsonArray.get(i).toString());
    return recommendedCommitMsgs;
  }

  // get max3Count of Frequency in Actions whose Operation equals key
  private static List<Integer> getMax3IndexTypeFrom(List<Action> Actions, String key) {
    int sizeActions = Actions.size();
    int count[] = new int[sizeActions];
    for(int i = 0; i < sizeActions; i ++){
      String operation = Actions.get(i).getOperation().label;
      if(!(operation.equals(key) ||
        (key.equals("Refactor") &&
        (operation.equals("Convert") || operation.equals("Extract") || operation.equals("Introduce") ||
        operation.equals("Merge") || operation.equals("Parameterize") || operation.equals("Pull up") ||
        operation.equals("Pull down") || operation.equals("Split")))))
        continue;
      String typeFrom = Actions.get(i).getTypeFrom();
      int j;
      for(j = 0; j < i; j ++){
        if(Actions.get(j).getTypeFrom().equals(typeFrom)) {
          count[j] = count[j]+1;
          break;
        }
      }
      if(j == i) count[j] = 1;
    }
    // lazy to sort, simply cycle once
    int max1 = 0, max2 = 0, max3 = 0;
    int max1Index = 0, max2Index = 0, max3Index = 0;
    for(int i = 0; i < sizeActions; i ++){
      if(count[i] > max1) {
        max3 = max2; max2 = max1;
        max1 = count[i];
        max3Index = max2Index; max2Index = max1Index;
        max1Index = i;
      } else if (count[i] > max2) {
        max3 = max2;
        max2 = count[i];
        max3Index = max2Index;
        max2Index = i;
      } else if(count[i] > max3) {
        max3 = count[i];
        max3Index = i;
      }
    }
    List<Integer> max3Indexes = new ArrayList<>(3);
    max3Indexes.add(max1Index);
    max3Indexes.add(max2Index);
    max3Indexes.add(max3Index);
    return max3Indexes;
  }

  // get max4Count of Frequency in Actions: package, class, method，interface
  private static List<Integer> get4IndexOfTypeFrom(List<Action> Actions, String key) {
    int sizeActions = Actions.size();
    int index1 = -1, index2 = -1, index3 = -1, index4 = -1;
    for(int i = 0; i < sizeActions; i ++){
      if(!Actions.get(i).getOperation().label.equals(key)) continue;
      String typeFrom = Actions.get(i).getTypeFrom();
      if(typeFrom.equals("Package")) index1 = i;
      if(typeFrom.equals("Class")) index2 = i;
      if(typeFrom.equals("Method")) index3 = i;
      if(typeFrom.equals("Interface")) index4 = i;
    }
    List<Integer> indexes = new ArrayList<>();
    if(index1+index2+index3+index4 > -4) {
      indexes.add(index1);
      indexes.add(index2);
      indexes.add(index3);
      indexes.add(index4);
    }
    return indexes;
  }

  // extendCommitMsg by adding action type+label, from+to
  private static String extendCommitMsg(String key, Action action) {
    String tempString = "";
    // Only TypeFrom is a must
    if (key.equals("Special4Cases")) {
      tempString += " " + action.getTypeFrom();
      if (!action.getLabelTo().isEmpty()) tempString += " " + action.getLabelFrom();
      if (!action.getTypeTo().isEmpty() && !action.getLabelTo().isEmpty())
        tempString += " to " + action.getTypeTo() + " " + action.getLabelTo();
    } else {
      tempString += " " + action.getTypeFrom();
      if (!action.getLabelTo().isEmpty())
        tempString += " to " + action.getTypeTo();
    }
    return tempString;
  }
}
package com.github.smartcommit.commitmsg;

import com.github.smartcommit.intent.model.ASTOperation;
import com.github.smartcommit.intent.model.Intent;
import com.github.smartcommit.intent.model.MsgClass;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.GroupLabel;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import com.github.smartcommit.model.constant.Operation;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommitMsgGenerator {
  private List<Action> astActions;
  private List<Action> refactorActions;
  private String templateMsg;
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
      else if(action.getTypeFrom().equals("code"))
        continue;
      else {
        for(AstActionType astActionType: AstActionType.values()){
          if(action.getTypeFrom().equals(astActionType.label)){
            indexType = astActionType.index;
            break;
          }
        }
        indexFinal = (indexOperation-1)*AstOperationSum + indexType-1;
      }
      vectors.set(indexFinal, vectors.get(indexFinal)+1);
    }

    for(Action action : refactorActions){
      indexOperation = action.getOperationIndex();
      if (indexOperation == AstOperationSum + RefOperationSum + 1)
        continue;
      else {
        for (RefActionType refActionType : RefActionType.values()) {
          if (action.getTypeFrom().equals(refActionType.label)) {
            indexType = refActionType.index;
            break;
          }
        }
        indexFinal = AstSum + (indexOperation - 1) * RefOperationSum + indexType - 1;
      }
      vectors.set(indexFinal, vectors.get(indexFinal)+1);
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
   * Generate final detailed commit message
   *
   * @return
   */
  public List<String> generateDetailedMsgs(MsgClass msgClass, GroupLabel intentLabel) {

    // Count Frequency of typeFrom in Actions whose Operation equals key
    String key = msgClass.label;
    List<Action> actions = new ArrayList<>();
    actions.addAll(astActions);
    actions.addAll(refactorActions);
    List<Integer> max3Indexes = getMaxIndexTypeFrom(actions, key);
    Action action1 = actions.get(max3Indexes.get(0));
    commitMsg = key +" "+ action1.getTypeFrom() +" "+ action1.getLabelFrom();
    if(!action1.getLabelTo().isEmpty())
      commitMsg += " to "+ action1.getTypeTo() +" "+ action1.getLabelTo();
    // Change takes top2, while others takes top3
    if(max3Indexes.get(1) != max3Indexes.get(0)) {
      Action action2 = actions.get(max3Indexes.get(1));
      commitMsg += ", and "+ action2.getTypeFrom() +" "+ action2.getLabelFrom();
      if(!action1.getLabelTo().isEmpty())
        commitMsg += " to "+ action2.getTypeTo() +" "+ action2.getLabelTo();
    }
    if(max3Indexes.get(2) != max3Indexes.get(1) && !key.equals("Change")) {
      Action action3 = actions.get(max3Indexes.get(2));
      commitMsg += ", and "+ action3.getTypeFrom() +" "+ action3.getLabelFrom();
      if(!action1.getLabelTo().isEmpty())
        commitMsg += " to "+ action3.getTypeTo() +" "+ action3.getLabelTo();
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
    recommendedCommitMsgs.add(intentLabel+" : " + commitMsg);
    for(int i = 0; i < jsonArray.length(); i ++)
      recommendedCommitMsgs.add(jsonArray.get(i).toString());
    return recommendedCommitMsgs;
  }

  // get maxCount of Frequency in Actions using C-style :(
  private List<Integer> getMaxIndexTypeFrom(List<Action> Actions, String key) {
    int sizeActions = Actions.size();
    int count[] = new int[sizeActions];
    for(int i = 0; i < sizeActions; i ++){
      if(Actions.get(i).getOperation().label.equals(key)) continue;
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
    max3Indexes.add(max1);
    max3Indexes.add(max2);
    max3Indexes.add(max3);
    return max3Indexes;
  }
}

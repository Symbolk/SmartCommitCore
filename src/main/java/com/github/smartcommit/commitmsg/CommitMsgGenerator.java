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

    File file=new File("./src/MsgTemplate.json");
    String key = msgClass.label;
    String content= null;
    try {
      content = FileUtils.readFileToString(file,"UTF-8");
    } catch (IOException e) {
      e.printStackTrace();
    }

    JSONObject jsonObject=new JSONObject(content);
    JSONArray jsonArray = jsonObject.getJSONArray(key);

    // Simply count Frequency of typeFrom in astActions
    int maxIndex = getMaxIndexTypeFrom(astActions);
    String typeFrom = astActions.get(maxIndex).getTypeFrom();
    String labelFrom = astActions.get(maxIndex).getLabelFrom();
    String typeTo = astActions.get(maxIndex).getTypeTo();
    String labelTo = astActions.get(maxIndex).getLabelTo();

    List<String> recommendedCommitMsgs = new ArrayList<>();
    // Directly generate recommendedCommitMsg for chosen MsgClass
    if(key.equals("Add") || key.equals("Create") || key.equals("implement") || key.equals("update") || key.equals("upgrade")
            || key.equals("replace") || key.equals("change") || key.equals("rename")){
      recommendedCommitMsgs.add("Intent: "+msgClass.label+" "+typeFrom+" to "+labelTo+" in "+labelFrom);
      return recommendedCommitMsgs;
    }
    // fill the first blank using substring
    // the first as the suggested
    templateMsg = jsonArray.get(0).toString();
    int chosenIndex = 0;
    // the same type as the fittest
    for(int i = 0; i < jsonArray.length(); i++){
      if(jsonArray.get(i).toString().contains(typeFrom)){
        templateMsg = jsonArray.get(i).toString();
        chosenIndex = i;
      }
    }
    int start = templateMsg.indexOf("(.+)");
    commitMsg = templateMsg.substring(0, start)+labelFrom+templateMsg.substring(start+4);

    // generate and return recommendedCommitMsgs
    recommendedCommitMsgs.add(intentLabel+" : " + commitMsg);
    for(int i = 0; i < jsonArray.length(); i ++)
      recommendedCommitMsgs.add(jsonArray.get(i).toString());
    return recommendedCommitMsgs;
  }

  // get maxCount of Frequency in Actions using C-style :(
  private int getMaxIndexTypeFrom(List<Action> Actions) {
    int sizeActions = Actions.size();
    int count[] = new int[sizeActions];
    String types[] = new String[sizeActions];
    for(int i = 0; i < sizeActions; i ++){
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
    int max = 0, maxIndex = 0;
    for(int i = 0; i < sizeActions; i ++){
      if(count[i] == 0)break;
      if(count[i] > max) {
        max = count[i];
        maxIndex = i;
      }
    }
    return maxIndex;
  }
}

package com.github.smartcommit.commitmsg;

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
    List<Integer> vectors = new ArrayList<>(Collections.nCopies(50,0));
    int index;
    for(Action action : astActions){
      index = action.getOperationIndex();
      vectors.set(index, vectors.get(index)+1);
    }
    for(Action action : refactorActions){
      index = action.getOperationIndex();
      vectors.set(index, vectors.get(index)+1);
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

    List<String> recommendedCommitMsgs = new ArrayList<>();
    // the most recommended at the first place
    recommendedCommitMsgs.add(commitMsg);
    // Leftover as the follows
    for(int i = 0; i < jsonArray.length(); i ++)
      if(i != chosenIndex) recommendedCommitMsgs.add(jsonArray.get(i).toString());
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

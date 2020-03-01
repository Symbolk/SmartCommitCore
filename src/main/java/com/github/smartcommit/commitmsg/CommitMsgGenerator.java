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

    // Simply count Frequency of typeFrom in astActions to get the first keyword in the first template
    // get maxCount using C-style :(
    int sizeAstActions = astActions.size();
    int count[] = new int[sizeAstActions];
    String types[] = new String[sizeAstActions];
    for(int i = 0; i < sizeAstActions; i ++){
      String typeFrom = astActions.get(i).getTypeFrom();
      int j;
      for(j = 0; j < i; j ++){
        if(astActions.get(j).getTypeFrom().equals(typeFrom)) {
          count[j] = count[j]+1;
          break;
        }
      }
      if(j == i) count[j] = 1;
    }
    int max = 0, maxIndex = 0;
    for(int i = 0; i < sizeAstActions; i ++){
      if(count[i] == 0)break;
      if(count[i] > max) {
        max = count[i];
        maxIndex = i;
      }
    }
    String type = astActions.get(maxIndex).getTypeFrom();

    // fill the first blank using substring,
    templateMsg = jsonArray.get(0).toString();
    int start = templateMsg.indexOf("(.+)");
    commitMsg = templateMsg.substring(0, start)+type+templateMsg.substring(start+4);

    List<String> recommendedCommitMsgs = new ArrayList<>();
    recommendedCommitMsgs.add("\nMsgClass: " + msgClass.label);
    recommendedCommitMsgs.add("\nGroupLabel: "+intentLabel.label);
    recommendedCommitMsgs.add("\ntemplateMsg: " + templateMsg);
    recommendedCommitMsgs.add("\ncommitMsg: " + commitMsg);
    recommendedCommitMsgs.add(("\nAll possible templates: " + jsonArray.toString()));
    return recommendedCommitMsgs;
  }
}

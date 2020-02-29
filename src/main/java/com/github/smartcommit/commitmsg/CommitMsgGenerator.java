package com.github.smartcommit.commitmsg;

import com.github.smartcommit.intent.model.MsgClass;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.GroupLabel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

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
  public static void main(String[] args) {
    System.out.println(generateDetailedMsgs(MsgClass.ADD, GroupLabel.FEATURE));
  }
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
  public static List<String> generateDetailedMsgs(MsgClass msgClass, GroupLabel intentLabel) {

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

    List<String> recommendedCommitMsgs = new ArrayList<>();
    recommendedCommitMsgs.add(intentLabel.label);
    for(int i = 0; i < jsonArray.length(); i ++){
      recommendedCommitMsgs.add((String) jsonArray.get(0));
    }
    return recommendedCommitMsgs;
  }
}

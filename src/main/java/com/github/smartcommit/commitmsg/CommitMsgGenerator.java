package com.github.smartcommit.commitmsg;

import com.github.smartcommit.intent.model.MsgClass;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.GroupLabel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    List<String> recommendedCommitMsgs = new ArrayList<>();
    recommendedCommitMsgs.add(intentLabel.label);
    return recommendedCommitMsgs;
  }
}

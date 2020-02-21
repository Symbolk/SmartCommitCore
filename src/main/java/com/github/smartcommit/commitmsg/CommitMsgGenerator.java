package com.github.smartcommit.commitmsg;

import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.GroupLabel;

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
     * @return
     */
    public List<Integer> generateGroupVector(){
        return null;
    }

    /**
     * Invoke the AI model to generate template commit msg
     * @param vectors
     * @param label
     * @return
     */
    public String invokeAIModel(List<Integer> vectors, GroupLabel label){
        return null;
    }

    /**
     * Generate final detailed commit message
     * @param template
     * @return
     */
    public String generateDetailedMsg(String template){
        return null;
    }
}

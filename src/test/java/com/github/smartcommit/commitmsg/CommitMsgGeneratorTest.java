package com.github.smartcommit.commitmsg;

import com.github.smartcommit.intent.model.MsgClass;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.GroupLabel;
import com.github.smartcommit.model.constant.Operation;

import java.util.ArrayList;
import java.util.List;

class CommitMsgGeneratorTest {
   public static void main(String[] args) {
     List<Action> astActions = new ArrayList<>();
     astActions.add(new Action(Operation.ADD, "MethodDeclaration", "VarA"));
     astActions.add(new Action(Operation.ADD, "SimpleName", "VarB"));
     astActions.add(new Action(Operation.ADD, "SimpleName", "VarC"));
     astActions.add(new Action(Operation.ADD, "MethodDeclaration", "VarA"));
     astActions.add(new Action(Operation.ADD, "MethodDeclaration", "VarB"));
     astActions.add(new Action(Operation.ADD, "MethodDeclaration", "VarC"));
     List<Action> refActions = new ArrayList<>();
     refActions.add(new Action(Operation.DEL, "Class", "E"));
     refActions.add(new Action(Operation.ADD, "MethodDeclaration", "G"));
     refActions.add(new Action(Operation.ADD, "MethodDeclaration", "G"));
     refActions.add(new Action(Operation.EXTRACT, "MethodDeclaration", "G"));
     CommitMsgGenerator commitMsgGenerator = new CommitMsgGenerator(astActions, refActions);
     System.out.println(commitMsgGenerator.generateDetailedMsgs(MsgClass.ADD, GroupLabel.FEATURE));
   }

}
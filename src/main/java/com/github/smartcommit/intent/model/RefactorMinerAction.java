package com.github.smartcommit.intent.model;

import org.refactoringminer.api.RefactoringType;

public class RefactorMinerAction {
    private RefactoringType refactoringType;
    private String name;

    public RefactorMinerAction(RefactoringType refactoringType, String name) {
        this.refactoringType = refactoringType;
        this.name = name;
    }

    public String getOperation() {
        switch(name){
            case "Extract Method" :
                return "Extract";
            case "Inline Method" :
                return "Inline";
            case "Rename Method" :
                return "Rename";
            case "Move Method":
                return "Move";
            case "Move Attribute" :
                return "Move";
            case "Pull Up Method" :
                return "Pull Up";
            case "Pull Up Attribute" :
                return "Pull Up";
            case "Push Down Method" :
                return "Pull Down";
            case "Push Down Attribute" :
                return "Pull Down";
            case "Extract Superclass" :
                return "Extract";
            case "Extract Interface" :
                return "Extract";
            case "Move Class" :
                return "Move";
            case "Rename Class" :
                return "Rename";
            case "Extract and Move Method" :
                return "Extract and Move";
            case "Change Package (Move, Rename, Split, Merge)" :
                return "Change";
            case "Move and Rename Class" :
                return "Move and Rename";
            case "Extract Class" :
                return "Extract";
            case "Extract Subclass" :
                return "Extract";
            case "Extract Variable" :
                return "Extract";
            case "Inline Variable" :
                return "Inline";
            case "Parameterize Variable" :
                return "Parameterize";
            case "Rename Variable" :
                return "Rename";
            case "Rename Parameter" :
                return "Rename";
            case "Rename Attribute" :
                return "Rename";
            case "Move and Rename Attribute" :
                return "Move and Rename";
            case "Replace Variable with Attribute" :
                return "Replace with Attribute";
            case "Replace Attribute (with Attribute)" :
                return "Replace (with Attribute)";
            case "Merge Variable" :
                return "Merge";
            case "Merge Parameter" :
                return "Merge";
            case "Merge Attribute" :
                return "Merge";
            case "Split Variable" :
                return "Split";
            case "Split Parameter" :
                return "Split";
            case "Split Attribute" :
                return "Split";
            case "Change Variable Type" :
                return "Change";
            case "Change Parameter Type" :
                return "Change";
            case "Change Return Type" :
                return "Change";
            case "Change Attribute Type" :
                return "Change";
            case "Extract Attribute" :
                return "Extract";
            case "Move and Rename Method" :
                return "Move and Rename";
            case "Move and Inline Method" :
                return "Move and Inline";
            default :
                return "";
        }
    }

    public String getRefactoringType(){
        switch(name){
            case "Extract Method" :
                return "Method";
            case "Inline Method" :
                return "Method";
            case "Rename Method" :
                return "Method";
            case "Move Method":
                return "Method";
            case "Move Attribute" :
                return "Attribute";
            case "Pull Up Method" :
                return "Method";
            case "Pull Up Attribute" :
                return "Attribute";
            case "Push Down Method" :
                return "Method";
            case "Push Down Attribute" :
                return "Attribute";
            case "Extract Superclass" :
                return "Superclass";
            case "Extract Interface" :
                return "Interface";
            case "Move Class" :
                return "Class";
            case "Rename Class" :
                return "Class";
            case "Extract and Move Method" :
                return "Method";
            case "Change Package (Move, Rename, Split, Merge)" :
                return "Package (Move, Rename, Split, Merge)";
            case "Move and Rename Class" :
                return "Class";
            case "Extract Class" :
                return "Class";
            case "Extract Subclass" :
                return "Subclass";
            case "Extract Variable" :
                return "Variable";
            case "Inline Variable" :
                return "Variable";
            case "Parameterize Variable" :
                return "Variable";
            case "Rename Variable" :
                return "Variable";
            case "Rename Parameter" :
                return "Parameter";
            case "Rename Attribute" :
                return "Attribute";
            case "Move and Rename Attribute" :
                return "Attribute";
            case "Replace Variable with Attribute" :
                return "Variable";
            case "Replace Attribute (with Attribute)" :
                return "Attribute";
            case "Merge Variable" :
                return "Variable";
            case "Merge Parameter" :
                return "Parameter";
            case "Merge Attribute" :
                return "Attribute";
            case "Split Variable" :
                return "Variable";
            case "Split Parameter" :
                return "Parameter";
            case "Split Attribute" :
                return "Attribute";
            case "Change Variable Type" :
                return "Variable Type";
            case "Change Parameter Type" :
                return "Parameter Type";
            case "Change Return Type" :
                return "Return Type";
            case "Change Attribute Type" :
                return "Attribute Type";
            case "Extract Attribute" :
                return "Attribute";
            case "Move and Rename Method" :
                return "Method";
            case "Move and Inline Method" :
                return "Method";
            default :
                return "";
        }
    }

    public String getName() {
        return name;
    }
}

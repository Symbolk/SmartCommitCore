package com.github.smartcommit.intent.model;

import org.refactoringminer.api.RefactoringType;

public class RefactorCodeChange {
    private RefactoringType refactoringType;
    private String operation;

    public RefactorCodeChange(RefactoringType refactoringType, String operation) {
        this.refactoringType = refactoringType;
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public RefactoringType getRefactoringType(){
        return refactoringType;
    }

}

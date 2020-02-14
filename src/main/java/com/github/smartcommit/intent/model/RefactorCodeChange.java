package com.github.smartcommit.intent.model;

import org.refactoringminer.api.RefactoringType;

public class RefactorCodeChange {
    private RefactoringType refactoringType;
    private String name;

    public RefactorCodeChange(RefactoringType refactoringType, String name) {
        this.refactoringType = refactoringType;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public RefactoringType getRefactoringType(){
        return refactoringType;
    }

}

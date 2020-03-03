package com.github.smartcommit.commitmsg;

public enum RefActionType {
    ANONYMOUS_CLASS("Anonymous Class"),
    ATTRIBUTE("Attribute"),
    CLASS("Class"),
    FIELD("Field"),
    FIELD_TYPE("Field Type"),
    INTERFACE("Interface"),
    METHOD("Method"),
    METHOD_SIGNATURE("Method Signature"),
    PACKAGE("Package"),
    PARAMETER_TYPE("Parameter Type"),
    POLYMORPHISM("Polymorphism"),
    RETURN_TYPE("Return Type"),
    SOURCE_FOLDER("Source Folder"),
    SUBCLASS("Subclass"),
    SUPERCLASS("Superclass"),
    VARIABLE("Variable"),
    VARIABLE_TYPE("Variable Type");

    public String label;

    RefActionType(String label) {
        this.label = label;
    }
}

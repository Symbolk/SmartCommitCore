package com.github.smartcommit.commitmsg;

public enum RefActionType {
    ANONYMOUS_CLASS("Anonymous Class", 1),
    ATTRIBUTE("Attribute", 2),
    CLASS("Class", 3),
    FIELD("Field", 4),
    FIELD_TYPE("Field Type", 5),
    INTERFACE("Interface", 6),
    METHOD("Method", 7),
    METHOD_SIGNATURE("Method Signature", 8),
    PACKAGE("Package", 9),
    PARAMETER_TYPE("Parameter Type", 10),
    POLYMORPHISM("Polymorphism", 11),
    RETURN_TYPE("Return Type", 12),
    SOURCE_FOLDER("Source Folder", 13),
    SUBCLASS("Subclass", 14),
    SUPERCLASS("Superclass", 15),
    TYPE("Type", 16),
    VARIABLE("Variable", 17),
    VARIABLE_TYPE("Variable Type", 18);

    public String label;
    public int index;

    RefActionType(String label, int index) {
        this.label = label;
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}

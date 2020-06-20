package com.github.smartcommit.commitmsg;

public enum RefActionType {
    ANONYMOUS_CLASS("Anonymous Class", 1),
    ATTRIBUTE("Attribute", 2),
    ATTRIBUTE_TYPE("Attribute Type", 3),
    ATTRIBUTE_ANNOTATION("Attribute Annotation", 4),
    CLASS("Class", 5),
    CLASS_ANNOTATION("Class Annotation", 6),
    FIELD("Field", 7),
    FIELD_TYPE("Field Type", 8),
    INTERFACE("Interface", 9),
    METHOD("Method", 10),
    METHOD_ANNOTATION("Method Annotation", 11),
    METHOD_SIGNATURE("Method Signature", 12),
    PACKAGE("Package", 13),
    PARAMETER("Parameter", 14),
    PARAMETER_TYPE("Parameter Type", 15),
    POLYMORPHISM("Polymorphism", 16),
    RETURN_TYPE("Return Type", 17),
    SOURCE_FOLDER("Source Folder", 18),
    SUBCLASS("Subclass", 19),
    SUPERCLASS("Superclass", 20),
    TYPE("Type", 21),
    VARIABLE("Variable", 22),
    VARIABLE_TYPE("Variable Type", 23);

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

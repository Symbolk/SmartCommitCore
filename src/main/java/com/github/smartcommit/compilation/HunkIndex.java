package com.github.smartcommit.compilation;

import org.eclipse.jdt.core.dom.ASTNode;

import java.util.List;

public class HunkIndex {

    private String relativeFilePath;
    private Integer startLine;
    private Integer endLine;
    private Integer fileIndex;
    private Integer index;
    private String diffHunkID;
    private String uuid;
    private String packagePath;
    private List<String> rawDiffs;
    private List<ASTNode> baseAstNodes;
    private List<ASTNode> currentAstNodes;

    public HunkIndex(String relativeFilePath, Integer startLine, Integer endLine) {
        this.relativeFilePath = relativeFilePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.fileIndex = null;
        this.index = null;
        this.uuid = null;
        this.rawDiffs = null;
        this.baseAstNodes = null;
        this.currentAstNodes = null;
    }

    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    public void setRelativeFilePath(String relativeFilePath) {
        this.relativeFilePath = relativeFilePath;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public void setStartLine(Integer startLine) {
        this.startLine = startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public void setEndLine(Integer endLine) {
        this.endLine = endLine;
    }

    public Integer getFileIndex() {
        return fileIndex;
    }

    public void setFileIndex(Integer fileIndex) {
        this.fileIndex = fileIndex;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getPackagePath() {
        return packagePath;
    }

    public void setPackagePath(String packagePath) {
        this.packagePath = packagePath;
    }

    public List<String> getRawDiffs() {
        return rawDiffs;
    }

    public void setRawDiffs(List<String> rawDiffs) {
        this.rawDiffs = rawDiffs;
    }

    public String getDiffHunkID() {
        return diffHunkID;
    }

    public void setDiffHunkID(String diffHunkID) {
        this.diffHunkID = diffHunkID;
    }

    public List<ASTNode> getBaseAstNodes() {
        return baseAstNodes;
    }

    public void setBaseAstNodes(List<ASTNode> baseAstNodes) {
        this.baseAstNodes = baseAstNodes;
    }

    public List<ASTNode> getCurrentAstNodes() {
        return currentAstNodes;
    }

    public void setCurrentAstNodes(List<ASTNode> currentAstNodes) {
        this.currentAstNodes = currentAstNodes;
    }


    @Override
    public String toString() {
        return "HunkIndex{" +
                "relativeFilePath='" + relativeFilePath + '\'' +
                ", startLine=" + startLine +
                ", endLine=" + endLine +
                ", fileIndex=" + fileIndex +
                ", index=" + index +
                ", diffHunkID='" + diffHunkID + '\'' +
                ", uuid='" + uuid + '\'' +
                ", rawDiffs=" + rawDiffs +
                '}';
    }
}

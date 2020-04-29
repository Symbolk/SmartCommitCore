package com.github.smartcommit.compilation;

public class HunkIndex {

    private String relativeFilePath;
    private Integer startLine;
    private Integer endLine;
    private Integer fileIndex;
    private Integer index;


    private String uuid;

    public HunkIndex(String relativeFilePath, Integer startLine, Integer endLine) {
        this.relativeFilePath = relativeFilePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.fileIndex = null;
        this.index = null;
        this.uuid = null;
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

    @Override
    public String toString() {
        return "HunkIndex{" +
                "relativeFilePath='" + relativeFilePath + '\'' +
                ", startLine=" + startLine +
                ", endLine=" + endLine +
                ", fileIndex=" + fileIndex +
                ", index=" + index +
                '}';
    }


}

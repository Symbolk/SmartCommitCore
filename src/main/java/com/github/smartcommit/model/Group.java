package com.github.smartcommit.model;

import java.util.List;

/** The output result, one group for one commit */
public class Group {
  private String repoID;
  private String repoName;
  private String groupID;
  private String commitID;

  private List<String> diffHunks;
  private String commitMsg;
  private String templateCommitMsg;
  private String intentLabel;
}

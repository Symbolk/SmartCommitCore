package com.github.smartcommit.intent;

import com.github.smartcommit.intent.model.Intent;

class CommitInfoHandlerTest {


  public static void main(String[] args) {
    String string ="    Merge pull request #3011 from testfixer/com.alibaba.json.bvt.jsonp.JSONPParseTest3\n"
                    + "    Enable SerializeFeature.MapSortField for deterministic order";
    System.out.println(getIntentFromMsg(string.toLowerCase()));

  }
  // generate Intent from Message
  private static Intent getIntentFromMsg(String commitMsg) {
    String[] parts = commitMsg.toLowerCase().split("\\n");
    for (int i = 0; i < parts.length; i++) {
      for (Intent intent : Intent.values()) {
        if (parts[i].contains(intent.label)) {
          return intent;
        }
      }
    }
    return Intent.CHORE;
  }

  }
package com.github.smartcommit.intent;

import com.github.smartcommit.intent.CommitInfoHandler;
import com.github.smartcommit.util.Utils;

import java.io.*;
import java.io.IOException;

public class MultiCommitInfoHandler {
    public static void main(String[] args) throws IOException {
        args = new String[]{"/Users/Chuncen/IdeaProjects/SmartCommitCore/src/main/java/com/github/smartcommit/intent/RepoList.txt"};
        String fileName = args[0];

        // Buffered Read
        FileReader fileReader = new FileReader(fileName);
        BufferedReader bufferedReader = new BufferedReader(fileReader);

        // Line by line
        String line = bufferedReader.readLine();
        String collectionName = line;
        line = bufferedReader.readLine();
        String tempDir = line;

        while (line != null){
            try {
                line = bufferedReader.readLine();
                // get RepoName and tempLocation

                File folder = new File(line);
                String[] args0;
                // pwd
                if (folder.exists()) { // found it in given local pathway
                    args0 = new String[]{line, collectionName};
                }
                // url
                else {
                    // the length of ".git" is 4
                    int index = line.lastIndexOf(File.separator);
                    String repoName = line.substring(index+1, line.length()-4);
                    String tempLocation = tempDir+File.separator+repoName;
                    folder = new File(tempLocation);

                    if(folder.exists()) {           // found it in local temp dir
                        args0 = new String[]{tempLocation, collectionName};
                    }
                    else {                            // clone from git
                    String log = Utils.runSystemCommand(tempDir, "git", "clone", line);
                    System.out.print(log);
                    args0 = new String[]{tempLocation, collectionName};
                    }
                }
                System.out.println("working in " + args0[0]);
                //CommitInfoHandler.main(args0);

            } catch (Exception e) {
                //e.printStackTrace();
            }
        }

        bufferedReader.close();
        fileReader.close();
    }
}

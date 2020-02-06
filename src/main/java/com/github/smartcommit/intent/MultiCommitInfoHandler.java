package com.github.smartcommit.intent;

import com.github.smartcommit.intent.CommitInfoHandler;
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
        while (line != null){
            try {
                line = bufferedReader.readLine();
                System.out.println(line);
                String[] args0 = new String[]{line, collectionName};
                CommitInfoHandler.main(args0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        bufferedReader.close();
        fileReader.close();
    }
}

package com.github.smartcommit.intent;

import com.github.smartcommit.intent.CommitInfoHandler;
import com.github.smartcommit.util.Utils;

import java.io.*;
import java.io.IOException;
class RunnableDemo implements Runnable {
    private Thread t;
    private String threadName;
    private String line;
    private String collectionName;
    private String tempDir;

    RunnableDemo( String name) {
        threadName = name;
        System.out.println("Creating " +  threadName );
    }

    public void run() {
        System.out.println("Running " +  threadName );

        try {
            // get RepoName and tempLocation to construct String[] args0
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
        System.out.println("Thread " +  threadName + " exiting.");
    }

    public void start (String tempDir, String collectionName, String line) {
        this.line = line;
        this.tempDir = tempDir;
        this.collectionName = collectionName;
        System.out.println("Starting " +  threadName );
        if (t == null) {
            t = new Thread (this, threadName);
            t.start ();
        }
    }
}

public class MultiThreadCommitInfoHandler {
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

        Integer i = 0;
        line = bufferedReader.readLine();
        while (line != null){
            RunnableDemo rd = new RunnableDemo( "Thread-"+(i++));
            rd.start(tempDir, collectionName, line);
            line = bufferedReader.readLine();
            // wait for a while before Reading next line and Creating next thread
            try {
                Thread.sleep(5000);
            }catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        bufferedReader.close();
        fileReader.close();
    }
}

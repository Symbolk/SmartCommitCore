package com.github.smartcommit.intent;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;

import java.io.File;
import java.util.ArrayList;

// Get commit message from repo
// git log or read from local???
public class GetLabel {
    // ref DataCollector
    public static void main(String[] args) {
        String REPO_NAME = "guava";
        String REPO_DIR = "/Users/Chuncen/IdeaProjects/" + REPO_NAME;
        String DATA_DIR = "/Users/Chuncen/IdeaProjects/commit_data";
        String commitID = "dcf63a6c97dfde";

        GitService gitService = new GitServiceCGit();

        ArrayList<DiffFile> filePairs = gitService.getChangedFilesAtCommit(REPO_DIR, commitID);
        // write old/new content to disk
        for (DiffFile filePair : filePairs) {
            // currently only collect MODIFIED Java files
            if (filePair.getBaseRelativePath().endsWith(".java")
                    && filePair.getStatus().equals(FileStatus.MODIFIED)) {
                String dir =
                        DATA_DIR + File.separator + REPO_NAME + File.separator + commitID + File.separator;
                String aPath = dir + "a" + File.separator + filePair.getBaseRelativePath();
                String bPath = dir + "b" + File.separator + filePair.getCurrentRelativePath();
                boolean aOk = Utils.writeContentToPath(aPath, filePair.getBaseContent());
                boolean bOk = Utils.writeContentToPath(bPath, filePair.getCurrentContent());
                if (!(aOk && bOk)) {
                    System.out.println("Error with: " + filePair.getBaseRelativePath());
                } else {
                    System.out.println(aPath);
                    System.out.println(bPath);
                }
            }
        }

        // read from json https://www.cnblogs.com/dwb91/p/6726823.html
        /*
        String path = "";
        public ResponseBean getAreas() {
            String path = getClass().getClassLoader().getResource("area.json").toString();
            path = path.replace("\\", "/");
            if (path.contains(":")) {
                //path = path.substring(6);// 1
　　　　　　　　 path = path.replace("file:/","");
            }
            JSONArray jsonArray = null;
            try {
                String input = FileUtils.readFileToString(new File(path), "UTF-8");
                JSONObject jsonObject = JSONObject.fromObject(input);
                if (jsonObject != null) {
                    jsonArray = jsonObject.getJSONArray("list");
                }
            } catch (Exception e) {
                e.printStackTrace();
                jsonArray = null;
            }
            return new ResponseBean(jsonArray);
        }
         */
    }
}
// read from txt
// https://blog.csdn.net/xuehyunyu/article/details/77873420
/*
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

*/

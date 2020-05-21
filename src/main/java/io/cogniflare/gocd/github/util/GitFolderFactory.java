package io.cogniflare.gocd.github.util;

import java.io.File;

public class GitFolderFactory {

    public File create(String folderName) {
        return new File(folderName);
    }
}

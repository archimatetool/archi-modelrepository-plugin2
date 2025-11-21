/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class RepoUtilsTests {
    
    @BeforeEach
    public void runOnceBeforeEachTest() {
    }
    
    @AfterEach
    public void runOnceAfterEachTest() throws IOException {
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    
    @Test
    public void isSSH() {
        // True
        String[] trueOnes = {
                "git@github.com:archimatetool/archi-modelrepository-plugin.git",
                "ssh://user@host.xz/path/to/repo.git/",
                "ssh://user@host.xz:4019/path/to/repo.git/",
                "ssh://user:password@host.xz/path/to/repo.git/",
                "ssh://host.xz/path/to/repo.git/",
                "ssh://user@host.xz/path/to/repo.git/",
                "ssh://host.xz/path/to/repo.git/",
                "ssh://user@host.xz/~user/path/to/repo.git/",
                "ssh://host.xz/~user/path/to/repo.git/",
                "ssh://user@host.xz/~/path/to/repo.git",
                "ssh://host.xz/~/path/to/repo.git",
                "user@host.xz:/path/to/repo.git/",
                "host.xz:/path/to/repo.git/",
                "user@host.xz:~user/path/to/repo.git/",
                "host.xz:~user/path/to/repo.git/",
                "user@host.xz:path/to/repo.git",
                "host.xz:path/to/repo.git"
        };
        
        for(String url : trueOnes) {
            assertTrue(RepoUtils.isSSH(url));
        }
        
        // False
        String[] falseOnes = {
                "ssh://user@host.example.com",
                "https://githosting.org/path/archi-demo-grafico.git",
                "http://githosting.org/path/archi-demo-grafico.git",
                "ssh://:8888/path/to/repo.git/",
                "/Users/Fred/MyRepo",
                "file:/Users/Fred/MyRepo",
                "file:///Users/Fred/MyRepo"
        };
        
        for(String url : falseOnes) {
            assertFalse(RepoUtils.isSSH(url));
        }
    }

    @Test
    public void isArchiGitRepository() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        tmpFolder.mkdirs();
        assertFalse(RepoUtils.isArchiGitRepository(tmpFolder));
        
        File gitFolder = new File(tmpFolder, ".git");
        gitFolder.mkdirs();
        assertFalse(RepoUtils.isArchiGitRepository(tmpFolder));
        
        new File(tmpFolder, RepoConstants.MODEL_FILENAME).createNewFile();
        assertTrue(RepoUtils.isArchiGitRepository(tmpFolder));
    }
    
    @Test
    public void isModelInArchiRepository() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        tmpFolder.mkdirs();
        
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        File modelFile = new File(tmpFolder, RepoConstants.MODEL_FILENAME);
        modelFile.createNewFile();
        model.setFile(modelFile);
        assertFalse(RepoUtils.isModelInArchiRepository(model));

        File gitFolder = new File(tmpFolder, ".git");
        gitFolder.mkdirs();
        assertTrue(RepoUtils.isModelInArchiRepository(model));
    }

    @Test
    public void getWorkingFolderForModel() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        tmpFolder.mkdirs();
        
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        File modelFile = new File(tmpFolder, RepoConstants.MODEL_FILENAME);
        model.setFile(modelFile);
        assertNull(RepoUtils.getWorkingFolderForModel(model).orElse(null));
        
        modelFile.createNewFile();
        assertNull(RepoUtils.getWorkingFolderForModel(model).orElse(null));
        
        File gitFolder = new File(tmpFolder, ".git");
        gitFolder.mkdirs();
        assertEquals(tmpFolder, RepoUtils.getWorkingFolderForModel(model).orElse(null));
    }
}

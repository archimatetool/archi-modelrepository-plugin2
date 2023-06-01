/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.GitHelper;


@SuppressWarnings("nls")
public class RepoUtilsTests {
    
    @Before
    public void runOnceBeforeEachTest() {
    }
    
    @After
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
        };
        
        for(String url : falseOnes) {
            assertFalse(RepoUtils.isSSH(url));
        }
    }

    @Test
    public void isArchiGitRepository_File() throws Exception {
        File tmpFile = File.createTempFile("architest", null);
        assertFalse(RepoUtils.isArchiGitRepository(tmpFile));
        tmpFile.delete();
    }

    @Test
    public void isArchiGitRepository_NoModelFile() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        File gitFolder = new File(tmpFolder, ".git");
        gitFolder.mkdirs();
        assertFalse(RepoUtils.isArchiGitRepository(tmpFolder));
    }

    @Test
    public void isArchiGitRepository_True() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        File gitFolder = new File(tmpFolder, ".git");
        gitFolder.mkdirs();
        new File(tmpFolder, RepoConstants.MODEL_FILENAME).createNewFile();
        
        assertTrue(RepoUtils.isArchiGitRepository(tmpFolder));
    }
    
    @Test
    public void isModelInArchiRepository_True() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        File gitFolder = new File(tmpFolder, ".git");
        gitFolder.mkdirs();
        
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        File modelFile = new File(tmpFolder, RepoConstants.MODEL_FILENAME);
        modelFile.createNewFile();
        model.setFile(modelFile);
        assertTrue(RepoUtils.isModelInArchiRepository(model));
    }

    @Test
    public void getWorkingFolderForModel_True() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        File gitFolder = new File(tmpFolder, ".git");
        gitFolder.mkdirs();
        
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        File modelFile = new File(tmpFolder, RepoConstants.MODEL_FILENAME);
        modelFile.createNewFile();
        model.setFile(modelFile);
        assertEquals(tmpFolder, RepoUtils.getWorkingFolderForModel(model));
    }
    
    @Test
    public void getWorkingFolderForModel_Null() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        File modelFile = new File(tmpFolder, RepoConstants.MODEL_FILENAME);
        model.setFile(modelFile);
        assertNull(RepoUtils.getWorkingFolderForModel(model));
    }
}

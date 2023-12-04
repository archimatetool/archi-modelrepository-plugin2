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

import org.eclipse.jgit.api.Git;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.GitHelper;


@SuppressWarnings("nls")
public class ArchiRepositoryTests {
    
    @Before
    public void runOnceBeforeEachTest() {
    }
    
    @After
    public void runOnceAfterEachTest() throws IOException {
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    

    @Test
    public void init() throws Exception {
        File repoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        new ArchiRepository(repoFolder).init();
        
        try(Git git = Git.open(repoFolder)) {
            assertEquals(repoFolder, git.getRepository().getWorkTree());
            assertFalse(git.getRepository().isBare());
            assertEquals(RepoConstants.MAIN, git.getRepository().getBranch());
        }
    }
    
    @Test
    public void getWorkingFolder() {
        File repoFolder = new File("/temp/folder");
        IArchiRepository repo = new ArchiRepository(repoFolder);
        assertEquals(repoFolder, repo.getWorkingFolder());
    }

    @Test
    public void getGitFolder() {
        File repoFolder = new File("/temp/folder");
        IArchiRepository repo = new ArchiRepository(repoFolder);
        assertEquals(new File(repoFolder, ".git"), repo.getGitFolder());
    }

    @Test
    public void getName() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        File modelFile = new File(tmpFolder, RepoConstants.MODEL_FILENAME);
        
        IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
        model.setName("Test Model");
        model.setFile(modelFile);
        
        // Name will come from model open in EditorModelManager 
        IArchiRepository repo = new ArchiRepository(tmpFolder);
        assertEquals("Test Model", repo.getName());
        
        // Save and close model and name will come from file
        model.setName("Test Model 2");
        IEditorModelManager.INSTANCE.saveModel(model);
        IEditorModelManager.INSTANCE.closeModel(model);
        assertEquals("Test Model 2", repo.getName());
    }

    @Test
    public void getModelFile() {
        File repoFolder = new File("/temp/folder");
        IArchiRepository repo = new ArchiRepository(repoFolder);
        assertEquals(new File(repoFolder, RepoConstants.MODEL_FILENAME), repo.getModelFile());
    }
    
    @Test
    public void getOpenModel() {
        File repoFolder = new File("/temp/folder");
        IArchiRepository repo = new ArchiRepository(repoFolder);
        
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setFile(repo.getModelFile());
        
        // Not open
        assertNull(repo.getOpenModel());
        
        IEditorModelManager.INSTANCE.openModel(model);
        assertEquals(model, repo.getOpenModel());
    }
    
    @Test
    public void equals() {
        IArchiRepository repo1 = new ArchiRepository(new File("path1"));
        IArchiRepository repo2 = new ArchiRepository(new File("path1"));
        IArchiRepository repo3 = new ArchiRepository(new File("path2"));
        assertTrue(repo1.equals(repo2));
        assertFalse(repo1.equals(repo3));
    }
    
    @Test
    public void hashCodeSame() {
        IArchiRepository repo1 = new ArchiRepository(new File("path1"));
        IArchiRepository repo2 = new ArchiRepository(new File("path1"));
        IArchiRepository repo3 = new ArchiRepository(new File("path2"));
        assertTrue(repo1.hashCode() == repo2.hashCode());
        assertFalse(repo1.hashCode() == repo3.hashCode());
    }

}

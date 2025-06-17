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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class ArchiRepositoryTests {
    
    @BeforeEach
    public void runOnceBeforeEachTest() {
    }
    
    @AfterEach
    public void runOnceAfterEachTest() throws IOException {
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }

    @Test
    public void init() throws Exception {
        IArchiRepository archiRepo = GitHelper.createNewRepository().init();
        
        try(Repository repository = Git.open(archiRepo.getGitFolder()).getRepository()) {
            assertEquals(archiRepo.getWorkingFolder(), repository.getWorkTree());
            assertEquals(archiRepo.getGitFolder(), repository.getDirectory());
            assertTrue(archiRepo.getGitFolder().exists());
            assertFalse(repository.isBare());
            assertEquals(RepoConstants.MAIN, repository.getBranch());
        }
    }
    
    @Test
    public void cloneModel() throws Exception {
        // Create a new local repo and remote repo
        IArchiRepository archiRepo = GitHelper.createNewRepository().init();
        String repoURL = GitHelper.createBareRepository().getAbsolutePath();
        archiRepo.setRemote(repoURL);
        
        try(GitUtils utils = GitUtils.open(archiRepo.getGitFolder())) {
            // Add some commits
            GitHelper.writeFileToTestRepo(archiRepo, "file1.txt", "123");
            utils.commitChanges("Message 1", false);
            GitHelper.writeFileToTestRepo(archiRepo, "file2.txt", "456");
            utils.commitChanges("Message 2", false);
            
            // Push to remote
            utils.pushToRemote(null, null);
        }
        
        // Create a new local repo location
        archiRepo = GitHelper.createNewRepository("testRepo2");
        
        // Clone
        archiRepo.cloneModel(repoURL, null, null);
        
        try(GitUtils utils = GitUtils.open(archiRepo.getGitFolder())) {
            assertEquals(RepoConstants.MAIN, utils.getPrimaryBranch());
            assertEquals(RepoConstants.MAIN, utils.getCurrentLocalBranchName());
            assertEquals(RepoConstants.ORIGIN_MAIN, utils.getRemoteRefNameForCurrentBranch());
            assertEquals(repoURL, utils.getRemoteURL());
            Ref refHead = utils.getRepository().exactRef(RepoConstants.HEAD);
            assertEquals(RepoConstants.R_HEADS_MAIN, refHead.getTarget().getName());
            assertEquals(2, utils.getCommitCount());
        }
    }

    @Test
    public void cloneModel_EmptyRepo() throws Exception {
        IArchiRepository archiRepo = GitHelper.createNewRepository();
        String repoURL = GitHelper.createBareRepository().getAbsolutePath();
        archiRepo.cloneModel(repoURL, null, null);
        
        try(Repository repository = Git.open(archiRepo.getGitFolder()).getRepository()) {
            assertEquals(RepoConstants.MAIN, repository.getBranch());
            assertEquals(RepoConstants.ORIGIN, repository.getRemoteNames().iterator().next());
            Ref refHead = repository.exactRef(RepoConstants.HEAD);
            assertEquals(RepoConstants.R_HEADS_MAIN, refHead.getTarget().getName());
        }
    }
    
    @Test
    public void getWorkingFolder() {
        File repoFolder = new File("/temp/folder");
        IArchiRepository archiRepo = new ArchiRepository(repoFolder);
        assertEquals(repoFolder, archiRepo.getWorkingFolder());
    }

    @Test
    public void deleteWorkingFolderContents() throws Exception {
        IArchiRepository repo = GitHelper.createNewRepository().init();
        File file = GitHelper.writeFileToTestRepo(repo, "file.txt", "123");
        assertTrue(file.exists());
        assertTrue(repo.getGitFolder().exists());
        
        repo.deleteWorkingFolderContents();
        assertFalse(file.exists());
        assertTrue(repo.getGitFolder().exists());
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

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
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.GitHelper;

import junit.framework.JUnit4TestAdapter;


@SuppressWarnings("nls")
public class ArchiRepositoryTests {
    
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(ArchiRepositoryTests.class);
    }
    
    @Before
    public void runOnceBeforeEachTest() {
    }
    
    @After
    public void runOnceAfterEachTest() throws IOException {
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    

    @Test
    public void init() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        new ArchiRepository(localRepoFolder).init();
        
        try(Git git = Git.open(localRepoFolder)) {
            assertEquals(localRepoFolder, git.getRepository().getWorkTree());
            assertFalse(git.getRepository().isBare());
            assertEquals(IRepositoryConstants.MAIN, git.getRepository().getBranch());
        }
    }
    
    @Test
    public void setRemote() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(localRepoFolder);
        repository.init();
        
        String url = "https://www.somewherethereish.net/myRepo.git";
        repository.setRemote(url);
        
        try(Git git = Git.open(localRepoFolder)) {
            List<RemoteConfig> remotes = git.remoteList().call();
            assertEquals(1, remotes.size());
            RemoteConfig config = remotes.get(0);
            assertEquals(IRepositoryConstants.ORIGIN, config.getName());
            assertEquals(1, config.getURIs().size());
            assertEquals(url, config.getURIs().get(0).toASCIIString());
        }
    }
    
    @Test
    public void removeRemote() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(localRepoFolder);
        repository.init();
        
        String url = "https://www.somewherethereish.net/myRepo.git";
        repository.setRemote(url);
        
        RemoteConfig config = repository.setRemote(null);
        assertEquals(IRepositoryConstants.ORIGIN, config.getName());
        assertEquals(1, config.getURIs().size());
        assertEquals(url, config.getURIs().get(0).toASCIIString());
        
        try(Git git = Git.open(localRepoFolder)) {
            List<RemoteConfig> remotes = git.remoteList().call();
            assertEquals(0, remotes.size());
        }
    }
    
    @Test
    public void isHeadAndRemoteSame_False() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(localRepoFolder);
        repository.init();
        assertFalse(repository.isHeadAndRemoteSame());
    }
    
    @Test
    public void getCurrentLocalBranchName() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(localRepoFolder);
        repository.init();
        assertEquals(IRepositoryConstants.MAIN, repository.getCurrentLocalBranchName());
    }
    
    @Test
    public void getLocalGitFolder() {
        File localRepoFolder = new File("/temp/folder");
        IArchiRepository repo = new ArchiRepository(localRepoFolder);
        assertEquals(new File(localRepoFolder, ".git"), repo.getLocalGitFolder());
    }

    @Test
    public void getName() throws IOException {
        File tmpFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        File modelFile = new File(tmpFolder, IRepositoryConstants.MODEL_FILENAME);
        
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
        File localRepoFolder = new File("/temp/folder");
        IArchiRepository repo = new ArchiRepository(localRepoFolder);
        assertEquals(new File(localRepoFolder, IRepositoryConstants.MODEL_FILENAME), repo.getModelFile());
    }
    
    @Test
    public void getOnlineRepositoryURL() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repo = new ArchiRepository(localRepoFolder);
        String url = "https://www.somewherethereish.net/myRepo.git";
        
        repo.init();
        repo.setRemote(url);
        
        assertEquals(url, repo.getOnlineRepositoryURL());
    }
    
    @Test
    public void getModel() {
        File localRepoFolder = new File("/temp/folder");
        IArchiRepository repo = new ArchiRepository(localRepoFolder);
        
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setFile(repo.getModelFile());
        
        // Not open
        assertNull(repo.getModel());
        
        IEditorModelManager.INSTANCE.openModel(model);
        assertEquals(model, repo.getModel());
    }
    
    @Test
    public void savegetUserDetails() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repo = new ArchiRepository(localRepoFolder);
        repo.init();
        repo.saveUserDetails("Monkey", "monkey@apes.com");
        PersonIdent personIdent = repo.getUserDetails();
        assertEquals("Monkey", personIdent.getName());
        assertEquals("monkey@apes.com", personIdent.getEmailAddress());
    }
    
    @Test
    public void equals() {
        IArchiRepository repo1 = new ArchiRepository(new File("path1"));
        IArchiRepository repo2 = new ArchiRepository(new File("path1"));
        IArchiRepository repo3 = new ArchiRepository(new File("path2"));
        assertTrue(repo1.equals(repo2));
        assertFalse(repo1.equals(repo3));
    }
}

/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.modelrepository.GitHelper;

import junit.framework.JUnit4TestAdapter;


@SuppressWarnings("nls")
public class GitUtilsTests {
    
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(GitUtilsTests.class);
    }
    
    @Before
    public void runOnceBeforeEachTest() {
    }
    
    @After
    public void runOnceAfterEachTest() throws IOException {
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    

    @Test
    public void isRemoteRefForCurrentBranchAtHead_False() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(localRepoFolder);
        repository.init();
        
        try(GitUtils utils = GitUtils.open(localRepoFolder)) {
            assertFalse(utils.isRemoteRefForCurrentBranchAtHead());
        }
    }
    
    @Test
    public void getOnlineRepositoryURL() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repo = new ArchiRepository(localRepoFolder);
        repo.init();
        
        String url = "https://www.somewherethereish.net/myRepo.git";
        
        try(GitUtils utils = GitUtils.open(localRepoFolder)) {
            utils.setRemote(url);
            assertEquals(url, utils.getOnlineRepositoryURL());
        }
    }
    
    @Test
    public void setRemote() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(localRepoFolder);
        repository.init();
        
        String url = "https://www.somewherethereish.net/myRepo.git";
        
        try(GitUtils utils = GitUtils.open(localRepoFolder)) {
            utils.setRemote(url);
        }
        
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
        
        try(GitUtils utils = GitUtils.open(localRepoFolder)) {
            String url = "https://www.somewherethereish.net/myRepo.git";
            utils.setRemote(url); // Set a remote
            
            // Remove the remote
            RemoteConfig config = utils.setRemote(null);
            
            // Returned RemoteConfig will have old values 
            assertEquals(IRepositoryConstants.ORIGIN, config.getName());
            assertEquals(1, config.getURIs().size());
            assertEquals(url, config.getURIs().get(0).toASCIIString());
            
            // Zero remotes
            List<RemoteConfig> remotes = utils.getGit().remoteList().call();
            assertEquals(0, remotes.size());
        }
    }
    
    @Test
    public void savegetUserDetails() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repo = new ArchiRepository(localRepoFolder);
        repo.init();
        
        try(GitUtils utils = GitUtils.open(localRepoFolder)) {
            utils.saveUserDetails("Monkey", "monkey@apes.com");
            PersonIdent personIdent = utils.getUserDetails();
            assertEquals("Monkey", personIdent.getName());
            assertEquals("monkey@apes.com", personIdent.getEmailAddress());
        }
    }

    @Test
    public void getCurrentLocalBranchName() throws Exception {
        File localRepoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repo = new ArchiRepository(localRepoFolder);
        repo.init();
        
        try(GitUtils utils = GitUtils.open(localRepoFolder)) {
            assertEquals(IRepositoryConstants.MAIN, utils.getCurrentLocalBranchName());
        }
    }

}
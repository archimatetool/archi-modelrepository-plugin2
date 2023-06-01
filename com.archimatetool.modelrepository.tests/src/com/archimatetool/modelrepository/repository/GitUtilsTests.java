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


@SuppressWarnings("nls")
public class GitUtilsTests {
    
    @Before
    public void runOnceBeforeEachTest() {
    }
    
    @After
    public void runOnceAfterEachTest() throws IOException {
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    

    @Test
    public void isRemoteRefForCurrentBranchAtHead_False() throws Exception {
        File repoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(repoFolder);
        repository.init();
        
        try(GitUtils utils = GitUtils.open(repoFolder)) {
            assertFalse(utils.isRemoteRefForCurrentBranchAtHead());
        }
    }
    
    @Test
    public void getRemoteURL() throws Exception {
        File repoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repo = new ArchiRepository(repoFolder);
        repo.init();
        
        String url = "https://www.somewherethereish.net/myRepo.git";
        
        try(GitUtils utils = GitUtils.open(repoFolder)) {
            utils.setRemote(url);
            assertEquals(url, utils.getRemoteURL());
        }
    }
    
    @Test
    public void setRemote() throws Exception {
        File repoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(repoFolder);
        repository.init();
        
        String url = "https://www.somewherethereish.net/myRepo.git";
        
        try(GitUtils utils = GitUtils.open(repoFolder)) {
            utils.setRemote(url);
        }
        
        try(Git git = Git.open(repoFolder)) {
            List<RemoteConfig> remotes = git.remoteList().call();
            assertEquals(1, remotes.size());
            RemoteConfig config = remotes.get(0);
            assertEquals(RepoConstants.ORIGIN, config.getName());
            assertEquals(1, config.getURIs().size());
            assertEquals(url, config.getURIs().get(0).toASCIIString());
        }
    }
    
    @Test
    public void removeRemote() throws Exception {
        File repoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repository =  new ArchiRepository(repoFolder);
        repository.init();
        
        try(GitUtils utils = GitUtils.open(repoFolder)) {
            String url = "https://www.somewherethereish.net/myRepo.git";
            utils.setRemote(url); // Set a remote
            
            // Remove the remote
            RemoteConfig config = utils.setRemote(null);
            
            // Returned RemoteConfig will have old values 
            assertEquals(RepoConstants.ORIGIN, config.getName());
            assertEquals(1, config.getURIs().size());
            assertEquals(url, config.getURIs().get(0).toASCIIString());
            
            // Zero remotes
            List<RemoteConfig> remotes = utils.remoteList().call();
            assertEquals(0, remotes.size());
        }
    }
    
    @Test
    public void savegetUserDetails() throws Exception {
        File repoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repo = new ArchiRepository(repoFolder);
        repo.init();
        
        try(GitUtils utils = GitUtils.open(repoFolder)) {
            utils.saveUserDetails("Monkey", "monkey@apes.com");
            PersonIdent personIdent = utils.getUserDetails();
            assertEquals("Monkey", personIdent.getName());
            assertEquals("monkey@apes.com", personIdent.getEmailAddress());
        }
    }

    @Test
    public void getCurrentLocalBranchName() throws Exception {
        File repoFolder = new File(GitHelper.getTempTestsFolder(), "testRepo");
        IArchiRepository repo = new ArchiRepository(repoFolder);
        repo.init();
        
        try(GitUtils utils = GitUtils.open(repoFolder)) {
            assertEquals(RepoConstants.MAIN, utils.getCurrentLocalBranchName());
        }
    }

}

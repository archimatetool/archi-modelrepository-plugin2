/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.PlatformUtils;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;

/**
 * Representation of a local repository
 * This is a wrapper class around a local repo folder
 * 
 * @author Phillip Beauvoir
 */
public class ArchiRepository implements IArchiRepository {
    
    /**
     * The folder location of the local repository
     */
    private File fLocalRepoFolder;

    public ArchiRepository(File localRepoFolder) {
        fLocalRepoFolder = localRepoFolder;
    }
    
    @Override
    public void init() throws GitAPIException, IOException {
        // Init
        InitCommand initCommand = Git.init().setDirectory(getLocalRepositoryFolder());
        
        // Call
        try(Git git = initCommand.call()) {
            // Default config
            setDefaultConfigSettings(git.getRepository());
            
            // Set tracked "main" branch
            setTrackedBranch(git.getRepository(), MAIN);
            
            // Kludge to set default branch to "main" not "master"
            String ref = "ref: refs/heads/main"; //$NON-NLS-1$
            File headFile = new File(getLocalGitFolder(), "HEAD"); //$NON-NLS-1$
            Files.write(headFile.toPath(), ref.getBytes());
        }

        // Set a default repo name. This will also create the "archi" marker file
        setName(getLocalRepositoryFolder().getName());
    }
    
    @Override
    public RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException, IOException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            Status status = git.status().call();
            
            // Nothing changed
            if(status.isClean()) {
                return null;
            }
            
            // Add all modified files to index
            git.add().addFilepattern(".").call(); //$NON-NLS-1$
            
            // Add missing files to index
            for(String s : status.getMissing()) {
                git.rm().addFilepattern(s).call();
            }
            
            // Commit
            CommitCommand commitCommand = git.commit();
            PersonIdent userDetails = getUserDetails();
            commitCommand.setAuthor(userDetails);
            commitCommand.setMessage(commitMessage);
            commitCommand.setAmend(amend);
            return commitCommand.call();
        }
    }

    @Override
    public boolean hasChangesToCommit() throws IOException, GitAPIException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            Status status = git.status().call();
            return !status.isClean();
        }
    }

    @Override
    public String getCurrentLocalBranchName() throws IOException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            return git.getRepository().getBranch();
        }
    }
    
    @Override
    public File getLocalRepositoryFolder() {
        return fLocalRepoFolder;
    }
    
    @Override
    public File getLocalGitFolder() {
        return new File(getLocalRepositoryFolder(), ".git"); //$NON-NLS-1$
    }

    @Override
    public String getName() {
        try {
            return ArchiRepositoryProperties.open(this).getProperty("name", Messages.ArchiRepository_0); //$NON-NLS-1$
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }

        return Messages.ArchiRepository_0;
    }
    
    @Override
    public void setName(String name) {
        try {
            ArchiRepositoryProperties properties = ArchiRepositoryProperties.open(this);
            properties.setProperty("name", name); //$NON-NLS-1$
            properties.save();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public File getModelFile() {
        return new File(getLocalGitFolder(), MODEL_FILENAME);
    }
    
    @Override
    public String getOnlineRepositoryURL() throws IOException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            return git.getRepository().getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, ORIGIN, ConfigConstants.CONFIG_KEY_URL);
        }
    }
    
    @Override
    public IArchimateModel getModel() {
        File modelFile = getModelFile();
        
        for(IArchimateModel model : IEditorModelManager.INSTANCE.getModels()) {
            if(modelFile.equals(model.getFile())) {
                return model;
            }
        }
        
        return null;
    }
    
    @Override
    public PersonIdent getUserDetails() throws IOException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            StoredConfig config = git.getRepository().getConfig();
            String name = StringUtils.safeString(config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME));
            String email = StringUtils.safeString(config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL));
            return new PersonIdent(name, email);
        }
    }

    @Override
    public void copyModelToWorkingDirectory() throws IOException {
        FileHandler.copyModelFileToWorkingDirectory(getLocalRepositoryFolder(), getModelFile());
        
        // Staging the model.xml file will clear different line endings but can be slow on a large model file
//        try(Git git = Git.open(workingDir)) {
//            git.add().addFilepattern(WORKING_MODEL_FILENAME).call();
//        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if((obj != null) && (obj instanceof ArchiRepository)) {
            return fLocalRepoFolder != null && fLocalRepoFolder.equals(((IArchiRepository)obj).getLocalRepositoryFolder());
        }
        return false;
    }
    
    /**
     * Set default settings in the config file 
     * @param repository
     * @throws IOException
     */
    private void setDefaultConfigSettings(Repository repository) throws IOException {
        StoredConfig config = repository.getConfig();
        
        // Set line endings depending on platform
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, PlatformUtils.isWindows() ? "true" : "input"); //$NON-NLS-1$ //$NON-NLS-2$
        
        config.save();
    }
    
    /**
     * Set the given branchName to track "origin"
     */
    private void setTrackedBranch(Repository repository, String branchName) throws IOException {
        if(branchName == null) {
            return;
        }
        
        StoredConfig config = repository.getConfig();
        
        if(!ORIGIN.equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE))) {
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName,  ConfigConstants.CONFIG_KEY_REMOTE, ORIGIN);
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branchName);
            config.save();
        }
    }
}

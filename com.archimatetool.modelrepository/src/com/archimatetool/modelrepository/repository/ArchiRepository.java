/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.PlatformUtils;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.authentication.CredentialsAuthenticator;
import com.archimatetool.modelrepository.authentication.UsernamePassword;

/**
 * Representation of a local repository
 * This is a wrapper class around a local repo folder
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
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
        try(Git git = Git.init().setDirectory(getLocalRepositoryFolder()).call()) {
            // Config Defaults
            setDefaultConfigSettings(git.getRepository());
            
            // Exclude file
            createExcludeFile();
            
            // Set head to "main"
            setHeadToMainBranch();
        }
    }
    
    @Override
    public RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException, IOException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            Status status = git.status().call();
            
            // Nothing changed
            if(status.isClean()) {
                return null;
            }
            
            // Add modified files to index
            git.add().addFilepattern(".").call();
            //git.add().addFilepattern(MODEL_FILENAME).addFilepattern(IMAGES_FOLDER).call();
            
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
    public void cloneModel(String repoURL, UsernamePassword npw, ProgressMonitor monitor) throws GitAPIException, IOException {
        CloneCommand cloneCommand = Git.cloneRepository();
        cloneCommand.setDirectory(getLocalRepositoryFolder());
        cloneCommand.setURI(repoURL);
        cloneCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(repoURL, npw));
        cloneCommand.setProgressMonitor(monitor);
        
        try(Git git = cloneCommand.call()) {
            // Config Defaults
            setDefaultConfigSettings(git.getRepository());
            
            // Exclude file
            createExcludeFile();
        }
    }

    @Override
    public boolean hasChangesToCommit() throws IOException, GitAPIException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            Status status = git.status().call();
            //Status status = git.status().addPath(MODEL_FILENAME).call();
            return !status.isClean();
        }
    }
    
    @Override
    public Iterable<PushResult> pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            PushCommand pushCommand = git.push();
            pushCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(getOnlineRepositoryURL(), npw));
            pushCommand.setProgressMonitor(monitor);
            
            Iterable<PushResult> result = pushCommand.call();
            
            // After a successful push, ensure we are tracking the current branch
            setTrackedBranch(git.getRepository(), git.getRepository().getBranch());
            
            return result;
        }
    }
    
    @Override
    public RemoteConfig addRemote(String URL) throws IOException, GitAPIException, URISyntaxException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            RemoteAddCommand remoteAddCommand = git.remoteAdd();
            remoteAddCommand.setName(ORIGIN);
            remoteAddCommand.setUri(new URIish(URL));
            return remoteAddCommand.call();
        }
    }
    
    @Override
    public RemoteConfig removeRemote() throws IOException, GitAPIException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            RemoteRemoveCommand remoteRemoveCommand = git.remoteRemove();
            remoteRemoveCommand.setRemoteName(ORIGIN);
            return remoteRemoveCommand.call();
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
        // Open the "model.archimate" file and read it from the XML
        try(Stream<String> stream = Files.lines(Paths.get(getLocalRepositoryFolder().getAbsolutePath(), MODEL_FILENAME))
                .filter(line -> line.indexOf("<archimate:model") != -1)) {
            Optional<String> result = stream.findFirst();
            if(result.isPresent()) {
                String segments[] = result.get().split("\"");
                for(int i = 0; i < segments.length; i++) {
                    if(segments[i].contains("name=")) {
                        return segments[i + 1];
                    }
                }
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        return Messages.ArchiRepository_0;
    }
    
    @Override
    public File getModelFile() {
        return new File(getLocalRepositoryFolder(), MODEL_FILENAME);
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
    public boolean equals(Object obj) {
        if(obj instanceof ArchiRepository) {
            return fLocalRepoFolder != null && fLocalRepoFolder.equals(((ArchiRepository)obj).getLocalRepositoryFolder());
        }
        return false;
    }
    
    private void setDefaultConfigSettings(Repository repository) throws IOException {
        StoredConfig config = repository.getConfig();

        // Set line endings depending on platform
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, PlatformUtils.isWindows() ? "true" : "input");

        // Set ignore case on Windows
        if(PlatformUtils.isWindows()) {
            config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, "ignorecase", "true");
        }
        
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
    
    /**
     * Create exclude file for ignored files
     */
    private void createExcludeFile() throws IOException {
        String excludes = "*.bak\n.DS_Store";
        File excludeFile = new File(getLocalGitFolder(), "/info/exclude");
        excludeFile.getParentFile().mkdirs();
        Files.write(excludeFile.toPath(), excludes.getBytes());
    }
    
    /**
     * Kludge to set default branch to "main" not "master"
     */
    private void setHeadToMainBranch() throws IOException {
        String ref = "ref: refs/heads/main";
        File headFile = new File(getLocalGitFolder(), "HEAD");
        Files.write(headFile.toPath(), ref.getBytes());
    }
}

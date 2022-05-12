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
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.PlatformUtils;
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
    
    private static Logger logger = Logger.getLogger(ArchiRepository.class.getName());
    
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
        try(Git git = Git.init().setInitialBranch(IRepositoryConstants.MAIN).setDirectory(getLocalRepositoryFolder()).call()) {
            // Config Defaults
            setDefaultConfigSettings(git.getRepository());
            
            // Exclude file
            createExcludeFile();
        }
    }
    
    @Override
    public void cloneModel(String repoURL, UsernamePassword npw, ProgressMonitor monitor) throws GitAPIException, IOException {
        CloneCommand cloneCommand = Git.cloneRepository();
        cloneCommand.setDirectory(getLocalRepositoryFolder());
        cloneCommand.setURI(repoURL);
        cloneCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        cloneCommand.setProgressMonitor(monitor);
        
        try(Git git = cloneCommand.call()) {
            // Config Defaults
            setDefaultConfigSettings(git.getRepository());
            
            // Exclude file
            createExcludeFile();
        }
    }

    @Override
    public RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException, IOException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            return utils.commitChanges(commitMessage, amend);
        }
    }

    @Override
    public boolean hasChangesToCommit() throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            return utils.hasChangesToCommit();
        }
    }
    
    @Override
    public Iterable<PushResult> pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            return utils.pushToRemote(npw, monitor);
        }
    }
    
    @Override
    public PullResult pullFromRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            return utils.pullFromRemote(npw, monitor);
        }
    }

    @Override
    public RemoteConfig setRemote(String URL) throws IOException, GitAPIException, URISyntaxException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            return utils.setRemote(URL);
        }
    }
    
    @Override
    public void resetToRef(String ref) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            utils.resetToRef(ref);
        }
    }

    @Override
    public String getCurrentLocalBranchName() throws IOException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            return utils.getCurrentLocalBranchName();
        }
    }
    
    @Override
    public File getLocalRepositoryFolder() {
        return fLocalRepoFolder;
    }
    
    @Override
    public File getGitFolder() {
        return new File(getLocalRepositoryFolder(), ".git");
    }

    @Override
    public String getName() {
        // If the model is open, return its name
        IArchimateModel model = getModel();
        if(model != null) {
            return model.getName();
        }
        
        // If model not open, open the "model.archimate" file and read it from there
        File modelFile = getModelFile();
        if(modelFile.exists()) {
            try(Stream<String> stream = Files.lines(modelFile.toPath())
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
            catch(Exception ex) { // Catch all exceptions to stop exception dialog
                logger.severe("Could not get repository name (wrong file type) for: " + getModelFile());
            }
        }
        
        return Messages.ArchiRepository_0;
    }
    
    @Override
    public File getModelFile() {
        return new File(getLocalRepositoryFolder(), IRepositoryConstants.MODEL_FILENAME);
    }
    
    @Override
    public String getOnlineRepositoryURL() throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            return utils.getOnlineRepositoryURL();
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
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            return utils.getUserDetails();
        }
    }

    @Override
    public void saveUserDetails(String name, String email) throws IOException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            utils.saveUserDetails(name, email);
        }
    }

    @Override
    public void extractCommit(RevCommit commit, File folder) throws IOException {
        try(GitUtils utils = GitUtils.open(getLocalRepositoryFolder())) {
            utils.extractCommit(commit, folder);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof ArchiRepository) {
            return fLocalRepoFolder != null && fLocalRepoFolder.equals(((ArchiRepository)obj).getLocalRepositoryFolder());
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        // Equality for Java sets
        return fLocalRepoFolder != null ? fLocalRepoFolder.hashCode() : super.hashCode();
    }

    /**
     * Set some default local config settings
     */
    private void setDefaultConfigSettings(Repository repository) throws IOException {
        StoredConfig config = repository.getConfig();

        // Set line endings depending on platform
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, PlatformUtils.isWindows() ? "true" : "input");

        // Set ignore case on Mac/Windows
        if(!PlatformUtils.isLinux()) {
            config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, "ignorecase", "true");
        }
        
        config.save();
    }
    
    /**
     * Create exclude file for ignored files
     */
    private void createExcludeFile() throws IOException {
        String excludes = "*.bak\n.DS_Store";
        File excludeFile = new File(getGitFolder(), "/info/exclude");
        excludeFile.getParentFile().mkdirs();
        Files.write(excludeFile.toPath(), excludes.getBytes());
    }
}

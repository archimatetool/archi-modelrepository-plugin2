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
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
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
     * The working directory of the local git repository
     */
    private File repoFolder;

    public ArchiRepository(File repoFolder) {
        this.repoFolder = repoFolder;
    }
    
    @Override
    public void init() throws GitAPIException, IOException {
        // Init
        try(Git git = Git.init().setInitialBranch(RepoConstants.MAIN).setDirectory(getWorkingFolder()).call()) {
            // Config Defaults
            setDefaultConfigSettings(git.getRepository());
            
            // Exclude file
            createExcludeFile();
        }
    }
    
    @Override
    public void cloneModel(String url, UsernamePassword npw, ProgressMonitor monitor) throws GitAPIException, IOException {
        CloneCommand cloneCommand = Git.cloneRepository();
        cloneCommand.setDirectory(getWorkingFolder());
        cloneCommand.setURI(url);
        cloneCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        cloneCommand.setProgressMonitor(monitor);
        
        try(Git git = cloneCommand.call()) {
            // Config Defaults
            setDefaultConfigSettings(git.getRepository());
            
            // Exclude file
            createExcludeFile();
            
            // If this is an empty repository, ensure that we have HEAD pointing to "main"
            Ref refHead = git.getRepository().exactRef(Constants.HEAD); // What's HEAD referencing?
            // This is an empty repo because HEAD file points to an unborn branch
            if(refHead != null && refHead.getTarget().getObjectId() == null) {
                // So write this ref to the HEAD file
                String ref = "ref: refs/heads/main";
                File headFile = new File(getGitFolder(), "HEAD");
                Files.write(headFile.toPath(), ref.getBytes());
            }
        }
    }

    @Override
    public RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException, IOException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.commitChanges(commitMessage, amend);
        }
    }

    @Override
    public boolean hasChangesToCommit() throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.hasChangesToCommit();
        }
    }
    
    @Override
    public PushResult pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.pushToRemote(npw, monitor);
        }
    }
    
    @Override
    public RemoteConfig setRemote(String URL) throws IOException, GitAPIException, URISyntaxException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.setRemote(URL);
        }
    }
    
    @Override
    public FetchResult fetchFromRemote(UsernamePassword npw, ProgressMonitor monitor, boolean isDryrun) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.fetchFromRemote(npw, monitor, isDryrun);
        }
    }

    @Override
    public void resetToRef(String ref) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            utils.resetToRef(ref);
        }
    }

    @Override
    public String getCurrentLocalBranchName() throws IOException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.getCurrentLocalBranchName();
        }
    }
    
    @Override
    public File getWorkingFolder() {
        return repoFolder;
    }
    
    @Override
    public File getGitFolder() {
        return new File(getWorkingFolder(), ".git");
    }

    @Override
    public String getName() {
        // If the model is open, return its name
        IArchimateModel model = getOpenModel();
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
        return new File(getWorkingFolder(), RepoConstants.MODEL_FILENAME);
    }
    
    @Override
    public String getRemoteURL() throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.getRemoteURL();
        }
    }
    
    @Override
    public IArchimateModel getOpenModel() {
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
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.getUserDetails();
        }
    }

    @Override
    public void saveUserDetails(String name, String email) throws IOException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            utils.saveUserDetails(name, email);
        }
    }

    @Override
    public void extractCommit(RevCommit commit, File folder, boolean preserveEol) throws IOException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            utils.extractCommit(commit, folder, preserveEol);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof ArchiRepository) {
            return repoFolder != null && repoFolder.equals(((ArchiRepository)obj).getWorkingFolder());
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        // Equality for Java sets
        return repoFolder != null ? repoFolder.hashCode() : super.hashCode();
    }

    /**
     * Set some default local config settings
     */
    private void setDefaultConfigSettings(Repository repository) throws IOException {
        StoredConfig config = repository.getConfig();

        /*
         * Set line endings depending on platform
         * 
         * Windows autocrlf=true
         *         Checked out files will be CRLF, checked in files will be LF
         *         Eclipse saves as CRLF so git Status will ignore EOLs
         * 
         * Mac/Linux autocrlf=input   
         *           Checked out files will be LF, checked in files will be LF
         *           Eclipse saves as LF so git Status will ignore EOLs
         */
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, PlatformUtils.isWindows() ? "true" : "input");

        // Set ignore case on Mac/Windows
        if(!PlatformUtils.isLinux()) {
            config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, "ignorecase", "true");
        }
        
        // Set GPG signing false
        config.setString(ConfigConstants.CONFIG_COMMIT_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, "false");
        
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

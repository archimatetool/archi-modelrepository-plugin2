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
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.PlatformUtils;
import com.archimatetool.model.IArchimateModel;

/**
 * Representation of a local repository
 * This is a wrapper class around a local repo folder
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ArchiRepository implements IArchiRepository {
    
    private static Logger logger = Logger.getLogger(ArchiRepository.class.getName());
    
    // For matching name attribute in model.archimate file
    private static final Pattern NAME_PATTERN = Pattern.compile("name=\"([^\"]+)\"");
    
    /**
     * The working directory of the local git repository
     */
    private File repoFolder;

    public ArchiRepository(File repoFolder) {
        this.repoFolder = repoFolder;
    }
    
    @Override
    public IArchiRepository init() throws GitAPIException, IOException {
        // Init
        try(Repository repository = Git.init()
                .setInitialBranch(RepoConstants.MAIN)
                .setDirectory(getWorkingFolder())
                .call().getRepository()) {
            
            // Config Defaults
            setDefaultConfigSettings(repository);
            
            // Exclude file
            createExcludeFile();
        }
        
        return this;
    }
    
    @Override
    public void cloneModel(String url, CredentialsProvider credentialsProvider, ProgressMonitor monitor) throws GitAPIException, IOException {
        try(Repository repository = Git.cloneRepository()
                .setDirectory(getWorkingFolder())
                .setURI(url)
                .setCredentialsProvider(credentialsProvider)
                .setProgressMonitor(monitor)
                .setCloneAllBranches(true)
                .call().getRepository()) {
            
            // Config Defaults
            setDefaultConfigSettings(repository);
            
            // Exclude file
            createExcludeFile();
            
            // If this is an empty repository, ensure that HEAD references "refs/heads/main" and not, say, "refs/heads/master"
            Ref refHead = repository.exactRef(RepoConstants.HEAD); // What's HEAD referencing?
            // This is an empty repo because HEAD file points to an non-existing branch (objectId is null)
            if(refHead != null && refHead.getTarget().getObjectId() == null
                               && !RepoConstants.R_HEADS_MAIN.equals(refHead.getTarget().getName())) {
                RefUpdate refUpdate = repository.updateRef(RepoConstants.HEAD);
                refUpdate.disableRefLog();
                refUpdate.link(RepoConstants.R_HEADS_MAIN);
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
    public RevCommit commitChangesWithManifest(String commitMessage, boolean amend) throws GitAPIException, IOException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.commitChangesWithManifest(commitMessage, amend);
        }
    }

    @Override
    public RevCommit commitModelWithManifest(IArchimateModel model, String commitMessage) throws GitAPIException, IOException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.commitModelWithManifest(model, commitMessage);
        }
    }

    @Override
    public boolean hasChangesToCommit() throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.hasChangesToCommit();
        }
    }
    
    @Override
    public PushResult pushToRemote(CredentialsProvider credentialsProvider, ProgressMonitor monitor) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.pushToRemote(credentialsProvider, monitor);
        }
    }
    
    @Override
    public RemoteConfig setRemote(String url) throws IOException, GitAPIException, URISyntaxException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.setRemote(url);
        }
    }
    
    @Override
    public Optional<String> getRemoteURL() throws IOException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.getRemoteURL();
        }
    }
    
    @Override
    public void removeRemoteRefs(String url) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            utils.removeRemoteRefs(url);
        }
    }
    
    @Override
    public List<FetchResult> fetchFromRemote(CredentialsProvider credentialsProvider, ProgressMonitor monitor, boolean fetchTags) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.fetchFromRemote(credentialsProvider, monitor, fetchTags);
        }
    }

    @Override
    public void resetToRef(String ref) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            utils.resetToRef(ref);
        }
    }

    @Override
    public Optional<String> getCurrentLocalBranchName() throws IOException {
        try(GitUtils utils = GitUtils.open(getWorkingFolder())) {
            return utils.getCurrentLocalBranchName();
        }
    }
    
    @Override
    public File getWorkingFolder() {
        return repoFolder;
    }
    
    @Override
    public void deleteWorkingFolderContents() throws IOException {
        Path gitFolder = Path.of(getWorkingFolder().getPath(), ".git");
        
        Files.walk(getWorkingFolder().toPath())
             .filter(path -> !path.startsWith(gitFolder)) // Not the .git folder
             .sorted(Comparator.reverseOrder())           // Has to be sorted in reverse order to prevent removal of a non-empty directory
             .map(Path::toFile)
             .forEach(File::delete);
    }
    
    @Override
    public File getGitFolder() {
        return new File(getWorkingFolder(), ".git");
    }

    @Override
    public String getName() {
        // If the model is open, return its name
        IArchimateModel model = getOpenModel().orElse(null);
        if(model != null) {
            return model.getName();
        }
        
        // If model not open, open the "model.archimate" file and read it from there
        File modelFile = getModelFile();
        if(modelFile.exists()) {
            try(Stream<String> stream = Files.lines(modelFile.toPath())
                                             .filter(line -> line.contains("<archimate:model"))) {
                String result = stream.findFirst().orElse(null);
                if(result != null) {
                    Matcher matcher = NAME_PATTERN.matcher(result);
                    if(matcher.find()) {
                        return matcher.group(1)
                                      .replace("&amp;", "&")
                                      .replace("&lt;", "<")
                                      .replace("&gt;", ">")
                                      .replace("&quot;", "\"")
                                      .replace("&apos;", "'");
                    }
                }
            }
            catch(Exception ex) { // Catch all exceptions to stop exception dialog
                logger.severe("Could not read model name for: " + getModelFile());
            }
        }
        
        return Messages.ArchiRepository_0;
    }
    
    @Override
    public File getModelFile() {
        return new File(getWorkingFolder(), RepoConstants.MODEL_FILENAME);
    }
    
    @Override
    public Optional<IArchimateModel> getOpenModel() {
        File modelFile = getModelFile();
        
        for(IArchimateModel model : IEditorModelManager.INSTANCE.getModels()) {
            if(modelFile.equals(model.getFile())) {
                return Optional.of(model);
            }
        }
        
        return Optional.empty();
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
        // Equality based on repo (working) folder
        return obj instanceof ArchiRepository repo ? Objects.equals(repoFolder, repo.repoFolder) : false;
    }
    
    @Override
    public int hashCode() {
        // Equality based on repo (working) folder for Java sets
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

        // Set ignore case for file names on Mac/Windows
        if(!PlatformUtils.isLinux()) {
            config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, "ignorecase", "true");
        }
        
        // Set hooksPath to null in case the user has set a global hooksPath and cause problems
        // such as git-lfs not being found on the system path.
        // JGit will resolve hooksPath to localrepo/.git/hooks
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_HOOKS_PATH, null);
        
        config.save();
    }
    
    /**
     * Create /info/exclude file for ignored files
     */
    private void createExcludeFile() throws IOException {
        List<String> excludes = List.of("*.bak", ".DS_Store");
        File excludeFile = new File(getGitFolder(), "/info/exclude");
        excludeFile.getParentFile().mkdirs();
        Files.write(excludeFile.toPath(), excludes);
    }
}

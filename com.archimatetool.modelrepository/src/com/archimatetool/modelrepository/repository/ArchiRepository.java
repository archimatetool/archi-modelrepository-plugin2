/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.eclipse.jgit.api.CleanCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;

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
        try(Git git = Git.init().setInitialBranch(MAIN).setDirectory(getLocalRepositoryFolder()).call()) {
            // Config Defaults
            setDefaultConfigSettings(git.getRepository());
            
            // Exclude file
            createExcludeFile();
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
            // Ensure we are tracking the current branch
            setTrackedBranch(git.getRepository(), git.getRepository().getBranch());
            
            PushCommand pushCommand = git.push();
            pushCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(getOnlineRepositoryURL(), npw));
            pushCommand.setProgressMonitor(monitor);
            return pushCommand.call();
        }
    }
    
    @Override
    public PullResult pullFromRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            // Ensure we are tracking the current branch
            setTrackedBranch(git.getRepository(), git.getRepository().getBranch());
            
            PullCommand pullCommand = git.pull();
            pullCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(getOnlineRepositoryURL(), npw));
            pullCommand.setRebase(false); // Merge, not rebase
            pullCommand.setProgressMonitor(monitor);
            return pullCommand.call();
        }
    }

    @Override
    public RemoteConfig setRemote(String URL) throws IOException, GitAPIException, URISyntaxException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            RemoteConfig config;
            
            // Remove existing remote
            config = git.remoteRemove().setRemoteName(IRepositoryConstants.ORIGIN).call();
            
            // Add new one
            if(StringUtils.isSetAfterTrim(URL)) {
                config = git.remoteAdd().setName(ORIGIN).setUri(new URIish(URL)).call();
            }
            
            return config;
        }
    }
    
    @Override
    public void resetToRef(String ref) throws IOException, GitAPIException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            // Reset
            ResetCommand resetCommand = git.reset();
            resetCommand.setRef(ref);
            resetCommand.setMode(ResetType.HARD);
            resetCommand.call();
            
            // Clean extra files
            CleanCommand cleanCommand = git.clean();
            cleanCommand.setCleanDirectories(true);
            cleanCommand.call();
        }
    }

    @Override
    public boolean isHeadAndRemoteSame() throws IOException {
        // TODO: Possibly replace this with the version from coArchi 1 using BranchStatus and BranchInfo
        try(Repository repository = Git.open(getLocalRepositoryFolder()).getRepository()) {
            Ref onlineRef = repository.findRef(ORIGIN + "/" + repository.getBranch());
            Ref localRef = repository.findRef(HEAD);
            
            // In case of missing ref return false
            if(onlineRef == null || localRef == null) {
                return false;
            }
            
            try(RevWalk revWalk = new RevWalk(repository)) {
                RevCommit onlineCommit = revWalk.parseCommit(onlineRef.getObjectId());
                RevCommit localLatestCommit = revWalk.parseCommit(localRef.getObjectId());
                revWalk.dispose();
                return onlineCommit.equals(localLatestCommit);
            }
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
        return new File(getLocalRepositoryFolder(), MODEL_FILENAME);
    }
    
    @Override
    public String getOnlineRepositoryURL() throws IOException, GitAPIException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            List<RemoteConfig> remotes = git.remoteList().call();
            if(!remotes.isEmpty()) {
                List<URIish> uris = remotes.get(0).getURIs();
                if(!uris.isEmpty()) {
                    return uris.get(0).toASCIIString();
                }
            }
            return null;
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
    public void saveUserDetails(String name, String email) throws IOException {
        // Get global user details from .gitconfig for comparison
        PersonIdent global = new PersonIdent("", "");
        
        try {
            global = RepoUtils.getGitConfigUserDetails();
        }
        catch(ConfigInvalidException ex) {
            logger.warning("Could not get user details!");
            ex.printStackTrace();
        }
        
        // Save to local config
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            StoredConfig config = git.getRepository().getConfig();
            
            // If global name == local name or blank then unset
            if(!StringUtils.isSet(name) || global.getName().equals(name)) {
                config.unset(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME);
            }
            // Set
            else {
                config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, name);
            }
            
            // If global email == local email or blank then unset
            if(!StringUtils.isSet(email) || global.getEmailAddress().equals(email)) {
                config.unset(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL);
            }
            else {
                config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, email);
            }

            config.save();
        }
    }

    @Override
    public void extractCommit(RevCommit commit, File folder) throws IOException {
        try(Repository repository = Git.open(getLocalRepositoryFolder()).getRepository()) {
            // Walk the tree and extract the contents of the commit
            try(TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);

                while(treeWalk.next()) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);
                    
                    File file = new File(folder, treeWalk.getPathString());
                    file.getParentFile().mkdirs();
                    
                    try(FileOutputStream out = new FileOutputStream(file)) {
                        loader.copyTo(out);
                    }
                }
            }
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

        // Set ignore case on Mac/Windows
        if(!PlatformUtils.isLinux()) {
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
}

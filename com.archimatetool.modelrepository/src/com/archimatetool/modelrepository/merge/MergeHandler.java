/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.merge;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryConstants;

/**
 * Handle merging of branches
 * 
 * A lot of this code is subject to change!
 * 
 * @author Phillip Beauvoir
 */
public class MergeHandler {

    private static Logger logger = Logger.getLogger(MergeHandler.class.getName());
    
    // Set true for development
    private static boolean USE_3WAY_MERGE = false;
    
    private static MergeHandler instance = new MergeHandler();
    
    public static MergeHandler getInstance() {
        return instance;
    }
    
    public enum MergeHandlerResult {
        MERGED_OK,
        ALREADY_UP_TO_DATE,
        MERGED_WITH_CONFLICTS_RESOLVED,
        CANCELLED,
        MERGED_WITH_MODEL_CORRUPT
    }
    
    private MergeHandler() {
    }
    
    /**
     * Merge a branch into current branch.
     * The branch can be local or remote as a result of a Fetch
     */
    public MergeHandlerResult merge(IArchiRepository repo, BranchInfo branchToMerge) throws IOException, GitAPIException {
        if(USE_3WAY_MERGE) {
            return do3WayMerge(repo, branchToMerge);
        }
        
        return doMerge(repo, branchToMerge);
    }
    
    /**
     * This is placeholder code so we can at least work with coArchi 2 until we implement 3-way merge.
     * We do a git merge and offer some limited options.
     */
    private MergeHandlerResult doMerge(IArchiRepository repo, BranchInfo branchToMerge) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
            Git git = utils.getGit();
            
            String currentBranchName = git.getRepository().getBranch();
            logger.info(NLS.bind("Merging {0} into {1}", branchToMerge.getShortName(), currentBranchName)); //$NON-NLS-1$
            
            // Do the merge
            MergeResult mergeResult = git.merge()
                    .include(branchToMerge.getRef())
                    .setCommit(false) // Don't commit the merge until we've checked the model
                    .setFastForward(FastForwardMode.FF)
                    .setStrategy(MergeStrategy.RESOLVE)
                    .setSquash(false)
                    .call();
            
            // Get the merge status
            MergeStatus mergeStatus = mergeResult.getMergeStatus();
            
            // Already up to date
            if(mergeStatus == MergeStatus.ALREADY_UP_TO_DATE) {
                logger.info("Merge is already up to date, nothing to do."); //$NON-NLS-1$
                return MergeHandlerResult.ALREADY_UP_TO_DATE;
            }

            // Conflicting - take ours or theirs
            if(mergeStatus == MergeStatus.CONFLICTING) {
                // Conflicting files
                for(String path : mergeResult.getConflicts().keySet()) {
                    logger.warning("Conflicting file: " + path); //$NON-NLS-1$
                }
                
                int response = MessageDialog.open(MessageDialog.QUESTION,
                        null,
                        "Merge Branch", //$NON-NLS-1$
                        "Bummer, there's a conflict. What do you want from life?", //$NON-NLS-1$
                        SWT.NONE,
                        "My stuff", //$NON-NLS-1$
                        "Their stuff", //$NON-NLS-1$
                        "Cancel"); //$NON-NLS-1$

                // Cancel
                if(response == -1 || response == 2) {
                    // Reset and clear
                    utils.resetToRef(Constants.HEAD);
                    return MergeHandlerResult.CANCELLED;
                }
                
                // Take ours or theirs
                checkout(git, response == 0 ? Stage.OURS : Stage.THEIRS, new ArrayList<>(mergeResult.getConflicts().keySet()));

                // Commit
                commitMergedChanges(utils, NLS.bind("Merge branch {0} ''{1}'' into ''{2}'' with conflicts solved", //$NON-NLS-1$
                        new Object[] { branchToMerge.isRemote() ? "remote" : "",  branchToMerge.getShortName(), currentBranchName} ));  //$NON-NLS-1$//$NON-NLS-2$
                
                return MergeHandlerResult.MERGED_WITH_CONFLICTS_RESOLVED;
            }
            
            // Successful git merge, but is it a successful logical merge?
            if(!isModelIntegral(repo)) {
                int response = MessageDialog.open(MessageDialog.QUESTION,
                        null,
                        "Merge Branch", //$NON-NLS-1$
                        "Bummer, the model is corrupt. What do you want to do?", //$NON-NLS-1$
                        SWT.NONE,
                        "Commit anyway, I'll sort it out", //$NON-NLS-1$
                        "Abort the merge"); //$NON-NLS-1$
                
                // Cancel / Abort
                if(response == -1 || response == 1) {
                    // Reset and clear
                    utils.resetToRef(Constants.HEAD);
                    return MergeHandlerResult.CANCELLED;
                }
                
                commitMergedChanges(utils, NLS.bind("Merge {0} branch ''{1}'' into ''{2}'' - MODEL CORRUPT!", //$NON-NLS-1$
                        new Object[] { branchToMerge.isRemote() ? "remote" : "",  branchToMerge.getShortName(), currentBranchName} ));  //$NON-NLS-1$//$NON-NLS-2$
                
                return MergeHandlerResult.MERGED_WITH_MODEL_CORRUPT;
            }
            
            // Commit any changes from a successful merge
            commitMergedChanges(utils, NLS.bind("Merge {0} branch ''{1}'' into ''{2}''", //$NON-NLS-1$
                    new Object[] { branchToMerge.isRemote() ? "remote" : "",  branchToMerge.getShortName(), currentBranchName} ));  //$NON-NLS-1$//$NON-NLS-2$
        }
        
        return MergeHandlerResult.MERGED_OK;
    }
    
    /**
     * Try to load the model after a merge.
     * If an exception is thrown we'll assume that the model is not integral
     * This is temporary until we implement proper checking by actually loading the model even if broken
     */
    private boolean isModelIntegral(IArchiRepository repo) {
        try {
            IEditorModelManager.INSTANCE.load(repo.getModelFile());
        }
        catch(IOException ex) {
            return false;
        }

        return true;
    }

    /**
     * Commit any merged changes, if any
     */
    private boolean commitMergedChanges(GitUtils utils, String message) throws GitAPIException {
        if(utils.hasChangesToCommit()) {
            // Set "amend" has to be false after a merge conflict or else the commit will be orphaned.
            // I'm not really clear about the consequences of setting it to true, there may be an advantage
            utils.commitChanges(message, false);
            return true;
        }
        
        return false;
    }

    /**
     * Check out conflicting files either from us or them
     */
    private void checkout(Git git, Stage stage, List<String> paths) throws GitAPIException {
        CheckoutCommand checkoutCommand = git.checkout();
        checkoutCommand.setStage(stage);
        checkoutCommand.addPaths(paths);
        checkoutCommand.call();
    }
    
    
    
    // ===================================================================================================
    // The following code is for the future.
    // Instead of merging and handling the merge result we will load 3 models - ours, theirs and the common ancestor.
    // Then we will show changes and resolve them before committing and merging.
    // ===================================================================================================
    
    /**
     * Compare 3 models - ours, theirs and base
     */
    private MergeHandlerResult do3WayMerge(IArchiRepository repo, BranchInfo branchToMerge) throws IOException {
        IArchimateModel ourModel = null;
        IArchimateModel theirModel = null;
        IArchimateModel baseModel = null;
        
        // Load the three models...
        try(GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
            ourModel = loadModel(utils, Constants.HEAD);
            theirModel = loadModel(utils, branchToMerge.getFullName());
            baseModel = loadBaseModel(utils, branchToMerge.getFullName());
        }
        
        System.out.println(ourModel);
        System.out.println(theirModel);
        System.out.println(baseModel);
        
        // Now draw the rest of the owl...
        // https://knowyourmeme.com/memes/how-to-draw-an-owl
        
        return MergeHandlerResult.CANCELLED;
    }
    
    /**
     * Load the model at the base commit (common ancestor between HEAD and the branch to merge)
     */
    private IArchimateModel loadBaseModel(GitUtils utils, String revStr) throws IOException {
        RevCommit mergeBase = utils.getBaseCommit(Constants.HEAD, revStr);
        return mergeBase != null ? loadModel(utils, mergeBase.getName()) : null;
    }
    
    /**
     * Load a model from a revStr
     * revStr could be "HEAD" or "refs/remotes/origin/main", or a SHA-1 - same as for Repository#resolve()
     */
    private IArchimateModel loadModel(GitUtils utils, String revStr) throws IOException {
        File tempFolder = Files.createTempDirectory("archi-").toFile(); //$NON-NLS-1$
        
        try {
            utils.extractCommit(revStr, tempFolder);
            
            // Load it
            File modelFile = new File(tempFolder, IRepositoryConstants.MODEL_FILENAME);
            return modelFile.exists() ? IEditorModelManager.INSTANCE.load(modelFile) : null;
        }
        finally {
            FileUtils.deleteFolder(tempFolder);
        }
    }
}

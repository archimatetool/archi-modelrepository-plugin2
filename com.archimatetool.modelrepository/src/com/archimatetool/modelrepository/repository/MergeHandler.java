/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger.Registry;
import org.eclipse.emf.compare.merge.IMerger.RegistryImpl;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.model.ModelChecker;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;

import static com.google.common.base.Predicates.and;
import static com.google.common.base.Predicates.not;
import static org.eclipse.emf.compare.utils.EMFComparePredicates.fromSide;
import static org.eclipse.emf.compare.utils.EMFComparePredicates.hasConflict;


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
    private boolean USE_3WAY_MERGE = true;
    
    private static MergeHandler instance = new MergeHandler();
    
    public static MergeHandler getInstance() {
        return instance;
    }
    
    public enum MergeHandlerResult {
        MERGED_OK,
        ALREADY_UP_TO_DATE,
        MERGED_WITH_CONFLICTS_RESOLVED,
        CANCELLED
    }
    
    private MergeHandler() {
    }
    
    /**
     * Merge a branch into current branch.
     * The branch can be local or remote as a result of a Fetch
     */
    public MergeHandlerResult merge(IArchiRepository repo, BranchInfo branchToMerge) throws IOException, GitAPIException {
        logger.info("Merging " + branchToMerge.getFullName()); //$NON-NLS-1$
        
        try(GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
            // Do the merge
            MergeResult mergeResult = utils.merge()
                    .include(branchToMerge.getRef())
                    .setCommit(false) // Don't commit the merge until we've checked the model
                    .setFastForward(FastForwardMode.NO_FF) // Don't FF because we still need to check the model
                    .setStrategy(MergeStrategy.RECURSIVE)  // This strategy is used in JGit's PullCommand
                    .setSquash(false)
                    .call();
            
            // Get the merge status
            MergeStatus mergeStatus = mergeResult.getMergeStatus();
            
            // Already up to date
            if(mergeStatus == MergeStatus.ALREADY_UP_TO_DATE) {
                logger.info("Merge up to date"); //$NON-NLS-1$
                return MergeHandlerResult.ALREADY_UP_TO_DATE;
            }
            
            // Conflicting or model corrupt (successful git merge but model broken)
            if(mergeStatus == MergeStatus.CONFLICTING || !isModelIntegral(repo.getModelFile())) {
                logger.info("Conflicting merge"); //$NON-NLS-1$
                return USE_3WAY_MERGE ? handle3WayMerge(utils, branchToMerge) : handleConflictingMerge(utils, branchToMerge);
            }

            // Successful git merge and the model is OK!

            // If FF merge is possible (head is reachable from the branch to merge) just move HEAD to the target branch ref
            if(utils.isMergedInto(Constants.HEAD, branchToMerge.getFullName())) {
                logger.info("Doing a FastForward merge"); //$NON-NLS-1$
                utils.resetToRef(branchToMerge.getFullName());
            }
            // Else commit the merge if we have something to commit
            else if(mergeStatus == MergeStatus.MERGED_NOT_COMMITTED) {
                commitChanges(utils, "Merge{0}branch ''{1}'' into ''{2}''", branchToMerge); //$NON-NLS-1$
            }
        }

        return MergeHandlerResult.MERGED_OK;
    }
    
    /**
     * This is placeholder code.
     * It means we can at least work with the code until we implement 3-way merge.
     * We offer to cancel the merge or take our or their branch
     */
    private MergeHandlerResult handleConflictingMerge(GitUtils utils, BranchInfo branchToMerge) throws IOException, GitAPIException {
        int response = MessageDialog.open(MessageDialog.QUESTION,
                null,
                "Merge Branch", //$NON-NLS-1$
                "There's a conflict. What do you want from life?", //$NON-NLS-1$
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
        
        // Check out either the current or the other branch
        utils.checkout()
             .setAllPaths(true)
             .setStartPoint(response == 0 ? utils.getCurrentLocalBranchName() : branchToMerge.getFullName())
             .call();

        commitChanges(utils, "Merge{0}branch ''{1}'' into ''{2}'' with conflicts resolved", branchToMerge); //$NON-NLS-1$
        
        return MergeHandlerResult.MERGED_WITH_CONFLICTS_RESOLVED;
    }
    
    /**
     * The following code is for the future.
     * Instead of merging we will load 3 models - ours, theirs and the common ancestor.
     * Then we will show changes and resolve them before merging and committing
     */
    private MergeHandlerResult handle3WayMerge(GitUtils utils, BranchInfo branchToMerge) throws IOException, GitAPIException {
        // Reset to HEAD
        utils.resetToRef(Constants.HEAD);
        
        // Load the three models...
        IArchimateModel ourModel = loadModel(utils, Constants.HEAD);
        // Or just load the file in the working dir?
        //ourModel = IEditorModelManager.INSTANCE.loadModel(repo.getModelFile());
        IArchimateModel theirModel = loadModel(utils, branchToMerge.getFullName());
        IArchimateModel baseModel = loadBaseModel(utils, branchToMerge.getFullName());
        
        // POC EMF Compare...
        
        IComparisonScope scope = new DefaultComparisonScope(ourModel, theirModel, baseModel);
        
        Comparison comparison = EMFCompare.builder().build().compare(scope);
        List<Diff> differences = comparison.getDifferences();

        Registry mergerRegistry = RegistryImpl.createStandaloneInstance();
        //IBatchMerger merger = new BatchMerger(mergerRegistry);
        
        // Merge non conflicting changes coming from LEFT
        (new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.LEFT), not(hasConflict(ConflictKind.REAL))))).copyAllLeftToRight(differences, new BasicMonitor());
        
        // Merge non conflicting changes coming from RIGHT
        (new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.RIGHT), not(hasConflict(ConflictKind.REAL))))).copyAllRightToLeft(differences, new BasicMonitor());
		
        // Merge conflicts
        //(new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.RIGHT), hasConflict(ConflictKind.REAL)))).copyAllRightToLeft(differences, new BasicMonitor());
        (new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.LEFT), hasConflict(ConflictKind.REAL)))).copyAllLeftToRight(differences, new BasicMonitor());
        
        // If OK, save the model
        if(isModelIntegral(ourModel)) {
            ourModel.setFile(new File(utils.getRepository().getWorkTree(), RepoConstants.MODEL_FILENAME));
            IEditorModelManager.INSTANCE.saveModel(ourModel);
            commitChanges(utils, "Merge{0}branch ''{1}'' into ''{2}'' with conflicts resolved", branchToMerge); //$NON-NLS-1$
            System.out.println("Model was merged"); //$NON-NLS-1$
            return MergeHandlerResult.MERGED_WITH_CONFLICTS_RESOLVED;
        }
        
        System.out.println("Model was not integral"); //$NON-NLS-1$
        
        return MergeHandlerResult.CANCELLED;
    }
    
    /**
     * Commit any changes from a merge
     */
    private void commitChanges(GitUtils utils, String message, BranchInfo branchToMerge) throws IOException, GitAPIException {
        String fullMessage = NLS.bind(message,
                new Object[] { branchToMerge.isRemote() ? " remote " : " ",  //$NON-NLS-1$ //$NON-NLS-2$
                        branchToMerge.getShortName(), utils.getCurrentLocalBranchName()} );
        
        logger.info("Committing merge " + fullMessage); //$NON-NLS-1$
        utils.commitChanges(fullMessage, false);
    }
    
    /**
     * Try to load the model after a merge.
     * @return false if an exception is thrown when loading, an image is missing, or the ModelChecker fails
     */
    private boolean isModelIntegral(File modelFile) {
        try {
            IArchimateModel model = IEditorModelManager.INSTANCE.load(modelFile);
            return isModelIntegral(model);
        }
        catch(IOException ex) {
            return false;
        }
    }
    
    /**
     * Check the model integroty after a merge
     * @return false if an image is missing, or the ModelChecker fails
     */
    private boolean isModelIntegral(IArchimateModel model) {
        // Check that all referenced images are present (they might have deleted image while we are still using it)
        IArchiveManager archiveManager = (IArchiveManager)model.getAdapter(IArchiveManager.class);
        for(String imagePath : archiveManager.getImagePaths()) {
            if(archiveManager.getBytesFromEntry(imagePath) == null) {
                return false;
            }
        }
        
        return new ModelChecker(model).checkAll();
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
            utils.extractCommit(revStr, tempFolder, false);
            
            // Load it
            File modelFile = new File(tempFolder, RepoConstants.MODEL_FILENAME);
            return modelFile.exists() ? IEditorModelManager.INSTANCE.load(modelFile) : null;
        }
        finally {
            FileUtils.deleteFolder(tempFolder);
        }
    }
}

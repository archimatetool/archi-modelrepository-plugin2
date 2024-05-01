/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import static com.google.common.base.Predicates.and;
import static com.google.common.base.Predicates.not;
import static org.eclipse.emf.compare.utils.EMFComparePredicates.fromSide;
import static org.eclipse.emf.compare.utils.EMFComparePredicates.hasConflict;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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


/**
 * Handle merging of branches
 *  
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class MergeHandler {

    private static Logger logger = Logger.getLogger(MergeHandler.class.getName());
    
    private static MergeHandler instance = new MergeHandler();
    
    // Allow Fast-Forward merges if possible
    private static boolean ALLOW_FF_MERGE = true;
    
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
            // If a FF merge is possible (head is reachable from the branch to merge) just move HEAD to the target branch ref
            if(ALLOW_FF_MERGE && utils.isMergedInto(Constants.HEAD, branchToMerge.getFullName())) {
                logger.info("Doing a FastForward merge");
                utils.resetToRef(branchToMerge.getFullName());
                return MergeHandlerResult.MERGED_OK;
            }

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
                logger.info("Merge up to date");
                return MergeHandlerResult.ALREADY_UP_TO_DATE;
            }
            
            // 3 way merge
            return handle3WayMerge(utils, branchToMerge);
        }
    }
    
    /**
     * We load 3 models - ours, theirs and the common ancestor.
     * Then we will show any conflicts and resolve them before merging and committing
     */
    @SuppressWarnings("deprecation")
    private MergeHandlerResult handle3WayMerge(GitUtils utils, BranchInfo branchToMerge) throws IOException, GitAPIException {
        logger.info("Handling 3Way merge...");

        // Load the three models...
        IArchimateModel ourModel = loadModel(utils, Constants.HEAD);
        IArchimateModel theirModel = loadModel(utils, branchToMerge.getFullName());
        IArchimateModel baseModel = loadBaseModel(utils, branchToMerge.getFullName());
        
        IComparisonScope scope = new DefaultComparisonScope(ourModel, theirModel, baseModel);
        Comparison comparison = EMFCompare.builder().build().compare(scope);
        List<Diff> differences = comparison.getDifferences();
        
        // Get the Registry
        Registry mergerRegistry = RegistryImpl.createStandaloneInstance();
        
        // Merge non conflicting changes coming from LEFT
        new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.LEFT), not(hasConflict(ConflictKind.REAL)))).copyAllLeftToRight(differences, new BasicMonitor());
        
        // Merge non conflicting changes coming from RIGHT
        new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.RIGHT), not(hasConflict(ConflictKind.REAL)))).copyAllRightToLeft(differences, new BasicMonitor());
		
        // Merge conflicts
        //(new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.RIGHT), hasConflict(ConflictKind.REAL)))).copyAllRightToLeft(differences, new BasicMonitor());
        new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.LEFT), hasConflict(ConflictKind.REAL))).copyAllLeftToRight(differences, new BasicMonitor());
        
        // Fix any missing images (should we get them from the baseModel?)
        fixMissingImages(ourModel, theirModel);
        
        /*
         * If the result is a non-integral model then return cancelled
         * TODO: Show and resolve conflicts
         */
        if(!isModelIntegral(ourModel)) {
            System.err.println("Model was not integral");
            logger.warning("Model was not integral");
            return MergeHandlerResult.CANCELLED;
        }
        
        // If OK, save the model
        ourModel.setFile(new File(utils.getRepository().getWorkTree(), RepoConstants.MODEL_FILENAME));
        saveModel(ourModel);
        
        // Commit the merge
        commitChanges(utils, "Merge{0}branch ''{1}'' into ''{2}''", branchToMerge);
        
        logger.info("Merge succesful!");
        
        // Return
        return MergeHandlerResult.MERGED_OK;
    }
    
    /**
     * Save the model
     */
    private void saveModel(IArchimateModel model) throws IOException {
        // This has overheads - model check, creating a backup, setting model version, notifications
        // IEditorModelManager.INSTANCE.saveModel(model);
        IArchiveManager archiveManager = (IArchiveManager)model.getAdapter(IArchiveManager.class);
        archiveManager.saveModel();
    }
    
    /**
     * Commit any changes from a merge
     */
    private void commitChanges(GitUtils utils, String message, BranchInfo branchToMerge) throws IOException, GitAPIException {
        String fullMessage = NLS.bind(message,
                new Object[] { branchToMerge.isRemote() ? " remote " : " ",
                        branchToMerge.getShortName(), utils.getCurrentLocalBranchName()} );
        
        logger.info("Committing merge " + fullMessage);
        utils.commitChanges(fullMessage, false);
    }
    
    /**
     * Try to load the model after a merge.
     * @return false if an exception is thrown when loading, an image is missing, or the ModelChecker fails
     */
    @SuppressWarnings("unused")
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
     * Check the model integrity after a merge
     * @return false if an image is missing, or the ModelChecker fails
     */
    private boolean isModelIntegral(IArchimateModel model) {
        // Check that all referenced images are present
        if(!getMissingImagePaths(model).isEmpty()) {
            return false;
        }
        
        // Now pass it to the ModelChecker
        return new ModelChecker(model).checkAll();
    }
    
    /**
     * If our model contains missing images, get them from the other model
     */
    private void fixMissingImages(IArchimateModel ourModel, IArchimateModel otherModel) throws IOException {
        Set<String> missingPaths = getMissingImagePaths(ourModel);
        if(missingPaths.isEmpty()) {
            return;
        }
        
        logger.info("Restoring missing images...");
        
        IArchiveManager ourArchiveManager = (IArchiveManager)ourModel.getAdapter(IArchiveManager.class);
        IArchiveManager otherArchiveManager = (IArchiveManager)otherModel.getAdapter(IArchiveManager.class);
        
        for(String imagePath : missingPaths) {
            byte[] bytes = otherArchiveManager.getBytesFromEntry(imagePath);
            if(bytes != null) {
                logger.info("Restoring missing image: " + imagePath);
                ourArchiveManager.addByteContentEntry(imagePath, bytes);
            }
            else {
                logger.warning("Could not get image: " + imagePath);
            }
        }
    }
    
    /**
     * Check for any missing image paths.
     * They might have deleted an image but we are still using it, or we might have deleted it but they were using it
     */
    private Set<String> getMissingImagePaths(IArchimateModel model) {
        Set<String> missingPaths = new HashSet<>();
        
        IArchiveManager archiveManager = (IArchiveManager)model.getAdapter(IArchiveManager.class);
        for(String imagePath : archiveManager.getImagePaths()) {
            if(archiveManager.getBytesFromEntry(imagePath) == null) {
                missingPaths.add(imagePath);
            }
        }
        
        return missingPaths;
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
        File tempFolder = Files.createTempDirectory("archi-").toFile();
        
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
    
    
    /**
     * This is placeholder code. TODO: remove this.
     * It means we can at least work with the code until we implement 3-way merge.
     * We offer to cancel the merge or take our or their branch
     */
    @SuppressWarnings("unused")
    private MergeHandlerResult handleConflictingMerge(GitUtils utils, BranchInfo branchToMerge) throws IOException, GitAPIException {
        int response = MessageDialog.open(MessageDialog.QUESTION,
                null,
                "Merge Branch",
                "There's a conflict. What do you want from life?",
                SWT.NONE,
                "My stuff",
                "Their stuff",
                "Cancel");

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

        commitChanges(utils, "Merge{0}branch ''{1}'' into ''{2}'' with conflicts resolved", branchToMerge);
        
        return MergeHandlerResult.MERGED_WITH_CONFLICTS_RESOLVED;
    }
}

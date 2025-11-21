/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.merge;

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
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.merge.IMerger.RegistryImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.model.ModelChecker;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;


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
        
        // If the branch to merge is at HEAD there's nothing to merge
        if(branchToMerge.isRefAtHead()) {
            return MergeHandlerResult.ALREADY_UP_TO_DATE;
        }
        
        try(GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
            // If a FF merge is possible (head is reachable from the branch to merge) just move HEAD to the target branch ref
            if(ALLOW_FF_MERGE && utils.isMergedInto(RepoConstants.HEAD, branchToMerge.getFullName())) {
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
        IArchimateModel ourModel = loadModel(utils, RepoConstants.HEAD);
        if(ourModel == null) {
            throw new IOException("Our model was null.");
        }

        IArchimateModel theirModel = loadModel(utils, branchToMerge.getFullName());
        if(theirModel == null) {
            throw new IOException("Their model was null.");
        }
        
        IArchimateModel baseModel = loadBaseModel(utils, branchToMerge.getFullName());
        if(baseModel == null) {
            throw new IOException("Base model was null.");
        }
        
        // Make copies of our models so we can retrieve objects from the originals before they are merged
        IArchimateModel ourModelCopy = copyModel(ourModel);
        IArchimateModel theirModelCopy = copyModel(theirModel);
        
        // Create Comparison and get Diffs
        List<Diff> differences = MergeFactory.createComparison(ourModel, theirModel, baseModel).getDifferences();
        
        // Create a Merger Registry
        IMerger.Registry mergerRegistry = RegistryImpl.createStandaloneInstance();
        
        // Merge non conflicting changes coming from LEFT
        new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.LEFT), not(hasConflict(ConflictKind.REAL)))).copyAllLeftToRight(differences, new BasicMonitor());
        
        // Merge non conflicting changes coming from RIGHT
        new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.RIGHT), not(hasConflict(ConflictKind.REAL)))).copyAllRightToLeft(differences, new BasicMonitor());
		
        // Merge conflicts
        //new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.RIGHT), hasConflict(ConflictKind.REAL))).copyAllRightToLeft(differences, new BasicMonitor());
        new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.LEFT), hasConflict(ConflictKind.REAL))).copyAllLeftToRight(differences, new BasicMonitor());
        
        // Fix any missing images
        fixMissingImages(ourModel, theirModelCopy);
        fixMissingImages(theirModel, ourModelCopy);
        
        /*
         * If the result is a non-integral model then ask the user to use ours or theirs
         * TODO: Show and resolve conflicts
         */
        if(!isModelIntegral(ourModel)) {
            // Reset and clear for now
            logger.warning("Model was not integral");
            return handleConflictingMerge(utils, branchToMerge);
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
                        branchToMerge.getShortName(), utils.getCurrentLocalBranchName().orElse("null")} );
        
        logger.info("Committing merge " + fullMessage);
        utils.commitChangesWithManifest(fullMessage, false);
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
     * They might have deleted an image but we are still using it, or we might have deleted it but they were using it
     */
    private void fixMissingImages(IArchimateModel model, IArchimateModel otherModel) throws IOException {
        Set<String> missingPaths = getMissingImagePaths(model);
        if(missingPaths.isEmpty()) {
            return;
        }
        
        IArchiveManager archiveManager = (IArchiveManager)model.getAdapter(IArchiveManager.class);
        IArchiveManager otherArchiveManager = (IArchiveManager)otherModel.getAdapter(IArchiveManager.class);
        
        for(String imagePath : missingPaths) {
            byte[] bytes = otherArchiveManager.getBytesFromEntry(imagePath);
            if(bytes != null) {
                logger.info("Restoring missing image: " + imagePath);
                archiveManager.addByteContentEntry(imagePath, bytes);
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
        RevCommit mergeBase = utils.getBaseCommit(RepoConstants.HEAD, revStr);
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
     * Copy a model and add a cloned ArchiveManager
     */
    private IArchimateModel copyModel(IArchimateModel model) {
        IArchimateModel copy = EcoreUtil.copy(model);
        copy.setAdapter(IArchiveManager.class, ((IArchiveManager)model.getAdapter(IArchiveManager.class)).clone(copy));
        return copy;
    }
    
    /**
     * This is placeholder code. TODO: remove this.
     * It means we can at least work with the code until we manage conflicts,
     * We offer to cancel the merge or take ours or theirs branch
     */
    private MergeHandlerResult handleConflictingMerge(GitUtils utils, BranchInfo branchToMerge) throws IOException, GitAPIException {
        int response = MessageDialog.open(MessageDialog.QUESTION,
                null,
                "Merge Branch",
                "There's a conflict. What do you want to do?",
                SWT.NONE,
                "Take Mine",
                "Take Theirs",
                "Cancel");

        // Cancel
        if(response == -1 || response == 2) {
            // Reset and clear
            logger.info("User cancelled merge conflict. Resetting to HEAD.");
            utils.resetToRef(RepoConstants.HEAD);
            return MergeHandlerResult.CANCELLED;
        }
        
        // Check out either the current or the other branch
        utils.checkout()
             .setAllPaths(true)
             .setStartPoint(response == 0 ? utils.getCurrentLocalBranchName().orElse(null) : branchToMerge.getFullName())
             .call();

        commitChanges(utils, "Merge{0}branch ''{1}'' into ''{2}'' with conflicts resolved", branchToMerge);
        
        return MergeHandlerResult.MERGED_WITH_CONFLICTS_RESOLVED;
    }
}

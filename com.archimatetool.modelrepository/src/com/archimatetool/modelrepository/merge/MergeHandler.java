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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.merge.IMerger.RegistryImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.model.ModelChecker;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IRunnable;
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
    
    // Allow Fast-Forward merges if possible
    private static boolean ALLOW_FF_MERGE = true;
    
    // Methods of Merging
    private static enum MergeMethod {
        APPLY_ALL,                   // The old method
        APPLY_NONCONFLICTING         // Method suggested by Copilot
    }
    
    private static MergeMethod MERGE_METHOD = MergeMethod.APPLY_ALL;
    
    public enum MergeHandlerResult {
        MERGED_OK,
        ALREADY_UP_TO_DATE,
        MERGED_WITH_CONFLICTS_RESOLVED,
        CANCELLED
    }

    private IWorkbenchWindow workbenchWindow;
    
    public MergeHandler(IWorkbenchWindow workbenchWindow) {
        this.workbenchWindow = workbenchWindow;
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
        catch(Exception ex) {
            // If any exception occurs while the repo is in a merging state we need to reset to HEAD to clear the merge state
            try(GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
                logger.info("Resetting to HEAD due to exception."); //$NON-NLS-1$
                utils.resetToRef(RepoConstants.HEAD);
            }
            // throw orginal exception
            if(ex instanceof IOException || ex instanceof GitAPIException) {
                throw ex;
            }
            // else wrap the exception and re-throw
            throw new IOException(ex); // throw this exception
        }
    }
    
    /**
     * We load 3 models - ours, theirs and the common ancestor.
     * Then we will show any conflicts and resolve them before merging and committing
     */
    private MergeHandlerResult handle3WayMerge(GitUtils utils, BranchInfo branchToMerge) throws IOException, GitAPIException {
        logger.info("Handling 3Way merge...");
        
        ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        AtomicReference<IArchimateModel> ourModelRef = new AtomicReference<>();
        AtomicReference<IArchimateModel> theirModelRef = new AtomicReference<>();
        AtomicReference<IArchimateModel> baseModelRef = new AtomicReference<>();

        // Load the three models...
        try {
            IRunnable.run(progressDialog, true, false, monitor -> {
                monitor.beginTask("Extracting models...", IProgressMonitor.UNKNOWN);
                
                ourModelRef.set(loadModel(utils, RepoConstants.HEAD));
                if(ourModelRef.get() == null) {
                    throw new IOException("Our model was null.");
                }
                
                theirModelRef.set(loadModel(utils,branchToMerge.getFullName()));
                if(theirModelRef.get() == null) {
                    throw new IOException("Their model was null.");
                }
                
                baseModelRef.set(loadBaseModel(utils, branchToMerge.getFullName()));
                if(baseModelRef.get() == null) {
                    throw new IOException("Base model was null.");
                }
            });
        }
        catch(Exception ex) {
            throw new IOException(ex);
        }

        IArchimateModel ourModel = ourModelRef.get();
        IArchimateModel theirModel = theirModelRef.get();
        IArchimateModel baseModel = baseModelRef.get();
        
        // Make copies of our models so we can retrieve objects from the originals before they are merged
        IArchimateModel ourModelCopy = copyModel(ourModel);
        IArchimateModel theirModelCopy = copyModel(theirModel);

        // Create a Merger Registry
        IMerger.Registry mergerRegistry = RegistryImpl.createStandaloneInstance();
        
        if(MERGE_METHOD == MergeMethod.APPLY_ALL) {
            try {
                IRunnable.run(progressDialog, true, false, monitor -> {
                    monitor.beginTask("Merging...", IProgressMonitor.UNKNOWN);
                    List<Diff> differences = MergeFactory.createComparison(ourModel, theirModel, baseModel).getDifferences();
                    
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
                });
            }
            catch(Exception ex) {
                throw new IOException(ex);
            }
        }
        
        if(MERGE_METHOD == MergeMethod.APPLY_NONCONFLICTING) {
            AtomicReference<Comparison> comparison = new AtomicReference<>();
            
            try {
                IRunnable.run(progressDialog, true, false, monitor -> {
                    monitor.beginTask("Merging...", IProgressMonitor.UNKNOWN);
                    
                    // Notice that left and right are swapped here
                    comparison.set(MergeFactory.createComparison(theirModel, ourModel, baseModel));
                    List<Diff> differences = comparison.get().getDifferences();
                    
                    // Apply non-conflicting incoming changes (theirs -> ours)
                    new BatchMerger(mergerRegistry, and(fromSide(DifferenceSource.LEFT), not(hasConflict(ConflictKind.REAL)))).copyAllLeftToRight(differences, new BasicMonitor());

                    // Fix any missing images
                    fixMissingImages(ourModel, theirModelCopy);
                    fixMissingImages(theirModel, ourModelCopy);
                });
            }
            catch(Exception ex) {
                throw new IOException(ex);
            }
            
            // Do not auto-apply real conflicts. Let the conflict handler run if any conflicts exist.
            if(!comparison.get().getConflicts().isEmpty()) {
                logger.warning("Found " + comparison.get().getConflicts().size() + " conflicts");
                return handleConflictingMerge(progressDialog, utils, branchToMerge);
            }
        }
        
        /*
         * If the result is a non-integral model then ask the user to use ours or theirs
         * TODO: Show and resolve conflicts
         */
        if(!isModelIntegral(ourModel)) {
            // Reset and clear for now
            logger.warning("Model was not integral");
            return handleConflictingMerge(progressDialog, utils, branchToMerge);
        }
        
        // If OK, save the model
        ourModel.setFile(new File(utils.getRepository().getWorkTree(), RepoConstants.MODEL_FILENAME));
        saveModel(ourModel);
        
        // Commit the merge
        commitChanges(progressDialog, utils, "Merge{0}branch ''{1}'' into ''{2}''", branchToMerge);
        
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
    private void commitChanges(ProgressMonitorDialog progressDialog, GitUtils utils, String message, BranchInfo branchToMerge) throws IOException {
        String fullMessage = NLS.bind(message,
                new Object[] { branchToMerge.isRemote() ? " remote " : " ",
                        branchToMerge.getShortName(), utils.getCurrentLocalBranchName().orElse("null")} );
        
        try {
            IRunnable.run(progressDialog, true, false, monitor -> {
                monitor.beginTask("Committing...", IProgressMonitor.UNKNOWN);
                logger.info("Committing merge " + fullMessage);
                utils.commitChangesWithManifest(fullMessage, false);
            });
        }
        catch(Exception ex) {
            throw new IOException(ex);
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
     * They might have deleted an image but we are still using it, or we might have deleted it but they were using it
     */
    private void fixMissingImages(IArchimateModel model, IArchimateModel otherModel) {
        Set<String> missingPaths = getMissingImagePaths(model);
        if(missingPaths.isEmpty()) {
            return;
        }
        
        IArchiveManager archiveManager = (IArchiveManager)model.getAdapter(IArchiveManager.class);
        IArchiveManager otherArchiveManager = (IArchiveManager)otherModel.getAdapter(IArchiveManager.class);
        
        for(String imagePath : missingPaths) {
            byte[] bytes = otherArchiveManager.getBytesFromEntry(imagePath);
            if(bytes != null) {
                try {
                    logger.info("Restoring missing image: " + imagePath);
                    archiveManager.addByteContentEntry(imagePath, bytes);
                }
                catch(IOException ex) {
                    // Don't fail beacause of an image that might be in a format unsupported by this version of Archi
                    ex.printStackTrace();
                    logger.log(Level.SEVERE, "Could not load image: " + imagePath, ex); //$NON-NLS-1$
                }
            }
            else {
                logger.warning("Could not load image, bytes were null: " + imagePath);
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
    private MergeHandlerResult handleConflictingMerge(ProgressMonitorDialog progressDialog, GitUtils utils, BranchInfo branchToMerge) throws IOException, GitAPIException {
        int response = MessageDialog.open(MessageDialog.QUESTION,
                workbenchWindow.getShell(),
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

        commitChanges(progressDialog, utils, "Merge{0}branch ''{1}'' into ''{2}'' with conflicts resolved", branchToMerge);
        
        return MergeHandlerResult.MERGED_WITH_CONFLICTS_RESOLVED;
    }
}

/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Merge a Branch
 */
public class MergeBranchAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(MergeBranchAction.class.getName());
    
    private static final int MERGE_STATUS_CANCELLED = -1;
    private static final int MERGE_STATUS_MERGED_OK = 0;
    private static final int MERGE_STATUS_MERGED_WITH_CONFLICTS_RESOLVED = 1;
    private static final int MERGE_STATUS_ALREADY_UP_TO_DATE = 2;

    private BranchInfo fBranchInfo;
	
    public MergeBranchAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_MERGE));
        setText(Messages.MergeBranchAction_0);
        setToolTipText(Messages.MergeBranchAction_0);
    }

    public void setBranch(BranchInfo branchInfo) {
        fBranchInfo = branchInfo;
        setEnabled(shouldBeEnabled());
    }
    
    @Override
    public void run() {
        if(!shouldBeEnabled()) {
            setEnabled(false);
            return;
        }

        // Ask user to merge online, local or cancel
        int response = MessageDialog.open(MessageDialog.QUESTION,
                fWindow.getShell(),
                Messages.MergeBranchAction_1,
                Messages.MergeBranchAction_2,
                SWT.NONE,
                Messages.MergeBranchAction_3,
                Messages.MergeBranchAction_4,
                Messages.MergeBranchAction_5);
        
        // Cancel
        if(response == -1 || response == 2) {
            return;
        }

        // Online merge
        if(response == 0) {
            doOnlineMerge(fBranchInfo);
        }
        // Local merge
        else {
            doLocalMerge(fBranchInfo);
        }
    }
    
    /**
     * Local Merge
     */
    private void doLocalMerge(BranchInfo branchToMerge) {
        // Model is open and needs saving
        IArchimateModel model = getRepository().getModel();
        if(model != null && IEditorModelManager.INSTANCE.isModelDirty(model)) {
            try {
                if(askToSaveModel(model) == SWT.CANCEL) {
                    return;
                }
            }
            catch(IOException ex) {
                logger.log(Level.SEVERE, "Save", ex); //$NON-NLS-1$
                return;
            }
        }

        // There are uncommitted changes
        try {
            if(getRepository().hasChangesToCommit()) {
                int response = openYesNoCancelDialog(Messages.MergeBranchAction_1, Messages.MergeBranchAction_6);
                // Cancel / Yes
                if(response == SWT.CANCEL || (response == SWT.YES && !commitChanges())) { // Commit dialog
                    // Commit cancelled
                    return;
                }
                // No. Discard changes by resetting to HEAD before merging
                else if(response == SWT.NO) {
                    logger.info("Resetting to HEAD"); //$NON-NLS-1$
                    getRepository().resetToRef(Constants.HEAD);
                }
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Commit Changes", ex); //$NON-NLS-1$
            ex.printStackTrace();
            closeModel(false); // Safety precaution
            return;
        }

        logger.info("Starting Local Merge of " + branchToMerge.getShortName()); //$NON-NLS-1$

        int result = 0;
        
        // Do the merge
        try {
            result = merge(branchToMerge);
        }
        catch(IOException | GitAPIException ex) {
            closeModel(false);
            logger.log(Level.SEVERE, "Merge", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.MergeBranchAction_1, ex);
        }
        
        // User cancelled
        if(result == MERGE_STATUS_CANCELLED) {
            return;
        }
        
        // Already up to date
        if(result == MERGE_STATUS_ALREADY_UP_TO_DATE) {
            MessageDialog.openInformation(fWindow.getShell(),  Messages.MergeBranchAction_1, Messages.MergeBranchAction_8);
            return;
        }
        
        // Notify
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
        
        // Close and open model last
        OpenModelState modelState = closeModel(false);
        restoreModel(modelState);
    }
    
    /**
     * Online Merge
     */
    private void doOnlineMerge(BranchInfo branchToMerge) {
        MessageDialog.openInformation(fWindow.getShell(), "Online Merge", "Not implemented yet!"); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /**
     * Merge the given branch into the current branch
     */
    private int merge(BranchInfo branchToMerge) throws IOException, GitAPIException {
        try(GitUtils utils = GitUtils.open(getRepository().getWorkingFolder())) {
            Git git = utils.getGit();
            
            String currentBranchName = git.getRepository().getBranch();
            String mergeMessage = NLS.bind(Messages.MergeBranchAction_7, branchToMerge.getShortName(), currentBranchName);
            logger.info(NLS.bind("Merging {0} into {1}", branchToMerge.getShortName(), currentBranchName)); //$NON-NLS-1$
            
            // Do the merge
            MergeResult mergeResult = git.merge()
                    .include(branchToMerge.getRef())
                    .setCommit(true)
                    .setFastForward(FastForwardMode.FF)
                    .setStrategy(MergeStrategy.RESOLVE)
                    .setSquash(false)
                    .setMessage(mergeMessage)
                    .call();
            
            // Get the merge status
            MergeStatus mergeStatus = mergeResult.getMergeStatus();
            
            // Already up to date
            if(mergeStatus == MergeStatus.ALREADY_UP_TO_DATE) {
                logger.info("Merge is already up to date, nothing to do."); //$NON-NLS-1$
                return MERGE_STATUS_ALREADY_UP_TO_DATE;
            }

            // Conflict
            if(mergeStatus == MergeStatus.CONFLICTING) {
                // Conflicting files
                for(String path : mergeResult.getConflicts().keySet()) {
                    logger.warning("Conflicting file: " + path); //$NON-NLS-1$
                }
                
                // The following is temporary code.
                // We can take ours, theirs or cancel
                // TODO: Handle conflicts in a dialog and show logical diff
                
                int response = MessageDialog.open(MessageDialog.QUESTION,
                        fWindow.getShell(),
                        "Merge Branch", //$NON-NLS-1$
                        "Bummer, there's a conflict. What do you want from life?", //$NON-NLS-1$
                        SWT.NONE,
                        "My stuff", //$NON-NLS-1$
                        "Their stuff", //$NON-NLS-1$
                        "Cancel"); //$NON-NLS-1$

                // Cancel
                if(response == -1 || response == 2) {
                    // Reset and clear
                    git.reset().setMode(ResetType.HARD).call();
                    return MERGE_STATUS_CANCELLED;
                }
                
                // Take ours or theirs
                checkout(git, response == 0 ? Stage.OURS : Stage.THEIRS, new ArrayList<>(mergeResult.getConflicts().keySet()));
                
                // Commit
                if(utils.hasChangesToCommit()) {
                    mergeMessage = NLS.bind("Merge branch ''{0}'' into ''{1}'' with conflicts solved", branchToMerge.getShortName(), currentBranchName); //$NON-NLS-1$
                    
                    // IMPORTANT!!! "amend" has to be false after a merge conflict or else the commit will be orphaned
                    utils.commitChanges(mergeMessage, false);
                }
                
                return MERGE_STATUS_MERGED_WITH_CONFLICTS_RESOLVED;
            }
        }
        
        return MERGE_STATUS_MERGED_OK;
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
    
    @Override
    protected boolean shouldBeEnabled() {
        boolean branchRefIsSameAsCurrentRef = true;
        
        if(fBranchInfo != null) {
            try(GitUtils utils = GitUtils.open(getRepository().getWorkingFolder())) {
                branchRefIsSameAsCurrentRef = utils.isRefAtHead(fBranchInfo.getRef());
            }
            catch(IOException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, "Get Ref", ex); //$NON-NLS-1$
            }
        }
        
        return fBranchInfo != null
                && fBranchInfo.isLocal()          // Has to be local
                && !branchRefIsSameAsCurrentRef   // Not same ref
                && super.shouldBeEnabled();
    }
}

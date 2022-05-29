/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.merge.MergeHandler;
import com.archimatetool.modelrepository.merge.MergeHandler.MergeHandlerResult;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Merge a Branch
 */
public class MergeBranchAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(MergeBranchAction.class.getName());
    
    private BranchInfo fBranchInfo;
	
    public MergeBranchAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_MERGE));
        setText(Messages.MergeBranchAction_0);
        setToolTipText(Messages.MergeBranchAction_0);
    }

    public void setBranch(BranchInfo branchInfo) {
        if(fBranchInfo != branchInfo) {
            fBranchInfo = branchInfo;
            setEnabled(shouldBeEnabled());
        }
    }

    @Override
    public void setRepository(IArchiRepository repository) {
        fBranchInfo = null;
        super.setRepository(repository);
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
        // Check if the model is open and needs saving
        if(!checkModelNeedsSaving()) {
            return;
        }

        // Check if there are uncommitted changes
        if(!checkIfCommitNeeded()) {
            return;
        }

        logger.info("Starting Local Merge of " + branchToMerge.getShortName()); //$NON-NLS-1$

        MergeHandlerResult mergeHandlerResult = MergeHandlerResult.MERGED_OK;
        
        // Do the merge
        try {
            mergeHandlerResult = MergeHandler.getInstance().merge(getRepository(), branchToMerge);
        }
        catch(IOException | GitAPIException ex) {
            closeModel(false); // Safety precaution
            logger.log(Level.SEVERE, "Merge", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.MergeBranchAction_1, ex);
        }
        
        // User cancelled
        if(mergeHandlerResult == MergeHandlerResult.CANCELLED) {
            return;
        }
        
        // Already up to date
        if(mergeHandlerResult == MergeHandlerResult.ALREADY_UP_TO_DATE) {
            MessageDialog.openInformation(fWindow.getShell(),  Messages.MergeBranchAction_1, Messages.MergeBranchAction_7);
            return;
        }
        
        // Notify
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
        
        // Close and open model
        OpenModelState modelState = closeModel(false);
        restoreModel(modelState);
    }
    
    /**
     * Online Merge
     */
    private void doOnlineMerge(BranchInfo branchToMerge) {
        MessageDialog.openInformation(fWindow.getShell(), "Online Merge", "Not implemented yet!"); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        if(fBranchInfo == null) {
            return false;
        }
        
        boolean branchRefIsSameAsCurrentRef = true;
        
        try(GitUtils utils = GitUtils.open(getRepository().getWorkingFolder())) {
            branchRefIsSameAsCurrentRef = utils.isRefAtHead(fBranchInfo.getRef());
        }
        catch(IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Get Ref", ex); //$NON-NLS-1$
        }
        
        return fBranchInfo.isLocal()             // Has to be local
               && !branchRefIsSameAsCurrentRef   // Not same ref
               && super.shouldBeEnabled();
    }
}

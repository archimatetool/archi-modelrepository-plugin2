/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.MergeHandler;
import com.archimatetool.modelrepository.repository.MergeHandler.MergeHandlerResult;

/**
 * Merge a Branch
 */
public class MergeBranchWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(MergeBranchWorkflow.class.getName());
    
    private BranchInfo currentBranchInfo;
	
    public MergeBranchWorkflow(IWorkbenchWindow workbenchWindow, BranchInfo currentBranchInfo) {
        super(workbenchWindow, new ArchiRepository(currentBranchInfo.getWorkingFolder()));
        this.currentBranchInfo = currentBranchInfo;
    }

    @Override
    public void run() {
        // TODO - enable local and online merge
        boolean doLocal = true;
        
        if(doLocal) {
            if(MessageDialog.openConfirm(workbenchWindow.getShell(),
                    Messages.MergeBranchWorkflow_0,
                    NLS.bind(Messages.MergeBranchWorkflow_6, currentBranchInfo.getShortName()))) {
                doLocalMerge(currentBranchInfo);
            }
            
            return;
        }
        
        // Ask user to merge online, local or cancel
        int response = MessageDialog.open(MessageDialog.QUESTION,
                workbenchWindow.getShell(),
                Messages.MergeBranchWorkflow_0,
                Messages.MergeBranchWorkflow_1,
                SWT.NONE,
                Messages.MergeBranchWorkflow_3,
                Messages.MergeBranchWorkflow_2,
                Messages.MergeBranchWorkflow_4);
        
        // Local merge
        if(response == 0) {
            doLocalMerge(currentBranchInfo);
        }
        // Online merge
        else if(response == 1) {
            doOnlineMerge(currentBranchInfo);
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
            mergeHandlerResult = MergeHandler.getInstance().merge(archiRepository, branchToMerge);
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Merge", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.MergeBranchWorkflow_0, ex);
        }
        
        // User cancelled
        if(mergeHandlerResult == MergeHandlerResult.CANCELLED) {
            return;
        }
        
        // Already up to date
        if(mergeHandlerResult == MergeHandlerResult.ALREADY_UP_TO_DATE) {
            MessageDialog.openInformation(workbenchWindow.getShell(),  Messages.MergeBranchWorkflow_0, Messages.MergeBranchWorkflow_5);
            return;
        }
        
        // Notify
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
        
        // Close and open model
        closeAndRestoreModel();
    }
    
    /**
     * Online Merge
     */
    private void doOnlineMerge(BranchInfo branchToMerge) {
        MessageDialog.openInformation(workbenchWindow.getShell(), "Online Merge", "Not implemented yet!"); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    @Override
    public boolean canRun() {
        return currentBranchInfo != null
                && currentBranchInfo.isLocal()          // Has to be local
                && !currentBranchInfo.isRefAtHead()     // Not same ref as HEAD ref (ie. at the same commit)
                && super.canRun();
    }
}

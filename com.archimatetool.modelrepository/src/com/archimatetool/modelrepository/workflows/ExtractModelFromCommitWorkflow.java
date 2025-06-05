/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Checkout a commit and extract the .archimate file from it
 */
public class ExtractModelFromCommitWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(ExtractModelFromCommitWorkflow.class.getName());
    
    private RevCommit revCommit;
    
    public ExtractModelFromCommitWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository, RevCommit revCommit) {
        super(workbenchWindow, archiRepository);
        this.revCommit = revCommit;
    }
    
    @Override
    public void run() {
        logger.info("Extracting model from a commit..."); //$NON-NLS-1$
        
        if(!MessageDialog.openConfirm(workbenchWindow.getShell(),
                Messages.ExtractModelFromCommitWorkflow_0,
                Messages.ExtractModelFromCommitWorkflow_1)) {
            return;
        }
        
        try {
            // Create a temporary folder to extract to
            File tempFolder = Files.createTempDirectory("archi-").toFile(); //$NON-NLS-1$
            
            // Extract the commit
            logger.info("Extracting the commit"); //$NON-NLS-1$
            archiRepository.extractCommit(revCommit, tempFolder, false);
            
            // If the model file exists, open it
            File modelFile = new File(tempFolder, RepoConstants.MODEL_FILENAME);
            if(modelFile.exists()) {
                IArchimateModel model = IEditorModelManager.INSTANCE.openModel(modelFile);
                if(model != null) {
                    // Set file to null
                    model.setFile(null);
                    // Add part of the commit hash to name
                    model.setName(model.getName() + " (" + revCommit.getName().substring(0, 8) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            else {
                logger.warning("Model does not exist!"); //$NON-NLS-1$
                displayErrorDialog(Messages.ExtractModelFromCommitWorkflow_0, Messages.ExtractModelFromCommitWorkflow_2);
            }
            
            FileUtils.deleteFolder(tempFolder);
        }
        catch(IOException ex) {
            logger.log(Level.SEVERE, "Extract Model", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.ExtractModelFromCommitWorkflow_0, ex);
        }
    }
    
    @Override
    public boolean canRun() {
        return revCommit != null && super.canRun();
    }
}

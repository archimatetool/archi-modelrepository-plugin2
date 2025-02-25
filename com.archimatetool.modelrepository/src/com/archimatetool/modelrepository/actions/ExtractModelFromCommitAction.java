/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Checkout a commit and extract the .archimate file from it
 */
public class ExtractModelFromCommitAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(ExtractModelFromCommitAction.class.getName());
    
    private RevCommit fCommit;
    
    public ExtractModelFromCommitAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IArchiImages.ImageFactory.getImageDescriptor(IArchiImages.ICON_MODELS));
        setText(Messages.ExtractModelFromCommitAction_0);
        setToolTipText(Messages.ExtractModelFromCommitAction_0);
    }
    
    @Override
    public void setRepository(IArchiRepository repository) {
        fCommit = null;
        super.setRepository(repository);
    }

    public void setCommit(RevCommit commit) {
        if(fCommit != commit) {
            fCommit = commit;
            setEnabled(shouldBeEnabled());
        }
    }
    
    @Override
    public void run() {
        if(!shouldBeEnabled()) {
            setEnabled(false);
            return;
        }

        logger.info("Extracting model from a commit..."); //$NON-NLS-1$
        
        if(!MessageDialog.openConfirm(fWindow.getShell(),
                Messages.ExtractModelFromCommitAction_1,
                Messages.ExtractModelFromCommitAction_2)) {
            return;
        }
        
        try {
            // Create a temporary folder to extract to
            File tempFolder = Files.createTempDirectory("archi-").toFile(); //$NON-NLS-1$
            
            // Extract the commit
            logger.info("Extracting the commit"); //$NON-NLS-1$
            getRepository().extractCommit(fCommit, tempFolder, false);
            
            // If the model file exists, open it
            File modelFile = new File(tempFolder, RepoConstants.MODEL_FILENAME);
            if(modelFile.exists()) {
                IArchimateModel model = IEditorModelManager.INSTANCE.openModel(modelFile);
                if(model != null) {
                    // Set file to null
                    model.setFile(null);
                    // Add part of the commit hash to name
                    model.setName(model.getName() + " (" + fCommit.getName().substring(0, 8) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            else {
                logger.warning("Model does not exist!"); //$NON-NLS-1$
            }
            
            FileUtils.deleteFolder(tempFolder);
        }
        catch(IOException ex) {
            logger.log(Level.SEVERE, "Extract Model", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.ExtractModelFromCommitAction_1, ex);
        }
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return fCommit != null && super.shouldBeEnabled();
    }
}

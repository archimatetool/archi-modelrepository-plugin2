/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.dialogs.AddTagDialog;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Add Tag Workflow
 * 
 * @author Phillip Beauvoir
 */
public class AddTagWorkflow extends AbstractRepositoryWorkflow {

    private static Logger logger = Logger.getLogger(AddTagWorkflow.class.getName());
    
    // The RevCommit of the point where the new tag will be added
    private RevCommit revCommit;

    public AddTagWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository repository, RevCommit revCommit) {
        super(workbenchWindow, repository);
        this.revCommit = revCommit;
    }

    @Override
    public void run() {
        AddTagDialog dialog = new AddTagDialog(workbenchWindow.getShell());
        int retVal = dialog.open();
        String tagName = dialog.getTagName();
        String tagMessage = dialog.getTagMessage();
        
        if(retVal == IDialogConstants.CANCEL_ID || !StringUtils.isSet(tagName)) {
            return;
        }
        
        logger.info("Adding tag..."); //$NON-NLS-1$
        
        try(Git git = Git.open(archiRepository.getWorkingFolder())) {
            String fullName = RepoConstants.R_TAGS + tagName;
            
            // If the tag exists show error
            if(git.getRepository().exactRef(fullName) != null) {
                logger.info("Tag already exists"); //$NON-NLS-1$
                MessageDialog.openError(workbenchWindow.getShell(),
                        Messages.AddTagWorkflow_0,
                        NLS.bind(Messages.AddTagWorkflow_1, tagName));
                return;
            }
            
            // Create the tag
            logger.info("Creating tag: " + tagName); //$NON-NLS-1$
            git.tag()
               .setObjectId(revCommit)
               .setName(tagName)
               .setMessage(tagMessage)
               .call();

            // Notify listeners
            notifyChangeListeners(IRepositoryListener.TAGS_CHANGED);
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Add Tag", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.AddTagWorkflow_0, ex);
        }
    }

    @Override
    public boolean canRun() {
        return revCommit != null && super.canRun();
    }
}

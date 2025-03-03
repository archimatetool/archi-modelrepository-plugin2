/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.workflows.CommitModelWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Commit Model Action
 * @author Phillip Beauvoir
 */
public class CommitModelAction extends AbstractRepositoryAction {
    
    public CommitModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_COMMIT));
        setActionDefinitionId("com.archimatetool.modelrepository.command.commitModel"); //$NON-NLS-1$
        setText(Messages.CommitModelAction_0);
        setToolTipText(getText());
    }

    @Override
    public void run() {
        IRepositoryWorkflow workflow = new CommitModelWorkflow(workbenchWindow, archiRepository);
        if(workflow.canRun()) {
            workflow.run();
        }
    }
}

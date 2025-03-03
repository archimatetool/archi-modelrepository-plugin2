/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;
import com.archimatetool.modelrepository.workflows.PushModelWorkflow;

/**
 * Push Model Action ("Publish")
 * 
 * @author Phillip Beauvoir
 */
public class PushModelAction extends AbstractRepositoryAction {
    
    public PushModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_PUSH));
        setActionDefinitionId("com.archimatetool.modelrepository.command.pushModel"); //$NON-NLS-1$
        setText(Messages.PushModelAction_0);
        setToolTipText(Messages.PushModelAction_0);
    }

    @Override
    public void run() {
        IRepositoryWorkflow workflow = new PushModelWorkflow(workbenchWindow, archiRepository);
        if(workflow.canRun()) {
            workflow.run();
        }
    }
}

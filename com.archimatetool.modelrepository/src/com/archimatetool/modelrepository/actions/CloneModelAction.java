/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.workflows.CloneModelWorkflow;

/**
 * Clone a model
 */
public class CloneModelAction extends AbstractRepositoryAction {
    
    public CloneModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_CLONE));
        setActionDefinitionId("com.archimatetool.modelrepository.command.cloneModel"); //$NON-NLS-1$
        setText(Messages.CloneModelAction_0);
        setToolTipText(getText());
    }

    @Override
    public void run() {
        new CloneModelWorkflow(workbenchWindow, null).run();
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return true;
    }
}

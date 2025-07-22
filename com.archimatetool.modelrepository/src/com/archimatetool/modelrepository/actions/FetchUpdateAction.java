/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;
import com.archimatetool.modelrepository.workflows.FetchUpdateWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Fetch on all repositories in the workspace
 */
public class FetchUpdateAction extends AbstractRepositoryAction {
    
    public FetchUpdateAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_REFRESH));
        setActionDefinitionId("com.archimatetool.modelrepository.command.fetchUpdate"); //$NON-NLS-1$
        setText(Messages.FetchUpdateAction_0);
        setToolTipText(getText());
        update();
    }
    
    @Override
    public void run() {
        IRepositoryWorkflow workflow = new FetchUpdateWorkflow(workbenchWindow);
        if(workflow.canRun()) {
            workflow.run();
        }
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return !RepositoryTreeModel.getInstance().getAllChildRepositoryRefs().isEmpty();
    }
}

/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.TagInfo;
import com.archimatetool.modelrepository.workflows.DeleteTagsWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Delete Tags Action
 * 
 * @author Phillip Beauvoir
 */
public class DeleteTagsAction extends AbstractRepositoryAction {
    
    private IRepositoryWorkflow workflow;
    
    public DeleteTagsAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_DELETE));
        setText(Messages.DeleteTagsAction_0);
        setToolTipText(getText());
    }

    public void setTags(IArchiRepository archiRepository, TagInfo... tagInfos) {
        workflow = tagInfos != null && tagInfos.length != 0 ? new DeleteTagsWorkflow(workbenchWindow, archiRepository, tagInfos) : null;
        update();
    }

    @Override
    public void run() {
        if(shouldBeEnabled()) {
            workflow.run();
        }
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return workflow != null && workflow.canRun();
    }
}

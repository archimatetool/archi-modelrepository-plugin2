/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;


/**
 * Abstract Model handler manageing enabled state
 * 
 * @author Phillip Beauvoir
 */
public abstract class AbstractModelHandler extends AbstractHandler {
    
    protected IArchimateModel getActiveArchimateModel() {
        IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService().getActivePart();
        return part != null ? part.getAdapter(IArchimateModel.class) : null;
    }

    protected IArchiRepository getActiveArchiRepository() {
        if(RepoUtils.isModelInArchiRepository(getActiveArchimateModel())) {
            return new ArchiRepository(RepoUtils.getLocalRepositoryFolderForModel(getActiveArchimateModel()));
        }
        
        IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService().getActivePart();
        return part != null ? part.getAdapter(IArchiRepository.class) : null;
    }

    @Override
    public boolean isEnabled() {
        return RepoUtils.isModelInArchiRepository(getActiveArchimateModel());
    }
}

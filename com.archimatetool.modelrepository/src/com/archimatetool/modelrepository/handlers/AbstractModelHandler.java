/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.handlers;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;


/**
 * Abstract Model handler managing enabled state
 * 
 * @author Phillip Beauvoir
 */
public abstract class AbstractModelHandler extends AbstractHandler {
    
    protected IArchimateModel getActiveArchimateModel() {
        IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService().getActivePart();
        return part != null ? part.getAdapter(IArchimateModel.class) : null;
    }

    protected IArchiRepository getActiveArchiRepository() {
        // Check active model first if in a repo folder
        File folder = RepoUtils.getWorkingFolderForModel(getActiveArchimateModel());
        if(folder != null) {
            return new ArchiRepository(folder);
        }
        
        // Part contains IArchiRepository objects
        IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService().getActivePart();
        return part != null ? part.getAdapter(IArchiRepository.class) : null;
    }

    @Override
    public boolean isEnabled() {
        return RepoUtils.isModelInArchiRepository(getActiveArchimateModel());
    }
}

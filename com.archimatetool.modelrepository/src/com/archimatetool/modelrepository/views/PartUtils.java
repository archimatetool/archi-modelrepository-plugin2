/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views;

import java.io.File;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;
import com.archimatetool.modelrepository.views.repositories.ModelRepositoryView;

/**
 * Some Part Utils
 * 
 * @author Phillip Beauvoir
 */
public class PartUtils {

    /**
     * @return the selected IArchiRepository for a given part, or null
     */
    public static IArchiRepository getSelectedArchiRepositoryInWorkbenchPart(IWorkbenchPart part) {
        if(part == null) {
            return null;
        }
        
        // Repository is selected in part
        IArchiRepository archiRepository = part.getAdapter(IArchiRepository.class);
        if(archiRepository != null) {
            return archiRepository;
        }
        
        // Model is selected in part
        IArchimateModel model = part.getAdapter(IArchimateModel.class);
        if(model != null) {
            return getSelectedArchiRepositoryForModel(model);
        }
        
        // Repository is selected wrapped in a Repository Ref
        RepositoryRef ref = part.getAdapter(RepositoryRef.class);
        if(ref != null) {
            return ref.getArchiRepository();
        }
        
        // Fallbacks in case the selected part doesn't adapt to any of the above
        
        // ModelRepositoryView is open so get it from that part
        IViewPart modelRepositoryView = ViewManager.findViewPart(ModelRepositoryView.ID);
        if(modelRepositoryView != null) {
            return modelRepositoryView.getAdapter(IArchiRepository.class);
        }
        
        // Go through each ViewPart and see if any adapts to IArchimateModel.class
        for(IViewReference viewRef : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getViewReferences()) {
            model = viewRef.getPart(false).getAdapter(IArchimateModel.class);
            if(model != null) {
                return getSelectedArchiRepositoryForModel(model);
            }
        }
        
        return null;
    }
    
    private static IArchiRepository getSelectedArchiRepositoryForModel(IArchimateModel model) {
        File folder = RepoUtils.getWorkingFolderForModel(model); // But is it in a git repo?
        return folder != null ? new ArchiRepository(folder) : null;
    }
}

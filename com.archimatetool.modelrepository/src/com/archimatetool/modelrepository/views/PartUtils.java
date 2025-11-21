/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views;

import java.util.Optional;

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
     * @return the selected IArchiRepository in Optional for a given part, or empty Optional
     */
    public static Optional<IArchiRepository> getSelectedArchiRepositoryInWorkbenchPart(IWorkbenchPart part) {
        if(part == null) {
            return Optional.empty();
        }
        
        // Repository is selected in part
        IArchiRepository archiRepository = part.getAdapter(IArchiRepository.class);
        if(archiRepository != null) {
            return Optional.of(archiRepository);
        }
        
        // Model is selected in part
        IArchimateModel model = part.getAdapter(IArchimateModel.class);
        if(model != null) {
            return getSelectedArchiRepositoryForModel(model);
        }
        
        // Repository is selected wrapped in a Repository Ref
        RepositoryRef ref = part.getAdapter(RepositoryRef.class);
        if(ref != null) {
            return Optional.ofNullable(ref.getArchiRepository());
        }
        
        // Fallbacks in case the selected part doesn't adapt to any of the above
        
        // ModelRepositoryView is open so get it from that part
        IViewPart modelRepositoryView = ViewManager.findViewPart(ModelRepositoryView.ID);
        if(modelRepositoryView != null) {
            return Optional.ofNullable(modelRepositoryView.getAdapter(IArchiRepository.class));
        }
        
        // Go through each ViewPart and see if any adapts to IArchimateModel.class
        for(IViewReference viewRef : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getViewReferences()) {
            part = viewRef.getPart(false);
            if(part != null) {
                model = part.getAdapter(IArchimateModel.class);
                if(model != null) {
                    return getSelectedArchiRepositoryForModel(model);
                }
            }
        }
        
        return Optional.empty();
    }
    
    private static Optional<IArchiRepository> getSelectedArchiRepositoryForModel(IArchimateModel model) {
        // But is it in a git repo?
        return RepoUtils.getWorkingFolderForModel(model).map(ArchiRepository::new);
    }
}

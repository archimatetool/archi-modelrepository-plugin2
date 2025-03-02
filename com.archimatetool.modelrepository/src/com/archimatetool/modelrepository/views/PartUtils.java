/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views;

import java.io.File;

import org.eclipse.ui.IWorkbenchPart;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;

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
        if(part.getAdapter(IArchiRepository.class) instanceof IArchiRepository archiRepository) {
            return archiRepository;
        }
        
        // Model is selected in part
        if(part.getAdapter(IArchimateModel.class) instanceof IArchimateModel model) {
            File folder = RepoUtils.getWorkingFolderForModel(model); // But is it in a git repo?
            return folder != null ? new ArchiRepository(folder) : null;
        }
        
        // Repository is selected wrapped in a Repository Ref
        if(part.getAdapter(RepositoryRef.class) instanceof RepositoryRef ref) {
            return ref.getArchiRepository();
        }
        
        return null;
    }
}

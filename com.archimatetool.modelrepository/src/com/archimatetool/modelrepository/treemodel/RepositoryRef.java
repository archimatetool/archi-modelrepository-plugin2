/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.treemodel;

import org.eclipse.swt.graphics.Image;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Repository Ref
 * 
 * @author Phillip Beauvoir
 */
public class RepositoryRef implements IModelRepositoryTreeEntry {
    
    private IArchiRepository repo;
    private Group parent;
    
    public RepositoryRef(IArchiRepository repo) {
        this.repo = repo;
    }
    
    public IArchiRepository getArchiRepository() {
        return repo;
    }
    
    @Override
    public Group getParent() {
        return parent;
    }
    
    void setParent(Group parent) {
        this.parent = parent;
    }

    @Override
    public void delete() {
        if(parent != null) {
            parent.repos.remove(this);
            parent = null;
        }
    }

    @Override
    public String getName() {
        return repo.getName();
    }
    
    @Override
    public Image getImage() {
        return IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_MODEL);
    }
}
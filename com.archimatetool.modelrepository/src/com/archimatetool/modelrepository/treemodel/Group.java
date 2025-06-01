/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.treemodel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.graphics.Image;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * User Group
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class Group implements IModelRepositoryTreeEntry {
    
    private String name;
    private Group parent;
    
    protected List<Group> groups;
    protected List<RepositoryRef> repos;
    
    public Group(String name) {
        this.name = name;
        groups = new ArrayList<>();
        repos = new ArrayList<>();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public void setName(String name, boolean doSave) {
        if(name != null && !name.equals(this.name)) {
            this.name = name;
            RepositoryTreeModel.getInstance().fireListenerEvent(this);
            
            if(doSave) {
                try {
                    RepositoryTreeModel.getInstance().saveManifest();
                }
                catch(IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Add a new Repository Ref with repository
     */
    public RepositoryRef addNewRepositoryRef(IArchiRepository repository) {
        RepositoryRef ref = new RepositoryRef(repository);
        add(ref);
        
        RepositoryTreeModel.getInstance().fireListenerEvent(ref);
        
        try {
            RepositoryTreeModel.getInstance().saveManifest();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        return ref;
    }
    
    /**
     * Add a new Repository Ref with folder
     */
    public RepositoryRef addNewRepositoryRef(File repositoryFolder) {
        return addNewRepositoryRef(new ArchiRepository(repositoryFolder));
    }
    
    /**
     * Add a new Group
     * @param name
     */
    public Group addNewGroup(String name) {
        Group group = new Group(name);
        add(group);
        
        RepositoryTreeModel.getInstance().fireListenerEvent(group);
        
        try {
            RepositoryTreeModel.getInstance().saveManifest();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        return group;
    }

    public void add(IModelRepositoryTreeEntry entry) {
        if(entry == this || entry instanceof RepositoryTreeModel) {
            throw new IllegalArgumentException("Cannot add to self!");
        }
        
        // Remove from old parent
        entry.delete();
        
        if(entry instanceof Group group) {
            groups.add(group);
            group.setParent(this);
        }
        else if(entry instanceof RepositoryRef ref) {
            repos.add(ref);
            ref.setParent(this);
        }
    }
    
    @Override
    public void delete() {
        if(parent != null) {
            parent.groups.remove(this);
            parent = null;
        }
    }
    
    @Override
    public Group getParent() {
        return parent;
    }

    void setParent(Group parent) {
        if(parent == this) {
            throw new IllegalArgumentException("Cannot add to self!");
        }
        this.parent = parent;
    }
    
    public List<RepositoryRef> getRepositoryRefs() {
        return new ArrayList<>(repos);
    }
    
    public List<Group> getGroups() {
        return new ArrayList<>(groups);
    }
    
    /**
     * @return All Repository Refs in this Group and all Repository Refs in sub-groups
     */
    public List<RepositoryRef> getAllChildRepositoryRefs() {
        List<RepositoryRef> list = getRepositoryRefs();
        for(Group childGroup : getGroups()) {
            list.addAll(childGroup.getAllChildRepositoryRefs());
        }
        return list;
    }
    
    /**
     * @return All child Groups in this Group and any sub-groups
     */
    public List<Group> getAllChildGroups() {
        List<Group> list = getGroups();
        for(Group childGroup : getGroups()) {
            list.addAll(childGroup.getAllChildGroups());
        }
        return list;
    }

    /**
     * @return All immediate Repository Refs and immediate child-groups
     */
    public List<IModelRepositoryTreeEntry> getAll() {
        List<IModelRepositoryTreeEntry> list = new ArrayList<IModelRepositoryTreeEntry>();
        list.addAll(groups);
        list.addAll(repos);
        return list;
    }
    
    public void addListener(IRepositoryTreeModelListener listener) {
        RepositoryTreeModel.getInstance().addListener(listener);
    }
    
    public void removeListener(IRepositoryTreeModelListener listener) {
        RepositoryTreeModel.getInstance().removeListener(listener);
    }
    
    @Override
    public Image getImage() {
        return IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_GROUP);
    }

}
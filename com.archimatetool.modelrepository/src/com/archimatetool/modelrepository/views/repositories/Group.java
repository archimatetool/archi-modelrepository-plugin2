/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.graphics.Image;

import com.archimatetool.modelrepository.IModelRepositoryImages;

/**
 * User Group
 * 
 * @author Phillip Beauvoir
 */
public class Group implements IModelRepositoryTreeEntry {
    
    private String name;
    private Group parent;
    
    protected List<Group> groups;
    protected List<RepositoryRef> repos;
    
    public Group(String name) {
        this.name = name;
        groups = new ArrayList<Group>();
        repos = new ArrayList<RepositoryRef>();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        if(name != null && !name.equals(this.name)) {
            this.name = name;
            RepositoryTreeModel.getInstance().fireListenerEvent(this);
        }
    }
    
    public void add(IModelRepositoryTreeEntry entry) {
        // Remove from old parent
        entry.delete();
        
        if(entry instanceof Group) {
            groups.add((Group)entry);
            ((Group)entry).setParent(this);
        }
        else if(entry instanceof RepositoryRef) {
            repos.add((RepositoryRef)entry);
            ((RepositoryRef)entry).setParent(this);
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
        this.parent = parent;
    }
    
    public List<RepositoryRef> getRepositoryRefs() {
        return new ArrayList<RepositoryRef>(repos);
    }
    
    public List<Group> getGroups() {
        return new ArrayList<Group>(groups);
    }
    
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
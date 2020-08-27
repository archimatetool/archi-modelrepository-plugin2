/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import org.eclipse.jface.viewers.IFilter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.modelrepository.treemodel.Group;
import com.archimatetool.modelrepository.treemodel.IModelRepositoryTreeEntry;
import com.archimatetool.modelrepository.treemodel.IRepositoryTreeModelListener;


/**
 * Property Section for Group
 * 
 * @author Phillip Beauvoir
 */
public class GroupSection extends AbstractArchiPropertySection implements IRepositoryTreeModelListener {
    
    public static class Filter implements IFilter {
        @Override
        public boolean select(Object object) {
            return object instanceof Group;
        }
    }
    
    private UpdatingTextControl fTextNameControl;
    
    private Group group;
    
    private boolean isSetting;
    
    public GroupSection() {
    }

    @Override
    protected void createControls(Composite parent) {
        createLabel(parent, Messages.GroupSection_0, STANDARD_LABEL_WIDTH, SWT.CENTER);
        
        fTextNameControl = new UpdatingTextControl(createSingleTextControl(parent, SWT.NONE)) {
            @Override
            protected void textChanged(String newText) {
                if(newText.isEmpty()) {
                    fTextNameControl.setText(group.getName());
                }
                else {
                    isSetting = true;
                    group.setName(newText, true);
                    isSetting = false;
                }
            }
        };
    }
    
    @Override
    protected void handleSelection(IStructuredSelection selection) {
        if(selection.getFirstElement() instanceof Group) {
            Group newGroup = (Group)selection.getFirstElement();
            
            if(newGroup == group) {
                return;
            }

            if(group != null) {
                group.removeListener(this);
            }
            
            if(newGroup != null) {
                fTextNameControl.setText(newGroup.getName());
                newGroup.addListener(this);
                group = newGroup;
            }
        }
    }

    @Override
    public void treeEntryChanged(IModelRepositoryTreeEntry entry) {
        if(entry == group && group != null) {
            updatePropertiesLabel();
            if(!isSetting) {
                fTextNameControl.setText(group.getName());
            }
        }
    }
    
    @Override
    public void dispose() {
        super.dispose();
        
        if(group != null) {
            group.removeListener(this);
            group = null;
        }
    }
}

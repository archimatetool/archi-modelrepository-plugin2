/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

import java.io.IOException;

import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.widgets.TreeItem;




/**
 * Drag Drop Handler
 * 
 * @author Phillip Beauvoir
 */
public class ModelRepositoryTreeViewerDragDropHandler {

    private StructuredViewer fViewer;
    
    /**
     * Drag operations we support
     */
    private int fDragOperations = DND.DROP_MOVE | DND.DROP_COPY;

    /**
     * Drop operations we support on the tree
     */
    private int fDropOperations = DND.DROP_MOVE | DND.DROP_COPY;

    /**
     * Whether we have a valid tree selection
     */
    private boolean fIsValidTreeSelection;
    
    // Can only drag and drop local type
    Transfer[] transferTypes = new Transfer[] { LocalSelectionTransfer.getTransfer() };
    
    public ModelRepositoryTreeViewerDragDropHandler(StructuredViewer viewer) {
        fViewer = viewer;
        registerDragSupport();
        registerDropSupport();
    }
    
    /**
     * Register drag support that starts from the Tree
     */
    private void registerDragSupport() {
        fViewer.addDragSupport(fDragOperations, transferTypes, new DragSourceListener() {
            
            @Override
            public void dragFinished(DragSourceEvent event) {
                LocalSelectionTransfer.getTransfer().setSelection(null);
                fIsValidTreeSelection = false; // Reset to default
            }

            @Override
            public void dragSetData(DragSourceEvent event) {
                // For consistency set the data to the selection even though
                // the selection is provided by the LocalSelectionTransfer
                // to the drop target adapter.
                event.data = LocalSelectionTransfer.getTransfer().getSelection();
            }

            @Override
            public void dragStart(DragSourceEvent event) {
                // Drag started from the Tree
                IStructuredSelection selection = (IStructuredSelection)fViewer.getSelection();
                fIsValidTreeSelection = isValidTreeSelection(selection);

                LocalSelectionTransfer.getTransfer().setSelection(selection);
                event.doit = true;
            }
        });
    }
    
    private void registerDropSupport() {
        fViewer.addDropSupport(fDropOperations, transferTypes, new DropTargetAdapter() {
            @Override
            public void dragOperationChanged(DropTargetEvent event) {
                event.detail = getEventDetail(event);
            }

            @Override
            public void dragOver(DropTargetEvent event) {
                event.detail = getEventDetail(event);
                
                if(event.detail == DND.DROP_NONE) {
                    event.feedback = DND.FEEDBACK_NONE;
                    return;
                }
                
                event.detail |= DND.DROP_MOVE;
                event.feedback |= DND.FEEDBACK_SCROLL | DND.FEEDBACK_EXPAND;
            }

            @Override
            public void drop(DropTargetEvent event) {
                doDropOperation(event);
            }

            private int getEventDetail(DropTargetEvent event) {
                return isValidSelection(event) && isValidDropTarget(event) ? DND.DROP_MOVE : DND.DROP_NONE;
            }
            
        });
    }
    
    private boolean isValidSelection(DropTargetEvent event) {
        return fIsValidTreeSelection;
    }

    /**
     * Determine whether we have a valid selection of objects dragged from the Tree
     */
    private boolean isValidTreeSelection(IStructuredSelection selection) {
        return selection != null && !selection.isEmpty();
    }
    
    private void doDropOperation(DropTargetEvent event) {
        if(isLocalTreeDragOperation(event.currentDataType)) {
            Group parent = getTargetParent(event);
            if(parent != null) {
                IStructuredSelection selection = (IStructuredSelection)LocalSelectionTransfer.getTransfer().getSelection();
                moveTreeObjects(parent, selection.toArray());
            }
        }
    }
    
    /**
     * Move Tree Objects
     */
    private void moveTreeObjects(Group newParent, Object[] objects) {
        for(Object o : objects) {
            if(o instanceof IModelRepositoryTreeEntry) {
                newParent.add((IModelRepositoryTreeEntry)o);
            }
        }
        
        try {
            RepositoryTreeModel.getInstance().saveManifest();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        fViewer.refresh();
    }
    
    /**
     * Determine the target parent from the drop event
     */
    private Group getTargetParent(DropTargetEvent event) {
        // Dropped on blank area = root Group
        if(event.item == null) {
            return (Group)fViewer.getInput();
        }
        
        return event.item.getData() instanceof Group ? (Group)event.item.getData() : null;
    }

    /**
     * @return True if target is valid
     */
    private boolean isValidDropTarget(DropTargetEvent event) {
        // Dragging onto a Group
        Group parentGroup = getTargetParent(event);
        
        if(parentGroup != null) {
            IStructuredSelection selection = (IStructuredSelection)LocalSelectionTransfer.getTransfer().getSelection();
            for(Object object : selection.toList()) {
                if(!canDropObject(object, (TreeItem)event.item)) {
                    return false;
                }
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * Return true if object can be dropped on a target tree item
     */
    private boolean canDropObject(Object object, TreeItem targetTreeItem) {
        if(targetTreeItem == null) {  // Root tree
            return true;
        }
        
        if(object == targetTreeItem.getData()) {  // Cannot drop onto itself
            return false;
        }
        
        // If moving a Group check that target Group is not a descendant of the source folder
        while((targetTreeItem = targetTreeItem.getParentItem()) != null) {
            if(targetTreeItem.getData() == object) {
                return false;
            }
        }
        
        return true;
    }

    private boolean isLocalTreeDragOperation(TransferData dataType) {
        return LocalSelectionTransfer.getTransfer().isSupportedType(dataType);
    }
}

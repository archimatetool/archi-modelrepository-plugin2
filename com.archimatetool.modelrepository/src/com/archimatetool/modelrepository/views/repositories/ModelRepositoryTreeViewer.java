/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TreeItem;
import org.jdom2.JDOMException;

import com.archimatetool.editor.ui.ColorFactory;
import com.archimatetool.editor.ui.components.TreeTextCellEditor;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.Group;
import com.archimatetool.modelrepository.treemodel.IModelRepositoryTreeEntry;
import com.archimatetool.modelrepository.treemodel.IRepositoryTreeModelListener;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;


/**
 * Repository Tree Viewer
 */
public class ModelRepositoryTreeViewer extends TreeViewer implements IRepositoryListener, IRepositoryTreeModelListener {

    /**
     * Constructor
     */
    public ModelRepositoryTreeViewer(Composite parent) {
        super(parent, SWT.MULTI);
        
        setContentProvider(new ModelRepoTreeContentProvider());
        setLabelProvider(new ModelRepoTreeLabelProvider());
        
        RepositoryListenerManager.INSTANCE.addListener(this);
        
        // Dispose of this and clean up
        getTree().addDisposeListener(e -> {
            RepositoryListenerManager.INSTANCE.removeListener(ModelRepositoryTreeViewer.this);
            RepositoryTreeModel.getInstance().dispose();
        });
        
        // Tooltip support
        ColumnViewerToolTipSupport.enableFor(this);
        
        // Drag and Drop support
        new ModelRepositoryTreeViewerDragDropHandler(this);
        
        setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object o1, Object o2) {
                int cat1 = category(o1);
                int cat2 = category(o2);

                if(cat1 != cat2) {
                    return cat1 - cat2;
                }
                
                IModelRepositoryTreeEntry e1 = (IModelRepositoryTreeEntry)o1;
                IModelRepositoryTreeEntry e2 = (IModelRepositoryTreeEntry)o2;
                
                return e1.getName().compareToIgnoreCase(e2.getName());
            }
            
            @Override
            public int category(Object element) {
                if(element instanceof Group) {
                    return 0;
                }
                if(element instanceof RepositoryRef) {
                    return 1;
                }
                return 0;
            }
        });
        
        
        // Cell Editor
        TreeTextCellEditor cellEditor = new TreeTextCellEditor(getTree());
        setColumnProperties(new String[]{ "col1" }); //$NON-NLS-1$
        setCellEditors(new CellEditor[]{ cellEditor });

        // Edit cell programmatically, not on mouse click
        TreeViewerEditor.create(this, new ColumnViewerEditorActivationStrategy(this){
            @Override
            protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
                return event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC;
            }  
            
        }, ColumnViewerEditor.DEFAULT);

        setCellEditors(new CellEditor[]{ cellEditor });
        
        setCellModifier(new ICellModifier() {
            @Override
            public void modify(Object element, String property, Object value) {
                if(element instanceof TreeItem) {
                    Object data = ((TreeItem)element).getData();
                    if(data instanceof Group) {
                        String text = (String)value;
                        if(!text.isEmpty()) {
                            ((Group)data).setName(text, true);
                        }
                    }
                }
            }
            
            @Override
            public Object getValue(Object element, String property) {
                if(element instanceof Group) {
                    return ((Group)element).getName();
                }
                return null;
            }
            
            @Override
            public boolean canModify(Object element, String property) {
                return element instanceof Group;
            }
        });
        
        try {
            RepositoryTreeModel.getInstance().loadManifest();
        }
        catch(IOException | JDOMException ex) {
            ex.printStackTrace();
        }
        
        RepositoryTreeModel.getInstance().addListener(this);
        
        setInput(RepositoryTreeModel.getInstance());
        
        expandAll();
        
        // TODO
        // Fetch Job
        // new FetchJob(this);
    }

    protected void refreshInBackground() {
        if(!getControl().isDisposed()) {
            getControl().getDisplay().asyncExec(() -> {
                if(!getControl().isDisposed()) {
                    refresh();
                }
            });
        }
    }

    @Override
    public void repositoryChanged(String eventName, IArchiRepository repository) {
        switch(eventName) {
            case IRepositoryListener.REPOSITORY_CHANGED:
                RepositoryRef ref = RepositoryTreeModel.getInstance().findRepositoryRef(repository.getLocalRepositoryFolder());
                if(ref != null) {
                    update(ref, null);
                }
                break;
                
            default:
                refresh();
        }

    }
    
    @Override
    public void treeEntryChanged(IModelRepositoryTreeEntry entry) {
        TreePath[] expanded = getExpandedTreePaths(); // save these to restore expanded state
        refresh(entry.getParent());
        setExpandedTreePaths(expanded);
    }
    
    // ===============================================================================================
	// ===================================== Tree Model ==============================================
	// ===============================================================================================
    
    /**
     * The model for the Tree.
     */
    class ModelRepoTreeContentProvider implements ITreeContentProvider {
        
        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }
        
        @Override
        public void dispose() {
        }
        
        @Override
        public Object[] getElements(Object parent) {
            return getChildren(parent);
        }
        
        @Override
        public Object getParent(Object child) {
            if(child instanceof IModelRepositoryTreeEntry) {
                return ((IModelRepositoryTreeEntry)child).getParent();
            }
            
            return null;
        }
        
        @Override
        public Object[] getChildren(Object parent) {
            if(parent instanceof Group) {
                return ((Group)parent).getAll().toArray();
            }
            
            return new Object[0];
        }
        
        @Override
        public boolean hasChildren(Object parent) {
            if(parent instanceof Group) {
                return !((Group)parent).getAll().isEmpty();
            }
            
            return false;
        }
    }
    
    // ===============================================================================================
	// ===================================== Label Model ==============================================
	// ===============================================================================================

    class ModelRepoTreeLabelProvider extends CellLabelProvider {
        // Cache status for expensive calls
        private class StatusCache {
            boolean hasUnpushedCommits;
            boolean hasRemoteCommits;
            boolean hasLocalChanges;
            
            private StatusCache(boolean hasUnpushedCommits, boolean hasRemoteCommits, boolean hasLocalChanges) {
                this.hasUnpushedCommits = hasUnpushedCommits;
                this.hasRemoteCommits = hasRemoteCommits;
                this.hasLocalChanges = hasLocalChanges;
            }
        }
        
        private Map<IArchiRepository, StatusCache> cache = new Hashtable<IArchiRepository, StatusCache>();
        
        Image getImage(IArchiRepository repo) {
            Image image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_MODEL);
            
            StatusCache sc = cache.get(repo);
            if(sc != null) {
                if(sc.hasLocalChanges) {
                    image = IModelRepositoryImages.ImageFactory.getOverlayImage(image,
                            IModelRepositoryImages.ICON_LEFT_BALL_OVERLAY, IDecoration.BOTTOM_LEFT);
                }
                
                if(sc.hasUnpushedCommits) {
                    image = IModelRepositoryImages.ImageFactory.getOverlayImage(image,
                            IModelRepositoryImages.ICON_RIGHT_BALL_OVERLAY, IDecoration.BOTTOM_RIGHT);
                }
                
                if(sc.hasRemoteCommits) {
                    image = IModelRepositoryImages.ImageFactory.getOverlayImage(image,
                            IModelRepositoryImages.ICON_TOP_BALL_OVERLAY, IDecoration.TOP_RIGHT);
                }
            }
            
            return image;
        }
        
        String getStatusText(IArchiRepository repo) {
            String s = ""; //$NON-NLS-1$
            
            StatusCache sc = cache.get(repo);
            if(sc != null) {
                if(sc.hasLocalChanges) {
                    s += Messages.ModelRepositoryTreeViewer_0;
                }
                if(sc.hasUnpushedCommits) {
                    if(StringUtils.isSet(s)) {
                        s += " | "; //$NON-NLS-1$
                    }
                    s += Messages.ModelRepositoryTreeViewer_1;
                }
                if(sc.hasRemoteCommits) {
                    if(StringUtils.isSet(s)) {
                        s += " | "; //$NON-NLS-1$
                    }
                    s += Messages.ModelRepositoryTreeViewer_2;
                }
                if(!StringUtils.isSet(s)) {
                    s = Messages.ModelRepositoryTreeViewer_3;
                }
            }
            
            return s;
        }
        
        @Override
        public void update(ViewerCell cell) {
            // Need to clear this
            cell.setForeground(null);
            
            if(cell.getElement() instanceof RepositoryRef) {
                IArchiRepository repo = ((RepositoryRef)cell.getElement()).getArchiRepository();
                
                // Local repo was perhaps deleted
                if(!repo.getModelFile().exists()) {
                    cell.setImage(IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_MODEL));
                    cell.setText(Messages.ModelRepositoryTreeViewer_4);
                    return;
                }
                
                // Check status of current branch
                String currentLocalBranch = ""; //$NON-NLS-1$
                
                // TODO Get status, current branch etc...
                boolean hasUnpushedCommits = false;
                boolean hasRemoteCommits = false;
                boolean hasLocalChanges = false;
                
                try {
                    currentLocalBranch = repo.getCurrentLocalBranchName();
                    hasLocalChanges = repo.hasChangesToCommit();
                }
                catch(IOException | GitAPIException ex) {
                    ex.printStackTrace();
                }
                
                StatusCache sc = new StatusCache(hasUnpushedCommits, hasRemoteCommits, hasLocalChanges);
                cache.put(repo, sc);

                // Red text
                if(hasUnpushedCommits || hasRemoteCommits || hasLocalChanges) {
                    cell.setForeground(ColorFactory.get(255, 64, 0));
                }
                
                // Image
                cell.setImage(getImage(repo));

                // Repository name and current branch
                cell.setText(repo.getName() + " [" + currentLocalBranch + "]"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            
            if(cell.getElement() instanceof Group) {
                Group group = (Group)cell.getElement();
                cell.setText(group.getName());
                cell.setImage(group.getImage());
            }
        }
        
        @Override
        public String getToolTipText(Object element) {
            if(element instanceof RepositoryRef) {
                IArchiRepository repo = ((RepositoryRef)element).getArchiRepository();
                
                String s = repo.getName();
                
                String status = getStatusText(repo);
                if(StringUtils.isSet(status)) {
                    s += "\n" + status.replaceAll(" \\| ", "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                
                return s;
            }
            
            if(element instanceof Group) {
                return ((Group)element).getName();
            }
            
            return null;
        }
    }
}

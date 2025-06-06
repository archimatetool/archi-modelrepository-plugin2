/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

import java.io.IOException;
import java.text.Collator;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TreeItem;
import org.jdom2.JDOMException;

import com.archimatetool.editor.ui.components.TreeTextCellEditor;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.BranchInfo.Option;
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
@SuppressWarnings("nls")
public class ModelRepositoryTreeViewer extends TreeViewer implements IRepositoryListener, IRepositoryTreeModelListener {
    
    private static Logger logger = Logger.getLogger(ModelRepositoryTreeViewer.class.getName());
    
    // Cache status for expensive branch info calls
    private class StatusCache {
        BranchInfo branchInfo;
        boolean hasChangesToCommit;
        
        public StatusCache(BranchInfo branchInfo, boolean hasChangesToCommit) {
            this.branchInfo = branchInfo;
            this.hasChangesToCommit = hasChangesToCommit;
        }
    }
    
    private Map<IArchiRepository, StatusCache> statusCache = new Hashtable<>();

    /**
     * Constructor
     */
    public ModelRepositoryTreeViewer(Composite parent) {
        super(parent, SWT.MULTI);
        
        setContentProvider(new ModelRepoTreeContentProvider());
        setLabelProvider(new ModelRepoTreeLabelProvider());
        
        RepositoryListenerManager.getInstance().addListener(this);
        
        // Dispose of this and clean up
        getTree().addDisposeListener(e -> {
            RepositoryListenerManager.getInstance().removeListener(ModelRepositoryTreeViewer.this);
            RepositoryTreeModel.getInstance().dispose();
        });
        
        // Tooltip support
        ColumnViewerToolTipSupport.enableFor(this);
        
        // Drag and Drop support
        new ModelRepositoryTreeViewerDragDropHandler(this);
        
        setComparator(new ViewerComparator(Collator.getInstance()) {
            @Override
            public int compare(Viewer viewer, Object o1, Object o2) {
                int cat1 = category(o1);
                int cat2 = category(o2);

                if(cat1 != cat2) {
                    return cat1 - cat2;
                }
                
                IModelRepositoryTreeEntry e1 = (IModelRepositoryTreeEntry)o1;
                IModelRepositoryTreeEntry e2 = (IModelRepositoryTreeEntry)o2;
                
                return getComparator().compare(e1.getName(), e2.getName());
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
        setColumnProperties(new String[]{ "col1" });
        setCellEditors(new CellEditor[]{ cellEditor });

        // Edit cell programmatically, not on mouse click
        TreeViewerEditor.create(this, new ColumnViewerEditorActivationStrategy(this){
            @Override
            protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
                return event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC;
            }  
            
        }, ColumnViewerEditor.DEFAULT);

        setCellModifier(new ICellModifier() {
            @Override
            public void modify(Object element, String property, Object value) {
                if(element instanceof TreeItem item && item.getData() instanceof Group group) {
                    String text = (String)value;
                    if(!text.isEmpty()) {
                        group.setName(text, true);
                    }
                }
            }
            
            @Override
            public Object getValue(Object element, String property) {
                if(element instanceof Group group) {
                    return group.getName();
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
            logger.log(Level.SEVERE, "Loading Manifest", ex);
        }
        
        RepositoryTreeModel.getInstance().addListener(this);
        
        setInput(RepositoryTreeModel.getInstance());
        
        expandAll();
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
            case IRepositoryListener.MODEL_RENAMED,
                 IRepositoryListener.MODEL_SAVED,
                 IRepositoryListener.BRANCHES_CHANGED,
                 IRepositoryListener.HISTORY_CHANGED -> {
                     
                RepositoryRef ref = RepositoryTreeModel.getInstance().findRepositoryRef(repository.getWorkingFolder());
                if(ref != null) {
                    updateStatusCache(ref.getArchiRepository());
                    update(ref, null);
                    setSelection(getSelection()); // This will update the selection and the status bar
                }
            }
            
            case IRepositoryListener.REPOSITORY_DELETED -> {
                statusCache.remove(repository);
                refresh();
            }
            
            default -> {
                refresh();
            }
        }
    }
    
    @Override
    public void treeEntryChanged(IModelRepositoryTreeEntry entry) {
        resetStatusCache();
        TreePath[] expanded = getExpandedTreePaths(); // save these to restore expanded state
        refresh(entry.getParent());
        setExpandedTreePaths(expanded);
    }

    /**
     * Reset the status cache for all repositories
     */
    private void resetStatusCache() {
        statusCache = new Hashtable<>();
        
        for(RepositoryRef ref : RepositoryTreeModel.getInstance().getAllChildRepositoryRefs()) {
            updateStatusCache(ref.getArchiRepository());
        }
    }
    
    /**
     * Update the status cache for one repository
     */
    private void updateStatusCache(IArchiRepository repo) {
        try {
            BranchInfo branchInfo = BranchInfo.currentLocalBranchInfo(repo.getWorkingFolder(), Option.COMMIT_STATUS);
            if(branchInfo != null) {
                StatusCache sc = new StatusCache(branchInfo, repo.hasChangesToCommit());
                statusCache.put(repo, sc);
            }
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Status Cache", ex);
        }
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
            if(newInput != null) {
                resetStatusCache();
            }
        }
        
        @Override
        public Object[] getElements(Object parent) {
            return getChildren(parent);
        }
        
        @Override
        public Object getParent(Object child) {
            if(child instanceof IModelRepositoryTreeEntry treeEntry) {
                return treeEntry.getParent();
            }
            
            return null;
        }
        
        @Override
        public Object[] getChildren(Object parent) {
            if(parent instanceof Group group) {
                return group.getAll().toArray();
            }
            
            return new Object[0];
        }
        
        @Override
        public boolean hasChildren(Object parent) {
            if(parent instanceof Group group) {
                return !group.getAll().isEmpty();
            }
            
            return false;
        }
    }
    
    // ===============================================================================================
    // ===================================== Label Model ==============================================
    // ===============================================================================================

    class ModelRepoTreeLabelProvider extends CellLabelProvider {
        Color alertColor = new Color(255, 64, 0);
        
        Image getImage(IArchiRepository repo) {
            Image image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_MODEL);
            
            StatusCache sc = statusCache.get(repo);
            if(sc != null) {
                if(sc.hasChangesToCommit) {
                    image = IModelRepositoryImages.ImageFactory.getOverlayImage(image,
                            IModelRepositoryImages.ICON_LEFT_BALL_OVERLAY, IDecoration.BOTTOM_LEFT);
                }
                
                if(sc.branchInfo.hasUnpushedCommits()) {
                    image = IModelRepositoryImages.ImageFactory.getOverlayImage(image,
                            IModelRepositoryImages.ICON_RIGHT_BALL_OVERLAY, IDecoration.BOTTOM_RIGHT);
                }
                
                if(sc.branchInfo.hasRemoteCommits()) {
                    image = IModelRepositoryImages.ImageFactory.getOverlayImage(image,
                            IModelRepositoryImages.ICON_TOP_BALL_OVERLAY, IDecoration.TOP_RIGHT);
                }
            }
            
            return image;
        }
        
        String getStatusText(IArchiRepository repo) {
            String s = "";
            
            StatusCache sc = statusCache.get(repo);
            if(sc != null) {
                if(sc.hasChangesToCommit) {
                    s += Messages.ModelRepositoryTreeViewer_0;
                }
                if(sc.branchInfo.hasUnpushedCommits()) {
                    if(StringUtils.isSet(s)) {
                        s += " | ";
                    }
                    s += Messages.ModelRepositoryTreeViewer_1;
                }
                if(sc.branchInfo.hasRemoteCommits()) {
                    if(StringUtils.isSet(s)) {
                        s += " | ";
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
            
            if(cell.getElement() instanceof RepositoryRef ref) {
                IArchiRepository repo = ref.getArchiRepository();
                
                // Local repo was perhaps deleted or the commit doesn;t contain a model file
                if(!repo.getModelFile().exists()) {
                    cell.setImage(IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_MODEL));
                    cell.setText(Messages.ModelRepositoryTreeViewer_4);
                    return;
                }
                
                StatusCache sc = statusCache.get(repo);
                if(sc != null) {
                    // Repository name and current branch
                    cell.setText(repo.getName() + " [" + sc.branchInfo.getShortName() + "]");
                    
                    // Red text
                    if(sc.branchInfo.hasUnpushedCommits() || sc.branchInfo.hasRemoteCommits() || sc.hasChangesToCommit) {
                        cell.setForeground(alertColor);
                    }
                }
                else {
                    cell.setText(repo.getName());
                }

                // Image
                cell.setImage(getImage(repo));
            }
            else if(cell.getElement() instanceof Group group) {
                cell.setText(group.getName());
                cell.setImage(group.getImage());
            }
        }
        
        @Override
        public String getToolTipText(Object element) {
            if(element instanceof RepositoryRef ref) {
                IArchiRepository repo = ref.getArchiRepository();
                
                String s = repo.getName();
                
                String status = getStatusText(repo);
                if(StringUtils.isSet(status)) {
                    s += "\n" + status.replaceAll(" \\| ", "\n");
                }
                
                return s;
            }
            else if(element instanceof Group group) {
                return group.getName();
            }
            
            return null;
        }
    }
}

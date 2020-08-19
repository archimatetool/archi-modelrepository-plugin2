/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;


/**
 * Repository Tree Viewer
 */
public class ModelRepositoryTreeViewer extends TreeViewer implements IRepositoryListener {

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
        });
        
        ColumnViewerToolTipSupport.enableFor(this);
        
        setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                IArchiRepository r1 = (IArchiRepository)e1;
                IArchiRepository r2 = (IArchiRepository)e2;
                return r1.getName().compareToIgnoreCase(r2.getName());
            }
        });
        
        setInput(""); //$NON-NLS-1$
        
        // TODO
        
        // Refresh File System Job
        // new RefreshFilesJob(this);
        
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
            case IRepositoryListener.REPOSITORY_ADDED:
                refresh();
                setSelection(new StructuredSelection(repository));
                break;
                
            case IRepositoryListener.REPOSITORY_DELETED:
                refresh();
                break;

            default:
                refresh();
                break;
        }
    }
    
    /**
     * @return Root folder of model repos
     */
    private File getRootFolder() {
        return ModelRepositoryPlugin.INSTANCE.getUserModelRepositoryFolder();
    }
    
    /**
     * @return All repos in the file system
     */
    protected List<IArchiRepository> getRepositories(File folder) {
        // Only show top level folders that are git repos
        List<IArchiRepository> repos = new ArrayList<IArchiRepository>();
        
        if(folder.exists() && folder.isDirectory()) {
            for(File file : getRootFolder().listFiles()) {
                if(RepoUtils.isArchiGitRepository(file)) {
                    repos.add(new ArchiRepository(file));
                }
            }
        }
        
        return repos;
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
            return getChildren(getRootFolder());
        }
        
        @Override
        public Object getParent(Object child) {
            if(child instanceof File) {
                return ((File)child).getParentFile();
            }
            if(child instanceof IArchiRepository) {
                return ((IArchiRepository)child).getLocalRepositoryFolder().getParentFile();
            }
            return null;
        }
        
        @Override
        public Object[] getChildren(Object parent) {
            if(parent instanceof File) {
                return getRepositories((File)parent).toArray();
            }
            
            return new Object[0];
        }
        
        @Override
        public boolean hasChildren(Object parent) {
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
            if(cell.getElement() instanceof IArchiRepository) {
                IArchiRepository repo = (IArchiRepository)cell.getElement();
                
                // Local repo was perhaps deleted
                if(!repo.getLocalRepositoryFolder().exists()) {
                    return;
                }
                
                // Check status of current branch
                String currentLocalBranch = ""; //$NON-NLS-1$

                // TODO Get status, current branch etc...
                
                // Repository name and current branch
                cell.setText(repo.getName() + " [" + currentLocalBranch + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                
                // Image
                cell.setImage(getImage(repo));
            }
        }
        
        @Override
        public String getToolTipText(Object element) {
            if(element instanceof IArchiRepository) {
                IArchiRepository repo = (IArchiRepository)element;
                
                String s = repo.getName();
                
                String status = getStatusText(repo);
                if(StringUtils.isSet(status)) {
                    s += "\n" + status.replaceAll(" \\| ", "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                
                return s;
            }
            
            return null;
        }
    }
}

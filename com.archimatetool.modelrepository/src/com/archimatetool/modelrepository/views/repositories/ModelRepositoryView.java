/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.CloneModelAction;
import com.archimatetool.modelrepository.actions.IModelRepositoryAction;
import com.archimatetool.modelrepository.actions.ShowInHistoryAction;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.Group;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;
import com.archimatetool.modelrepository.views.repositories.ModelRepositoryTreeViewer.ModelRepoTreeLabelProvider;


/**
 * Model Repository ViewPart for managing models
 */
public class ModelRepositoryView
extends ViewPart
implements IContextProvider, ISelectionListener, ITabbedPropertySheetPageContributor {
    
    private static Logger logger = Logger.getLogger(ModelRepositoryView.class.getName());

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".modelRepositoryView"; //$NON-NLS-1$
    public static String HELP_ID = ModelRepositoryPlugin.PLUGIN_ID + ".modelRepositoryViewHelp"; //$NON-NLS-1$
    
    /**
     * The Repository Viewer
     */
    private ModelRepositoryTreeViewer fTreeViewer;
    
    /*
     * Actions
     */
    private IModelRepositoryAction fActionClone;
    private IModelRepositoryAction fActionShowInHistory;
    
    private IAction fActionOpen;
    private IAction fActionAddGroup;
    private IAction fActionAddRepository;
    private IAction fActionDelete;
    private IAction fActionRenameEntry;
    private IAction fActionSelectAll;
    private IAction fActionProperties;

    @Override
    public void createPartControl(Composite parent) {
        // Create the Tree Viewer first
        fTreeViewer = new ModelRepositoryTreeViewer(parent);
        
        makeActions();
        registerGlobalActions();
        hookContextMenu();
        makeLocalMenuActions();
        makeLocalToolBarActions();
        
        // Register us as a selection provider so that Actions can pick us up
        getSite().setSelectionProvider(getViewer());
        
        /*
         * Listen to Selections to update local Actions
         */
        getViewer().addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                updateActions(event.getSelection());
                updateStatusBar(event.getSelection());
            }
        });
        
        // Listen to workbench selections
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);
        
        // Initialise with whatever is selected in the workbench
        selectionChanged(getSite().getWorkbenchWindow().getPartService().getActivePart(),
                getSite().getWorkbenchWindow().getSelectionService().getSelection());
        
        /*
         * Listen to Double-click Action
         */
        getViewer().addDoubleClickListener((event) -> {
            Object obj = ((IStructuredSelection)event.getSelection()).getFirstElement();
            if(obj instanceof RepositoryRef) {
                IArchiRepository repo = ((RepositoryRef)obj).getArchiRepository();
                
                BusyIndicator.showWhile(Display.getCurrent(), () -> {
                    IEditorModelManager.INSTANCE.openModel(repo.getModelFile());
                });
            }
        });

        // Register Help Context
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getViewer().getControl(), HELP_ID);
    }
    
    /**
     * Make local actions
     */
    private void makeActions() {
        fActionClone = new CloneModelAction(getViewSite().getWorkbenchWindow());
        
        // Open Model
        fActionOpen = new Action(Messages.ModelRepositoryView_13) {
            @Override
            public void run() {
                Object selected = ((IStructuredSelection)getViewer().getSelection()).getFirstElement();
                if(selected instanceof RepositoryRef) {
                    BusyIndicator.showWhile(Display.getCurrent(), new Runnable() {
                        @Override
                        public void run() {
                            IEditorModelManager.INSTANCE.openModel(((RepositoryRef)selected).getArchiRepository().getModelFile());
                        }
                    });
                }
            }
        };
        
        // Add New Group
        fActionAddGroup = new Action(Messages.ModelRepositoryView_2, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_GROUP)) {
            @Override
            public void run() {
                addNewGroup();
            }
        };
        
        // Add Existing Repository
        fActionAddRepository = new Action(Messages.ModelRepositoryView_3, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_MODEL)) {
            @Override
            public void run() {
                addNewRepositoryRef();
            }
        };
        
        // Delete
        fActionDelete = new Action(Messages.ModelRepositoryView_4, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_DELETE)) {
            @Override
            public void run() {
                deleteSelected();
            }
        };
        
        // Rename
        fActionRenameEntry = new Action(Messages.ModelRepositoryView_5) {
            @Override
            public void run() {
                Object o = ((IStructuredSelection)getViewer().getSelection()).getFirstElement();
                if(o != null) {
                    getViewer().editElement(o, 0);
                }
            }
            
            @Override
            public String getActionDefinitionId() {
                return "org.eclipse.ui.edit.rename"; // Ensures key binding is displayed //$NON-NLS-1$
            }
        };
        
        // Select All
        fActionSelectAll = new Action(Messages.ModelRepositoryView_6) {
            @Override
            public void run() {
                getViewer().getTree().selectAll();
            }
            
            @Override
            public String getActionDefinitionId() {
                return "org.eclipse.ui.edit.selectAll"; // Ensures key binding is displayed //$NON-NLS-1$
            }
        };

        // Properties
        fActionProperties = new Action(Messages.ModelRepositoryView_14) {
            @Override
            public void run() {
                ViewManager.showViewPart(ViewManager.PROPERTIES_VIEW, false);
            }
            
            @Override
            public String getActionDefinitionId() {
                return IWorkbenchCommandConstants.FILE_PROPERTIES; // Ensures key binding is displayed
            }
        };
        
        // Show in History
        fActionShowInHistory = new ShowInHistoryAction(getViewSite().getWorkbenchWindow());
        fActionShowInHistory.setEnabled(false);
        
        // Register the Keybinding for actions
//        IHandlerService service = (IHandlerService)getViewSite().getService(IHandlerService.class);
//        service.activateHandler(fActionRefresh.getActionDefinitionId(), new ActionHandler(fActionRefresh));
    }
    
    private void addNewGroup() {
        NewGroupDialog dialog = new NewGroupDialog(getViewSite().getShell());
        if(dialog.open()) {
            String name = dialog.getGroupName();
            if(name != null) {
                Group parentGroup = getSelectedParentGroup();
                Group newGroup = parentGroup.addNewGroup(name);

                getViewer().expandToLevel(parentGroup, 1);
                getViewer().setSelection(new StructuredSelection(newGroup));
            }
        }
    }
    
    private void addNewRepositoryRef() {
        DirectoryDialog dialog = new DirectoryDialog(getViewSite().getShell());
        dialog.setText(Messages.ModelRepositoryView_7);
        
        String path = dialog.open();
        if(path != null) {
            File folder = new File(path);
            
            // This is an Archi Repository folder
            if(RepoUtils.isArchiGitRepository(folder)) {
                // But we already have it...
                if(RepositoryTreeModel.getInstance().hasRepositoryRef(folder)) {
                    MessageDialog.openInformation(getViewSite().getShell(),
                            Messages.ModelRepositoryView_8,
                            NLS.bind(Messages.ModelRepositoryView_9, folder));
                    return;
                }
                
                Group parentGroup = getSelectedParentGroup();
                RepositoryRef ref = parentGroup.addNewRepositoryRef(folder);
                
                getViewer().expandToLevel(parentGroup, 1);
                getViewer().setSelection(new StructuredSelection(ref));
            }
            else {
                MessageDialog.openInformation(getViewSite().getShell(),
                        Messages.ModelRepositoryView_8,
                        Messages.ModelRepositoryView_10);
            }
        }
    }
    
    private void deleteSelected() {
        if(MessageDialog.openQuestion(getViewSite().getShell(),
                Messages.ModelRepositoryView_11,
                Messages.ModelRepositoryView_12)) {
            
            Set<RepositoryRef> refsToDelete = new HashSet<RepositoryRef>();
            Set<Group> groupsToDelete = new HashSet<Group>();
            
            // Get all selected Repository Refs and Groups
            for(Object object : ((IStructuredSelection)getViewer().getSelection()).toArray()) {
                // Selected RepositoryRef
                if(object instanceof RepositoryRef) {
                    refsToDelete.add((RepositoryRef)object);
                }
                // Selected Group and its sub-groups and sub-RepositoryRefs
                if(object instanceof Group) {
                    Group group = (Group)object;
                    groupsToDelete.add(group);
                    groupsToDelete.addAll(group.getAllChildGroups());
                    refsToDelete.addAll(group.getAllChildRepositoryRefs());
                }
            }
            
            // Check if a repository model is open, if it is warn and cancel
            for(RepositoryRef ref : refsToDelete) {
                boolean isModelOpen = ref.getArchiRepository().getModel() != null;
                if(isModelOpen) {
                    MessageDialog.openError(getViewSite().getShell(),
                            Messages.ModelRepositoryView_11,
                            Messages.ModelRepositoryView_15);
                    return;
                }
            }
            
            // Delete repositories
            try {
                for(RepositoryRef ref : refsToDelete) {
                    // Delete repository folder
                    FileUtils.deleteFolder(ref.getArchiRepository().getLocalRepositoryFolder());
                    
                    // Delete from tree model
                    ref.delete();
                    
                    // Notify
                    RepositoryListenerManager.INSTANCE.fireRepositoryChangedEvent(IRepositoryListener.REPOSITORY_DELETED, ref.getArchiRepository());
                }
            }
            catch(IOException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, "Deleting Tree Models", ex); //$NON-NLS-1$
                MessageDialog.openError(getViewSite().getShell(),
                        Messages.ModelRepositoryView_11, Messages.ModelRepositoryView_16 + "\n" + ex.getMessage()); //$NON-NLS-1$
            }

            // Now delete Groups
            for(Group group : groupsToDelete) {
                // Safety measure in case a repository was not deleted in a Group
                boolean isSafeToDelete = group.getAllChildRepositoryRefs().isEmpty();
                if(isSafeToDelete) {
                    group.delete();
                }
            }
            
            // Save manifest
            try {
                RepositoryTreeModel.getInstance().saveManifest();
            }
            catch(IOException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, "Saving Manifest", ex); //$NON-NLS-1$
            }

            getViewer().refresh();
        }
    }
    
    /**
     * @return The current selection's parent group
     */
    private Group getSelectedParentGroup() {
        Object object = ((IStructuredSelection)getViewer().getSelection()).getFirstElement();
        
        if(object instanceof Group) {
            return ((Group)object);
        }
        
        if(object instanceof RepositoryRef) {
            return ((RepositoryRef)object).getParent();
        }
        
        return RepositoryTreeModel.getInstance();
    }

    /**
     * Register Global Action Handlers
     */
    private void registerGlobalActions() {
        IActionBars actionBars = getViewSite().getActionBars();
        
        // Register our interest in the global menu actions
        actionBars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), fActionProperties);
        actionBars.setGlobalActionHandler(ActionFactory.RENAME.getId(), fActionRenameEntry);
        actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), fActionSelectAll);
    }

    /**
     * Hook into a right-click menu
     */
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#RepoViewerPopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                fillContextMenu(manager);
            }
        });
        
        Menu menu = menuMgr.createContextMenu(getViewer().getControl());
        getViewer().getControl().setMenu(menu);
        
        getSite().registerContextMenu(menuMgr, getViewer());
    }
    
    /**
     * Make Any Local Bar Menu Actions
     */
    private void makeLocalMenuActions() {
        IActionBars actionBars = getViewSite().getActionBars();

        // Local menu items go here
        IMenuManager manager = actionBars.getMenuManager();
        
        // Fetch in Background preference
        IPreferenceStore store = ModelRepositoryPlugin.INSTANCE.getPreferenceStore();
        
        IAction fetchAction = new Action(Messages.ModelRepositoryView_0, IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                store.setValue(IPreferenceConstants.PREFS_FETCH_IN_BACKGROUND, isChecked());
            }
        };
        
        manager.add(fetchAction);
        fetchAction.setChecked(store.getBoolean(IPreferenceConstants.PREFS_FETCH_IN_BACKGROUND));
        
        IPropertyChangeListener listener = e -> {
            fetchAction.setChecked(store.getBoolean(IPreferenceConstants.PREFS_FETCH_IN_BACKGROUND));
        };
        store.addPropertyChangeListener(listener);
        
        getViewer().getControl().addDisposeListener(e -> {
            store.removePropertyChangeListener(listener);
        });
    }

    /**
     * Make Local Toolbar items
     */
    private void makeLocalToolBarActions() {
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager manager = bars.getToolBarManager();

        manager.add(new Separator(IWorkbenchActionConstants.NEW_GROUP));
        manager.add(fActionClone);
        manager.add(new Separator());
        manager.add(fActionAddRepository);
        manager.add(fActionAddGroup);
    }
    
    /**
     * Update the Local Actions depending on the selection 
     * @param selection
     */
    private void updateActions(ISelection selection) {
        Object obj = ((IStructuredSelection)selection).getFirstElement();
        
        // TODO: more actions
        if(obj instanceof RepositoryRef) {
            IArchiRepository repo = ((RepositoryRef)obj).getArchiRepository();
            fActionShowInHistory.setRepository(repo);
            //fActionShowInBranches.setRepository(repo);
        }
    }
    
    private void updateStatusBar(ISelection selection) {
        Object obj = ((IStructuredSelection)selection).getFirstElement();
        
        if(obj instanceof RepositoryRef) {
            IArchiRepository repo = ((RepositoryRef)obj).getArchiRepository();
            ModelRepoTreeLabelProvider labelProvider = (ModelRepoTreeLabelProvider)getViewer().getLabelProvider();
            Image image = labelProvider.getImage(repo);
            String text = repo.getName() + " - " + labelProvider.getStatusText(repo); //$NON-NLS-1$
            getViewSite().getActionBars().getStatusLineManager().setMessage(image, text);
        }
        else if(obj instanceof Group) {
            getViewSite().getActionBars().getStatusLineManager().setMessage(IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_GROUP), ((Group)obj).getName());
        }
        else {
            getViewSite().getActionBars().getStatusLineManager().setMessage(null, ""); //$NON-NLS-1$
        }
    }
    
    private void fillContextMenu(IMenuManager manager) {
        Object obj = ((IStructuredSelection)getViewer().getSelection()).getFirstElement();
        
        if(getViewer().getSelection().isEmpty()) {
            manager.add(fActionClone);
            manager.add(new Separator());
            manager.add(fActionAddRepository);
            manager.add(fActionAddGroup);
        }
        else {
            if(obj instanceof RepositoryRef) {
                manager.add(fActionOpen);
                manager.add(new Separator());
                manager.add(fActionAddRepository);
                manager.add(fActionAddGroup);
                manager.add(new Separator());
                manager.add(fActionShowInHistory);
                //manager.add(fActionShowInBranches);
                manager.add(new Separator());
                manager.add(fActionDelete);
            }
            else if(obj instanceof Group) {
                manager.add(fActionAddRepository);
                manager.add(fActionAddGroup);
                manager.add(new Separator());
                manager.add(fActionRenameEntry);
                manager.add(fActionDelete);
            }
            
            manager.add(new Separator());
            manager.add(fActionProperties);
        }
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if(part == null || part == this) {
            return;
        }
        
        // Model selected
        IArchimateModel model = part.getAdapter(IArchimateModel.class);
        selectObject(model);
    }

    public void selectObject(Object object) {
        // Model
        if(object instanceof IArchimateModel) {
            object = RepositoryTreeModel.getInstance().findRepositoryRef(RepoUtils.getLocalRepositoryFolderForModel((IArchimateModel)object));
        }
        // Repository
        else if(object instanceof IArchiRepository) {
            object = RepositoryTreeModel.getInstance().findRepositoryRef(((IArchiRepository)object).getLocalRepositoryFolder());
        }
        
        if(object != null) {
            getViewer().setSelection(new StructuredSelection(object));
        }
    }
    
    /**
     * @return The Viewer
     */
    public TreeViewer getViewer() {
        return fTreeViewer;
    }
    
    @Override
    public void setFocus() {
        if(getViewer() != null) {
            getViewer().getControl().setFocus();
        }
    }
    
    @Override
    public String getContributorId() {
        return ModelRepositoryPlugin.PLUGIN_ID;
    }
    
    @Override
    public <T> T getAdapter(Class<T> adapter) {
        /*
         * Return the PropertySheet Page
         */
        if(adapter == IPropertySheetPage.class) {
            return adapter.cast(new TabbedPropertySheetPage(this));
        }
        
        /*
         * Return the active repository
         */
        if(adapter == IArchiRepository.class) {
            Object obj = getViewer().getStructuredSelection().getFirstElement();
            if(obj instanceof RepositoryRef) {
                return adapter.cast(((RepositoryRef)obj).getArchiRepository());
            }
        }
        
        return super.getAdapter(adapter);
    }

    @Override
    public void dispose() {
        super.dispose();
        getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
    }

    // =================================================================================
    //                       Contextual Help support
    // =================================================================================
    
    @Override
    public int getContextChangeMask() {
        return NONE;
    }

    @Override
    public IContext getContext(Object target) {
        return HelpSystem.getContext(HELP_ID);
    }

    @Override
    public String getSearchExpression(Object target) {
        return Messages.ModelRepositoryView_1;
    }
}

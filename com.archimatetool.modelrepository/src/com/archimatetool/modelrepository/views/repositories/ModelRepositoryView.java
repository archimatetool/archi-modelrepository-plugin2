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

import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
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
import org.eclipse.ui.SelectionListenerFactory;
import org.eclipse.ui.SelectionListenerFactory.Predicates;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import com.archimatetool.editor.ArchiPlugin;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.CloneModelAction;
import com.archimatetool.modelrepository.actions.CommitModelAction;
import com.archimatetool.modelrepository.actions.DiscardChangesAction;
import com.archimatetool.modelrepository.actions.FetchUpdateAction;
import com.archimatetool.modelrepository.actions.IRepositoryAction;
import com.archimatetool.modelrepository.actions.PushModelAction;
import com.archimatetool.modelrepository.actions.RefreshModelAction;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.Group;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;
import com.archimatetool.modelrepository.views.branches.BranchesView;
import com.archimatetool.modelrepository.views.history.HistoryView;
import com.archimatetool.modelrepository.views.repositories.ModelRepositoryTreeViewer.ModelRepoTreeLabelProvider;
import com.archimatetool.modelrepository.views.tags.TagsView;


/**
 * Model Repository ViewPart for managing models
 */
public class ModelRepositoryView
extends ViewPart
implements IContextProvider, ISelectionListener, ITabbedPropertySheetPageContributor {
    
    private static Logger logger = Logger.getLogger(ModelRepositoryView.class.getName());

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".modelRepositoryView"; //$NON-NLS-1$
    
    /**
     * The Repository Viewer
     */
    private ModelRepositoryTreeViewer fTreeViewer;
    
    /*
     * Actions
     */
    private IRepositoryAction fActionClone;
    private IRepositoryAction fActionRefresh;
    private IRepositoryAction fActionCommit;
    private IRepositoryAction fActionPush;
    private IRepositoryAction fActionDiscardChanges;

    private FetchUpdateAction fActionUpdate;

    private IAction fActionShowInHistory;
    private IAction fActionShowInBranches;
    private IAction fActionShowInTags;
    
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
        getViewer().addSelectionChangedListener(event -> {
            updateActions(event.getSelection());
            updateStatusBar(event.getSelection());
        });
        
        // Listen to workbench selections using a SelectionListenerFactory
        getSite().getPage().addSelectionListener(SelectionListenerFactory.createListener(this,
                                                 Predicates.alreadyDeliveredAnyPart.and(Predicates.selfMute)));
        
        // Initialise with whatever is selected in the workbench
        selectionChanged(getSite().getPage().getActivePart(), getSite().getPage().getSelection());
        
        /*
         * Listen to Double-click Action
         */
        getViewer().addDoubleClickListener(event -> {
            Object obj = ((IStructuredSelection)event.getSelection()).getFirstElement();
            if(obj instanceof RepositoryRef ref) {
                IArchiRepository repo = ref.getArchiRepository();
                
                BusyIndicator.showWhile(Display.getCurrent(), () -> {
                    IEditorModelManager.INSTANCE.openModel(repo.getModelFile());
                });
            }
        });

        // Register Help Context
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getViewer().getControl(), ModelRepositoryPlugin.HELP_ID);
    }
    
    /**
     * Make local actions
     */
    private void makeActions() {
        // Clone
        fActionClone = new CloneModelAction(getViewSite().getWorkbenchWindow());
        
        // Refresh
        fActionRefresh = new RefreshModelAction(getViewSite().getWorkbenchWindow());

        // Commit
        fActionCommit = new CommitModelAction(getViewSite().getWorkbenchWindow());
        
        // Push
        fActionPush = new PushModelAction(getViewSite().getWorkbenchWindow());

        // Discard changes
        fActionDiscardChanges = new DiscardChangesAction(getViewSite().getWorkbenchWindow());
        
        // Open Model
        fActionOpen = new Action(Messages.ModelRepositoryView_13) {
            @Override
            public void run() {
                if(getViewer().getStructuredSelection().getFirstElement() instanceof RepositoryRef ref) {
                    BusyIndicator.showWhile(Display.getCurrent(), new Runnable() {
                        @Override
                        public void run() {
                            IEditorModelManager.INSTANCE.openModel(ref.getArchiRepository().getModelFile());
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
            
            @Override
            public String getActionDefinitionId() {
                return "com.archimatetool.modelrepository.newGroup"; //$NON-NLS-1$
            }
        };
        
        // Add Existing Repository
        fActionAddRepository = new Action(Messages.ModelRepositoryView_3, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_MODEL)) {
            @Override
            public void run() {
                addNewRepositoryRef();
            }
            
            @Override
            public String getActionDefinitionId() {
                return "com.archimatetool.modelrepository.addExistingRepository"; //$NON-NLS-1$
            }
        };
        
        // Delete
        fActionDelete = new Action(Messages.ModelRepositoryView_4, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_DELETE)) {
            @Override
            public void run() {
                deleteSelected();
            }
            
            @Override
            public String getActionDefinitionId() {
                return "org.eclipse.ui.edit.delete"; // Ensures key binding is displayed //$NON-NLS-1$
            }
        };
        
        // Rename
        fActionRenameEntry = new Action(Messages.ModelRepositoryView_5) {
            @Override
            public void run() {
                Object o = getViewer().getStructuredSelection().getFirstElement();
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
        fActionShowInHistory = new Action(Messages.ModelRepositoryView_20, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_HISTORY_VIEW)) {
            @Override
            public void run() {
                ViewManager.showViewPart(HistoryView.ID, false);
            }
            
            @Override
            public String getActionDefinitionId() {
                return "com.archimatetool.modelrepository.command.showInHistoryView"; //$NON-NLS-1$
            }
        };
        
        // Show in Branches
        fActionShowInBranches = new Action(Messages.ModelRepositoryView_21, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_BRANCHES)) {
            @Override
            public void run() {
                ViewManager.showViewPart(BranchesView.ID, false);
            }
            
            @Override
            public String getActionDefinitionId() {
                return "com.archimatetool.modelrepository.command.showInBranchesView"; //$NON-NLS-1$
            }
        };
        
        // Show in Tags
        fActionShowInTags = new Action(Messages.ModelRepositoryView_22, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_TAGS)) {
            @Override
            public void run() {
                ViewManager.showViewPart(TagsView.ID, false);
            }
            
            @Override
            public String getActionDefinitionId() {
                return "com.archimatetool.modelrepository.command.showInTagsView"; //$NON-NLS-1$
            }
        };
        
        // Fetch update
        fActionUpdate = new FetchUpdateAction(getSite().getWorkbenchWindow());
        
        // Register the Keybinding for these actions
        IHandlerService service = getViewSite().getService(IHandlerService.class);
        service.activateHandler(fActionAddGroup.getActionDefinitionId(), new ActionHandler(fActionAddGroup));
        service.activateHandler(fActionAddRepository.getActionDefinitionId(), new ActionHandler(fActionAddRepository));
        service.activateHandler(fActionUpdate.getActionDefinitionId(), new ActionHandler(fActionUpdate));
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
        Set<RepositoryRef> refsToDelete = new HashSet<RepositoryRef>();
        Set<Group> groupsToDelete = new HashSet<Group>();
        boolean delete = false;

        // Get all selected Repository Refs and Groups
        for(Object object : getViewer().getStructuredSelection().toArray()) {
            // Selected RepositoryRef
            if(object instanceof RepositoryRef ref) {
                refsToDelete.add(ref);
            }
            // Selected Group and its sub-groups and sub-RepositoryRefs
            if(object instanceof Group group) {
                groupsToDelete.add(group);
                groupsToDelete.addAll(group.getAllChildGroups());
                refsToDelete.addAll(group.getAllChildRepositoryRefs());
            }
        }
        
        // Nothing to delete
        if(refsToDelete.isEmpty() && groupsToDelete.isEmpty()) {
            return;
        }

        // No Repositories selected, only groups
        if(refsToDelete.isEmpty()) {
            if(!MessageDialog.openQuestion(getViewSite().getShell(),
                    Messages.ModelRepositoryView_11,
                    Messages.ModelRepositoryView_12)) {
                return;
            }
        }
        else {
            int response = MessageDialog.open(MessageDialog.QUESTION,
                    getViewSite().getShell(),
                    Messages.ModelRepositoryView_11,
                    Messages.ModelRepositoryView_16,
                    SWT.NONE,
                    Messages.ModelRepositoryView_17,
                    Messages.ModelRepositoryView_18,
                    Messages.ModelRepositoryView_19);

            // Cancel
            if(response == -1 || response == 2) {
                return;
            }
            
            delete = response == 1;
        }

        // Delete repositories
        try {
            for(RepositoryRef ref : refsToDelete) {
                // Close model without asking to save
                IEditorModelManager.INSTANCE.closeModel(ref.getArchiRepository().getOpenModel(), false);

                // Delete repository folder and any HTTP credentials
                if(delete) {
                    // Delete credentials *first*
                    CredentialsStorage.getInstance().storeCredentials(ref.getArchiRepository(), new UsernamePassword(null, null));
                    // Delete folder
                    FileUtils.deleteFolder(ref.getArchiRepository().getWorkingFolder());
                }

                // Delete from tree model
                ref.delete();

                // Notify deleted
                RepositoryListenerManager.getInstance().fireRepositoryChangedEvent(IRepositoryListener.REPOSITORY_DELETED, ref.getArchiRepository());
            }
        }
        catch(IOException | StorageException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Deleting Tree Models", ex); //$NON-NLS-1$
            MessageDialog.openError(getViewSite().getShell(),
                    Messages.ModelRepositoryView_11, Messages.ModelRepositoryView_15 + "\n" + ex.getMessage()); //$NON-NLS-1$
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
    
    /**
     * @return The current selection's parent group
     */
    private Group getSelectedParentGroup() {
        Object object = getViewer().getStructuredSelection().getFirstElement();
        
        if(object instanceof Group group) {
            return group;
        }
        
        if(object instanceof RepositoryRef ref) {
            return ref.getParent();
        }
        
        return RepositoryTreeModel.getInstance();
    }

    /**
     * Register Global Action Handlers for key bindings
     */
    private void registerGlobalActions() {
        IActionBars actionBars = getViewSite().getActionBars();
        
        // Register our interest in the global menu actions
        actionBars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), fActionProperties);
        actionBars.setGlobalActionHandler(ActionFactory.RENAME.getId(), fActionRenameEntry);
        actionBars.setGlobalActionHandler(ActionFactory.DELETE.getId(), fActionDelete);
        actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), fActionSelectAll);
    }

    /**
     * Hook into a right-click menu
     */
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#RepoViewerPopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(manager -> {
            fillContextMenu(manager);
        });
        
        Menu menu = menuMgr.createContextMenu(getViewer().getControl());
        getViewer().getControl().setMenu(menu);
        
        getSite().registerContextMenu(menuMgr, getViewer());
    }
    
    /**
     * Make Any Local Bar Menu Actions
     */
    private void makeLocalMenuActions() {
        // TODO: Enable this if we are implementing Fetch in Background
        boolean localMenuDisabled = true;
        if(localMenuDisabled) {
            return;
        }
        
        IActionBars actionBars = getViewSite().getActionBars();

        // Local menu items go here
        IMenuManager manager = actionBars.getMenuManager();
        
        // Fetch in Background preference
        IPreferenceStore store = ModelRepositoryPlugin.getInstance().getPreferenceStore();
        
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
        manager.add(fActionAddRepository);
        manager.add(fActionAddGroup);
        manager.add(new Separator());
        manager.add(fActionUpdate);
    }
    
    /**
     * Update the Local Actions depending on the selection 
     * @param selection
     */
    private void updateActions(ISelection selection) {
        Object obj = ((IStructuredSelection)selection).getFirstElement();
        
        if(obj instanceof RepositoryRef ref) {
            IArchiRepository repo = ref.getArchiRepository();
            
            fActionRefresh.setRepository(repo);
            fActionCommit.setRepository(repo);
            fActionPush.setRepository(repo);
            fActionDiscardChanges.setRepository(repo);
        }
        
        fActionUpdate.setEnabled();
    }
    
    private void updateStatusBar(ISelection selection) {
        Object obj = ((IStructuredSelection)selection).getFirstElement();
        
        if(obj instanceof RepositoryRef ref) {
            IArchiRepository repo = ref.getArchiRepository();
            ModelRepoTreeLabelProvider labelProvider = (ModelRepoTreeLabelProvider)getViewer().getLabelProvider();
            Image image = labelProvider.getImage(repo);
            String text = repo.getName() + " - " + labelProvider.getStatusText(repo); //$NON-NLS-1$
            getViewSite().getActionBars().getStatusLineManager().setMessage(image, text);
        }
        else if(obj instanceof Group group) {
            getViewSite().getActionBars().getStatusLineManager().setMessage(IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_GROUP), group.getName());
        }
        else {
            getViewSite().getActionBars().getStatusLineManager().setMessage(null, ""); //$NON-NLS-1$
        }
    }
    
    private void fillContextMenu(IMenuManager manager) {
        Object obj = ((IStructuredSelection)getViewer().getSelection()).getFirstElement();
        
        if(obj instanceof RepositoryRef) {
            manager.add(fActionOpen);
            manager.add(new Separator());
        }
        
        manager.add(fActionClone);
        manager.add(fActionAddRepository);
        manager.add(new Separator());
        
        if(getViewer().getSelection().isEmpty()) {
            manager.add(fActionAddGroup);
            manager.add(new Separator());
            manager.add(fActionUpdate);
       }
        else {
            if(obj instanceof RepositoryRef) {
                manager.add(fActionAddGroup);
                manager.add(new Separator());
                manager.add(fActionUpdate);
                manager.add(new Separator());
                manager.add(fActionRefresh);
                manager.add(fActionCommit);
                manager.add(fActionPush);
                manager.add(fActionDiscardChanges);
                manager.add(new Separator());
                manager.add(fActionShowInHistory);
                manager.add(fActionShowInBranches);
                manager.add(fActionShowInTags);
                manager.add(new Separator());
                manager.add(fActionDelete);
            }
            else if(obj instanceof Group) {
                manager.add(fActionAddGroup);
                manager.add(new Separator());
                manager.add(fActionUpdate);
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
        // Model selected
        if(part != null) {
            IArchimateModel model = part.getAdapter(IArchimateModel.class);
            selectObject(model);
        }
    }

    public void selectObject(Object object) {
        // Model
        if(object instanceof IArchimateModel model) {
            object = RepositoryTreeModel.getInstance().findRepositoryRef(RepoUtils.getWorkingFolderForModel(model));
        }
        // Repository
        else if(object instanceof IArchiRepository repo) {
            object = RepositoryTreeModel.getInstance().findRepositoryRef(repo.getWorkingFolder());
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
        // Use the ID of the host app so our Property Sheet contributions can be used when selecting an IArchimateModel in Archi
        return ArchiPlugin.PLUGIN_ID;
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
            return obj instanceof RepositoryRef ref ? adapter.cast(ref.getArchiRepository()) : null;
        }
        
        return super.getAdapter(adapter);
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
        return HelpSystem.getContext(ModelRepositoryPlugin.HELP_ID);
    }

    @Override
    public String getSearchExpression(Object target) {
        return Messages.ModelRepositoryView_1;
    }
}

/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

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
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.IModelRepositoryAction;
import com.archimatetool.modelrepository.actions.PropertiesAction;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.views.repositories.ModelRepositoryTreeViewer.ModelRepoTreeLabelProvider;


/**
 * Model Repository ViewPart for managing models
 */
public class ModelRepositoryView
extends ViewPart
implements IContextProvider, ISelectionListener, ITabbedPropertySheetPageContributor {

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".modelRepositoryView"; //$NON-NLS-1$
    public static String HELP_ID = ModelRepositoryPlugin.PLUGIN_ID + ".modelRepositoryViewHelp"; //$NON-NLS-1$
    
    /**
     * The Repository Viewer
     */
    private ModelRepositoryTreeViewer fTreeViewer;
    
    /*
     * Actions
     */
    private IModelRepositoryAction fActionProperties;
    

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
            if(obj instanceof IArchiRepository) {
                BusyIndicator.showWhile(Display.getCurrent(), () -> {
                    IEditorModelManager.INSTANCE.openModel(((IArchiRepository)obj).getModelFile());
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
        // TODO More actions here...
        
        fActionProperties = new PropertiesAction(getViewSite().getWorkbenchWindow());
        fActionProperties.setEnabled(false);
        
        // Register the Keybinding for actions
//        IHandlerService service = (IHandlerService)getViewSite().getService(IHandlerService.class);
//        service.activateHandler(fActionRefresh.getActionDefinitionId(), new ActionHandler(fActionRefresh));
    }

    /**
     * Register Global Action Handlers
     */
    private void registerGlobalActions() {
        IActionBars actionBars = getViewSite().getActionBars();
        
        // Register our interest in the global menu actions
        actionBars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), fActionProperties);
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
        
        IPropertyChangeListener listener = (e) -> {
            fetchAction.setChecked(store.getBoolean(IPreferenceConstants.PREFS_FETCH_IN_BACKGROUND));
        };
        store.addPropertyChangeListener(listener);
        
        getViewer().getControl().addDisposeListener((e) -> {
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
        
        // TODO
        //manager.add(fActionClone);
        //manager.add(fActionDelete);
    }
    
    /**
     * Update the Local Actions depending on the selection 
     * @param selection
     */
    private void updateActions(ISelection selection) {
        Object obj = ((IStructuredSelection)selection).getFirstElement();
        
        if(obj instanceof IArchiRepository) {
            IArchiRepository repo = (IArchiRepository)obj;
            
            // TODO
            //fActionRefresh.setRepository(repo);
            //fActionOpen.setRepository(repo);
            //fActionDelete.setRepository(repo);
            //fActionAbortChanges.setRepository(repo);
            
            //fActionCommit.setRepository(repo);
            //fActionPush.setRepository(repo);
            
            //fActionShowInHistory.setRepository(repo);
            //fActionShowInBranches.setRepository(repo);
            
            fActionProperties.setRepository(repo);
        }
    }
    
    private void updateStatusBar(ISelection selection) {
        Object obj = ((IStructuredSelection)selection).getFirstElement();
        
        if(obj instanceof IArchiRepository) {
            IArchiRepository repo = (IArchiRepository)obj;
            ModelRepoTreeLabelProvider labelProvider = (ModelRepoTreeLabelProvider)getViewer().getLabelProvider();
            Image image = labelProvider.getImage(repo);
            String text = repo.getName() + " - " + labelProvider.getStatusText(repo); //$NON-NLS-1$
            getViewSite().getActionBars().getStatusLineManager().setMessage(image, text);
        }
        else {
            getViewSite().getActionBars().getStatusLineManager().setMessage(null, ""); //$NON-NLS-1$
        }
    }
    
    private void fillContextMenu(IMenuManager manager) {
        // TODO
        if(getViewer().getSelection().isEmpty()) {
            //manager.add(fActionClone);
        }
        else {
            //manager.add(fActionOpen);
            //manager.add(fActionShowInHistory);
            //manager.add(fActionShowInBranches);
            manager.add(new Separator());
            //manager.add(fActionDelete);
            manager.add(new Separator());
            manager.add(fActionProperties);
        }
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if(part == null || part == this) {
            return;
        }
        
        // Model selected, but is it in a git repo?
        IArchimateModel model = part.getAdapter(IArchimateModel.class);
        if(model != null) {
            if(RepoUtils.isModelInArchiRepository(model)) {
                IArchiRepository selectedRepository = new ArchiRepository(RepoUtils.getLocalRepositoryFolderForModel(model));
                getViewer().setSelection(new StructuredSelection(selectedRepository));
            }
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
    
    public void selectObject(Object object) {
        getViewer().setSelection(new StructuredSelection(object));
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

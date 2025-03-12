/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.SelectionListenerFactory;
import org.eclipse.ui.SelectionListenerFactory.Predicates;
import org.eclipse.ui.part.IContributedContentsView;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.ExtractModelFromCommitAction;
import com.archimatetool.modelrepository.actions.ResetToRemoteCommitAction;
import com.archimatetool.modelrepository.actions.RestoreCommitAction;
import com.archimatetool.modelrepository.actions.UndoLastCommitAction;
import com.archimatetool.modelrepository.dialogs.CompareDialog;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.ModelComparison;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.views.PartUtils;


/**
 * History Viewpart
 */
public class HistoryView
extends ViewPart
implements IContextProvider, ISelectionListener, IRepositoryListener, IContributedContentsView {
    
    private static Logger logger = Logger.getLogger(HistoryView.class.getName());

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".historyView"; //$NON-NLS-1$
    public static String HELP_ID = ModelRepositoryPlugin.PLUGIN_ID + ".historyViewHelp"; //$NON-NLS-1$
    
    /*
     * Selected repository
     */
    private IArchiRepository fSelectedRepository;
    
    // Last selection in the workbench
    private ISelection fLastSelection;
    
    // Is filtering on an object but not permanently
    private boolean isFilteringObject;
    
    private Label fRepoLabel;

    private HistoryTableViewer fHistoryTableViewer;
    private CommitViewer fCommitViewer;
    private BranchesViewer fBranchesViewer;
    
    /*
     * Actions
     */
    private UndoLastCommitAction fActionUndoLastCommit;
    private ExtractModelFromCommitAction fActionExtractCommit;
    private RestoreCommitAction fActionRestoreCommit;
    private ResetToRemoteCommitAction fActionResetToRemoteCommit;
    
    private IAction fActionCompare = new Action(Messages.HistoryView_3) {
        @Override
        public void run() {
            List<?> selection = getHistoryViewer().getStructuredSelection().toList();
            ModelComparison mc = null;
            
            // Selected Working Tree so compare with latest commit
            if(selection.size() == 1 && !(selection.get(0) instanceof RevCommit)) {
                try {
                    BranchInfo branchInfo = BranchInfo.currentLocalBranchInfo(fSelectedRepository.getWorkingFolder(), true);
                    mc = new ModelComparison(fSelectedRepository, branchInfo.getLatestCommit());
                }
                catch(IOException | GitAPIException ex) {
                    ex.printStackTrace();
                    logger.log(Level.SEVERE, "Branch Info", ex); //$NON-NLS-1$
                }
            }
            // Selected two objects
            else if(selection.size() == 2) {
                // Two RevCommits
                if(selection.get(0) instanceof RevCommit revCommit1 && selection.get(1) instanceof RevCommit revCommit2) {
                    mc = new ModelComparison(fSelectedRepository, revCommit1, revCommit2);
                }
                // One RevCommit and Working Tree
                else if(selection.get(0) instanceof RevCommit revCommit) {
                    mc = new ModelComparison(fSelectedRepository, revCommit);
                }
                // One RevCommit and Working Tree
                else if(selection.get(1) instanceof RevCommit revCommit) {
                    mc = new ModelComparison(fSelectedRepository, revCommit);
                }
            }
            
            if(mc != null) {
                try {
                    mc.init();
                    new CompareDialog(getSite().getShell(), mc).open();
                }
                catch(IOException ex) {
                    ex.printStackTrace();
                    logger.log(Level.SEVERE, "Model Comparison", ex); //$NON-NLS-1$
                }
            }
        }
        
        @Override
        public ImageDescriptor getImageDescriptor() {
            return IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_TWOWAY_COMPARE);
        };
    };
    
    private IAction fActionFilter = new Action(Messages.HistoryView_6, IAction.AS_CHECK_BOX) {
        @Override
        public void run() {
            if(isChecked()) {
                setFilteredModelObject(fLastSelection, true);
            }
            else {
                getHistoryViewer().setFilteredModelObject(null, true);
            }
            
            updateLabel();
        }
    };
    
    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout());
        
        // Create Info Section
        createInfoSection(parent);
        
        // Create History Table and Comment Viewer
        createHistorySection(parent);

        makeActions();
        hookContextMenu();
        makeLocalMenuActions();
        makeLocalToolBarActions();
        
        // Register us as a selection provider so that Actions can pick us up
        getSite().setSelectionProvider(getHistoryViewer());
        
        // Listen to workbench selections using a SelectionListenerFactory
        getSite().getPage().addSelectionListener(SelectionListenerFactory.createListener(this,
                                                 Predicates.alreadyDeliveredAnyPart.and(Predicates.selfMute)));

        // Register Help Context
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getHistoryViewer().getControl(), HELP_ID);
        
        // Initialise with whatever is selected in the workbench
        selectionChanged(getSite().getPage().getActivePart(), getSite().getPage().getSelection());
        
        // Add listener
        RepositoryListenerManager.INSTANCE.addListener(this);
    }
    
    private void createInfoSection(Composite parent) {
        Composite mainComp = new Composite(parent, SWT.NONE);
        mainComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        mainComp.setLayout(layout);

        // Repository name
        fRepoLabel = new Label(mainComp, SWT.NONE);
        fRepoLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fRepoLabel.setText(Messages.HistoryView_0);

        // Branches
        Label label = new Label(mainComp, SWT.NONE);
        label.setText(Messages.HistoryView_2);

        fBranchesViewer = new BranchesViewer(mainComp);
        GridData gd = new GridData(SWT.END);
        fBranchesViewer.getControl().setLayoutData(gd);
        fBranchesViewer.getControl().setToolTipText(Messages.HistoryView_5);
        
        /*
         * Listen to Branch Selections and forward on to History Viewer
         */
        fBranchesViewer.addSelectionChangedListener(event -> {
            BranchInfo branchInfo = (BranchInfo)event.getStructuredSelection().getFirstElement();
            getHistoryViewer().setSelectedBranch(branchInfo);
        });
    }
    
    private void createHistorySection(Composite parent) {
        SashForm tableSash = new SashForm(parent, SWT.VERTICAL);
        tableSash.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        Composite tableComp = new Composite(tableSash, SWT.NONE);
        tableComp.setLayout(new TableColumnLayout());
        
        // This ensures a minumum and equal size and no horizontal size creep for the table
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 100;
        gd.heightHint = 50;
        tableComp.setLayoutData(gd);
        
        // History Table
        fHistoryTableViewer = new HistoryTableViewer(tableComp);
        
        // Commit Viewer
        fCommitViewer = new CommitViewer(tableSash);
        
        tableSash.setWeights(new int[] { 75, 25 });
        
        /*
         * Listen to History Selections to update local Actions
         */
        fHistoryTableViewer.addSelectionChangedListener(event -> {
            updateActions();
        });
    }
    
    /**
     * Make local actions
     */
    private void makeActions() {
        fActionCompare.setEnabled(false);
        
        fActionExtractCommit = new ExtractModelFromCommitAction(getViewSite().getWorkbenchWindow());
        fActionExtractCommit.setEnabled(false);

        fActionUndoLastCommit = new UndoLastCommitAction(getViewSite().getWorkbenchWindow());
        fActionUndoLastCommit.setEnabled(false);
        
        fActionRestoreCommit = new RestoreCommitAction(getViewSite().getWorkbenchWindow());
        fActionRestoreCommit.setEnabled(false);

        fActionResetToRemoteCommit = new ResetToRemoteCommitAction(getViewSite().getWorkbenchWindow());
        fActionResetToRemoteCommit.setEnabled(false);
        
        // Register the Keybinding for actions
//        IHandlerService service = (IHandlerService)getViewSite().getService(IHandlerService.class);
//        service.activateHandler(fActionRefresh.getActionDefinitionId(), new ActionHandler(fActionRefresh));
    }

    /**
     * Hook into a right-click menu
     */
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#HistoryPopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(manager -> {
            fillContextMenu(manager);
        });
        
        Menu menu = menuMgr.createContextMenu(getHistoryViewer().getControl());
        getHistoryViewer().getControl().setMenu(menu);
        
        getSite().registerContextMenu(menuMgr, getHistoryViewer());
    }
    
    /**
     * Make Any Local Bar Menu Actions
     */
    private void makeLocalMenuActions() {
        IActionBars actionBars = getViewSite().getActionBars();

        // Local menu items go here
        IMenuManager manager = actionBars.getMenuManager();
        manager.add(fActionFilter);
    }

    /**
     * Make Local Toolbar items
     */
    private void makeLocalToolBarActions() {
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager manager = bars.getToolBarManager();

        manager.add(new Separator(IWorkbenchActionConstants.NEW_GROUP));
        
        manager.add(fActionCompare);
        manager.add(new Separator());
        manager.add(fActionExtractCommit);
        manager.add(fActionRestoreCommit);
        manager.add(new Separator());
        manager.add(fActionUndoLastCommit);
        manager.add(fActionResetToRemoteCommit);
        
        manager.add(new Separator());
    }
    
    /**
     * Update the Local Actions depending on the local selection 
     */
    private void updateActions() {
        IStructuredSelection selection = getHistoryViewer().getStructuredSelection();
        boolean isSingleSelection = selection.size() == 1;
        
        fActionUndoLastCommit.setRepository(fSelectedRepository);
        fActionResetToRemoteCommit.setRepository(fSelectedRepository);

        // Selected Working tree or an empty selection, not a RevCommit
        if(!(selection.getFirstElement() instanceof RevCommit revCommit)) {
            fActionExtractCommit.setCommit(null, null);
            fActionRestoreCommit.setCommit(null, null);
            
            fActionCompare.setEnabled(isSingleSelection || selection.size() == 2);
            fActionCompare.setText(isSingleSelection ? Messages.HistoryView_4 : Messages.HistoryView_3);
            
            // Commit Viewer
            fCommitViewer.setCommit(null);
            
            return;
        }
        
        fActionExtractCommit.setCommit(fSelectedRepository, isSingleSelection ? revCommit : null);
        fActionRestoreCommit.setCommit(fSelectedRepository, isSingleSelection ? revCommit : null);
        
        fActionCompare.setText(Messages.HistoryView_3);
        fActionCompare.setEnabled(selection.size() == 2);
        
        // Commit Viewer
        fCommitViewer.setCommit(isSingleSelection ? revCommit : null);
    }
    
    private void fillContextMenu(IMenuManager manager) {
        manager.add(fActionCompare);
        manager.add(new Separator());
        manager.add(fActionExtractCommit);
        manager.add(fActionRestoreCommit);
        manager.add(new Separator());
        manager.add(fActionUndoLastCommit);
        manager.add(fActionResetToRemoteCommit);
        manager.add(new Separator());
        manager.add(fActionFilter);
    }

    HistoryTableViewer getHistoryViewer() {
        return fHistoryTableViewer;
    }
    
    BranchesViewer getBranchesViewer() {
        return fBranchesViewer;
    }

    @Override
    public void setFocus() {
        if(getHistoryViewer() != null) {
            getHistoryViewer().getControl().setFocus();
        }
    }
    
    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        IArchiRepository selectedRepository = PartUtils.getSelectedArchiRepositoryInWorkbenchPart(part);
        
        // Update if selectedRepository is not the currently selected one 
        if(!Objects.equals(selectedRepository, fSelectedRepository)) {
            // Store last selected
            fSelectedRepository = selectedRepository;

            // Set filtered model object
            updateObjectFilter(selection, false);
            
            // Set label text
            updateLabel();

            // Set History
            getHistoryViewer().setRepository(selectedRepository);
            
            // Set Branches
            getBranchesViewer().setRepository(selectedRepository);
            
            // If selectedRepository is null update actions, if it isn't null actions will be updated on first selection
            if(selectedRepository == null) {
                updateActions();
            }
        }
        // If it is the currently selected one then...
        else {
            // ...set filtered model object if we are filtering
            updateObjectFilter(selection, true);
        }
        
        fLastSelection = selection;
    }
    
    /**
     * Called from the external menu item "Show In History"
     * This sets the filtered object on a single selection and then unsets it when another selection is made
     */
    public void setFilteredModelObject(ISelection selection) {
        if(!fActionFilter.isChecked()) {
            isFilteringObject = true;
            setFilteredModelObject(selection, true);
            updateLabel();
        }
    }
    
    private void updateObjectFilter(ISelection selection, boolean doUpdate) {
        // If we are filtering all the time
        if(fActionFilter.isChecked()) {
            setFilteredModelObject(selection, doUpdate);
        }
        // If we are doing a one-off single selection filter then unset it
        else if(isFilteringObject && !Objects.equals(selection, fLastSelection)) {
            getHistoryViewer().setFilteredModelObject(null, true);
            isFilteringObject = false;
            updateLabel();
        }
    }
    
    /**
     * If the object filter is active set the history filter object's id to that in the selection if it's a model object in the selected model.
     */
    private void setFilteredModelObject(ISelection selection, boolean doUpdate) {
        if(fSelectedRepository != null && selection instanceof IStructuredSelection sel) {
            Object selected = sel.getFirstElement();
            IArchimateModelObject modelObject = null;
            
            if(selected instanceof IArchimateModelObject) {
                modelObject = (IArchimateModelObject)selected;
            }
            
            // If it's an EditPart...
            else if(selected instanceof IAdaptable adaptable) {
                boolean getDiagramModelForDiagramComponents = true;
                
                // If it's a diagram model component get the DiagramModel
                if(getDiagramModelForDiagramComponents) {
                    modelObject = adaptable.getAdapter(IDiagramModelComponent.class);
                    if(modelObject instanceof IDiagramModelComponent dmc) {
                        modelObject = dmc.getDiagramModel();
                    }
                }
                // Else get the ArchimateConcept if it's an Archimate diagram model component, or null if a plain diagram model compenent
                else {
                    modelObject = adaptable.getAdapter(IArchimateModelObject.class);
                }
            }
            
            if(modelObject != null && modelObject.getArchimateModel() == fSelectedRepository.getOpenModel()) {
                getHistoryViewer().setFilteredModelObject(modelObject.getId(), doUpdate);
            }
            // No selected object in a repo part
            else {
                getHistoryViewer().setFilteredModelObject(null, true);
            }
        }
    }
    
    /**
     * Update the label
     */
    private void updateLabel() {
        String text = Messages.HistoryView_0 + " "; //$NON-NLS-1$
        if(fSelectedRepository != null) {
            text += fSelectedRepository.getName();
            if(fActionFilter.isChecked() || isFilteringObject) {
                text += " " + Messages.HistoryView_7; //$NON-NLS-1$
            }
        }
        fRepoLabel.setText(text);
    }
    
    @Override
    public void repositoryChanged(String eventName, IArchiRepository repository) {
        // Update only if the repository change is the currently selected one
        if(!Objects.equals(repository, fSelectedRepository)) {
            return;
        }

        switch(eventName) {
            case IRepositoryListener.HISTORY_CHANGED -> {
                getHistoryViewer().setInput(repository);
                updateLabel();
            }
            
            case IRepositoryListener.REPOSITORY_DELETED -> {
                fSelectedRepository = null; // Reset this
                updateLabel();
                getHistoryViewer().setRepository(null);
                getBranchesViewer().setRepository(null);
                updateActions();
            }
                
            case IRepositoryListener.MODEL_RENAMED -> {
                updateLabel();
            }
                
            case IRepositoryListener.MODEL_SAVED -> {
                getHistoryViewer().modelSaved();
            }

            case IRepositoryListener.BRANCHES_CHANGED -> {
                getBranchesViewer().setRepository(repository);
            }
        }

        // Actions need to be updated
        updateActions();
    }
    
    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if(adapter == IArchiRepository.class) {
            return adapter.cast(getHistoryViewer().getInput());
        }
        
        return super.getAdapter(adapter);
    }
    
    /**
     * Return null so that the Properties View displays "The active part does not provide properties" instead of a table
     */
    @Override
    public IWorkbenchPart getContributingPart() {
        return null;
    }

    @Override
    public void dispose() {
        super.dispose();
        RepositoryListenerManager.INSTANCE.removeListener(this);
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
        return Messages.HistoryView_1;
    }
}

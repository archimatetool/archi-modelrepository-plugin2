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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
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
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.IRunnable;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.AddBranchAction;
import com.archimatetool.modelrepository.actions.AddTagAction;
import com.archimatetool.modelrepository.actions.CommitModelAction;
import com.archimatetool.modelrepository.actions.ExtractModelFromCommitAction;
import com.archimatetool.modelrepository.actions.ResetToRemoteCommitAction;
import com.archimatetool.modelrepository.actions.RestoreCommitAction;
import com.archimatetool.modelrepository.actions.UndoLastCommitAction;
import com.archimatetool.modelrepository.dialogs.CompareDialog;
import com.archimatetool.modelrepository.dialogs.ErrorMessageDialog;
import com.archimatetool.modelrepository.merge.ModelComparison;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
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
    private RevMessageViewer fMessageViewer;
    private BranchesViewer fBranchesViewer;
    
    /*
     * Actions
     */
    private UndoLastCommitAction fActionUndoLastCommit;
    private ExtractModelFromCommitAction fActionExtractCommit;
    private RestoreCommitAction fActionRestoreCommit;
    private ResetToRemoteCommitAction fActionResetToRemoteCommit;
    private AddBranchAction fActionAddBranch;
    private CommitModelAction fActionCommit;
    private AddTagAction fActionAddTag;
    
    private IAction fActionCompare = new Action(Messages.HistoryView_3) {
        @Override
        public void run() {
            List<?> selection = getHistoryViewer().getStructuredSelection().toList();
            ModelComparison mc = null;
            
            // Selected Working Tree so compare with latest commit
            if(selection.size() == 1 && !(selection.get(0) instanceof RevCommit)) {
                try {
                    BranchInfo branchInfo = BranchInfo.currentLocalBranchInfo(fSelectedRepository.getWorkingFolder()).orElse(null);
                    if(branchInfo != null) {
                        mc = branchInfo.getLatestCommit().map(commit -> new ModelComparison(fSelectedRepository, commit)).orElse(null);
                    }
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
                    ProgressMonitorDialog dialog = new ProgressMonitorDialog(getSite().getShell());
                    
                    try {
                        final ModelComparison mcRef = mc;
                        IRunnable.run(dialog, true, false, monitor -> {
                            monitor.beginTask(Messages.HistoryView_15, IProgressMonitor.UNKNOWN);
                            mcRef.init();
                        });
                    }
                    catch(Exception ex) {
                        throw new IOException(ex);
                    }
                    
                    new CompareDialog(getSite().getShell(), mc).open();
                }
                catch(IOException ex) {
                    ex.printStackTrace();
                    logger.log(Level.SEVERE, "Model Comparison", ex); //$NON-NLS-1$
                    ErrorMessageDialog.open(getSite().getShell(), Messages.HistoryView_3, Messages.HistoryView_12, ex);
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
    
    private class SortStrategyAction extends Action {
        RevSort revSort;
        
        SortStrategyAction(String text, RevSort revSort) {
            super(text, IAction.AS_CHECK_BOX);
            this.revSort = revSort;
        }
        
        @Override
        public void run() {
            for(SortStrategyAction action : sortActions) {
                action.setChecked(revSort);
            }
            
            getHistoryViewer().setSortStrategy(revSort);
        }
        
        void setChecked(RevSort revSort) {
            setChecked(this.revSort == revSort);
        }
    }
    
    private SortStrategyAction[] sortActions = {
            new SortStrategyAction(Messages.HistoryView_8, RevSort.TOPO),
            new SortStrategyAction(Messages.HistoryView_9, RevSort.TOPO_KEEP_BRANCH_TOGETHER),
            new SortStrategyAction(Messages.HistoryView_10, RevSort.COMMIT_TIME_DESC)
    };
    
    /**
     * Select commit in Table
     */
    public void selectCommit(RevCommit commit) {
        if(commit != null) {
            // Sync this in case viewer input is not ready
            Display.getCurrent().asyncExec(() -> {
                if(!getHistoryViewer().getControl().isDisposed()) {
                    getHistoryViewer().setSelection(new StructuredSelection(commit), true);
                }
            });
        }
    }
    
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
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getHistoryViewer().getControl(), ModelRepositoryPlugin.HELP_ID);
        
        // Initialise with whatever is selected in the workbench
        selectionChanged(getSite().getPage().getActivePart(), getSite().getPage().getSelection());
        
        // Add listener
        RepositoryListenerManager.getInstance().addListener(this);
    }
    
    private void createInfoSection(Composite parent) {
        Composite mainComp = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(mainComp);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(mainComp);
 
        // Repository name
        fRepoLabel = new Label(mainComp, SWT.NONE);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(fRepoLabel);
        fRepoLabel.setText(Messages.HistoryView_0);

        // Branches
        Composite branchesComp = new Composite(mainComp, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(branchesComp);
        GridDataFactory.create(SWT.NONE).align(SWT.END, SWT.CENTER).applyTo(branchesComp);

        Label label = new Label(branchesComp, SWT.NONE);
        label.setText(Messages.HistoryView_2);
       
        fBranchesViewer = new BranchesViewer(branchesComp);
        GridDataFactory.create(SWT.NONE).hint(250, SWT.DEFAULT).applyTo(fBranchesViewer.getControl()); 
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
        tableComp.setLayout(new TableColumnLayout(true));
        
        // This ensures a minimum and equal size and no horizontal size creep for the table
        GridDataFactory.create(GridData.FILL_BOTH).hint(100, 50).applyTo(tableComp); 
        
        // History Table
        fHistoryTableViewer = new HistoryTableViewer(tableComp);
        
        // Message Viewer
        fMessageViewer = new RevMessageViewer(tableSash);
        
        tableSash.setWeights(new int[] { 75, 25 });
        
        /*
         * Listen to History Selections to update local Actions
         */
        fHistoryTableViewer.addSelectionChangedListener(event -> {
            updateSelectionActions();
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
        
        fActionAddBranch = new AddBranchAction(getViewSite().getWorkbenchWindow(), Messages.HistoryView_13);
        fActionAddBranch.setEnabled(false);
        
        fActionCommit = new CommitModelAction(getViewSite().getWorkbenchWindow());
        fActionCommit.setEnabled(false);
        
        fActionAddTag = new AddTagAction(getViewSite().getWorkbenchWindow(), Messages.HistoryView_14);
        fActionAddTag.setEnabled(false);
        
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
        
        IMenuManager sortMenu = new MenuManager(Messages.HistoryView_11);
        manager.add(sortMenu);

        RevSort revSort = getHistoryViewer().getSortStrategy();
        for(SortStrategyAction action : sortActions) {
            sortMenu.add(action);
            action.setChecked(revSort);
        }
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
        manager.add(fActionAddBranch);
        manager.add(fActionAddTag);
    }
    
    /**
     * Update the Local Actions depending on the local selection 
     */
    private void updateSelectionActions() {
        IStructuredSelection selection = getHistoryViewer().getStructuredSelection();
        boolean isSingleSelection = selection.size() == 1;
        
        // Selected Working tree or an empty selection, not a RevCommit
        if(!(selection.getFirstElement() instanceof RevCommit revCommit)) {
            fActionExtractCommit.setCommit(null, null);
            fActionRestoreCommit.setCommit(null, null);
            fActionAddBranch.setObjectId(null, null);
            fActionAddTag.setCommit(null, null);
            
            fActionCompare.setEnabled(isSingleSelection || selection.size() == 2);
            fActionCompare.setText(isSingleSelection ? Messages.HistoryView_4 : Messages.HistoryView_3);
            
            // Message Viewer
            fMessageViewer.setRevObject(null);
            
            return;
        }
        
        fActionExtractCommit.setCommit(fSelectedRepository, isSingleSelection ? revCommit : null);
        fActionRestoreCommit.setCommit(fSelectedRepository, isSingleSelection ? revCommit : null);
        
        fActionCompare.setText(Messages.HistoryView_3);
        fActionCompare.setEnabled(selection.size() == 2);
        
        fActionAddBranch.setObjectId(fSelectedRepository, isSingleSelection ? revCommit : null);
        fActionAddTag.setCommit(fSelectedRepository, isSingleSelection ? revCommit : null);
        
        // Commit Viewer
        fMessageViewer.setRevObject(isSingleSelection ? revCommit : null);
    }
    
    /**
     * Update actions depending on repository
     */
    private void updateRepositoryActions() {
        fActionUndoLastCommit.setRepository(fSelectedRepository);
        fActionResetToRemoteCommit.setRepository(fSelectedRepository);
    }
    
    private void fillContextMenu(IMenuManager manager) {
        IStructuredSelection selection = getHistoryViewer().getStructuredSelection();
        boolean isRevCommit = selection.getFirstElement() instanceof RevCommit;
        
        manager.add(fActionCompare);
        manager.add(new Separator());
        
        // Commit Changes when working tree is selected
        if(!isRevCommit) {
            fActionCommit.setRepository(fSelectedRepository);
            manager.add(fActionCommit);
        }
        
        manager.add(fActionExtractCommit);
        manager.add(fActionRestoreCommit);
        manager.add(new Separator());
        manager.add(fActionUndoLastCommit);
        manager.add(fActionResetToRemoteCommit);
        if(isRevCommit) {
            manager.add(new Separator());
            manager.add(fActionAddBranch);
            manager.add(fActionAddTag);
        }
        manager.add(new Separator());
        manager.add(fActionFilter);
        
        IMenuManager sortMenu = new MenuManager(Messages.HistoryView_11);
        manager.add(sortMenu);
        
        RevSort revSort = getHistoryViewer().getSortStrategy();
        for(SortStrategyAction action : sortActions) {
            sortMenu.add(action);
            action.setChecked(revSort);
        }
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
        IArchiRepository selectedRepository = PartUtils.getSelectedArchiRepositoryInWorkbenchPart(part).orElse(null);
        
        // Update if selectedRepository is not the currently selected one 
        if(!Objects.equals(selectedRepository, fSelectedRepository)) {
            // Store last selected
            fSelectedRepository = selectedRepository;

            // Set filtered model object
            updateObjectFilter(selection, false);
            
            // Set label text
            updateLabel();

            // Set History *first*
            getHistoryViewer().setRepository(selectedRepository);
            
            // Set Branches *second* as this will trigger a selection event
            getBranchesViewer().setRepository(selectedRepository);
            
            // If selectedRepository is null update actions, if it isn't null actions will be updated on first selection
            if(selectedRepository == null) {
                updateSelectionActions();
            }

            updateRepositoryActions();
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
            
            // Model Object
            if(selected instanceof IArchimateModelObject) {
                modelObject = (IArchimateModelObject)selected;
            }
            // Is it a diagram EditPart...
            else if(selected instanceof IAdaptable adaptable) {
                IDiagramModelComponent dmc = adaptable.getAdapter(IDiagramModelComponent.class);

                // If it's a concept
                if(dmc instanceof IDiagramModelArchimateComponent dmac) {
                    modelObject = dmac.getArchimateConcept();
                }
                // If it's a diagram model component get the DiagramModel
                else if(dmc != null) {
                    modelObject = dmc.getDiagramModel();
                }
            }
            
            // If a model object is selected and the model is open and equal to the selected object's model
            if(modelObject != null && modelObject.getArchimateModel() == fSelectedRepository.getOpenModel().orElse(null)) {
                getHistoryViewer().setFilteredModelObject(modelObject.getId(), doUpdate);
            }
            // No selected object in a repo part so set object id to null to cancel filtering 
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

            case IRepositoryListener.TAGS_CHANGED -> {
                getHistoryViewer().updateTags();
            }
        }

        // Repository actions need to be updated
        updateRepositoryActions();
    }
    
    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if(adapter == IArchiRepository.class) {
            return adapter.cast(fSelectedRepository);
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
        RepositoryListenerManager.getInstance().removeListener(this);
        
        // Store sort strategy
        ModelRepositoryPlugin.getInstance().getPreferenceStore().putValue(IPreferenceConstants.PREFS_HISTORY_SORT_STRATEGY, getHistoryViewer().getSortStrategy().name());
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
        return Messages.HistoryView_1;
    }
}

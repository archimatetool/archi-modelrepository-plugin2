/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import java.io.File;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
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
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.editor.ui.components.UpdatingTableColumnLayout;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.ExtractModelFromCommitAction;
import com.archimatetool.modelrepository.actions.IModelRepositoryAction;
import com.archimatetool.modelrepository.actions.ResetToRemoteCommitAction;
import com.archimatetool.modelrepository.actions.RestoreCommitAction;
import com.archimatetool.modelrepository.actions.UndoLastCommitAction;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;


/**
 * History Viewpart
 */
public class HistoryView
extends ViewPart
implements IContextProvider, ISelectionListener, IRepositoryListener {

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".historyView"; //$NON-NLS-1$
    public static String HELP_ID = ModelRepositoryPlugin.PLUGIN_ID + ".historyViewHelp"; //$NON-NLS-1$
    
    private Label fRepoLabel;

    private HistoryTableViewer fHistoryTableViewer;
    private RevisionCommentViewer fCommentViewer;
    private BranchesViewer fBranchesViewer;
    
    /*
     * Actions
     */
    private IModelRepositoryAction fActionUndoLastCommit;
    private ExtractModelFromCommitAction fActionExtractCommit;
    private RestoreCommitAction fActionRestoreCommit;
    private ResetToRemoteCommitAction fActionResetToRemoteCommit;
    
    /*
     * Selected repository
     */
    private IArchiRepository fSelectedRepository;

    
    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout());
        
        // Create Info Section
        createInfoSection(parent);
        
        // Create History Table and Comment Viewer
        createHistorySection(parent);

        makeActions();
        hookContextMenu();
        //makeLocalMenuActions();
        makeLocalToolBarActions();
        
        // Register us as a selection provider so that Actions can pick us up
        getSite().setSelectionProvider(getHistoryViewer());
        
        // Listen to workbench selections
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);

        // Register Help Context
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getHistoryViewer().getControl(), HELP_ID);
        
        // Initialise with whatever is selected in the workbench
        selectionChanged(getSite().getWorkbenchWindow().getPartService().getActivePart(),
                getSite().getWorkbenchWindow().getSelectionService().getSelection());
        
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
        
        /*
         * Listen to Branch Selections and forward on to History Viewer
         */
        fBranchesViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                BranchInfo branchInfo = (BranchInfo)event.getStructuredSelection().getFirstElement();
                getHistoryViewer().setSelectedBranch(branchInfo);
                updateActions();
            }
        });
    }
    
    private void createHistorySection(Composite parent) {
        SashForm tableSash = new SashForm(parent, SWT.VERTICAL);
        tableSash.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        Composite tableComp = new Composite(tableSash, SWT.NONE);
        tableComp.setLayout(new UpdatingTableColumnLayout(tableComp));
        
        // This ensures a minumum and equal size and no horizontal size creep for the table
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 100;
        gd.heightHint = 50;
        tableComp.setLayoutData(gd);
        
        // History Table
        fHistoryTableViewer = new HistoryTableViewer(tableComp);
        
        // Comments Viewer
        fCommentViewer = new RevisionCommentViewer(tableSash);
        
        tableSash.setWeights(new int[] { 80, 20 });
        
        /*
         * Listen to History Selections to update local Actions
         */
        fHistoryTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                updateActions();
            }
        });
    }
    
    /**
     * Make local actions
     */
    private void makeActions() {
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
        
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                fillContextMenu(manager);
            }
        });
        
        Menu menu = menuMgr.createContextMenu(getHistoryViewer().getControl());
        getHistoryViewer().getControl().setMenu(menu);
        
        getSite().registerContextMenu(menuMgr, getHistoryViewer());
    }
    
    /**
     * Make Any Local Bar Menu Actions
     */
//    protected void makeLocalMenuActions() {
//        IActionBars actionBars = getViewSite().getActionBars();
//
//        // Local menu items go here
//        IMenuManager manager = actionBars.getMenuManager();
//        manager.add(new Action("&View Management...") {
//            public void run() {
//                MessageDialog.openInformation(getViewSite().getShell(),
//                        "View Management",
//                        "This is a placeholder for the View Management Dialog");
//            }
//        });
//    }

    /**
     * Make Local Toolbar items
     */
    private void makeLocalToolBarActions() {
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager manager = bars.getToolBarManager();

        manager.add(new Separator(IWorkbenchActionConstants.NEW_GROUP));
        
        manager.add(fActionExtractCommit);
        manager.add(fActionRestoreCommit);
        manager.add(new Separator());
        manager.add(fActionUndoLastCommit);
        manager.add(fActionResetToRemoteCommit);
        
        manager.add(new Separator());
    }
    
    /**
     * Update the Local Actions depending on the local selection 
     * @param selection
     */
    private void updateActions() {
        RevCommit commit = (RevCommit)getHistoryViewer().getStructuredSelection().getFirstElement();
        
        // Set commit in these actions
        fActionExtractCommit.setCommit(commit);
        fActionRestoreCommit.setCommit(commit);

        // Set the commit in the Comment Viewer
        fCommentViewer.setCommit(commit);
        
        // Disable these actions if our selected branch in the combo is not actually the current branch
        BranchInfo selectedBranch = (BranchInfo)getBranchesViewer().getStructuredSelection().getFirstElement();
        if(selectedBranch != null && selectedBranch.isCurrentBranch()) {
            fActionRestoreCommit.update();
            fActionUndoLastCommit.update();
            fActionResetToRemoteCommit.update();
        }
        else {
            fActionRestoreCommit.setEnabled(false);
            fActionUndoLastCommit.setEnabled(false);
            fActionResetToRemoteCommit.setEnabled(false);
        }
    }
    
    private void fillContextMenu(IMenuManager manager) {
        manager.add(fActionExtractCommit);
        manager.add(fActionRestoreCommit);
        manager.add(new Separator());
        manager.add(fActionUndoLastCommit);
        manager.add(fActionResetToRemoteCommit);
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
        if(part == null || part == this || selection == null) {
            return;
        }
        
        IArchiRepository selectedRepository = null;
        
        // Repository selected in another part
        if(part.getAdapter(IArchiRepository.class) != null) {
            selectedRepository = part.getAdapter(IArchiRepository.class);
        }
        // Model selected in another part, but is it in a git repo?
        else if(part.getAdapter(IArchimateModel.class) != null) {
            IArchimateModel model = part.getAdapter(IArchimateModel.class);
            File folder = RepoUtils.getWorkingFolderForModel(model);
            if(folder != null) {
                selectedRepository = new ArchiRepository(folder);
            }
        }
        // Repository wrapped in a Repository Ref from Branches View
        else if(part.getAdapter(RepositoryRef.class) != null) {
            selectedRepository = part.getAdapter(RepositoryRef.class).getArchiRepository();
        }
        
        // Update if selectedRepository is different 
        if(selectedRepository != null && !selectedRepository.equals(fSelectedRepository)) {
            // Store last selected
            fSelectedRepository = selectedRepository;

            // Set label text
            fRepoLabel.setText(Messages.HistoryView_0 + " " + selectedRepository.getName()); //$NON-NLS-1$
            
            // Set History first
            getHistoryViewer().doSetInput(selectedRepository);
            
            // Set Branches
            getBranchesViewer().doSetInput(selectedRepository);

            // Update actions
            fActionExtractCommit.setRepository(selectedRepository);
            fActionUndoLastCommit.setRepository(selectedRepository);
            fActionRestoreCommit.setRepository(selectedRepository);
            fActionResetToRemoteCommit.setRepository(selectedRepository);
        }
    }
    
    @Override
    public void repositoryChanged(String eventName, IArchiRepository repository) {
        if(repository.equals(fSelectedRepository)) {
            switch(eventName) {
                case IRepositoryListener.HISTORY_CHANGED:
                    fRepoLabel.setText(Messages.HistoryView_0 + " " + repository.getName()); //$NON-NLS-1$
                    getHistoryViewer().setInput(repository);
                    break;
                    
                case IRepositoryListener.REPOSITORY_DELETED:
                    fRepoLabel.setText(Messages.HistoryView_0);
                    getHistoryViewer().setInput(""); //$NON-NLS-1$
                    fSelectedRepository = null; // Reset this
                    break;
                    
                case IRepositoryListener.REPOSITORY_CHANGED:
                    fRepoLabel.setText(Messages.HistoryView_0 + " " + repository.getName()); //$NON-NLS-1$
                    break;

                case IRepositoryListener.BRANCHES_CHANGED:
                    getBranchesViewer().doSetInput(fSelectedRepository);
                    break;
                    
                default:
                    break;
            }
        }
    }
    
    @Override
    public <T> T getAdapter(Class<T> adapter) {
        /*
         * Return the active repository wrapped in a RepositoryRef so that we don't enable main toolbar/menu items
         */
        if(adapter == RepositoryRef.class) {
            return adapter.cast(new RepositoryRef((IArchiRepository)getHistoryViewer().getInput()));
        }
        
        return super.getAdapter(adapter);
    }
    
    @Override
    public void dispose() {
        super.dispose();
        getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
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

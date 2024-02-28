/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
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
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
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
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.ExtractModelFromCommitAction;
import com.archimatetool.modelrepository.actions.ResetToRemoteCommitAction;
import com.archimatetool.modelrepository.actions.RestoreCommitAction;
import com.archimatetool.modelrepository.actions.UndoLastCommitAction;
import com.archimatetool.modelrepository.dialogs.CompareDialog;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.ModelComparison;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;


/**
 * History Viewpart
 */
public class HistoryView
extends ViewPart
implements IContextProvider, ISelectionListener, IRepositoryListener {
    
    private static Logger logger = Logger.getLogger(HistoryView.class.getName());

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".historyView"; //$NON-NLS-1$
    public static String HELP_ID = ModelRepositoryPlugin.PLUGIN_ID + ".historyViewHelp"; //$NON-NLS-1$
    
    /*
     * Selected repository
     */
    private IArchiRepository fSelectedRepository;
    
    private Label fRepoLabel;

    private HistoryTableViewer fHistoryTableViewer;
    private RevisionCommentViewer fCommentViewer;
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
                if(selection.get(0) instanceof RevCommit && selection.get(1) instanceof RevCommit) {
                    mc = new ModelComparison(fSelectedRepository, (RevCommit)selection.get(0), (RevCommit)selection.get(1));
                }
                // One RevCommit and Working Tree
                else if(selection.get(0) instanceof RevCommit) {
                    mc = new ModelComparison(fSelectedRepository, (RevCommit)selection.get(0));
                }
                // One RevCommit and Working Tree
                else if(selection.get(1) instanceof RevCommit) {
                    mc = new ModelComparison(fSelectedRepository, (RevCommit)selection.get(1));
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
        tableComp.setLayout(new TableColumnLayout());
        
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
        Object firstSelected = selection.getFirstElement();
        boolean isSingleSelection = selection.size() == 1;
        
        fActionCompare.setText(Messages.HistoryView_3);
        
        // Selected Working tree
        if(!(firstSelected instanceof RevCommit revCommit)) {
            fCommentViewer.setCommit(null);
            fActionExtractCommit.setCommit(null);
            fActionRestoreCommit.setCommit(null);
            fActionUndoLastCommit.setEnabled(false);
            fActionResetToRemoteCommit.setEnabled(false);
            fActionCompare.setEnabled(selection.size() > 0 && selection.size() < 3);
            if(isSingleSelection) {
                fActionCompare.setText(Messages.HistoryView_4);
            }
            return;
        }
        
        // Set the commit in the Comment Viewer
        fCommentViewer.setCommit(isSingleSelection ? revCommit : null);
        
        fActionExtractCommit.setCommit(isSingleSelection ? revCommit : null);
        
        // Enable these actions if our selected branch in the combo is the current branch
        BranchInfo selectedBranch = (BranchInfo)getBranchesViewer().getStructuredSelection().getFirstElement();
        if(selectedBranch != null && selectedBranch.isCurrentBranch()) {
            fActionRestoreCommit.setCommit(isSingleSelection ? revCommit : null);
            fActionUndoLastCommit.update();
            fActionResetToRemoteCommit.update();
        }
        // Else disable
        else {
            fActionRestoreCommit.setCommit(null);
            fActionUndoLastCommit.setEnabled(false);
            fActionResetToRemoteCommit.setEnabled(false);
        }
        
        // Compare
        fActionCompare.setEnabled(selection.size() == 2);
    }
    
    private void fillContextMenu(IMenuManager manager) {
        manager.add(fActionCompare);
        manager.add(new Separator());
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
            
            // Set repository in actions *first*
            fActionExtractCommit.setRepository(selectedRepository);
            fActionUndoLastCommit.setRepository(selectedRepository);
            fActionRestoreCommit.setRepository(selectedRepository);
            fActionResetToRemoteCommit.setRepository(selectedRepository);

            // Set History
            getHistoryViewer().setRepository(selectedRepository);
            
            // Set Branches
            getBranchesViewer().setRepository(selectedRepository);
        }
    }
    
    @Override
    public void repositoryChanged(String eventName, IArchiRepository repository) {
        if(Objects.equals(repository, fSelectedRepository)) {
            switch(eventName) {
                case IRepositoryListener.HISTORY_CHANGED:
                    fRepoLabel.setText(Messages.HistoryView_0 + " " + repository.getName()); //$NON-NLS-1$
                    getHistoryViewer().setInput(repository);
                    fCommentViewer.setCommit(null);
                    break;
                    
                case IRepositoryListener.REPOSITORY_DELETED:
                    fRepoLabel.setText(Messages.HistoryView_0);
                    getHistoryViewer().setInput(null);
                    fCommentViewer.setCommit(null);
                    fSelectedRepository = null; // Reset this
                    break;
                    
                case IRepositoryListener.MODEL_RENAMED:
                    fRepoLabel.setText(Messages.HistoryView_0 + " " + repository.getName()); //$NON-NLS-1$
                    break;
                    
                case IRepositoryListener.MODEL_SAVED:
                    getHistoryViewer().modelSaved();
                    break;

                case IRepositoryListener.BRANCHES_CHANGED:
                    getBranchesViewer().setRepository(repository);
                    break;
                    
                default:
                    break;
            }

            // Actions need to be updated
            updateActions();
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

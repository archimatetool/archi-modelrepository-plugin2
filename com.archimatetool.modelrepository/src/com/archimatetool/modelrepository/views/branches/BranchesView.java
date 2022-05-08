/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.branches;

import java.io.File;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.editor.ui.components.UpdatingTableColumnLayout;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;


/**
 * Branches Viewpart
 */
public class BranchesView
extends ViewPart
implements IContextProvider, ISelectionListener, IRepositoryListener {

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".branchesView"; //$NON-NLS-1$
    public static String HELP_ID = ModelRepositoryPlugin.PLUGIN_ID + ".branchesViewHelp"; //$NON-NLS-1$
    
    private IArchiRepository fSelectedRepository;
    
    private Label fRepoLabel;
    private BranchesTableViewer fBranchesTableViewer;
    
    // TODO: Add actions
    // private AddBranchAction fActionAddBranch;
    // private SwitchBranchAction fActionSwitchBranch;
    // private MergeBranchAction fActionMergeBranch;
    // private DeleteBranchAction fActionDeleteBranch;
    // private DeleteStaleBranchesAction fActionDeleteStaleBranches;
    
    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout());
        
        // Create Info Section
        createInfoSection(parent);

        // Create Table Section
        createTableSection(parent);
        
        makeActions();
        hookContextMenu();
        makeLocalToolBarActions();
        
        // Register us as a selection provider so that Actions can pick us up
        getSite().setSelectionProvider(getBranchesViewer());
        
        // Listen to workbench selections
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);

        // Register Help Context
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getBranchesViewer().getControl(), HELP_ID);
        
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
        fRepoLabel.setText(Messages.BranchesView_0);
    }

    private void createTableSection(Composite parent) {
        Composite tableComp = new Composite(parent, SWT.NONE);
        tableComp.setLayout(new UpdatingTableColumnLayout(tableComp));
        
        // This ensures a minumum and equal size and no horizontal size creep for the table
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 100;
        gd.heightHint = 50;
        tableComp.setLayoutData(gd);
        
        // Branches Table
        fBranchesTableViewer = new BranchesTableViewer(tableComp);
        
        /*
         * Listen to Table Selections to update local Actions
         */
        fBranchesTableViewer.addSelectionChangedListener((event) -> {
            updateActions();
        });
        
        fBranchesTableViewer.addDoubleClickListener((event) -> {
            // TODO: switch branch
            //if(fActionSwitchBranch.isEnabled()) {
            //    fActionSwitchBranch.run();
            //}
        });
    }
    
    /**
     * Make local actions
     */
    private void makeActions() {
        //fActionAddBranch = new AddBranchAction(getViewSite().getWorkbenchWindow());
        //fActionAddBranch.setEnabled(false);
        
        //fActionSwitchBranch = new SwitchBranchAction(getViewSite().getWorkbenchWindow());
        //fActionSwitchBranch.setEnabled(false);
        
        //fActionMergeBranch = new MergeBranchAction(getViewSite().getWorkbenchWindow());
        //fActionMergeBranch.setEnabled(false);
        
        //fActionDeleteBranch = new DeleteBranchAction(getViewSite().getWorkbenchWindow());
        //fActionDeleteBranch.setEnabled(false);
        
        //fActionDeleteStaleBranches = new DeleteStaleBranchesAction(getViewSite().getWorkbenchWindow());
        //fActionDeleteStaleBranches.setEnabled(false);
    }

    /**
     * Hook into a right-click menu
     */
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#BranchesPopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                fillContextMenu(manager);
            }
        });
        
        Menu menu = menuMgr.createContextMenu(getBranchesViewer().getControl());
        getBranchesViewer().getControl().setMenu(menu);
        
        getSite().registerContextMenu(menuMgr, getBranchesViewer());
    }
    
    /**
     * Make Local Toolbar items
     */
    protected void makeLocalToolBarActions() {
        //IActionBars bars = getViewSite().getActionBars();
        //IToolBarManager manager = bars.getToolBarManager();

        //manager.add(fActionAddBranch);
        //manager.add(fActionSwitchBranch);
        //manager.add(fActionMergeBranch);
        //manager.add(new Separator());
        //manager.add(fActionDeleteBranch);
        //manager.add(fActionDeleteStaleBranches);
    }
    
    /**
     * Update the Local Actions depending on the local selection 
     * @param selection
     */
    private void updateActions() {
        //BranchInfo branchInfo = (BranchInfo)getBranchesViewer().getStructuredSelection().getFirstElement();
        //fActionSwitchBranch.setBranch(branchInfo);
        //fActionAddBranch.setBranch(branchInfo);
        //fActionMergeBranch.setBranch(branchInfo);
        //fActionDeleteBranch.setBranch(branchInfo);
        //fActionDeleteStaleBranches.setSelection(getBranchesViewer().getStructuredSelection());
    }
    
    private void fillContextMenu(IMenuManager manager) {
        //manager.add(fActionAddBranch);
        //manager.add(fActionSwitchBranch);
        //manager.add(fActionMergeBranch);
        //manager.add(new Separator());
        //manager.add(fActionDeleteBranch);
        //manager.add(fActionDeleteStaleBranches);
    }

    @Override
    public void setFocus() {
        if(getBranchesViewer() != null) {
            getBranchesViewer().getControl().setFocus();
        }
    }
    
    BranchesTableViewer getBranchesViewer() {
        return fBranchesTableViewer;
    }
    
    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if(part == null || part == this || selection == null) {
            return;
        }
        
        Object selected = ((IStructuredSelection)selection).getFirstElement();
        
        IArchiRepository selectedRepository = null;
        
        // Repository ref selected
        if(selected instanceof RepositoryRef) {
            selectedRepository = ((RepositoryRef)selected).getArchiRepository();
        }
        // Model selected, but is it in a git repo?
        else {
            IArchimateModel model = part.getAdapter(IArchimateModel.class);
            File folder = RepoUtils.getLocalRepositoryFolderForModel(model);
            if(folder != null) {
                selectedRepository = new ArchiRepository(folder);
            }
        }
        
        // Update if selectedRepository is different 
        if(selectedRepository != null && !selectedRepository.equals(fSelectedRepository)) {
            // Store last selected
            fSelectedRepository = selectedRepository;

            // Set label text
            fRepoLabel.setText(Messages.BranchesView_0 + " " + selectedRepository.getName()); //$NON-NLS-1$
            
            // Set Branches
            getBranchesViewer().doSetInput(selectedRepository);
            
            // Update actions
            //fActionAddBranch.setRepository(selectedRepository);
            //fActionSwitchBranch.setRepository(selectedRepository);
            //fActionMergeBranch.setRepository(selectedRepository);
            //fActionDeleteBranch.setRepository(selectedRepository);
            //fActionDeleteStaleBranches.setRepository(selectedRepository);
        }
    }
    
    @Override
    public void repositoryChanged(String eventName, IArchiRepository repository) {
        if(repository.equals(fSelectedRepository)) {
            switch(eventName) {
                case IRepositoryListener.HISTORY_CHANGED:
                    getBranchesViewer().doSetInput(repository);
                    break;
                    
                case IRepositoryListener.REPOSITORY_DELETED:
                    fRepoLabel.setText(Messages.BranchesView_0);
                    getBranchesViewer().setInput(""); //$NON-NLS-1$
                    fSelectedRepository = null; // Reset this
                    break;
                    
                case IRepositoryListener.REPOSITORY_CHANGED:
                    fRepoLabel.setText(Messages.BranchesView_0 + " " + repository.getName()); //$NON-NLS-1$
                    break;

                case IRepositoryListener.BRANCHES_CHANGED:
                    getBranchesViewer().doSetInput(repository);
                    break;
                    
                default:
                    break;
            }
        }
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
        return Messages.BranchesView_1;
    }
}

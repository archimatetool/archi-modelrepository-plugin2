/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.branches;

import java.util.Objects;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.SelectionListenerFactory;
import org.eclipse.ui.SelectionListenerFactory.Predicates;
import org.eclipse.ui.part.IContributedContentsView;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.AddBranchAction;
import com.archimatetool.modelrepository.actions.DeleteBranchAction;
import com.archimatetool.modelrepository.actions.MergeBranchAction;
import com.archimatetool.modelrepository.actions.SwitchBranchAction;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.views.PartUtils;


/**
 * Branches Viewpart
 */
public class BranchesView
extends ViewPart
implements IContextProvider, ISelectionListener, IRepositoryListener, IContributedContentsView {

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".branchesView"; //$NON-NLS-1$
    
    private IArchiRepository fSelectedRepository;
    
    private Label fRepoLabel;
    private BranchesTableViewer fBranchesTableViewer;
    
    private AddBranchAction fActionAddBranch;
    private SwitchBranchAction fActionSwitchBranch;
    private DeleteBranchAction fActionDeleteBranch;
    private MergeBranchAction fActionMergeBranch;
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
        
        // Listen to workbench selections using a SelectionListenerFactory
        getSite().getPage().addSelectionListener(SelectionListenerFactory.createListener(this,
                                                 Predicates.alreadyDeliveredAnyPart.and(Predicates.selfMute)));

        // Register Help Context
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getBranchesViewer().getControl(), ModelRepositoryPlugin.HELP_ID);
        
        // Initialise with whatever is selected in the workbench
        selectionChanged(getSite().getPage().getActivePart(), getSite().getPage().getSelection());
        
        // Add listener
        RepositoryListenerManager.getInstance().addListener(this);
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
        tableComp.setLayout(new TableColumnLayout());
        
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
        fBranchesTableViewer.addSelectionChangedListener(event -> {
            updateActions();
        });
        
        fBranchesTableViewer.addDoubleClickListener(event -> {
            if(fActionSwitchBranch.isEnabled()) {
                fActionSwitchBranch.run();
            }
        });
    }
    
    /**
     * Make local actions
     */
    private void makeActions() {
        fActionAddBranch = new AddBranchAction(getViewSite().getWorkbenchWindow(), Messages.BranchesView_2);
        fActionAddBranch.setEnabled(false);
        
        fActionSwitchBranch = new SwitchBranchAction(getViewSite().getWorkbenchWindow());
        fActionSwitchBranch.setEnabled(false);
        
        fActionDeleteBranch = new DeleteBranchAction(getViewSite().getWorkbenchWindow());
        fActionDeleteBranch.setEnabled(false);
        
        fActionMergeBranch = new MergeBranchAction(getViewSite().getWorkbenchWindow());
        fActionMergeBranch.setEnabled(false);
        
        //fActionDeleteStaleBranches = new DeleteStaleBranchesAction(getViewSite().getWorkbenchWindow());
        //fActionDeleteStaleBranches.setEnabled(false);
    }

    /**
     * Hook into a right-click menu
     */
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#BranchesPopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(manager -> {
            fillContextMenu(manager);
        });
        
        Menu menu = menuMgr.createContextMenu(getBranchesViewer().getControl());
        getBranchesViewer().getControl().setMenu(menu);
        
        getSite().registerContextMenu(menuMgr, getBranchesViewer());
    }
    
    /**
     * Make Local Toolbar items
     */
    protected void makeLocalToolBarActions() {
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager manager = bars.getToolBarManager();

        manager.add(fActionAddBranch);
        manager.add(fActionSwitchBranch);
        manager.add(fActionMergeBranch);
        manager.add(new Separator());
        manager.add(fActionDeleteBranch);
        //manager.add(fActionDeleteStaleBranches);
    }
    
    /**
     * Update the Local Actions depending on the local selection 
     */
    private void updateActions() {
        BranchInfo branchInfo = (BranchInfo)getBranchesViewer().getStructuredSelection().getFirstElement();
        fActionAddBranch.setObjectId(fSelectedRepository, branchInfo != null ? branchInfo.getRef().getObjectId() : null);
        fActionSwitchBranch.setBranch(branchInfo);
        fActionMergeBranch.setBranch(branchInfo);
        fActionDeleteBranch.setBranch(branchInfo);
        //fActionDeleteStaleBranches.setBranch(branchInfo);
    }
    
    private void fillContextMenu(IMenuManager manager) {
        manager.add(fActionAddBranch);
        manager.add(fActionSwitchBranch);
        manager.add(fActionMergeBranch);
        manager.add(new Separator());
        manager.add(fActionDeleteBranch);
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
        IArchiRepository selectedRepository = PartUtils.getSelectedArchiRepositoryInWorkbenchPart(part).orElse(null);
        
        // Update if selectedRepository is different 
        if(!Objects.equals(selectedRepository, fSelectedRepository)) {
            // Store last selected
            fSelectedRepository = selectedRepository;

            // Set label text
            fRepoLabel.setText(selectedRepository != null ? Messages.BranchesView_0 + " " + selectedRepository.getName() : Messages.BranchesView_0); //$NON-NLS-1$
            
            // Set Branches
            getBranchesViewer().doSetInput(selectedRepository);
        }
    }
    
    @Override
    public void repositoryChanged(String eventName, IArchiRepository repository) {
        // Update only if the repository change is the currently selected one
        if(!Objects.equals(repository, fSelectedRepository)) {
            return;
        }
        
        switch(eventName) {
            case IRepositoryListener.HISTORY_CHANGED -> {
                getBranchesViewer().doSetInput(repository);
            }

            case IRepositoryListener.REPOSITORY_DELETED -> {
                fRepoLabel.setText(Messages.BranchesView_0);
                getBranchesViewer().setInput(null);
                fSelectedRepository = null; // Reset this
            }

            case IRepositoryListener.MODEL_RENAMED -> {
                fRepoLabel.setText(Messages.BranchesView_0 + " " + repository.getName()); //$NON-NLS-1$
            }

            case IRepositoryListener.BRANCHES_CHANGED -> {
                getBranchesViewer().doSetInput(repository);
                updateActions(); // These need to be updated (switching branch doesn't generate a new selection event)
            }
        }
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
        return Messages.BranchesView_1;
    }
}

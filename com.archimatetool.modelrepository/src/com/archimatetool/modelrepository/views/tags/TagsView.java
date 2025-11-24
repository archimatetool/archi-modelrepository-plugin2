/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.tags;

import java.util.Arrays;
import java.util.Objects;

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
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
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

import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.actions.DeleteTagsAction;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.repository.TagInfo;
import com.archimatetool.modelrepository.views.PartUtils;
import com.archimatetool.modelrepository.views.history.HistoryView;
import com.archimatetool.modelrepository.views.history.RevMessageViewer;


/**
 * Tags Viewpart
 */
public class TagsView
extends ViewPart
implements IContextProvider, ISelectionListener, IRepositoryListener, IContributedContentsView {

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".tagsView"; //$NON-NLS-1$
    
    private IArchiRepository fSelectedRepository;
    
    private Label fRepoLabel;
    private TagsTableViewer fTagsTableViewer;
    private RevMessageViewer fMessageViewer;
    
    private DeleteTagsAction fActionDeleteTags;
    private IAction fActionRevealCommit;
    
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
        getSite().setSelectionProvider(getTagsViewer());
        
        // Listen to workbench selections using a SelectionListenerFactory
        getSite().getPage().addSelectionListener(SelectionListenerFactory.createListener(this,
                                                 Predicates.alreadyDeliveredAnyPart.and(Predicates.selfMute)));

        // Register Help Context
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getTagsViewer().getControl(), ModelRepositoryPlugin.HELP_ID);
        
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
        fRepoLabel.setText(Messages.TagsView_0);
    }

    private void createTableSection(Composite parent) {
        SashForm tableSash = new SashForm(parent, SWT.VERTICAL);
        tableSash.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        Composite tableComp = new Composite(tableSash, SWT.NONE);
        tableComp.setLayout(new TableColumnLayout());
        
        // This ensures a minumum and equal size and no horizontal size creep for the table
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 100;
        gd.heightHint = 50;
        tableComp.setLayoutData(gd);
        
        // Tags Table
        fTagsTableViewer = new TagsTableViewer(tableComp);
        
        // Message Viewer
        fMessageViewer = new RevMessageViewer(tableSash);
        
        tableSash.setWeights(new int[] { 75, 25 });
        
        /*
         * Listen to Table Selections to update local Actions
         */
        fTagsTableViewer.addSelectionChangedListener(event -> {
            updateActions();
        });
        
        fTagsTableViewer.addDoubleClickListener(event -> {
            fActionRevealCommit.run();
        });
    }
    
    /**
     * Make local actions
     */
    private void makeActions() {
        // Delete
        fActionDeleteTags = new DeleteTagsAction(getViewSite().getWorkbenchWindow());
        fActionDeleteTags.setEnabled(false);
        
        // Reveal Commit
        fActionRevealCommit = new Action(Messages.TagsView_1, IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_HISTORY_VIEW)) {
            @Override
            public void run() {
                if(isEnabled()) {
                    HistoryView historyView = (HistoryView)ViewManager.showViewPart(HistoryView.ID, false);
                    if(historyView != null) {
                        TagInfo tagInfo = (TagInfo)fTagsTableViewer.getStructuredSelection().getFirstElement();
                        historyView.selectCommit(tagInfo.getCommit().orElse(null));
                    }
                }
            }
        };
        fActionRevealCommit.setEnabled(false);
    }

    /**
     * Hook into a right-click menu
     */
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#TagsPopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(manager -> {
            fillContextMenu(manager);
        });
        
        Menu menu = menuMgr.createContextMenu(getTagsViewer().getControl());
        getTagsViewer().getControl().setMenu(menu);
        
        getSite().registerContextMenu(menuMgr, getTagsViewer());
    }
    
    /**
     * Make Local Toolbar items
     */
    protected void makeLocalToolBarActions() {
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager manager = bars.getToolBarManager();
        manager.add(fActionDeleteTags);
    }
    
    /**
     * Update the Local Actions depending on the local selection 
     */
    private void updateActions() {
        TagInfo tagInfo = (TagInfo)getTagsViewer().getStructuredSelection().getFirstElement();
        
        TagInfo[] tagInfos = Arrays.stream(getTagsViewer().getStructuredSelection().toArray())
                                   .map(obj -> (TagInfo) obj) // Cast to TagInfo
                                   .toArray(TagInfo[]::new);
        
        fActionDeleteTags.setTags(fSelectedRepository, tagInfos);
        
        fActionRevealCommit.setEnabled(tagInfo != null ? !tagInfo.isOrphaned() : false);
        fMessageViewer.setRevObject(tagInfo != null ? tagInfo.getTag().orElse(null) : null);
    }
    
    private void fillContextMenu(IMenuManager manager) {
        manager.add(fActionRevealCommit);
        manager.add(new Separator());
        manager.add(fActionDeleteTags);
    }

    @Override
    public void setFocus() {
        if(getTagsViewer() != null) {
            getTagsViewer().getControl().setFocus();
        }
    }
    
    TagsTableViewer getTagsViewer() {
        return fTagsTableViewer;
    }
    
    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        IArchiRepository selectedRepository = PartUtils.getSelectedArchiRepositoryInWorkbenchPart(part).orElse(null);
        
        // Update if selectedRepository is different 
        if(!Objects.equals(selectedRepository, fSelectedRepository)) {
            // Store last selected
            fSelectedRepository = selectedRepository;

            // Set label text
            fRepoLabel.setText(selectedRepository != null ? Messages.TagsView_0 + " " + selectedRepository.getName() : Messages.TagsView_0); //$NON-NLS-1$
            
            // Set Tags
            getTagsViewer().doSetInput(selectedRepository);
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
                getTagsViewer().doSetInput(repository);
            }

            case IRepositoryListener.REPOSITORY_DELETED -> {
                fRepoLabel.setText(Messages.TagsView_0);
                getTagsViewer().setInput(null);
                fSelectedRepository = null; // Reset this
            }

            case IRepositoryListener.MODEL_RENAMED -> {
                fRepoLabel.setText(Messages.TagsView_0 + " " + repository.getName());  //$NON-NLS-1$
            }

            case IRepositoryListener.TAGS_CHANGED -> {
                getTagsViewer().doSetInput(repository);
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
        return Messages.TagsView_2;
    }
}

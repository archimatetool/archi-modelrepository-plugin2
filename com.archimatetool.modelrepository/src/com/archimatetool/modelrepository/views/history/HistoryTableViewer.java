/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;

import com.archimatetool.editor.ui.FontFactory;
import com.archimatetool.editor.ui.ThemeUtils;
import com.archimatetool.editor.utils.PlatformUtils;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.ModelObjectIdFilter;


/**
 * History Table Viewer
 */
public class HistoryTableViewer extends TableViewer {
    
    private static Logger logger = Logger.getLogger(HistoryTableViewer.class.getName());
    
    private RevCommit fLocalCommit, fRemoteCommit;
    private BranchInfo fSelectedBranch;
    
    private enum CommitStatus {
        MERGED,
        AHEAD,
        BEHIND
    }
    
    // Commit IDs mapped to commits ahead/behind remote
    private Map<String, CommitStatus> commitStatusMap;
    
    // Tags mapping commit ID to list of tags
    private Map<String, List<String>> tagMap;
    
    private boolean hasWorkingTree;
    
    private String filteredObjectId;
    
    private RevSort revSort;
    
    public HistoryTableViewer(Composite parent) {
        super(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);
        
        setup(parent);
        
        setContentProvider(new HistoryContentProvider());
        setLabelProvider(new HistoryLabelProvider());
        
        ColumnViewerToolTipSupport.enableFor(this);
        
        setUseHashlookup(true);
        
        // Get sort strategy from Preferences
        // TOPO_KEEP_BRANCH_TOGETHER keeps local and Remote commits together regardless of commit time
        // But TOPO maintains the order when merging a branch
        try {
            revSort = RevSort.valueOf(ModelRepositoryPlugin.getInstance().getPreferenceStore().getString(IPreferenceConstants.PREFS_HISTORY_SORT_STRATEGY));
        }
        catch(Exception ex) {
            revSort = RevSort.TOPO;
        }
    }

    private void setup(Composite parent) {
        getTable().setHeaderVisible(true);
        getTable().setLinesVisible(false);
        
        TableColumnLayout tableLayout = (TableColumnLayout)parent.getLayout();
        
        TableViewerColumn column = new TableViewerColumn(this, SWT.NONE, 0);
        column.getColumn().setText(Messages.HistoryTableViewer_1);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(50, false));

        column = new TableViewerColumn(this, SWT.NONE, 1);
        column.getColumn().setText(Messages.HistoryTableViewer_2);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));

        column = new TableViewerColumn(this, SWT.NONE, 2);
        column.getColumn().setText(Messages.HistoryTableViewer_3);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
    
        column = new TableViewerColumn(this, SWT.NONE, 3);
        column.getColumn().setText(Messages.HistoryTableViewer_11);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
    
        column = new TableViewerColumn(this, SWT.NONE, 4);
        column.getColumn().setText(Messages.HistoryTableViewer_0);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
    }
    
    void setRepository(IArchiRepository archiRepo) {
        if(archiRepo == null) {
            fSelectedBranch = null;
            setInput(null);
            return;
        }
        
        // Get basic current LocalBranch Info
        try {
            fSelectedBranch = BranchInfo.currentLocalBranchInfo(archiRepo.getWorkingFolder());
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Branch Status", ex); //$NON-NLS-1$
        }

        setInputAndSelect(archiRepo);
    }
    
    void setSelectedBranch(BranchInfo branchInfo) {
        if(Objects.equals(branchInfo, fSelectedBranch)) {
            return;
        }

        fSelectedBranch = branchInfo;
        setInputAndSelect(getInput());
    }
    
    void modelSaved() {
        // If we have a working tree then update history view
        if(getHasWorkingTree(getInput()) != hasWorkingTree) {
            setInput(getInput());
            
            // Avoid bogus horizontal scrollbar cheese
            Display.getCurrent().asyncExec(() -> {
                if(!getTable().isDisposed()) {
                    getTable().getParent().layout();
                }
            });
        }
    }
    
    void updateTags() {
        try(GitUtils utils = GitUtils.open(getInput().getWorkingFolder())) {
            tagMap = utils.getTagsMap();
            refresh();
        }
        catch(GitAPIException | IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Get Tags Map", ex); //$NON-NLS-1$
        }
    }
    
    private void setInputAndSelect(IArchiRepository archiRepo) {
        Display.getCurrent().asyncExec(() -> {
            if(!getTable().isDisposed()) {
                setInput(archiRepo);
            }
        });
        
        Display.getCurrent().asyncExec(() -> {
            if(!getTable().isDisposed()) {
                // Avoid bogus horizontal scrollbar cheese
                getTable().getParent().layout();
                
                // Select first row
                // This will ensure we don't select the current row index from the previously selected repo
                Object element = getElementAt(0);
                if(element != null) {
                    setSelection(new StructuredSelection(element), true);
                }
            }
        });
    }
    
    /**
     * Set an object's id to filter on
     */
    void setFilteredModelObject(String objectId, boolean doUpdate) {
        if(!Objects.equals(objectId, filteredObjectId)) {
            filteredObjectId = objectId;
            if(doUpdate && getInput() != null) { // Check for null input! This can happen because we use async in setInputAndSelect
                setInputAndSelect(getInput());
            }
        }
    }
    
    void setSortStrategy(RevSort revSort) {
        if(this.revSort != revSort) {
            this.revSort = revSort;
            setInputAndSelect(getInput());
        }
    }
    
    RevSort getSortStrategy() {
        return revSort;
    }
    
    boolean hasWorkingTree() {
        return hasWorkingTree;
    }
    
    private boolean getHasWorkingTree(IArchiRepository repo) {
        try {
            return repo != null && repo.hasChangesToCommit() && fSelectedBranch != null && fSelectedBranch.isCurrentBranch();
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Has changes to commit", ex); //$NON-NLS-1$
            return false;
        }
    }
    
    @Override
    public IArchiRepository getInput() {
        return (IArchiRepository)super.getInput();
    }
    
    private CommitStatus getCommitStatus(RevCommit commit) {
        CommitStatus commitStatus = commitStatusMap.get(commit.getName());
        return commitStatus != null ? commitStatus : CommitStatus.MERGED;
    }
    
    // ===============================================================================================
    // ===================================== Table Model =============================================
    // ===============================================================================================
    
    /**
     * The Model for the Table
     */
    private class HistoryContentProvider implements ILazyContentProvider {
        List<RevCommit> commits;
        
        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
            if(oldInput == null && newInput == null) { // nothing to do
                return;
            }
            
            dispose();
            
            if(!(newInput instanceof IArchiRepository repo // Must be non-null repo
                    && repo.getWorkingFolder().exists()    // Local Repo might have been deleted
                    && fSelectedBranch != null)) {         // Must have branch selected
                setItemCount(0);
                return;
            }
            
            commits = new ArrayList<>();
            commitStatusMap = new HashMap<>();
            
            // Get this now
            hasWorkingTree = getHasWorkingTree(repo);
            
            try {
                loadCommits(repo);
                setItemCount(hasWorkingTree ? commits.size() + 1 : commits.size());
            }
            catch(IOException | GitAPIException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, "RevWalk", ex); //$NON-NLS-1$
                dispose();
                setItemCount(0);
            }
        }
        
        /**
         * Loads all commits and keeps a reference to the current local and remote commits
         */
        void loadCommits(IArchiRepository repo) throws IOException, GitAPIException {
            try(Git git = Git.open(repo.getWorkingFolder())) {
                try(RevWalk revWalk = new RevWalk(git.getRepository())) {
                    revWalk.sort(revSort);
                    
                    // Add a filter to show only commits that contain an object's Id
                    if(filteredObjectId != null) {
                        revWalk.setRevFilter(new ModelObjectIdFilter(filteredObjectId));
                    }

                    // Set the local branch commit start
                    ObjectId localCommitID = git.getRepository().resolve(fSelectedBranch.getLocalBranchName());
                    if(localCommitID != null) {
                        fLocalCommit = revWalk.parseCommit(localCommitID);
                        revWalk.markStart(fLocalCommit);
                    }

                    // Set the remote branch commit start
                    ObjectId remoteCommitID = git.getRepository().resolve(fSelectedBranch.getRemoteBranchName());
                    if(remoteCommitID != null) {
                        fRemoteCommit = revWalk.parseCommit(remoteCommitID);
                        revWalk.markStart(fRemoteCommit);
                    }

                    // Add the commits
                    for(RevCommit commit : revWalk) {
                        commits.add(commit);
                    }
                }
                
                tagMap = GitUtils.wrap(git.getRepository()).getTagsMap();
                
                getCommitStatus(git);
            }
        }
        
        /**
         * We want to show a different colored line between the local and remote commit if there are ahead/behind commits.
         * Store the IDs of commits that are ahead/behind mapped to their status.
         */
        void getCommitStatus(Git git) throws IOException {
            // There will only be ahead/behind commits if we have a tracked remote branch
            if(fLocalCommit != null && fRemoteCommit != null) {
                // Local commits that are ahead of the remote commit
                addCommitStatus(git, fRemoteCommit, fLocalCommit, CommitStatus.AHEAD);
                
                // Local commits that are behind the remote commit
                addCommitStatus(git, fLocalCommit, fRemoteCommit, CommitStatus.BEHIND);
            }
        }
        
        /**
         * Collect commits that are ahead/behind remote so we can show their status
         * @param sinceId The oldest commit
         * @param untilId The newest commit
         */
        private void addCommitStatus(Git git, ObjectId sinceId, ObjectId untilId, CommitStatus status) throws IOException {
            // This is the equivalent of git.log().addRange(sinceId, untilId).call()
            try(RevWalk revWalk = new RevWalk(git.getRepository())) {
                revWalk.setRetainBody(false);
                revWalk.markStart(revWalk.parseCommit(untilId));
                revWalk.markUninteresting(revWalk.parseCommit(sinceId));
                for(RevCommit commit : revWalk) {
                    commitStatusMap.put(commit.getName(), status);
                }
            }
        }
        
        @Override
        public void updateElement(int index) {
            // If this is the working tree row insert dummy object
            if(index == 0 && hasWorkingTree) {
                replace(new String(), index);
                return;
            }
            
            // The real index of the RevCommit depends on whether we are showing the working tree row
            int realIndex = hasWorkingTree ? index - 1 : index;
            
            replace(commits.get(realIndex), index);
        }

        @Override
        public void dispose() {
            commits = null;
            fLocalCommit = null;
            fRemoteCommit = null;
            commitStatusMap = null;
            tagMap = null;
        }
    }
    
    // ===============================================================================================
	// ===================================== Label Model =============================================
	// ===============================================================================================

    private class HistoryLabelProvider extends StyledCellLabelProvider {
        final DateFormat dateFormat = DateFormat.getDateTimeInstance();
        
        final Color defaultColor = ThemeUtils.isDarkTheme() ? new Color(250, 250, 250) : new Color(0, 0, 0);
        final Color aheadColor = new Color(0, 200, 64);
        final Color behindColor = new Color(255, 64, 0);
        
        // Each OS has a different image indent
        final int imageGap = PlatformUtils.isWindows() ? 8 : PlatformUtils.isMac() ? 11 : 10;
        final int circleDiameter = 8;
        
        private String getColumnText(RevCommit commit, int columnIndex) {
            return switch(columnIndex) {
                // Short Message
                case 0 -> {
                    yield commit.getShortMessage();
                }
                // Author Name
                case 1 -> {
                    PersonIdent personIdent = commit.getAuthorIdent();
                    yield personIdent != null ? personIdent.getName() : null;
                }
                // Date
                case 2 -> {
                    yield dateFormat.format(new Date(commit.getCommitTime() * 1000L));
                }
                // Tags
                case 3 -> {
                    List<String> tags = tagMap.get(commit.getName());
                    yield tags == null ? "" : String.join(", ", tags); //$NON-NLS-1$ //$NON-NLS-2$
                }
                // Short SHA-1
                case 4 -> {
                    yield commit.getName().substring(0, 8);
                }
                default -> {
                    yield null;
                }
            };
        }

        @Override
        public void update(ViewerCell cell) {
            // Working Tree
            if(!(cell.getElement() instanceof RevCommit commit)) {
                if(cell.getColumnIndex() == 0) {
                    cell.setForeground(null);
                    cell.setText(Messages.HistoryTableViewer_7);
                    cell.setFont(FontFactory.SystemFontBold);
                    Image image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_BLANK);
                    cell.setImage(image);
                }
                return;
            }
            
            cell.setText(getColumnText(commit, cell.getColumnIndex()));
            
            if(cell.getColumnIndex() == 0) {
                Image image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_BLANK); // Need a blank icon for indent on Mac/Linux

                // Local/Remote are same commit
                if(commit.equals(fLocalCommit) && commit.equals(fRemoteCommit)) {
                    image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_HISTORY_VIEW);
                }
                // Local commit
                else if(commit.equals(fLocalCommit)) {
                    image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_LOCAL);
                }
                // Remote commit
                else if(commit.equals(fRemoteCommit)) {
                    image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_REMOTE);
                }
                
                cell.setImage(image);
            }
        }
        
        @Override
        protected void paint(Event event, Object element) {
            if(!(element instanceof RevCommit commit)     // Working Tree
                    || event.index != 0                   // not column 0
                    || commit.equals(fLocalCommit)        // local commit
                    || commit.equals(fRemoteCommit)) {    // remote commit
                super.paint(event, element);
                return;
            }
            
            // remember color to restore the GC later
            Color oldForeground = event.gc.getForeground();

            event.gc.setAntialias(SWT.ON);
            event.gc.setLineWidth(2);

            CommitStatus commitStatus = getCommitStatus(commit);
            
            Color color = commitStatus == CommitStatus.AHEAD ? aheadColor :
                          commitStatus == CommitStatus.BEHIND ? behindColor :
                          defaultColor;

            event.gc.setForeground(color);

            // circle
            event.gc.drawOval(event.x + imageGap - (circleDiameter / 2), event.y + (event.height - circleDiameter) / 2, circleDiameter, circleDiameter);

            // top line
            event.gc.drawLine(event.x + imageGap, event.y, event.x + imageGap, event.y + (event.height - circleDiameter) / 2);

            // bottom line if not the oldest commit
            List<RevCommit> commits = ((HistoryContentProvider)getContentProvider()).commits;
            if(commits != null && commit != commits.get(commits.size() - 1)) {
                event.gc.drawLine(event.x + imageGap, event.y + (event.height + circleDiameter) / 2, event.x + imageGap, event.y + event.height);
            }

            // restore color
            event.gc.setForeground(oldForeground);
            
            super.paint(event, element);
        }
        
        @Override
        public String getToolTipText(Object element) {
            // Working Tree
            if(!(element instanceof RevCommit commit)) {
                return Messages.HistoryTableViewer_8;
            }
            
            CommitStatus commitStatus = getCommitStatus(commit);

            String s = ""; //$NON-NLS-1$
            
            // Local/Remote are same commit
            if(commit.equals(fLocalCommit) && commit.equals(fRemoteCommit)) {
                s += Messages.HistoryTableViewer_4 + " "; //$NON-NLS-1$
            }
            // Local commit
            else if(commit.equals(fLocalCommit)) {
                s += Messages.HistoryTableViewer_5 + " "; //$NON-NLS-1$
            }
            // Remote commit
            else if(commit.equals(fRemoteCommit)) {
                s += Messages.HistoryTableViewer_6 + " "; //$NON-NLS-1$
            }
            // Ahead (local commits)
            else if(commitStatus == CommitStatus.AHEAD) {
                s += Messages.HistoryTableViewer_9 + " "; //$NON-NLS-1$
            }
            // Behind (remote commits)
            else if(commitStatus == CommitStatus.BEHIND) {
                s += Messages.HistoryTableViewer_10 + " "; //$NON-NLS-1$
            }

            s += commit.getShortMessage().trim();

            return s;
        }
    }
}

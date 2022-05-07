/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.editor.ui.components.ExtendedTitleAreaDialog;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Commit Dialog
 * 
 * @author Phil Beauvoir
 */
public class CommitDialog extends ExtendedTitleAreaDialog {
    
    private static Logger logger = Logger.getLogger(CommitDialog.class.getName());
    
    private static String DIALOG_ID = "CommitDialog"; //$NON-NLS-1$
    
    private Text fTextUserName, fTextUserEmail, fTextCommitMessage;
    private Button fAmendLastCommitCheckbox;
    
    private String fCommitMessage;
    private boolean fAmend;
    
    private IArchiRepository fRepository;
    
    public CommitDialog(Shell parentShell, IArchiRepository repo) {
        super(parentShell, DIALOG_ID);
        setTitle(Messages.CommitDialog_0);
        fRepository = repo;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(Messages.CommitDialog_0);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setMessage(Messages.CommitDialog_1, IMessageProvider.INFORMATION);
        setTitleImage(IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.BANNER_COMMIT));

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);
        
        // Repo and branch
        String shortBranchName = ""; //$NON-NLS-1$
        
        try {
            shortBranchName = fRepository.getCurrentLocalBranchName();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }

        Label label = new Label(container, SWT.NONE);
        label.setText(Messages.CommitDialog_2);
        
        label = new Label(container, SWT.NONE);
        label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        label.setText(fRepository.getName() + " [" + shortBranchName + "]"); //$NON-NLS-1$ //$NON-NLS-2$
        
        // User name & email
        String userName = ""; //$NON-NLS-1$
        String userEmail = ""; //$NON-NLS-1$
        
        try {
            PersonIdent result = fRepository.getUserDetails();
            userName = result.getName();
            userEmail = result.getEmailAddress();
        }
        catch(IOException ex) {
            logger.log(Level.WARNING, "Could not get user details", ex); //$NON-NLS-1$
            ex.printStackTrace();
        }

        label = new Label(container, SWT.NONE);
        label.setText(Messages.CommitDialog_3);
        
        fTextUserName = UIUtils.createSingleTextControl(container, SWT.BORDER, false);
        fTextUserName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fTextUserName.setText(userName);
        
        label = new Label(container, SWT.NONE);
        label.setText(Messages.CommitDialog_4);
        
        fTextUserEmail = UIUtils.createSingleTextControl(container, SWT.BORDER, false);
        fTextUserEmail.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fTextUserEmail.setText(userEmail);
        
        label = new Label(container, SWT.NONE);
        label.setText(Messages.CommitDialog_5);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        label.setLayoutData(gd);
        
        fTextCommitMessage = new Text(container, SWT.BORDER | SWT.V_SCROLL | SWT.WRAP | SWT.MULTI);
        gd = new GridData(GridData.FILL_BOTH);
        gd.horizontalSpan = 2;
        fTextCommitMessage.setLayoutData(gd);
        
        // Tab Traversal and Enter key
        UIUtils.applyTraverseListener(fTextCommitMessage, SWT.TRAVERSE_TAB_NEXT | SWT.TRAVERSE_TAB_PREVIOUS | SWT.TRAVERSE_RETURN);
        
        fAmendLastCommitCheckbox = new Button(container, SWT.CHECK);
        fAmendLastCommitCheckbox.setText(Messages.CommitDialog_6);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        fAmendLastCommitCheckbox.setLayoutData(gd);
        fAmendLastCommitCheckbox.setEnabled(isAmendAllowed());
        
        if(!StringUtils.isSet(userName)) {
            fTextUserName.setFocus();
        }
        else if(!StringUtils.isSet(userEmail)) {
            fTextUserEmail.setFocus();
        }
        else {
            fTextCommitMessage.setFocus();
        }
        
        return area;
    }

    @Override
    protected boolean isResizable() {
        return true;
    }
    
    @Override
    protected Point getDefaultDialogSize() {
        return new Point(600, 450);
    }
    
    public String getCommitMessage() {
        return fCommitMessage;
    }
    
    public boolean getAmend() {
        return fAmend;
    }

    @Override
    protected void okPressed() {
        fCommitMessage = fTextCommitMessage.getText();
        fAmend = fAmendLastCommitCheckbox.getSelection();
        
        // Store user name and email
        try {
            fRepository.saveUserDetails(fTextUserName.getText().trim(), fTextUserEmail.getText().trim());
        }
        catch(IOException ex) {
            logger.log(Level.WARNING, "Could not save user details", ex); //$NON-NLS-1$
            ex.printStackTrace();
        }
        
        super.okPressed();
    }

    /**
     * An amend of last commit is allowed
     * If HEAD and remote are not the same AND
     * The latest local commit does not have more than one parent (i.e last commit was a merge)
     */
    private boolean isAmendAllowed() {
        try {
            return !fRepository.isHeadAndRemoteSame() && getLatestLocalCommitParentCount() < 2;
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Could not get amend allowed", ex); //$NON-NLS-1$
            ex.printStackTrace();
        }
        
        return false;
    }
    
    private int getLatestLocalCommitParentCount() throws IOException {
        try(Repository repository = Git.open(fRepository.getLocalRepositoryFolder()).getRepository()) {
            Ref head = repository.exactRef(Constants.HEAD);
            if(head == null) {
                return 0;
            }
            
            ObjectId objectID = head.getObjectId();
            if(objectID == null) {
                return 0;
            }

            try(RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(objectID);
                revWalk.dispose();
                return commit.getParentCount();
            }
        }
    }
}
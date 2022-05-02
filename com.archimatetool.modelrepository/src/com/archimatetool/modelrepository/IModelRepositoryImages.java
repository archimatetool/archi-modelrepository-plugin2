/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository;

import com.archimatetool.editor.ui.ImageFactory;




/**
 * Image Factory for this application
 * 
 * @author Phillip Beauvoir
 */
public interface IModelRepositoryImages {
    
    ImageFactory ImageFactory = new ImageFactory(ModelRepositoryPlugin.INSTANCE);

    String IMGPATH = "img/"; //$NON-NLS-1$
    
    String ICON_PLUGIN = IMGPATH + "plugin.png"; //$NON-NLS-1$
    
    String ICON_ABORT = IMGPATH + "abort.png"; //$NON-NLS-1$
    String ICON_BRANCH = IMGPATH + "branch_obj.png"; //$NON-NLS-1$
    String ICON_BRANCHES = IMGPATH + "branches_obj.png"; //$NON-NLS-1$
    String ICON_CLONE = IMGPATH + "cloneGit.png"; //$NON-NLS-1$
    String ICON_COMMIT = IMGPATH + "commit.png"; //$NON-NLS-1$
    String ICON_CREATE_REPOSITORY = IMGPATH + "createRepository.png"; //$NON-NLS-1$
    String ICON_DELETE = IMGPATH + "delete.png"; //$NON-NLS-1$
    String ICON_GROUP = IMGPATH + "group.png"; //$NON-NLS-1$
    String ICON_HISTORY_VIEW = IMGPATH + "history_view.png"; //$NON-NLS-1$
    String ICON_MERGE = IMGPATH + "merge.png"; //$NON-NLS-1$
    String ICON_MODEL = IMGPATH + "elements_obj.png"; //$NON-NLS-1$
    String ICON_NEW_BRANCH = IMGPATH + "new_branch_obj.png"; //$NON-NLS-1$
    String ICON_PUSH = IMGPATH + "push.png"; //$NON-NLS-1$
    String ICON_REFRESH = IMGPATH + "pull.png"; //$NON-NLS-1$
    String ICON_RESET = IMGPATH + "reset.png"; //$NON-NLS-1$
    String ICON_REVERT = IMGPATH + "revert.png"; //$NON-NLS-1$
    String ICON_SYNCED = IMGPATH + "synced.png"; //$NON-NLS-1$
    String ICON_TICK = IMGPATH + "tick.png"; //$NON-NLS-1$
    String ICON_UNDO_COMMIT = IMGPATH + "undo_commit.png"; //$NON-NLS-1$

    String ICON_LEFT_BALL_OVERLAY = IMGPATH + "left_ball_ovr.png"; //$NON-NLS-1$
    String ICON_RIGHT_BALL_OVERLAY = IMGPATH + "right_ball_ovr.png"; //$NON-NLS-1$
    String ICON_TOP_BALL_OVERLAY = IMGPATH + "top_ball_ovr.png"; //$NON-NLS-1$
    
    String ICON_LOCAL = IMGPATH + "local.png"; //$NON-NLS-1$
    String ICON_REMOTE = IMGPATH + "remote.png"; //$NON-NLS-1$

    String BANNER_COMMIT = IMGPATH + "commit_wizban.png"; //$NON-NLS-1$

}

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
@SuppressWarnings("nls")
public interface IModelRepositoryImages {
    
    ImageFactory ImageFactory = new ImageFactory(ModelRepositoryPlugin.INSTANCE);

    String IMGPATH = "img/";
    
    String ICON_PLUGIN = IMGPATH + "plugin.png";
    
    String ICON_ABORT = IMGPATH + "abort.png";
    String ICON_BLANK = IMGPATH + "blank.png";
    String ICON_BRANCH = IMGPATH + "branch_obj.png";
    String ICON_BRANCHES = IMGPATH + "branches_obj.png";
    String ICON_CLONE = IMGPATH + "cloneGit.png";
    String ICON_COMMIT = IMGPATH + "commit.png";
    String ICON_CREATE_REPOSITORY = IMGPATH + "createRepository.png";
    String ICON_DELETE = IMGPATH + "delete.png";
    String ICON_GROUP = IMGPATH + "group.png";
    String ICON_HISTORY_VIEW = IMGPATH + "history_view.png";
    String ICON_MERGE = IMGPATH + "merge.png";
    String ICON_MODEL = IMGPATH + "elements_obj.png";
    String ICON_NEW_BRANCH = IMGPATH + "new_branch_obj.png";
    String ICON_PUSH = IMGPATH + "push.png";
    String ICON_REFRESH = IMGPATH + "pull.png";
    String ICON_RESET = IMGPATH + "reset.png";
    String ICON_REVERT = IMGPATH + "revert.png";
    String ICON_SYNCED = IMGPATH + "synced.png";
    String ICON_TICK = IMGPATH + "tick.png";
    String ICON_TWOWAY_COMPARE = IMGPATH + "twowaycompare_co.png";
    String ICON_UNDO_COMMIT = IMGPATH + "undo_commit.png";

    String ICON_LEFT_BALL_OVERLAY = IMGPATH + "left_ball_ovr.png";
    String ICON_RIGHT_BALL_OVERLAY = IMGPATH + "right_ball_ovr.png";
    String ICON_TOP_BALL_OVERLAY = IMGPATH + "top_ball_ovr.png";
    
    String ICON_LOCAL = IMGPATH + "local.png";
    String ICON_REMOTE = IMGPATH + "remote.png";

    String BANNER_COMMIT = IMGPATH + "commit_wizban.png";

}

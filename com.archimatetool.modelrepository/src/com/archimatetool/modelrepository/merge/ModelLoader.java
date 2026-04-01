package com.archimatetool.modelrepository.merge;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Service for loading ArchiMate models from different Git revisions or working tree.
 */
public class ModelLoader {

    /**
     * Loads the main model file from a detached checkout of {@code revStr} into a temp directory, then deletes the temp tree.
     *
     * @param utils   open Git helper for the repository
     * @param revStr  commit SHA, branch name, or symbolic ref understood by JGit
     * @return loaded model, or {@code null} if the model file is missing in that tree
     * @throws IOException if extraction or load fails
     */
    public static IArchimateModel loadModel(GitUtils utils, String revStr) throws IOException {
        File tempFolder = Files.createTempDirectory("archi-").toFile();

        try {
            utils.extractCommit(revStr, tempFolder, false);

            File modelFile = new File(tempFolder, RepoConstants.MODEL_FILENAME);
            return modelFile.exists() ? IEditorModelManager.INSTANCE.load(modelFile) : null;
        } finally {
            FileUtils.deleteFolder(tempFolder);
        }
    }

    /**
     * Loads the merge-base model between {@code HEAD} and {@code revStr} (three-way merge ancestor).
     *
     * @param utils   open Git helper
     * @param revStr  the other side of the merge (e.g. branch being merged)
     * @return model at the common ancestor commit, or {@code null} if no merge base exists
     * @throws IOException if Git or load fails
     */
    public static IArchimateModel loadBaseModel(GitUtils utils, String revStr) throws IOException {
        org.eclipse.jgit.revwalk.RevCommit mergeBase = utils.getBaseCommit(RepoConstants.HEAD, revStr);
        return mergeBase != null ? loadModel(utils, mergeBase.getName()) : null;
    }

    /**
     * Loads the repository’s on-disk model file (not an already-open editor instance).
     *
     * @param repository Archi repository wrapper pointing at the working copy
     * @return parsed model from {@link IArchiRepository#getModelFile()}
     * @throws IOException if the file cannot be read
     */
    public static IArchimateModel loadWorkingTreeModel(IArchiRepository repository) throws IOException {
        // Always from disk — avoids stale in-memory model from the Models tree
        return IEditorModelManager.INSTANCE.load(repository.getModelFile());
    }
}

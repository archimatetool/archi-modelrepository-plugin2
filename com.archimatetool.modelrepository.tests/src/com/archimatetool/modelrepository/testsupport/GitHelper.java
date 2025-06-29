/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.testsupport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;

@SuppressWarnings("nls")
public class GitHelper {

    public static IArchiRepository createNewRepository() throws IOException {
        return createNewRepository("testRepo");
    }

    public static IArchiRepository createNewRepository(String folderName) throws IOException {
        return new ArchiRepository(new File(getTempTestsFolder(), folderName));
    }
    
    public static File createBareRepository() throws IOException, GitAPIException {
        File repoFolder = new File(getTempTestsFolder(), "testBareRepo");
        try(Git git = Git.init()
                .setBare(true)
                .setDirectory(repoFolder)
                .setInitialBranch(RepoConstants.MAIN)
                .call()) {
        }
        return repoFolder;
    }
    
    public static File getTempTestsFolder() throws IOException {
        // Need canonical file because on Windows the temp folder path includes short name like C:\Users\PHILLI~1\AppData\Local\Temp\
        File folder = new File(System.getProperty("java.io.tmpdir"), "com.archimatetool.modelrepository.tests.tmp").getCanonicalFile();
        folder.deleteOnExit();
        folder.mkdirs();
        return folder;
    }
    
    public static File writeFileToTestRepo(IArchiRepository repo, String fileName, String contents) throws IOException {
        return writeFileToTestRepo(repo, fileName, contents, StandardOpenOption.CREATE);
    }
    
    public static File writeFileToTestRepo(IArchiRepository repo, String fileName, String contents, OpenOption option) throws IOException {
        Path filePath = Path.of(repo.getWorkingFolder().getPath(), fileName);
        Files.writeString(filePath, contents, option);
        return filePath.toFile();
    }
    
    public static IArchimateModel createSimpleModel() {
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setAdapter(IArchiveManager.class, IArchiveManager.FACTORY.createArchiveManager(model));        
        model.setDefaults();
        
        // One diagram model
        IArchimateDiagramModel dm = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        model.getDefaultFolderForObject(dm).getElements().add(dm);
        
        return model;
    }
    
    public static IArchimateModel createSimpleModelInTestRepo(IArchiRepository repo) throws IOException {
        return saveModelToTestRepo(createSimpleModel(), repo);
    }
    
    public static IArchimateModel saveModelToTestRepo(IArchimateModel model, IArchiRepository repo) throws IOException {
        model.setFile(repo.getModelFile());
        saveModel(model);
        return model;
    }
    
    public static void saveModel(IArchimateModel model) throws IOException {
        IArchiveManager archiveManager = (IArchiveManager)model.getAdapter(IArchiveManager.class);
        archiveManager.saveModel();
    }
}
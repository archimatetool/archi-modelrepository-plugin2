/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.utils.FileUtils;

/**
 * Manages exporting model file to working dir files and back again
 * 
 * @author Phillip Beauvoir
 */
final class FileHandler implements IRepositoryConstants {
    
    static void copyModelFileToWorkingDirectory(File workingDir, File modelFile) throws IOException {
        // Delete the images folder
        File imagesFolder = new File(workingDir, IMAGES_FOLDER);
        FileUtils.deleteFolder(imagesFolder);
        
        // Model is in archive format and contains images
        if(IArchiveManager.FACTORY.isArchiveFile(modelFile)) {
            imagesFolder.mkdirs();
            
            try(ZipFile zipFile = new ZipFile(modelFile)) {
                // Open model zip file and extract and copy all entries
                for(Enumeration<? extends ZipEntry> enm = zipFile.entries(); enm.hasMoreElements();) {
                    ZipEntry zipEntry = enm.nextElement();
                    String entryName = zipEntry.getName();
                    
                    try(InputStream in = zipFile.getInputStream(zipEntry)) {
                        File outFile = null;
                        
                        if(entryName.startsWith("images/")) { //$NON-NLS-1$
                            outFile = new File(workingDir, entryName);
                        }
                        if(entryName.equalsIgnoreCase(WORKING_MODEL_FILENAME)) {
                            outFile = new File(workingDir, WORKING_MODEL_FILENAME);
                        }
                        
                        if(outFile != null) {
                            copyFile(in, outFile);
                        }
                    }
                }
            }
        }
        // A normal file so copy it
        else {
            File outFile = new File(workingDir, WORKING_MODEL_FILENAME);
            copyFile(modelFile, outFile);
        }
    }
    
    // Our method is quicker than Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    private static void copyFile(File srcFile, File destFile) throws IOException {
        copyFile(new FileInputStream(srcFile), destFile);
    }

    // Our method is quicker than Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    private static void copyFile(InputStream in, File destFile) throws IOException {
        final int bufSize = 1024 * 64;
        byte[] buf = new byte[bufSize];
        int size;
        
        try(BufferedInputStream bis = new BufferedInputStream(in, bufSize)) {
            try(BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile), bufSize)) {
                while((size = bis.read(buf)) != -1) {
                    bos.write(buf, 0, size);
                }
                bos.flush();
            }
        }
    }
}

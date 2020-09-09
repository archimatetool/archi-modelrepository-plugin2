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

/**
 * Manages exporting model file to working dir files and back again
 * 
 * @author Phillip Beauvoir
 */
public final class FileHandler implements IRepositoryConstants {
    
    /**
     * @return True if folder is empty (ignoring special files)
     */
    public static boolean isFolderEmpty(File folder) {
        if(folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> !name.equals(".DS_Store")); //$NON-NLS-1$
            return files != null && files.length == 0;
        }

        // Might not exist yet
        return true;
    }
    
    // Our method is quicker than Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    public static void copyFile(File srcFile, File destFile) throws IOException {
        copyFile(new FileInputStream(srcFile), destFile);
    }

    // Our method is quicker than Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    public static void copyFile(InputStream in, File destFile) throws IOException {
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

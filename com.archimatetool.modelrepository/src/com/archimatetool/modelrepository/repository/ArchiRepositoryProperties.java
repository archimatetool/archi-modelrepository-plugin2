/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Properties file stored as "archi" file 
 * Not used at the moment
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ArchiRepositoryProperties extends Properties {
    
    private File folder;

    public static ArchiRepositoryProperties open(IArchiRepository archiRepo) throws IOException {
        return new ArchiRepositoryProperties(archiRepo.getGitFolder()).load();
    }
    
    private ArchiRepositoryProperties(File folder) {
        this.folder = folder;
    }
    
    public File getPropertiesFile() {
        return new File(folder, "archi");
    }
    
    private ArchiRepositoryProperties load() throws IOException {
        File file = getPropertiesFile();
        if(file.exists()) {
            try(FileInputStream is = new FileInputStream(file)) {
                load(is);
            }
        }
        
        return this;
    }
    
    public void save() throws IOException {
        try(FileOutputStream out = new FileOutputStream(getPropertiesFile())) {
            store(out, "Archi Repository");
        }
    }
}

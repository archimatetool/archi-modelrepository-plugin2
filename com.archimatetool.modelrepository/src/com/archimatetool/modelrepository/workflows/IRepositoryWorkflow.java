/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

/**
 * Interface for Repository Workflow
 * 
 * @author Phillip Beauvoir
 */
public interface IRepositoryWorkflow {

    /**
     * Run the workflow
     */
    void run();

    /**
     * @return true if the workflow can run
     */
    boolean canRun();

}
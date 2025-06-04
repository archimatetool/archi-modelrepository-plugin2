/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * JGit ProgressMonitor Wrapper around a IProgressMonitor
 * 
 * @author Phillip Beauvoir
 */
public class ProgressMonitorWrapper implements ProgressMonitor {
    private IProgressMonitor pm;
    private String mainTaskName;

    public ProgressMonitorWrapper(IProgressMonitor pm) {
        this(pm, null);
    }
    
    public ProgressMonitorWrapper(IProgressMonitor pm, String mainTaskName) {
        this.pm = pm;
        this.mainTaskName = mainTaskName;
    }
    
    @Override
    public void start(int totalTasks) {
    }
    
    @Override
    public void beginTask(String title, int totalWork) {
        if(pm != null) {
            if(totalWork == UNKNOWN) {
                totalWork = IProgressMonitor.UNKNOWN;
            }
            
            if(mainTaskName != null) {
                pm.beginTask(mainTaskName, totalWork);
                pm.subTask(title);
            }
            else {
                pm.beginTask(title, totalWork);
            }
        }
    }

    @Override
    public void update(int completed) {
        if(pm != null) {
            pm.worked(completed);
        }
    }

    @Override
    public void endTask() {
    }
    
    @Override
    public boolean isCancelled() {
        return (pm != null) ? pm.isCanceled() : false;
    }
    
    @Override
    public void showDuration(boolean enabled) {
    }
}
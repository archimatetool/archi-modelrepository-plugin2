package com.archimatetool.modelrepository.repository;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

    private static final String BUNDLE_NAME = "com.archimatetool.modelrepository.repository.messages"; //$NON-NLS-1$

    public static String ArchiRepository_0;
    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}

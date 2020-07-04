package com.archimatetool.modelrepository.propertysections;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

    private static final String BUNDLE_NAME = "com.archimatetool.modelrepository.propertysections.messages"; //$NON-NLS-1$

    public static String RepoInfoSection_0;

    public static String RepoInfoSection_1;

    public static String RepoInfoSection_2;
    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}

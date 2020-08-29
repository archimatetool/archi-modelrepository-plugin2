package com.archimatetool.modelrepository.authentication;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

    private static final String BUNDLE_NAME = "com.archimatetool.modelrepository.authentication.messages"; //$NON-NLS-1$

    public static String CredentialsAuthenticator_0;

    public static String CredentialsAuthenticator_1;

    public static String CredentialsAuthenticator_2;
    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}

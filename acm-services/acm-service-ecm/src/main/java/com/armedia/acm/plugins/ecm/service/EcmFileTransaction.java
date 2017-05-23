package com.armedia.acm.plugins.ecm.service;

import com.armedia.acm.plugins.ecm.model.AcmContainer;
import com.armedia.acm.plugins.ecm.model.EcmFile;
import org.apache.chemistry.opencmis.client.api.Document;
import org.mule.api.MuleException;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by armdev on 5/1/14.
 */
public interface EcmFileTransaction
{
    EcmFile addFileTransaction(Authentication authentication, String originalFileName, AcmContainer container,
                               String targetCmisFolderId, InputStream fileContents, EcmFile metadata,
                               Document existingCmisDocument) throws MuleException, IOException;

    EcmFile addFileTransaction(Authentication authentication, String originalFileName, AcmContainer container,
                               String targetCmisFolderId, InputStream fileContents, EcmFile metadata)
            throws MuleException, IOException;

    @Deprecated
    EcmFile addFileTransaction(String originalFileName, Authentication authentication, String fileType, String fileCategory,
                               InputStream fileInputStream, String mimeType, String fileName, String cmisFolderId, AcmContainer container, String cmisRepositoryId)
            throws MuleException, IOException;

    @Deprecated
    EcmFile addFileTransaction(String originalFileName, Authentication authentication, String fileType,
                               String fileCategory, InputStream fileContents, String fileContentType, String fileName,
                               String targetCmisFolderId, AcmContainer container, String cmisRepositoryId,
                               Document existingCmisDocument) throws MuleException, IOException;

    EcmFile updateFileTransaction(Authentication authentication, EcmFile ecmFile, InputStream fileInputStream)
            throws MuleException, IOException;

    EcmFile updateFileTransactionEventAware(Authentication authentication, EcmFile ecmFile, InputStream fileInputStream)
            throws MuleException, IOException;

    String downloadFileTransaction(EcmFile ecmFile) throws MuleException;

    InputStream downloadFileTransactionAsInputStream(EcmFile ecmFile) throws MuleException;
}

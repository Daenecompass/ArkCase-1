package com.armedia.acm.plugins.ecm.service.sync.impl;

import com.armedia.acm.core.exceptions.AcmEncryptionException;
import com.armedia.acm.files.propertymanager.PropertyFileManager;
import com.armedia.acm.plugins.ecm.model.sync.EcmEvent;
import com.armedia.acm.plugins.ecm.service.sync.EcmAuditResponseReader;
import com.armedia.acm.web.api.MDCConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by dmiller on 5/15/17.
 */
public class AlfrescoSyncService implements ApplicationEventPublisherAware
{
    private PropertyFileManager propertyFileManager;
    private AlfrescoAuditApplicationRestClient auditApplicationRestClient;
    private Map<String, EcmAuditResponseReader> auditApplications;
    private String auditApplicationLastAuditIdsFilename;

    private transient final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationEventPublisher applicationEventPublisher;

    public void queryAlfrescoAuditApplications()
    {
        for (Map.Entry<String, EcmAuditResponseReader> auditApplication : getAuditApplications().entrySet())
        {

            if (MDC.get(MDCConstants.EVENT_MDC_REQUEST_ALFRESCO_USER_ID_KEY) == null)
            {
                MDC.put(MDCConstants.EVENT_MDC_REQUEST_ALFRESCO_USER_ID_KEY, "admin");
                MDC.put(MDCConstants.EVENT_MDC_REQUEST_ID_KEY, UUID.randomUUID().toString());
            }

            String lastAuditIdFetched = null;
            String applicationName = auditApplication.getKey();
            EcmAuditResponseReader reader = auditApplication.getValue();

            log.info("Starting Alfresco sync for audit application {}", applicationName);

            try
            {
                String lastAuditIdKey = applicationName + ".lastAuditId";

                lastAuditIdFetched = getPropertyFileManager().load(getAuditApplicationLastAuditIdsFilename(),
                        lastAuditIdKey, "0");
                // start from the *next* audit Id
                long lastAuditId = Long.valueOf(lastAuditIdFetched);
                long startingAuditId = lastAuditId + 1;
                JSONObject auditEntries = getAuditApplicationRestClient().service(applicationName, startingAuditId);
                updatePropertiesWithLastAuditId(lastAuditIdKey, auditEntries);

                List<EcmEvent> events = reader.read(auditEntries);

                if (events != null && !events.isEmpty())
                {
                    log.info("Fetched {} audit records for audit application {}", events.size(), applicationName);


                    for (EcmEvent e : events)
                    {
                        applicationEventPublisher.publishEvent(e);
                    }
                }


            } catch (AcmEncryptionException e)
            {
                log.error("Could not decrypt property {}.lastAuditId", applicationName, e);
            } catch (NumberFormatException e)
            {
                log.error("The last audit id {} should be a number, but it is not!", lastAuditIdFetched);
            } catch (Exception e)
            {
                log.error("Could not query Alfresco audit records for application {}", applicationName, e);
            }

        }

    }

    protected void updatePropertiesWithLastAuditId(String lastAuditIdKey, JSONObject fullAuditResponse)
    {
        // some of the readers ignore some events.  So we want to store the last audit id from the full
        // response... not the last audit id returned by the reader.
        JSONArray allAudits = fullAuditResponse.getJSONArray("entries");
        int numAudits = allAudits.length();

        if (numAudits > 0)
        {
            long lastAuditFromFullResponse = allAudits.getJSONObject(numAudits - 1).getLong("id");
            Map<String, String> properties = Collections.singletonMap(lastAuditIdKey, String.valueOf(lastAuditFromFullResponse));
            getPropertyFileManager().storeMultiple(properties, getAuditApplicationLastAuditIdsFilename(), false);
        }
    }

    public void setPropertyFileManager(PropertyFileManager propertyFileManager)
    {
        this.propertyFileManager = propertyFileManager;
    }

    public PropertyFileManager getPropertyFileManager()
    {
        return propertyFileManager;
    }

    public void setAuditApplicationRestClient(AlfrescoAuditApplicationRestClient auditApplicationRestClient)
    {
        this.auditApplicationRestClient = auditApplicationRestClient;
    }

    public AlfrescoAuditApplicationRestClient getAuditApplicationRestClient()
    {
        return auditApplicationRestClient;
    }

    public void setAuditApplicationLastAuditIdsFilename(String auditApplicationLastAuditIdsFilename)
    {
        this.auditApplicationLastAuditIdsFilename = auditApplicationLastAuditIdsFilename;
    }

    public String getAuditApplicationLastAuditIdsFilename()
    {
        return auditApplicationLastAuditIdsFilename;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher)
    {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public Map<String, EcmAuditResponseReader> getAuditApplications()
    {
        return auditApplications;
    }

    public void setAuditApplications(Map<String, EcmAuditResponseReader> auditApplications)
    {
        this.auditApplications = auditApplications;
    }
}

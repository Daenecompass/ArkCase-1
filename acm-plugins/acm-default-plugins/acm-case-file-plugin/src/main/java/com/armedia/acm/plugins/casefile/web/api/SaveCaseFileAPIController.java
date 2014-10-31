package com.armedia.acm.plugins.casefile.web.api;

import com.armedia.acm.core.exceptions.AcmCreateObjectFailedException;
import com.armedia.acm.plugins.casefile.model.CaseFile;
import com.armedia.acm.plugins.casefile.service.SaveCaseService;
import org.drools.core.RuntimeDroolsException;
import org.mule.api.MuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.persistence.PersistenceException;
import javax.servlet.http.HttpSession;

@Controller
@RequestMapping( { "/api/v1/plugin/casefile", "/api/latest/plugin/casefile"})
public class SaveCaseFileAPIController
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private SaveCaseService saveCaseService;


    @RequestMapping(method = RequestMethod.POST, produces = { MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE })
    @ResponseBody
    public CaseFile createCaseFile(
            @RequestBody CaseFile in,
            HttpSession session,
            Authentication auth
    ) throws AcmCreateObjectFailedException
    {
        if (log.isTraceEnabled())
        {
            log.trace("Got a case file: " + in + "; case ID: '" + in.getId() + "'");
        }
        String ipAddress = (String) session.getAttribute("acm_ip_address");

        try
        {
            return getSaveCaseService().saveCase(in, auth, ipAddress);
        }
        catch (MuleException | PersistenceException | RuntimeDroolsException e)
        {
            throw new AcmCreateObjectFailedException("Case File", e.getMessage(), e);
        }
    }


    public SaveCaseService getSaveCaseService()
    {
        return saveCaseService;
    }

    public void setSaveCaseService(SaveCaseService saveCaseService)
    {
        this.saveCaseService = saveCaseService;
    }
}
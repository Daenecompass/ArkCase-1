package com.armedia.acm.auth;

import com.armedia.acm.pluginmanager.AcmPlugin;
import com.armedia.acm.pluginmanager.AcmPluginManager;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.Authentication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.easymock.EasyMock.expect;

/**
 * Created by dmiller on 3/18/14.
 */
public class AcmLoginSuccessHandlerTest extends EasyMockSupport
{
    private AcmLoginSuccessHandler unit;

    private HttpSession mockSession;
    private Authentication mockAuthentication;
    private HttpServletRequest mockRequest;
    private AcmPluginManager mockPluginManager;

    @Before
    public void setUp()
    {
        unit = new AcmLoginSuccessHandler();

        mockSession = createMock(HttpSession.class);
        mockRequest = createMock(HttpServletRequest.class);
        mockAuthentication = createMock(Authentication.class);
        mockPluginManager = createMock(AcmPluginManager.class);

        unit.setAcmPluginManager(mockPluginManager);
    }

    @Test
    public void addUserIdToSession() throws Exception
    {
        String userId = "userId";

        expect(mockAuthentication.getName()).andReturn(userId);
        expect(mockRequest.getSession(true)).andReturn(mockSession);
        mockSession.setAttribute("acm_username", userId);

        replayAll();

        unit.addUserIdToSession(mockRequest, mockAuthentication);

        verifyAll();
    }

    @Test
    public void addNavigatorPluginsToSession() throws Exception
    {
        AcmPlugin navPlugin = new AcmPlugin();
        navPlugin.setEnabled(true);
        navPlugin.setHomeUrl("navPlugin");
        navPlugin.setNavigatorTab(true);

        List<AcmPlugin> plugins = Collections.singletonList(navPlugin);

        expect(mockPluginManager.getEnabledNavigatorPlugins()).andReturn(Arrays.asList(navPlugin));
        expect(mockRequest.getSession(true)).andReturn(mockSession);
        mockSession.setAttribute("acm_navigator_plugins", plugins);

        replayAll();

        unit.addNavigatorPluginsToSession(mockRequest, mockAuthentication);

        verifyAll();

    }
}
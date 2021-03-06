/*
 * Copyright 2017 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.server.service.plugins.processor.authorization;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.plugin.access.authorization.AuthorizationExtension;
import com.thoughtworks.go.plugin.access.authorization.AuthorizationMessageConverterV1;
import com.thoughtworks.go.plugin.api.request.DefaultGoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.infra.PluginRequestProcessorRegistry;
import com.thoughtworks.go.plugin.infra.plugininfo.GoPluginDescriptor;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.server.service.PluginRoleService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;

import static com.thoughtworks.go.server.service.plugins.processor.authorization.AuthorizationRequestProcessor.Request.GET_ROLE_CONFIG_REQUEST;
import static com.thoughtworks.go.server.service.plugins.processor.authorization.AuthorizationRequestProcessor.Request.INVALIDATE_CACHE_REQUEST;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class AuthorizationRequestProcessorTest {

    @Mock
    private PluginRequestProcessorRegistry registry;
    @Mock
    private AuthorizationExtension authorizationExtension;
    @Mock
    private GoPluginDescriptor pluginDescriptor;
    @Mock
    private GoConfigService goConfigService;
    @Mock
    private SecurityConfig securityConfig;
    private SecurityAuthConfigs securityAuthConfigsSpy;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        when(pluginDescriptor.id()).thenReturn("cd.go.authorization.github");
        stub(goConfigService.security()).toReturn(securityConfig);
        securityAuthConfigsSpy = spy(new SecurityAuthConfigs());
        stub(securityConfig.securityAuthConfigs()).toReturn(securityAuthConfigsSpy);
    }

    @Test
    public void shouldProcessInvalidateCacheRequest() throws Exception {
        PluginRoleService pluginRoleService = mock(PluginRoleService.class);
        when(authorizationExtension.getMessageConverter(AuthorizationMessageConverterV1.VERSION)).thenReturn(new AuthorizationMessageConverterV1());

        GoApiRequest request = new DefaultGoApiRequest(INVALIDATE_CACHE_REQUEST.requestName(), "1.0", null);
        AuthorizationRequestProcessor authorizationRequestProcessor = new AuthorizationRequestProcessor(registry, null, authorizationExtension, pluginRoleService);

        GoApiResponse response = authorizationRequestProcessor.process(pluginDescriptor, request);

        assertThat(response.responseCode(), is(200));
        verify(pluginRoleService).invalidateRolesFor("cd.go.authorization.github");
    }

    @Test
    public void shouldProcessRoleConfigRequest() throws Exception {
        securityAuthConfigsSpy.add(new SecurityAuthConfig("github", "cd.go.authorization.github"));
        AuthorizationMessageConverterV1 converterV1 = spy(new AuthorizationMessageConverterV1());
        when(authorizationExtension.getMessageConverter(AuthorizationMessageConverterV1.VERSION)).thenReturn(converterV1);

        PluginRoleConfig pluginRoleConfig = new PluginRoleConfig("blackbird", "github");
        RolesConfig rolesConfigSpy = spy(new RolesConfig(pluginRoleConfig));
        when(securityConfig.getRoles()).thenReturn(rolesConfigSpy);

        DefaultGoApiRequest request = new DefaultGoApiRequest(GET_ROLE_CONFIG_REQUEST.requestName(), "1.0", null);
        request.setRequestBody("{\"auth_config_id\":\"github\"}");

        AuthorizationRequestProcessor authorizationRequestProcessor = new AuthorizationRequestProcessor(registry, goConfigService, authorizationExtension, null);
        GoApiResponse response = authorizationRequestProcessor.process(pluginDescriptor, request);

        assertThat(response.responseCode(), is(200));
        verify(authorizationExtension.getMessageConverter(AuthorizationMessageConverterV1.VERSION)).processGetRoleConfigsRequest(request.requestBody());
        verify(authorizationExtension.getMessageConverter(AuthorizationMessageConverterV1.VERSION)).getProcessRoleConfigsResponseBody(Arrays.asList(pluginRoleConfig));
        verify(securityAuthConfigsSpy).findByPluginIdAndProfileId(pluginDescriptor.id(), "github");
        verify(securityConfig.getRoles()).getPluginRolesConfig("github");

    }
}
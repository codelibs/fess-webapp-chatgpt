/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.plugin.webapp.api.chatgpt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.Constants;
import org.codelibs.fess.api.WebApiManagerFactory;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.plugin.webapp.api.chatgpt.auth.PluginAuthenticator;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;

public class ChatGptApiManagerTest extends LastaFluteTestCase {

    private WebApiManagerFactory webApiManagerFactory;

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp() throws Exception {
        ComponentUtil.setFessConfig(new FessConfig.SimpleImpl() {
            private static final long serialVersionUID = 1L;
        });
        webApiManagerFactory = new WebApiManagerFactory();
        ComponentUtil.register(webApiManagerFactory, "webApiManagerFactory");
        PluginAuthenticator pluginAuthenticator = new PluginAuthenticator();
        ComponentUtil.register(pluginAuthenticator, "pluginAuthenticator");
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown();
    }

    public void test_pathPrefix() {
        final ChatGptApiManager chatGptApiManager = getComponent("chatGptApiManager");
        assertEquals("/chatgpt", chatGptApiManager.getPathPrefix());
    }

    public void test_updateAiPluginContent() throws IOException {
        final StringBuilder buf = new StringBuilder(8000);
        final ChatGptApiManager chatGptApiManager = getComponent("chatGptApiManager");
        try (final BufferedReader br = new BufferedReader(
                new InputStreamReader(ResourceUtil.getResourceAsStream("chatgpt/ai-plugin.json"), Constants.CHARSET_UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                buf.append(line).append('\n');
            }

            assertEquals(
                    """
                            {
                                "schema_version": "v1",
                                "name_for_model": "retrieval",
                                "name_for_human": "Fess Plugin",
                                "description_for_model": "Plugin for searching through the user's documents (such as files, emails, and more) to find answers to questions and retrieve relevant information. Use it whenever a user asks something that might be found in their personal information, or asks you to save information for later.",
                                "description_for_human": "Search through your documents.",
                                "auth": {"type":"none"},
                                "api": {
                                    "type": "openapi",
                                    "url": "http://localhost:8080/.well-known/openapi.yaml",
                                    "has_user_authentication": false
                                },
                                "logo_url": "http://localhost:8080/.well-known/logo.png",
                                "contact_email": "info@codelibs.co",
                                "legal_info_url": "https://codelibs.co/"
                            }
                            """,
                    chatGptApiManager.updateAiPluginContent(buf).replace("\t", "    "));

            System.setProperty(ChatGptApiManager.FESS_CHATGPT_OPENAPI_YAML_URL, "yaml_url");
            System.setProperty(ChatGptApiManager.FESS_CHATGPT_LOGO_URL, "logo_url");
            System.setProperty(ChatGptApiManager.FESS_CHATGPT_AI_PLUGIN_DESCRIPTION_FOR_HUMAN, "desc for human");
            System.setProperty(ChatGptApiManager.FESS_CHATGPT_AI_PLUGIN_DESCRIPTION_FOR_MODEL, "desc for model");
            System.setProperty(ChatGptApiManager.FESS_CHATGPT_AI_PLUGIN_NAME_FOR_HUMAN, "name for human");
            System.setProperty(ChatGptApiManager.FESS_CHATGPT_AI_PLUGIN_NAME_FOR_MODEL, "name for model");
            assertEquals("""
                    {
                        "schema_version": "v1",
                        "name_for_model": "name for model",
                        "name_for_human": "name for human",
                        "description_for_model": "desc for model",
                        "description_for_human": "desc for human",
                        "auth": {"type":"none"},
                        "api": {
                            "type": "openapi",
                            "url": "yaml_url",
                            "has_user_authentication": false
                        },
                        "logo_url": "logo_url",
                        "contact_email": "info@codelibs.co",
                        "legal_info_url": "https://codelibs.co/"
                    }
                    """, chatGptApiManager.updateAiPluginContent(buf).replace("\t", "    "));
        }
    }
}

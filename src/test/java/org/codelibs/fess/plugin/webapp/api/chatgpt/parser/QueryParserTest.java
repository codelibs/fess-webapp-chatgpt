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
package org.codelibs.fess.plugin.webapp.api.chatgpt.parser;

import java.io.ByteArrayInputStream;

import org.codelibs.fess.api.WebApiManagerFactory;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.plugin.webapp.api.chatgpt.auth.PluginAuthenticator;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Query;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Source;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;

public class QueryParserTest extends LastaFluteTestCase {

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

    public void test_parse() throws Exception {
        String body = """
                {
                  "queries": [
                    {
                      "query": "QUERY",
                      "filter": {
                        "document_id": "DOCUMENT_ID",
                        "source": "email",
                        "source_id": "SOURCE_ID",
                        "author": "AUTHOR",
                        "start_date": "2013-05-11T21:23:58.970460+07:00",
                        "end_date": "2013-05-05 12:30:45"
                      },
                      "top_k": 10
                    }
                  ]
                }""";

        try (QueryParser parser = new QueryParser(new ByteArrayInputStream(body.getBytes()))) {
            Query[] queries = parser.parse();
            assertEquals(1, queries.length);
            Query query = queries[0];
            assertEquals("QUERY", query.getQuery());
            assertEquals(10, query.getTopK());
            Query.Filter filter = query.getFilter();
            assertEquals("DOCUMENT_ID", filter.getDocumentId());
            assertEquals(Source.EMAIL, filter.getSource());
            assertEquals("SOURCE_ID", filter.getSourceId());
            assertEquals("AUTHOR", filter.getAuthor());
            assertEquals("2013-05-11T14:23:58.970Z", filter.getStartDate());
            assertEquals("2013-05-05T12:30:45.000Z", filter.getEndDate());

        }
    }
}

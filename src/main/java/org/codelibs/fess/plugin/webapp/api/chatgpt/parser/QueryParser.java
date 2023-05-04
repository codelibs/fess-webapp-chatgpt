/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.codelibs.core.exception.IORuntimeException;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Query;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Query.QueryBuilder;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.NamedXContentRegistry;

public class QueryParser implements AutoCloseable {
    private final InputStream in;

    public QueryParser(final InputStream in) {
        this.in = in;

    }

    @Override
    public void close() throws Exception {
        in.close();
    }

    public Query[] parse() {
        try {
            final Map<String, Object> requestBodyMap =
                    JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, in).map();
            if (requestBodyMap.get("queries") instanceof final List<?> queryList) {
                return parseQueries(queryList);
            }
        } catch (final IOException e) {
            throw new IORuntimeException(e);
        }
        return new Query[0];
    }

    private Query[] parseQueries(final List<?> queryList) {
        return queryList.stream().map(queryObj -> {
            if ((queryObj instanceof final Map<?, ?> queryMap) && (queryMap.get("query") instanceof final String q)) {
                final QueryBuilder builder = new QueryBuilder(q);
                if (queryMap.get("top_k") instanceof final Number topK) {
                    builder.topK(topK.intValue());
                }
                if (queryMap.get("filter") instanceof final Map<?, ?> filterMap) {
                    if (filterMap.get("document_id") instanceof final String documentId) {
                        builder.documentId(documentId);
                    }
                    if (filterMap.get("source") instanceof final String source) {
                        builder.source(source);
                    }
                    if (filterMap.get("source_id") instanceof final String sourceId) {
                        builder.sourceId(sourceId);
                    }
                    if (filterMap.get("author") instanceof final String author) {
                        builder.author(author);
                    }
                    if (filterMap.get("start_date") instanceof final String startDate) {
                        builder.startDate(startDate);
                    }
                    if (filterMap.get("end_date") instanceof final String endDate) {
                        builder.endDate(endDate);
                    }
                }
                return builder.build();
            }
            return null;
        }).filter(o -> o != null).toArray(n -> new Query[n]);
    }
}

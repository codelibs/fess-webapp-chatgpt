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
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Document;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Document.DocumentBuilder;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.NamedXContentRegistry;

public class DocumentParser implements AutoCloseable {
    private final InputStream in;

    public DocumentParser(final InputStream in) {
        this.in = in;

    }

    @Override
    public void close() throws Exception {
        in.close();
    }

    public Document[] parse() {
        try {
            final Map<String, Object> requestBodyMap =
                    JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, in).map();
            if (requestBodyMap.get("documents") instanceof final List<?> documentList) {
                return parseDocuments(documentList);
            }
        } catch (final IOException e) {
            throw new IORuntimeException(e);
        }
        return new Document[0];
    }

    private Document[] parseDocuments(final List<?> documentList) {
        return documentList.stream().map(docObj -> {
            if ((docObj instanceof final Map<?, ?> docMap) && (docMap.get("text") instanceof final String text)) {
                final DocumentBuilder builder = new DocumentBuilder(text);
                if (docMap.get("id") instanceof final String id) {
                    builder.id(id);
                }
                if (docMap.get("metadata") instanceof final Map<?, ?> metadataMap) {
                    if (metadataMap.get("source") instanceof final String source) {
                        builder.source(source);
                    }
                    if (metadataMap.get("source_id") instanceof final String sourceId) {
                        builder.sourceId(sourceId);
                    }
                    if (metadataMap.get("url") instanceof final String url) {
                        builder.url(url);
                    }
                    if (metadataMap.get("author") instanceof final String author) {
                        builder.author(author);
                    }
                    if (metadataMap.get("created_at") instanceof final String createdAt) {
                        builder.createdAt(createdAt);
                    }
                }
                return builder.build();
            }
            return null;
        }).filter(o -> o != null).toArray(n -> new Document[n]);
    }

}

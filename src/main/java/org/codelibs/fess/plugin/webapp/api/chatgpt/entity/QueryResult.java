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
package org.codelibs.fess.plugin.webapp.api.chatgpt.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.poi.util.StringUtil;
import org.codelibs.fess.Constants;
import org.codelibs.fess.entity.SearchRenderData;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;

public class QueryResult {
    protected final String query;

    protected final DocumentResult[] documents;

    protected QueryResult(final String query, final DocumentResult[] documents) {
        this.query = query;
        this.documents = documents;
    }

    public String toJsonString() {
        final StringBuilder buf = new StringBuilder(1000);
        buf.append("{\"query\":\"").append(StringEscapeUtils.escapeJson(query)).append("\"");
        buf.append(",\"results\":[").append(Arrays.stream(documents).map(DocumentResult::toJsonString).collect(Collectors.joining(",")))
                .append(']');
        buf.append(",\"top_k\":").append(documents.length);
        buf.append('}');
        return buf.toString();
    }

    public static class DocumentResult {
        protected final String id;
        protected String text;
        protected float score = 0.0f;
        protected float[] embedding = null;
        protected final DocumentMetadataResult metadata;

        public DocumentResult(final String id, final DocumentMetadataResult metadata) {
            this.id = id;
            this.metadata = metadata;
        }

        public String toJsonString() {
            final StringBuilder buf = new StringBuilder(100);
            buf.append("{\"id\":\"").append(StringEscapeUtils.escapeJson(id)).append('"');
            buf.append(",\"metadata\":").append(metadata.toJsonString());
            buf.append(",\"score\":").append(score);
            if (text != null) {
                buf.append(",\"text\":\"").append(StringEscapeUtils.escapeJson(text)).append('"');
            }
            if (embedding != null) {

                buf.append(",\"embedding\":[");
                if (embedding.length > 0) {
                    buf.append(embedding[0]);
                    for (int i = 1; i < embedding.length; i++) {
                        buf.append(',').append(embedding[i]);
                    }
                }
                buf.append(']');
            }
            buf.append('}');
            return buf.toString();
        }
    }

    public static class DocumentMetadataResult {
        protected Source source = Source.UNKNOWN;
        protected String sourceId;
        protected String url;
        protected String createdAt;
        protected String author;
        protected String documentId;

        public String toJsonString() {
            final List<String> list = new ArrayList<>();
            if (source != Source.UNKNOWN) {
                list.add("\"source\":\"" + source.name() + "\"");
            }
            if (StringUtil.isNotBlank(sourceId)) {
                list.add("\"source_id\":\"" + StringEscapeUtils.escapeJson(sourceId) + "\"");
            }
            if (StringUtil.isNotBlank(url)) {
                list.add("\"url\":\"" + StringEscapeUtils.escapeJson(url) + "\"");
            }
            if (StringUtil.isNotBlank(createdAt)) {
                list.add("\"created_at\":\"" + StringEscapeUtils.escapeJson(createdAt) + "\"");
            }
            if (StringUtil.isNotBlank(author)) {
                list.add("\"author\":\"" + StringEscapeUtils.escapeJson(author) + "\"");
            }
            if (StringUtil.isNotBlank(documentId)) {
                list.add("\"document_id\":\"" + StringEscapeUtils.escapeJson(documentId) + "\"");
            }
            return "{" + list.stream().collect(Collectors.joining(",")) + "}";
        }
    }

    public static QueryResult create(final Query query, final SearchRenderData data) {
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final List<Map<String, Object>> documentItems = data.getDocumentItems();
        final float maxScore = getMaxScore(documentItems);
        final DocumentResult[] documents = documentItems.stream().map(e -> {
            final DocumentMetadataResult metadata = new DocumentMetadataResult();
            if (e.get(fessConfig.getIndexFieldLabel()) instanceof final List<?> labelList) {
                labelList.stream().forEach(o -> {
                    switch (o.toString()) {
                    case "email": {
                        metadata.source = Source.EMAIL;
                        break;
                    }
                    case "chat": {
                        metadata.source = Source.CHAT;
                        break;
                    }
                    case "file": {
                        metadata.source = Source.FILE;
                        break;
                    }
                    default:
                        break;
                    }
                });
            }
            if (e.get(fessConfig.getIndexFieldFilename()) instanceof final String sourceId) {
                metadata.sourceId = sourceId;
            }
            if (e.get(fessConfig.getIndexFieldUrl()) instanceof final String url) {
                metadata.url = url;
            }
            if (e.get(fessConfig.getIndexFieldTimestamp()) instanceof final String createdAt) {
                metadata.createdAt = createdAt;
            }
            if (e.get("author") instanceof final String author) {
                metadata.author = author;
            }
            if (e.get(fessConfig.getIndexFieldDocId()) instanceof final String documentId) {
                metadata.documentId = documentId;
            }

            final DocumentResult document = new DocumentResult(e.get(fessConfig.getIndexFieldId()).toString(), metadata);
            if (e.get(fessConfig.getIndexFieldContent()) instanceof final String text) {
                document.text = text;
            }
            if (e.get(Constants.SCORE) instanceof final Number score) {
                document.score = score.floatValue() / maxScore;
            }
            return document;
        }).toArray(n -> new DocumentResult[n]);
        return new QueryResult(query.getQuery(), documents);
    }

    protected static float getMaxScore(final List<Map<String, Object>> documentItems) {
        if (!documentItems.isEmpty()) {
            final Map<String, Object> doc = documentItems.get(0);
            if (doc.get(Constants.SCORE) instanceof final Number score && score.floatValue() > 1.0f) {
                return score.floatValue();
            }
        }
        return 1.0f;
    }
}

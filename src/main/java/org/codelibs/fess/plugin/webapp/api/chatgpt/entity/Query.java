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
package org.codelibs.fess.plugin.webapp.api.chatgpt.entity;

import org.codelibs.fess.plugin.webapp.api.chatgpt.util.DateUtil;

public class Query {

    protected final String query;

    protected final Filter filter;

    protected int topK = 3;

    protected Query(final String query) {
        this.query = query;
        this.filter = new Filter();
    }

    public String getQuery() {
        return query;
    }

    public Filter getFilter() {
        return filter;
    }

    public int getTopK() {
        return topK;
    }

    public static class Filter {
        protected String documentId;
        protected Source source = Source.UNKNOWN;
        protected String sourceId;
        protected String author;
        protected long startDate;
        protected long endDate;

        public String getDocumentId() {
            return documentId;
        }

        public Source getSource() {
            return source;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getAuthor() {
            return author;
        }

        public String getStartDate() {
            return DateUtil.format(startDate);
        }

        public String getEndDate() {
            return DateUtil.format(endDate);
        }
    }

    public static class QueryBuilder {
        final Query query;

        public QueryBuilder(final String q) {
            query = new Query(q);
        }

        public Query build() {
            return query;
        }

        public QueryBuilder topK(final int topK) {
            query.topK = topK;
            return this;
        }

        public QueryBuilder documentId(final String documentId) {
            query.filter.documentId = documentId;
            return this;
        }

        public QueryBuilder source(final String source) {
            if (Source.EMAIL.name().equalsIgnoreCase(source)) {
                query.filter.source = Source.EMAIL;
            } else if (Source.FILE.name().equalsIgnoreCase(source)) {
                query.filter.source = Source.FILE;
            } else if (Source.CHAT.name().equalsIgnoreCase(source)) {
                query.filter.source = Source.CHAT;
            }
            return this;
        }

        public QueryBuilder sourceId(final String sourceId) {
            query.filter.sourceId = sourceId;
            return this;
        }

        public QueryBuilder author(final String author) {
            query.filter.author = author;
            return this;
        }

        public QueryBuilder startDate(final String startDate) {
            query.filter.startDate = DateUtil.parse(startDate);
            return this;
        }

        public QueryBuilder endDate(final String endDate) {
            query.filter.endDate = DateUtil.parse(endDate);
            return this;
        }
    }

}

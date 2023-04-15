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

import org.codelibs.fess.plugin.webapp.api.chatgpt.util.DateUtil;

public class Document {
    protected String id;
    protected final String text;
    protected final Metadata metadata;

    public Document(final String text) {
        this.text = text;
        this.metadata = new Metadata();
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public static class Metadata {
        protected Source source = Source.UNKNOWN;
        protected String sourceId;
        protected String url;
        protected String author;
        protected long createdAt;

        public Source getSource() {
            return source;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getUrl() {
            return url;
        }

        public String getAuthor() {
            return author;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    public static class DocumentBuilder {
        final Document document;

        public DocumentBuilder(final String text) {
            document = new Document(text);
        }

        public Document build() {
            return document;
        }

        public DocumentBuilder id(final String id) {
            document.id = id;
            return this;
        }

        public DocumentBuilder source(final String source) {
            if (Source.EMAIL.name().equalsIgnoreCase(source)) {
                document.metadata.source = Source.EMAIL;
            } else if (Source.FILE.name().equalsIgnoreCase(source)) {
                document.metadata.source = Source.FILE;
            } else if (Source.CHAT.name().equalsIgnoreCase(source)) {
                document.metadata.source = Source.CHAT;
            }
            return this;
        }

        public DocumentBuilder sourceId(final String sourceId) {
            document.metadata.sourceId = sourceId;
            return this;
        }

        public DocumentBuilder url(final String url) {
            document.metadata.url = url;
            return this;
        }

        public DocumentBuilder author(final String author) {
            document.metadata.author = author;
            return this;
        }

        public DocumentBuilder createdAt(final String createdAt) {
            document.metadata.createdAt = DateUtil.parse(createdAt);
            return this;
        }
    }

}

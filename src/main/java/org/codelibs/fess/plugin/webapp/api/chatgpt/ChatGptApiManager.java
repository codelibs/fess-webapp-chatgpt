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
package org.codelibs.fess.plugin.webapp.api.chatgpt;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.Constants;
import org.codelibs.fess.api.BaseApiManager;
import org.codelibs.fess.entity.FacetInfo;
import org.codelibs.fess.entity.GeoInfo;
import org.codelibs.fess.entity.HighlightInfo;
import org.codelibs.fess.entity.SearchRenderData;
import org.codelibs.fess.entity.SearchRequestParams;
import org.codelibs.fess.exception.InvalidAccessTokenException;
import org.codelibs.fess.exception.InvalidQueryException;
import org.codelibs.fess.exception.ResultOffsetExceededException;
import org.codelibs.fess.helper.SearchHelper;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Query;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Query.Filter;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.QueryResult;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Source;
import org.codelibs.fess.plugin.webapp.api.chatgpt.exception.FessChatGptResponseException;
import org.codelibs.fess.plugin.webapp.api.chatgpt.parser.QueryParser;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.web.util.LaResponseUtil;

public class ChatGptApiManager extends BaseApiManager {

    private static final Logger logger = LogManager.getLogger(ChatGptApiManager.class);

    protected String mimeType = "application/json";

    public ChatGptApiManager() {
        setPathPrefix("/chatgpt");
    }

    @PostConstruct
    public void register() {
        if (logger.isInfoEnabled()) {
            logger.info("Load {}", this.getClass().getSimpleName());
        }
        // TODO init app
        ComponentUtil.getWebApiManagerFactory().add(this);
    }

    @Override
    public boolean matches(final HttpServletRequest request) {
        final String servletPath = request.getServletPath();
        return servletPath.startsWith(pathPrefix);
    }

    @Override
    public void process(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final String servletPath = request.getServletPath();
        switch (servletPath) {
        case "/.well-known/ai-plugin.json": {
            // TODO
            return;
        }
        case "/.well-known/logo.png": {
            // TODO
            return;
        }
        case "/.well-known/openapi.yaml": {
            // TODO
            return;
        }
        default:
            break;
        }

        final String[] values = servletPath.replaceAll("/+", "/").split("/");
        try {
            if (values.length > 2) {
                switch (values[2]) {
                case "upsert-file": {
                    // TODO
                    return;
                }
                case "upsert": {
                    // TODO
                    return;
                }
                case "query": {
                    if (!"post".equalsIgnoreCase(request.getMethod())) {
                        processQueries(request, response);
                        return;
                    }
                    break;
                }
                default:
                    break;
                }
            }
        } catch (final FessChatGptResponseException e) {
            writeErrorResponse(e.getStatus(), e.getMessage(), e.getLocations());
            return;
        }

        writeErrorResponse(HttpServletResponse.SC_NOT_FOUND, "Cannot understand your request.", StringUtil.EMPTY_STRINGS);
    }

    protected void processQueries(final HttpServletRequest request, final HttpServletResponse response) {
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        try (QueryParser parser = new QueryParser(request.getInputStream())) {
            final StringBuilder buf = new StringBuilder(1000);
            buf.append("{\"results\":[");
            for (final Query query : parser.parse()) {
                buf.append(processQuery(request, response, query, fessConfig));
            }
            buf.append("]}");
            response.setStatus(HttpServletResponse.SC_OK);
            write(buf.toString(), mimeType, Constants.UTF_8);
        } catch (final Exception e) {
            throwErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot process your request.");
        }
    }

    protected String processQuery(final HttpServletRequest request, final HttpServletResponse response, final Query query,
            final FessConfig fessConfig) {
        final SearchHelper searchHelper = ComponentUtil.getSearchHelper();

        request.setAttribute(Constants.SEARCH_LOG_ACCESS_TYPE, Constants.SEARCH_LOG_ACCESS_TYPE_JSON);
        try {
            final SearchRenderData data = new SearchRenderData();
            final QueryRequestParams params = new QueryRequestParams(request, fessConfig, query);
            searchHelper.search(params, data, OptionalThing.empty());
            final QueryResult queryResult = QueryResult.create(data);
            return queryResult.toJsonString();
        } catch (final InvalidQueryException | ResultOffsetExceededException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to process a search request.", e);
            }
            throwErrorResponse(HttpServletResponse.SC_BAD_REQUEST, "Cannot understande your query.", e);
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to process a search request.", e);
            }
            throwErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot process your request.", e);
        }
        return StringUtil.EMPTY;
    }

    protected void throwErrorResponse(final int status, final String message, final Throwable t) {
        final Supplier<String> stacktraceString = () -> {
            final StringBuilder buf = new StringBuilder(100);
            if (StringUtil.isBlank(t.getMessage())) {
                buf.append(t.getClass().getName());
            } else {
                buf.append(t.getMessage());
            }
            try (final StringWriter sw = new StringWriter(); final PrintWriter pw = new PrintWriter(sw)) {
                t.printStackTrace(pw);
                pw.flush();
                buf.append(" [ ").append(sw.toString()).append(" ]");
            } catch (final IOException ignore) {}
            return buf.toString();
        };
        final String[] locations;
        if (Constants.TRUE.equalsIgnoreCase(ComponentUtil.getFessConfig().getApiJsonResponseExceptionIncluded())) {
            locations = stacktraceString.get().split("\n");
        } else {
            final String errorCode = UUID.randomUUID().toString();
            locations = new String[] { "error_code:" + errorCode };
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] {}", errorCode, stacktraceString.get().replace("\n", "\\n"));
            } else {
                logger.warn("[{}] {}", errorCode, t.getMessage());
            }
        }
        final HttpServletResponse response = LaResponseUtil.getResponse();
        if (t instanceof final InvalidAccessTokenException e) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"" + e.getType() + "\"");
            writeErrorResponse(HttpServletResponse.SC_UNAUTHORIZED, message, locations);
        } else {
            writeErrorResponse(status, message, locations);
        }
    }

    protected void throwErrorResponse(final int status, final String message) {
        throwErrorResponse(status, message, StringUtil.EMPTY_STRINGS);
    }

    protected void throwErrorResponse(final int status, final String message, final String[] locations) {
        throw new FessChatGptResponseException(status, message, locations);
    }

    protected void writeErrorResponse(final int status, final String message, final String[] locations) {
        final HttpServletResponse response = LaResponseUtil.getResponse();
        response.setStatus(status);
        final StringBuilder buf = new StringBuilder();
        buf.append("{\"detail\":[");
        buf.append("{\"msg\":\"").append(StringEscapeUtils.escapeJson(message)).append('"');
        if (locations.length > 0) {
            buf.append(",\"loc\":[");
            buf.append(Arrays.stream(locations).map(s -> "\"" + StringEscapeUtils.escapeJson(s) + "\"").collect(Collectors.joining(",")));
            buf.append(']');
        }
        buf.append('}');
        buf.append("]}");
        write(buf.toString(), mimeType, Constants.UTF_8);
    }

    protected static class QueryRequestParams extends SearchRequestParams {

        private final HttpServletRequest request;
        private final FessConfig fessConfig;
        private final Query query;

        protected QueryRequestParams(final HttpServletRequest request, final FessConfig fessConfig, final Query query) {
            this.request = request;
            this.fessConfig = fessConfig;
            this.query = query;
        }

        @Override
        public String getQuery() {
            return query.getQuery();
        }

        @Override
        public Map<String, String[]> getFields() {
            final HashMap<String, String[]> fieldMap = new HashMap<>();
            final Filter filter = query.getFilter();
            if (StringUtil.isNotBlank(filter.getDocumentId())) {
                fieldMap.put(fessConfig.getIndexFieldDocId(), new String[] { filter.getDocumentId() });
            }
            if (filter.getSource() != Source.UNKNOWN) {
                fieldMap.put("source_id", new String[] { filter.getSource().name() });
            }
            if (StringUtil.isNotBlank(filter.getSourceId())) {
                fieldMap.put("source_id", new String[] { filter.getSourceId() });
            }
            if (StringUtil.isNotBlank(filter.getAuthor())) {
                fieldMap.put("author", new String[] { filter.getAuthor() });
            }
            return fieldMap;
        }

        @Override
        public Map<String, String[]> getConditions() {
            return Collections.emptyMap();
        }

        @Override
        public String[] getLanguages() {
            return StringUtil.EMPTY_STRINGS;
        }

        @Override
        public GeoInfo getGeoInfo() {
            return null;
        }

        @Override
        public FacetInfo getFacetInfo() {
            return null;
        }

        @Override
        public HighlightInfo getHighlightInfo() {
            return null;
        }

        @Override
        public String getSort() {
            return null;
        }

        @Override
        public int getStartPosition() {
            return 0;
        }

        @Override
        public int getPageSize() {
            return query.getTopK();
        }

        @Override
        public String[] getExtraQueries() {
            final Filter filter = query.getFilter();
            final String startDate = filter.getStartDate();
            final String endDate = filter.getEndDate();
            if (StringUtil.isNotBlank(startDate) || StringUtil.isNotBlank(endDate)) {
                final StringBuilder buf = new StringBuilder();
                buf.append(fessConfig.getIndexFieldTimestamp()).append(":[");
                if (StringUtil.isNotBlank(startDate)) {
                    buf.append(startDate);
                } else {
                    buf.append('*');
                }
                buf.append(' ');
                if (StringUtil.isNotBlank(endDate)) {
                    buf.append(endDate);
                } else {
                    buf.append('*');
                }
                buf.append(']');
                return new String[] { buf.toString() };
            }
            return StringUtil.EMPTY_STRINGS;
        }

        @Override
        public Object getAttribute(final String name) {
            return request.getAttribute(name);
        }

        @Override
        public Locale getLocale() {
            return request.getLocale();
        }

        @Override
        public SearchRequestType getType() {
            return SearchRequestType.JSON;
        }

        @Override
        public String getSimilarDocHash() {
            return null;
        }
    }

    @Override
    protected void writeHeaders(final HttpServletResponse response) {
        ComponentUtil.getFessConfig().getApiGsaResponseHeaderList().forEach(e -> response.setHeader(e.getFirst(), e.getSecond()));
    }
}

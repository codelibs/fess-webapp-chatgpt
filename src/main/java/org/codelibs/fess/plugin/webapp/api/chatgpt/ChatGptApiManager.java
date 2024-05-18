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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import org.codelibs.core.io.CopyUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.core.security.MessageDigestUtil;
import org.codelibs.fess.Constants;
import org.codelibs.fess.api.BaseApiManager;
import org.codelibs.fess.entity.FacetInfo;
import org.codelibs.fess.entity.GeoInfo;
import org.codelibs.fess.entity.HighlightInfo;
import org.codelibs.fess.entity.SearchRenderData;
import org.codelibs.fess.entity.SearchRequestParams;
import org.codelibs.fess.es.client.SearchEngineClient;
import org.codelibs.fess.exception.InvalidAccessTokenException;
import org.codelibs.fess.exception.InvalidQueryException;
import org.codelibs.fess.exception.ResultOffsetExceededException;
import org.codelibs.fess.helper.CrawlingInfoHelper;
import org.codelibs.fess.helper.SearchHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.plugin.webapp.api.chatgpt.auth.PluginAuthenticator;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Document;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Document.Metadata;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Query;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Query.Filter;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.QueryResult;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Source;
import org.codelibs.fess.plugin.webapp.api.chatgpt.exception.FessChatGptResponseException;
import org.codelibs.fess.plugin.webapp.api.chatgpt.parser.DocumentParser;
import org.codelibs.fess.plugin.webapp.api.chatgpt.parser.QueryParser;
import org.codelibs.fess.plugin.webapp.api.chatgpt.util.DateUtil;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.web.util.LaResponseUtil;

public class ChatGptApiManager extends BaseApiManager {

    private static final Logger logger = LogManager.getLogger(ChatGptApiManager.class);

    protected static final String FESS_CHATGPT_LOGO_URL = "fess.chatgpt.logo.url";

    protected static final String FESS_CHATGPT_OPENAPI_YAML_URL = "fess.chatgpt.openapi_yaml.url";

    protected static final String FESS_CHATGPT_OPENAPI_URL = "fess.chatgpt.openapi.url";

    protected static final String FESS_CHATGPT_DEFAULT_CONFIG_ID = "fess.chatgpt.default.config_id";

    protected static final String FESS_CHATGPT_DEFAULT_HOST = "fess.chatgpt.default.host";

    protected static final String FESS_CHATGPT_DEFAULT_VIRTUAL_HOSTS = "fess.chatgpt.default.virtual_hosts";

    protected static final String FESS_CHATGPT_DEFAULT_ROLES = "fess.chatgpt.default.roles";

    protected static final String FESS_CHATGPT_BASE_URL = "fess.chatgpt.base_url";

    protected static final String FESS_CHATGPT_RESPONSE_FIELDS = "fess.chatgpt.response_fields";

    protected static final String FESS_CHATGPT_AI_PLUGIN_DESCRIPTION_FOR_HUMAN = "fess.chatgpt.ai_plugin.description_for_human";

    protected static final String FESS_CHATGPT_AI_PLUGIN_DESCRIPTION_FOR_MODEL = "fess.chatgpt.ai_plugin.description_for_model";

    protected static final String FESS_CHATGPT_AI_PLUGIN_NAME_FOR_HUMAN = "fess.chatgpt.ai_plugin.name_for_human";

    protected static final String FESS_CHATGPT_AI_PLUGIN_NAME_FOR_MODEL = "fess.chatgpt.ai_plugin.name_for_model";

    protected static final String CHATGPT_PERMISSION_LIST = "chatgpt.permissionList";

    protected static final String LOCALHOST_URL = "http://localhost:8080";

    protected static final String OPENAPI_YAML_PATH = "/.well-known/openapi.yaml";

    protected static final String LOGO_PNG_PATH = "/.well-known/logo.png";

    protected static final String AI_PLUGIN_JSON_PATH = "/.well-known/ai-plugin.json";

    protected static final String AUTHOR_FIELD = "author";

    protected String mimeType = "application/json";

    protected PluginAuthenticator pluginAuthenticator;

    public ChatGptApiManager() {
        setPathPrefix("/chatgpt");
    }

    @PostConstruct
    public void register() {
        if (logger.isInfoEnabled()) {
            logger.info("Load {}", this.getClass().getSimpleName());
        }

        ComponentUtil.getWebApiManagerFactory().add(this);
        pluginAuthenticator = ComponentUtil.getComponent("pluginAuthenticator");
    }

    protected String[] getResponseFields() {
        return System.getProperty(FESS_CHATGPT_RESPONSE_FIELDS, "source,filename,url,timestamp,doc_id,content," + AUTHOR_FIELD).split(",");
    }

    protected String getBaseUrl() {
        return System.getProperty(FESS_CHATGPT_BASE_URL, "https://github.com/codelibs/fess-webapp-chatgpt");
    }

    protected List<String> getDefaultRoleList() {
        return Arrays.stream(System.getProperty(FESS_CHATGPT_DEFAULT_ROLES, "Rguest").split(",")).filter(StringUtil::isNotBlank).toList();
    }

    protected List<String> getDefaultVirtualHostList() {
        return Arrays.stream(System.getProperty(FESS_CHATGPT_DEFAULT_VIRTUAL_HOSTS, StringUtil.EMPTY).split(","))
                .filter(StringUtil::isNotBlank).toList();
    }

    protected String getDefaultHost() {
        return System.getProperty(FESS_CHATGPT_DEFAULT_HOST, "chatgpt");
    }

    protected String getDefaultConfigId() {
        return System.getProperty(FESS_CHATGPT_DEFAULT_CONFIG_ID, "chatgpt");
    }

    @Override
    public boolean matches(final HttpServletRequest request) {
        final String servletPath = request.getServletPath();
        if (servletPath.startsWith(pathPrefix)) {
            return true;
        }
        return "get".equalsIgnoreCase(request.getMethod())
                && (AI_PLUGIN_JSON_PATH.equals(servletPath) || LOGO_PNG_PATH.equals(servletPath) || OPENAPI_YAML_PATH.equals(servletPath));
    }

    @Override
    public void process(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final String servletPath = request.getServletPath();
        switch (servletPath) {
        case AI_PLUGIN_JSON_PATH: {
            processAiPluginJson(response);
            return;
        }
        case LOGO_PNG_PATH: {
            processLogoPng(response);
            return;
        }
        case OPENAPI_YAML_PATH: {
            processOpenApiYaml(response);
            return;
        }
        default:
            break;
        }

        final String[] values = servletPath.replaceAll("/+", "/").split("/");
        try {
            request.setAttribute(CHATGPT_PERMISSION_LIST, pluginAuthenticator.authenticate(request));

            if (values.length > 2) {
                switch (values[2]) {
                case "upsert-file": {
                    // TODO
                    writeErrorResponse(HttpServletResponse.SC_NOT_FOUND, "Your request is not supported.", StringUtil.EMPTY_STRINGS);
                    return;
                }
                case "upsert": {
                    if ("post".equalsIgnoreCase(request.getMethod())) {
                        processUpsert(request, response);
                        return;
                    }
                }
                case "query": {
                    if ("post".equalsIgnoreCase(request.getMethod())) {
                        processQuery(request, response);
                        return;
                    }
                    break;
                }
                default:
                    break;
                }
            }
        } catch (final InvalidAccessTokenException e) {
            writeErrorResponse(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage(), e);
            return;
        } catch (final FessChatGptResponseException e) {
            writeErrorResponse(e.getStatus(), e.getMessage(), e.getLocations());
            return;
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to process {}", servletPath, e);
            }
            writeErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
            return;
        }

        writeErrorResponse(HttpServletResponse.SC_NOT_FOUND, "Cannot understand your request.", StringUtil.EMPTY_STRINGS);
    }

    protected void processOpenApiYaml(final HttpServletResponse response) {
        final StringBuilder buf = new StringBuilder(8000);
        try (final BufferedReader br = new BufferedReader(
                new InputStreamReader(ResourceUtil.getResourceAsStream("/chatgpt/openapi.yaml"), Constants.CHARSET_UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                buf.append(line).append('\n');
            }

            final String url = System.getProperty(FESS_CHATGPT_OPENAPI_URL, LOCALHOST_URL + "/chatgpt");
            response.setStatus(HttpServletResponse.SC_OK);
            write(buf.toString().replace(LOCALHOST_URL + "/chatgpt", url), "application/x-yaml", Constants.UTF_8);
        } catch (final Exception e) {
            writeErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot process your request.", e);
        }
    }

    protected void processAiPluginJson(final HttpServletResponse response) {
        final StringBuilder buf = new StringBuilder(8000);
        try (final BufferedReader br = new BufferedReader(
                new InputStreamReader(ResourceUtil.getResourceAsStream("/chatgpt/ai-plugin.json"), Constants.CHARSET_UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                buf.append(line).append('\n');
            }

            response.setStatus(HttpServletResponse.SC_OK);
            write(updateAiPluginContent(buf), "application/json", Constants.UTF_8);
        } catch (final Exception e) {
            writeErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot process your request.", e);
        }
    }

    protected String updateAiPluginContent(final StringBuilder contentBuf) {
        final String openapiYamlUrl = System.getProperty(FESS_CHATGPT_OPENAPI_YAML_URL, LOCALHOST_URL + OPENAPI_YAML_PATH);
        final String logoUrl = System.getProperty(FESS_CHATGPT_LOGO_URL, LOCALHOST_URL + LOGO_PNG_PATH);
        String content = contentBuf.toString()//
                .replace(LOCALHOST_URL + OPENAPI_YAML_PATH, openapiYamlUrl)//
                .replace(LOCALHOST_URL + LOGO_PNG_PATH, logoUrl)//
                .replace("{\"type\":\"none\"}", pluginAuthenticator.getAiPluginJson());
        final String nameForModel = System.getProperty(FESS_CHATGPT_AI_PLUGIN_NAME_FOR_MODEL);
        if (nameForModel != null) {
            content = content.replace("\"name_for_model\": \"retrieval\"", "\"name_for_model\": \"" + nameForModel + "\"");
        }
        final String nameForHuman = System.getProperty(FESS_CHATGPT_AI_PLUGIN_NAME_FOR_HUMAN);
        if (nameForHuman != null) {
            content = content.replace("\"name_for_human\": \"Fess Plugin\"", "\"name_for_human\": \"" + nameForHuman + "\"");
        }
        final String descriptionForModel = System.getProperty(FESS_CHATGPT_AI_PLUGIN_DESCRIPTION_FOR_MODEL);
        if (descriptionForModel != null) {
            content =
                    content.replaceFirst("\"description_for_model\": \".*\"", "\"description_for_model\": \"" + descriptionForModel + "\"");
        }
        final String descriptionForHuman = System.getProperty(FESS_CHATGPT_AI_PLUGIN_DESCRIPTION_FOR_HUMAN);
        if (descriptionForHuman != null) {
            content =
                    content.replaceFirst("\"description_for_human\": \".*\"", "\"description_for_human\": \"" + descriptionForHuman + "\"");
        }
        return content;
    }

    protected void processLogoPng(final HttpServletResponse response) {
        try (InputStream in = new BufferedInputStream(ResourceUtil.getResourceAsStream("/chatgpt/logo.png"));
                OutputStream out = response.getOutputStream()) {
            CopyUtil.copy(in, out);
        } catch (final Exception e) {
            writeErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot process your request.", e);
        }
    }

    protected void processUpsert(final HttpServletRequest request, final HttpServletResponse response) {
        final SearchEngineClient client = ComponentUtil.getSearchEngineClient();
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final String segment = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(ComponentUtil.getSystemHelper().getCurrentTime());
        try (DocumentParser parser = new DocumentParser(request.getInputStream())) {
            final Document[] documents = parser.parse();
            final List<Map<String, Object>> docList =
                    Arrays.stream(documents).map(d -> createDocMap(request, segment, d, fessConfig)).toList();
            final String[] docIds = client.addAll(fessConfig.getIndexDocumentUpdateIndex(), docList, (doc, builder) -> {});
            final StringBuilder buf = new StringBuilder(1000);
            buf.append("{\"ids\":[")
                    .append(Arrays.stream(docIds).map(s -> "\"" + StringEscapeUtils.escapeJson(s) + "\"").collect(Collectors.joining(",")))
                    .append("]}");
            response.setStatus(HttpServletResponse.SC_OK);
            write(buf.toString(), mimeType, Constants.UTF_8);
        } catch (final Exception e) {
            writeErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot process your request.", e);
        }
    }

    protected Map<String, Object> createDocMap(final HttpServletRequest request, final String segment, final Document document,
            final FessConfig fessConfig) {
        final SystemHelper systemHelper = ComponentUtil.getSystemHelper();
        final Map<String, Object> docMap = new HashMap<>();
        docMap.put(fessConfig.getIndexFieldContent(), document.getText());
        final Metadata metadata = document.getMetadata();
        if (metadata.getSource() != Source.UNKNOWN) {
            docMap.put(fessConfig.getIndexFieldLabel(), Arrays.asList(metadata.getSource().name()));
        }
        if (StringUtil.isNotBlank(metadata.getSourceId())) {
            docMap.put(fessConfig.getIndexFieldFilename(), metadata.getSourceId());
        } else {
            docMap.put(fessConfig.getIndexFieldFilename(), StringUtil.EMPTY);
        }
        if (StringUtil.isNotBlank(metadata.getUrl())) {
            docMap.put(fessConfig.getIndexFieldUrl(), metadata.getUrl());
        } else {
            final String digest = MessageDigestUtil.digest(ComponentUtil.getFessConfig().getIndexIdDigestAlgorithm(), document.getText());
            docMap.put(fessConfig.getIndexFieldUrl(), getBaseUrl() + "?" + digest);
        }
        if (StringUtil.isNotBlank(metadata.getAuthor())) {
            docMap.put(AUTHOR_FIELD, metadata.getAuthor());
        }
        docMap.put(fessConfig.getIndexFieldContent(), document.getText());
        final String createdAt =
                DateUtil.format(metadata.getCreatedAt() != 0L ? metadata.getCreatedAt() : systemHelper.getCurrentTime().getTime());
        docMap.put(fessConfig.getIndexFieldTimestamp(), createdAt);
        docMap.put(fessConfig.getIndexFieldLastModified(), createdAt);
        docMap.put(fessConfig.getIndexFieldCreated(), createdAt);

        final List<String> roleList = new ArrayList<>();
        if (request.getAttribute(CHATGPT_PERMISSION_LIST) instanceof final List<?> permissionList) {
            permissionList.stream().map(Object::toString).forEach(roleList::add);
        }
        roleList.addAll(getDefaultRoleList());
        docMap.put(fessConfig.getIndexFieldRole(), roleList);
        docMap.put(fessConfig.getIndexFieldFiletype(), "txt");
        docMap.put(fessConfig.getIndexFieldClickCount(), 0);
        docMap.put(fessConfig.getIndexFieldTitle(), StringUtil.EMPTY);
        docMap.put(fessConfig.getIndexFieldSegment(), segment);
        docMap.put(fessConfig.getIndexFieldDigest(), StringUtil.EMPTY);
        docMap.put(fessConfig.getIndexFieldHost(), getDefaultHost());
        docMap.put(fessConfig.getIndexFieldFavoriteCount(), 0);
        docMap.put(fessConfig.getIndexFieldContentLength(), document.getText().length());
        docMap.put(fessConfig.getIndexFieldVirtualHost(), getDefaultVirtualHostList());
        docMap.put(fessConfig.getIndexFieldConfigId(), getDefaultConfigId());
        docMap.put(fessConfig.getIndexFieldParentId(), StringUtil.EMPTY);
        docMap.put(fessConfig.getIndexFieldAnchor(), StringUtil.EMPTY_STRINGS);
        docMap.put(fessConfig.getIndexFieldBoost(), 1.0f);
        docMap.put(fessConfig.getIndexFieldMimetype(), "text/plain");

        ComponentUtil.getLanguageHelper().updateDocument(docMap);
        docMap.put(fessConfig.getIndexFieldDocId(), systemHelper.generateDocId(docMap));

        if (StringUtil.isNotBlank(document.getId())) {
            docMap.put(fessConfig.getIndexFieldId(), document.getId());
        } else {
            final CrawlingInfoHelper crawlingInfoHelper = ComponentUtil.getCrawlingInfoHelper();
            docMap.put(fessConfig.getIndexFieldId(), crawlingInfoHelper.generateId(docMap));
        }
        return docMap;
    }

    protected void processQuery(final HttpServletRequest request, final HttpServletResponse response) {
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        request.setAttribute(Constants.SEARCH_LOG_ACCESS_TYPE, Constants.SEARCH_LOG_ACCESS_TYPE_JSON);
        try (QueryParser parser = new QueryParser(request.getInputStream())) {
            final StringBuilder buf = new StringBuilder(1000);
            buf.append("{\"results\":[");
            for (final Query query : parser.parse()) {
                buf.append(processQuery(request, response, query, fessConfig));
            }
            buf.append("]}");
            response.setStatus(HttpServletResponse.SC_OK);
            write(buf.toString(), mimeType, Constants.UTF_8);
        } catch (final InvalidQueryException | ResultOffsetExceededException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to process a search request.", e);
            }
            writeErrorResponse(HttpServletResponse.SC_BAD_REQUEST, "Cannot understand your query.", e);
        } catch (final InvalidAccessTokenException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Invalid access token.", e);
            }
            writeErrorResponse(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage(), e);
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to process a search request.", e);
            }
            writeErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot process your request.", e);
        }
    }

    protected String processQuery(final HttpServletRequest request, final HttpServletResponse response, final Query query,
            final FessConfig fessConfig) {
        final SearchHelper searchHelper = ComponentUtil.getSearchHelper();
        final SearchRenderData data = new SearchRenderData();
        final QueryRequestParams params = new QueryRequestParams(request, fessConfig, query, getResponseFields());
        request.setAttribute(Query.QUERY, query);
        searchHelper.search(params, data, OptionalThing.empty());
        final QueryResult queryResult = QueryResult.create(query, data);
        return queryResult.toJsonString();
    }

    protected void writeErrorResponse(final int status, final String message, final Throwable t) {
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
        private final String[] responseFields;

        protected QueryRequestParams(final HttpServletRequest request, final FessConfig fessConfig, final Query query,
                final String[] responseFields) {
            this.request = request;
            this.fessConfig = fessConfig;
            this.query = query;
            this.responseFields = responseFields;
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
                fieldMap.put(fessConfig.getIndexFieldLabel(), new String[] { filter.getSource().name() });
            }
            if (StringUtil.isNotBlank(filter.getSourceId())) {
                fieldMap.put(fessConfig.getIndexFieldFilename(), new String[] { filter.getSourceId() });
            }
            if (StringUtil.isNotBlank(filter.getAuthor())) {
                fieldMap.put(AUTHOR_FIELD, new String[] { filter.getAuthor() });
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
        public int getOffset() {
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

        @Override
        public String[] getResponseFields() {
            return responseFields;
        }
    }

    @Override
    protected void writeHeaders(final HttpServletResponse response) {
        ComponentUtil.getFessConfig().getApiGsaResponseHeaderList().forEach(e -> response.setHeader(e.getFirst(), e.getSecond()));
    }
}

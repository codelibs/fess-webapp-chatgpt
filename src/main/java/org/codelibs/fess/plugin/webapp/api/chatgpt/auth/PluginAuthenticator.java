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
package org.codelibs.fess.plugin.webapp.api.chatgpt.auth;

import static org.codelibs.core.stream.StreamUtil.stream;

import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.es.config.exbhv.AccessTokenBhv;
import org.codelibs.fess.exception.InvalidAccessTokenException;
import org.codelibs.fess.util.ComponentUtil;

public class PluginAuthenticator {
    private static final Logger logger = LogManager.getLogger(PluginAuthenticator.class);

    public String getAiPluginJson() {
        final String verificationToken = getverificationToken();
        if (StringUtil.isBlank(verificationToken)) {
            return "{\"type\":\"none\"}";
        }
        final StringBuilder buf = new StringBuilder(100);
        buf.append('{');
        buf.append("\"type\":\"service_http\",");
        buf.append("\"authorization_type\":\"bearer\",");
        buf.append("\"verification_tokens\":{");
        buf.append("\"openai\":\"").append(StringEscapeUtils.escapeJson(verificationToken)).append('"');
        buf.append('}');
        buf.append('}');
        return buf.toString();
    }

    public List<String> authenticate(final HttpServletRequest request) {
        final String token = ComponentUtil.getAccessTokenHelper().getAccessTokenFromRequest(request);
        if (StringUtil.isBlank(token)) {
            if (!isAuthenticated()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("verification_token is empty. no authentication.");
                }
                return Collections.emptyList();
            }
            throw new InvalidAccessTokenException("no_token", "The token is specified.");
        }

        return ComponentUtil.getComponent(AccessTokenBhv.class).selectEntity(cb -> {
            cb.query().setToken_Term(token);
        }).map(accessToken -> {
            final Long expiredTime = accessToken.getExpiredTime();
            if (expiredTime != null && expiredTime.longValue() > 0
                    && expiredTime.longValue() < ComponentUtil.getSystemHelper().getCurrentTimeAsLong()) {
                throw new InvalidAccessTokenException("expired_token", "The token is expired.");
            }
            final List<String> permissionList = stream(accessToken.getPermissions()).get(stream -> stream.distinct().toList());
            if (permissionList.isEmpty()) {
                throw new InvalidAccessTokenException("no_permissions", "Your token does not contain permissions for this system.");
            }
            return permissionList;
        }).orElseThrow(() -> new InvalidAccessTokenException("invalid_token", "Your token is invalid."));
    }

    public boolean isAuthenticated() {
        return !StringUtil.isBlank(getverificationToken());
    }

    protected String getverificationToken() {
        return System.getProperty("fess.chatgpt.verification_token", StringUtil.EMPTY);
    }
}

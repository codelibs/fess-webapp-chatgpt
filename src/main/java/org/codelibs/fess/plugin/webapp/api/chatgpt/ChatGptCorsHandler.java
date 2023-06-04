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

import javax.annotation.PostConstruct;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.codelibs.core.lang.StringUtil;
import org.codelibs.core.stream.StreamUtil;
import org.codelibs.fess.cors.CorsHandler;
import org.codelibs.fess.cors.CorsHandlerFactory;
import org.codelibs.fess.util.ComponentUtil;

public class ChatGptCorsHandler extends CorsHandler {

    @PostConstruct
    public void register() {
        final CorsHandlerFactory factory = ComponentUtil.getCorsHandlerFactory();
        String allowOrigin = System.getProperty("fess.chatgpt.cors.origin", "https://chat.openai.com");
        StreamUtil.split(allowOrigin, ",")
                .of(stream -> stream.map(String::trim).filter(StringUtil::isNotEmpty).forEach(s -> factory.add(s, this)));
    }

    @Override
    public void process(String origin, ServletRequest request, ServletResponse response) {
        final HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.addHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        httpResponse.addHeader(ACCESS_CONTROL_ALLOW_HEADERS, System.getProperty("fess.chatgpt.cors.headers", "*"));
        httpResponse.addHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, System.getProperty("fess.chatgpt.cors.credentials", "true"));
        httpResponse.addHeader(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK, System.getProperty("fess.chatgpt.cors.private_network", "true"));
    }

}

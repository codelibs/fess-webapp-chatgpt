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
package org.codelibs.fess.plugin.webapp.api.chatgpt.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.codelibs.fess.plugin.webapp.api.chatgpt.entity.Query;
import org.codelibs.fess.query.parser.QueryParser;
import org.lastaflute.web.util.LaRequestUtil;

public class CustomQueryParser extends QueryParser {

    private static final Logger logger = LogManager.getLogger(CustomQueryParser.class);

    @Override
    protected org.apache.lucene.queryparser.classic.QueryParser createQueryParser() {
        final LuceneQueryParser parser = new LuceneQueryParser(defaultField, analyzer);
        parser.setAllowLeadingWildcard(allowLeadingWildcard);
        LaRequestUtil.getOptionalRequest().ifPresentOrElse(req -> {
            if (req.getAttribute(Query.QUERY) instanceof final Query query) {
                final Operator operator = query.getOperator();
                if (logger.isDebugEnabled()) {
                    logger.debug("operator: {}", operator);
                }
                if (operator != null) {
                    parser.setDefaultOperator(operator);
                    return;
                }
            }
            parser.setDefaultOperator(defaultOperator);
        }, () -> {
            parser.setDefaultOperator(defaultOperator);
        });
        return parser;
    }
}

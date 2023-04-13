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
package org.codelibs.fess.plugin.webapp.api.chatgpt.util;

import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.taglib.FessFunctions;
import org.opensearch.common.time.DateFormatter;

public class DateUtil {
    private static final Logger logger = LogManager.getLogger(DateUtil.class);

    private static final DateFormatter DEFAULT_DATE_TIME_FORMATTER =
            DateFormatter.forPattern("date_optional_time||epoch_millis||yyyy-MM-dd HH:mm:ss");

    DateUtil() {
        // nothing
    }

    public static long parse(final String input) {
        if (StringUtil.isBlank(input)) {
            return 0;
        }
        try {
            return DEFAULT_DATE_TIME_FORMATTER.parseMillis(input);
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("invalid date format: {}", input, e);
            }
        }
        return 0;
    }

    public static String format(final long input) {
        if (input == 0) {
            return null;
        }
        return FessFunctions.formatDate(new Date(input));
    }
}

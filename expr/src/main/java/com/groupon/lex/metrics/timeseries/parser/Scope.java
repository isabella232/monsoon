/*
 * Copyright (c) 2016, Groupon, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * Neither the name of GROUPON nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.groupon.lex.metrics.timeseries.parser;

import static com.groupon.lex.metrics.timeseries.parser.Scope.Valid.DIFFERENT_IDENTIFIER;
import static com.groupon.lex.metrics.timeseries.parser.Scope.Valid.NOT_AN_IDENTIFIER;
import static com.groupon.lex.metrics.timeseries.parser.Scope.Valid.VALID_IDENTIFIER;
import static java.lang.Boolean.FALSE;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author ariane
 */
public interface Scope {
    public static Scope empty() {
        return new SimpleScope();
    }

    public static enum Type {
        GROUP,
        METRIC,
        VALUE
    }

    public static enum Valid {
        VALID_IDENTIFIER,
        DIFFERENT_IDENTIFIER,
        NOT_AN_IDENTIFIER
    }

    public Map<String, Type> getIdentifiers();

    public default Optional<Type> getIdentifier(String identifier) {
        return Optional.ofNullable(getIdentifiers().get(identifier));
    }

    public default boolean isIdentifier(String identifier) {
        return getIdentifiers().containsKey(identifier);
    }

    public default boolean isIdentifier(String identifier, Type type) {
        return getIdentifier(identifier).map((Type id_type) -> id_type == type).orElse(FALSE);
    }

    public default Valid isValidIdentifier(String identifier, Type type) {
        return getIdentifier(identifier)
                .map((Type id_type) -> id_type == type ? VALID_IDENTIFIER : DIFFERENT_IDENTIFIER)
                .orElse(NOT_AN_IDENTIFIER);
    }
}

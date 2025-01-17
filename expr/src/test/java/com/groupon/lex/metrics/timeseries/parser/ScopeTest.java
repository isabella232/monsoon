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

import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author ariane
 */
public class ScopeTest {
    @Test
    public void empty() {
        Scope scope = Scope.empty();

        assertTrue(scope.getIdentifiers().isEmpty());
        assertEquals(Optional.empty(), scope.getIdentifier("foo"));
        assertFalse(scope.isIdentifier("foo"));
        assertFalse(scope.isIdentifier("foo", Scope.Type.GROUP));
        assertFalse(scope.isIdentifier("foo", Scope.Type.METRIC));
        assertFalse(scope.isIdentifier("foo", Scope.Type.VALUE));
        assertEquals(Scope.Valid.NOT_AN_IDENTIFIER, scope.isValidIdentifier("foo", Scope.Type.GROUP));
        assertEquals(Scope.Valid.NOT_AN_IDENTIFIER, scope.isValidIdentifier("foo", Scope.Type.METRIC));
        assertEquals(Scope.Valid.NOT_AN_IDENTIFIER, scope.isValidIdentifier("foo", Scope.Type.VALUE));
    }
}

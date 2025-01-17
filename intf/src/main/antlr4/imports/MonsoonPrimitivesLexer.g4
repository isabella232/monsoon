/*
 * Copyright (c) 2016, 2017, Groupon, Inc.
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
lexer grammar MonsoonPrimitivesLexer;

@header {
    import java.util.regex.Matcher;
    import java.util.regex.Pattern;
}

@members {
    private static final Pattern SPECIAL = Pattern.compile("\\\\([\\\\abtnvfr'\"/]"
            + "|[0-7][0-7][0-7]"
            + "|[0-7][0-7]"
            + "|[0-7]"
            + "|x[0-9a-fA-F][0-9a-fA-F]"
            + "|u[0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F]"
            + "|U[0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F])");

    private static String handleEscapes(String s) {
        final StringBuilder result = new StringBuilder(s.length());

        Matcher matcher = SPECIAL.matcher(s);
        int b = 0;
        while (matcher.find()) {
            result.append(s, b, matcher.start());
            b = matcher.end();

            switch (s.charAt(matcher.start() + 1)) {
                default:
                    throw new IllegalStateException("Programmer error: unhandled sequence " + s.substring(matcher.start(), matcher.end()));
                case '\\':
                    result.append('\\');
                    break;
                case 'a':
                    result.append('\007'); // Unknown to java.
                    break;
                case 'b':
                    result.append('\010'); // Unknown to java.
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'n':
                    result.append('\n');
                    break;
                case 'v':
                    result.append('\013'); // Unknown to java.
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case '/':
                    result.append('/');
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                    final String octal_part = s.substring(matcher.start() + 1, matcher.end());
                    final int ch_int = Integer.parseInt(octal_part, 8);
                    if (ch_int > 127)
                        throw new NumberFormatException("Invalid octal escape: " + octal_part);
                    result.append((char) ch_int);
                    break;
                case 'x':
                case 'u':
                case 'U':
                    final String hex_part = s.substring(matcher.start() + 2, matcher.end());
                    final int unicode_int = Integer.parseInt(hex_part, 16);
                    if (unicode_int > 0x10ffff)
                        throw new NumberFormatException("Invalid hex escape: " + hex_part);
                    result.append(Character.toChars(unicode_int));
                    break;
            }
        }
        result.append(s, b, s.length());

        return result.toString();
    }
}


WS              : (' '|'\n'|'\t')+
                  { skip(); }
                ;
ID              : ('_'|'a'..'z'|'A'..'Z') ('_'|'a'..'z'|'A'..'Z'|'0'..'9')*
                ;


FP_DECIMAL      : ('0'..'9')+ (('e'|'E') '-'? ('0'..'9')+)
                | ('0'..'9')* '.' ('0'..'9')+ (('e'|'E') ('0'..'9')+)?
                ;
FP_HEX          : '0x' ('0'..'9'|'a'..'f'|'A'..'F')+ ('.' ('0'..'9'|'a'..'f'|'A'..'F')*)
                | '0x' ('0'..'9'|'a'..'f'|'A'..'F')+ ('.' ('0'..'9'|'a'..'f'|'A'..'F')*)? (('p'|'P') '-'? ('0'..'9'|'a'..'f'|'A'..'F')+)
                ;
DIGITS          : ('1'..'9') ('0'..'9')*
                ;
HEXDIGITS       : '0x' ('0'..'9'|'a'..'f'|'A'..'F')+
                ;
OCTDIGITS       : '0' ('0'..'7')*
                ;


/*
 * String logic.
 *
 * Strings are enclosed in double quotes and may contain escape sequences.
 * Strings are sensitive to white space.
 */

QSTRING         : '"'
                  ( ~('\\'|'"'|'\u0000'..'\u001f')
                  | '\\\\'
                  | '\\a'
                  | '\\b'
                  | '\\t'
                  | '\\n'
                  | '\\v'
                  | '\\f'
                  | '\\r'
                  | '\\\''
                  | '\\"'
                  | '\\/'
                  | '\\' ('0'..'7') ('0'..'7') ('0'..'7')
                  | '\\' ('0'..'7') ('0'..'7')
                  | '\\' ('0'..'7')
                  | '\\x' ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                  | '\\u' ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                  | '\\U' ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                  )*
                  '"'
                  { setText(handleEscapes(getText().substring(1, getText().length() - 1))); }
                ;
SQSTRING        : '\''
                  ( ~('\\'|'\''|'\u0000'..'\u001f')
                  | '\\\\'
                  | '\\a'
                  | '\\b'
                  | '\\t'
                  | '\\n'
                  | '\\v'
                  | '\\f'
                  | '\\r'
                  | '\\\''
                  | '\\"'
                  | '\\/'
                  | '\\' ('0'..'7') ('0'..'7') ('0'..'7')
                  | '\\' ('0'..'7') ('0'..'7')
                  | '\\' ('0'..'7')
                  | '\\x' ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                  | '\\u' ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                  | '\\U' ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                          ('0'..'9' | 'a'..'f' | 'A'..'F')
                  )*
                  '\''
                  { setText(handleEscapes(getText().substring(1, getText().length() - 1))); }
                ;
REGEX           : '//'
                  ( '/' (~('/'|'\\') | '\\' .)
                  | '\\' .
                  | ~('/')
                  )*
                  '//'
                  { setText(getText().substring(2, getText().length() - 2).replace("\\/", "/")); }
                ;


SQBRACE_OPEN_LIT: '['
                ;
SQBRACE_CLOSE_LIT
                : ']'
                ;
DASH_LIT        : '-'
                ;
DOT_DOT_LIT     : '..'
                ;
EQ_KW           : '='
                ;

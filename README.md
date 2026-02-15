# RFC4180 CSV Parser (Java)

A Java implementation of RFC-4180 style parsing built using a FSM table.

## Features

- RFC-4180 Compliant
- Streaming Parser
- Accepts any Reader that extends Java Reader class
- Configurable Line-Ending Policy (LF-only, or normalise CR/CRLF to LF)
- Configurable Whitespace Trimming
- Fixed COMMA Delimiter as of now
- Debuggable DFA (Deterministic Finite Automaton) Trace Mode

## Design

The Parser is implemented as a FSM table with separate transition and action tables, resembling a Mealy Machine.
Line-ending normalisation and whitespace trimming both are handled in a safe manner without multichar lookaheads.

## Configurable Options

- `onlyLF`: Accept only LF line-endings
- `trimSpaces`: Trim leading+trailing spaces for both quoted/unquoted fields
- `enableFSMTrace`: Output DFA state transitions for learning/debugging

## Status

Solely an educational project as of now. Theory of Computation got me interested in making this.
I wanted to understand CSV Parsing. Even though it is simple, it still connects many subfields of TCS.

## Future Improvements

- Custom Delimiters
- Two-Phase Arch (Tokenizer+Parser) in place of current single FSM
- Benchmark Comparisons and more testing 


# RFC4180 CSV Parser (Java)

A Java implementation of RFC-4180 style parsing built using a FSM table.

## Features

- RFC-4180 Compliant
- Streaming Parser
- Accepts any Reader that extends Java Reader class
- Configurable Line-Ending Policy (LF-only, normalised CRLF-only to LF, normalised CR/CRLF to LF)
- Configurable Whitespace Trimming
- Fixed COMMA Delimiter as of now
- Debuggable FSM (Finite State Machine) Trace Mode

## Design

The Parser is implemented as a FSM table with separate transition and action tables, resembling a Mealy Machine.
Line-ending normalisation and whitespace trimming both are handled in a safe manner without multichar lookaheads.

## Configurable Options

- `model`: 0 for UNIX line endings (LF), 1 for WINDOWS line endings (CRLF), 2 for LENIENT mode (CR/CRLF)
- `trimSpaces`: Trim leading+trailing spaces for both quoted/unquoted fields
- `enableFSMTrace`: Output FSM state transitions for learning/debugging

## Status

This is solely an educational project as of now. Theory of Computation had gotten me interested in making this.
Weekly or monthly updates are scheduled.

## Future Improvements

- Custom Delimiters
- Two-Phase Architecture (Tokenizer+Parser) in place of current single FSM + hardcoded workarounds
- Benchmark Comparisons and more testing 


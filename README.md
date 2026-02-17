# RFC4180 CSV Parser (Java)

A lightweight Java implementation of RFC-4180 style parsing built using FSM tables. 

## Features

- RFC-4180 Compliant
- Streaming Parser
- Accepts any Reader that extends Java Reader class
- Configurable Line-Ending Policy (LF-only, normalised CRLF-only to LF, normalised CR/CRLF to LF)
- Configurable Whitespace Trimming
- Allows any ASCII Delimiter (up to 5 as of now)
- Debuggable FSM (Finite State Machine) Trace Mode

## Design

This Parser is implemented as a FSM with separate transition and action tables, resembling a Mealy Machine.
Line-ending normalisation and whitespace trimming both are handled in a safe manner without multichar lookaheads.
Delimiter handling is optimised by BitSet operations.

## Configurable Options

- `model`: 0 for UNIX line endings (LF), 1 for WINDOWS line endings (CRLF), 2 for LENIENT mode (CR/CRLF)
- `trimSpaces`: Trim leading+trailing spaces for both quoted/unquoted fields
- `enableFSMTrace`: Output FSM state transitions for learning/debugging
- `delimiters`: A vararg that accepts any ASCII delimiter with DELIMITER_LIMIT of 5 

## Status

This is solely an educational project as of now. Theory of Computation had gotten me interested in making this.
Weekly or monthly updates are scheduled.

## Future Improvements

- Two-Phase Architecture (Tokenizer+Parser) in place of current single FSM + hardcoded workarounds
- Benchmark Comparisons and more testing 
- More optimizations

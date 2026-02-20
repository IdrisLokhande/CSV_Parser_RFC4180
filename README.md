# RFC4180 CSV Parser (Java)

A lightweight Java implementation of RFC-4180 style parsing built using FSM.
Pandas.read_csv() and Theory of Computation had gotten me interested in making this. 

## Features

- RFC-4180 Compliant
- Streaming, Lazy CSV Engine
- Accepts any Reader that extends Java Reader class
- Configurable Line-Ending Policy (LF-only, normalise CRLF-only to LF, normalise CR/CRLF to LF)
- Configurable Whitespace Trimming
- Allows any ASCII Delimiter (up to 5 as of now)
- Debuggable FSM (Finite State Machine) Trace Mode

## Design

- Parser is implemented as a FSM with separate transition and action tables, resembling a Mealy Machine
- Line-ending normalisation and whitespace trimming both are handled in a safe manner without multichar lookaheads
- Delimiter handling is optimised by BitSet operations
- Uses buffered reading with buffer of size 8192 bytes that enables lower system cost
- Equipped with custom CSVFormatException (extending RuntimeException) for flexible exception handling

## Configurable Options

- `mode`: UNIX Mode for LF line endings, WINDOWS Mode for CRLF line endings, LENIENT Mode for CR/CRLF line endings
- `trimSpaces`: Trim leading+trailing spaces for both quoted/unquoted fields
- `enableFSMTrace`: Output FSM state transitions for learning/debugging
- `delimiters`: A vararg that accepts any ASCII delimiter with DELIMITER_LIMIT of 5 

## Status

- This is solely an educational project as of now
- Fuzzy Tested till 500 MB generated CSV File
- Soak Tested till 100 MB generated CSV File
- Fuzzy and Soak stable, with throughput stabilising at approx. 44MB/s
- Weekly or monthly updates are scheduled.

## Future Improvements

- Two-Phase Architecture (Tokenizer+Parser) in place of current single FSM + hardcoded workarounds
- Benchmark comparisons and more testing 
- Using byte streams instead of character streams to significantly decrease internal UTF8 to UTF16 conversion costs

# Background

- This project explores a **streaming ingestion engine** that **parses arbitarily large CSV files** in a **single pass** with **O(1) memory**
- The naive approach requires loading the whole file into memory, leading to inefficient Heap usage
- Hence, this project uses optimised methods such as array lookups, using a reader buffer, lazy materialisation and BitSet operations along with streaming

# Motivation

- Pandas.read_csv() had made me wonder how CSV Parsing works
- With the knowledge of Theory of Computation, I was able to explore how CSV Parsing through Finite State Machine (FSM) works
- This also got me interested in system designing and different methods of testing

# Introduction

This project is a **lightweight Java implementation** of **RFC-4180** style parsing built using a **FSM**. This
explores how a streaming ingestion engine works and what methods are used to tune and increase its flexibility
and efficiency.

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

- **Two-Phase Architecture** (Tokenizer+Parser) in place of current single FSM + hardcoded workarounds
- **Benchmark comparisons** and more testing 
- Using **byte streams** instead of character streams to significantly **decrease internal UTF8 to UTF16 conversion costs**

package RFC4180;

import RFC4180.CSVRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;

import java.io.Reader;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class CSVReader implements Iterator<CSVRecord>, AutoCloseable{
	// For FSM Trace
	private static final String[] states = {"FIELD_START", "UNQUOTED", "QUOTED", "QUOTED_END", "DEAD"};
	private static final String[] classes = {"COMMA", "QUOTE", "CR", "LF", "EOF", "OTHER"};
	private static final String[] actions = {"EMIT_FIELD", "EMIT_RECORD", "NO_OP", "THROW_ERROR", "APPEND"};

	private final Reader reader;	

	private StringBuilder curr_field;
	private List<String> fields;
	private int state;
	private boolean recReady;

	// Lookahead
	private int nextChar;
	private boolean finished;	
	private int buffered = -2;

	// Delay Commit
	private int countTrailSpaces;

	public enum Mode{UNIX, WINDOWS, LENIENT};

	// Configurations
	private Mode mode;
	private char[] delimiters;
	private long low = 0, high = 0; //1-64, 65-128 ASCII bitset storage
	private boolean enableFSMTrace;
	private boolean trimSpaces;

	private static final int DELIMITER_LIMIT = 5;
	private static final int FIELD_START = 0, UNQUOTED = 1,  QUOTED = 2, QUOTED_END = 3, DEAD = 4;
	private static final int DELIMITER = 0, QUOTE = 1, CR = 2, LF = 3, EOF = 4, OTHER = 5;
	private static final int EMIT_FIELD = 0, EMIT_RECORD = 1, NO_OP = 2, THROW_ERROR = 3, APPEND = 4;

	// Transition Function as Lookup Table
	private static final int [][] transition = {
		// FIELD_START
		// DELIMITER,  QUOTE,       CR,         LF,           EOF,         OTHER
		{FIELD_START,  QUOTED,      DEAD,     FIELD_START,    FIELD_START,  UNQUOTED},
		// UNQUOTED
		// DELIMITER,  QUOTE,       CR,         LF,           EOF,         OTHER
		{FIELD_START,  DEAD,        DEAD,     FIELD_START,    FIELD_START,  UNQUOTED},
		// QUOTED
		// DELIMITER,  QUOTE,       CR,         LF,           EOF,         OTHER
		{QUOTED,       QUOTED_END,  QUOTED,   QUOTED,         DEAD,         QUOTED},
		// QUOTED_END
		// DELIMITER,  QUOTE,       CR,         LF,           EOF,         OTHER
		{FIELD_START,  QUOTED,      DEAD,     FIELD_START,    FIELD_START,  DEAD},
		// DEAD state
		// DELIMITER,  QUOTE,       CR,         LF,           EOF,         OTHER
		{DEAD,         DEAD,        DEAD,     DEAD,           DEAD,         DEAD}
	};

	// Action Function as Lookup Table
	private static final int [][] action = {
		// FIELD_START
		// DELIMITER, QUOTE,          CR,           LF,           EOF,        OTHER
		{EMIT_FIELD,  NO_OP,        THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  APPEND},
		// UNQUOTED
		// DELIMITER, QUOTE,          CR,           LF,           EOF,        OTHER
		{EMIT_FIELD,  THROW_ERROR,  THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  APPEND},
		// QUOTED
		// DELIMITER, QUOTE,          CR,           LF,           EOF,        OTHER
		{APPEND,      NO_OP,        APPEND,       APPEND,       THROW_ERROR, APPEND},
		// QUOTED_END
		// DELIMITER, QUOTE,          CR,           LF,           EOF,        OTHER
		{EMIT_FIELD,  APPEND,       THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  THROW_ERROR},
		// DEAD state
		// DELIMITER, QUOTE,          CR,           LF,           EOF,        OTHER
		{THROW_ERROR, THROW_ERROR,  THROW_ERROR,  THROW_ERROR,  THROW_ERROR, THROW_ERROR}
	};

	public static class Builder{
		private Mode mode = Mode.UNIX;

		private char[] delimiters = new char[DELIMITER_LIMIT];
		{
			delimiters[0] = ',';
		}

		private boolean trimSpaces = false;
		private boolean enableFSMTrace = false;

		public Builder enableTrimming(boolean trimSpaces){
			this.trimSpaces = trimSpaces;
			return this;
		}
		public Builder setMode(Mode mode){
			this.mode = mode;
			return this;
		}
		public Builder setDelimiters(char...delimiters){
			if(delimiters.length>DELIMITER_LIMIT){
				throw new IllegalArgumentException("Exceeded delimiter limit of " + DELIMITER_LIMIT);
			}
			this.delimiters = delimiters.clone();
			return this;
		}
		public Builder enableTrace(boolean enableFSMTrace){
			this.enableFSMTrace = enableFSMTrace;
			return this;
		}
	
		public CSVReader build(Reader reader){
			return new CSVReader(reader, mode, delimiters, trimSpaces, enableFSMTrace);
		}
	}

	public static CSVReader defaultReader(Reader reader){ 
		return new CSVReader.Builder().build(reader);
	}
	
	private CSVReader(Reader reader, Mode mode, char[] delimiters, boolean trimSpaces,  boolean enableFSMTrace){
		this.reader = reader;
		this.nextChar = normalisedRead();
		
		this.mode = mode;
		for(int delimiter:delimiters){
			if(delimiter == '\r' || delimiter == '\n'){
				throw new IllegalArgumentException("Delimiter cannot be Line Ending");
			}
			if(delimiter != '\0'){
				bitsetAdd(delimiter); // for O(1) lookup with less space
			}
		}
		this.delimiters = delimiters;
		this.trimSpaces = trimSpaces;
		this.enableFSMTrace = enableFSMTrace;

		this.countTrailSpaces = 0;

		this.recReady = false;
		this.finished = false;
		
		this.curr_field = new StringBuilder(32);
		this.fields = new ArrayList<>();
		this.state = FIELD_START; // Starting state of FSM
	}

	@Override
        public void close(){
                if(reader != null){
                        try{
                                reader.close();
                        }catch(IOException e){
                                throw new UncheckedIOException(e);
                        }
                }
        }	

	private void bitsetAdd(int num){
		if(num < 65){
			low |= (1L << (num-1));
		}else{
			high |= (1L << (num-65));
		}
	}

	private boolean bitsetContains(int num){
		if(num < 65){
			return (low & (1L << (num-1))) != 0;
		}else{
			return (high & (1L << (num-65))) != 0;
		}
	}

	private void getFSMTrace(){
		int ch = inputClass(nextChar);
		String line = "-------------------------------------------------";

		System.out.println(line);

		System.out.println(
			"CURRENTLY READ CHARACTER: " +
			(nextChar == -1 ? "EOF" : 
				(nextChar == '\r' ? "<CR>" : 
					(nextChar == '\n' ? "<LF>" : "'" + (char)nextChar + "'")))
			+ "\nCHARACTER CLASS: " + classes[ch]
		);
		
		System.out.println("\nCURRENT FIELD: [" + curr_field.toString().replace("\r", "<CR>").replace("\n", "<LF>") + "]");
		System.out.println("TOTAL FIELDS: " + fields.size());
		System.out.println("\nFIELDS:");
		if(fields.isEmpty()){
			System.out.println("<EMPTY>");
		}
		for(String field:fields){
			System.out.println("[" + field.replace("\r", "<CR>").replace("\n", "<LF>") + "]");
		}

		System.out.println("\nCURRENT RECORD READY? ["+(recReady || ch == 4)+"]");
		System.out.println(line);
	} 

	private int inputClass(int c){
		if(bitsetContains(c)){
			return DELIMITER;			
		}
                switch(c){
                        case '\"':
                                return QUOTE;
                        case '\r':
                                return CR;
                        case '\n':
                                return LF;
                        case -1:
                                return EOF;
                }

                return OTHER;
        }

	private int normalisedRead(){
		if(buffered != -2){
			int temp = buffered;
			buffered = -2;
			return temp;
		}

		try{
			return reader.read();
		}catch(IOException e){
			throw new UncheckedIOException(e);
		}
	}

	private void perform(int act){
		// FIELD_START = 0, UNQUOTED = 1,  QUOTED = 2, QUOTED_END = 3, DEAD = 4
		// DELIMITER = 0, QUOTE = 1, CR = 2, LF = 3, EOF = 4, OTHER = 5
		// EMIT_FIELD = 0, EMIT_RECORD = 1, NO_OP = 2, THROW_ERROR = 3, APPEND = 4

		switch(act){
			case EMIT_RECORD:
				recReady = true;
			case EMIT_FIELD:
				fields.add(curr_field.toString());	
				curr_field.setLength(0);
				break;
			case NO_OP:
				break;
			case APPEND:
				curr_field.append((char)nextChar);
				break;
			case THROW_ERROR:
				throw new RuntimeException("Invalid CSV Format for Chosen Mode");
		}
	}
	
	private void normaliseEnding(){
		if(mode == Mode.WINDOWS && nextChar == '\r' && state != QUOTED){
			// In Windows Mode, when CR is currently read,
			// throw error when lookahead is not LF,
			// else skip this CR and process lookahead LF

			int lookahead = normalisedRead();

			if(lookahead != '\n'){
			        perform(THROW_ERROR);
			}else{
			        nextChar = lookahead;
			}
		}else if(mode == Mode.LENIENT && nextChar == '\r' && state != QUOTED){
			// buffered is -2 when empty
			// In Lenient Mode, when CR is currently read,
			// if '\r\n' case, skip CR and process lookahead LF
			// else if '\rX', buffer X and set currently read CR to LF
			// On next normalisedRead(), buffered X will be processed
			// All this buffering is because read() is strictly one way

			int lookahead = normalisedRead();

			if(lookahead != '\n'){
			        buffered = lookahead;
			        nextChar = '\n';
			}else{
			        nextChar = lookahead;
			}
		}else if(mode != Mode.UNIX && mode != Mode.WINDOWS && mode != Mode.LENIENT){
			throw new IllegalArgumentException("Incorrect Reader Mode");
		}
	}
	
	private void delayedCommit(int charClass){
		int ch = inputClass(nextChar);
		if(ch == charClass){
			while(countTrailSpaces > 0){
				curr_field.append(' ');
				countTrailSpaces--;
			}
		}else{
			countTrailSpaces = 0;
		}
	}
	
	@Override
	public boolean hasNext(){
		return !finished;
	}

	@Override
	public CSVRecord next() throws NoSuchElementException{
		while(true){
			// In Trim Mode, read but do not process space chars at FIELD_START
			if(trimSpaces && nextChar == ' ' && state == FIELD_START){
				nextChar = normalisedRead();
				continue; 
			}
			// In Trim Mode, read but delay proccessing of trailing space chars due to ambiguity
			if(trimSpaces && nextChar == ' ' && (state == UNQUOTED || state == QUOTED_END)){
				countTrailSpaces++;
				nextChar = normalisedRead();
				continue;
			}
			
			// normalise CR/CRLF to LF cases
			normaliseEnding();

			int ch = inputClass(nextChar);
			int act = action[state][ch];
			
			// Delayed commit before performing action when OTHER char encountered
			delayedCommit(OTHER);
						
			perform(act);

			if(enableFSMTrace) getFSMTrace();

			state = transition[state][ch];                     

			if(recReady){
				recReady = false;
				nextChar = normalisedRead();
				state = FIELD_START;

				// To deal with edge cases of the form "(...)     \r\n"
				if(nextChar == -1){
					finished = true;
				}

				CSVRecord r = new CSVRecord(fields);
				fields.clear();
				return r;
			}

			if(ch == EOF){
				finished = true;
				break;
			}
			
			nextChar = normalisedRead();
		}
		
		// Flush at EOF
		// !fields.empty() guards against completely empty inputs
		if(finished && !fields.isEmpty()){
			CSVRecord r = new CSVRecord(fields);
			fields.clear();
			return r;
		}

		throw new NoSuchElementException();
	} 
}

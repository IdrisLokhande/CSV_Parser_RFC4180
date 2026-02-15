package RFC4180.RFC_CSV;

import RFC4180.RFC_CSV.CSVRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;

import java.io.Reader;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class CSVReader implements Iterator<CSVRecord>, AutoCloseable{
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
	private int buffered = -2; // empty

	// Delay
	private int countTrailSpaces;

	// Configurations
	private boolean onlyLF;
	private boolean enableFSMTrace;
	private boolean trimSpaces;

	private static final int FIELD_START = 0, UNQUOTED = 1,  QUOTED = 2, QUOTED_END = 3, DEAD = 4;
	private static final int COMMA = 0, QUOTE = 1, CR = 2, LF = 3, EOF = 4, OTHER = 5;
	private static final int EMIT_FIELD = 0, EMIT_RECORD = 1, NO_OP = 2, THROW_ERROR = 3, APPEND = 4;

	private static final int [][] transition = {
		// FIELD_START
		// COMMA,      QUOTE,        CR,         LF,           EOF,         OTHER
		{FIELD_START,  QUOTED,      DEAD,     FIELD_START,    FIELD_START,  UNQUOTED},
		// UNQUOTED
		// COMMA,      QUOTE,        CR,         LF,           EOF,         OTHER
		{FIELD_START,  DEAD,        DEAD,     FIELD_START,    FIELD_START,  UNQUOTED},
		// QUOTED
		// COMMA,      QUOTE,        CR,         LF,           EOF,         OTHER
		{QUOTED,       QUOTED_END,  QUOTED,   QUOTED,         DEAD,         QUOTED},
		// QUOTED_END
		// COMMA,      QUOTE,        CR,         LF,           EOF,         OTHER
		{FIELD_START,  QUOTED,      DEAD,     FIELD_START,    FIELD_START,  DEAD},
		// DEAD state
		// COMMA,      QUOTE,        CR,         LF,           EOF,         OTHER
		{DEAD,         DEAD,        DEAD,     DEAD,           DEAD,         DEAD}
	};

	private static final int [][] action = {
		// FIELD_START
		// COMMA,     QUOTE,          CR,           LF,           EOF,        OTHER
		{EMIT_FIELD,  NO_OP,        THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  APPEND},
		// UNQUOTED
		// COMMA,     QUOTE,          CR,           LF,           EOF,        OTHER
		{EMIT_FIELD,  THROW_ERROR,  THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  APPEND},
		// QUOTED
		// COMMA,     QUOTE,          CR,           LF,           EOF,        OTHER
		{APPEND,      NO_OP,        APPEND,       APPEND,       THROW_ERROR, APPEND},
		// QUOTED_END
		// COMMA,     QUOTE,          CR,           LF,           EOF,        OTHER
		{EMIT_FIELD,  APPEND,       THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  THROW_ERROR},
		// DEAD state
		// COMMA,     QUOTE,          CR,           LF,           EOF,        OTHER
		{THROW_ERROR, THROW_ERROR,  THROW_ERROR,  THROW_ERROR,  THROW_ERROR, THROW_ERROR}
	};

	public CSVReader(Reader reader, boolean onlyLF, boolean trimSpaces,  boolean enableFSMTrace) throws IOException{
		this.reader = reader;
		this.nextChar = reader.read();
		
		this.onlyLF = onlyLF;
		this.trimSpaces = trimSpaces;
		this.enableFSMTrace = enableFSMTrace;

		this.countTrailSpaces = 0;

		this.recReady = false;
		this.finished = false;
		
		this.curr_field = new StringBuilder(32);
		this.fields = new ArrayList<>();
		this.state = FIELD_START;
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

	private void getDFATrace(){
		int ch = inputClass(nextChar);
		String head = "------[STATE "+states[state]+"]------";
		System.out.println(head);
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
		for(String field:fields){
			System.out.println("[" + field.replace("\r", "<CR>").replace("\n", "<LF>") + "]");
		}

		System.out.println("\nCURRENT RECORD READY? ["+(recReady || ch == 4)+"]");
		
		for(int i = 0; i<head.length(); i++){
			System.out.print("-");
		}
		
		System.out.print("\n");
	} 

	private int inputClass(int c){
		switch(c){
			case ',':
				return COMMA;
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
		// FIELD_START = 0, UNQUOTED = 1,  QUOTED = 2, QUOTED_END = 3, EXPECT_LF = 4, DEAD = 5, DEAD? = 6
		// COMMA = 0, QUOTE = 1, CR = 2, LF = 3, EOF = 4, OTHER = 5
		// EMIT_FIELD = 0, EMIT_RECORD = 1, NO_OP = 2, THROW_ERROR = 3, APPEND = 4

		switch(act){
			case EMIT_RECORD:
				recReady = true;
			case EMIT_FIELD:
				countTrailSpaces = 0;
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

	@Override
	public boolean hasNext(){
		return !finished;
	}

	@Override
	public CSVRecord next() throws NoSuchElementException{
		if(nextChar == -1 && state == FIELD_START){
			throw new NoSuchElementException();
		}
		while(true){
			if(trimSpaces && nextChar == ' ' && state == FIELD_START){
				nextChar = normalisedRead();
				continue; 
			}
			if(trimSpaces && nextChar == ' ' && (state == UNQUOTED || state == QUOTED_END)){
				countTrailSpaces++;
				nextChar = normalisedRead();
				continue;
			}

			if(!onlyLF && nextChar == '\r' && state != QUOTED){
				// normalise CR or CRLF to LF
				int lookahead = normalisedRead();
				
				if(lookahead != '\n'){
					buffered = lookahead;
				}

				nextChar = '\n';
			}

			int ch = inputClass(nextChar);
			int act = action[state][ch];
			
			if(ch == OTHER){
				while(countTrailSpaces > 0){
					curr_field.append(' ');
					countTrailSpaces--;
				}
			}
						
			perform(act);

			if(enableFSMTrace) getDFATrace();

			state = transition[state][ch];                        

			if(recReady){
				recReady = false;
				nextChar = normalisedRead();
				state = FIELD_START;
				CSVRecord r = new CSVRecord(fields);
				fields = new ArrayList<>();
				return r;
			}
			
			if(ch == EOF){
				break;
			}

			nextChar = normalisedRead();
		}
		
		if(!fields.isEmpty()){
			CSVRecord r = new CSVRecord(fields);
			fields = new ArrayList<>();
			finished = true;
			return r;
		} // flush at EOF

		throw new NoSuchElementException();
	} 
}

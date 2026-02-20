package RFC4180;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;

import java.io.Reader;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class CSVReader implements Iterator<CSVRecord>, AutoCloseable{
	private interface EndingHandler{
                void handle();
        }

	// For FSM Trace
	private static final String[] states = {"FIELD_START", "UNQUOTED", "QUOTED", "QUOTED_END", "DEAD"};
	private static final String[] classes = {"OTHER", "QUOTE", "CR", "LF", "EOF", "DELIMITER"};
	private static final String[] actions = {"EMIT_FIELD", "EMIT_RECORD", "NO_OP", "THROW_ERROR", "APPEND"};

	private final Reader reader;	

	private StringBuilder recordBuffer;
	private int maxRecSizeSeen;
	private int[] fieldLastIndices;
	private int size;
	private boolean firstRecRead;
	private int expectedColumnCount;
	private int state;
	private boolean recReady;

	// Exception Handling
	private int recordNumber;
	private int actualColumnCount;
	private StringBuilder recHistory;

	// Buffering and Lookahead
	private final char[] ioBuff;
        private int ioPos;
	private int limit;
	private int nextChar;	
	private int buffered;
	private boolean finished;

	// Delay Commit
	private int countTrailSpaces;

	public enum Mode{UNIX, WINDOWS, LENIENT};

	// Configurations
	private final Mode mode;
	private final char[] delimiters;
	private long low = 0, high = 0; //1-64, 65-128 ASCII bitset storage for delimiters
	private final boolean enableFSMTrace;
	private final boolean trimSpaces;

	private static final int DELIMITER_LIMIT = 5;
	private static final int IO_LIMIT = 8192; // 8192 bytes
	private static final int FIELD_START = 0, UNQUOTED = 1,  QUOTED = 2, QUOTED_END = 3, DEAD = 4;
	private static final int OTHER = 0, QUOTE = 1, CR = 2, LF = 3, EOF = 4, DELIMITER = 5;
	private static final int EMIT_FIELD = 0, EMIT_RECORD = 1, NO_OP = 2, THROW_ERROR = 3, APPEND = 4;

	private final int[] inputClassTable;

	private final EndingHandler endingHandler;

	// Transition Function as Lookup Table
	private static final int [][] transition = {
		// FIELD_START
		// OTHER,   QUOTE,       CR,         LF,           EOF,          DELIMITER
		{UNQUOTED,  QUOTED,      DEAD,     FIELD_START,    FIELD_START,  FIELD_START},
		// UNQUOTED
		// OTHER,   QUOTE,       CR,         LF,           EOF,          DELIMITER
		{UNQUOTED,  DEAD,        DEAD,     FIELD_START,    FIELD_START,  FIELD_START},
		// QUOTED
		// OTHER,   QUOTE,       CR,         LF,           EOF,          DELIMITER
		{QUOTED,    QUOTED_END,  QUOTED,   QUOTED,         DEAD,         QUOTED},
		// QUOTED_END
		// OTHER,   QUOTE,       CR,         LF,           EOF,          DELIMITER
		{DEAD,      QUOTED,      DEAD,     FIELD_START,    FIELD_START,  FIELD_START},
		// DEAD state
		// OTHER,   QUOTE,       CR,         LF,           EOF,          DELIMITER
		{DEAD,      DEAD,        DEAD,     DEAD,           DEAD,         DEAD}
	};

	// Action Function as Lookup Table
	private static final int [][] action = {
		// FIELD_START
		// OTHER,      QUOTE,          CR,           LF,           EOF,       DELIMITER
		{APPEND,       NO_OP,        THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  EMIT_FIELD},
		// UNQUOTED
		// OTHER,      QUOTE,          CR,           LF,           EOF,       DELIMITER
		{APPEND,       THROW_ERROR,  THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  EMIT_FIELD},
		// QUOTED
		// OTHER,      QUOTE,          CR,           LF,           EOF,       DELIMITER
		{APPEND,       NO_OP,        APPEND,       APPEND,       THROW_ERROR, APPEND},
		// QUOTED_END
		// OTHER,      QUOTE,          CR,           LF,           EOF,       DELIMITER
		{THROW_ERROR,  APPEND,       THROW_ERROR,  EMIT_RECORD,  EMIT_FIELD,  EMIT_FIELD},
		// DEAD state
		// OTHER,      QUOTE,          CR,           LF,           EOF,       DELIMITER
		{THROW_ERROR,  THROW_ERROR,  THROW_ERROR,  THROW_ERROR,  THROW_ERROR, THROW_ERROR}
	};

	public static class Builder{
		// -----------------------------------------------------
		// Default configurable values
		private Mode mode = Mode.UNIX;

		private char[] delimiters = new char[DELIMITER_LIMIT];
		{
			delimiters[0] = ',';
		}

		private boolean trimSpaces = false;
		private boolean enableFSMTrace = false;
		// -----------------------------------------------------
		
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
		this.mode = mode;
		
		// better to use switch because endingHandler is final but not intialised yet
		switch(mode){
			case WINDOWS:
				endingHandler = this::windowsEnding;
				break;
			case LENIENT:
				endingHandler = this::lenientEnding;
				break;
			case UNIX:
				endingHandler = () -> {}; // nothing
				break;
			default:
				throw new IllegalArgumentException("Invalid Reader Mode");
		}

		

		this.delimiters = delimiters;
		
		for(int delimiter:delimiters){
			if(delimiter == '\r' || delimiter == '\n'){
				throw new IllegalArgumentException("Delimiter cannot be Line Ending");
			}
			if(delimiter != '\0'){
				bitsetAdd(delimiter); // for O(1) lookup with less space
			}
		}

		this.inputClassTable = new int[128]; // already filled with OTHER
		inputClassTable['\"'] = QUOTE;
		inputClassTable['\r'] = CR;
		inputClassTable['\n'] = LF;
		for(char d:delimiters){
			inputClassTable[d] = DELIMITER;
		}

		this.trimSpaces = trimSpaces;
		this.enableFSMTrace = enableFSMTrace;

		this.countTrailSpaces = 0;
		
		this.recReady = false;
		this.finished = false;
		this.firstRecRead = false;

		this.ioBuff = new char[IO_LIMIT];
		this.ioPos = 0;
		this.limit = 0;
		this.buffered = -2; // empty
		this.nextChar = bufferedRead();

		this.maxRecSizeSeen = 64;
		this.recordBuffer = new StringBuilder(maxRecSizeSeen);
		this.recHistory = new StringBuilder(64);
		this.fieldLastIndices = new int[128]; // 128 cols initially
		this.size = 0;
		this.expectedColumnCount = 0;
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
		int ch = (nextChar == -1)? EOF : (nextChar<128)? inputClassTable[nextChar] : OTHER;
		String line = "-------------------------------------------------";
		System.out.println(line);

		System.out.println(
			"STATE: " + states[state] +
			"\nCURRENTLY READ CHARACTER: " +
			(nextChar == -1 ? "EOF" : 
				(nextChar == '\r' ? "<CR>" : 
					(nextChar == '\n' ? "<LF>" : "'" + (char)nextChar + "'")))
			+ "\nCHARACTER CLASS: " + classes[ch]
		);
		
		System.out.println("\nCURRENT RECORD READY? ["+(recReady || ch == EOF)+"]");
		System.out.println(line);
	} 

	private int bufferedRead(){
		if(buffered != -2){
			int temp = buffered;
			buffered = -2;
			return temp;
		}

		if(ioPos >= limit){
			try{
				limit = reader.read(ioBuff);
				ioPos = 0;
				
				if(limit == -1){
					return -1;
				}	
			}catch(IOException e){
				throw new UncheckedIOException(e);
			}
		}

		return ioBuff[ioPos++];
	}

	private void perform(int act){
		// FIELD_START = 0, UNQUOTED = 1,  QUOTED = 2, QUOTED_END = 3, DEAD = 4
		// DELIMITER = 0, QUOTE = 1, CR = 2, LF = 3, EOF = 4, OTHER = 5
		// EMIT_FIELD = 0, EMIT_RECORD = 1, NO_OP = 2, THROW_ERROR = 3, APPEND = 4

		switch(act){
			case EMIT_RECORD:
				recReady = true;
			case EMIT_FIELD:
				if(size == fieldLastIndices.length){
					int capacity = size + (size >> 1);
					int[] newArr = new int[capacity];

					System.arraycopy(fieldLastIndices, 0, newArr, 0, size);

					fieldLastIndices = newArr;
				}
				actualColumnCount++;
				if(firstRecRead && actualColumnCount > expectedColumnCount){
                                	throw new CSVFormatException(recordNumber+1, expectedColumnCount, actualColumnCount, recHistory.toString());
                        	} 
				fieldLastIndices[size] = recordBuffer.length();
				size++;
				break;
			case NO_OP:
				break;
			case APPEND:
				recordBuffer.append((char)nextChar);
				break;
			case THROW_ERROR:
				throw new CSVFormatException(recordNumber+1, actualColumnCount+1, mode.name(), trimSpaces);
		}
	}
	
	private void windowsEnding(){
		if(nextChar == '\r' && state != QUOTED){
                        // In Windows Mode, when CR is currently read,
                        // throw error when lookahead is not LF,
                        // else skip this CR and process lookahead LF

                        int lookahead = bufferedRead();

                        if(lookahead != '\n'){
                                perform(THROW_ERROR);
                        }else{
                                nextChar = lookahead;
                        }
                }else if(nextChar == '\n' && state != QUOTED){
                        perform(THROW_ERROR);
                }
	}

	private void lenientEnding(){
		if(nextChar == '\r' && state != QUOTED){
                        // buffered is -2 when empty
                        // In Lenient Mode, when CR is currently read,
                        // if '\r\n' case, skip CR and process lookahead LF
                        // else if '\rX', buffer X and set currently read CR to LF
                        // On next bufferedRead(), buffered X will be processed.
                        // All this buffering is because read() is strictly one way
                        // and read() consumes character

                        int lookahead = bufferedRead();

                        if(lookahead != '\n'){
                                buffered = lookahead;
                                nextChar = '\n';
                        }else{
                                nextChar = lookahead;
                        }
                }
	}
	
	private void delayedCommit(int ch){
		if(ch == OTHER){
			while(countTrailSpaces > 0){
				recordBuffer.append(' ');
				countTrailSpaces--;
			}
		}else{
			countTrailSpaces = 0;
		}
	}
	
	@Override
	public boolean hasNext(){
		return !finished || recReady;
	}

	@Override
	public CSVRecord next() throws NoSuchElementException{
		while(true){
			// In Trim Mode, read but do not process space chars at FIELD_START
			if(trimSpaces && nextChar == ' ' && state == FIELD_START){
				nextChar = bufferedRead();
				continue; 
			}
			// In Trim Mode, read but delay proccessing of trailing space chars due to ambiguity
			if(trimSpaces && nextChar == ' ' && (state == UNQUOTED || state == QUOTED_END)){
				countTrailSpaces++;
				nextChar = bufferedRead();
				continue;
			}
			
			// normalise CR/CRLF to LF
			endingHandler.handle();

			if(nextChar != -1) recHistory.append((char)nextChar);

			int ch = (nextChar == -1)? EOF : (nextChar<128)? inputClassTable[nextChar] : OTHER;
			int act = action[state][ch];
			
			// Delayed commit before performing action when OTHER char encountered
			delayedCommit(ch);
			
			perform(act);

			if(enableFSMTrace) getFSMTrace();

			state = transition[state][ch];                     

			if(recReady){
				int len = recordBuffer.length();
				maxRecSizeSeen = (maxRecSizeSeen > len)? maxRecSizeSeen:len;
				if(!firstRecRead){
					firstRecRead = true;				
					expectedColumnCount = size;
					int[] newArr = new int[size];

					System.arraycopy(fieldLastIndices, 0, newArr, 0, size);

					fieldLastIndices = newArr;
				}

				if(actualColumnCount < expectedColumnCount){
					String recHistoryString = recHistory.toString().replace("\r", "<CR>").replace("\n", "<LF>");
                                	throw new CSVFormatException(recordNumber+1, expectedColumnCount, actualColumnCount, recHistoryString);
                        	} 

				recReady = false;
				nextChar = bufferedRead();
				state = FIELD_START;

				// To deal with edge cases of the form "(...)     \r\n"
				if(nextChar == -1){
					finished = true;
					break;
				}

				String record = recordBuffer.toString();
				
				CSVRecord r = new CSVRecord(record, fieldLastIndices, expectedColumnCount);
				recordBuffer.setLength(0);
				recordBuffer.ensureCapacity(maxRecSizeSeen);
				recHistory.setLength(0);

				size = 0;
				actualColumnCount = 0;

				recordNumber++;

				return r;
			}

			if(ch == EOF){
				finished = true;
				break;
			}
			
			nextChar = bufferedRead();
		}

		// Flush at EOF
		// !recordBuffer.isEmpty() guards against completely empty inputs
		if(finished && !recordBuffer.isEmpty()){
			String record = recordBuffer.toString();

                        CSVRecord r = new CSVRecord(record, fieldLastIndices, expectedColumnCount);
                        recordBuffer.setLength(0);
			recordNumber++;

			return r;
		}

		throw new NoSuchElementException();
	} 
}

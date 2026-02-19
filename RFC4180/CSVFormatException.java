package RFC4180;

public final class CSVFormatException extends RuntimeException{
	private final int recordNumber;
	private final int expectedColumns;
	private final int actualColumns;
	private final String rawRecord;
	private final String mode;
	private final boolean enabledTrim;

	private CSVFormatException(String message, int recordNumber, int expectedColumns, int actualColumns, String rawRecord, String mode, boolean enabledTrim){
		super(message);
		
		this.recordNumber = recordNumber;
		this.expectedColumns = expectedColumns;
		this.actualColumns = actualColumns;
		this.rawRecord = rawRecord;
		this.mode = mode;
		this.enabledTrim = enabledTrim;
	}

	public CSVFormatException(String mode, boolean enabledTrim){
		this("mode " + mode + (enabledTrim? "":" without trimming ") + " incompatible with current CSV Format",
			-1, -1, -1, null, mode, enabledTrim
		);
	}

	public CSVFormatException(int recordNumber, int expectedColumns, int actualColumns, String rawRecord){
		this("expected " + expectedColumns + " columns but found " + actualColumns + "\n\tin CSV File (Line " + recordNumber + ")",
			recordNumber, expectedColumns, actualColumns, rawRecord, null, false
		);
	}

	public int recordNumber(){
		return recordNumber;	
	}

	public int getExpectedColumnCount(){
		return expectedColumns;
	}

	public int getActualColumnCount(){
		return actualColumns;
	}

	public String getRawRecord(){
		return rawRecord;
	}

	public String getMode(){
		return mode;
	}

	public boolean isTrimEnabled(){
		return enabledTrim;
	}
} 

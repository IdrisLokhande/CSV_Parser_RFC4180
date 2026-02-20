package RFC4180;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public final class CSVRecord{
	private String record;
	private int[] fieldLastIndices;
	private int expectedColumnCount;

        CSVRecord(String record, int[] fieldLastIndices, int expectedColumnCount){
		this.record = record;
		this.fieldLastIndices = fieldLastIndices.clone();
		this.expectedColumnCount = expectedColumnCount;
        }

        public int getRecordSize(){
                return expectedColumnCount;
        }

        public String getField(int fIndex){
		if(fIndex == 0){
			return record.substring(0, fieldLastIndices[fIndex]);
		}
                return record.substring(fieldLastIndices[fIndex-1], fieldLastIndices[fIndex]);
        }
}

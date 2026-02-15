package RFC4180.RFC_CSV;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public final class CSVRecord{
        private final List<String> fields;

        CSVRecord(List<String> fields){
                List<String> copy = new ArrayList<>(fields);
                this.fields = Collections.unmodifiableList(copy);
        }

        public int getRecordSize(){
                return fields.size();
        }

        public String getField(int fIndex){
                return fields.get(fIndex);
        }
}

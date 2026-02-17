package Testing;

import RFC4180.CSVReader;
import RFC4180.CSVRecord;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

import java.nio.charset.StandardCharsets;

public class Main{
	public static void main(String[]args){
		String CSV1 = "abc\n\"def\"\nghi\n\"\"\"jkl\"\"\""; 
		String filePath = "Datasets/TestCSV - Sheet1.csv";
		
		try(CSVReader reader = new CSVReader.Builder()
					.enableTrimming(true)
					.setMode(CSVReader.Mode.WINDOWS)
					.build(new InputStreamReader(
							new FileInputStream(filePath), StandardCharsets.UTF_8
					))
		){
			System.out.println("Records:");
			int pos = 0;
			int j = 0, LIM = 20;
			while(j<LIM && reader.hasNext()){
				pos++;
				j++;
				CSVRecord rec = reader.next();

				System.out.println("\nRECORD: " + pos);

				for(int i = 0; i<rec.getRecordSize(); i++){
					System.out.print("[" + rec.getField(i).replace("\r", "<CR>").replace("\n", "<LF>") + "]");
				}
				
				System.out.println("");
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}

package Testing;

import java.util.Random;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;

class CSVFuzzer{
	// tune this as you wish
	// mainly used for testing custom exception throws, or soaking parser with fuzzy consistent data
	
	private static final String[] TOKENS = {
		"a", "b", "c", "d", "e", "f", "g", "h",
		";", " ", ".", "\t"
	};

	private static final String DELIMITER = ",";
	private static final String LINE_ENDING = "\n";

	public static String randomRow(Random r){
		int cols = 30;
		StringBuilder builder = new StringBuilder();
		for(int f = 0; f<cols; f++){
			boolean quoted = r.nextBoolean();
			if(quoted) builder.append("\"");

			int flen = r.nextInt(10)+1;

			for(int i = 0; i<flen; i++){
				builder.append(TOKENS[r.nextInt(TOKENS.length)]);
			}

			if(quoted) builder.append("\"");
			if(f<cols-1) builder.append(DELIMITER);
		}	

		builder.append(LINE_ENDING);

		return builder.toString();
	}
}

public class CSVFuzzyGen{
	private static final long target = 500L * 1024 * 1024;
	public static void main(String[]args){
		try(BufferedWriter w = new BufferedWriter(new FileWriter("Datasets/cleanConsistentFuzz500MB.csv"))){
			// filepath is relative to YOUR current working directory
			Random r = new Random();

			int cols = 30;
			int rows = 1000000;

			for(int i = 0; i<rows; i++){
				for(int j = 0; j<cols; j++){
					w.write(CSVFuzzer.randomRow(r));
				}
				if(new java.io.File("Datasets/cleanConsistentFuzz500MB.csv").length() >= target) break;
			}
		}catch(IOException e){
			throw new UncheckedIOException(e);
		}
	}
}

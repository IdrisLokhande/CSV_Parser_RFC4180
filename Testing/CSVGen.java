package Testing;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;

public class CSVGen{
	private static final String DELIMITER = ",";
	private static final String LINE_ENDING = "\n";

	private static long target = 100L*1024*1024;

	public static void main(String[]args){
		try(BufferedWriter w = new BufferedWriter(new FileWriter("Datasets/test100MB.csv"))){
			// filepath is relative to YOUR current working directory
			int rows = 10000000;
			int cols = 20;

			for(int i = 0; i<rows; i++){
				for(int j = 0; j<cols; j++){
					w.write("data " + j);
					if(j < cols-1) w.write(DELIMITER);
				}

				if(new java.io.File("Datasets/test100MB.csv").length() >= target) break;
				
				w.write(LINE_ENDING);
			}
		}catch(IOException e){
			throw new UncheckedIOException(e);
		}
	}
}

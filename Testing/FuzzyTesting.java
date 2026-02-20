package Testing;

import RFC4180.CSVReader;
import java.io.FileReader;

public class FuzzyTesting{
	private static int MARK = 200000;
	public static void main(String[]args){
		Runtime rt = Runtime.getRuntime();

		// Datasets to be generated on your local		
		String[] datasets = {"Datasets/fuzz100MB.csv", "Datasets/cleanButInconsistentFuzz500MB.csv", "Datasets/cleanConsistentFuzz500MB.csv"};

		for(String dataset:datasets){
			System.out.println(dataset);
			try(CSVReader reader = new CSVReader.Builder()
						.enableTrimming(true)
						.setMode(CSVReader.Mode.UNIX)
						.build(new FileReader(dataset))){
				long start = System.nanoTime();

				int count = 0;

				while(reader.hasNext()){
					reader.next();
					count++;

					if(count%MARK == 0){
						System.gc();
						long used = rt.totalMemory() - rt.freeMemory();
						
						System.out.println("Records Parsed: " + count + ", Heap Used: " + used/1024 + " KB");
					}
				}
			
				System.gc();
                                long used = rt.totalMemory() - rt.freeMemory();
		
				long end = System.nanoTime();

				double elapsedSeconds = (end-start)/1_000_000_000.00;
				
                                System.out.println("Records Parsed: " + count + ", Heap Used: " + used/1024 + " KB");
				
				System.out.println("\nElapsed Time: " + elapsedSeconds + " seconds");
				System.out.println("Done. Total Records: " + count);
			}catch(Exception e){
				e.printStackTrace();
			}
			System.out.println("");
		}
	}
}

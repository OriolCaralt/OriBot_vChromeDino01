import java.awt.AWTException;
import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.lang.StrictMath;

public class ChromeDinoBot{

	private Point dinoPosition, gameOverTextPosition, rightMarginOffset;
	private Color dinoTextAndObstaclesColor = new Color(83, 83, 83);
	private int dinoSize = 25 /*TODO ADJUST*/, obstacleSize, distance, obstaclesJumped, maxNumCreatures = 32 /*Minimum due to the evolution strategy is 30*/;
	private long pseudoSpeed;
	private Robot robot;
	private double generation[][];
	private Rectangle captureRect;
	private boolean isDataRealQ = false;
	
	public static void main(String[] args) {
		new ChromeDinoBot();
	}
	public ChromeDinoBot() {
		
	    try {
			robot = new Robot();
		} catch (AWTException e1) {
			e1.printStackTrace();
		}   
		
		System.out.println("Let the game fail and hold that screen, then put the mouse on the Dino's body at the arm high and press Enter/Intro while the focus is on the cmd");
		Scanner keyboard = new Scanner(System.in);
		keyboard.nextLine();
		
		dinoPosition = MouseInfo.getPointerInfo().getLocation();
		
		System.out.println("Put the mouse on the right edge of the game and press Enter/Intro");
		keyboard.nextLine();
		rightMarginOffset = MouseInfo.getPointerInfo().getLocation();		
		
		System.out.println("Put the mouse on the M of the Game Over text press Enter/Intro, then chage the focus to the browser");
		keyboard.nextLine();
		keyboard.close();
		gameOverTextPosition = MouseInfo.getPointerInfo().getLocation();
		
 		generation = new double[maxNumCreatures][5];
 		
 		captureRect = new Rectangle(dinoPosition.x+25, dinoPosition.y, rightMarginOffset.x-dinoPosition.x-25, 1); // TODO +25? Sure?
		
		obtainFirstGeneration();
	
		startEvolving();
	}
	private void obtainFirstGeneration() {
		// Read from file, if file is not there create form scratch	
 		if(!loadGenerationFromFile()){
 			System.out.println("Create a generation from scratch");
			for(int i=0; i < maxNumCreatures; i++){
				generation[i][0] = Math.random(); // speedWeight
				generation[i][1] = Math.random(); // distanceWeight
				generation[i][2] = Math.random(); // sizeWeight
				generation[i][3] = Math.random(); // biasWeight, TODO Why do we need Bias? Also, could we use more features like distance/speed, (distance+size)/speed, etc.?
				generation[i][4] = 0; // Fitness = 0 to start
			}		
 		}
	}
	private boolean loadGenerationFromFile() {
		String csvFile = "./initialGeneration.csv";
		BufferedReader br = null;
		String line = "";
		String cvsSplitBy = ",";
		int creaturesRead = 0;

		try {

			br = new BufferedReader(new FileReader(csvFile));
			while ((line = br.readLine()) != null){

			        // use comma as separator
				String[] creature = line.split(cvsSplitBy);
				
				generation[creaturesRead][0] = Double.parseDouble(creature[0]); // speedWeight
				generation[creaturesRead][1] = Double.parseDouble(creature[1]); // distanceWeight
				generation[creaturesRead][2] = Double.parseDouble(creature[2]); // sizeWeight
				generation[creaturesRead][3] = Double.parseDouble(creature[3]); // biasWeight
				generation[creaturesRead][4] = Double.parseDouble(creature[4]); // Fitness // Dosen't matter anyway, we will test again
				
				creaturesRead++;
			}
			
			for(; creaturesRead < maxNumCreatures; creaturesRead++){
				generation[creaturesRead][0] = Math.random(); // speedWeight
				generation[creaturesRead][1] = Math.random(); // distanceWeight
				generation[creaturesRead][2] = Math.random(); // sizeWeight
				generation[creaturesRead][3] = Math.random(); // biasWeight
				generation[creaturesRead][4] = 0; // Fitness = 0 to start
			}
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return true;
	}
	private void startEvolving() {
		// Test current generation, once tested apply "evolution" to obtain a new generation, save it on a file and start all over		
		int generationIndex = 1;
		while(true)
		{	
			System.out.println("Generation:" + generationIndex++);
			for(int creatureIndex = 0; creatureIndex < maxNumCreatures; creatureIndex++)
			{
				testCreature(creatureIndex);
				System.out.print(generation[creatureIndex][4] + ",");
			}
			System.out.println();
			evolve();
			saveGeneration();
		}		
	}
	private void saveGeneration() {
		try{			
			FileWriter writer = new FileWriter("./" + System.currentTimeMillis() + "_Generation.csv");          
	        for(int creatureIndex = 0; creatureIndex < maxNumCreatures; creatureIndex++){
	        	writer.append(String.valueOf(generation[creatureIndex][0]));
	        	writer.append(',');
	        	writer.append(String.valueOf(generation[creatureIndex][1]));
	        	writer.append(',');
	        	writer.append(String.valueOf(generation[creatureIndex][2]));
	        	writer.append(',');
	        	writer.append(String.valueOf(generation[creatureIndex][3]));
	        	writer.append(',');
	        	writer.append(String.valueOf(generation[creatureIndex][4]));
	            
	        	writer.append('\n');
	            writer.flush();
	        }
	        
	        writer.close();
	    }        
	    catch(Exception e){
	      e.printStackTrace();
	    }
	}
	private void evolve() {
		List<Integer> bestCreaturesIndex = obtainBestCreatures(); // Min 2 Max 8
		double newGeneration[][] = new double[maxNumCreatures][5];
			
		// Add the best creatures unmodified to the new generation (2-8)
		// Add the best creatures with 100% random mutations to the new generation (2-8)
		for(int creatureIndex = 0; creatureIndex < bestCreaturesIndex.size(); creatureIndex++)
			for(int featureIndex = 0; featureIndex < 4; featureIndex++){
				newGeneration[creatureIndex][featureIndex] = generation[bestCreaturesIndex.get(creatureIndex)][featureIndex];
				newGeneration[bestCreaturesIndex.size() + creatureIndex][featureIndex] = Math.random() + generation[bestCreaturesIndex.get(creatureIndex)][featureIndex];
			}
		
		// Add a full cross over between the best 2 creatures. 2 creatures with 4 genomes each => 16 creatures (but we already have the originals so total 14 new)
		for(int i = 1; i <= 14; i++)
			for(int featuresIndex = 0; featuresIndex < 4; featuresIndex++)
				newGeneration[(bestCreaturesIndex.size()*2)+i-1][featuresIndex] = generation[bestCreaturesIndex.get((i&(byte)Math.pow(2, featuresIndex))==0?0:1)][featuresIndex];
		
		// Complete until maxNumCreatures with 100% random cross over of the best creatures plus randomness (maxNumCreatures minus 18-30)
		for(int creatureIndex = (bestCreaturesIndex.size()*2)+14; creatureIndex < maxNumCreatures; creatureIndex++)
			for(int featureIndex = 0; featureIndex < 4; featureIndex++)
				newGeneration[creatureIndex][featureIndex] = Math.random() + generation[ThreadLocalRandom.current().nextInt(0,bestCreaturesIndex.size())][featureIndex];
		
		generation = newGeneration;
	}
	private List<Integer> obtainBestCreatures() {	
		Map<Integer, Integer> generationsMap = new TreeMap<Integer, Integer>();
		int fitnessSum = 0;
		double fitnessMedian;
		List<Integer> bestCreatures = new ArrayList<Integer>(); // List of the best (highest fitness) creatures of the generation
		
		// First we order the creatures by the inverse of fitness (-fitness) while adding the values to calculate the median(?)
		for(int i=0; i < maxNumCreatures; i++){
			generationsMap.put(Integer.valueOf((Double.valueOf(-generation[i][4])).intValue()), i); // TODO cal fer tot aixo???		
			fitnessSum += generation[i][4];
		}
		
		fitnessMedian = fitnessSum/maxNumCreatures; // Calculating the median
		
		// Obtaining the "best" creatures. Min 2, max 8
		Set set = generationsMap.entrySet();
		Iterator iterator = set.iterator();
		
		int creaturesUsed = 0;
		boolean thresholdReached = false;
		
		//System.out.println(iterator.hasNext());
		
	     while(iterator.hasNext() && creaturesUsed <= 8 && !thresholdReached){
	         Entry<Integer, Integer> mentry = (Entry<Integer, Integer>) iterator.next(); // TODO esto no me mola...
	         
	         if(creaturesUsed < 2)
	        	 bestCreatures.add(mentry.getValue());
	         else
	         {
	        	 if(-mentry.getKey() > fitnessMedian)// Remember we used the inverse of fitness to order
	        		 bestCreatures.add(mentry.getValue());
	        	 else
	        		 thresholdReached = true;
	         }
	         
	         creaturesUsed++;
	      }		
	     return bestCreatures;
	}
	private void testCreature(int creatureIndex) {

		// The fist thing we do is pressing 'space', assuming the game is stopped since the last creature failed, until the "Game Over" text goes off
		while(robot.getPixelColor(gameOverTextPosition.x, gameOverTextPosition.y).equals(dinoTextAndObstaclesColor))
		{
			robot.keyPress(KeyEvent.VK_SPACE);
	        robot.keyRelease(KeyEvent.VK_SPACE);
		}
		
		obstacleSize = 0;
		distance = rightMarginOffset.x-dinoPosition.x;
		obstaclesJumped = 0;		
		pseudoSpeed = 0;
		
		Thread updateInputsAndFitnessThread = new Thread() {
			public void run() {
				asyncUpdateInputsAndJumps();
			}
		};
		
		updateInputsAndFitnessThread.start();
		
		// While the "Game Over" text is not in place we keep testing the current Creature
		while(!robot.getPixelColor(gameOverTextPosition.x, gameOverTextPosition.y).equals(dinoTextAndObstaclesColor)) // TODO create a thread to check this?
		{			
			// If data is real and we are not on the air... TODO
			if(isDataRealQ){
				// Using the inputs we calculate the output 
				if(((pseudoSpeed * generation[creatureIndex][0] + 
						distance * generation[creatureIndex][1] + 
						obstacleSize * generation[creatureIndex][2] + 
						generation[creatureIndex][3])) < 50) // Perceptron ?? TODO!!! TODO Maybe we should "normalize" the inputs...(?)
				{
					//System.out.print(" A: Jump!");
					robot.keyPress(KeyEvent.VK_SPACE);
					// TODO, if we jump we should let some time until we check again since we are on the air
				}	/*
				else
					System.out.print(" A: NO Jump!");
				/*
				System.out.print(" D: " + distance);		
				System.out.print(" S: " + pseudoSpeed);
				System.out.print(" W: " + obstacleSize);
				System.out.println(" J: " + obstaclesJumped);
				*/
			}
			
			// Some delay
			try {
				Thread.sleep(50); // TODO ADJUST if needed
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		
		generation[creatureIndex][4] = obstaclesJumped; // Updating fitness		
		updateInputsAndFitnessThread.stop(); // Stopping checking's TODO sort the deprecated situation
	}
	void asyncUpdateInputsAndJumps() {
		
		BufferedImage image;
		
		while(true){			
			image = robot.createScreenCapture(captureRect);		
			
			int localDistance = 0;
			Color col= new Color(image.getRGB(localDistance, 0), true);
			
			// DISTANCE to first obstacle form the left
			while(localDistance < image.getWidth()&& !col.equals(dinoTextAndObstaclesColor)){
				col= new Color(image.getRGB(localDistance, 0), true);
				localDistance++;
			}
		
			// To be sure we have "real" data we wait till the obstacle is full on screen to calculate speed, size and obstacles jumped
			if(localDistance < 400 && localDistance > 10){// TODO ADJUST if needed
			
				// Count obstacles jumped
				if(localDistance>distance){ // New obstacle
					obstaclesJumped++;
				}
				else
					pseudoSpeed = distance - localDistance; // Assuming every lap take similar time the key is the distance difference
				
				distance = localDistance;				
				
				// SIZE of the first form left obstacle 
				//TODO, si es el mateix obstacle no cal recalcular, nomes reusar!!!
				boolean doWeHaveWidthQ = false;
				int counter = 0;
				int displacement = 0;
				while(localDistance + displacement < image.getWidth() && !doWeHaveWidthQ){
					col= new Color(image.getRGB(localDistance + displacement, 0), true);
					if(col.equals(dinoTextAndObstaclesColor))
						counter = 0;
					else
						counter++;
					if(counter >= dinoSize)
						doWeHaveWidthQ = true;
					else
						displacement++;
				}
			
				obstacleSize = displacement;
				/*
				System.out.print(" D: " + distance);		
				System.out.print(" S: " + pseudoSpeed);
				System.out.print(" W: " + obstacleSize);
				System.out.println(" J: " + obstaclesJumped);
				*/
				isDataRealQ = true;
			}
			else
				isDataRealQ = false;
			
			// Some delay
			try {
				Thread.sleep(50); // TODO adjust if needed, maybe a function over the speed
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
	}	
}

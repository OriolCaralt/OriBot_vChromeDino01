/*
 * First approach to develop an evolutive learning algorithm to play an easy version of Chrome's off-line game
 * 
 * by Oriol Caralt
 * 
 * */

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

public class ChromeDinoBot{

	private Point dinoPosition, gameOverTextPosition, rightMarginOffset;
	private Color dinoTextAndObstaclesColor = new Color(83, 83, 83);
	private int dinoSize = 25, obstacleSize, distance, obstaclesJumped, maxNumCreatures = 32;
	private long pseudoSpeed;
	private Robot robot;
	private double generation[][];
	private Rectangle captureRect;
	private boolean isDataRealQ = false;
	private int samplingRate = 50; // If modified the speed function may need to be updated too
	
	public static void main(String[] args) {
		new ChromeDinoBot();
	}
	public ChromeDinoBot() {
		
	    try {
			robot = new Robot();
		} catch (AWTException e1) {
			e1.printStackTrace();
		}   
		
		System.out.println("Let the game fail and hold that screen, then change the focus to the cmd, put the mouse on the midle of Dino's body at the arm height and press Enter/Intro");
		Scanner keyboard = new Scanner(System.in);
		keyboard.nextLine();		
		dinoPosition = MouseInfo.getPointerInfo().getLocation();
		
		System.out.println("Keep the focus on the cmd, put the mouse on the right edge of the game and press Enter/Intro");
		keyboard.nextLine();
		rightMarginOffset = MouseInfo.getPointerInfo().getLocation();		
		
		System.out.println("Put the mouse on the 'M' of the 'Game Over' text(be sure the mouse is on a 'colored' pixel), press Enter/Intro, then chage the focus to the browser");
		keyboard.nextLine();
		keyboard.close();
		gameOverTextPosition = MouseInfo.getPointerInfo().getLocation();
		
 		generation = new double[maxNumCreatures][5];
 		
 		captureRect = new Rectangle(dinoPosition.x+(dinoSize/2), dinoPosition.y, rightMarginOffset.x-dinoPosition.x-(dinoSize/2), 1);
		
		obtainFirstGeneration();	
		startEvolving();
	}
	private void obtainFirstGeneration() {
		// Read from file, if file is not there create form scratch	
 		if(!loadGenerationFromFile()){
 			System.out.println("Create a generation from scratch");
			for(int i=0; i < maxNumCreatures; i++){ // Random weights from 0.5 to -0.5 for each feature
				// We could use more features like distance/speed, (distance+size)/speed, etc. but so far...
				generation[i][0] = Math.random()-0.5; // speedWeight
				generation[i][1] = Math.random()-0.5; // distanceWeight
				generation[i][2] = Math.random()-0.5; // sizeWeight
				generation[i][3] = Math.random()-0.5; // biasWeight
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

				String[] creature = line.split(cvsSplitBy);
				
				generation[creaturesRead][0] = Double.parseDouble(creature[0]); // speedWeight
				generation[creaturesRead][1] = Double.parseDouble(creature[1]); // distanceWeight
				generation[creaturesRead][2] = Double.parseDouble(creature[2]); // sizeWeight
				generation[creaturesRead][3] = Double.parseDouble(creature[3]); // biasWeight
				generation[creaturesRead++][4] = Double.parseDouble(creature[4]); // Fitness,  the value dosen't matter, we will test it again anyway
			}
			
			if(creaturesRead < maxNumCreatures)
				return false;
		
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
		// Test current generation, once tested evolve it to obtain a new generation, save it on a file and start all over		
		int generationIndex = 1;
		while(true)
		{
			int creatureIndex = 0;
			System.out.println("Generation:" + generationIndex++);
			for(; creatureIndex < maxNumCreatures; creatureIndex++)
			{
				testCreature(creatureIndex);
				System.out.print(((Double)generation[creatureIndex][4]).intValue() + ",");
			}
			System.out.println();
			saveGeneration();
			evolve();			
		}		
	}
	private void saveGeneration() {
		try{			
			FileWriter writer = new FileWriter("./" + System.currentTimeMillis() + "_Generation.csv");          
	        for(int creatureIndex = 0; creatureIndex < maxNumCreatures; creatureIndex++){
	        	for(int featureIndex = 0; featureIndex < 5; featureIndex++)
		        	writer.append(String.valueOf(generation[creatureIndex][featureIndex]) + ',');

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
		List<Integer> bestCreaturesIndex = obtainBestCreatures();
		double newGeneration[][] = new double[maxNumCreatures][5];
		
		System.out.print("B:");
		for(int i =0; i<bestCreaturesIndex.size();i++)
			System.out.print(bestCreaturesIndex.get(i) + ",");
		System.out.println();
		
		// Clone + mutation clone
		// Add the best creatures unmodified to the new generation and add the best creatures with 100% random mutations to the new generation
		for(int creatureIndex = 0; creatureIndex < bestCreaturesIndex.size(); creatureIndex++)
			for(int featureIndex = 0; featureIndex < 4; featureIndex++){
				newGeneration[creatureIndex][featureIndex] = generation[bestCreaturesIndex.get(creatureIndex)][featureIndex];
				newGeneration[bestCreaturesIndex.size() + creatureIndex][featureIndex] = (Math.random()-0.5) + generation[bestCreaturesIndex.get(creatureIndex)][featureIndex];
			}
		
		// Full cross over with random mutations
		// Add a full cross over between the best 2 creatures. So 2 creatures with 4 genes each => 16 creatures (but we already have the originals so total 14 new)
		for(int i = 1; i <= 14; i++)
			for(int featuresIndex = 0; featuresIndex < 4; featuresIndex++)
				newGeneration[(bestCreaturesIndex.size()*2)+i-1][featuresIndex] = 
					(Math.random()>0.5?0:Math.random()-0.5) + generation[bestCreaturesIndex.get((i&(byte)Math.pow(2, featuresIndex))==0?0:1)][featuresIndex];
		
		// Random cross over with mutations
		// Complete until maxNumCreatures with random cross over of the genes the best creatures plus mutations
		for(int creatureIndex = (bestCreaturesIndex.size()*2)+14; creatureIndex < maxNumCreatures; creatureIndex++)
			for(int featureIndex = 0; featureIndex < 4; featureIndex++)
				newGeneration[creatureIndex][featureIndex] = (Math.random()-0.5) + generation[ThreadLocalRandom.current().nextInt(0,bestCreaturesIndex.size())][featureIndex];
		
		generation = newGeneration;
	}
	private List<Integer> obtainBestCreatures() {	
		Map<Integer, Integer> generationsMap = new TreeMap<Integer, Integer>();
		int bestFitness = 0;
		List<Integer> bestCreatures = new ArrayList<Integer>(); // Indexes list of the best (highest fitness) creatures of the generation
		
		// First we order the creatures by the inverse of fitness (-fitness) while adding the values to calculate the median(?)
		for(int i=0; i < maxNumCreatures; i++){
			generationsMap.put(Integer.valueOf((Double.valueOf(-generation[i][4])).intValue()), i);// We use -fitness to order since the order function is "smallest to largest"
			bestFitness = (bestFitness<generation[i][4])?Integer.valueOf((Double.valueOf(generation[i][4])).intValue()):bestFitness;
		}
						
		Iterator<Entry<Integer, Integer>> iterator = generationsMap.entrySet().iterator();
		
		int creaturesUsed = 0;
		boolean thresholdReached = false;
		
		// Obtaining the best creatures. We add at least 2 creatures and max 8, the top ones above the threshold (66% of the best, we can play with it: Q3, 75%, only the best 2...)
		while(iterator.hasNext() && creaturesUsed < 8 && !thresholdReached){
	         Entry<Integer, Integer> mentry = (Entry<Integer, Integer>) iterator.next();
	         
	         if(creaturesUsed < 2)
	        	 bestCreatures.add(mentry.getValue());
	         else{
	        	 if(-mentry.getKey() > bestFitness*0.66) // (Remember we used -fitness to order so we need to negate again to compare)       		 
	        		 bestCreatures.add(mentry.getValue());	        		
	        	 else
	        		 thresholdReached = true;
	         }	         
	         creaturesUsed++;
	      }		
	     return bestCreatures;
	}
	private void testCreature(int creatureIndex) {

		// The fist thing we do is pressing 'space', assuming the game is stopped due to the last creature failed, until the "Game Over" text disappears
		while(robot.getPixelColor(gameOverTextPosition.x, gameOverTextPosition.y).equals(dinoTextAndObstaclesColor))
		{
			robot.keyPress(KeyEvent.VK_SPACE);
	        robot.keyRelease(KeyEvent.VK_SPACE);
		}
		
		obstacleSize = 0;
		distance = rightMarginOffset.x-dinoPosition.x;
		obstaclesJumped = 0;		
		pseudoSpeed = 0;
		
		// Start checking the inputs
		Thread updateInputsAndFitnessThread = new Thread() {
			public void run() {
				asyncUpdateInputsAndJumps();
			}
		};		
		updateInputsAndFitnessThread.start();
		
		// While the "Game Over" text is not in place we keep testing the current Creature
		while(!robot.getPixelColor(gameOverTextPosition.x, gameOverTextPosition.y).equals(dinoTextAndObstaclesColor))
		{			
			// If data is "real" we calculate the output. Idea for next version: we don't need to calculate if we are on the air
			if(isDataRealQ){
						
				
				double maxDistance = rightMarginOffset.x-dinoPosition.x-(dinoSize/2)-100, minDistance = 0; // Values taken to match the distance "sensor" margin
				double maxPseudoSpeed = 48, minPseudoSpeed = 24; // Values checked by sampling, update if needed
				double maxObstacleSize = 100, minObstacleSize = 34; // Values checked by sampling, update if needed
				
				// Normalizing inputs:				
				//+1 to -1
				double normDistance = (distance - (maxDistance + minDistance)/2)/(maxDistance - (maxDistance + minDistance)/2);
				//+1 to -1
				double normPseudoSpeed = (pseudoSpeed - (maxPseudoSpeed + minPseudoSpeed)/2)/(maxPseudoSpeed - (maxPseudoSpeed + minPseudoSpeed)/2);
				//+1 to -1
				double normObstacleSize = (obstacleSize - (maxObstacleSize + minObstacleSize)/2)/(maxObstacleSize - (maxObstacleSize + minObstacleSize)/2);

				double output = Math.tanh(((normPseudoSpeed * generation[creatureIndex][0] + 
						normDistance * generation[creatureIndex][1] + 
						normObstacleSize * generation[creatureIndex][2] + 
						generation[creatureIndex][3]))); // Perceptron using Hyperbolic tangent
								
				if( output> 0) 
					robot.keyPress(KeyEvent.VK_SPACE);		
				
				/* // Remove the initial '/' to comment the section.
				System.out.print(" D: " + normDistance + "(" + distance + ")");		
				System.out.print(" S: " + normPseudoSpeed + "(" + pseudoSpeed + ")");
				System.out.print(" W: " + normObstacleSize + "(" + obstacleSize + ")");
				System.out.print(" O:" + output);
				System.out.println(" J: " + obstaclesJumped);
				//*/
			}
			
			// Some delay
			try {
				Thread.sleep(samplingRate);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}			
		}
		
		generation[creatureIndex][4] = obstaclesJumped; // Updating fitness		
		updateInputsAndFitnessThread.stop();
	}
	void asyncUpdateInputsAndJumps() {

		while(true){			
			
			// To do the calculations we read an image of 1 pixel height from the Dino's position to the right edge, at the Dino's arm height 
			BufferedImage image = robot.createScreenCapture(captureRect);		
			
			int currentDistance = 0;
			
			// Distance to the first obstacle form the left: if we are between the margins and the current pixel does't have the right color, move to the right and check again
			while(currentDistance < image.getWidth() && !dinoTextAndObstaclesColor.equals(new Color(image.getRGB(currentDistance++, 0), true))){/*sorry, in-line code*/}
		
			// To be sure we have "real" data we wait till the obstacle is full on screen and not "too close" to the Dino
			if(currentDistance < image.getWidth()-100 && currentDistance > 0){// 100 is the max size of an obstacle
				
				if(currentDistance > distance) // If the obstacle detected is further than the last time we checked, we assume is a new obstacle
					obstaclesJumped++;
				else // Otherwise we use the difference between last distance calculated and current calculation to calculate the speed of the game
					pseudoSpeed = distance - currentDistance; // Assuming every lap take similar time the key for speed is the distance difference so we use it as a pseudo-speed
												
				distance = currentDistance;		

				int gapLength = 0;
				int localObstacleSize = 0;
				// Looking for a gap, of a dinoSize length, after the first "colored" pixel (distance), from the first "colored" pixel till the gap beginning that's the obstacle size
				while(distance + localObstacleSize++ < image.getWidth() && gapLength < dinoSize)
					gapLength = (dinoTextAndObstaclesColor.equals(new Color(image.getRGB(distance + localObstacleSize, 0), true)))?0:gapLength+1;
			
				obstacleSize = localObstacleSize;
		
				isDataRealQ = true;
			}
			else
				isDataRealQ = false;
			
			// Some delay
			try {
				Thread.sleep(samplingRate);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}	
		}
	}	
}

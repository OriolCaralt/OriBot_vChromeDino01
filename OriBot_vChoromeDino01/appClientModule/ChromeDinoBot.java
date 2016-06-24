/**
 * First approach to develop an evolutive learning algorithm to play Chrome's jumping Dino off-line game
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
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class ChromeDinoBot{

	private Point dinoEyePosition, gameOverTextPosition;
	private int dinoSize = 25, obstacleSize, distance, obstaclesJumped, maxNumCreatures = 32, rightMarginXValue;
	private double speed;
	private Robot robot;
	private double generation[][] = new double[maxNumCreatures][5];
	private Rectangle imageFrame;
	private String filename = "./initialGeneration.csv";
	private double maxDistance = 450, minDistance = 0; // maxDistance = rightMarginOffset-dinoEyePosition.x - obstacleMaxSize.
	private double maxSpeed = 10, minSpeed = 0; // maxSpeed = max pixels difference ~(550) / min time difference (~67). Values checked by sampling, update if needed. 
	private int maxObstacleSize = 100, minObstacleSize = 0; // maxObstacleSize ~100. Values checked by sampling, update if needed.
	
	public static void main(String[] args) {
		new ChromeDinoBot();
	}
	public ChromeDinoBot() {
		
	    try {
			robot = new Robot();
		} catch (AWTException e1) {
			e1.printStackTrace();
		}   
	    
	    /*// Remove the initial '/' to comment the code section OR add a '/' at the beginning to uncomment.
	    //Code to learn about the mouse x,y position and the RGB color on that point. If the offsets and colors hardcoded here do not match your screen you will need this.
	    System.out.println("Put the pointer on the pixel you want to know the location and color and press Enter/Intro");
  		Scanner scanner = new Scanner(System.in);
  		scanner.nextLine();
  		scanner.close();
  		Point point = MouseInfo.getPointerInfo().getLocation();
  		System.out.println("Pos: " + point.x + "," + point.y + " Color: " + robot.getPixelColor(point.x, point.y));
  		//*/

		System.out.println("Let the game fail and hold that screen, then change the focus back to the cmd, put the mouse at the Dino's eye and press Enter/Intro.");
	    Scanner keyboard = new Scanner(System.in);
		keyboard.nextLine();		
		dinoEyePosition = MouseInfo.getPointerInfo().getLocation();
		
		rightMarginXValue = dinoEyePosition.x+550; // A little bit longer than the actual margin since we will use the last pixel as a background pixel reference
		
		calculateDinoPosition(); // This gives us a more accurate Dino's position than a user pointer location, but we use that one as a starting point.
		
		gameOverTextPosition = new Point(dinoEyePosition.x + 209,dinoEyePosition.y-52);
		
		if(!robot.getPixelColor(dinoEyePosition.x + 209,dinoEyePosition.y-52).equals(new Color(83,83,83))){
			System.out.println("We didn't find the 'Game Over' text");
	  		keyboard.close();
			return;
		}
		
  		keyboard.close();
  		
  		// Since the user put the mouse on the Dino's eye we can change the focus to the browser by simulating a left button mouse pressed
  		robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
  		
		obtainFirstGeneration();	
		startLearning();
	}
	private void obtainFirstGeneration() {
		// Read from file, if file is not there create a generation form scratch	
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
		BufferedReader br = null;
		String line = "";
		int creaturesRead = 0;

		if(filename != null){
			try {
				br = new BufferedReader(new FileReader(filename));

				while ((line = br.readLine()) != null){
	
					String[] creature = line.split(",");
					
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
		return false;
	}
	// Evolve the previous generation to obtain a new generation, test current generation, save it on a file and start all over	
	private void startLearning() {
		int generationIndex = 1;
		double averageCreature;
		while(true)
		{
			evolve();
			int creatureIndex = 0;
			System.out.println("Generation:" + generationIndex++);
			for(; creatureIndex < maxNumCreatures; creatureIndex++)
			{
				// We test each creature 3 times and we take the average result as the creature final fitness				
				testCreature(creatureIndex);
				averageCreature = ((Double)generation[creatureIndex][4]).intValue();				
				
				testCreature(creatureIndex);
				averageCreature += ((Double)generation[creatureIndex][4]).intValue();			
				
				testCreature(creatureIndex);
				averageCreature += ((Double)generation[creatureIndex][4]).intValue();

				generation[creatureIndex][4] = ((Double)(averageCreature/3)).intValue();

				System.out.print(((Double)generation[creatureIndex][4]).intValue() + ",");
				
			}
			System.out.println();
			saveGeneration();						
		}		
	}
	/***
	 * Creates a file, named <current time in milliseconds>_Generation with csv extension with a row per creature on the current generation and each gene plus the fitness as a columns.
	 */
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
	/***
	 * Creates a new generation from the current one and takes its place
	 * First step is create an exact clone of the current generation best creatures (see obtainBestCreatures())
	 * Second is create mutated clones of the best ones. The level of mutation goes between +20% and -20% of the original value of each gene. We mutate all genes.
	 * Third we do a full cross over of the 2 best creatures: all possible combinations of genes. Each gene has 50% chance to have mutations. The mutations are between +20% and -20% of the original value.
	 * The forth step is a random cross over with 50% chances of "big" mutation of each gene until we reach the maximum number of creatures per generation.
	 * NOTE: a full cross over can be done only because we have "few" (4) genes and "enough" (32) max creatures allowed per generation.
	 */
	private void evolve() {
		List<Integer> bestCreaturesIndex = obtainBestCreatures();
		double newGeneration[][] = new double[maxNumCreatures][5];
		
		System.out.print("B:");
		for(int i =0; i<bestCreaturesIndex.size();i++)
			System.out.print(bestCreaturesIndex.get(i) + ",");
		System.out.println();
		
		// Exact clone + mutated clone (mutation between -20% and +20% of the original gene value)
		// Add the best creatures unmodified to the new generation and add the best creatures with random mutations to the new generation
		for(int creatureIndex = 0; creatureIndex < bestCreaturesIndex.size(); creatureIndex++)
			for(int featureIndex = 0; featureIndex < 4; featureIndex++){
				newGeneration[creatureIndex][featureIndex] = generation[bestCreaturesIndex.get(creatureIndex)][featureIndex];
				newGeneration[bestCreaturesIndex.size() + creatureIndex][featureIndex] = (0.8 + (0.4 * Math.random())) * generation[bestCreaturesIndex.get(creatureIndex)][featureIndex];
			}
		
		// Full cross over with 50% random mutations. If the mutation occurs the mutation is between -20% and +20% of the original gene value
		// Add a full cross over between the best 2 creatures. So 2 creatures with 4 genes each => 16 creatures (but we already have the originals so total 14 new)
		for(int i = 1; i <= 14; i++)
			for(int featuresIndex = 0; featuresIndex < 4; featuresIndex++)
				newGeneration[(bestCreaturesIndex.size()*2)+i-1][featuresIndex] = 
					(Math.random()>0.5?1:(0.8 + (0.4 * Math.random())) ) * generation[bestCreaturesIndex.get((i&(byte)Math.pow(2, featuresIndex))==0?0:1)][featuresIndex];
		
		// Random cross over with random mutations
		// Complete until maxNumCreatures with random cross over of the genes the best creatures plus mutations
		for(int creatureIndex = (bestCreaturesIndex.size()*2)+14; creatureIndex < maxNumCreatures; creatureIndex++)
			for(int featureIndex = 0; featureIndex < 4; featureIndex++)
				newGeneration[creatureIndex][featureIndex] = (Math.random()>0.5?0:Math.random()-0.5) + generation[ThreadLocalRandom.current().nextInt(0,bestCreaturesIndex.size())][featureIndex];
		
		generation = newGeneration;
	}
	/***
	 * Returns the list of indexes of the current generation's best creatures.
	 * Once the creatures are ordered by their fitness we select the first 2 and max 6 of the rest creatures above a threshold.
	 * @return List of indexes of the current generation's best creatures.
	 */
	private List<Integer> obtainBestCreatures() {
		List<Point> generationsList = new ArrayList<Point>(); // We use Point since there is no Pair to be used and we are too lazy to code our own Pair
		int bestFitness = 0; // We will use it to set the threshold
		List<Integer> bestCreatures = new ArrayList<Integer>(); // Index list of the best (highest fitness) creatures of the generation
		
		// First we create a list of <fitness,creatureIndex> to be sorted by fitness. We also storage the best fitness of all.
		for(int creatueIndex=0; creatueIndex < maxNumCreatures; creatueIndex++){
			generationsList.add(new Point(Integer.valueOf((Double.valueOf(generation[creatueIndex][4])).intValue()), creatueIndex));
			bestFitness = bestFitness < generation[creatueIndex][4]?Integer.valueOf((Double.valueOf(generation[creatueIndex][4])).intValue()):bestFitness;
		}
		
		generationsList.sort(new Comparator<Point>() {@Override public int compare(Point point1, Point point2) {return Integer.compare(point1.x, point2.x);}});
		
		int creaturesUsed = 0;
		boolean thresholdReached = false;

		// We add at least 2 creatures and max 8, the top ones above the threshold: 66% of the best (we can play with it: Q3, 75%, only the best 2...)
		while(creaturesUsed < 8 && !thresholdReached) // Since the list is ordered lowest-first we start checking by the last element.
		{
			if(creaturesUsed < 2)
	        	 bestCreatures.add(generationsList.get(generationsList.size() - creaturesUsed -1).y);
	         else{
	        	 if(generationsList.get(generationsList.size() - creaturesUsed -1).x > bestFitness*0.66)     		 
	        		 bestCreatures.add(generationsList.get(generationsList.size() - creaturesUsed -1).y);	        		
	        	 else
	        		 thresholdReached = true;
	         }	         
	         creaturesUsed++;
		}	
		return bestCreatures;
	}
	private double applyNN(int creatureIndex) {
		// Normalizing inputs: +1 to -1.
		double normDistance = (distance - (maxDistance + minDistance)/2)/(maxDistance - (maxDistance + minDistance)/2);
		double normSpeed = (speed - (maxSpeed + minSpeed)/2)/(maxSpeed - (maxSpeed + minSpeed)/2);
		double normObstacleSize = (obstacleSize - (maxObstacleSize + minObstacleSize)/2)/(maxObstacleSize - (maxObstacleSize + minObstacleSize)/2);

		return Math.tanh(((normSpeed * generation[creatureIndex][0] + 
				normDistance * generation[creatureIndex][1] + 
				normObstacleSize * generation[creatureIndex][2] + 
				generation[creatureIndex][3]))); // Perceptron using Hyperbolic tangent.
	}
	private void calculateDinoPosition() {

		// Keep the Dino straight
		robot.keyRelease(KeyEvent.VK_UP);
		robot.keyRelease(KeyEvent.VK_DOWN);
		
		int increment = 0;

		//System.out.println("increment: " + increment);
		//System.out.println("dinoEyePosition.x: " + dinoEyePosition.x);
		//System.out.println("dinoEyePosition.x -15 + (increment): " + (dinoEyePosition.x -15 + (increment)));
		//System.out.println("robot.getPixelColor(dinoEyePosition.x -15 + (increment), dinoEyePosition.y): " + robot.getPixelColor(dinoEyePosition.x -15 + (increment), dinoEyePosition.y));
		// From the last Dino's eye last position (a little bit behind actually, we look for the neck more than for the eye) we move to the right looking for a Dino-colored pixel.
		while(!robot.getPixelColor(dinoEyePosition.x -15 + (increment++), dinoEyePosition.y).equals(new Color(83,83,83)))
		{
			//System.out.println(robot.getPixelColor(dinoEyePosition.x -15 + (increment), dinoEyePosition.y));
		}
		
		//System.out.println("increment:" + increment);
		
		dinoEyePosition.x += increment-15;

		imageFrame = new Rectangle(dinoEyePosition.x + 40, dinoEyePosition.y, rightMarginXValue-(dinoEyePosition.x + 40), 15);
 	
 		//robot.mouseMove(dinoEyePosition.x, dinoEyePosition.y); // We use this to be sure the x,y point is where we expected to be: at the neck of the Dino.
	}

	void testCreature(int creatureIndex) {

		int pixels = 0, currentDistance = 0, gapLength = 0, localObstacleSize = 0;
		BufferedImage image;
		Color backgroundColor;
		long newTimeMillis, lastTimeMillis =  System.currentTimeMillis(), timeMillis;
		
		obstacleSize = 0;
		distance = 0;
		obstaclesJumped = -1; // The first obstacle detected, not jumped, will already add 1 to this variable
		speed = 0;
	
		// The fist thing we do is pressing 'space', assuming the game is stopped due to the last creature failed, until the "Game Over" text disappears.
		while(robot.getPixelColor(gameOverTextPosition.x, gameOverTextPosition.y).equals(new Color(83,83,83)) || 
				robot.getPixelColor(gameOverTextPosition.x, gameOverTextPosition.y).equals(new Color(172,172,172))){
			robot.keyPress(KeyEvent.VK_SPACE);
	        robot.keyRelease(KeyEvent.VK_SPACE);
		}
		
		// Since the Dino moves to the right from game to game we need to calculate Dino's position each new game.
		calculateDinoPosition();
		
		// While the 'Game Over' text is not there...
		while(!(robot.getPixelColor(gameOverTextPosition.x, gameOverTextPosition.y).equals(new Color(83,83,83)) || 
				robot.getPixelColor(gameOverTextPosition.x, gameOverTextPosition.y).equals(new Color(172,172,172)))){
								
			image = robot.createScreenCapture(imageFrame);
			backgroundColor = new Color(image.getRGB(image.getWidth()-1, 0));			
			currentDistance = 0;
			
			// Calculate distance from Dino's position to the first obstacle from left
			while(++currentDistance < image.getWidth() - maxObstacleSize // Knowing the obstacle is completely on image we stop checking pixels before the end of the image
					&& backgroundColor.equals(new Color(image.getRGB(currentDistance, 0), true)) // We check the upper side for "mid" level obstacles
					&& backgroundColor.equals(new Color(image.getRGB(currentDistance, 14), true))){} // Here we check the lower side for "low" level obstacles

			if(currentDistance > distance){// If the obstacle detected is further than the last time we checked we assume we encountered a obstacle, so we update that variable and check for the obstacle size
				obstaclesJumped++;
				
				gapLength = 0;                                     
				localObstacleSize = 0;
				
				while(currentDistance + ++localObstacleSize < image.getWidth() && gapLength < dinoSize)
					gapLength = ((backgroundColor.equals(new Color(image.getRGB(currentDistance + localObstacleSize, 0), true))) 
							&& backgroundColor.equals(new Color(image.getRGB(currentDistance + localObstacleSize, 14), true)))?gapLength+1:0;

				obstacleSize = localObstacleSize;		
			}
			else // Otherwise we use last distance and time, and the current values of them, to calculate the speed of the obstacles
			{
				newTimeMillis = System.currentTimeMillis();
				timeMillis = newTimeMillis - lastTimeMillis;
				pixels = distance - currentDistance;					
				speed = (double)pixels/(double)timeMillis;
				lastTimeMillis = newTimeMillis;
			}		
			
			distance = currentDistance;
			applyOutputRules(applyNN(creatureIndex));
		}		

		// Correction needed due detection mistakes
		if(distance > maxObstacleSize)
			obstaclesJumped--;
		
		generation[creatureIndex][4] = obstaclesJumped; // Updating fitness.
	}
	/***
	 * If 'output' is between +20% and -20% the UP key is pressed (and the DOWN key released).
	 * If 'output' is below -20% the DOWN key is pressed (and the UP key released).
	 * If 'output' is adobe +20$ all keys are released.
	 * @param output: the output of the NN. A double value expected to be between +1 and -1.
	 */
	int state = 0;
	private void applyOutputRules(double output) {
		//System.out.print("J:" + obstaclesJumped + " D: " + String.format( "%.2f", normDistance) + " S: " + String.format( "%.2f", normSpeed) + " W: " + String.format( "%.2f", normObstacleSize) + " O: " + String.format( "%.2f", output));
		//System.out.print("J:" + obstaclesJumped + " D: " + distance + " S: " + String.format( "%.2f", speed*100) + " W: " + obstacleSize + " O: " + String.format( "%.2f", output));

		if( Math.abs(output) < 0.2){
			if(state != 1){
				robot.keyRelease(KeyEvent.VK_DOWN);
				robot.keyPress(KeyEvent.VK_UP);
				state = 1;
			}
			//System.out.println(" Up!");
		}			
		else if(output < -0.2){
			if(state != -1){
				robot.keyRelease(KeyEvent.VK_UP);
				robot.keyPress(KeyEvent.VK_DOWN);				
				state = -1;
			}
			//System.out.println(" Down!");
		}
		else{
			if(state !=0){
				robot.keyRelease(KeyEvent.VK_UP);
				robot.keyRelease(KeyEvent.VK_DOWN);
				state = 0;
			}
			//System.out.println(" Hands up!");
		}
	}
}

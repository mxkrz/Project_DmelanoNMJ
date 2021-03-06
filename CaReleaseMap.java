package Util;

import java.util.Arrays;

import ij.*;
import ij.process.ImageProcessor;

public class CaReleaseMap {

	private static int CUT8BIT = 180;

	public static int LOWRESOLUTION = 32;
	private static int HLRATIO;

	private ImagePlus imgMax;

	private int maxPixCount, maxPixH, maxPixW;

	private float[][] histoData;
	private char[] interMap;
	private int[][] connectedComponents;

	@SuppressWarnings("static-access")
	public CaReleaseMap(ImagePlus imgMax, int LOWRESOLUTION) {
		this.imgMax = imgMax;
		ImageProcessor ipMax = this.imgMax.getProcessor();
		// is byte processing faster?

		// basic image information
		maxPixCount = ipMax.getPixelCount();
		maxPixH = ipMax.getHeight();
		maxPixW = ipMax.getWidth();

		// determining the ration between high and low resolution images
		if (maxPixH == maxPixW) {
			HLRATIO = maxPixH / LOWRESOLUTION;
		} else {
			System.err
					.println("Max. intensity images' height and width must be equal.");
			return;
		}

		/*
		 * create HLRATIO "datasquares" for standard deviation analysis
		 * 
		 * example: High Res picture = 512x512; Low Res picture = 32 x 32 ->
		 * 16:1 ratio 1squ = 256pix of High Res => divided into 4x4 squares each
		 * containing 16pix
		 * 
		 * The mean of the 16pixel is compared to all other 15 16pixel squares
		 * and tested on its SD dependency.
		 */
		int[][] pixelArray = ipMax.getIntArray();

		// mode 8x8

		for (int i = 0; i < pixelArray.length / (HLRATIO * 0.5); i++) {

			for (int j = 0; j < pixelArray[i].length / (HLRATIO * 0.5); j++) {
				int[] by8 = new int[(HLRATIO / 2) * (HLRATIO / 2)];
				// creating 8x8 datapoint array
				for (int k = 0; k < HLRATIO / 2; k++) {
					for (int l = 0; l < HLRATIO / 2; l++) {
						by8[(int) (k * HLRATIO * 0.5) + l] = pixelArray[(int) (k + (i
								* HLRATIO * 0.5))][(int) (l + (j * HLRATIO * 0.5))];
					}

				}
				// System.out.println();
				int[] mode = MathCalc.getMode(by8);
				// System.out.println(mode[0] + "\t"+mode[1]);

				if (mode[1] > 10) {

					for (int k = 0; k < HLRATIO / 2; k++) {
						for (int l = 0; l < HLRATIO / 2; l++) {
							pixelArray[(int) (k + (i * HLRATIO * 0.5))][(int) (l + (j
									* HLRATIO * 0.5))] = mode[0];
						}

					}
				}
			}

		}

		int[][] oneDArray = new int[LOWRESOLUTION * LOWRESOLUTION][HLRATIO
				* HLRATIO];
		/*
		 * redistribute pixel within array to ratio, i.e.: oneDRatioArray[left
		 * to right, top to bottom; Low Res pixel] [left to Right, top to
		 * bottom; representative pixel of High Res]
		 */
		int a = -1;
		int b = 0;

		for (int i = 0; i < oneDArray.length; i++) {
			a++;
			if (a > 31) {
				a = 0;
				b++;
			}

			for (int j = 0; j < oneDArray[i].length; j++) {
				int x = (j / HLRATIO) + (b * HLRATIO);
				int y = (j % HLRATIO) + (a * HLRATIO);
				oneDArray[i][j] = pixelArray[x][y];
			}
		}

		// build island spotter - used for pixel decision process (line 291)
		int[][] islandMap = new int[oneDArray.length][6];
		for (int i = 0; i < oneDArray.length; i++) {
			int[] xx = oneDArray[i];
			float[] xxx = new float[xx.length];
			for (int j = 0; j < xx.length; j++) {
				xxx[j] = (float) xx[j];
			}
			int[] mode = MathCalc.getMode(oneDArray[i]);
			StandardDeviation x = new StandardDeviation(xxx);
			islandMap[i][0] = (int) x.getMean();
			islandMap[i][1] = (int) x.getSD();
			islandMap[i][2] = mode[0];
			islandMap[i][3] = mode[1];

			/*
			 * System.out.print((int) x.getMean() + "�" + (int) x.getSD() + " "
			 * + mode[0] + " " + mode[1] + "\t"); if (i % 32 == 31)
			 * System.out.println();
			 */
		}
		// System.out.println();*

		int[] meanSort = new int[oneDArray.length];
		for (int i = 0; i < oneDArray.length; i++) {
			meanSort[i] = islandMap[i][0];
		}
		Arrays.sort(meanSort);
		int nill = meanSort[0];
		// int range = meanSort[meanSort.length - 1] - meanSort[0];
		meanSort = null;

		for (int i = 0; i < islandMap.length; i++) {
			islandMap[i][4] = (int) ((float) (islandMap[i][0] - nill) / 20);
			islandMap[i][5] = (int) ((float) (islandMap[i][2] - nill) / 20);
		}

		/*
		 * for (int i = 0; i < islandMap.length; i++) {
		 * System.out.print(islandMap[i][4] + " " + islandMap[i][5] + "\t\t");
		 * if (i % 32 == 31){ System.out.println(); } }
		 */

		// dump pixelArray
		pixelArray = null;

		/*
		 * create 128x128 average for the following SD analysis. Each 16x16
		 * square is divided into 16 4x4squares and their average taken. The
		 * order is from left to right, top to bottom.
		 */
		float[][] avgImg = new float[LOWRESOLUTION * LOWRESOLUTION][HLRATIO];

		a = 0;
		int sum = 0;
		int c = 0;

		for (int i = 0; i < avgImg.length; i++) {

			for (int j = 0; j < avgImg[i].length; j++) {

				for (int k = 0; k < oneDArray[i].length / HLRATIO * 0.25; k++) {

					for (int l = 0; l < oneDArray[i].length / HLRATIO * 0.25; l++) {

						sum += oneDArray[i][l + (k * HLRATIO)
								+ (int) (j * HLRATIO * 0.25) + a];

						c = l + (k * HLRATIO) + (int) (j * HLRATIO * 0.25) + a;
					}
				}
				if (j % 4 == 3)
					a = c - ((j + 1) * 4) + 1;

				avgImg[i][j] = (float) sum / HLRATIO;
				sum = 0;
			}
			a = 0;
		}

		// dump oneDArray
		oneDArray = null;

		// calculating the z-Score: (Mean-datapoint)/SD
		int[][] zScore = new int[avgImg.length][HLRATIO];
		// storing mean and SD for each 16x16 avg square (256pix)
		float[][] meanSD = new float[2][avgImg.length];

		for (int i = 0; i < avgImg.length; i++) {
			StandardDeviation outline = new StandardDeviation(avgImg[i]);

			for (int j = 0; j < avgImg[i].length; j++) {
				zScore[i][j] = outline.getZScore(avgImg[i][j]);
				meanSD[0][i] = outline.getMean();
				meanSD[1][i] = outline.getSD();
			}
		}

		// histogramming zScore from -9 to 9
		// -> 19fields + mean + sd =21 fields
		histoData = new float[avgImg.length][21];
		MathCalc intm = new MathCalc();

		for (int i = 0; i < histoData.length; i++) {
			// cycle through avgImg and compute histogram data points
			for (int j = 0; j < HLRATIO; j++) {
				histoData[i][intm.getHistoField(zScore[i][j])]++;
			}
			// assign mean and sd
			histoData[i][19] = meanSD[0][i];
			histoData[i][20] = meanSD[1][i];
		}

		// weighting and elimination of avgImg XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

		for (int i = 0; i < zScore.length; i++) {

			/*
			 * 1. if sum of histo zScores(-2 - 2) add up to 12 or more (>74%)
			 * and (zScore 1 < CUT8BIT) || (zScore -1 > CUT8BIT) either assign 0
			 * or 1, respectively.
			 */

			if (((histoData[i][8] + histoData[i][9] + histoData[i][10]) > 11)
					&& ((histoData[i][19] + histoData[i][20]) >= CUT8BIT)) {
				for (int j = 0; j < HLRATIO; j++) {
					if ((zScore[i][j] > -3) && (zScore[i][j] < 3)) {
						zScore[i][j] = 1;
					}
				}
			}
			if (((histoData[i][8] + histoData[i][9] + histoData[i][10]) > 11)
					&& ((histoData[i][19] - histoData[i][20]) <= CUT8BIT)) {
				for (int j = 0; j < HLRATIO; j++) {
					if ((zScore[i][j] > -3) && (zScore[i][j] < 3)) {
						zScore[i][j] = 0;
					}
				}
			}

			// if >80% (eqv. to 13) of zScore == 1 || 0 then set all to 1 or 0
			// F respectively
			int setAllCount = 0;
			for (int j = 0; j < zScore[i].length; j++) {
				if (zScore[i][j] == 1)
					setAllCount++;
			}
			if (setAllCount > 12)
				Arrays.fill(zScore[i], (int) 1);

			setAllCount = 0;
			for (int j = 0; j < zScore[i].length; j++) {
				if (zScore[i][j] == 0)
					setAllCount++;
			}
			if (setAllCount > 12)
				Arrays.fill(zScore[i], (int) 0);
		}

		for (int i = 0; i < zScore.length; i++) {
			for (int j = 0; j < zScore[i].length; j++) {

				zScore[i][j] = (int) (zScore[i][j] * (meanSD[1][i] * 0.1) + meanSD[0][i]);

				if ((zScore[i][j] < CUT8BIT / 4) && (meanSD[1][i] < 20))
					zScore[i][j] = 0;
				if (zScore[i][j] < 80)
					zScore[i][j] = 0;

				if ((zScore[i][j] > CUT8BIT) && (meanSD[1][i] < 10))
					zScore[i][j] = 1;

				if ((zScore[i][j] > CUT8BIT * 1.15)
						&& (meanSD[1][i]) < (CUT8BIT * 1.15 - CUT8BIT))
					zScore[i][j] = 1;
				if (meanSD[0][i] > 199)
					zScore[i][j] = 1;
				if (zScore[i][j] > 210)
					zScore[i][j] = 1;
				if ((zScore[i][j] > 125) && ((islandMap[i][5] > 10)))
					zScore[i][j] = 1;

			}
			for (int k = 0; k < zScore[i].length; k++) {
				if (zScore[i][k] == 0)
					Arrays.fill(zScore[i], 0);
				if (zScore[i][k] == 1) {
					int[] mode = MathCalc.getMode(zScore[i]);
					if (mode[0] == 1)
						Arrays.fill(zScore[i], 1);
				}
			}
		}

		// transform to show weighted128[x][y] -> 128x128
		int[][] weighted128 = new int[(int) (maxPixW / (HLRATIO * 0.25))][(int) (maxPixH / (HLRATIO * 0.25))];

		int s = 0;
		int t = 0;
		int r = 0;
		int q = 0;
		int v = 0;
		int w = 0;

		for (int i = 0; i < maxPixW / (HLRATIO * 0.25); i++) {

			t = (int) ((int) i % (HLRATIO * 0.25));
			t *= 4;
			s = t;
			r = w;

			for (int j = 0; j < maxPixH / (HLRATIO * 0.25); j++) {

				weighted128[i][j] = zScore[r][t];
				t++;
				if (t > s + 3) {
					t = s;
					r++;
				}
			}
			t = 0;
			q++;
			if (q > 3) {
				v++;
				q = 0;
				w = v * LOWRESOLUTION;
			}
		}

		// meanSD
		meanSD = null;

		/*
		 * System.err.println("Diagnostics: 128x128 zScore weighting."); for
		 * (int i = 0; i < 128; i++) { for (int j = 0; j < 128; j++) {
		 * System.out.print(weighted128[i][j] + " "); if (j % 4 == 3) {
		 * System.out.print("\t"); }
		 * 
		 * } System.out.println(); if (i % 4 == 3) { System.out.println("\t"); }
		 * 
		 * }
		 */
		// dump weighted 128
		weighted128 = null;

		// intermediate 32 x 32 map !!!!!!!!!!!!! interMap[y][x]
		interMap = new char[LOWRESOLUTION * LOWRESOLUTION];
		for (int i = 0; i < interMap.length; i++) {
			int zero = 0;
			int one = 0;
			int none = 0;
			for (int j = 0; j < zScore[i].length; j++) {
				if (zScore[i][j] == 0)
					zero++;
				if (zScore[i][j] == 1)
					one++;

				if ((zScore[i][j] == 0) || (zScore[i][j] == 0))
					none++;
			}
			if ((none > 0) || (one < 5))
				interMap[i] = ' ';
			if (zero > 8)
				interMap[i] = ' ';
			if (one == 16)
				interMap[i] = 'O';

			/*
			 * System.out.print(interMap[i] + " "); if (i % 32 == 31) {
			 * System.out.println("X"); }
			 */
		}
		// dump zScore
		zScore = null;
		// compute connected components - caReleaseMap[no. of
		// components][y,x,y,x,..]
		computeComponents(interMap);

	}

	private void computeComponents(char[] interMp) {
		char[] interMap = interMp;

		int[] firstPass = new int[interMap.length];
		Arrays.fill(firstPass, 0);
		int nextComp = 0;

		// FIRST PASS
		for (int i = 0; i < firstPass.length; i++) {

			// get neighbours
			int[] neighbours = new int[3];
			Arrays.fill(neighbours, 0);
			if (i > 0) {
				if (interMap[i - 1] == 'O')
					neighbours[0] = firstPass[i - 1];
			}
			if (i > 31) {
				if (interMap[i - 32] == 'O')
					neighbours[1] = firstPass[i - 32];
			}
			if (i > 32) {
				if (interMap[i - 33] == 'O')
					neighbours[1] = firstPass[i - 33];
			}
			// lowest component or new compoenent
			int comp = nextComp;
			Arrays.sort(neighbours);
			int itter = 0;
			for (int k = 0; k < neighbours.length; k++) {
				if (neighbours[k] != 0) {
					comp = neighbours[k];
					break;
				} else {
					itter++;
				}
			}

			// assign component number
			if (interMap[i] == 'O') {
				if (itter == 3) {
					nextComp++;
					comp = nextComp;
				}
				firstPass[i] = comp;
			}

		}

		// SECOND PASS - generating equivalence
		int[][] eq = new int[nextComp][1];

		for (int i = 0; i < eq.length; i++)
			eq[i][0] = i + 1;

		for (int i = LOWRESOLUTION; i < firstPass.length - 2 * LOWRESOLUTION; i++) {

			int[] neighbours = new int[8];
			Arrays.fill(neighbours, 0);

			if (firstPass[i] != 0) {

				// get neighbours
				// upper row
				if ((i - LOWRESOLUTION - 1 >= 0)
						&& ((i - LOWRESOLUTION - 1) % 32 < 31))
					neighbours[0] = firstPass[i - LOWRESOLUTION - 1];
				neighbours[1] = firstPass[i - LOWRESOLUTION];
				if ((i - LOWRESOLUTION + 1) % 32 > 0)
					neighbours[2] = firstPass[i - LOWRESOLUTION + 1];

				// mid row
				if ((i - 1) % 32 < 31)
					neighbours[3] = firstPass[i - 1];
				if ((i + 1) % 32 > 0)
					neighbours[4] = firstPass[i + 1];

				// bottom row
				if ((i + LOWRESOLUTION - 1) % 32 < 31)
					neighbours[5] = firstPass[i + LOWRESOLUTION - 1];
				neighbours[6] = firstPass[i + LOWRESOLUTION];
				if ((i + LOWRESOLUTION + 1) % 32 > 0)
					neighbours[7] = firstPass[i + LOWRESOLUTION + 1];
			}

			Arrays.sort(neighbours);

			for (int k = 0; k < neighbours.length; k++) {

				if (neighbours[k] != 0) {

					boolean present = false;

					for (int h = 0; h < eq[firstPass[i] - 1].length; h++) {

						if (eq[firstPass[i] - 1][h] == neighbours[k])
							present = true;
					}

					if (present == false) {
						int[] inter = new int[eq[firstPass[i] - 1].length + 1];
						for (int h = 0; h < eq[firstPass[i] - 1].length; h++) {
							inter[h] = eq[firstPass[i] - 1][h];
						}
						inter[inter.length - 1] = neighbours[k];
						eq[firstPass[i] - 1] = inter;
					}

				}
			}

		}

		// generating component's eqiovalence

		for (int i = 0; i < eq.length; i++) {

			if (eq[i].length > 1) {

				for (int j = 0; j < eq[i].length; j++) {

					for (int k = 0; k < eq[eq[i][j] - 1].length; k++) {

						boolean present = false;

						for (int h = 0; h < eq[i].length; h++) {

							if (eq[eq[i][j] - 1][k] == eq[i][h])
								present = true;
						}

						if (present == false) {

							int[] inter = new int[eq[i].length + 1];
							for (int h = 0; h < eq[i].length; h++) {
								inter[h] = eq[i][h];
							}
							inter[inter.length - 1] = eq[eq[i][j] - 1][k];
							eq[i] = inter;
						}

					}
				}

				Arrays.sort(eq[i]);
			}
		}

		
		//Removing double
		int [][]equivalence = new int[0][];
		
		for(int i = 0; i<eq.length; i++){
			
			boolean present = false;
			
			for(int j=0; j<equivalence.length; j++){
				
				if(eq[i][0] == equivalence[j][0])
					present = true;
			}
			
			if(present ==false){
				int[][] inter = new int[equivalence.length+1][];
				for(int j = 0; j<equivalence.length; j++){
					inter[j] = equivalence[j];
				}
				inter[inter.length-1] = eq[i];
				equivalence = inter;
			}
			
		}		
		
		//THIRD PASSes - obtaining coordinates
		int[][] comps = new int[equivalence.length][];
		
		for(int i = 0; i<equivalence.length; i++){
			
			for(int j = 0; j<firstPass.length; j++){
				
				if(firstPass[j]>0){
					
					boolean present = false;
					
					for(int k = 0; k<equivalence[i].length; k++){
						if(equivalence[i][k]==firstPass[j])
						present = true;		
					}
					
					if(present == true){
						
						if(comps[i]==null){
							comps[i] = new int[2];
							comps[i][0] = j%LOWRESOLUTION;
							comps[i][1] = j/LOWRESOLUTION;
							
						} else {
						
						int[] inter = comps[i];
						comps[i] = new int[inter.length+2];
						for(int k = 0; k<inter.length; k++){
							comps[i][k] = inter[k];
						}
						comps[i][comps[i].length-2] = j%LOWRESOLUTION;
						comps[i][comps[i].length-1] = j/LOWRESOLUTION;
						
						}
					}
					
				}
				
			}
			
		}
		
		connectedComponents = comps;


	}

	// getters
	public int getPixCount() {
		return maxPixCount;
	}

	public int getPixW() {
		return maxPixW;
	}

	public int getPixH() {
		return maxPixH;
	}

	public int getHLRatio() {
		return HLRATIO;
	}

	public char[] getInterMap() {
		// interMap[y][x]
		return interMap;
	}

	public int[][] getComponents() {

		return connectedComponents;
	}

	public float[][] getHistogram128() {
		// [pixel left-right,top-bottom][21 -> no of zScore from -9 to 9;
		// 20=mean, 21=sd]
		return histoData;
	}

	// setters
	public void setCutOff(int cutOff, String depth) {
		if (depth.equals("8bit")) {
			CUT8BIT = cutOff;
		}
	}
}

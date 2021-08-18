package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.demo.PalettePNG.optimiseToPngImage;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.Integer.toHexString;
import static java.lang.Math.abs;
import static org.apache.commons.lang3.StringUtils.leftPad;

/**
 * Version améliorée de NotShrink2 : 
 * - s'appuie sur kmeans++
 * - change le calcul de distance en s'appuyant sur CieLAB au lieu de RGB.
 * 
 */
@Slf4j
public class NoteShrink2 {
    
    
    public static boolean DEBUG = true;
    
    void run() throws IOException {

        Path executionPath = Paths.get("/home/flo/tmp/test_noteshrink");
        String fichierOrigine = "out-001.png";
        
        int K = 16;  // nombre de clusters = nombre de couleurs max
        // différence de saturation d'un pixel par rapport au background pour qu'il soit considéré comme faisant
        // parti du foreground
        float foregroundSaturationDiff = 0.20f;  
        // idem pour la brightness
        float foregroundBrightnessDiff = 0.30f;  
        // nombre d'iterations max pour le calcul de clusterisation
        int maxClusterIterations = 5000;  // 5000 semble suffisant
        // taille d'échantillonage de l'image (en pourcentage) 
        float samplePercentage = 0.05f;   // 5% 
                        
        BufferedImage source = ImageIO.read(executionPath.resolve(fichierOrigine).toFile());
        
        int[] sample = sample(source, samplePercentage);
        if(DEBUG) displaySampleImage(sample, "original_sample.png");
        
        // Les pixels sont réduits aux 6 bits les plus significatif pour faciliter
        reduceTo6bits(sample);

        sortToBrightness(sample);
        if(DEBUG) displaySampleImage(sample, "sorted_reduced_sample.png");
        
        
       
        
        int backgroundColor = findBackgroundColor(sample);
        log.info("BG pixel : {} {} - ", toBinary(backgroundColor), toHexString(backgroundColor));

        int[] foregroundSample = filterToOnlyForegroundColor(sample, backgroundColor, foregroundSaturationDiff, foregroundBrightnessDiff);
        if(DEBUG) displaySampleImage(foregroundSample, "fg_sample.png");

        // pourrait faire le même exercice avec la couleur la plus foncée
        double darkestBrightness = Double.MAX_VALUE;
        int darkestColor = 0x0;
        for(int i = 0; i<foregroundSample.length; i++) {
            double[] hsb = ColorUtils.RGBtoHSB(foregroundSample[i]);
            log.info("{}", hsb[2]);
            if(hsb[2] < darkestBrightness) {
                darkestBrightness = hsb[2];
                darkestColor = foregroundSample[i];
            }
        }
        
        log.info("darkestBrighness {}   darkestColor {}", darkestBrightness, darkestColor);
        List<Double> range = new ArrayList<Double>();
        for(int i = 0; i<foregroundSample.length; i++) {
            double[] hsb = ColorUtils.RGBtoHSB(foregroundSample[i]);
            if(hsb[2] < 0.2d) {
                foregroundSample[i] = 0x0; //darkestColor;
            } else if(hsb[1] < 0.2d) {
                foregroundSample[i] = 0x4d4d4d;
            } else {
                // pour tous les pixels suffisamment visibles
                range.add(hsb[0]);
            }
        }

        range.sort(Double::compareTo);
        double diff = Math.abs(range.get(0) - range.get(range.size()-1));
        if(diff <= 0.45d) // si moins de 45 degré, alors on considère qu'il n'ya qu'un groupe de couleurs
            K = 4;
         // pour tous les autres cas, on reste en K = 16
                    
        if(DEBUG) displaySampleImage(foregroundSample, "fg_sample2.png");
             
        // prépare la liste de Record
        List<PixelHSBRecord> records = new ArrayList<>(foregroundSample.length);
        for(int i = 0; i < foregroundSample.length; i++ ) {
            records.add(new PixelHSBRecord(foregroundSample[i]));
        }

        KMeansPlusPlusClusterer<PixelHSBRecord>  clusterer = new KMeansPlusPlusClusterer<>(K-1, maxClusterIterations);
        
        //MultiKMeansPlusPlusClusterer multiClusterer = new MultiKMeansPlusPlusClusterer(clusterer, 10);
        //List<CentroidCluster<PixelLABRecord>> clusters = multiClusterer.cluster(records);
                
         List<CentroidCluster<PixelHSBRecord>> clusters = clusterer.cluster(records);
        
        int[] palette = new int[clusters.size()+1];
        int paletteIdx = 0;
        palette[paletteIdx++] = backgroundColor;
        for(CentroidCluster<PixelHSBRecord> cluster : clusters) {
           double[] hsb = cluster.getCenter().getPoint();
           int value = ColorUtils.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
           palette[paletteIdx++] = value;
        }
        
        log.info("nb of elements in palette : {}", palette.length );
       
        if(DEBUG) displaySampleImage(palette, "palette.png");

        // Applique les centroid sur tous les pixels de l'image finale
        double[] bgHSB = ColorUtils.RGBtoHSB(backgroundColor);
        int[] sourceArray = toArray(source);
        for(int i = 0; i < sourceArray.length; i++) {
            double[] currentHSB = ColorUtils.RGBtoHSB(sourceArray[i]);
            double saturationDiff = Math.abs(bgHSB[1] - currentHSB[1]);
            double brightnessDiff = Math.abs(bgHSB[2] - currentHSB[2]);
            if(saturationDiff < foregroundSaturationDiff && brightnessDiff < foregroundBrightnessDiff) {
                // toutes les pixels qui ne se différencient pas assez du background auront tous 
                // la meme couleur
                sourceArray[i] = backgroundColor;
            } else {
                CentroidCluster<PixelHSBRecord> closestCentroid = vq(clusters, new PixelHSBRecord(sourceArray[i]));
                double[] hsb = closestCentroid.getCenter().getPoint();
                sourceArray[i] = ColorUtils.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
            }
        }
               
        // debugging
        displaySampleImage(sourceArray, "out_before_write.png", source.getWidth(), source.getHeight());
        BufferedImage out = optimiseToPngImage(sourceArray, palette, source.getWidth(), source.getHeight());
        ImageIO.write( out, "PNG", new File("out.png") );  
 
    }
    
    
    class PixelRGBRecord implements Clusterable {
        int value;
        PixelRGBRecord(int value) {
            this.value = value;
        }
        
        @Override
        public double[] getPoint() {
            int[] rgb = intToRGB(value);
            return new double[] { rgb[0]*1d, rgb[1]*1d, rgb[2]*1d };
        }
    }

    class PixelLABRecord implements Clusterable {
        int value;
        PixelLABRecord(int value) {
            this.value = value;
        }

        @Override
        public double[] getPoint() {
            double[] outlab = new double[3];
            int[] rgb = intToRGB(value);
            ColorUtils.RGBToLAB(rgb[0], rgb[1], rgb[2], outlab);
            return outlab;
        }
    }

    class PixelHSBRecord implements Clusterable {
        int value;
        PixelHSBRecord(int value) {
            this.value = value;
        }

        @Override
        public double[] getPoint() {
            return  ColorUtils.RGBtoHSB(value);
        }
    }
    
    
    int[] intToRGB(int value) {
        int red = (value >> 16) & 0xFF;
        int green = (value >> 8) & 0xFF;
        int blue = value & 0xFF;
        return new int[] { red, green, blue };
    }
    
    int rgbToInt(int[] rgb) {
        return ((rgb[0] & 0x0ff) << 16) | ((rgb[1] & 0x0ff) << 8) | (rgb[2] & 0x0ff);
    }

    int rgbToInt(int r, int g, int b) {
        return ((r & 0x0ff) << 16) | ((g & 0x0ff) << 8) | (b & 0x0ff);
    }
    
    
         
    public static CentroidCluster<PixelHSBRecord> vq(List<CentroidCluster<PixelHSBRecord>> centroids, Clusterable observation) {
        double min = Double.MAX_VALUE;
        EuclideanDistance distance = new EuclideanDistance();
        CentroidCluster<PixelHSBRecord> closestCentroid = null;
        for (CentroidCluster<PixelHSBRecord> centroid : centroids) {
            double d = distance.compute(centroid.getCenter().getPoint(), observation.getPoint());
            if(d < min) {
                min = d;
                closestCentroid = centroid;
            }
        }
        return closestCentroid;
    }
    
    int[] filterToOnlyForegroundColor(int[] image, int backgroundColor, float fgSaturationDiff, float fgBrightnessDiff) {
        // calcule le fg_mask
        int[] fg_sample = new int[0];
        double[] bgHSB = ColorUtils.RGBtoHSB(backgroundColor);
        for(int i = 0; i < image.length; i++) {

            double[] sampleHSB = ColorUtils.RGBtoHSB(image[i]);

            double saturationDiff = Math.abs(bgHSB[1] - sampleHSB[1]);
            double brightnessDiff = Math.abs(bgHSB[2] - sampleHSB[2]);

            if(saturationDiff >= fgSaturationDiff|| brightnessDiff >= fgBrightnessDiff) {
                // TODO surement prévoir d'optimiser ce truc...
                fg_sample = ArrayUtils.add(fg_sample, image[i]);
            }
        }
        return fg_sample;
    }
    
    /** cherche la couleur de fond, c'est à faire celle qui est le plus présente à l'image */
    int findBackgroundColor(int[] image) {
        // nombre d'occurence par couleur 
        // ca pue du cul de repasser par un Array de Integer juste pour ça.
        // TODO A OPTIMISER plus TARD et éviter de repasser par un Array
        Integer[] sampleInt = new Integer[image.length];
        Arrays.parallelSetAll(sampleInt, i -> image[i]);
        Map<Integer, Long> counters = Arrays.stream(sampleInt)
                .collect(Collectors.groupingBy(p -> p, Collectors.counting()));

        Integer bgColor = 0x00;
        Long maxCount = -1L;
        // faire du max
        for(Map.Entry<Integer, Long> counter : counters.entrySet()) {
            if(counter.getValue() > maxCount ) {
                maxCount = counter.getValue();
                bgColor = counter.getKey();
            }
        }
        return bgColor;
    }
    
    void sortToBrightness(int[] sample) {
        // TODO A OPTMISER PLUS TARD avec fastUtils par exemple
        Integer[] sampleInt = new Integer[sample.length];
        Arrays.parallelSetAll(sampleInt, i -> sample[i]);
        // les trier par Brightness, il faut que je les convertisse en HSV 
        Arrays.sort(sampleInt, new Comparator<Integer>() {

            @Override
            public int compare(Integer p1, Integer p2) {

                int firstcompare = Double.compare(ColorUtils.toBrightness(p1), ColorUtils.toBrightness(p2));
                if(firstcompare != 0) {
                    return firstcompare;
                } else {
                    int secondcompare = Double.compare(ColorUtils.toSaturation(p2), ColorUtils.toSaturation(p1));
                    if(secondcompare != 0) {
                        return secondcompare;
                    } else {
                        return Double.compare(abs(ColorUtils.toHue(p2)), abs(ColorUtils.toHue(p1)));
                    }
                }
            }

            @Override
            public boolean equals(Object o) {
                return false;
            }
        });
        
        // je remet dans l'autre sens. c'est moche et surement pas performant...
        Arrays.parallelSetAll(sample, i -> sampleInt[i]);
    }

    // TODO pourrait être fait en parallele
    void reduceTo6bits(int[] sample) {
        for(int i = 0; i < sample.length; i++) {
            int rgb = sample[i];
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;
            
            int nBBitsToReduceTo = 4;
            int shift = 8 - nBBitsToReduceTo;
            
            red = (red >> shift) << shift;   
            green = (green >> shift) << shift;  
            blue = (blue >> shift) << shift;    

            int newRgb = ((red&0x0ff)<<16)|((green&0x0ff)<<8)|(blue&0x0ff);
            sample[i] = newRgb;
            
        }
                
    }


    
   
    
    
    int[] toArray(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        
        int[] result = new int[width*height];
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++) {
                // on the last line there may be some pixels not affected.
                int index = ((width) * y) + x;
                // TODO à optimiser : passer par l'appel par array 
                result[index] = source.getRGB(x, y);
            }
        }
        return result;
    }
    
    
    
    int[] sample(BufferedImage source, float percentage) throws IOException {
        int width = source.getWidth();
        int height = source.getHeight();

        int pixelCount = width * height;
        int sampleSize = (int)(pixelCount * percentage);

        // TODO a voir si c'est utile d'aligner sur un nombre pair ...
        //      je sais plus pourquoi j'ai fait ça. Ca sert probablement pas a grand chose...
        sampleSize = (sampleSize) - sampleSize % 2;
        log.info("align to even number of pixels : {}", sampleSize);

        // randomize
        Random rand = new Random();

        int[] sample = new int[sampleSize];
        for(int i = 0; i < sampleSize; i++) {
            int randX = rand.nextInt(width);
            int randY = rand.nextInt(height);

            int randPixel = source.getRGB(randX, randY);
            sample[i] = randPixel;

        }
       
        return sample;
    }
    
    
     void displayToString(int[] pixels) {
         int width = (int) Math.sqrt(pixels.length);
         int height = pixels.length / width;
         int rest = pixels.length % width;   // il peut y avoir du rab
         if(rest != 0) {
             height++;
         }
         log.info("creating sample debug image of size : {} x {}", width, height);


         for(int y = 0; y < height; y++) {
             for(int x = 0; x<width; x++) {
                 // on the last line there may be some pixels not affected.
                 int index = (width * y) + x;
                 if(index < pixels.length) {
                     double[] hsb = ColorUtils.RGBtoHSB(pixels[index]);
                     log.info("{} {} - H {}  S {}  B {} ", toBinary(pixels[index]), toHexString(pixels[index]), hsb[0], hsb[1], hsb[2]);    
                 }
             }
             log.warn("----------------- NEW LINE {} ---- ", y+1 );
         }
     }
     
     String toBinary(int rgb) {
         return leftPad(Integer.toBinaryString((rgb & 0xffffff)), 24, '0');
     }
    
     void displaySampleImage(int[] pixels, String name) throws IOException {
         displaySampleImage(pixels, name, -1, -1);
     }
     
    void displaySampleImage(int[] pixels, String name, int width, int height) throws IOException {
        log.info("length : {}", pixels.length);
        
        if(width == -1) {
            // affiche sous forme de carré (à peu près)
            width = (int) Math.sqrt(pixels.length);    
        }
        if(height == -1) {
            height = pixels.length / width;
            log.info("width / height : {} / {}", width, height);
            int rest = pixels.length - (width * height);   // il peut y avoir du rab
            if(rest > 0) {
                height++;
            }    
        }
        
        log.info("creating sample debug image of size : {} x {}", width, height);
        BufferedImage img = new BufferedImage(width, height, TYPE_INT_RGB);
        for(int y = 0; y < height; y++) {
           for(int x = 0; x < width; x++) {
               // on the last line there may be some pixels not affected.
               int index = ((width) * y) + x;
               if(index >= pixels.length) {
                   img.setRGB(x, y, 0x01ff70);    // couleur flashy "de remplissage" pour être sûr de bien distinguer
               } else {
                   img.setRGB(x, y, pixels[index]);    
               }
               
            }
        }
        ImageIO.write(img, "png", new File(name));
    }

    /**
     * retourne le nombre de bits nécessaire pour un nombre de valeur
     * par exemple : 256valeurs -> 8 bits
     *               4  valeurs -> 2 bits
     *               16 valeurs -> 4 bits
     *               12 valeurs -> 4 bits 
     */
    int numberOfBitsNecessaryForNumberOfValues(int numberOfValues) {
        return (int)(Math.ceil(Math.log(numberOfValues * 1.0)/Math.log(2d)));
    }
}

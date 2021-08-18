package com.example.demo;

import com.example.demo.kmeans.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
//import java.awt.*;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.demo.PalettePNG.optimiseToPngImage;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.Integer.min;
import static java.lang.Integer.toHexString;
import static java.lang.Math.abs;
import static java.lang.StrictMath.max;
import static javax.imageio.ImageWriteParam.MODE_COPY_FROM_METADATA;
import static org.apache.commons.lang3.StringUtils.leftPad;

/**
 * Tentative de reproduction de NoteShrink en Java avec en plus un algorithme de Silhouette pour essayer 
 * de trouver une bonne valeur de K.
 * 
 * Ca marche, mais c'est trop variable d'un run à l'autre. et pas très efficient. proposant parfois 4k ou 18k..
 * Augmenter la taille de l'échantillon ne change pas grand chose... :-(
 * 
 * Du coup ca marche bien (ISO noteshrink.py), sans la partie variable de Silhouette
 * 
 * Serait ptet mieux d'utiliser du Kmeans++ -> Apache Commons Math le propose
 * 
 */
@Slf4j
public class NoteShrink {
    
    
    public static boolean DEBUG = false;
    
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
        float samplePercentage = 0.10f;   // 5% 
                
        
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
        
        // prépare la liste de Record
        List<Record> records = new ArrayList<>(foregroundSample.length);
        for(int i = 0; i < foregroundSample.length; i++ ) {
            records.add(rgbToRecord(foregroundSample[i]));
        }
        
        // kmeans !!
        // voir https://github.com/eugenp/tutorials/blob/master/algorithms-miscellaneous-3/src/main/java/com/baeldung/algorithms/kmeans/LastFm.java
        // K-1 car j'enleve le blanc
        // double diffWithLast = 0;
        
        /*
            La méthode Elbow est intéressante car elle permet de visuellement avoir une idée du nombre optimal, 
            Mais la meilleure valeur n'est pas évidente.
            
            https://en.wikipedia.org/wiki/Elbow_method_(clustering)
            https://towardsdatascience.com/clustering-metrics-better-than-the-elbow-method-6926e1f723a6 
            https://www.kdnuggets.com/2017/05/must-know-most-useful-number-clusters.html
            https://scikit-learn.org/stable/auto_examples/cluster/plot_kmeans_silhouette_analysis.html
            https://en.wikipedia.org/wiki/Silhouette_(clustering)
            https://towardsdatascience.com/silhouette-method-better-than-elbow-method-to-find-optimal-clusters-378d62ff6891
            
            
            https://scikit-learn.org/stable/modules/clustering.html#silhouette-coefficient
        
         */
        Map<Integer, Double> meanSilhouettesForK = new HashMap<>();
        double maxMeanSilouette = 0d;
        
        for(int i = 4; i < 32; i++) {
            System.out.println("== For K = " + i);
            Map<Centroid, List<Record>> clusters = KMeans.fit(records, i-1, new EuclideanDistance(), maxClusterIterations);
            Set<Centroid> centroids = clusters.keySet();
            // le background sera par contre bien présent dans la palette finale
            int[] palette = new int[clusters.size()+1];
            int paletteIdx = 0;
            // ajoute le bgColor
            palette[paletteIdx++] = backgroundColor;
            for(Centroid centroid : centroids) {
                palette[paletteIdx++] = centroidToRgb(centroid);
            }

            // silhouette
            // calcule la distance moyenne du centre du centroid avec tous ses membres.
            double sumSilhouette = 0d;
            
            
            EuclideanDistance distance = new EuclideanDistance();
            for(Map.Entry<Centroid, List<Record>> cluster : clusters.entrySet()) {
                
                Centroid centroid = cluster.getKey();
                List<Record> cRecords = cluster.getValue();
                // distance moyenne du centroid avec tous ses membres
                double sumA = 0d;
                for(Record record : cRecords) {
                    // normalement c'est pas le centre, c'est 1 sample au hasard dans le cluster
                    // en fait n'importe quel point dans la zone convexe autour du cluster fonctionne
                    // par simplicité, on prend le centre du centroid.
                    sumA += distance.calculate(centroid.getCoordinates(), record.getFeatures());
                }
                double meanA = sumA / cRecords.size();
                
                // distance moyenne du centroid avec tous les membres du centroid le plus proche.
                Centroid nextClosestCentroid = null;
                double minDistance = Double.MAX_VALUE;
                for(Centroid nextCentroid : centroids) {
                    if(nextCentroid == centroid)
                        continue;
                    double centroidDist = distance.calculate(centroid.getCoordinates(), nextCentroid.getCoordinates());
                    if(centroidDist < minDistance) {
                        nextClosestCentroid = nextCentroid;
                        minDistance = centroidDist;
                    }
                }
                
                // maintenant, effectue la distance entre ce même point et tous les
                List<Record> nextClosestCentroidRecords = clusters.get(nextClosestCentroid);
                double sumB = 0;
                for(Record record : nextClosestCentroidRecords) {
                    sumB += distance.calculate(centroid.getCoordinates(), record.getFeatures());
                }
                double meanB = sumB / nextClosestCentroidRecords.size();
                
                double silhouetteCoefficient = (meanB - meanA) / max(meanA, meanB);

//                System.out.println("silhouette coef : " + silhouetteCoefficient);
                sumSilhouette += silhouetteCoefficient;
                
            }

            double meanSilouetteForK = sumSilhouette / ((clusters.size()) * 1d);
            log.info("for K : {}    cluster size : {}   silouette moyenne : {}", i, clusters.size(), meanSilouetteForK);

            meanSilhouettesForK.put(i, meanSilouetteForK);
            // on peut se retrouver dans des situations comme expliqués ici : 
            // https://scikit-learn.org/stable/auto_examples/cluster/plot_kmeans_silhouette_analysis.html
            // ou ce n'est pas forcément le plus haut score qui est le mieux
            if(meanSilouetteForK > maxMeanSilouette ) {
                maxMeanSilouette = meanSilouetteForK;
            }
        }

        int optimalK = 0;
        double epsilon = 0.05d;   // a adapter ?
        for(int i = 4; i < 32; i++) {
            double current = meanSilhouettesForK.get(i);
            // si 2 valeurs sont assez proches, on privilégie la valeur de K la plus grande
            if(Math.abs(current - maxMeanSilouette) < epsilon) {
               optimalK = i;
            }
        }

      
        log.info("recomputing Centroids with optimal K value of {}", optimalK);

        Map<Centroid, List<Record>> clusters = KMeans.fit(records, optimalK-1, new EuclideanDistance(), maxClusterIterations);
        Set<Centroid> centroids = clusters.keySet();
        // le background sera par contre bien présent dans la palette finale
        int[] palette = new int[clusters.size()+1];
        int paletteIdx = 0;
        // ajoute le bgColor
        palette[paletteIdx++] = backgroundColor;
        for(Centroid centroid : centroids) {
            palette[paletteIdx++] = centroidToRgb(centroid);
        }
    
        if(DEBUG) displaySampleImage(palette, "palette.png");

        // Applique les centroid sur tous les pixels de l'image finale
        float[] bgHSB = toHSB(backgroundColor);
        int[] sourceArray = toArray(source);
        for(int i = 0; i < sourceArray.length; i++) {
            float[] currentHSB = toHSB(sourceArray[i]);
            float saturationDiff = Math.abs(bgHSB[1] - currentHSB[1]);
            float brightnessDiff = Math.abs(bgHSB[2] - currentHSB[2]);
            if(saturationDiff < foregroundSaturationDiff && brightnessDiff < foregroundBrightnessDiff) {
                // toutes les pixels qui ne se différencients pas assez du background auront tous 
                // la meme couleur
                sourceArray[i] = backgroundColor;
            } else {
                Centroid closestCentroid = vq(centroids, rgbToRecord(sourceArray[i]));
                sourceArray[i] = centroidToRgb(closestCentroid);
            }
        }
               
        // debugging
        displaySampleImage(sourceArray, "out_before_write.png", source.getWidth(), source.getHeight());
        BufferedImage out = optimiseToPngImage(sourceArray, palette, source.getWidth(), source.getHeight());
        ImageIO.write( out, "PNG", new File("out.png") );  
 
    }

    Record rgbToRecord(int value) {
        Map<String, Double> features = new HashMap<>(3);
        int rgb[] = intToRGB(value);
        features.put("red", rgb[0] * 1.0);
        features.put("green", rgb[1] * 1.0);
        features.put("blue", rgb[2] * 1.0);
        return new Record(features);
    }
    
    int centroidToRgb(Centroid centroid) {
        int red = centroid.getCoordinates().get("red").intValue();
        int green = centroid.getCoordinates().get("green").intValue();
        int blue = centroid.getCoordinates().get("blue").intValue();
        return rgbToInt(red, green, blue);
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
    
    
         
    public static Centroid vq(Set<Centroid> centroids, Record observation) {
        // TODO Surement à déplacer dans un Utils dédié
        Distance distance = new EuclideanDistance();
        double min = Double.MAX_VALUE;
        Centroid closestCentroid = null;
        for (Centroid centroid : centroids) {
            double d = distance.calculate(centroid.getCoordinates(), observation.getFeatures());
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
        float[] bgHSB = toHSB(backgroundColor);
        for(int i = 0; i < image.length; i++) {

            float[] sampleHSB = toHSB(image[i]);

            float saturationDiff = Math.abs(bgHSB[1] - sampleHSB[1]);
            float brightnessDiff = Math.abs(bgHSB[2] - sampleHSB[2]);

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

                int firstcompare = Float.compare(toBrightness(p1), toBrightness(p2));
                if(firstcompare != 0) {
                    return firstcompare;
                } else {
                    int secondcompare = Float.compare(toSaturation(p2), toSaturation(p1));
                    if(secondcompare != 0) {
                        return secondcompare;
                    } else {
                        return Float.compare(abs(toHue(p2)), abs(toHue(p1)));
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


    float[] toHSB(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return Color.RGBtoHSB(red, green, blue, null);
    }
    
    float toBrightness(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        return hsb[2];
    }

    float toSaturation(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        return hsb[1];
    }
    float toHue(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        return hsb[0];
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
                     float[] hsb = toHSB(pixels[index]);
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

package com.example.demo;

/**
 * adapté depuis https://github.com/NSLog0/java-image-processing-algorithm
 */

public class ThresholdOptimized {

    /**
     * 
     * @param image int-array of RGB values 0x00RRGGBB
     * @return an int-array of 2 colors 0x00000000 for black pixel
     *                                  0x00ffffff for white pixel
     */
    public static void applyInPlace(double threshold, int[] image, int width, int height) {
        int _r, p, r, g, b;
       
       
        
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {

                // Get pixels
                r = image[i*width+j];
                r = ((r >> 16) & 0xff);

                if (r > threshold) {
                    p = 255;
                } else {
                    p = 0;
                }
                p = (p << 16) | (p << 8) | (p);
                // in-place operation
                image[i*width+j] = p;

            }
        }
    }
    
    

    public static int otsuTreshold(int[] image, int width, int height) {
        int _histogram[] = Histogram.histogtam(image, width, height);

        int total = width * height;
        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * _histogram[i];
        }
        float sum_bg = 0;
        int wight_bg = 0, wight_fg = 0;

        float varMax = 0;
        int threshold = 0;

        for (int i = 0; i < 256; i++) {
            wight_bg += _histogram[i];
            if (wight_bg == 0) {
                continue;
            }
            wight_fg = total - wight_bg;

            if (wight_fg == 0) {
                break;
            }

            sum_bg += (float) (i * _histogram[i]);
            float mean_bg = sum_bg / wight_bg;
            float mean_fg = (sum - sum_bg) / wight_fg;
            float varBetween = (float) wight_bg * (float) wight_fg * (mean_bg - mean_fg) * (mean_bg - mean_fg);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = i;
            }
        }
        return threshold;
    }


    static class Histogram {

        public static int[] histogtam(int[] image, int width, int height) {
            int[] interval = new int[256];
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int p = image[i*width+j];
                    int r = (p >> 16) & 0xff;
                    interval[r]++;
                }
            }
            return interval;
        }
    }

  
}





package com.example.demo;

import java.awt.image.BufferedImage;

/**
 * adapté depuis https://github.com/NSLog0/java-image-processing-algorithm
 */

public class Threshold {

    public static BufferedImage apply(BufferedImage _image) {
        int _r, p, r, g, b;
        double threshold = otsuTreshold(_image);
//        BufferedImage imageOutput = new BufferedImage(_image.getWidth(), _image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);   // Set initial BufferedImage
        BufferedImage imageOutput = new BufferedImage(_image.getWidth(), _image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);   // Set initial BufferedImage
        for (int i = 0; i < _image.getWidth(); i++) {
            for (int j = 0; j < _image.getHeight(); j++) {

                // Get pixels
                r = RGB.getRGBW(_image, i, j);
                r = ((r >> 16) & 0xff);

                if (r > threshold) {
                    p = 255;
                } else {
                    p = 0;
                }
                p = (p << 16) | (p << 8) | (p);
                imageOutput.setRGB(i, j, p);

            }
        }

        return imageOutput;
    }

    public static int otsuTreshold(BufferedImage _image) {
        int _histogram[] = Histogram.histogtam(_image);

        int total = _image.getWidth() * _image.getHeight();
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
}


class Histogram {

    public static int[] histogtam(BufferedImage _image) {
        int interval[] = new int[256];
        for (int i = 0; i < _image.getWidth(); i++) {
            for (int j = 0; j < _image.getHeight(); j++) {
                int p = RGB.getRGBW(_image, i, j);
                int r = (p >> 16) & 0xff;
                interval[r]++;
            }

        }
        return interval;

    }

}

class RGB {

    public static int getRGBW(BufferedImage image, int i, int j) {
        int width = image.getWidth();
        int height = image.getHeight();
        i = Math.max(0, Math.min(width - 1, i));
        j = Math.max(0, Math.min(height - 1, j));
        return image.getRGB(i, j);
    }


    public static int getRGBH(BufferedImage image, int i, int j) {
        int width = image.getWidth();
        int height = image.getHeight();
        j = Math.max(0, Math.min(width - 1, j));
        i = Math.max(0, Math.min(height - 1, i));
        return image.getRGB(i, j);
    }
}

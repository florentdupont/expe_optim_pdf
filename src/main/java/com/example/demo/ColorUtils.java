package com.example.demo;


import java.awt.*;

/**
 * Honteusement copié de Android
 * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/com/android/internal/graphics/ColorUtils.java
 */
public final class ColorUtils {

    private static final double XYZ_WHITE_REFERENCE_X = 95.047;
    private static final double XYZ_WHITE_REFERENCE_Y = 100;
    private static final double XYZ_WHITE_REFERENCE_Z = 108.883;
    private static final double XYZ_EPSILON = 0.008856;
    private static final double XYZ_KAPPA = 903.3;

    private static final ThreadLocal<double[]> TEMP_ARRAY = new ThreadLocal<>();


    /**
     * Convert RGB components to its CIE Lab representative components.
     *
     * <ul>
     * <li>outLab[0] is L [0 ...1)</li>
     * <li>outLab[1] is a [-128...127)</li>
     * <li>outLab[2] is b [-128...127)</li>
     * </ul>
     *
     * @param r      red component value [0..255]
     * @param g      green component value [0..255]
     * @param b      blue component value [0..255]
     * @param outLab 3-element array which holds the resulting LAB components
     */
    public static void RGBToLAB(int r,
                                int g, 
                                int b,
                                double[] outLab) {
        // First we convert RGB to XYZ
        RGBToXYZ(r, g, b, outLab);
        // outLab now contains XYZ
        XYZToLAB(outLab[0], outLab[1], outLab[2], outLab);
        // outLab now contains LAB representation
    }

    
    /**
     * Convert RGB components to its CIE XYZ representative components.
     *
     * <p>The resulting XYZ representation will use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * <ul>
     * <li>outXyz[0] is X [0 ...95.047)</li>
     * <li>outXyz[1] is Y [0...100)</li>
     * <li>outXyz[2] is Z [0...108.883)</li>
     * </ul>
     *
     * @param r      red component value [0..255]
     * @param g      green component value [0..255]
     * @param b      blue component value [0..255]
     * @param outXyz 3-element array which holds the resulting XYZ components
     */
    public static void RGBToXYZ(int r,
                                int g, 
                                int b,
                                double[] outXyz) {
        if (outXyz.length != 3) {
            throw new IllegalArgumentException("outXyz must have a length of 3.");
        }
        double sr = r / 255.0;
        sr = sr < 0.04045 ? sr / 12.92 : Math.pow((sr + 0.055) / 1.055, 2.4);
        double sg = g / 255.0;
        sg = sg < 0.04045 ? sg / 12.92 : Math.pow((sg + 0.055) / 1.055, 2.4);
        double sb = b / 255.0;
        sb = sb < 0.04045 ? sb / 12.92 : Math.pow((sb + 0.055) / 1.055, 2.4);
        outXyz[0] = 100 * (sr * 0.4124 + sg * 0.3576 + sb * 0.1805);
        outXyz[1] = 100 * (sr * 0.2126 + sg * 0.7152 + sb * 0.0722);
        outXyz[2] = 100 * (sr * 0.0193 + sg * 0.1192 + sb * 0.9505);
    }

    /**
     * Converts a color from CIE XYZ to CIE Lab representation.
     *
     * <p>This method expects the XYZ representation to use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * <ul>
     * <li>outLab[0] is L [0 ...1)</li>
     * <li>outLab[1] is a [-128...127)</li>
     * <li>outLab[2] is b [-128...127)</li>
     * </ul>
     *
     * @param x      X component value [0...95.047)
     * @param y      Y component value [0...100)
     * @param z      Z component value [0...108.883)
     * @param outLab 3-element array which holds the resulting Lab components
     */
    public static void XYZToLAB(double x,
                                double y,
                                double z,
                                double[] outLab) {
        if (outLab.length != 3) {
            throw new IllegalArgumentException("outLab must have a length of 3.");
        }
        x = pivotXyzComponent(x / XYZ_WHITE_REFERENCE_X);
        y = pivotXyzComponent(y / XYZ_WHITE_REFERENCE_Y);
        z = pivotXyzComponent(z / XYZ_WHITE_REFERENCE_Z);
        outLab[0] = Math.max(0, 116 * y - 16);
        outLab[1] = 500 * (x - y);
        outLab[2] = 200 * (y - z);
    }


    /**
     * Converts a color from CIE Lab to its RGB representation.
     *
     * @param l L component value [0...100]
     * @param a A component value [-128...127]
     * @param b B component value [-128...127]
     * @return int containing the RGB representation
     */
    public static int LABToRGB(final double l,
                                 final double a,
                                 final double b) {
        final double[] result = getTempDouble3Array();
        LABToXYZ(l, a, b, result);
        return XYZToColor(result[0], result[1], result[2]);
    }

    /**
     * Converts a color from CIE Lab to CIE XYZ representation.
     *
     * <p>The resulting XYZ representation will use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * <ul>
     * <li>outXyz[0] is X [0 ...95.047)</li>
     * <li>outXyz[1] is Y [0...100)</li>
     * <li>outXyz[2] is Z [0...108.883)</li>
     * </ul>
     *
     * @param l      L component value [0...100)
     * @param a      A component value [-128...127)
     * @param b      B component value [-128...127)
     * @param outXyz 3-element array which holds the resulting XYZ components
     */
    public static void LABToXYZ(final double l,
                                final double a,
                                final double b,
                                double[] outXyz) {
        final double fy = (l + 16) / 116;
        final double fx = a / 500 + fy;
        final double fz = fy - b / 200;
        double tmp = Math.pow(fx, 3);
        final double xr = tmp > XYZ_EPSILON ? tmp : (116 * fx - 16) / XYZ_KAPPA;
        final double yr = l > XYZ_KAPPA * XYZ_EPSILON ? Math.pow(fy, 3) : l / XYZ_KAPPA;
        tmp = Math.pow(fz, 3);
        final double zr = tmp > XYZ_EPSILON ? tmp : (116 * fz - 16) / XYZ_KAPPA;
        outXyz[0] = xr * XYZ_WHITE_REFERENCE_X;
        outXyz[1] = yr * XYZ_WHITE_REFERENCE_Y;
        outXyz[2] = zr * XYZ_WHITE_REFERENCE_Z;
    }

    /**
     * Converts a color from CIE XYZ to its RGB representation.
     *
     * <p>This method expects the XYZ representation to use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * @param x X component value [0...95.047)
     * @param y Y component value [0...100)
     * @param z Z component value [0...108.883)
     * @return int containing the RGB representation
     */
    public static int XYZToColor(double x,
                                 double y,
                                 double z) {
        double r = (x * 3.2406 + y * -1.5372 + z * -0.4986) / 100;
        double g = (x * -0.9689 + y * 1.8758 + z * 0.0415) / 100;
        double b = (x * 0.0557 + y * -0.2040 + z * 1.0570) / 100;
        r = r > 0.0031308 ? 1.055 * Math.pow(r, 1 / 2.4) - 0.055 : 12.92 * r;
        g = g > 0.0031308 ? 1.055 * Math.pow(g, 1 / 2.4) - 0.055 : 12.92 * g;
        b = b > 0.0031308 ? 1.055 * Math.pow(b, 1 / 2.4) - 0.055 : 12.92 * b;
        return rgb(
                constrain((int) Math.round(r * 255), 0, 255),
                constrain((int) Math.round(g * 255), 0, 255),
                constrain((int) Math.round(b * 255), 0, 255));
    }

    private static int constrain(int amount, int low, int high) {
        return amount < low ? low : (amount > high ? high : amount);
    }

    private static double pivotXyzComponent(double component) {
        return component > XYZ_EPSILON
                ? Math.pow(component, 1 / 3.0)
                : (XYZ_KAPPA * component + 16) / 116;
    }


    /**
     * Return a color-int from red, green, blue components.
     * The alpha component is implicitly 255 (fully opaque).
     * These component values should be \([0..255]\), but there is no
     * range check performed, so if they are out of range, the
     * returned color is undefined.
     *
     * @param red  Red component \([0..255]\) of the color
     * @param green Green component \([0..255]\) of the color
     * @param blue  Blue component \([0..255]\) of the color
     */
    public static int rgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private static double[] getTempDouble3Array() {
        double[] result = TEMP_ARRAY.get();
        if (result == null) {
            result = new double[3];
            TEMP_ARRAY.set(result);
        }
        return result;
    }


    public static double[] RGBtoHSB(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        return new double[] { hsb[0], hsb[1], hsb[2] };
    }

    public static int HSBtoRGB(double h, double s, double b) {
        return Color.HSBtoRGB((float)h,(float)s,(float)b);
    }


    public static double toBrightness(int rgb) {
        double[] hsb = RGBtoHSB(rgb);
        return hsb[2];
    }

    public static double toSaturation(int rgb) {
        double[] hsb = RGBtoHSB(rgb);
        return hsb[1];
    }

    public static double toHue(int rgb) {
        double[] hsb = RGBtoHSB(rgb);
        return hsb[0];
    }
}

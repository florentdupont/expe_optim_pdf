package com.example.demo;

import com.jhlabs.image.PointFilter;

public class AtscGrayscaleFilter extends PointFilter {

	public AtscGrayscaleFilter() {
		canFilterIndexColorModel = true;
	}

	public int filterRGB(int x, int y, int rgb) {
		int a = rgb & 0xff000000;
		int r = (rgb >> 16) & 0xff;
		int g = (rgb >> 8) & 0xff;
		int b = rgb & 0xff;
//		rgb = (r + g + b) / 3;	// simple average
//		rgb = (r * 77 + g * 151 + b * 28) >> 8;	// NTSC luma
		rgb = (r * 54 + g * 182 + b * 19) >> 8;	// ATSC luma
		return a | (rgb << 16) | (rgb << 8) | rgb;
	}

	public String toString() {
		return "Colors/Grayscale";
	}

}
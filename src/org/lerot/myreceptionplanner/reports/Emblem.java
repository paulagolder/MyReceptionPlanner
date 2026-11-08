package org.lerot.myreceptionplanner.reports;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.FloatSize;
import com.github.weisj.jsvg.view.ViewBox;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/*
 * The occasion's emblem, an SVG picture (entwined hearts, bells, the couple's
 * own design), drawn on the screen and into PDFs as lines and shapes, so it
 * stays sharp at any size; its text is drawn as outlines, so it looks the
 * same whatever fonts the reader has.
 */
public final class Emblem
{
	private Emblem() {}

	public static final String[] SAMPLES = { "hearts", "bells" };

	// the picture, or null if the text is not an SVG that can be drawn
	public static SVGDocument parse(String svg)
	{
		if (svg == null || svg.isBlank()) return null;
		try
		{
			return new SVGLoader().load(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), null,
					LoaderContext.createDefault());
		} catch (RuntimeException e)
		{
			return null;
		}
	}

	// one of the samples that come with the app, "hearts" or "bells"
	public static String sample(String name) throws IOException
	{
		try (InputStream in = Emblem.class.getResourceAsStream("/org/lerot/myreceptionplanner/emblems/" + name + ".svg"))
		{
			if (in == null) throw new IOException("No sample emblem " + name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	// its width for a given height, keeping its shape
	public static float widthFor(SVGDocument doc, float height)
	{
		FloatSize s = doc.size();
		return s.height <= 0 ? height : height * s.width / s.height;
	}

	// drawn to fit the box, keeping its shape, centred
	public static void draw(Graphics2D g, SVGDocument doc, float x, float y, float w, float h)
	{
		FloatSize s = doc.size();
		float scale = Math.min(w / Math.max(1, s.width), h / Math.max(1, s.height));
		float dw = s.width * scale, dh = s.height * scale;
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		doc.render(null, g2, new ViewBox(x + (w - dw) / 2, y + (h - dh) / 2, dw, dh));
		g2.dispose();
	}

	// for a PDF: the picture this high, as an image to place or add
	public static Image pdfImage(PdfContentByte cb, SVGDocument doc, float height) throws BadElementException
	{
		float w = widthFor(doc, height);
		PdfTemplate t = cb.createTemplate(w, height);
		Graphics2D g = new PdfGraphics2D(t, w, height, true);  // text as outlines
		draw(g, doc, 0, 0, w, height);
		g.dispose();
		return Image.getInstance(t);
	}
}

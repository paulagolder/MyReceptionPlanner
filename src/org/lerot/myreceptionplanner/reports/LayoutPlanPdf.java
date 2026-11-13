package org.lerot.myreceptionplanner.reports;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import org.lerot.myreceptionplanner.model.Altar;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Pew;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Table;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/*
 * An event's layout for the people setting out the room: the tables drawn to
 * scale where they stand, each chair in use as a circle, each table's name
 * or number in its middle; no guests, no seat numbers, no chairs turned off.
 * Below, a scale bar and what is to be set out; at the top right whether the
 * layout is approved (see Event.getApproval) or still a draft.
 */
public class LayoutPlanPdf
{
	private static final float MARGIN = 36;
	private static final float PT_PER_CM = 72 / 2.54f;  // on the paper
	// scales a ruler can be used at, 1:n
	private static final int[] SCALES = { 20, 25, 50, 75, 100, 125, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000 };
	private static final BaseColor EDGE = new BaseColor(0x5A, 0x5A, 0x5E);
	private static final BaseColor TOP = new BaseColor(0xF4, 0xF1, 0xEA);
	private static final BaseColor HEAD = new BaseColor(0xB8, 0x86, 0x0B);
	private static final BaseColor TEXT = new BaseColor(0x2B, 0x2B, 0x2B);
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor APPROVED = new BaseColor(0x2E, 0x7D, 0x32);
	private static final BaseColor DRAFT = new BaseColor(0xC6, 0x28, 0x28);

	// "12 tables, 118 chairs; 6 pews; an altar / speakers' table": what is to be set out
	public static String counts(Plan plan)
	{
		int tables = 0, chairs = 0, pews = 0, altars = 0;
		for (Table t : plan.tables())
		{
			if (t instanceof Altar) altars++;
			else if (t instanceof Pew) pews++;
			else
			{
				tables++;
				chairs += t.usablePlaces();
			}
		}
		List<String> parts = new ArrayList<>();
		if (tables > 0) parts.add(tables + (tables == 1 ? " table, " : " tables, ") + chairs + (chairs == 1 ? " chair" : " chairs"));
		if (pews > 0) parts.add(pews + (pews == 1 ? " pew" : " pews"));
		if (altars > 0) parts.add(altars == 1 ? "an altar / speakers' table" : altars + " altars / speakers' tables");
		return String.join("; ", parts);
	}

	// the stamp: approved, by whom and when, or a draft
	public static String stamp(Event event)
	{
		return event.isLayoutApproved() ? "Approved " + event.getApproval().text()
				: event.layoutChangedSinceApproval() ? "Draft: changed since approved" : "Draft: not approved";
	}

	// the 1:n a ruler can be used at that fits floor cm into so many points
	static int scaleFor(double floorCm, double points)
	{
		double n = floorCm * PT_PER_CM / points;
		for (int s : SCALES)
			if (s >= n) return s;
		return (int) Math.ceil(n / 1000) * 1000;
	}

	public static void write(Occasion occasion, Event event, Rectangle page, Path file) throws IOException
	{
		Plan plan = event.getPlan();
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(page);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				doc.addTitle(occasion.getName() + " – " + event.getName() + " – layout");
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				PdfContentByte cb = writer.getDirectContent();

				float titleSize = 22, subSize = 14;
				float top = page.getHeight() - MARGIN;
				var picture = Emblem.parse(occasion.getEmblem());
				if (picture != null)
				{
					com.itextpdf.text.Image img = Emblem.pdfImage(cb, picture, titleSize + subSize * 2.4f);
					img.setAbsolutePosition(MARGIN, top - img.getHeight());
					cb.addImage(img);
				}
				text(cb, bold, titleSize, TEXT, occasion.heading(), page.getWidth() / 2, top - titleSize, PdfContentByte.ALIGN_CENTER);
				text(cb, regular, subSize, GREY, occasion.headingOf(event) + "  ·  Layout", page.getWidth() / 2,
						top - titleSize - subSize * 1.6f, PdfContentByte.ALIGN_CENTER);
				text(cb, bold, 11, event.isLayoutApproved() ? APPROVED : DRAFT, stamp(event),
						page.getWidth() - MARGIN, top - 11, PdfContentByte.ALIGN_RIGHT);

				float footer = 34;
				float areaTop = top - titleSize - subSize * 3.2f;
				float areaWidth = page.getWidth() - 2 * MARGIN, areaHeight = areaTop - MARGIN - footer;

				// the floor the tables and their chairs take, a little round it
				Rectangle2D floor = null;
				for (Table t : plan.tables())
				{
					Rectangle2D b = t.footprint().getBounds2D();
					floor = floor == null ? b : floor.createUnion(b);
				}
				int scale = 100;
				if (floor != null)
				{
					floor = new Rectangle2D.Double(floor.getX() - 20, floor.getY() - 20, floor.getWidth() + 40, floor.getHeight() + 40);
					scale = Math.max(scaleFor(floor.getWidth(), areaWidth), scaleFor(floor.getHeight(), areaHeight));
					float k = PT_PER_CM / scale;  // points to a floor cm
					// centred in the area; the floor's y runs down the page
					float left = MARGIN + (areaWidth - (float) floor.getWidth() * k) / 2;
					float upper = areaTop - (areaHeight - (float) floor.getHeight() * k) / 2;
					AffineTransform toPage = new AffineTransform(k, 0, 0, -k, left - floor.getX() * k, upper + floor.getY() * k);
					float labelSize = Math.max(6, Math.min(12, 22 * k));
					for (Table t : plan.tables())
						drawTable(cb, plan, t, toPage, k, bold, labelSize);
				}

				// the scale bar and what is to be set out
				float y = MARGIN + 8;
				float metre = 100 * PT_PER_CM / scale;
				int metres = Math.max(1, (int) Math.floor(areaWidth / 3 / metre));
				metres = metres >= 10 ? 10 : metres >= 5 ? 5 : metres >= 2 ? 2 : 1;
				cb.saveState();
				cb.setColorStroke(EDGE);
				cb.setLineWidth(1);
				cb.moveTo(MARGIN, y);
				cb.lineTo(MARGIN + metres * metre, y);
				for (int m = 0; m <= metres; m++)
				{
					cb.moveTo(MARGIN + m * metre, y - 3);
					cb.lineTo(MARGIN + m * metre, y + 3);
				}
				cb.stroke();
				cb.restoreState();
				text(cb, regular, 8, GREY, metres + " m   ·   scale 1:" + scale + " on " + paperName(page),
						MARGIN + metres * metre + 8, y - 3, PdfContentByte.ALIGN_LEFT);
				text(cb, regular, 10, TEXT, counts(plan), page.getWidth() - MARGIN, y - 3, PdfContentByte.ALIGN_RIGHT);
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	private static void drawTable(PdfContentByte cb, Plan plan, Table t, AffineTransform toPage, float k,
			BaseFont bold, float labelSize)
	{
		cb.saveState();
		cb.setColorFill(TOP);
		cb.setColorStroke(t.isHead() ? HEAD : EDGE);
		cb.setLineWidth(t.isHead() ? 1.6f : 0.8f);
		path(cb, toPage.createTransformedShape(t.topOnFloor()));
		cb.fillStroke();
		if (!(t instanceof Pew))
		{
			cb.setColorFill(BaseColor.WHITE);
			cb.setColorStroke(EDGE);
			cb.setLineWidth(0.6f);
			for (int i = 0; i < t.places(); i++)
			{
				if (t.isDisabled(i)) continue;
				Point2D c = toPage.transform(t.seatOnFloor(i), null);
				cb.circle((float) c.getX(), (float) c.getY(), (float) Table.SEAT_RADIUS * k);
				cb.fillStroke();
			}
		}
		cb.restoreState();
		Point2D middle = toPage.transform(new Point2D.Double(t.getX(), t.getY()), null);
		text(cb, bold, labelSize, TEXT, plan.label(t), (float) middle.getX(), (float) middle.getY() - labelSize * 0.35f,
				PdfContentByte.ALIGN_CENTER);
	}

	// the shape as a path on the page, curves and all
	private static void path(PdfContentByte cb, Shape shape)
	{
		double[] c = new double[6];
		double x = 0, y = 0;
		for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next())
		{
			switch (it.currentSegment(c))
			{
				case PathIterator.SEG_MOVETO -> cb.moveTo((float) c[0], (float) c[1]);
				case PathIterator.SEG_LINETO -> cb.lineTo((float) c[0], (float) c[1]);
				case PathIterator.SEG_QUADTO ->
					// as a cubic: the control points two thirds of the way to the quadratic's
					cb.curveTo((float) (x + 2 * (c[0] - x) / 3), (float) (y + 2 * (c[1] - y) / 3),
							(float) (c[2] + 2 * (c[0] - c[2]) / 3), (float) (c[3] + 2 * (c[1] - c[3]) / 3),
							(float) c[2], (float) c[3]);
				case PathIterator.SEG_CUBICTO -> cb.curveTo((float) c[0], (float) c[1], (float) c[2], (float) c[3],
						(float) c[4], (float) c[5]);
				case PathIterator.SEG_CLOSE -> cb.closePath();
			}
			int seg = it.currentSegment(c);
			if (seg == PathIterator.SEG_MOVETO || seg == PathIterator.SEG_LINETO) { x = c[0]; y = c[1]; }
			else if (seg == PathIterator.SEG_QUADTO) { x = c[2]; y = c[3]; }
			else if (seg == PathIterator.SEG_CUBICTO) { x = c[4]; y = c[5]; }
		}
	}

	private static String paperName(Rectangle page)
	{
		float shorter = Math.min(page.getWidth(), page.getHeight());
		for (String name : new String[] { "A4", "A3", "A2", "A1", "A0" })
			if (Math.abs(com.itextpdf.text.PageSize.getRectangle(name).getWidth() - shorter) < 2) return name;
		return "this paper";
	}

	private static void text(PdfContentByte cb, BaseFont font, float size, BaseColor colour, String s,
			float x, float y, int align)
	{
		cb.beginText();
		cb.setFontAndSize(font, size);
		cb.setColorFill(colour);
		cb.showTextAligned(align, s, x, y, 0);
		cb.endText();
	}
}

package org.lerot.myreceptionplanner.reports;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Occasion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 * An event's table plan as a one-page PDF to print and put up: the occasion
 * and its date, the event with its place and time, then a panel for each
 * table where it stands in the room (see TablePlanLayout).
 */
public class TablePlanPdf
{
	private static final float MARGIN = 36;
	private static final BaseColor EDGE = new BaseColor(0x8A, 0x8A, 0x8E);
	private static final BaseColor HEAD = new BaseColor(0xB8, 0x86, 0x0B);
	private static final BaseColor TEXT = new BaseColor(0x2B, 0x2B, 0x2B);
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor BAND = new BaseColor(0xF4, 0xF1, 0xEA);

	// returns false if the plan only fitted with the type at its smallest
	public static boolean write(Occasion occasion, Event event, Rectangle page, Path file)
			throws IOException
	{
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont italic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(page);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				doc.addTitle(occasion.getName() + " – " + event.getName());
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				PdfContentByte cb = writer.getDirectContent();

				float titleSize = 22, subSize = 14;
				float top = page.getHeight() - MARGIN;
				var picture = Emblem.parse(occasion.getEmblem());
				if (picture != null)
				{
					// the emblem at the top left, as tall as the heading
					com.itextpdf.text.Image img = Emblem.pdfImage(cb, picture, titleSize + subSize * 2.4f);
					img.setAbsolutePosition(MARGIN, top - img.getHeight());
					cb.addImage(img);
				}
				text(cb, bold, titleSize, TEXT, occasion.heading(), page.getWidth() / 2, top - titleSize, true);
				text(cb, regular, subSize, GREY, occasion.headingOf(event), page.getWidth() / 2,
						top - titleSize - subSize * 1.6f, true);
				float areaTop = top - titleSize - subSize * 3.2f;
				float areaWidth = page.getWidth() - 2 * MARGIN;
				float areaHeight = areaTop - MARGIN;

				TablePlanLayout.Layout layout = TablePlanLayout.layout(event.getPlan(), event.getSeating(), event.isSeated(), areaWidth, areaHeight,
						(s, size, isBold) -> (isBold ? bold : regular).getWidthPoint(s, size));
				float f = layout.fontSize();
				for (TablePlanLayout.Panel p : layout.panels())
				{
					float x = MARGIN + (float) p.x();
					float yTop = areaTop - (float) p.y();
					float w = (float) p.width(), h = (float) p.height();
					float titleH = (float) (f * TablePlanLayout.TITLE * TablePlanLayout.LINE + TablePlanLayout.PAD + f * 0.2);

					// the panel, with a band for its title
					cb.saveState();
					cb.setColorFill(BAND);
					cb.rectangle(x, yTop - titleH, w, titleH);
					cb.fill();
					cb.setColorStroke(p.head() ? HEAD : EDGE);
					cb.setLineWidth(p.head() ? 1.8f : 0.8f);
					cb.roundRectangle(x, yTop - h, w, h, 4);
					cb.stroke();
					cb.setLineWidth(0.5f);
					cb.moveTo(x, yTop - titleH);
					cb.lineTo(x + w, yTop - titleH);
					cb.stroke();
					cb.restoreState();

					float panelTitle = (float) (f * TablePlanLayout.TITLE);
					float cx = x + w / 2;
					float baseline = yTop - (float) TablePlanLayout.PAD - panelTitle;
					text(cb, bold, panelTitle, TEXT, fit(bold, p.title(), panelTitle, w), cx, baseline, true);
					float y = yTop - titleH - f * (float) (TablePlanLayout.LINE - 0.15);
					for (TablePlanLayout.Line line : p.lines())
					{
						BaseFont font = line.placeholder() ? italic : regular;
						text(cb, font, f, line.placeholder() ? GREY : TEXT, fit(font, line.text(), f, w), cx, y, true);
						y -= f * TablePlanLayout.LINE;
					}
				}
				doc.close();
				return layout.fits();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	private static void text(PdfContentByte cb, BaseFont font, float size, BaseColor colour, String s,
			float x, float y, boolean centred)
	{
		cb.beginText();
		cb.setFontAndSize(font, size);
		cb.setColorFill(colour);
		cb.showTextAligned(centred ? PdfContentByte.ALIGN_CENTER : PdfContentByte.ALIGN_LEFT, s, x, y, 0);
		cb.endText();
	}

	// shortened with … if too wide for the panel
	private static String fit(BaseFont font, String s, float size, float panelWidth)
	{
		float room = panelWidth - 2 * (float) TablePlanLayout.PAD;
		if (font.getWidthPoint(s, size) <= room) return s;
		String t = s;
		while (t.length() > 1 && font.getWidthPoint(t + "…", size) > room)
			t = t.substring(0, t.length() - 1);
		return t.strip() + "…";
	}
}

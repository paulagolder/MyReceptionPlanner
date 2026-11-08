package org.lerot.myreceptionplanner.reports;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// The catering list (see CateringReport) as an A4 PDF for the caterers
public class CateringPdf
{
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor BAND = new BaseColor(0xF4, 0xF1, 0xEA);

	public static void write(CateringReport report, Path file) throws IOException
	{
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Font title = new Font(bold, 18), sub = new Font(regular, 12, Font.NORMAL, GREY),
					heading = new Font(bold, 13), head = new Font(bold, 10), cell = new Font(regular, 10),
					note = new Font(regular, 10, Font.NORMAL, GREY);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter.getInstance(doc, out);
				doc.addTitle(report.subtitle);
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				doc.add(new Paragraph(report.title, title));
				Paragraph p = new Paragraph(report.subtitle, sub);
				p.setSpacingAfter(10);
				doc.add(p);
				for (CateringReport.Section s : report.sections)
				{
					Paragraph h = new Paragraph(s.heading().replace("⚠ ", "! "), heading);
					h.setSpacingBefore(10);
					h.setSpacingAfter(4);
					doc.add(h);
					if (!s.rows().isEmpty())
					{
						int cols = s.columns().isEmpty() ? s.rows().get(0).size() : s.columns().size();
						PdfPTable t = new PdfPTable(cols);
						t.setWidthPercentage(100);
						t.setHorizontalAlignment(PdfPTable.ALIGN_LEFT);
						for (String c : s.columns())
						{
							PdfPCell pc = new PdfPCell(new Phrase(c, head));
							pc.setBackgroundColor(BAND);
							pc.setBorderColor(BAND);
							pc.setPadding(4);
							t.addCell(pc);
						}
						for (List<String> r : s.rows())
							for (String c : r)
							{
								PdfPCell pc = new PdfPCell(new Phrase(c, cell));
								pc.setBorderColor(BAND);
								pc.setPadding(4);
								t.addCell(pc);
							}
						doc.add(t);
					}
					if (!s.footnote().isEmpty())
					{
						Paragraph f = new Paragraph(s.footnote(), note);
						f.setSpacingBefore(3);
						doc.add(f);
					}
				}
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}
}

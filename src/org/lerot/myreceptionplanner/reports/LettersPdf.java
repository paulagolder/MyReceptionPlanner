package org.lerot.myreceptionplanner.reports;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Utilities;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Messages;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * Messages on paper: letters, one A4 page for each party, from the occasion's
 * From and Reply to, dated, addressed to the lead; and address labels, 21 to
 * an A4 sheet (3 across, 7 down, 63.5 × 38.1 mm, as L7160 sheets).
 */
public class LettersPdf
{
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);

	// the party with the message written for it
	public record Letter(Party party, Messages.Message message) {}

	public static void letters(Occasion occasion, List<Letter> letters, Path file) throws IOException
	{
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Font text = new Font(regular, 11), strong = new Font(bold, 11), small = new Font(regular, 10, Font.NORMAL, GREY);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(PageSize.A4, 64, 64, 56, 56);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				var picture = Emblem.parse(occasion.getEmblem());
				doc.addTitle(occasion.getName());
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				// who it is from keeps clear of the emblem, wrapping rather than running into it
				float clear = picture == null ? 0 : Emblem.widthFor(picture, 64) + 16;
				boolean first = true;
				for (Letter l : letters)
				{
					if (!first) doc.newPage();
					first = false;
					if (picture != null)
					{
						// the emblem at the top left, beside who the letter is from
						com.itextpdf.text.Image img = Emblem.pdfImage(writer.getDirectContent(), picture, 64);
						img.setAbsolutePosition(doc.left(), doc.top() - img.getHeight() + 10);
						writer.getDirectContent().addImage(img);
					}
					Paragraph from = new Paragraph(occasion.getSender(), strong);
					from.setAlignment(Element.ALIGN_RIGHT);
					from.setIndentationLeft(clear);
					doc.add(from);
					if (!occasion.getReplyTo().isEmpty())
					{
						Paragraph reply = new Paragraph(occasion.getReplyTo(), small);
						reply.setAlignment(Element.ALIGN_RIGHT);
						reply.setIndentationLeft(clear);
						doc.add(reply);
					}
					Paragraph date = new Paragraph(Occasion.DATE.format(LocalDate.now()), small);
					date.setAlignment(Element.ALIGN_RIGHT);
					date.setSpacingAfter(24);
					doc.add(date);
					for (String line : addressLines(l.party()))
						doc.add(new Paragraph(line, text));
					Paragraph subject = new Paragraph(l.message().subject(), strong);
					subject.setSpacingBefore(28);
					subject.setSpacingAfter(12);
					doc.add(subject);
					for (String line : l.message().body().split("\n", -1))
					{
						Paragraph p = new Paragraph(line.isBlank() ? " " : line, text);
						p.setLeading(15);
						doc.add(p);
					}
				}
				if (letters.isEmpty()) doc.add(new Paragraph("No letters.", text));
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	public static void labels(List<Party> parties, Path file) throws IOException
	{
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Font text = new Font(regular, 10);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(PageSize.A4, 0, 0, 0, 0);
			float w = Utilities.millimetersToPoints(63.5f), h = Utilities.millimetersToPoints(38.1f);
			float left = Utilities.millimetersToPoints(7.25f), top = Utilities.millimetersToPoints(15.15f);
			float pitch = Utilities.millimetersToPoints(66.04f), pad = Utilities.millimetersToPoints(4);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				doc.open();
				PdfContentByte cb = writer.getDirectContent();
				int i = 0;
				for (Party p : parties)
				{
					if (i > 0 && i % 21 == 0) doc.newPage();
					int at = i % 21, col = at % 3, row = at / 3;
					float x = left + col * pitch, yTop = PageSize.A4.getHeight() - top - row * h;
					ColumnText ct = new ColumnText(cb);
					ct.setSimpleColumn(x + pad, yTop - h + pad, x + w - pad, yTop - pad);
					for (String line : addressLines(p))
						ct.addElement(new Paragraph(12, line, text));
					ct.go();
					i++;
				}
				if (parties.isEmpty()) doc.add(new Paragraph("No addresses."));
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	// the lead's name and address, a line each
	public static List<String> addressLines(Party p)
	{
		List<String> lines = new ArrayList<>();
		Guest lead = p.getLead();
		lines.add(lead == null ? p.getName() : lead.getName());
		if (lead != null && !lead.getContact().getAddress().isEmpty())
			for (String l : lead.getContact().getAddress().split("\n"))
				if (!l.isBlank()) lines.add(l.strip());
		return lines;
	}
}

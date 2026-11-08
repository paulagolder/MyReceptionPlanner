package org.lerot.myreceptionplanner.reports;

import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Pew;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Seating;
import org.lerot.myreceptionplanner.model.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
 * Sets out the table plan on a page: a panel for each table, with its name
 * or number and the guests seated there in seat order (empty seats left out).
 *
 * The panels stand roughly where the tables stand in the room. The tables are
 * taken in the rows Plan.rows gives (as renumbering does), so the panels read
 * in number order. Each row of panels goes down the page in step with where
 * its tables are, and each panel across the page in step with its table,
 * nudged only as far as needed to keep panels apart. The type is made as
 * large as lets every panel fit.
 *
 * Measurements are in points, from the top left of the area given.
 */
public class TablePlanLayout
{
	public static final double GAP = 14;           // between panels
	public static final double PAD = 5;            // inside a panel
	public static final double LINE = 1.3;         // line height, in font sizes
	public static final double TITLE = 1.2;        // title size, in font sizes
	public static final float MIN_FONT = 5, MAX_FONT = 13;
	private static final double MAX_PANEL_WIDTH = 230;

	// how wide text is: for working out the type size
	public interface Measure
	{
		double width(String text, float size, boolean bold);
	}

	// a guest's line: placeholders (Ann Smith+1) are shown differently
	public record Line(String text, boolean placeholder) {}

	public record Panel(Table table, String title, boolean head, List<Line> lines,
			double x, double y, double width, double height) {}

	// fits is false if even the smallest type would not fit the page
	public record Layout(List<Panel> panels, float fontSize, boolean fits) {}

	public static Layout layout(Plan plan, Seating seating, double width, double height, Measure measure)
	{
		return layout(plan, seating, true, width, height, measure);
	}

	// unseated: for a party with no seats, each panel gives the table's places instead of names
	public static Layout layout(Plan plan, Seating seating, boolean seated, double width, double height,
			Measure measure)
	{
		List<List<Table>> rows = plan.rows();
		if (rows.isEmpty()) return new Layout(List.of(), MAX_FONT, true);
		int across = rows.stream().mapToInt(List::size).max().orElse(1);
		double panelWidth = Math.min(MAX_PANEL_WIDTH, (width - (across - 1) * GAP) / across);

		// the lines in each panel, and the most in any panel of each row
		List<List<List<Line>>> lines = new ArrayList<>();
		double heightUnits = 0;  // the rows' heights, in font sizes, less padding
		double widest = 0;       // the widest text, at size 1
		for (List<Table> row : rows)
		{
			List<List<Line>> rowLines = new ArrayList<>();
			int most = 1;
			for (Table t : row)
			{
				List<Line> l = seated ? withOpenSeats(seated(t, seating), t)
						: List.of(new Line(t.usablePlaces() + (t instanceof Pew ? " seats" : " places"), true));
				rowLines.add(l);
				most = Math.max(most, l.size());
				widest = Math.max(widest, measure.width(plan.label(t), (float) TITLE, true));
				for (Line line : l)
					widest = Math.max(widest, measure.width(line.text(), 1, false));
			}
			lines.add(rowLines);
			heightUnits += TITLE * LINE + 0.4 + most * LINE;
		}
		double fixed = rows.size() * 2 * PAD + (rows.size() - 1) * GAP;
		double bySpace = (height - fixed) / heightUnits;
		double byWidth = widest == 0 ? MAX_FONT : (panelWidth - 2 * PAD) / widest;
		float font = (float) Math.max(MIN_FONT, Math.min(MAX_FONT, Math.min(bySpace, byWidth)));
		boolean fits = bySpace >= MIN_FONT;

		// how tall each row is, then where each row goes down the page
		double[] rowHeights = new double[rows.size()];
		double[] rowDepths = new double[rows.size()];
		for (int r = 0; r < rows.size(); r++)
		{
			int most = Math.max(1, lines.get(r).stream().mapToInt(List::size).max().orElse(1));
			rowHeights[r] = panelHeight(most, font);
			rowDepths[r] = rows.get(r).stream().mapToDouble(Table::getY).average().orElse(0);
		}
		double[] tops = spread(rowDepths, rowHeights, height);

		List<Panel> panels = new ArrayList<>();
		double minX = plan.tables().stream().mapToDouble(Table::getX).min().orElse(0);
		double maxX = plan.tables().stream().mapToDouble(Table::getX).max().orElse(0);
		for (int r = 0; r < rows.size(); r++)
		{
			List<Table> row = rows.get(r);
			double[] places = row.stream().mapToDouble(Table::getX).toArray();
			double[] widths = new double[row.size()];
			java.util.Arrays.fill(widths, panelWidth);
			double[] lefts = spread(places, widths, width, minX, maxX);
			for (int i = 0; i < row.size(); i++)
			{
				Table t = row.get(i);
				List<Line> l = lines.get(r).get(i);
				panels.add(new Panel(t, plan.label(t), t.isHead(), l, lefts[i], tops[r],
						panelWidth, panelHeight(Math.max(1, l.size()), font)));
			}
		}
		return new Layout(panels, font, fits);
	}

	public static double panelHeight(int lines, float font)
	{
		return 2 * PAD + font * (TITLE * LINE + 0.4 + lines * LINE);
	}

	// and a line for any open-access seats, which no one is given
	private static List<Line> withOpenSeats(List<Line> lines, Table t)
	{
		int open = t.getOpenSeats().size();
		if (open == 0) return lines;
		List<Line> out = new ArrayList<>(lines);
		out.add(new Line(open + (open == 1 ? " open seat" : " open seats"), true));
		return out;
	}

	// the guests at the table in seat order, seat 1 first
	static List<Line> seated(Table t, Seating seating)
	{
		List<Line> out = new ArrayList<>();
		for (int k = 0; k < t.places(); k++)
		{
			int chair = t.chairOfSeat(k + 1);
			Optional<Guest> g = seating.guestAt(t.getId(), chair);
			g.ifPresent(guest -> out.add(new Line(guest.displayName(), guest.isPlaceholder())));
		}
		return out;
	}

	private static double[] spread(double[] at, double[] sizes, double length)
	{
		double min = java.util.Arrays.stream(at).min().orElse(0);
		double max = java.util.Arrays.stream(at).max().orElse(0);
		return spread(at, sizes, length, min, max);
	}

	/*
	 * Where to start each of a line of boxes (in order) along a length, each
	 * as near as it can be to where its place falls between min and max,
	 * keeping GAP between boxes and all within the length if they can be.
	 */
	static double[] spread(double[] at, double[] sizes, double length, double min, double max)
	{
		int n = at.length;
		double[] start = new double[n];
		for (int i = 0; i < n; i++)
		{
			double share = max > min ? (at[i] - min) / (max - min) : 0.5;
			start[i] = share * (length - sizes[i]);
		}
		for (int i = 1; i < n; i++)
			start[i] = Math.max(start[i], start[i - 1] + sizes[i - 1] + GAP);
		if (n > 0 && start[n - 1] + sizes[n - 1] > length)
		{
			start[n - 1] = length - sizes[n - 1];
			for (int i = n - 2; i >= 0; i--)
				start[i] = Math.min(start[i], start[i + 1] - GAP - sizes[i]);
		}
		for (int i = 0; i < n; i++)
			start[i] = Math.max(start[i], i == 0 ? 0 : start[i - 1] + sizes[i - 1] + GAP);
		return start;
	}
}

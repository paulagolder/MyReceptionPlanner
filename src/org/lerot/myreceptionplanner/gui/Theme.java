package org.lerot.myreceptionplanner.gui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import java.awt.Color;
import java.util.Map;

// The app's colours, and the FlatLaf look set up with them
public final class Theme
{
	public static final Color PAGE = new Color(0xF4F1EA);     // the floor
	public static final Color GRID = new Color(0xE6E0D5);     // a line every metre
	public static final Color TABLE = Color.WHITE;
	public static final Color EDGE = new Color(0x8A8A8E);     // table and chair outlines
	public static final Color SEAT = new Color(0xE9E4DA);
	public static final Color TEXT = new Color(0x2B2B2B);
	public static final Color ACCENT = new Color(0x2F6FDB);   // the selected table
	public static final Color HEAD = new Color(0xB8860B);     // the head table
	public static final Color FIRST = new Color(0x2E8B57);    // seat 1's outline
	public static final Color FIRST_FILL = new Color(0xC9E6D3);
	public static final Color OFF = new Color(0xBDBDBD);      // a disabled chair's outline
	public static final Color ALTAR = new Color(0xEADFC4);
	public static final Color CLASH = new Color(0xC62828);    // tables standing on each other    // an altar or speakers' table

	private Theme() {}

	public static void install()
	{
		FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#2F6FDB"));
		FlatLightLaf.setup();
	}
}

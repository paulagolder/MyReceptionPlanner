package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.MessageTemplate;
import org.lerot.myreceptionplanner.model.Messages;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.OccasionXml;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * The occasion's message templates: each with a name, a subject and its
 * text, with fill-ins in braces ({leadfirst}, {events} ...) chosen from the
 * list and put in where the text cursor is. Templates can be added, copied
 * and removed; nothing changes until OK. They can also be shared: saved as
 * the default set (MyReceptions/email_templates.xml) that new occasions
 * start with, or taken from it.
 */
public class TemplatesDialog
{
	// true if changed
	public static boolean edit(Component parent, Occasion occasion, Path shared)
	{
		List<MessageTemplate> work = new ArrayList<>();
		for (MessageTemplate t : occasion.templates())
			work.add(new MessageTemplate(t.getName(), t.getSubject(), t.getBody()));
		DefaultListModel<MessageTemplate> listModel = new DefaultListModel<>();
		work.forEach(listModel::addElement);
		JList<MessageTemplate> list = new JList<>(listModel);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JTextField name = new JTextField();
		JTextField subject = new JTextField();
		JTextArea body = new JTextArea(16, 50);
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		boolean[] loading = { false };

		Runnable store = () ->
		{
			MessageTemplate t = list.getSelectedValue();
			if (t == null || loading[0]) return;
			t.setName(name.getText());
			t.setSubject(subject.getText());
			t.setBody(body.getText());
			list.repaint();
		};
		DocumentListener changes = new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { store.run(); }

			public void removeUpdate(DocumentEvent e) { store.run(); }

			public void changedUpdate(DocumentEvent e) { store.run(); }
		};
		name.getDocument().addDocumentListener(changes);
		subject.getDocument().addDocumentListener(changes);
		body.getDocument().addDocumentListener(changes);
		list.addListSelectionListener(e ->
		{
			MessageTemplate t = list.getSelectedValue();
			loading[0] = true;
			name.setText(t == null ? "" : t.getName());
			subject.setText(t == null ? "" : t.getSubject());
			body.setText(t == null ? "" : t.getBody());
			body.setCaretPosition(0);
			loading[0] = false;
			boolean on = t != null;
			name.setEnabled(on);
			subject.setEnabled(on);
			body.setEnabled(on);
		});

		JButton add = new JButton("New");
		add.addActionListener(e ->
		{
			MessageTemplate t = new MessageTemplate("New message", "{occasion}", "Dear {leadfirst},\n\n\n\n{from}");
			work.add(t);
			listModel.addElement(t);
			list.setSelectedValue(t, true);
			name.requestFocusInWindow();
			name.selectAll();
		});
		JButton copy = new JButton("Copy");
		copy.addActionListener(e ->
		{
			MessageTemplate from = list.getSelectedValue();
			if (from == null) return;
			MessageTemplate t = new MessageTemplate(from.getName() + " (copy)", from.getSubject(), from.getBody());
			work.add(t);
			listModel.addElement(t);
			list.setSelectedValue(t, true);
		});
		JButton remove = new JButton("Remove");
		remove.addActionListener(e ->
		{
			MessageTemplate t = list.getSelectedValue();
			if (t == null) return;
			work.remove(t);
			listModel.removeElement(t);
			if (!listModel.isEmpty()) list.setSelectedIndex(0);
		});
		JButton standard = new JButton("Add the standard ones missing");
		standard.setToolTipText("Invitation, Menu choices, Request for details, Reminder, Table and arrangements");
		standard.addActionListener(e ->
		{
			for (MessageTemplate t : Messages.standard())
			{
				if (work.stream().anyMatch(w -> w.getName().equals(t.getName()))) continue;
				work.add(t);
				listModel.addElement(t);
			}
		});

		DefaultListModel<String> fillModel = new DefaultListModel<>();
		for (Map.Entry<String, String> f : Messages.FILL_INS.entrySet())
			fillModel.addElement("{" + f.getKey() + "}  " + f.getValue());
		JList<String> fills = new JList<>(fillModel);
		fills.setVisibleRowCount(8);
		JButton insert = new JButton("Put in");
		insert.setToolTipText("Put the chosen fill-in where the cursor is in the text");
		insert.addActionListener(e ->
		{
			String f = fills.getSelectedValue();
			if (f == null) return;
			body.replaceSelection(f.substring(0, f.indexOf('}') + 1));
			body.requestFocusInWindow();
		});

		JPanel left = new JPanel(new BorderLayout(0, 6));
		left.add(new JScrollPane(list), BorderLayout.CENTER);
		JPanel leftButtons = new JPanel(new MigLayout("insets 0, wrap 3"));
		leftButtons.add(add);
		leftButtons.add(copy);
		leftButtons.add(remove);
		leftButtons.add(standard, "span 3");
		JButton share = new JButton("Make these the default");
		share.setToolTipText("New occasions start with these; saved as " + shared);
		share.addActionListener(e ->
		{
			if (JOptionPane.showConfirmDialog(p0(share), "New occasions will start with these " + work.size()
					+ " templates (and others can take them).\nSave them as " + shared + "?", "Default templates",
					JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
			try
			{
				OccasionXml.saveTemplates(work, shared);
			} catch (IOException ex)
			{
				JOptionPane.showMessageDialog(p0(share), ex.getMessage(), "Cannot save", JOptionPane.ERROR_MESSAGE);
			}
		});
		JButton useShared = new JButton("Take the default ones");
		useShared.setToolTipText("Replace these with the default set in " + shared);
		useShared.setEnabled(Files.isRegularFile(shared));
		useShared.addActionListener(e ->
		{
			if (JOptionPane.showConfirmDialog(p0(useShared), "Replace these templates with the default ones?",
					"Default templates", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
			try
			{
				List<MessageTemplate> loaded = OccasionXml.loadTemplates(shared);
				work.clear();
				work.addAll(loaded);
				listModel.clear();
				work.forEach(listModel::addElement);
				if (!listModel.isEmpty()) list.setSelectedIndex(0);
			} catch (IOException ex)
			{
				JOptionPane.showMessageDialog(p0(useShared), ex.getMessage(), "Cannot read", JOptionPane.ERROR_MESSAGE);
			}
		});
		leftButtons.add(share, "span 3");
		leftButtons.add(useShared, "span 3");
		left.add(leftButtons, BorderLayout.SOUTH);
		left.setPreferredSize(new Dimension(230, 0));

		JPanel right = new JPanel(new MigLayout("insets 0, wrap 2", "[right][grow,fill]", "[][][grow,fill][]"));
		right.add(new JLabel("Name"));
		right.add(name);
		right.add(new JLabel("Subject"));
		right.add(subject);
		right.add(new JLabel("Text"), "top");
		right.add(new JScrollPane(body), "grow");
		JPanel fillPanel = new JPanel(new BorderLayout(6, 0));
		fillPanel.add(new JScrollPane(fills), BorderLayout.CENTER);
		fillPanel.add(insert, BorderLayout.EAST);
		right.add(new JLabel("Fill-ins"), "top");
		right.add(fillPanel, "growx");

		JPanel p = new JPanel(new BorderLayout(10, 0));
		p.add(left, BorderLayout.WEST);
		p.add(right, BorderLayout.CENTER);
		p.setPreferredSize(new Dimension(920, 560));
		if (!listModel.isEmpty()) list.setSelectedIndex(0);

		if (JOptionPane.showConfirmDialog(parent, p, "Message templates", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return false;
		occasion.templates().clear();
		occasion.templates().addAll(work);
		return true;
	}

	private static Component p0(JComponent c)
	{
		return SwingUtilities.getWindowAncestor(c);
	}
}

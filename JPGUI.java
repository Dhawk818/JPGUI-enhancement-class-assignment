import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Drop-in UX upgrade for the decision support UI. */
public class JPGUIEnhanced extends UserInterface {
    private static final int DEFAULT_FONT_SIZE = 13;
    private static final int MIN_RANK = 0;
    private static final int MAX_RANK = 1000;

    static {
        // Native look & feel + slightly larger default font for readability
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        UIManager.put("OptionPane.messageFont", new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
        UIManager.put("OptionPane.buttonFont",  new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
        UIManager.put("Label.font",             new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
        UIManager.put("TextField.font",         new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
        UIManager.put("List.font",              new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
        UIManager.put("Table.font",             new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
        UIManager.put("TableHeader.font",       new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
        UIManager.put("Spinner.font",           new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
        UIManager.put("Button.font",            new Font("SansSerif", Font.PLAIN, DEFAULT_FONT_SIZE));
    }

    @Override
    public void showIntroduction() {
        String msg = """
            <html>
              <h2>Decision Support Aid</h2>
              <p>This wizard helps you compare alternatives against factors, then computes a preferred choice.</p>
              <ul>
                <li>Enter <b>Alternatives</b> (things you’re choosing between)</li>
                <li>Enter <b>Factors</b> (criteria you care about)</li>
                <li>Set <b>Factor Importances</b></li>
                <li>Rate each alternative per factor</li>
              </ul>
              <p>You can use the keyboard (Enter to add, Delete to remove) and all numeric fields are validated.</p>
            </html>
        """;
        JOptionPane.showMessageDialog(null, msg, "Decision Support Aid", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public List<Alternative> getAlternatives() {
        List<String> names = ListManagerDialog.collect(
                "Alternatives",
                "Add an alternative",
                "Enter a name and press Add",
                true
        );
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No alternatives entered.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(2);
        }
        List<Alternative> alts = new ArrayList<>();
        for (String n : names) alts.add(new Alternative(n));
        return alts;
    }

    @Override
    public List<Factor> getFactors() {
        List<String> names = ListManagerDialog.collect(
                "Factors",
                "Add a factor",
                "Enter a factor and press Add",
                true
        );
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No factors entered.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(2);
        }
        List<Factor> factors = new ArrayList<>();
        for (String n : names) factors.add(new Factor(n));
        return factors;
    }

    @Override
    public void getFactorRankings(final List<Factor> factorList, final int standard) {
        if (factorList.size() == 1) {
            factorList.get(0).setRank(standard);
            return;
        }
        // Last factor is baseline; you can change this if you prefer a dropdown for baseline choice.
        int baselineIndex = factorList.size() - 1;
        String header = "<html><h3>Factor Importances</h3>"
                + "Treat <b>" + esc(factorList.get(baselineIndex).getName())
                + "</b> as the baseline with importance = <b>" + standard + "</b>.<br>"
                + "Adjust other factors relative to that baseline.</html>";
        JPanel panel = new JPanel(new BorderLayout(8,8));
        panel.add(new JLabel(header), BorderLayout.NORTH);

        JPanel rows = new JPanel(new GridLayout(factorList.size(), 2, 6, 6));
        List<JSpinner> spinners = new ArrayList<>();
        for (int i = 0; i < factorList.size(); i++) {
            rows.add(new JLabel(esc(factorList.get(i).getName())));
            boolean isBaseline = (i == baselineIndex);
            JSpinner sp = new JSpinner(new SpinnerNumberModel(standard, MIN_RANK, MAX_RANK, 1));
            sp.setEnabled(!isBaseline);
            spinners.add(sp);
            rows.add(sp);
        }
        panel.add(rows, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(null, panel, "Factor Importances", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) {
            // If canceled, fall back to default standard for all.
            for (Factor f : factorList) f.setRank(standard);
            return;
        }
        for (int i = 0; i < factorList.size(); i++) {
            int val = (int) spinners.get(i).getValue();
            factorList.get(i).setRank(val);
        }
    }

    @Override
    public double[][] getCrossRankings(final List<Alternative> alternatives,
                                       final List<Factor> factors,
                                       final int standard) {
        // Matrix with spinners; first alternative fixed at "standard" for every factor.
        double[][] data = new double[alternatives.size()][factors.size()];
        for (int j = 0; j < factors.size(); j++) data[0][j] = standard;

        String lead = "<html><h3>Rate Alternatives per Factor</h3>"
                + "For each factor, the first alternative is anchored at <b>" + standard + "</b>.<br>"
                + "Higher values are more desirable.</html>";

        JTable table = new JTable(new CrossModel(alternatives, factors, data, standard));
        table.setRowHeight(26);
        // Set JSpinner editor for numeric cells (except anchored baseline)
        SpinnerNumberModel numModel = new SpinnerNumberModel(standard, 0, MAX_RANK, 1);
        JSpinner spinner = new JSpinner(numModel);
        DefaultCellEditor spinnerEditor = new DefaultCellEditor(new JTextField()) {
            private final JSpinner sp = new JSpinner(new SpinnerNumberModel(standard, 0, MAX_RANK, 1));
            {
                ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setColumns(5);
            }
            @Override
            public Component getTableCellEditorComponent(JTable t, Object value, boolean isSelected, int row, int column) {
                sp.setValue((int) Math.round(((Number) value).doubleValue()));
                return sp;
            }
            @Override
            public Object getCellEditorValue() {
                return ((Number) sp.getValue()).doubleValue();
            }
        };
        // Apply editor to editable cells
        for (int c = 0; c < table.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(120);
        }
        table.setDefaultEditor(Double.class, spinnerEditor);
        table.setDefaultRenderer(Double.class, table.getDefaultRenderer(Object.class));

        JPanel panel = new JPanel(new BorderLayout(8,8));
        panel.add(new JLabel(lead), BorderLayout.NORTH);
        panel.add(new JScrollPane(table,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(null, panel, "Cross-Rankings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) {
            // If canceled, default everything to the standard:
            for (int r = 0; r < alternatives.size(); r++) {
                for (int c = 0; c < factors.size(); c++) data[r][c] = standard;
            }
            return data;
        }
        // Pull values from table model
        CrossModel model = (CrossModel) table.getModel();
        return model.copyData();
    }

    @Override
    public void showResults(final List<Alternative> alternatives) {
        // Assumes caller already sorted best→worst. We’ll render a tidy summary.
        StringBuilder sb = new StringBuilder("<html><h3>Decider Results</h3>");
        if (!alternatives.isEmpty()) {
            sb.append("<p>Preferred choice: <b>").append(esc(alternatives.get(0).getDescriptor())).append("</b></p>");
        }
        sb.append("<table border='0' cellspacing='2' cellpadding='2'>");
        for (Alternative a : alternatives) {
            sb.append("<tr><td>").append(esc(a.getDescriptor())).append("</td>");
            try {
                // If Alternative has getScore(), show it; otherwise just list the name
                double score = (double) Alternative.class.getMethod("getScore").invoke(a);
                sb.append("<td style='padding-left:16px'>(score: ").append(String.format("%.4f", score)).append(")</td>");
            } catch (Exception ignore) {
                sb.append("<td></td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table></html>");
        JOptionPane.showMessageDialog(null, sb.toString(), "Results", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Helpers ----------

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Simple list manager dialog (add/remove/reorder optional). */
    static class ListManagerDialog extends JDialog {
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private boolean ok;

        static List<String> collect(String title, String fieldPlaceholder, String hint, boolean requireAtLeastOne) {
            ListManagerDialog d = new ListManagerDialog(title, fieldPlaceholder, hint, requireAtLeastOne);
            d.setVisible(true);
            List<String> out = new ArrayList<>();
            if (d.ok) {
                for (int i = 0; i < d.model.getSize(); i++) {
                    String v = d.model.getElementAt(i).trim();
                    if (!v.isEmpty()) out.add(v);
                }
            }
            return out;
        }

        private ListManagerDialog(String title, String fieldPlaceholder, String hint, boolean requireAtLeastOne) {
            super((Frame) null, title, true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JTextField field = new JTextField();
            field.setToolTipText(hint);
            JButton add = new JButton("Add");
            add.addActionListener(e -> {
                String txt = field.getText().trim();
                if (!txt.isEmpty()) {
                    model.addElement(txt);
                    field.setText("");
                    field.requestFocusInWindow();
                }
            });
            field.addActionListener(e -> add.doClick());

            JList<String> list = new JList<>(model);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            JButton remove = new JButton("Remove");
            remove.addActionListener(e -> {
                int idx = list.getSelectedIndex();
                if (idx >= 0) model.remove(idx);
            });

            JPanel top = new JPanel(new BorderLayout(6,6));
            top.add(new JLabel(fieldPlaceholder + ":"), BorderLayout.WEST);
            top.add(field, BorderLayout.CENTER);
            top.add(add, BorderLayout.EAST);

            JPanel center = new JPanel(new BorderLayout(6,6));
            center.add(new JScrollPane(list), BorderLayout.CENTER);
            center.add(remove, BorderLayout.EAST);

            JButton cancel = new JButton("Cancel");
            JButton okBtn = new JButton("OK");
            okBtn.addActionListener(e -> {
                if (requireAtLeastOne && model.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please add at least one item.", "Missing items", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                ok = true;
                dispose();
            });
            cancel.addActionListener(e -> dispose());

            JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            south.add(cancel);
            south.add(okBtn);

            JPanel root = new JPanel(new BorderLayout(8,8));
            root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            root.add(top, BorderLayout.NORTH);
            root.add(center, BorderLayout.CENTER);
            root.add(south, BorderLayout.SOUTH);

            setContentPane(root);
            setSize(520, 420);
            setLocationRelativeTo(null);
        }
    }

    /** Table model for cross rankings with anchored baseline on row 0. */
    static class CrossModel extends AbstractTableModel {
        private final List<Alternative> alts;
        private final List<Factor> factors;
        private final double[][] data;
        private final int standard;

        CrossModel(List<Alternative> alts, List<Factor> factors, double[][] data, int standard) {
            this.alts = alts;
            this.factors = factors;
            this.data = data;
            this.standard = standard;
            // initialize defaults (if untouched)
            for (int r = 0; r < alts.size(); r++) {
                for (int c = 0; c < factors.size(); c++) {
                    if (r == 0) data[r][c] = standard;
                    else if (data[r][c] == 0) data[r][c] = standard;
                }
            }
        }

        @Override public int getRowCount() { return alts.size(); }
        @Override public int getColumnCount() { return factors.size(); }
        @Override public String getColumnName(int c) { return factors.get(c).getName(); }
        @Override public Class<?> getColumnClass(int c) { return Double.class; }
        @Override public boolean isCellEditable(int r, int c) { return r != 0; }
        @Override public Object getValueAt(int r, int c) { return data[r][c]; }
        @Override public void setValueAt(Object val, int r, int c) {
            if (r == 0) return;
            double v = (val instanceof Number) ? ((Number) val).doubleValue() : standard;
            if (v < 0) v = 0;
            if (v > MAX_RANK) v = MAX_RANK;
            data[r][c] = v;
            fireTableCellUpdated(r, c);
        }

        double[][] copyData() {
            double[][] out = new double[data.length][data[0].length];
            for (int r = 0; r < data.length; r++) {
                System.arraycopy(data[r], 0, out[r], 0, data[r].length);
            }
            return out;
        }
    }
}

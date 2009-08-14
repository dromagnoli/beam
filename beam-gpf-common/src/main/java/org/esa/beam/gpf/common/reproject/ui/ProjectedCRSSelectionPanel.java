/*
 * $Id: $
 *
 * Copyright (C) 2009 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.gpf.common.reproject.ui;

import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.TableLayout.Anchor;
import com.bc.ceres.swing.TableLayout.Fill;
import com.jidesoft.list.FilterableListModel;
import com.jidesoft.list.QuickListFilterField;
import com.jidesoft.utils.Lm;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.ProjectedCRS;

import javax.swing.AbstractListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ProjectedCRSSelectionPanel extends JPanel {

    private JTextArea wktField;

    // for testing the UI
    public static void main(String[] args) {
        Lm.verifyLicense("Brockmann Consult", "BEAM", "lCzfhklpZ9ryjomwWxfdupxIcuIoCxg2");
        final JFrame jFrame = new JFrame("CRS Selection Panel");
        Container contentPane = jFrame.getContentPane();
        ProjectedCRSSelectionPanel projectedCRSSelectionPanel = new ProjectedCRSSelectionPanel();
        contentPane.add(projectedCRSSelectionPanel);
        jFrame.setSize(600, 400);
        jFrame.setLocationRelativeTo(null);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                jFrame.setVisible(true);
            }
        });
    }


    public ProjectedCRSSelectionPanel() {
        final CRSListModel crsListModel = new CRSListModel(generateSupportedCRSList());
        final QuickListFilterField filterField = new QuickListFilterField(crsListModel);
        filterField.setHintText("Type here to filter Projections");

        final FilterableListModel listModel = filterField.getDisplayListModel();
        final JList crsList = new JList(listModel);
        crsList.setVisibleRowCount(10);
        filterField.setList(crsList);
        crsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        crsList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                CrsInfo selectedValue = (CrsInfo) crsList.getSelectedValue();
                if (selectedValue != null) {
                    wktField.setText(selectedValue.crs.toString());
                } else {
                    wktField.setText("");
                }
            }
        });

        final JLabel filterLabel = new JLabel("Filter:");
        final JLabel infoLabel = new JLabel("CRS Info:");
        JScrollPane crsListScrollPane = new JScrollPane(crsList);
        crsListScrollPane.setPreferredSize(new Dimension(200, 150));
        wktField = new JTextArea(10, 30);
        wktField.setEditable(false);
        JScrollPane infoAreaScrollPane = new JScrollPane(wktField);
        final JButton defineCrsBtn = new JButton("Create User Defined Projection");

        TableLayout tableLayout = new TableLayout(3);
        setLayout(tableLayout);
        tableLayout.setTableFill(Fill.BOTH);
        tableLayout.setTableAnchor(Anchor.NORTHWEST);
        tableLayout.setTableWeightX(1);
        tableLayout.setTablePadding(4, 4);

        tableLayout.setRowWeightY(0, 0);        // no weight Y for first row
        tableLayout.setCellWeightX(0, 0, 0);    // filter label; no grow in X
        tableLayout.setRowWeightY(1, 1.0);      // second row grow in Y
        tableLayout.setCellColspan(1, 0, 2);    // CRS list; spans 2 cols
        tableLayout.setCellRowspan(1, 2, 2);    // info area; spans 2 rows
        tableLayout.setCellColspan(2, 0, 2);    // defineCrsBtn button; spans to cols

        add(filterLabel);
        add(filterField);
        add(infoLabel);
        add(crsListScrollPane);
        add(infoAreaScrollPane);
        add(defineCrsBtn);
    }


    private static List<CrsInfo> generateSupportedCRSList() {
        // todo - (mp/mz) this takes much time (5 sec.) try to speed up
        final CRSAuthorityFactory authorityFactory = CRS.getAuthorityFactory(true);
        List<CrsInfo> crsList = new ArrayList<CrsInfo>(1000);
        try {
            Set<String> codes = authorityFactory.getAuthorityCodes(ProjectedCRS.class);
            for (String code : codes) {
                try {
                    CoordinateReferenceSystem crs = authorityFactory.createCoordinateReferenceSystem(code);
                    crsList.add(new CrsInfo(code, (ProjectedCRS) crs));
                } catch (Exception ignore) {
                    // bad CRS --> ignore
                }
            }
        } catch (FactoryException ignore) {
        }
        return crsList;
    }

    private static class CRSListModel extends AbstractListModel {

        private final List<CrsInfo> crsList;

        private CRSListModel(List<CrsInfo> projectedCRSList) {
            crsList = new ArrayList<CrsInfo>();
            crsList.addAll(projectedCRSList);
        }

        @Override
        public CrsInfo getElementAt(int index) {
            return crsList.get(index);
        }

        @Override
        public int getSize() {
            return crsList.size();
        }
    }

    private static class CrsInfo {

        private final String epsgCode;
        private final ProjectedCRS crs;

        CrsInfo(String epsgCode, ProjectedCRS crs) {
            this.epsgCode = epsgCode;
            this.crs = crs;
        }

        @Override
        public String toString() {
            return epsgCode + " : " + crs.getName().getCode();
        }
    }

}

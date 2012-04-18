/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.visat.toolviews.stat;

import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.param.ParamChangeEvent;
import org.esa.beam.framework.param.ParamChangeListener;
import org.esa.beam.framework.param.ParamGroup;
import org.esa.beam.framework.param.Parameter;
import org.esa.beam.framework.param.editors.ComboBoxEditor;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.application.ToolView;
import org.esa.beam.util.Debug;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.title.Title;
import org.jfree.ui.RectangleInsets;

import javax.swing.*;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;


/**
 * The density plot pane within the statistics window.
 */
class DensityPlotPanel extends ChartPagePanel {

    private static final String NO_DATA_MESSAGE = "No density plot computed yet.\n" +
            ZOOM_TIP_MESSAGE;
    private static final String CHART_TITLE = "Density Plot";

    private static final int X_VAR = 0;
    private static final int Y_VAR = 1;

    private DataSourceConfig dataSourceConfig;
    private ParamGroup paramGroup;

    private static Parameter[] rasterNameParams = new Parameter[2];
    private static AxisRangeControl[] axisRangeControls = new AxisRangeControl[2];
    private IndexColorModel toggledColorModel;
    private IndexColorModel untoggledColorModel;

    private ChartPanel densityPlotDisplay;
    private XYImagePlot plot;
    private PlotAreaSelectionTool plotAreaSelectionTool;
    private static final Color backgroundColor = new Color(255, 255, 255, 0);
    private boolean plotColorsInverted;
    private JButton toggleColorButton;

    DensityPlotPanel(ToolView parentDialog, String helpId) {
        super(parentDialog, helpId, CHART_TITLE, true);
    }

    @Override
    protected void initContent() {
        initParameters();
        createUI();
        updateContent();
    }

    @Override
    protected void updateContent() {
        if (densityPlotDisplay != null) {
            plot.setImage(null);
            plot.setDataset(null);
            final String[] availableBands = createAvailableBandList();
            updateParameters(X_VAR, availableBands);
            updateParameters(Y_VAR, availableBands);
            toggleColorButton.setEnabled(false);
            setChartTitle();
        }
    }

    private void setChartTitle() {
        final JFreeChart chart = densityPlotDisplay.getChart();
        final List<Title> subtitles = new ArrayList<Title>(7);
        subtitles.add(new TextTitle(MessageFormat.format("{0}, {1}",
                rasterNameParams[X_VAR].getValueAsText(),
                rasterNameParams[Y_VAR].getValueAsText())));
        chart.setSubtitles(subtitles);
    }

    private void updateParameters(int varIndex, String[] availableBands) {

        final RasterDataNode raster = getRaster();
        String rasterName = null;
        if (raster != null) {
            rasterName = raster.getName();
        } else if (availableBands.length > 0) {
            rasterName = availableBands[0];
        }
        if (rasterName != null) {
            rasterNameParams[varIndex].getProperties().setValueSet(availableBands);
            rasterNameParams[varIndex].setValue(rasterName, null);
        } else {
            rasterNameParams[varIndex].getProperties().setValueSet(new String[0]);
        }

    }

    private void initParameters() {
        axisRangeControls[X_VAR] = new AxisRangeControl("X-Axis");
        axisRangeControls[Y_VAR] = new AxisRangeControl("Y-Axis");
        paramGroup = new ParamGroup();
        final String[] availableBands = createAvailableBandList();
        initParameters(X_VAR, availableBands);
        initParameters(Y_VAR, availableBands);
        initColorModels();
        plotColorsInverted = false;
        paramGroup.addParamChangeListener(new ParamChangeListener() {

            public void parameterValueChanged(ParamChangeEvent event) {
                updateUIState();
            }
        });
    }

    private void initParameters(int varIndex, String[] availableBands) {

        final String paramPrefix = "var" + varIndex + ".";

        final RasterDataNode raster = getRaster();
        final String rasterName;
        if (raster != null) {
            rasterName = raster.getName();
        } else if (availableBands.length > 0) {
            rasterName = availableBands[0];
        } else {
            rasterName = "";
        }
        rasterNameParams[varIndex] = new Parameter(paramPrefix + "rasterName", rasterName);
        rasterNameParams[varIndex].getProperties().setValueSet(availableBands);
        rasterNameParams[varIndex].getProperties().setValueSetBound(true);
        rasterNameParams[varIndex].getProperties().setDescription("Band name"); /*I18N*/
        rasterNameParams[varIndex].getProperties().setEditorClass(ComboBoxEditor.class);
        paramGroup.addParameter(rasterNameParams[varIndex]);
    }

    private void initColorModels() {
        for (int j = 0; j <= 1; j++) {
            final int palSize = 256;
            final byte[] r = new byte[palSize];
            final byte[] g = new byte[palSize];
            final byte[] b = new byte[palSize];
            final byte[] a = new byte[palSize];
            r[0] = (byte) backgroundColor.getRed();
            g[0] = (byte) backgroundColor.getGreen();
            b[0] = (byte) backgroundColor.getBlue();
            a[0] = (byte) backgroundColor.getAlpha();
            for (int i = 1; i < 128; i++) {
                if (j == 0) {
                    r[i] = (byte) (2 * i);
                    g[i] = (byte) 0;
                } else {
                    r[i] = (byte) 255;
                    g[i] = (byte) (255 - (2 * (i - 128)));
                }
                b[i] = (byte) 0;
                a[i] = (byte) 255;
            }
            for (int i = 128; i < 256; i++) {
                if (j == 0) {
                    r[i] = (byte) 255;
                    g[i] = (byte) (2 * (i - 128));
                } else {
                    r[i] = (byte) (255 - (2 * i));
                    g[i] = (byte) 0;
                }
                b[i] = (byte) 0;
                a[i] = (byte) 255;
            }
            if (j == 0) {
                toggledColorModel = new IndexColorModel(8, palSize, r, g, b, a);
            } else {
                untoggledColorModel = new IndexColorModel(8, palSize, r, g, b, a);
            }

        }
    }

    private void createUI() {
        plot = new XYImagePlot();
        plot.setAxisOffset(new RectangleInsets(5, 5, 5, 5));
        plot.setNoDataMessage(NO_DATA_MESSAGE);
        plot.getRenderer().setBaseToolTipGenerator(new XYPlotToolTipGenerator());

        JFreeChart chart = new JFreeChart(CHART_TITLE, plot);
        chart.removeLegend();

        dataSourceConfig = new DataSourceConfig();
        final BindingContext bindingContext = new BindingContext(PropertyContainer.createObjectBacked(dataSourceConfig));
        createUI(createChartPanel(chart), createMiddlePanel(), bindingContext);

        updateUIState();
    }

    private void toggleColor() {
        BufferedImage image = plot.getImage();
        if (image != null) {
            if (!plotColorsInverted) {
                image = new BufferedImage(untoggledColorModel, image.getRaster(), image.isAlphaPremultiplied(), null);
            } else {
                image = new BufferedImage(toggledColorModel, image.getRaster(), image.isAlphaPremultiplied(), null);
            }
            plot.setImage(image);
            updateUIState();
            plotColorsInverted = !plotColorsInverted;
        }
    }

    private JPanel createMiddlePanel() {
        toggleColorButton = new JButton("Invert Plot Colors");
        toggleColorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (plot != null) {
                    toggleColor();
                }
            }
        }
        );
        toggleColorButton.setEnabled(false);
        final JPanel middlePanel = GridBagUtils.createPanel();
        final GridBagConstraints gbc = GridBagUtils.createConstraints("anchor=NORTHWEST,fill=HORIZONTAL");
        GridBagUtils.setAttributes(gbc, "gridx=0,weightx=1,weighty=0");
        GridBagUtils.addToPanel(middlePanel, axisRangeControls[X_VAR].getPanel(), gbc, "gridy=0,insets.top=0");
        GridBagUtils.addToPanel(middlePanel, rasterNameParams[X_VAR].getEditor().getComponent(), gbc, "gridy=1");
        GridBagUtils.addToPanel(middlePanel, axisRangeControls[Y_VAR].getPanel(), gbc, "gridy=2");
        GridBagUtils.addToPanel(middlePanel, rasterNameParams[Y_VAR].getEditor().getComponent(), gbc, "gridy=3");
        GridBagUtils.addToPanel(middlePanel, new JPanel(), gbc, "gridy=4,weighty=1");
        GridBagUtils.addToPanel(middlePanel, toggleColorButton, gbc, "gridy=5,weighty=1");
        return middlePanel;
    }

    private ChartPanel createChartPanel(JFreeChart chart) {
        densityPlotDisplay = new ChartPanel(chart);

        MaskSelectionToolSupport maskSelectionToolSupport = new MaskSelectionToolSupport(this,
                densityPlotDisplay,
                "densitity_plot_area",
                "Mask generated from selected density plot area",
                Color.RED,
                PlotAreaSelectionTool.AreaType.ELLIPSE) {
            @Override
            protected String createMaskExpression(PlotAreaSelectionTool.AreaType areaType, double x0, double y0, double dx, double dy) {
                double rr = Math.sqrt(dx * dx + dy * dy);
                return String.format("distance(%s, %s, %s, %s) < %s",
                        rasterNameParams[0].getValue(),
                        rasterNameParams[1].getValue(),
                        x0,
                        y0,
                        rr);
            }
        };

        densityPlotDisplay.getPopupMenu().addSeparator();
        densityPlotDisplay.getPopupMenu().add(maskSelectionToolSupport.createMaskSelectionModeMenuItem());
        densityPlotDisplay.getPopupMenu().add(maskSelectionToolSupport.createDeleteMaskMenuItem());
        densityPlotDisplay.getPopupMenu().addSeparator();
        densityPlotDisplay.getPopupMenu().add(createCopyDataToClipboardMenuItem());
        return densityPlotDisplay;
    }

    private RasterDataNode getRaster(int varIndex) {
        final Product product = getProduct();
        if (product == null) {
            return null;
        }
        final String rasterName = rasterNameParams[varIndex].getValue().toString();
        RasterDataNode raster = product.getRasterDataNode(rasterName);
        if (raster == null) {
            if (getRaster() != null && rasterName.equalsIgnoreCase(getRaster().getName())) {
                raster = getRaster();
            }
        }
        Debug.assertTrue(raster != null);
        return raster;
    }

    private void updateUIState() {
        super.updateContent();
        setChartTitle();
    }

    @Override
    protected void compute() {

        final RasterDataNode rasterX = getRaster(X_VAR);
        final RasterDataNode rasterY = getRaster(Y_VAR);

        if (rasterX == null || rasterY == null) {
            return;
        }

        ProgressMonitorSwingWorker<BufferedImage, Object> swingWorker = new ProgressMonitorSwingWorker<BufferedImage, Object>(
                this, "Computing density plot") {

            @Override
            protected BufferedImage doInBackground(ProgressMonitor pm) throws Exception {
                pm.beginTask("Computing density plot...", 100);
                try {
                    setRange(X_VAR, rasterX, dataSourceConfig.roiMask, SubProgressMonitor.create(pm, 15));
                    setRange(Y_VAR, rasterY, dataSourceConfig.roiMask, SubProgressMonitor.create(pm, 15));
                    final BufferedImage densityPlotImage = ProductUtils.createDensityPlotImage(rasterX,
                            axisRangeControls[X_VAR].getMin().floatValue(),
                            axisRangeControls[X_VAR].getMax().floatValue(),
                            rasterY,
                            axisRangeControls[Y_VAR].getMin().floatValue(),
                            axisRangeControls[Y_VAR].getMax().floatValue(),
                            dataSourceConfig.roiMask,
                            512,
                            512,
                            backgroundColor,
                            null,
                            SubProgressMonitor.create(pm, 70));
                    return densityPlotImage;
                } finally {
                    pm.done();
                }
            }

            @Override
            public void done() {
                try {
                    final BufferedImage densityPlotImage = get();
                    double minX = axisRangeControls[X_VAR].getMin();
                    double maxX = axisRangeControls[X_VAR].getMax();
                    double minY = axisRangeControls[Y_VAR].getMin();
                    double maxY = axisRangeControls[Y_VAR].getMax();
                    if (minX > maxX || minY > maxY) {
                        JOptionPane.showMessageDialog(getParentDialogContentPane(),
                                "Failed to compute density plot.\n" +
                                        "No Pixels considered..",
                                /*I18N*/
                                CHART_TITLE, /*I18N*/
                                JOptionPane.ERROR_MESSAGE);
                        plot.setDataset(null);
                        return;

                    }

                    if (MathUtils.equalValues(minX, maxX, 1.0e-4)) {
                        minX = Math.floor(minX);
                        maxX = Math.ceil(maxX);
                    }
                    if (MathUtils.equalValues(minY, maxY, 1.0e-4)) {
                        minY = Math.floor(minY);
                        maxY = Math.ceil(maxY);
                    }
                    plot.setImage(densityPlotImage);
                    plot.setImageDataBounds(new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY));
                    axisRangeControls[X_VAR].setMin(MathUtils.round(minX, Math.pow(10.0, 2)));
                    axisRangeControls[X_VAR].setMax(MathUtils.round(maxX, Math.pow(10.0, 2)));
                    axisRangeControls[Y_VAR].setMin(MathUtils.round(minY, Math.pow(10.0, 2)));
                    axisRangeControls[Y_VAR].setMax(MathUtils.round(maxY, Math.pow(10.0, 2)));
                    plot.getDomainAxis().setLabel(getAxisLabel(getRaster(X_VAR)));
                    plot.getRangeAxis().setLabel(getAxisLabel(getRaster(Y_VAR)));
                    toggleColorButton.setEnabled(true);
                    if(plotColorsInverted){
                        toggleColor();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(getParentDialogContentPane(),
                            "Failed to compute density plot.\n" +
                                    "Calculation canceled.",
                            /*I18N*/
                            CHART_TITLE, /*I18N*/
                            JOptionPane.ERROR_MESSAGE);
                } catch (CancellationException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(getParentDialogContentPane(),
                            "Failed to compute density plot.\n" +
                                    "Calculation canceled.",
                            /*I18N*/
                            CHART_TITLE, /*I18N*/
                            JOptionPane.ERROR_MESSAGE);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(getParentDialogContentPane(),
                            "Failed to compute density plot.\n" +
                                    "An error occurred:\n" +
                                    e.getCause().getMessage(),
                            CHART_TITLE, /*I18N*/
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        swingWorker.execute();
    }

    private String getAxisLabel(RasterDataNode raster) {
        final String unit = raster.getUnit();
        if (unit != null) {
            return raster.getName() + " (" + unit + ")";
        }
        return raster.getName();
    }

    private void setRange(int varIndex, RasterDataNode raster, Mask mask, ProgressMonitor pm) throws IOException {
        if (axisRangeControls[varIndex].isAutoMinMax()) {
            Stx stx;
            if (mask == null) {
                stx = raster.getStx(false, pm);
            } else {
                stx = new StxFactory().withRoiMask(mask).create(raster, pm);
            }
            axisRangeControls[varIndex].setMin(stx.getMinimum());
            axisRangeControls[varIndex].setMax(stx.getMaximum());
        }
    }

    private String[] createAvailableBandList() {
        final Product product = getProduct();
        final List<String> availableBandList = new ArrayList<String>(17);
        if (product != null) {
            for (int i = 0; i < product.getNumBands(); i++) {
                final Band band = product.getBandAt(i);
                availableBandList.add(band.getName());
            }
            for (int i = 0; i < product.getNumTiePointGrids(); i++) {
                final TiePointGrid grid = product.getTiePointGridAt(i);
                availableBandList.add(grid.getName());
            }
        }
        // if raster is only bound to the product and does not belong to it
        final RasterDataNode raster = getRaster();
        if (raster != null && raster.getProduct() == product) {
            final String rasterName = raster.getName();
            if (!availableBandList.contains(rasterName)) {
                availableBandList.add(rasterName);
            }
        }

        return availableBandList.toArray(new String[availableBandList.size()]);
    }

    @Override
    protected boolean checkDataToClipboardCopy() {
        final int warnLimit = 2000;
        final int excelLimit = 65536;
        final int numNonEmptyBins = getNumNonEmptyBins();
        if (numNonEmptyBins > warnLimit) {
            String excelNote = "";
            if (numNonEmptyBins > excelLimit - 100) {
                excelNote = "Note that e.g., Microsoft® Excel 2002 only supports a total of "
                        + excelLimit + " rows in a sheet.\n";   /*I18N*/
            }
            final String message = MessageFormat.format(
                    "This density plot contains {0} non-empty bins.\n" +
                            "For each bin, a text data row containing an x, y and z value will be created.\n" +
                            "{1}\nPress ''Yes'' if you really want to copy this amount of data to the system clipboard.\n",
                    numNonEmptyBins, excelNote);
            final int status = JOptionPane.showConfirmDialog(this,
                    message, /*I18N*/
                    "Copy Data to Clipboard", /*I18N*/
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (status != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        return true;
    }

    private byte[] getValidData(BufferedImage image) {
        if (image != null &&
                image.getColorModel() instanceof IndexColorModel &&
                image.getData().getDataBuffer() instanceof DataBufferByte) {
            return ((DataBufferByte) image.getData().getDataBuffer()).getData();
        }
        return null;
    }

    protected int getNumNonEmptyBins() {
        final byte[] data = getValidData(plot.getImage());
        int n = 0;
        if (data != null) {
            int b;
            for (byte aData : data) {
                b = aData & 0xff;
                if (b != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    @Override
    protected String getDataAsText() {
        final BufferedImage image = plot.getImage();
        final Rectangle2D bounds = plot.getImageDataBounds();

        final byte[] data = getValidData(image);
        if (data == null) {
            return null;
        }

        final StringBuffer sb = new StringBuffer(64000);
        final int w = image.getWidth();
        final int h = image.getHeight();

        final RasterDataNode rasterX = getRaster(X_VAR);
        final String nameX = rasterX.getName();
        final double sampleMinX = bounds.getMinX();
        final double sampleMaxX = bounds.getMaxX();

        final RasterDataNode rasterY = getRaster(Y_VAR);
        final String nameY = rasterY.getName();
        final double sampleMinY = bounds.getMinY();
        final double sampleMaxY = bounds.getMaxY();

        sb.append("Product name:\t").append(rasterX.getProduct().getName()).append("\n");
        sb.append("Dataset X name:\t").append(nameX).append("\n");
        sb.append("Dataset Y name:\t").append(nameY).append("\n");
        sb.append('\n');
        sb.append(nameX).append(" minimum:\t").append(sampleMinX).append("\t").append(rasterX.getUnit()).append(
                "\n");
        sb.append(nameX).append(" maximum:\t").append(sampleMaxX).append("\t").append(rasterX.getUnit()).append(
                "\n");
        sb.append(nameX).append(" bin size:\t").append((sampleMaxX - sampleMinX) / w).append("\t").append(
                rasterX.getUnit()).append("\n");
        sb.append(nameX).append(" #bins:\t").append(w).append("\n");
        sb.append('\n');
        sb.append(nameY).append(" minimum:\t").append(sampleMinY).append("\t").append(rasterY.getUnit()).append(
                "\n");
        sb.append(nameY).append(" maximum:\t").append(sampleMaxY).append("\t").append(rasterY.getUnit()).append(
                "\n");
        sb.append(nameY).append(" bin size:\t").append((sampleMaxY - sampleMinY) / h).append("\t").append(
                rasterY.getUnit()).append("\n");
        sb.append(nameY).append(" #bins:\t").append(h).append("\n");
        sb.append('\n');

        sb.append(nameX);
        sb.append('\t');
        sb.append(nameY);
        sb.append('\t');
        sb.append("Bin counts\t(cropped at 255)");
        sb.append('\n');

        int x, y, z;
        double v1, v2;
        for (int i = 0; i < data.length; i++) {
            z = data[i] & 0xff;
            if (z != 0) {

                x = i % w;
                y = h - i / w - 1;

                v1 = sampleMinX + ((x + 0.5) * (sampleMaxX - sampleMinX)) / w;
                v2 = sampleMinY + ((y + 0.5) * (sampleMaxY - sampleMinY)) / h;

                sb.append(v1);
                sb.append('\t');
                sb.append(v2);
                sb.append('\t');
                sb.append(z);
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    private static class DataSourceConfig {

        public boolean useRoiMask;
        private Mask roiMask;

    }

}


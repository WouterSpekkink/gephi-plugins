/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.freetime.plugins.metric.trophicincoherence;

import java.text.DecimalFormat;
import javax.swing.JPanel;
import org.gephi.statistics.spi.Statistics;
import org.gephi.statistics.spi.StatisticsUI;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author wouter
 */
@ServiceProvider(service = StatisticsUI.class)
public class TrophicIncoherenceUI implements StatisticsUI {
    private TrophicIncoherence statistic;
    private TrophicIncoherencePanel panel;
        
    @Override    
    public JPanel getSettingsPanel() {
        panel = new TrophicIncoherencePanel();
        return panel;
    }

    @Override
    public void setup(Statistics statistics) {
        this.statistic = (TrophicIncoherence) statistics;
         if (panel != null) {
            panel.setAveraged(statistic.isAveraged());
        }
    }

    @Override
    public void unsetup() {
        if (panel != null) {
            statistic.setAveraged(panel.isAveraged());
        }
        panel = null;
        statistic = null;
        panel = null;
        statistic = null;
    }

    @Override
    public Class<? extends Statistics> getStatisticsClass() {
        return TrophicIncoherence.class;
    }

    @Override
    public String getValue() {
        DecimalFormat df = new DecimalFormat("###.###");
        double value = statistic.getIncoherence();
        if (value != -1.0) {
            return "" + df.format(value);
        } else {
            return "";
        }
    }

    @Override
    public String getDisplayName() {
        return "Trophic Incoherence";
    }

    @Override
    public String getCategory() {
        return StatisticsUI.CATEGORY_NETWORK_OVERVIEW;
    }

    @Override
    public int getPosition() {
        return 11000;
    }

    @Override
    public String getShortDescription() {
        return null;
    }

}

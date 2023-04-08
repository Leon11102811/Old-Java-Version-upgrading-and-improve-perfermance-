/****************************************************************************
 *
 *   Copyright (c) 2017,2018 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.ui.widgets.panel;

import com.comino.flight.FXMLLoadHelper;
import com.comino.flight.MainApp;
import com.comino.flight.model.AnalysisDataModel;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.prefs.MAVPreferences;
import com.comino.jfx.extensions.ChartControlPane;
import com.comino.mavcom.control.IMAVController;

import eu.hansolo.medusa.Gauge;
import eu.hansolo.medusa.Gauge.SkinType;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Control;
import javafx.scene.paint.Color;

public class BatteryWidget extends ChartControlPane  {

//	private static final float vo_range[] = { 10.0f, 13.0f, 11.5f,  0 };
//	private static final float cu_range[] = { 0.0f,  15.0f, 0,     12 };
//	private static final float ca_range[] = { 0.0f,  100.0f, 60.0f, 0 };


	@FXML
	private Gauge g_voltage;

	@FXML
	private Gauge g_capacity;

	private  AnalysisModelService dataService;

	private AnimationTimer   task;
	private AnalysisDataModel model;

	private double voltage = 0;
	private double capacity = 0;
	
	private Color  color_bar;
	private Color  color_text;
	private Color  color_text_off;

	public BatteryWidget() {
		super(300,true);

		FXMLLoadHelper.load(this, "BatteryWidget.fxml");
		
	}


	@FXML
	private void initialize() {
		
		if(MAVPreferences.isLightTheme()) {
			color_bar        = Color.web("#0000C0");
			color_text        = Color.BLACK;
			color_text_off = Color.web("#606060");
			
		} else {
			color_bar         = Color.web("#2e9fbf");
			color_text        = Color.WHITE;
			color_text_off = Color.DARKGRAY;
		}
		
		
		
		task = new AnimationTimer() {
			private long tms;
			@Override public void handle(long now) {
				if((System.currentTimeMillis()-tms)>1000 && MainApp.getPrimaryStage().isFocused()) {
					tms = System.currentTimeMillis();
		            
					if(Math.abs(voltage - model.getValue("BATV")) > 0.1f) {
						voltage = model.getValue("BATV");
						g_voltage.setValue(voltage);
						if(voltage < 10.5 && voltage > 0)
							g_voltage.setBarColor(Color.RED);
						if(voltage > 11.0)
							g_voltage.setBarColor(color_bar);
					}
					
					if(!Double.isFinite(voltage) || voltage <= 6){
		            	g_voltage.setValue(6);
		            	g_voltage.setBarColor(Color.DARKGREY);
		        
		            } 
					
					if(Math.abs(capacity - model.getValue("BATP")) > 0.01f) {
						capacity = model.getValue("BATP");
						g_capacity.setValue(capacity*100f);
						if(capacity < 0.15 && capacity > 0)
							g_capacity.setBarColor(Color.RED);
						if(capacity > 0.20)
							g_capacity.setBarColor(color_bar);
					}
					
					if(!Double.isFinite(capacity)) {
		            	g_capacity.setValue(0);
		            	g_capacity.setBarColor(Color.DARKGREY);
		            	return;
		            }
				}
			}
		};

	}


	private void setupGauge(Gauge gauge, String unit) {
		gauge.animatedProperty().set(false);
		gauge.setSkinType(SkinType.SLIM);
		gauge.setBarColor(color_bar);
		if(MAVPreferences.isLightTheme())
		 gauge.setBarBackgroundColor(Color.LIGHTGRAY);
		gauge.setTitle(unit);
		gauge.setUnit("Battery");
		gauge.disableProperty().bind(state.getConnectedProperty().not().and(state.getLogLoadedProperty().not()));
		gauge.setValueColor(color_text);
		gauge.setTitleColor(color_text);
		gauge.setUnitColor(color_text);
	}

	private void setColor(Gauge gauge, Color color) {
		gauge.setValueColor(color);
		gauge.setTitleColor(color);
		gauge.setUnitColor(color);
	}


	public void setup(IMAVController control) {
		this.dataService = AnalysisModelService.getInstance();
		this.model = dataService.getCurrent();
		
		setupGauge(g_voltage,"V");
		setupGauge(g_capacity,"%");
		
		g_voltage.setDecimals(1);
		g_capacity.setDecimals(0);

		state.getCurrentUpToDate().addListener((e,o,n) -> {
			Platform.runLater(() -> {
				if(n.booleanValue()) {
					setColor(g_voltage,color_text);
					setColor(g_capacity,color_text);
				}
				else {
					setColor(g_voltage,color_text_off);
					setColor(g_capacity,color_text_off);
				}
			});
		});
		task.start();

	}

}

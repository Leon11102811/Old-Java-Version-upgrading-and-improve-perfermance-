/****************************************************************************
 *
 *   Copyright (c) 2017,2020 Eike Mansfeld ecm@gmx.de.
 *   All rights reserved.
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


package com.comino.flight.ui.tabs;

import com.comino.flight.FXMLLoadHelper;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.prefs.MAVPreferences;
import com.comino.flight.ui.widgets.panel.AirWidget;
import com.comino.flight.ui.widgets.view3D.View3DWidget;
import com.comino.flight.ui.widgets.view3D.utils.Xform;
import com.comino.jfx.extensions.ChartControlPane;
import com.comino.mavcom.control.IMAVController;
import com.comino.mavcom.model.segment.Status;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;


public class MAV3DViewTab extends Pane  {

	private static final String[] PERSPECTIVES = { "Follow vehicle", "Static observer", "Vehicle"};
	private static final String[] SOURCES = { "LocalPosition","LocalPoition corr.", "VisionPosition","Groundtruth"};

	private View3DWidget widget = null;

	@FXML
	private Pane main;


//	@FXML
//	private MessageWidget msg;


	@FXML
	private ChoiceBox<String> perspective;
	
	@FXML
	private ChoiceBox<String> source;
	
	@FXML
	private CheckBox     show_traj;
	
	@FXML
	private CheckBox     show_obs;

	@FXML
	private Slider zoom;
	
	private StateProperties stateProperties = StateProperties.getInstance();

	public MAV3DViewTab() {
		FXMLLoadHelper.load(this, "MAV3DViewTab.fxml");
	}

	@FXML
	private void initialize() {
		

		widget = new View3DWidget(new Xform(),0,0,true,javafx.scene.SceneAntialiasing.BALANCED);
		widget.fillProperty().set(Color.ALICEBLUE);
		widget.widthProperty().bind(this.widthProperty().subtract(20));
		widget.heightProperty().bind(this.heightProperty().subtract(54));
		widget.setLayoutX(10);  widget.setLayoutY(10);
		main.getChildren().add(widget);

		perspective.getItems().addAll(PERSPECTIVES);
		source.getItems().addAll(SOURCES);


		perspective.getSelectionModel().selectedIndexProperty().addListener((v,o,n) -> {
			widget.setPerspective(n.intValue());
		});
		
		source.getSelectionModel().selectedIndexProperty().addListener((v,o,n) -> {
			widget.setDataSource(n.intValue());
		});

		zoom.setValue(100f);
		zoom.valueProperty().addListener(new ChangeListener<Number>() {
			public void changed(ObservableValue<? extends Number> ov,
					Number old_val, Number new_val) {
				widget.scale(new_val.floatValue());
			}
		});
		
		show_traj.selectedProperty().addListener((e,o,n) -> {
			widget.enableTrajectoryView(n.booleanValue());
		});
		
		show_obs.selectedProperty().addListener((e,o,n) -> {
			widget.enableObstacleView(n.booleanValue());
		});
		

		this.setOnZoom(event -> {
			double z = zoom.getValue() * ((( event.getZoomFactor() - 1 ) / 2) + 1.0);
			zoom.setValue(z);
		});
		
		//widget.disableProperty().bind(this.disabledProperty());


	}

	public MAV3DViewTab setup(IMAVController control) {
		ChartControlPane.addChart(4,widget.setup(control));
//		msg.setup(control);
		perspective.getSelectionModel().selectFirst();
		source.getSelectionModel().selectFirst();
		
		
		return this;
	}

}

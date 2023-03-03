/****************************************************************************
 *
 *   Copyright (c) 2017,2021 Eike Mansfeld ecm@gmx.de. All rights reserved.
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
import com.comino.flight.observables.StateProperties;
import com.comino.jfx.extensions.ChartControlPane;
import com.comino.mavcom.control.IMAVController;

import javafx.beans.property.BooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;

public class ControlWidget extends ChartControlPane  {

	@FXML
	private CheckBox details;

	@FXML
	private CheckBox parameters;

	@FXML
	private CheckBox video;

	@FXML
	private CheckBox vehiclectl;

	private StateProperties stateProperties = StateProperties.getInstance();

	public ControlWidget() {
		super(300,true);

		FXMLLoadHelper.load(this, "ControlWidget.fxml");
	}

	public BooleanProperty getDetailVisibility() {
		return details.selectedProperty();
	}

	public BooleanProperty getVideoVisibility() {
		return video.selectedProperty();
	}

	public BooleanProperty getVehicleCtlVisibility() {
		return vehiclectl.selectedProperty();
	}

	public BooleanProperty getTuningVisibility() {
		return parameters.selectedProperty();
	}

	@FXML
	private void initialize() {

		details.setSelected(true);
		
		vehiclectl.visibleProperty().bind(stateProperties.getMSPProperty());

		stateProperties.getMSPProperty().addListener((e,o,n) -> {
			if(n.booleanValue()) {
				vehiclectl.setSelected(true);
			}
		});
		
		stateProperties.getCVAvailableProperty().addListener((e,o,n) -> {
			if(!n.booleanValue()) {
				video.setSelected(false);
			}
		});
	

		stateProperties.getConnectedProperty().addListener((e,o,n) -> {
			if(!n.booleanValue() && !stateProperties.getLogLoadedProperty().get()) {
				video.setSelected(false);
			}
		});

		
		stateProperties.getParamLoadedProperty().addListener((e,o,n) -> {
			if(!n.booleanValue())
				parameters.setSelected(false);
		});
		
//		stateProperties.getLogLoadedProperty().addListener((e,o,n) -> {
//			if(n.booleanValue()) {
//				video.setSelected(false);
//			}
//		});
		
		parameters.disableProperty().bind(stateProperties.getParamLoadedProperty().not());
	

		vehiclectl.disableProperty().bind(stateProperties.getMSPProperty().not());
//		video.setDisable(true);
	    video.disableProperty().bind(stateProperties.isLogLoading());
		

	}

	public void setup(IMAVController control) {
        
	}

}

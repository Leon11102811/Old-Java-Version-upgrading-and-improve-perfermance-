///****************************************************************************
// *
// *   Copyright (c) 2021 Eike Mansfeld ecm@gmx.de. All rights reserved.
// *
// * Redistribution and use in source and binary forms, with or without
// * modification, are permitted provided that the following conditions
// * are met:
// *
// * 1. Redistributions of source code must retain the above copyright
// *    notice, this list of conditions and the following disclaimer.
// * 2. Redistributions in binary form must reproduce the above copyright
// *    notice, this list of conditions and the following disclaimer in
// *    the documentation and/or other materials provided with the
// *    distribution.
// * 3. Neither the name of the copyright holder nor the names of its
// *    contributors may be used to endorse or promote products derived
// *    from this software without specific prior written permission.
// *
// * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
// * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
// * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
// * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
// * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
// * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
// * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// * POSSIBILITY OF SUCH DAMAGE.
// *
// ****************************************************************************/
//
//
//package com.comino.video.src.mp4;
//
//import java.awt.image.BufferedImage;
//import java.io.File;
//
//import com.comino.flight.model.service.AnalysisModelService;
//import com.comino.flight.observables.StateProperties;
//import com.comino.video.src.IMWStreamVideoProcessListener;
//
//import javafx.beans.property.BooleanProperty;
//import javafx.beans.property.SimpleBooleanProperty;
//import javafx.embed.swing.SwingFXUtils;
//import javafx.scene.image.Image;
//
//public class MP4Recorder implements IMWStreamVideoProcessListener {
//
//	private MSPSequenceEncoder encoder = null;
//	private final StateProperties state = StateProperties.getInstance();
//
//	private BufferedImage bimg = null;
//	private final String path;
//	private long tms_start = 0;
//
//
//	public MP4Recorder(String path) {
//		this.path = path;
//	}
//
//	public void start() {
//		try {
//			encoder = new MSPSequenceEncoder(new File(path+"/video.mp4"));
//			tms_start = 0;
//			state.getMP4RecordingProperty().set(true);
//			System.out.println("MP4 recording started - MP4");
//		}  catch (Exception e1) { 
//			e1.printStackTrace();
//		}
//	}
//
//	public void stop() {
//		try {
//			if(encoder!=null && state.getMP4RecordingProperty().get()) {
//				encoder.finish();
//				state.getMP4RecordingProperty().set(false);
//				System.out.println("MP4 recording stopped");
//			}
//		}  catch (Exception e1) { 
//			e1.printStackTrace();
//		}
//
//	}
//
//	@Override
//	public void process(Image image,  float fps, long tms) throws Exception {
//		if(state.getMP4RecordingProperty().get() && image!=null && encoder!=null) {
//			bimg = SwingFXUtils.fromFXImage(image, bimg);
//			if(tms_start == 0)
//				tms_start = tms;
//			encoder.encodeImage(bimg, fps, tms, tms_start);
//		}
//	}
//
//}

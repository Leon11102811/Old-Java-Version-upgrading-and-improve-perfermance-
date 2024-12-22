/****************************************************************************
 *
 *   Copyright (c) 2017,2022 Eike Mansfeld ecm@gmx.de. All rights reserved.
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


package com.comino.flight.model.service;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.mavlink.messages.MAV_CMD;

import com.comino.flight.file.FileHandler;
import com.comino.flight.log.ulog.ULogFromMAVLinkReader;
import com.comino.flight.model.AnalysisDataModel;
import com.comino.flight.model.AnalysisDataModelMetaData;
import com.comino.flight.model.KeyFigureMetaData;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.param.MAVGCLPX4Parameters;
import com.comino.mavcom.control.IMAVController;
import com.comino.mavcom.model.DataModel;
import com.comino.mavcom.model.segment.Status;
import com.comino.mavutils.workqueue.WorkQueue;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import us.ihmc.log.LogTools;


public class AnalysisModelService  {

	private static AnalysisModelService instance = null;

	public static final int DEFAULT_INTERVAL_US  = 20000;
	public static final int MAVHIRES_INTERVAL_US = 10000;
	public static final int HISPEED_INTERVAL_US  = 5000;

	public static  final int STOPPED		 	= 0;
	public static  final int PRE_COLLECTING 	= 1;
	public static  final int COLLECTING     	= 2;
	public static  final int POST_COLLECTING    = 3;
	public static  final int READING_HEADER     = 4;

	private volatile List<AnalysisDataModel>      modelList   = null;

	private DataModel								  model   = null;
	private ULogFromMAVLinkReader                   ulogger   = null;
	private AnalysisDataModel				    	current   = null;
	private AnalysisDataModel                        record   = null;
	private StateProperties                           state   = null;

	private AnalysisDataModelMetaData                  meta  =  null;
	private List<ICollectorRecordingListener>    listener  =  null;

	private int mode     = STOPPED;
	private int old_mode = STOPPED;

	private boolean isFirst     = false;
	private boolean isReplaying = false;

	private boolean converter_running = false;

	private int totalTime_sec = 30;
	private int collector_interval_us = DEFAULT_INTERVAL_US;
	private IMAVController control = null;

	private CombinedConverter converter = null;

	private final WorkQueue wq = WorkQueue.getInstance();

	private AnimationTimer task = null;

	public static AnalysisModelService getInstance(IMAVController control) {
		if(instance==null) {
			instance = new AnalysisModelService(control);
		}
		return instance;
	}

	public static AnalysisModelService getInstance() {
		return instance;
	}


	private AnalysisModelService(IMAVController control) {

		this.control = control;
		this.converter = new CombinedConverter();

		this.meta = AnalysisDataModelMetaData.getInstance();
		this.listener = new ArrayList<ICollectorRecordingListener>();

		this.modelList     = new ArrayList<AnalysisDataModel>(50000);
		this.model         = control.getCurrentModel();
		this.current       =  new AnalysisDataModel();
		this.record        =  new AnalysisDataModel();
		this.state         = StateProperties.getInstance();

		this.ulogger = new ULogFromMAVLinkReader(control);


		state.getConnectedProperty().addListener((o,ov,nv) -> {
			
			control.getStatusManager().reset();

			if(state.getLogLoadedProperty().get())
				return;

			if(nv.booleanValue()) {

				synchronized(converter) {
					converter.notify();
				}

			} else {
				
				control.getCurrentModel().sys.clear();
				control.getCurrentModel().sys.resetStatus();
				current.clear();
			}
		});

		state.getIMUProperty().addListener((o,ov,nv) -> {
			if(nv.booleanValue()) {
				synchronized(converter) {
					converter.notify();
				}
			}
		});

		state.getReplayingProperty().addListener((o,ov,nv) -> {
			if(ov.booleanValue() && !nv.booleanValue()) {
				synchronized(converter) {
					converter.notify();
				}
			}
			isReplaying = nv.booleanValue();
		});

		task = new AnimationTimer() {
			long tms_old=0;
			@Override
			public void handle(long now) {
				if((now - tms_old)<5_000_000)
					return;
				tms_old = now;
				try {
					for(ICollectorRecordingListener updater : listener)
						updater.update(System.nanoTime());
				} catch(Exception e) {  e.printStackTrace(); }		
			}		
		};
	}

	public AnalysisModelService(DataModel model) {
		this.modelList     = new LinkedList<AnalysisDataModel>();
		this.model         =  model;
		this.current       =  new AnalysisDataModel();
		this.state         = StateProperties.getInstance();

	}

	public void startConverter() {
		Thread c = new Thread(converter);
		c.setName("Combined model converter");
		c.setPriority(Thread.NORM_PRIORITY+2);
		c.start();
	}

	public void close() {
		stop();
		if(ulogger.isLogging())
			control.sendMAVLinkCmd(MAV_CMD.MAV_CMD_LOGGING_STOP);
	}

	public void registerListener(ICollectorRecordingListener l) {
		listener.add(l);
	}


	public int setCollectorInterval(int interval_us) {
		this.collector_interval_us = interval_us;
		return this.collector_interval_us;
	}

	public void setDefaultCollectorInterval() {
		setCollectorInterval(DEFAULT_INTERVAL_US);
	}

	public List<AnalysisDataModel> getModelList() {
		return modelList;
	}

	public int size() {
		return modelList.size();
	}

	public AnalysisDataModel getCurrent() {
		return current;
	}
	
	public int getCurrentIndex() {
		return modelList.indexOf(current);
	}

	public AnalysisDataModel getLast(float f) {
		if(mode==STOPPED && modelList.size()>0)
			return modelList.get(calculateX1IndexByFactor(f));
		return current;
	}

	public AnalysisDataModel getLast() {
		if(modelList.size()>0)
			return modelList.get(modelList.size()-1);
		return current;
	}

	public void setCurrent(int index) {
		
		if(modelList.size() > index) {
			if(index < 0) {
				return;
			}
			current.set(modelList.get(index));
			
		} else if(modelList.size() == 0)
			return;
		else 
			current.set(modelList.get(modelList.size()-1));
		
	}

	public void setCurrent(double time) {
		setCurrent(calculateXIndexByTime(time));
	}

	public int getCollectorInterval_ms() {
		return collector_interval_us/1000;
	}

	public boolean start() {

		if(!control.isConnected()) {
			return false;
		}

		this.isFirst=true;

		if(mode==STOPPED) {
			setDefaultCollectorInterval();
			model.reset();
			clear();
			mode = COLLECTING;
			task.start();
			return true;
		}
		return mode != STOPPED;
	}


	public boolean stop() {
		mode = STOPPED;
		return false;
	}

	public void stop(int delay_sec) {
		mode = POST_COLLECTING;
		if(delay_sec > 0)
			wq.addSingleTask("LP",delay_sec * 1000, () -> { stop(); task.stop(); } );
		else {
			stop(); task.stop(); 
		}
	}

	public void setModelList(List<AnalysisDataModel> list) {
		mode = STOPPED;
		clear();
		list.forEach((e) -> {
			e.calculateVirtualKeyFigures(meta);
			modelList.add(e);
		});
		setCurrent(0);
	}

	public void reset() {
		//setDefaultCollectorInterval();
		mode = STOPPED;
		current.clear();
		clear();
		model.clear();
		
		FileHandler.getInstance().clear();
		state.reset();


		if(!state.getConnectedProperty().get())
			MAVGCLPX4Parameters.getInstance().clear();
		else
			MAVGCLPX4Parameters.getInstance().refreshParameterList(true);
	}

	public void setTotalTimeSec(int totalTime) {
		this.totalTime_sec = totalTime;
	}

	public int getTotalTimeSec() {
		return totalTime_sec;
	}

	public int calculateX0IndexByFactor(double factor) {
		int current_x0_pt = (int)((modelList.size() - totalTime_sec *  1000f / getCollectorInterval_ms()) * factor);

		if(current_x0_pt<0)
			current_x0_pt = 0;

		return current_x0_pt;
	}

	public int calculateIndexByFactor(double factor) {
		int current_x0_pt = (int)((modelList.size()) * factor);

		if(current_x0_pt<0)
			current_x0_pt = 0;

		return current_x0_pt;
	}

	public int calculateX1IndexByFactor(double factor) {

		int current_x1_pt = calculateX0IndexByFactor(factor) + (int)(totalTime_sec *  1000f / getCollectorInterval_ms());

		if(current_x1_pt>modelList.size()-1)
			current_x1_pt = modelList.size()-1;

		if(current_x1_pt<0)
			current_x1_pt = 0;

		return (int)(current_x1_pt);
	}

	public int calculateX0Index(int index_x1) {

		int current_x0_pt = index_x1 - (int)(totalTime_sec *  1000f / getCollectorInterval_ms());

		if(current_x0_pt<0)
			current_x0_pt = 0;

		return current_x0_pt;
	}

	public int calculateX1Index(int index_x0) {

		int current_x1_pt = index_x0 + (int)(totalTime_sec *  1000f / getCollectorInterval_ms());

		if(current_x1_pt>modelList.size()-1)
			current_x1_pt = modelList.size()-1;

		return current_x1_pt;
	}

	public int calculateXIndexByTime(double time) {
		int x = (int)(1000f / getCollectorInterval_ms() * time);
		if(x < 0)
			return 0;
		if(x > modelList.size()-1)
			return modelList.size()-1;
		return x;
	}

	public long getTotalRecordingTimeMS() {
		if(modelList.size()> 0)
			return (modelList.get(modelList.size()-1).tms) / 1000;
		else
			return 0;
	}

	public boolean isConverterRunning() {
		return converter_running;
	}


	public boolean isCollecting() {
		return mode != STOPPED ;
	}

	public boolean isReplaying() {
		return isReplaying;
	}

	public int getMode() {
		return mode;
	}

	private void clear() {
		modelList.clear();
		state.getLogULOGProperty().set(false);
	}

	private class CombinedConverter implements Runnable {

		long tms_start =0; long tms_last; long wait = 0;
		float perf = 0; AnalysisDataModel m = null;

		@Override
		public void run() {


            LogTools.info("AnalysisModelService converter thread started ..");
			mode = STOPPED;


			while(true) {

				if(!model.sys.isStatus(Status.MSP_CONNECTED) || isReplaying) {
					if(ulogger.isLogging())         
						ulogger.enableLogging(false);
					mode = STOPPED; old_mode = STOPPED;
					state.getRecordingProperty().set(STOPPED);
					if(!state.getReplayingProperty().get())
						current.setValue("SWIFI", 0);
					converter_running = false;
					perf = 0;
					current.setValue("MAVGCLNET", 0);
					current.setValue("MAVGCLACC", perf);
					synchronized(converter) {
						LogTools.info("Combined Converter is waiting");
						try { 	this.wait(); } catch (InterruptedException e) { }
						LogTools.info("Combined Converter continued");
					}
					continue;
				}


				current.setValue("MAVGCLACC", perf);
				current.setValue("MAVGCLNET", control.getTransferRate()/1024f);

				if(mode!=STOPPED && old_mode == STOPPED && model.sys.isStatus(Status.MSP_CONNECTED)) {
					Platform.runLater(()->{
						state.getRecordingProperty().set(READING_HEADER);
						state.getLogLoadedProperty().set(false);
						state.getRecordingProperty().set(COLLECTING);
					});
					tms_start = System.currentTimeMillis() * 1000;
				}

				if(mode==STOPPED && old_mode != STOPPED) {
					ulogger.enableLogging(false);
					state.getRecordingProperty().set(STOPPED);
				}

				old_mode = mode;

				current.msg = null; wait = System.nanoTime();

				if(state.getReplayingProperty().get()) {
					try { 	Thread.sleep(100); 	} catch (InterruptedException e) { 	}
					continue;
				}

				if(!state.getInitializedProperty().get())
					continue;

				if(state.getCurrentUpToDate().getValue() && !state.getLogLoadedProperty().get()) {
					try {
						current.setValues(KeyFigureMetaData.MSP_SOURCE,model,meta);
						current.calculateVirtualKeyFigures(meta);
					} catch(ConcurrentModificationException c) { }
				}


				if(ulogger.isLogging()) {
					//	record.setValues(KeyFigureMetaData.MSP_SOURCE,model,meta);
					record.setValues(KeyFigureMetaData.ULG_SOURCE,ulogger.getData(), meta);
					record.calculateVirtualKeyFigures(meta);
				}

				if(model.msg != null && model.msg.text!=null) {
					current.msg = model.msg;
					record.msg  = model.msg;
				} else {
					current.msg = null; record.msg = null;
				}

				converter_running = true;


				if(mode!=STOPPED) {

					// Skip first
					if(!isFirst) {

						if(ulogger.isLogging())
							m = (AnalysisDataModel)record.clone();
						else
							m = (AnalysisDataModel)current.clone();


						m.tms = System.currentTimeMillis() * 1000 - tms_start;
						m.dt_sec = m.tms / 1e6f;
						modelList.add(m);


						state.getRecordingAvailableProperty().set(false);

						perf = ( m.tms - tms_last ) / 1e3f;
						tms_last = m.tms;

					} else
						tms_last = System.nanoTime() / 1000 - tms_start;
					isFirst = false;
				} else {

					state.getRecordingAvailableProperty().set(modelList.size()>0);

					current.tms = System.currentTimeMillis() * 1000 ;
					perf = ( current.tms - tms_last ) / 1e3f;
					tms_last = current.tms;

					// Slow down conversion if not recording or armed
					if(!state.getArmedProperty().get()) {
						//						while((200000000  - (System.nanoTime()-wait)) > 0) {
						//							LockSupport.parkNanos(1000000);
						//						}
						LockSupport.parkNanos(100000000 - (System.nanoTime()-wait) - 2500000 );
						continue;
					}
				}

				//				while((collector_interval_us*1000 - (System.nanoTime()-wait)) > 0) {
				//					LockSupport.parkNanos(100000);
				//				}

				LockSupport.parkNanos(collector_interval_us*1000 - (System.nanoTime()-wait) - 3000000 );
			}
		}
	}


}
